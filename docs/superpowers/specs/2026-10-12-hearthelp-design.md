# HeartHelp — Design Spec

Date: 2026-10-12
Status: Approved in brainstorming, pending written-spec review

## 1. Purpose

A personal Android app for one user who takes medication that can make their heart rate vary. The app reads live heart rate from a Google Fitbit Air, warns the user when the rate stays outside limits they set, and, if the user doesn't respond, notifies up to 5 emergency contacts the user has chosen, by SMS and by phone call.

**Success criteria**
- A sustained out-of-range heart rate raises an alarm on the phone within about 10 seconds.
- If the user doesn't cancel within 20 seconds, every emergency contact gets an SMS and the call chain starts.
- A disconnected band, a revoked permission or a killed service is never silent. The user is told.
- No one outside the user's emergency contacts list is ever contacted.

**Non-goals:** iOS, publishing to the Play Store, a cloud backend or accounts, history charts, support for other wearables, medical diagnosis.

## 2. Data source

The Fitbit Air can share live heart rate over Bluetooth Low Energy using the standard Heart Rate Profile (GATT service `0x180D`, characteristic `0x2A37`), at about one reading every 2 seconds. The user turns sharing on in the Google Health app: Connections → Fitbit Air → Share heart rate.

Open risks, checked in plan step 1 with nRF Connect before any app code is written:
- Whether sharing stays on for hours or switches itself off.
- Whether the band connects to more than one app at a time. Assume one.
- Battery drain on the band.

If sharing turns out to stop after a while, the user must be told and the design revisited before going further.

## 3. Platform and stack

- Native Kotlin, minSdk 29 (Android 10), Jetpack Compose UI, DataStore for settings, Room for the event log.
- No backend. All data stays on the phone.

## 4. Components

| Unit | Responsibility | Depends on |
|---|---|---|
| `HeartRateSource` | Scans for and connects to the chosen band, subscribes to `0x2A37`, parses the readings (8-bit or 16-bit format according to the flags byte), outputs a `Flow<HeartRateReading(bpm, timestamp)>` and reconnects automatically. | Android BLE |
| `AlertEvaluator` | Pure Kotlin with no Android code. Takes the readings, clock ticks, user actions and settings, and returns the alert state. | none |
| `EmergencyDispatcher` | Sends the SMS to all contacts and runs the call chain. | SMS, telephony, call log, location |
| `MonitoringService` | Foreground service (`connectedDevice` type). Connects the source to the evaluator, updates the notification, launches `AlarmActivity` and invokes the dispatcher. Restarts after a reboot. | units above |
| `AlarmActivity` | Full-screen alarm shown over the lock screen. Plays a loud sound on the alarm audio stream (heard even on silent), vibrates, and shows a 20s countdown, an **I'm OK** button and a **Dismiss** button. | MonitoringService |
| Settings / Contacts / Log screens | Thresholds, band selection, emergency contacts, event log, Test alert, SOS. | DataStore, Room |

## 5. Emergency contacts

- 1 to 5 contacts, each stored by the app as a name and phone number. They can be chosen with the system contact picker or typed in. The app never reads the rest of the phone's address book.
- The list is ordered, and the order sets the call-chain order. The user can reorder it.
- Monitoring can't start while the list is empty.

## 6. Alert logic

### States
`NORMAL`, `BREACH_PENDING`, `ALARM`, `DISPATCHED`, `SNOOZED`, `SIGNAL_LOST`.

### Transitions
- `NORMAL` → `BREACH_PENDING` when a reading is below `low` or above `high`.
- `BREACH_PENDING` → `NORMAL` on any in-range reading.
- `BREACH_PENDING` → `ALARM` when every reading over the last `sustainSeconds` has been out of range.
- `ALARM` → `SNOOZED` when the user taps **I'm OK**. Alerts are paused for `snoozeMinutes`, then the state returns to `NORMAL`.
- `ALARM` → `DISPATCHED` when the `countdownSeconds` countdown expires.
- **SOS** (button in the app or notification action) → `DISPATCHED` straight from any state, with no countdown.
- `DISPATCHED` → `NORMAL` only when the user taps **Dismiss**. Nothing else is sent while in `DISPATCHED`.
- Any state except `DISPATCHED` → `SIGNAL_LOST` when no reading has arrived for `signalLostMinutes`. This alerts the user only, with a notification and a short sound. It never contacts anyone. The next reading returns the state to `NORMAL`.

### Defaults (all user-adjustable)
| Setting | Default |
|---|---|
| low | 45 bpm |
| high | 140 bpm |
| sustainSeconds | 10 |
| countdownSeconds | 20 |
| snoozeMinutes | 5 |
| signalLostMinutes | 2 |

The user is advised to set `low` and `high` with their doctor.

## 7. Dispatch

1. **SMS to every contact.** It contains the trigger (high or low heart rate, or SOS), the last bpm, the time, a Google Maps link to the last known location if one is available, and the line *"Reply OK if you're handling this."* Each SMS is retried up to 3 times if sending fails. If the location isn't available within 5 seconds, the SMS goes out without the link.
2. **Call chain**, one pass through the contacts in order:
   - Place a call (`ACTION_CALL`) to the current contact and wait until the phone state returns to idle.
   - Read the most recent outgoing call-log entry for that number. If it was connected for **20 seconds or more**, stop the chain.
   - Otherwise go on to the next contact.
   - The chain also stops if **any contact replies to the SMS with a message containing "OK"** (case-insensitive, from one of the contact numbers) or if the user taps **Dismiss**.
   - The chain ends after the last contact. There's no second pass through the list.
   - **Known limitation:** voicemail that records for 20 seconds or more counts as answered. The SMS to all contacts and the "OK" reply reduce this risk.
3. Every dispatch, SMS result, call and reply is written to the event log.

## 8. Reliability and errors

- Foreground service with a permanent notification ("Monitoring — 72 bpm").
- Setup asks the user to exempt the app from battery optimization and links to the phone maker's specific background setting.
- Monitoring restarts after a reboot (`BOOT_COMPLETED`) if it was on before.
- Bluetooth off counts as signal lost and also shows a prompt to turn Bluetooth back on.
- If any required permission is missing, a persistent warning names what won't work.
- Event log (Room): alarms, snoozes, dispatches, SMS results, calls, replies and periods of lost signal.

## 9. Permissions

`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (API 31+) / `BLUETOOTH` + `ACCESS_FINE_LOCATION` (API 29–30), `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `SEND_SMS`, `RECEIVE_SMS`, `CALL_PHONE`, `READ_PHONE_STATE`, `READ_CALL_LOG`, `ACCESS_FINE_LOCATION`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Each one is requested during setup with a sentence explaining why it's needed.

## 10. Testing

- **Unit tests:** `AlertEvaluator` (sustained breach, a single glitch resetting the timer, snooze expiring, SOS, signal lost, no repeat dispatch while `DISPATCHED`), the heart-rate parser (8-bit and 16-bit formats), and the call-chain decisions (connected ≥ 20s, OK reply, dismiss, list exhausted), each with fake inputs.
- **Test alert:** runs the full flow but sends the SMS and the call only to the user's own number, which the user enters in Settings.
- **Manual:** the nRF Connect check (plan step 1), then a 24-hour on-wrist run with the phone locked and the screen off.
