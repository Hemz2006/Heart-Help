# HeartHelp

An Android app that watches your live heart rate from a **Google Fitbit Air** and gets help if something goes wrong.

When your heart rate stays too high or too low, your phone sounds a loud alarm. If you don't tap **I'm OK** within 20 seconds, HeartHelp texts your emergency contacts your location and calls them one by one until someone answers or replies "OK". It was built for someone whose medication can make their heart rate swing.

> **Safety note.** HeartHelp is a personal safety backup, not a medical device. It depends on your phone, your band, Bluetooth, mobile signal and Android letting it run in the background, and any of those can fail. Don't rely on it as your only safety net, and ask your doctor what heart-rate limits are right for you.

---

## Contents

- [Features](#features)
- [How it works](#how-it-works)
- [Requirements](#requirements)
- [Installation](#installation)
  - [1. Get the code](#1-get-the-code)
  - [2. Install the build tools](#2-install-the-build-tools)
  - [3. Build the app](#3-build-the-app)
  - [4. Install it on your phone](#4-install-it-on-your-phone)
- [First-time setup in the app](#first-time-setup-in-the-app)
- [Using HeartHelp](#using-hearthelp)
- [Alert rules and defaults](#alert-rules-and-defaults)
- [Testing it before you rely on it](#testing-it-before-you-rely-on-it)
- [Troubleshooting](#troubleshooting)
- [Privacy](#privacy)
- [Known limitations](#known-limitations)
- [For developers](#for-developers)
- [Licence](#licence)

---

## Features

- **Live heart rate** straight from the Fitbit Air over Bluetooth, updated about every 2 seconds.
- **Loud full-screen alarm** when your heart rate stays out of range, shown over the lock screen and heard even when the phone is on silent.
- **20-second "I'm OK" countdown** so a brief spike or a sensor glitch doesn't alarm your family.
- **Emergency texts** to up to 5 contacts you choose, with your heart rate, the time and a Google Maps link to where you are.
- **Call chain**: calls your contacts in order until someone answers or replies "OK" to the text.
- **SOS button** that alerts your contacts immediately.
- **Test alert** that runs the whole flow but texts and calls only you.
- **"No signal" warning** if the band stops sending (off your wrist, out of range, flat battery, Bluetooth off). This warning goes to you only, never to your contacts.
- **Heart-rate history** for the last 6 hours up to 90 days, with your average, lowest and highest.
- **Share with your doctor** as a CSV spreadsheet and a picture of the graph.
- **Activity log** of every alarm, text, call and signal change.
- Keeps running in the background, restarts after a phone reboot, and restores an alert that was interrupted.
- A calm, always-dark design, made to be easy to read at night.

## How it works

```
Fitbit Air ──Bluetooth──▶ HeartHelp (runs in the background on your phone)
                              │
       heart rate out of range for 10 seconds
                              ▼
               Loud alarm + "I'm OK" countdown (20 s)
                   │                          │
            you tap I'm OK            no answer in 20 s
                   ▼                          ▼
      alerts paused for 5 minutes   texts to all contacts (with location)
                                              ▼
                                    calls contact 1, then 2, …
                                    stops when someone answers
                                    or replies "OK"
```

Everything happens on your phone. There's no account, no server and no cloud.

## Requirements

**Phone**
- Android **10 or newer**, with a SIM that can send texts and make calls
- Bluetooth Low Energy (every modern Android phone has it)

**Band**
- A **Google Fitbit Air** with **Share heart rate** turned on in the Google Health app (see [First-time setup](#first-time-setup-in-the-app)). Other bands that use the standard Bluetooth heart-rate format may also work, but haven't been tested.

**Computer** (to build and install the app)
- Windows, macOS or Linux
- **JDK 17 or newer** (JDK 21 recommended). Android Studio comes with one.
- **Android SDK** with platform 35. Android Studio installs this for you.
- A USB cable for your phone

> There's no Play Store listing. You build the app yourself and install it over USB. This is also the most reliable way to give it the SMS and call permissions it needs.

---

## Installation

### 1. Get the code

```bash
git clone https://github.com/Hemz2006/Heart-Help.git
cd Heart-Help
```

### 2. Install the build tools

**Option A: Android Studio (easiest)**

1. Install [Android Studio](https://developer.android.com/studio).
2. Open the `Heart-Help` folder in Android Studio. It downloads the SDK and Gradle automatically, then creates `local.properties` for you.
3. Skip to [step 4](#4-install-it-on-your-phone) and press **Run ▶** with your phone connected. Or carry on with step 3 to build from the terminal.

**Option B: Command line only**

1. Install a JDK 17+ (for example [Temurin 21](https://adoptium.net/)) and set `JAVA_HOME` to it.
2. Download the Android **command-line tools** from the bottom of the [Android Studio download page](https://developer.android.com/studio#command-tools).
3. Unzip them so that this path exists: `<SDK>/cmdline-tools/latest/bin/sdkmanager`. For `<SDK>`, a good choice is:
   - Windows: `%LOCALAPPDATA%\Android\Sdk`
   - macOS: `~/Library/Android/sdk`
   - Linux: `~/Android/Sdk`
4. Install the SDK packages and accept the licences:
   ```bash
   sdkmanager --sdk_root="<SDK>" --licenses
   sdkmanager --sdk_root="<SDK>" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
   ```
5. Create a file called `local.properties` in the project folder that points at the SDK:
   ```properties
   # Windows: escape the backslashes and the colon
   sdk.dir=C\:\\Users\\YOU\\AppData\\Local\\Android\\Sdk
   # macOS / Linux
   # sdk.dir=/Users/YOU/Library/Android/sdk
   ```
   `local.properties` is git-ignored, so each computer needs its own.

### 3. Build the app

macOS / Linux / Git Bash:
```bash
./gradlew assembleDebug
```
Windows PowerShell / Command Prompt:
```powershell
.\gradlew.bat assembleDebug
```

The first build downloads Gradle and the libraries, so it takes a few minutes. When it finishes you'll have:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 4. Install it on your phone

**a. Turn on developer mode and USB debugging**
1. On your phone, open **Settings → About phone** and tap **Build number** 7 times.
2. Open **Settings → System → Developer options** and turn on **USB debugging**.

**b. Connect and allow the computer**
1. Plug the phone into the computer with a USB cable.
2. Unlock the phone. When it asks **"Allow USB debugging?"**, tick **Always allow from this computer** and tap **Allow**.
3. Check the connection. `adb` is in `<SDK>/platform-tools/`:
   ```bash
   adb devices
   ```
   Your phone should be listed with the word **`device`** next to it. If it says `unauthorized`, see [Troubleshooting](#troubleshooting).

**c. Install**
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
On Windows PowerShell, with the default SDK location:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
```
You should see `Success`. You can unplug the phone now. The app runs entirely on the phone and doesn't need the cable or computer again, except to install updates.

> **Don't install by copying the APK to the phone and tapping it.** On Android 13 and newer, apps installed that way can't get the SMS and call-log permissions HeartHelp needs. If you must install that way, open **Settings → Apps → HeartHelp → ⋮ → Allow restricted settings** before granting permissions.

**Updating later:** pull the latest code, build again, and run the same `adb install -r …` command. Your settings, contacts, history and activity log are kept.

---

## First-time setup in the app

**1. Turn on heart-rate sharing on the band**
- In the **Google Health** app, go to **Connections → Fitbit Air → Share heart rate → Get started**.
- Close any other app that might be connected to the band's heart rate (for example nRF Connect, Strava or Zwift). The band may only allow one app at a time.

**2. Home: finish setup**

Tap **"N steps left to finish setup"** and use **Fix** on each one:
- **Allow permissions:** texts, calls, phone state, call log, location, Bluetooth and notifications. Each one is used only for the alert flow.
- **Location "Allow all the time"**, so texts include your location even when the phone is locked.
- **Turn off battery optimisation**, so Android doesn't stop HeartHelp in the background.
- **Allow full-screen alarms** (Android 14+), so the alarm shows over the lock screen.
- **Turn on alarm notifications.**
- **Turn on Bluetooth.**

Some phone makers (Samsung, Xiaomi, OnePlus, Oppo, Vivo and others) close background apps aggressively. The **"Stop your phone closing the app in the background"** link opens [dontkillmyapp.com](https://dontkillmyapp.com/) with steps for your phone.

**3. Settings**
- **Your name** appears in the alert text.
- **Your own number** is used only by Test alert.
- **Heart-rate limits:** set them with your doctor. The defaults are 45 and 140 bpm.
- **Band:** tap **Scan for Fitbit Air** and choose your band.
- Tap **Save settings**.

**4. Contacts**
- Add **1 to 5** people you trust, either with **Choose from your contacts** or by typing a number.
- The order matters: **#1 is called first**. Use **Move up** to reorder.
- Tell them what HeartHelp is, so a text from it doesn't confuse them, and that replying **OK** stops the calls.

**5. Home: start monitoring**
- Tap **Start monitoring**. Your live heart rate appears in the middle, and the ring around it pulses in time with your heartbeat.
- A permanent notification shows "Monitoring — N bpm" while it runs.

---

## Using HeartHelp

| Screen | What it's for |
|---|---|
| **Home** | Live heart rate, monitoring on/off, **SOS**, **Test alert**, and any setup still to finish. During an alert it also shows **I'm OK** / **Stop the alert**, in case the alarm screen didn't appear. |
| **History** | Your heart rate over 6 h, 24 h, 7 d, 30 d or 90 d: average per minute (line), lowest to highest (shaded) and your limits (dashed). Gaps mean the band wasn't sending. **Share** sends a CSV and a PNG. |
| **Contacts** | Your emergency contacts, in call order. |
| **Settings** | Your name and number, heart-rate limits, timing, and which band to use. |
| **Log** | Everything HeartHelp has done: alarms, texts, calls, replies, signal lost and restored. |

**When the alarm goes off**
- Tap **I'm OK** if you're fine. Alerts then pause for 5 minutes.
- If you don't answer within 20 seconds, your contacts are texted and called. Tap **I'm safe, stop the alert** when you're OK to stop further calls.
- You can also answer from the alarm notification, or from the Home screen.

**SOS**: tap **SOS → Send SOS** to text and call your contacts straight away. It's also available as a button on the monitoring notification.

**Test alert**: runs the full alarm, text and call flow, but texts and calls **only your own number**. Use it after setup and after every update.

## Alert rules and defaults

All of these can be changed in **Settings**.

| Setting | Default | Meaning |
|---|---|---|
| Low limit | 45 bpm | Alarm if your heart rate stays **below** this |
| High limit | 140 bpm | Alarm if your heart rate stays **above** this |
| Must last | 10 s | Every reading for this long must be out of range. One normal reading resets it. |
| Countdown | 20 s | Time to tap **I'm OK** before your contacts are alerted |
| Pause | 5 min | How long alerts pause after **I'm OK** |
| No signal | 2 min | Warn **you** (not your contacts) if no readings arrive for this long |

Other rules:
- A reading of 0 bpm, or one where the band reports it isn't touching your skin, is ignored, so taking the band off never sets off a LOW alarm.
- Losing the signal never cancels an alarm countdown that's already running.
- A call counts as **answered** if it stayed connected for 20 seconds or more.
- A reply counts as **OK** if it starts with "OK" or "okay" and isn't a question. "not ok" and "is he ok?" don't count.

## Testing it before you rely on it

Do these after installing and after every update. Tell your contacts before tests 6 and 7.

1. Put the phone on silent and tap **Test alert**. You should get a loud alarm, a 20 s countdown, then a text and a call to **you only**.
2. Tap **Test alert** again and tap **I'm OK**. It should stop, and nothing should be sent.
3. Temporarily set **High limit** a little above your resting rate and walk briskly. The alarm should start after about 10 seconds. Put the limit back afterwards.
4. Repeat test 3 with the screen locked. The alarm should wake the screen.
5. Turn off Share heart rate (or walk out of range) for 10+ minutes with the screen off. You should get **"No signal from your band"**, and your contacts should get nothing.
6. With a contact's agreement, tap **SOS**. They should get a text with a map link, then a call. If they reply **OK**, the Log shows "OK from … — no more calls".
7. Restart the phone while monitoring. Monitoring should come back by itself.
8. Leave it running for 24 hours, then check **Log** for unexpected "Signal lost" entries.

## Troubleshooting

| Problem | Fix |
|---|---|
| `adb: device unauthorized` | Unlock the phone and accept **Allow USB debugging?**. If no prompt appears: set the USB mode to **File transfer**, or go to **Developer options → Revoke USB debugging authorisations**, turn USB debugging off and on, reconnect, and run `adb kill-server` then `adb devices`. |
| `adb: no devices/emulators found` | Check the cable (some are charge-only), that USB debugging is on, and that the phone is unlocked. |
| **Scan for Fitbit Air** finds nothing | Make sure **Share heart rate** is on in Google Health, the band is near the phone, Bluetooth is on, and no other app is connected to the band. |
| Home says **No signal from your band** | The band is off your wrist, out of range, flat, or sharing has stopped. Turn **Share heart rate** back on in Google Health. HeartHelp reconnects by itself. |
| Can't grant SMS or call-log permission | The app was installed by tapping the APK. Reinstall with `adb install`, or enable **Allow restricted settings** (see [step 4](#4-install-it-on-your-phone)). |
| Monitoring stops overnight | Turn off battery optimisation for HeartHelp, and follow your phone maker's steps on [dontkillmyapp.com](https://dontkillmyapp.com/). Check **Log** for "Could not start". |
| Alarm only shows as a banner | Allow **full-screen alarms** (Home → finish setup). You can always answer from the banner or the Home screen. |
| No alarm sound | Make sure the **Heart-rate alarm** notification channel isn't blocked. HeartHelp plays on the alarm volume and turns it up during an alert. |
| "Monitoring could not start" notification | A required permission was removed, or Android blocked a background start. Open HeartHelp and finish setup again. |

## Privacy

- HeartHelp has **no server, no account and no analytics**. Your heart rate, settings, contacts and history stay on your phone.
- It only contacts the 1 to 5 people **you** add, and only during an alert, SOS or Test alert (which goes only to you).
- It reads your phone's contact list only when you use **Choose from your contacts**, to fill in the person you pick.
- Your location is read only when an alert is sent, so it can go in the text.
- Shared CSV and PNG files are created only when you tap **Share**, and go only where you send them.

## Known limitations

- **Voicemail can look like an answer.** Android can't tell a person from voicemail. If voicemail picks up and records for 20+ seconds, the call chain may stop there. Everyone still gets the text, and an **OK** reply is the reliable signal.
- **A lost signal only warns you.** If the band stops sending, HeartHelp warns you but never alerts your contacts.
- **Background restrictions:** if battery optimisation is on or your phone maker kills background apps, monitoring can stop. Setup walks you through preventing this.
- **Dual-SIM phones** set to "ask every time" may show a SIM picker instead of placing the call. Choose a default SIM for calls and texts.
- **After a reboot**, monitoring resumes once the phone has been unlocked for the first time.
- Only the Fitbit Air has been tested.

---

## For developers

**Stack:** Kotlin 2.1, Jetpack Compose (Material 3), Android Gradle Plugin 8.7, minSdk 29 / targetSdk 35, DataStore, Room, kotlinx-serialization, coroutines. No backend.

**Run the unit tests**
```bash
./gradlew testDebugUnitTest
```
They cover the alert logic, heart-rate parsing, contact rules, SMS text, OK-reply detection, call-chain decisions and history maths.

**Project layout**
```
app/src/main/java/com/hearthelp/
├── domain/      Pure Kotlin logic, no Android code (fully unit-tested)
│   ├── AlertEvaluator.kt     alert state machine (normal → alarm → contacts)
│   ├── CallChain.kt          who to call next
│   ├── AlertMessage.kt       the emergency text
│   ├── SmsReplies.kt         what counts as an "OK" reply
│   ├── MinuteAggregator.kt   per-minute heart-rate history
│   └── HistorySeries.kt      graph smoothing, summary, CSV
├── ble/         Fitbit Air connection (Bluetooth heart-rate profile)
├── emergency/   texts, calls, location, OK replies
├── monitor/     background service, alarm, notifications, reboot restart
├── data/        settings (DataStore), activity log + history (Room)
└── ui/          Compose screens and the "Night watch" theme
```

**Design documents**
- Design spec: [`docs/superpowers/specs/2026-10-12-hearthelp-design.md`](docs/superpowers/specs/2026-10-12-hearthelp-design.md)
- Implementation plan: [`docs/superpowers/plans/2026-10-12-hearthelp.md`](docs/superpowers/plans/2026-10-12-hearthelp.md)
- Hardware check: [`docs/hardware-check.md`](docs/hardware-check.md)

## Licence

No licence has been chosen for the code yet, so all rights are reserved by the author.

The bundled **IBM Plex Sans** font is © IBM Corp., used under the [SIL Open Font License 1.1](docs/IBM-Plex-Sans-OFL.txt).
