# HeartHelp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A native Android app that reads live heart rate from a Fitbit Air over BLE, raises a local alarm when the rate stays out of range, and, if the user doesn't cancel, texts and then calls up to 5 chosen emergency contacts.

**Architecture:** Pure-Kotlin domain logic (`domain/`, `ble/HeartRateParser`) holds every decision and is unit-tested. Thin Android adapters handle BLE, SMS, calls, location, sound, notifications and storage. A single foreground `MonitoringService` connects them on the main thread, so the alert state is only ever touched from one thread. Compose screens handle setup. No backend.

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, JDK 21 (`C:\Program Files\Android\openjdk\jdk-21.0.8`), compileSdk/targetSdk 35, minSdk 29, Jetpack Compose (BOM 2024.12.01) + Material3, DataStore Preferences 1.1.1, kotlinx-serialization-json 1.7.3, Room 2.6.1 via KSP 2.1.0-1.0.29, kotlinx-coroutines 1.9.0, JUnit 4.13.2.

**Spec:** `docs/superpowers/specs/2026-10-12-hearthelp-design.md`

## Global Constraints

- minSdk 29 (Android 10). Package / applicationId `com.hearthelp`.
- Only the 1–5 contacts the user stores in the app are ever texted or called. The app never reads the rest of the address book.
- Defaults: low 45 bpm, high 140 bpm, sustain 10 s, countdown 20 s, snooze 5 min, signal lost 2 min.
- Call chain: a call counts as answered if it was connected for 20 s or more. One pass through the list. Stops on an "OK" reply from a contact or when the user taps Dismiss.
- SMS: retried up to 3 times. Location wait capped at 5 s.
- A lost signal alerts only the user and never cancels an ALARM countdown.
- No backend, no accounts, no analytics.
- Shell: Git Bash. Before every Gradle command, run `export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8"`.

## Review Focus

1. **Off-wrist / no skin contact:** the band sends 0 bpm or clears the contact bit. Expected: ignored, never a LOW alarm. Tests: Task 2 `contactSupportedButNotDetectedIsNull`, Task 3 `zeroBpmIsIgnored`.
2. **Band disconnects during the 20 s countdown:** expected: the countdown keeps running and contacts are still notified. Test: Task 3 `signalLossDoesNotCancelAlarmCountdown`.
3. **A contact replies "OK" from a number saved in a different format** (`+91 98765 43210` vs `09876543210`): expected: recognised, so the calls stop. Test: Task 4 `sameNumberAcrossFormats`.
4. **SOS pressed twice, or during an alert that has already been sent:** expected: only one dispatch. Test: Task 3 `sosWhileDispatchedDoesNothing`.
5. **Limits changed in Settings while monitoring:** expected: the next reading uses the new limits. Test: Task 3 `newSettingsApplyToNextReading`.

---

### Task 0: Hardware check — Fitbit Air heart-rate sharing (manual, gate)

**Files:**
- Create: `docs/hardware-check.md`

Nothing else starts until this passes. It tells us whether the whole approach works.

- [ ] **Step 1:** On the phone, install **nRF Connect for Mobile** (Nordic Semiconductor) from the Play Store.
- [ ] **Step 2:** In the Google Health app, go to Connections → Fitbit Air → **Share heart rate** → Get started. Close the Google Health app.
- [ ] **Step 3:** In nRF Connect, go to Scanner and tap Scan. Find the Fitbit Air. Write down: (a) its name, (b) whether the advertisement lists the service UUID `0x180D` (open the device row and look for "Complete/Incomplete List of 16-bit Service UUIDs").
- [ ] **Step 4:** Tap Connect, open **Heart Rate**, and tap the triple-arrow (notify) icon on **Heart Rate Measurement (0x2A37)**. Check that values arrive about every 2 s.
- [ ] **Step 5:** Leave it connected with the phone screen off for **at least 2 hours**. Note the time, check that values are still arriving, and note the band's battery % at the start and end.
- [ ] **Step 6:** Write the results to `docs/hardware-check.md`:

```markdown
# Fitbit Air hardware check — <date>
- Advertised name: <name>
- 0x180D in advertisement: yes/no
- Notifications on 0x2A37: yes/no, interval ~<n> s
- Still streaming after <h> h: yes/no (if no: stopped after <m> min)
- Band battery: <start>% → <end>% over <h> h
```

- [ ] **Step 7: Gate.** If streaming stopped by itself, **stop and report back to the user**. The design has to be revisited before going further. If `0x180D` is *not* advertised, note it, and in Task 6 drop the `ScanFilter` in `BandScanner` (scan unfiltered). Then commit:

```bash
git add docs/hardware-check.md
git commit -m "docs: record Fitbit Air BLE heart-rate check"
```

---

### Task 1: Toolchain and project skeleton

**Files:**
- Create: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `local.properties` (git-ignored), `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/drawable/ic_heart.xml`, `app/src/main/java/com/hearthelp/ui/MainActivity.kt`, Gradle wrapper files

**Interfaces:**
- Produces: a buildable `:app` module, package `com.hearthelp`, drawable `R.drawable.ic_heart`.

- [ ] **Step 1: Install the Android SDK command-line tools** (the JDK is already installed)

```bash
export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8"
SDK="$LOCALAPPDATA/Android/Sdk"
mkdir -p "$SDK/cmdline-tools"
curl -L -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip
unzip -q /tmp/clt.zip -d "$SDK/cmdline-tools" && mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager.bat" --sdk_root="$SDK" --licenses
"$SDK/cmdline-tools/latest/bin/sdkmanager.bat" --sdk_root="$SDK" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

Expected: `ls "$SDK/platforms"` shows `android-35`.

- [ ] **Step 2: Write `.gitignore`**

```
.gradle/
build/
local.properties
.idea/
*.iml
.kotlin/
```

- [ ] **Step 3: Write `local.properties`**

```
sdk.dir=C\:\\Users\\DELL\\AppData\\Local\\Android\\Sdk
```

- [ ] **Step 4: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "HeartHelp"
include(":app")
```

- [ ] **Step 5: Write `build.gradle.kts` (root) and `gradle.properties`**

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}
```

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 6: Write `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.hearthelp"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.hearthelp"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 7: Write `app/src/main/res/drawable/ic_heart.xml`**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFD32F2F"
        android:pathData="M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"/>
</vector>
```

- [ ] **Step 8: Write `app/src/main/AndroidManifest.xml`.** It declares every permission. Later tasks add components inside `<application>`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
    <uses-feature android:name="android.hardware.telephony" android:required="true" />

    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.SEND_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.READ_CALL_LOG" />
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:label="HeartHelp"
        android:icon="@drawable/ic_heart"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

- [ ] **Step 9: Write a placeholder `app/src/main/java/com/hearthelp/ui/MainActivity.kt`** (Task 10 replaces it)

```kotlin
package com.hearthelp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("HeartHelp") }
    }
}
```

- [ ] **Step 10: Generate the Gradle wrapper**

```bash
export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8"
curl -L -o /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.11.1-bin.zip
unzip -q /tmp/gradle.zip -d /tmp/gradle-dist
/tmp/gradle-dist/gradle-8.11.1/bin/gradle wrapper --gradle-version 8.11.1 --distribution-type bin
```

Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}` exist.

- [ ] **Step 11: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 12: Commit**

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradlew gradlew.bat gradle app
git commit -m "build: Android project skeleton"
```

---

### Task 2: Heart-rate packet parser

**Files:**
- Create: `app/src/main/java/com/hearthelp/ble/HeartRateParser.kt`
- Test: `app/src/test/java/com/hearthelp/ble/HeartRateParserTest.kt`

**Interfaces:**
- Produces: `object HeartRateParser { fun parse(value: ByteArray): Int? }`. It returns bpm, or `null` when the packet is malformed or the band reports no skin contact.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hearthelp.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateParserTest {
    @Test fun eightBitValue() = assertEquals(72, HeartRateParser.parse(byteArrayOf(0x00, 72)))
    @Test fun eightBitValueAbove127IsUnsigned() =
        assertEquals(180, HeartRateParser.parse(byteArrayOf(0x00, 0xB4.toByte())))
    @Test fun sixteenBitValue() =
        assertEquals(300, HeartRateParser.parse(byteArrayOf(0x01, 0x2C, 0x01)))
    @Test fun contactSupportedButNotDetectedIsNull() =
        assertNull(HeartRateParser.parse(byteArrayOf(0x04, 70)))
    @Test fun contactSupportedAndDetected() =
        assertEquals(70, HeartRateParser.parse(byteArrayOf(0x06, 70)))
    @Test fun emptyIsNull() = assertNull(HeartRateParser.parse(byteArrayOf()))
    @Test fun truncatedSixteenBitIsNull() = assertNull(HeartRateParser.parse(byteArrayOf(0x01, 0x2C)))
}
```

- [ ] **Step 2: Run it and check that it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hearthelp.ble.HeartRateParserTest"`
Expected: compilation FAILS with `Unresolved reference 'HeartRateParser'`.

- [ ] **Step 3: Implement**

```kotlin
package com.hearthelp.ble

/** Parses the Bluetooth Heart Rate Measurement characteristic (0x2A37). */
object HeartRateParser {
    fun parse(value: ByteArray): Int? {
        if (value.isEmpty()) return null
        val flags = value[0].toInt()
        val contactSupported = (flags and 0x04) != 0
        val contactDetected = (flags and 0x02) != 0
        if (contactSupported && !contactDetected) return null
        return if ((flags and 0x01) == 0) {
            if (value.size < 2) null else value[1].toInt() and 0xFF
        } else {
            if (value.size < 3) null
            else (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        }
    }
}
```

- [ ] **Step 4: Run it and check that it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hearthelp.ble.HeartRateParserTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hearthelp/ble/HeartRateParser.kt app/src/test/java/com/hearthelp/ble/HeartRateParserTest.kt
git commit -m "feat: parse BLE heart-rate measurements"
```

---

### Task 3: Alert settings, states and evaluator

**Files:**
- Create: `app/src/main/java/com/hearthelp/domain/AlertSettings.kt`, `app/src/main/java/com/hearthelp/domain/AlertState.kt`, `app/src/main/java/com/hearthelp/domain/AlertEvaluator.kt`
- Test: `app/src/test/java/com/hearthelp/domain/AlertEvaluatorTest.kt`

**Interfaces:**
- Produces:
  - `data class AlertSettings(lowBpm=45, highBpm=140, sustainSeconds=10, countdownSeconds=20, snoozeMinutes=5, signalLostMinutes=2)`. The constructor throws `IllegalArgumentException` with a user-readable message if a value is invalid.
  - `enum class Trigger { HIGH, LOW, SOS, TEST }`
  - `sealed interface AlertState` with `Normal`, `BreachPending(trigger, since: Long)`, `Alarm(trigger, bpm: Int?, deadline: Long)`, `Dispatched(trigger, bpm: Int?)`, `Snoozed(until: Long)`, `SignalLost`
  - `class AlertEvaluator(settings: AlertSettings, startedAt: Long)` with `var settings`, `val state`, `val lastBpm: Int?`, and methods that all return the new `AlertState`: `onReading(bpm: Int, now: Long)`, `onTick(now: Long)`, `onImOk(now: Long)`, `onSos()`, `onDismiss()`, `startTest(now: Long)`. Times are epoch milliseconds.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hearthelp.domain

import com.hearthelp.domain.AlertState.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {
    private val defaults = AlertSettings()
    private fun ev(s: AlertSettings = defaults) = AlertEvaluator(s, startedAt = 0)

    /** Feeds one reading every 2 s from [fromSec] to [toSec] inclusive. */
    private fun AlertEvaluator.feed(bpm: Int, fromSec: Int, toSec: Int) {
        for (t in fromSec..toSec step 2) onReading(bpm, t * 1000L)
    }

    private fun alarmAtTenSeconds(): AlertEvaluator = ev().apply { feed(160, 0, 10) }

    @Test fun inRangeStaysNormal() {
        val e = ev(); e.feed(80, 0, 30)
        assertEquals(Normal, e.state)
    }

    @Test fun singleHighReadingIsPendingThenResets() {
        val e = ev()
        assertEquals(BreachPending(Trigger.HIGH, 0), e.onReading(160, 0))
        assertEquals(Normal, e.onReading(80, 2000))
    }

    @Test fun sustainedHighForTenSecondsRaisesAlarm() {
        val e = ev(); e.feed(160, 0, 8)
        assertTrue(e.state is BreachPending)
        assertEquals(Alarm(Trigger.HIGH, 160, deadline = 30_000), e.onReading(160, 10_000))
    }

    @Test fun glitchInsideWindowRestartsTimer() {
        val e = ev()
        e.feed(160, 0, 6); e.onReading(80, 8000); e.feed(160, 10, 18)
        assertEquals(BreachPending(Trigger.HIGH, 10_000), e.state)
        assertTrue(e.onReading(160, 20_000) is Alarm)
    }

    @Test fun switchingHighToLowRestartsTimer() {
        val e = ev(); e.feed(160, 0, 6)
        assertEquals(BreachPending(Trigger.LOW, 8000), e.onReading(40, 8000))
    }

    @Test fun sustainedLowRaisesAlarm() {
        val e = ev(); e.feed(40, 0, 10)
        assertEquals(Alarm(Trigger.LOW, 40, deadline = 30_000), e.state)
    }

    @Test fun zeroBpmIsIgnored() {
        val e = ev(); e.feed(0, 0, 30)
        assertEquals(Normal, e.state)
        assertNull(e.lastBpm)
    }

    @Test fun countdownExpiryDispatches() {
        val e = alarmAtTenSeconds()
        assertTrue(e.onTick(29_999) is Alarm)
        assertEquals(Dispatched(Trigger.HIGH, 160), e.onTick(30_000))
    }

    @Test fun imOkSnoozesAndIgnoresBreachesUntilExpiry() {
        val e = alarmAtTenSeconds()
        assertEquals(Snoozed(until = 315_000), e.onImOk(15_000))
        e.feed(160, 16, 300)
        assertTrue(e.state is Snoozed)
        assertEquals(Normal, e.onTick(315_000))
    }

    @Test fun signalLostAfterTwoMinutesWithoutReadings() {
        val e = ev(); e.onReading(80, 0)
        assertEquals(Normal, e.onTick(119_999))
        assertEquals(SignalLost, e.onTick(120_000))
        assertEquals(Normal, e.onReading(80, 121_000))
    }

    @Test fun signalLossDoesNotCancelAlarmCountdown() {
        val e = ev(defaults.copy(countdownSeconds = 300, signalLostMinutes = 1))
        e.feed(160, 0, 10)
        assertTrue(e.onTick(100_000) is Alarm)
        assertEquals(Dispatched(Trigger.HIGH, 160), e.onTick(310_000))
    }

    @Test fun sosDispatchesImmediatelyWithLastBpm() {
        val e = ev(); e.onReading(80, 0)
        assertEquals(Dispatched(Trigger.SOS, 80), e.onSos())
    }

    @Test fun sosWhileDispatchedDoesNothing() {
        val e = alarmAtTenSeconds(); e.onTick(30_000)
        assertEquals(Dispatched(Trigger.HIGH, 160), e.onSos())
    }

    @Test fun dismissReturnsToNormal() {
        val e = ev(); e.onSos()
        assertEquals(Normal, e.onDismiss())
    }

    @Test fun dismissOutsideDispatchedDoesNothing() {
        val e = alarmAtTenSeconds()
        assertTrue(e.onDismiss() is Alarm)
    }

    @Test fun testAlarmDispatchesAsTest() {
        val e = ev(); e.onReading(75, 0)
        assertEquals(Alarm(Trigger.TEST, 75, deadline = 21_000), e.startTest(1000))
        assertEquals(Dispatched(Trigger.TEST, 75), e.onTick(21_000))
    }

    @Test fun imOkOnTestAlarmDoesNotSnooze() {
        val e = ev(); e.startTest(0)
        assertEquals(Normal, e.onImOk(1000))
    }

    @Test fun newSettingsApplyToNextReading() {
        val e = ev()
        e.settings = defaults.copy(highBpm = 100)
        assertEquals(BreachPending(Trigger.HIGH, 0), e.onReading(110, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun lowMustBeBelowHigh() { AlertSettings(lowBpm = 100, highBpm = 90) }
}
```

- [ ] **Step 2: Run it and check that it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hearthelp.domain.AlertEvaluatorTest"`
Expected: compilation FAILS with unresolved `AlertSettings` / `AlertEvaluator`.

- [ ] **Step 3: Implement `AlertSettings.kt`**

```kotlin
package com.hearthelp.domain

data class AlertSettings(
    val lowBpm: Int = 45,
    val highBpm: Int = 140,
    val sustainSeconds: Int = 10,
    val countdownSeconds: Int = 20,
    val snoozeMinutes: Int = 5,
    val signalLostMinutes: Int = 2,
) {
    init {
        require(lowBpm in 20..120) { "Low limit must be between 20 and 120 bpm" }
        require(highBpm in 60..250) { "High limit must be between 60 and 250 bpm" }
        require(lowBpm < highBpm) { "Low limit must be below the high limit" }
        require(sustainSeconds in 2..300) { "Sustain time must be 2–300 seconds" }
        require(countdownSeconds in 5..300) { "Countdown must be 5–300 seconds" }
        require(snoozeMinutes in 1..60) { "Pause after I'm OK must be 1–60 minutes" }
        require(signalLostMinutes in 1..60) { "Signal-lost time must be 1–60 minutes" }
    }
}
```

- [ ] **Step 4: Implement `AlertState.kt`**

```kotlin
package com.hearthelp.domain

enum class Trigger { HIGH, LOW, SOS, TEST }

sealed interface AlertState {
    data object Normal : AlertState
    data class BreachPending(val trigger: Trigger, val since: Long) : AlertState
    data class Alarm(val trigger: Trigger, val bpm: Int?, val deadline: Long) : AlertState
    data class Dispatched(val trigger: Trigger, val bpm: Int?) : AlertState
    data class Snoozed(val until: Long) : AlertState
    data object SignalLost : AlertState
}
```

- [ ] **Step 5: Implement `AlertEvaluator.kt`**

```kotlin
package com.hearthelp.domain

import com.hearthelp.domain.AlertState.*

/** Pure alert state machine. Not thread-safe: call from one thread only. */
class AlertEvaluator(var settings: AlertSettings, startedAt: Long) {
    var state: AlertState = Normal
        private set
    var lastBpm: Int? = null
        private set
    private var lastReadingAt = startedAt

    fun onReading(bpm: Int, now: Long): AlertState {
        if (bpm <= 0) return state
        lastReadingAt = now
        lastBpm = bpm
        val s = state
        state = when (s) {
            is Alarm, is Dispatched, is Snoozed -> s
            Normal, SignalLost -> breachOf(bpm)?.let { BreachPending(it, now) } ?: Normal
            is BreachPending -> {
                val t = breachOf(bpm)
                when {
                    t == null -> Normal
                    t != s.trigger -> BreachPending(t, now)
                    now - s.since >= settings.sustainSeconds * 1000L ->
                        Alarm(t, bpm, now + settings.countdownSeconds * 1000L)
                    else -> s
                }
            }
        }
        return state
    }

    fun onTick(now: Long): AlertState {
        val s = state
        state = when {
            s is Alarm && now >= s.deadline -> Dispatched(s.trigger, s.bpm)
            s is Alarm || s is Dispatched -> s
            now - lastReadingAt >= settings.signalLostMinutes * 60_000L -> SignalLost
            s is Snoozed && now >= s.until -> Normal
            else -> s
        }
        return state
    }

    fun onImOk(now: Long): AlertState {
        val s = state
        if (s is Alarm) {
            state = if (s.trigger == Trigger.TEST) Normal
            else Snoozed(now + settings.snoozeMinutes * 60_000L)
        }
        return state
    }

    fun onSos(): AlertState {
        if (state !is Dispatched) state = Dispatched(Trigger.SOS, lastBpm)
        return state
    }

    fun onDismiss(): AlertState {
        if (state is Dispatched) state = Normal
        return state
    }

    fun startTest(now: Long): AlertState {
        if (state !is Alarm && state !is Dispatched) {
            state = Alarm(Trigger.TEST, lastBpm, now + settings.countdownSeconds * 1000L)
        }
        return state
    }

    private fun breachOf(bpm: Int): Trigger? = when {
        bpm < settings.lowBpm -> Trigger.LOW
        bpm > settings.highBpm -> Trigger.HIGH
        else -> null
    }
}
```

- [ ] **Step 6: Run it and check that it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hearthelp.domain.AlertEvaluatorTest"`
Expected: PASS (19 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hearthelp/domain app/src/test/java/com/hearthelp/domain/AlertEvaluatorTest.kt
git commit -m "feat: alert state machine with sustain, countdown, snooze, SOS and signal loss"
```

---

### Task 4: Emergency contacts and phone-number matching

**Files:**
- Create: `app/src/main/java/com/hearthelp/domain/PhoneNumbers.kt`, `app/src/main/java/com/hearthelp/domain/EmergencyContact.kt`
- Test: `app/src/test/java/com/hearthelp/domain/EmergencyContactsTest.kt`

**Interfaces:**
- Produces:
  - `object PhoneNumbers { fun same(a: String, b: String): Boolean }`. Compares the last 10 digits (or fewer if a number is shorter). Numbers with fewer than 7 digits never match.
  - `@Serializable data class EmergencyContact(val name: String, val number: String)`
  - `object EmergencyContacts { const val MAX = 5; fun add(list, c): List<EmergencyContact>` (throws `IllegalArgumentException` with a user-readable message)`; fun remove(list, index); fun moveUp(list, index) }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hearthelp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyContactsTest {
    private val a = EmergencyContact("A", "9876543210")
    private val b = EmergencyContact("B", "9123456780")

    @Test fun sameNumberAcrossFormats() {
        assertTrue(PhoneNumbers.same("+91 98765 43210", "09876543210"))
        assertTrue(PhoneNumbers.same("+1 (555) 123-4567", "5551234567"))
    }
    @Test fun differentNumbers() = assertFalse(PhoneNumbers.same("9876543210", "9876543211"))
    @Test fun tooShortNeverMatches() = assertFalse(PhoneNumbers.same("123", "123"))

    @Test fun addAppends() = assertEquals(listOf(a, b), EmergencyContacts.add(listOf(a), b))

    @Test fun blankNameFallsBackToNumber() =
        assertEquals(listOf(EmergencyContact("9876543210", "9876543210")),
            EmergencyContacts.add(emptyList(), EmergencyContact("  ", "9876543210")))

    @Test(expected = IllegalArgumentException::class)
    fun sixthContactRejected() {
        val five = (0 until 5).map { EmergencyContact("C$it", "98765432${it}0") }
        EmergencyContacts.add(five, EmergencyContact("X", "9000000000"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidNumberRejected() { EmergencyContacts.add(emptyList(), EmergencyContact("X", "12ab")) }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateInOtherFormatRejected() {
        EmergencyContacts.add(listOf(a), EmergencyContact("A2", "+91 98765 43210"))
    }

    @Test fun moveUpSwaps() = assertEquals(listOf(b, a), EmergencyContacts.moveUp(listOf(a, b), 1))
    @Test fun moveUpFirstUnchanged() = assertEquals(listOf(a, b), EmergencyContacts.moveUp(listOf(a, b), 0))
    @Test fun removeByIndex() = assertEquals(listOf(b), EmergencyContacts.remove(listOf(a, b), 0))
}
```

- [ ] **Step 2: Run it and check that it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hearthelp.domain.EmergencyContactsTest"`
Expected: compilation FAILS with unresolved `PhoneNumbers` / `EmergencyContact`.

- [ ] **Step 3: Implement `PhoneNumbers.kt`**

```kotlin
package com.hearthelp.domain

object PhoneNumbers {
    fun digits(number: String): String = number.filter(Char::isDigit)

    /** True when both numbers end in the same (up to) 10 digits, so "+91 98765 43210" matches "09876543210". */
    fun same(a: String, b: String): Boolean {
        val x = digits(a)
        val y = digits(b)
        if (x.length < 7 || y.length < 7) return false
        val k = minOf(x.length, y.length, 10)
        return x.takeLast(k) == y.takeLast(k)
    }
}
```

- [ ] **Step 4: Implement `EmergencyContact.kt`**

```kotlin
package com.hearthelp.domain

import kotlinx.serialization.Serializable

@Serializable
data class EmergencyContact(val name: String, val number: String)

object EmergencyContacts {
    const val MAX = 5

    fun add(list: List<EmergencyContact>, contact: EmergencyContact): List<EmergencyContact> {
        val number = contact.number.trim()
        require(list.size < MAX) { "You can add up to $MAX contacts" }
        require(PhoneNumbers.digits(number).length >= 7 && number.all { it.isDigit() || it in "+-() " }) {
            "Enter a valid phone number"
        }
        require(list.none { PhoneNumbers.same(it.number, number) }) { "That number is already in your list" }
        return list + EmergencyContact(contact.name.trim().ifBlank { number }, number)
    }

    fun remove(list: List<EmergencyContact>, index: Int): List<EmergencyContact> =
        list.filterIndexed { i, _ -> i != index }

    fun moveUp(list: List<EmergencyContact>, index: Int): List<EmergencyContact> {
        if (index <= 0 || index >= list.size) return list
        return list.toMutableList().apply { add(index - 1, removeAt(index)) }
    }
}
```

- [ ] **Step 5: Run it and check that it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hearthelp.domain.EmergencyContactsTest"`
Expected: PASS (11 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hearthelp/domain/PhoneNumbers.kt app/src/main/java/com/hearthelp/domain/EmergencyContact.kt app/src/test/java/com/hearthelp/domain/EmergencyContactsTest.kt
git commit -m "feat: emergency contact list rules and phone-number matching"
```

---

### Task 5: Alert message, OK-reply detection and call chain

**Files:**
- Create: `app/src/main/java/com/hearthelp/domain/AlertMessage.kt`, `app/src/main/java/com/hearthelp/domain/SmsReplies.kt`, `app/src/main/java/com/hearthelp/domain/CallChain.kt`
- Test: `app/src/test/java/com/hearthelp/domain/DispatchLogicTest.kt`

**Interfaces:**
- Consumes: `Trigger` (Task 3).
- Produces:
  - `object AlertMessage { fun build(name: String, trigger: Trigger, bpm: Int?, time: String, lat: Double?, lng: Double?): String }`
  - `object SmsReplies { fun isOk(body: String): Boolean }`
  - `class CallChain(numbers: List<String>, answeredSeconds: Int = 20)` with `sealed interface Step { data class Call(val index: Int, val number: String); data object Done }`, `fun start(): Step`, `fun onCallEnded(connectedSeconds: Int): Step`, `fun stop()`, `val isStopped: Boolean`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hearthelp.domain

import com.hearthelp.domain.CallChain.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchLogicTest {
    // --- AlertMessage
    @Test fun highWithLocation() = assertEquals(
        "HeartHelp ALERT: Alex's heart rate is HIGH (152 bpm) at 14:32. " +
            "Location: https://maps.google.com/?q=12.97161,77.59456 Reply OK if you're handling this.",
        AlertMessage.build("Alex", Trigger.HIGH, 152, "14:32", 12.971612, 77.594561))

    @Test fun lowWithoutLocation() = assertEquals(
        "HeartHelp ALERT: Alex's heart rate is LOW (38 bpm) at 03:05. " +
            "Location unavailable. Reply OK if you're handling this.",
        AlertMessage.build("Alex", Trigger.LOW, 38, "03:05", null, null))

    @Test fun sosWithoutBpm() = assertEquals(
        "HeartHelp ALERT: Alex pressed the SOS button at 09:00. " +
            "Location unavailable. Reply OK if you're handling this.",
        AlertMessage.build("Alex", Trigger.SOS, null, "09:00", null, null))

    @Test fun testMessage() = assertEquals(
        "HeartHelp ALERT: TEST from Alex's HeartHelp app (75 bpm) at 10:00. " +
            "Location unavailable. No action needed.",
        AlertMessage.build("Alex", Trigger.TEST, 75, "10:00", null, null))

    @Test fun blankNameUsesFallback() = assertTrue(
        AlertMessage.build(" ", Trigger.HIGH, 150, "10:00", null, null).contains("Your contact's heart rate"))

    // --- SmsReplies
    @Test fun okVariantsAccepted() {
        listOf("OK", "ok on my way", "Okay", "OK.", "calling you, ok").forEach { assertTrue(it, SmsReplies.isOk(it)) }
    }
    @Test fun nonOkRejected() {
        listOf("book a taxi", "No", "", "token").forEach { assertFalse(it, SmsReplies.isOk(it)) }
    }

    // --- CallChain
    private val nums = listOf("a", "b", "c")

    @Test fun startsWithFirst() = assertEquals(Step.Call(0, "a"), CallChain(nums).start())

    @Test fun unansweredMovesOn() {
        val c = CallChain(nums); c.start()
        assertEquals(Step.Call(1, "b"), c.onCallEnded(0))
    }
    @Test fun shortPickupMovesOn() {
        val c = CallChain(nums); c.start()
        assertEquals(Step.Call(1, "b"), c.onCallEnded(19))
    }
    @Test fun answeredStops() {
        val c = CallChain(nums); c.start()
        assertEquals(Step.Done, c.onCallEnded(20))
        assertTrue(c.isStopped)
    }
    @Test fun exhaustedAfterLast() {
        val c = CallChain(nums); c.start(); c.onCallEnded(0); c.onCallEnded(0)
        assertEquals(Step.Done, c.onCallEnded(0))
    }
    @Test fun stopDuringCallEndsChain() {
        val c = CallChain(nums); c.start(); c.stop()
        assertEquals(Step.Done, c.onCallEnded(0))
    }
    @Test fun emptyListIsDone() = assertEquals(Step.Done, CallChain(emptyList()).start())
}
```

- [ ] **Step 2: Run it and check that it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hearthelp.domain.DispatchLogicTest"`
Expected: compilation FAILS with unresolved `AlertMessage` / `SmsReplies` / `CallChain`.

- [ ] **Step 3: Implement `AlertMessage.kt`**

```kotlin
package com.hearthelp.domain

import java.util.Locale

object AlertMessage {
    fun build(name: String, trigger: Trigger, bpm: Int?, time: String, lat: Double?, lng: Double?): String {
        val who = name.trim().ifBlank { "Your contact" }
        val what = when (trigger) {
            Trigger.HIGH -> "$who's heart rate is HIGH"
            Trigger.LOW -> "$who's heart rate is LOW"
            Trigger.SOS -> "$who pressed the SOS button"
            Trigger.TEST -> "TEST from $who's HeartHelp app"
        }
        val bpmPart = bpm?.let { " ($it bpm)" } ?: ""
        val location = if (lat != null && lng != null) {
            " Location: https://maps.google.com/?q=" + String.format(Locale.US, "%.5f,%.5f", lat, lng)
        } else " Location unavailable."
        val ask = if (trigger == Trigger.TEST) " No action needed." else " Reply OK if you're handling this."
        return "HeartHelp ALERT: $what$bpmPart at $time.$location$ask"
    }
}
```

- [ ] **Step 4: Implement `SmsReplies.kt`**

```kotlin
package com.hearthelp.domain

object SmsReplies {
    private val ok = Regex("""\b(ok|okay)\b""", RegexOption.IGNORE_CASE)
    fun isOk(body: String): Boolean = ok.containsMatchIn(body)
}
```

- [ ] **Step 5: Implement `CallChain.kt`**

```kotlin
package com.hearthelp.domain

/** Decides who to call next. One pass through [numbers]; stops on an answered call or [stop]. */
class CallChain(private val numbers: List<String>, private val answeredSeconds: Int = 20) {
    sealed interface Step {
        data class Call(val index: Int, val number: String) : Step
        data object Done : Step
    }

    private var index = 0
    var isStopped = false
        private set

    fun start(): Step {
        index = 0
        return current()
    }

    fun onCallEnded(connectedSeconds: Int): Step {
        if (connectedSeconds >= answeredSeconds) isStopped = true
        index++
        return current()
    }

    fun stop() { isStopped = true }

    private fun current(): Step {
        if (isStopped || index >= numbers.size) {
            isStopped = true
            return Step.Done
        }
        return Step.Call(index, numbers[index])
    }
}
```

- [ ] **Step 6: Run it and check that it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hearthelp.domain.DispatchLogicTest"`
Expected: PASS (14 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hearthelp/domain/AlertMessage.kt app/src/main/java/com/hearthelp/domain/SmsReplies.kt app/src/main/java/com/hearthelp/domain/CallChain.kt app/src/test/java/com/hearthelp/domain/DispatchLogicTest.kt
git commit -m "feat: alert SMS text, OK-reply detection and call-chain decisions"
```

---

### Task 6: Storage, application class and BLE adapters

**Files:**
- Create: `app/src/main/java/com/hearthelp/data/AppPrefs.kt`, `app/src/main/java/com/hearthelp/data/EventLog.kt`, `app/src/main/java/com/hearthelp/HeartHelpApp.kt`, `app/src/main/java/com/hearthelp/ble/HeartRateSource.kt`, `app/src/main/java/com/hearthelp/ble/BandScanner.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`android:name` on `<application>`)

**Interfaces:**
- Consumes: `AlertSettings`, `EmergencyContact`, `HeartRateParser`.
- Produces:
  - `data class Profile(val name: String = "", val ownNumber: String = "")`
  - `class AppPrefs(context)` with flows `settings: Flow<AlertSettings>`, `contacts: Flow<List<EmergencyContact>>`, `profile: Flow<Profile>`, `bandAddress: Flow<String?>`, `bandName: Flow<String?>`, `monitoringEnabled: Flow<Boolean>`, and suspend setters `saveSettings`, `saveContacts`, `saveProfile`, `saveBand(address, name)`, `setMonitoringEnabled`.
  - `data class EventEntry(id, time, type, detail)`, `class EventLog(dao)` with `suspend fun add(type: String, detail: String)` and `val recent: Flow<List<EventEntry>>`.
  - `class HeartHelpApp : Application` with `prefs: AppPrefs` and `log: EventLog`. `val Context.app: HeartHelpApp`.
  - `class HeartRateSource(ctx) { fun readings(address: String): Flow<Int>; companion HR_SERVICE: UUID }`
  - `class BandScanner(ctx) { data class Found(name, address); fun scan(): Flow<Found> }`

These are thin adapters with no logic of their own to test. The proof for this task is that it compiles, plus Task 11's on-device run.

- [ ] **Step 1: Write `data/AppPrefs.kt`**

```kotlin
package com.hearthelp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.hearthelp.domain.AlertSettings
import com.hearthelp.domain.EmergencyContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "hearthelp")

data class Profile(val name: String = "", val ownNumber: String = "")

class AppPrefs(context: Context) {
    private val store = context.applicationContext.dataStore

    private object K {
        val low = intPreferencesKey("low_bpm")
        val high = intPreferencesKey("high_bpm")
        val sustain = intPreferencesKey("sustain_s")
        val countdown = intPreferencesKey("countdown_s")
        val snooze = intPreferencesKey("snooze_min")
        val signalLost = intPreferencesKey("signal_lost_min")
        val contacts = stringPreferencesKey("contacts")
        val name = stringPreferencesKey("name")
        val ownNumber = stringPreferencesKey("own_number")
        val band = stringPreferencesKey("band_address")
        val bandName = stringPreferencesKey("band_name")
        val monitoring = booleanPreferencesKey("monitoring")
    }

    val settings: Flow<AlertSettings> = store.data.map { p ->
        val d = AlertSettings()
        runCatching {
            AlertSettings(
                p[K.low] ?: d.lowBpm, p[K.high] ?: d.highBpm, p[K.sustain] ?: d.sustainSeconds,
                p[K.countdown] ?: d.countdownSeconds, p[K.snooze] ?: d.snoozeMinutes,
                p[K.signalLost] ?: d.signalLostMinutes,
            )
        }.getOrDefault(d)
    }.distinctUntilChanged()

    suspend fun saveSettings(s: AlertSettings) {
        store.edit {
            it[K.low] = s.lowBpm; it[K.high] = s.highBpm; it[K.sustain] = s.sustainSeconds
            it[K.countdown] = s.countdownSeconds; it[K.snooze] = s.snoozeMinutes
            it[K.signalLost] = s.signalLostMinutes
        }
    }

    val contacts: Flow<List<EmergencyContact>> = store.data.map { p ->
        p[K.contacts]?.let { runCatching { Json.decodeFromString<List<EmergencyContact>>(it) }.getOrNull() }
            ?: emptyList()
    }.distinctUntilChanged()

    suspend fun saveContacts(list: List<EmergencyContact>) {
        store.edit { it[K.contacts] = Json.encodeToString(list) }
    }

    val profile: Flow<Profile> = store.data.map { Profile(it[K.name] ?: "", it[K.ownNumber] ?: "") }
        .distinctUntilChanged()

    suspend fun saveProfile(p: Profile) {
        store.edit { it[K.name] = p.name.trim(); it[K.ownNumber] = p.ownNumber.trim() }
    }

    val bandAddress: Flow<String?> = store.data.map { it[K.band] }.distinctUntilChanged()
    val bandName: Flow<String?> = store.data.map { it[K.bandName] }.distinctUntilChanged()

    suspend fun saveBand(address: String, name: String) {
        store.edit { it[K.band] = address; it[K.bandName] = name }
    }

    val monitoringEnabled: Flow<Boolean> = store.data.map { it[K.monitoring] ?: false }.distinctUntilChanged()

    suspend fun setMonitoringEnabled(on: Boolean) {
        store.edit { it[K.monitoring] = on }
    }
}
```

- [ ] **Step 2: Write `data/EventLog.kt`**

```kotlin
package com.hearthelp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "events")
data class EventEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val type: String,
    val detail: String,
)

@Dao
interface EventDao {
    @Insert suspend fun insert(e: EventEntry)
    @Query("SELECT * FROM events ORDER BY time DESC, id DESC LIMIT 300")
    fun recent(): Flow<List<EventEntry>>
}

@Database(entities = [EventEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun events(): EventDao
}

class EventLog(private val dao: EventDao) {
    val recent: Flow<List<EventEntry>> = dao.recent()
    suspend fun add(type: String, detail: String) =
        dao.insert(EventEntry(time = System.currentTimeMillis(), type = type, detail = detail))
}
```

- [ ] **Step 3: Write `HeartHelpApp.kt`** (`Notifications.createChannels` is added in Task 8, so leave that line out for now)

```kotlin
package com.hearthelp

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.hearthelp.data.AppDatabase
import com.hearthelp.data.AppPrefs
import com.hearthelp.data.EventLog

class HeartHelpApp : Application() {
    lateinit var prefs: AppPrefs
        private set
    lateinit var log: EventLog
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        log = EventLog(Room.databaseBuilder(this, AppDatabase::class.java, "hearthelp.db").build().events())
    }
}

val Context.app: HeartHelpApp get() = applicationContext as HeartHelpApp
```

In the manifest, change `<application` to `<application android:name=".HeartHelpApp"` (keep the other attributes).

- [ ] **Step 4: Write `ble/HeartRateSource.kt`**

```kotlin
package com.hearthelp.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/** Streams bpm from a BLE Heart Rate Profile device. autoConnect=true makes Android reconnect after drops. */
class HeartRateSource(private val ctx: Context) {
    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    @SuppressLint("MissingPermission")
    fun readings(address: String): Flow<Int> = callbackFlow {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            close(IllegalStateException("Bluetooth is off"))
            return@callbackFlow
        }
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val ch = gatt.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT) ?: return
                gatt.setCharacteristicNotification(ch, true)
                val d = ch.getDescriptor(CCCD) ?: return
                if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(d)
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
                HeartRateParser.parse(value)?.let { trySend(it) }
            }

            @Deprecated("Pre-API 33 callback")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < 33) HeartRateParser.parse(ch.value ?: return)?.let { trySend(it) }
            }
        }
        val gatt = adapter.getRemoteDevice(address).connectGatt(ctx, true, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) close(IllegalStateException("Could not connect to band"))
        awaitClose { gatt?.close() }
    }
}
```

- [ ] **Step 5: Write `ble/BandScanner.kt`.** If Task 0 found that `0x180D` isn't advertised, pass `null` instead of `listOf(filter)`.

```kotlin
package com.hearthelp.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BandScanner(private val ctx: Context) {
    data class Found(val name: String, val address: String)

    @SuppressLint("MissingPermission")
    fun scan(): Flow<Found> = callbackFlow {
        val scanner = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth is off"))
            return@callbackFlow
        }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                trySend(Found(r.device.name ?: r.scanRecord?.deviceName ?: "Unknown device", r.device.address))
            }
            override fun onScanFailed(errorCode: Int) { close(IllegalStateException("Scan failed ($errorCode)")) }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HeartRateSource.HR_SERVICE)).build()
        scanner.startScan(listOf(filter), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
        awaitClose { scanner.stopScan(cb) }
    }
}
```

- [ ] **Step 6: Build and run all unit tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, and all earlier tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main
git commit -m "feat: settings/contacts storage, event log, BLE heart-rate source and scanner"
```

---

### Task 7: Emergency dispatch adapters (SMS, location, calls, replies)

**Files:**
- Create: `app/src/main/java/com/hearthelp/emergency/SmsSender.kt`, `.../emergency/LocationFetcher.kt`, `.../emergency/CallPlacer.kt`, `.../emergency/SmsReplyReceiver.kt`, `.../emergency/EmergencyDispatcher.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `AppPrefs.profile`, `AppPrefs.contacts`, `EventLog.add`, `AlertMessage`, `SmsReplies`, `PhoneNumbers`, `CallChain`, `EmergencyContact`, `Trigger`.
- Produces:
  - `class EmergencyDispatcher(ctx, prefs: AppPrefs, log: EventLog) { suspend fun dispatch(trigger: Trigger, bpm: Int?) }`. Cancelling the calling coroutine stops the chain (that's how Dismiss works). `Trigger.TEST` goes only to `Profile.ownNumber`.
  - `object ReplyBus { val replies: MutableSharedFlow<SmsReply> }`, `data class SmsReply(from: String, body: String)`

The decisions inside these were tested in Task 5. These classes only wrap Android APIs, so the proof is that they compile and work in the Task 11 device test.

- [ ] **Step 1: Write `emergency/SmsSender.kt`**

```kotlin
package com.hearthelp.emergency

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

class SmsSender(private val ctx: Context) {
    /** Sends [text], retrying up to 3 times. Returns true once the carrier accepts every part. */
    suspend fun send(number: String, text: String): Boolean {
        repeat(3) { attempt ->
            if (sendOnce(number, text)) return true
            if (attempt < 2) delay(5_000)
        }
        return false
    }

    private suspend fun sendOnce(number: String, text: String): Boolean = withTimeoutOrNull(30_000) {
        suspendCancellableCoroutine<Boolean> { cont ->
            val action = "com.hearthelp.SMS_SENT." + UUID.randomUUID()
            val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sms.divideMessage(text)
            var remaining = parts.size
            var ok = true
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (resultCode != Activity.RESULT_OK) ok = false
                    if (--remaining == 0) {
                        runCatching { ctx.unregisterReceiver(this) }
                        if (cont.isActive) cont.resume(ok)
                    }
                }
            }
            ContextCompat.registerReceiver(ctx, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            cont.invokeOnCancellation { runCatching { ctx.unregisterReceiver(receiver) } }
            val sentIntents = ArrayList(parts.indices.map {
                PendingIntent.getBroadcast(ctx, it, Intent(action).setPackage(ctx.packageName), PendingIntent.FLAG_IMMUTABLE)
            })
            runCatching { sms.sendMultipartTextMessage(number, null, parts, sentIntents, null) }.onFailure {
                runCatching { ctx.unregisterReceiver(receiver) }
                if (cont.isActive) cont.resume(false)
            }
        }
    } ?: false
}
```

- [ ] **Step 2: Write `emergency/LocationFetcher.kt`**

```kotlin
package com.hearthelp.emergency

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationFetcher(private val ctx: Context) {
    /** Fresh fix within 5 s if possible, otherwise the newest last-known fix, otherwise null. */
    @SuppressLint("MissingPermission")
    suspend fun current(): Location? {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER); add(LocationManager.NETWORK_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val fresh = if (Build.VERSION.SDK_INT >= 30 && providers.isNotEmpty()) {
            withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(providers.first(), signal, ctx.mainExecutor) { cont.resume(it) }
                }
            }
        } else null

        return fresh ?: lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}
```

- [ ] **Step 3: Write `emergency/CallPlacer.kt`**

```kotlin
package com.hearthelp.emergency

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager
import com.hearthelp.domain.PhoneNumbers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class CallPlacer(private val ctx: Context) {
    private val telecom = ctx.getSystemService(TelecomManager::class.java)

    /** Places a call, waits for it to end, returns seconds it was connected (0 = not answered). */
    @SuppressLint("MissingPermission")
    suspend fun callAndMeasure(number: String): Int {
        val startedAt = System.currentTimeMillis()
        runCatching { telecom.placeCall(Uri.fromParts("tel", number, null), Bundle()) }.onFailure { return 0 }
        withTimeoutOrNull(20_000) { while (!telecom.isInCall) delay(500) } ?: return 0
        withTimeoutOrNull(30 * 60_000L) { while (telecom.isInCall) delay(1_000) }
        delay(2_000) // give the system time to write the call-log entry
        return connectedSeconds(number, startedAt)
    }

    @SuppressLint("MissingPermission")
    private fun connectedSeconds(number: String, since: Long): Int {
        val cursor = runCatching {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?",
                arrayOf(CallLog.Calls.OUTGOING_TYPE.toString(), (since - 5_000).toString()),
                "${CallLog.Calls.DATE} DESC",
            )
        }.getOrNull() ?: return 0
        cursor.use {
            while (it.moveToNext()) {
                if (PhoneNumbers.same(it.getString(0) ?: "", number)) return it.getInt(1)
            }
        }
        return 0
    }
}
```

- [ ] **Step 4: Write `emergency/SmsReplyReceiver.kt`**

```kotlin
package com.hearthelp.emergency

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.flow.MutableSharedFlow

data class SmsReply(val from: String, val body: String)

object ReplyBus {
    val replies = MutableSharedFlow<SmsReply>(extraBufferCapacity = 16)
}

class SmsReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val from = messages.firstOrNull()?.originatingAddress ?: return
        ReplyBus.replies.tryEmit(SmsReply(from, messages.joinToString("") { it.messageBody ?: "" }))
    }
}
```

- [ ] **Step 5: Write `emergency/EmergencyDispatcher.kt`**

```kotlin
package com.hearthelp.emergency

import android.content.Context
import com.hearthelp.data.AppPrefs
import com.hearthelp.data.EventLog
import com.hearthelp.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmergencyDispatcher(
    ctx: Context,
    private val prefs: AppPrefs,
    private val log: EventLog,
    private val sms: SmsSender = SmsSender(ctx),
    private val location: LocationFetcher = LocationFetcher(ctx),
    private val calls: CallPlacer = CallPlacer(ctx),
) {
    suspend fun dispatch(trigger: Trigger, bpm: Int?) = coroutineScope {
        val profile = prefs.profile.first()
        val recipients = if (trigger == Trigger.TEST) {
            listOf(EmergencyContact("You (test)", profile.ownNumber))
        } else prefs.contacts.first()
        if (recipients.isEmpty() || recipients.any { it.number.isBlank() }) {
            log.add("Emergency", "Nobody to notify — check Contacts / your own number")
            return@coroutineScope
        }

        val chain = CallChain(recipients.map { it.number })
        // Start listening before any SMS goes out so an early reply is not missed.
        val okWatcher = launch {
            val reply = ReplyBus.replies.first { r ->
                SmsReplies.isOk(r.body) && recipients.any { PhoneNumbers.same(it.number, r.from) }
            }
            chain.stop()
            log.add("Reply", "OK from ${reply.from} — no more calls")
        }

        val loc = location.current()
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val text = AlertMessage.build(profile.name, trigger, bpm, time, loc?.latitude, loc?.longitude)
        recipients.map { c ->
            async { c to sms.send(c.number, text) }
        }.awaitAll().forEach { (c, ok) ->
            log.add("SMS", "${c.name}: ${if (ok) "sent" else "FAILED after 3 tries"}")
        }

        var step = chain.start()
        while (step is CallChain.Step.Call) {
            val c = recipients[step.index]
            log.add("Call", "Calling ${c.name}")
            val secs = calls.callAndMeasure(c.number)
            log.add("Call", "${c.name}: connected ${secs}s")
            step = chain.onCallEnded(secs)
        }
        log.add("Emergency", "Call chain finished")
        okWatcher.cancel()
    }
}
```

- [ ] **Step 6: Register the SMS receiver** inside `<application>` in the manifest:

```xml
        <receiver
            android:name=".emergency.SmsReplyReceiver"
            android:exported="true"
            android:permission="android.permission.BROADCAST_SMS">
            <intent-filter>
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 7: Build and run all unit tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main
git commit -m "feat: SMS, location, call chain and OK-reply dispatch"
```

---

### Task 8: Notifications, alarm sound and alarm screen

**Files:**
- Create: `app/src/main/java/com/hearthelp/monitor/MonitorStatus.kt`, `.../monitor/Notifications.kt`, `.../monitor/AlarmController.kt`, `app/src/main/java/com/hearthelp/ui/AlarmActivity.kt`
- Modify: `app/src/main/java/com/hearthelp/HeartHelpApp.kt`, `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `AlertState`, `Trigger`.
- Produces:
  - `data class MonitorSnapshot(running: Boolean = false, state: AlertState = Normal, bpm: Int? = null)`, `object MonitorStatus { val flow: MutableStateFlow<MonitorSnapshot> }`
  - `object Notifications` with `ONGOING_ID`, `createChannels(ctx)`, `ongoing(ctx, text): Notification`, `updateOngoing(ctx, text)`, `signalLost(ctx, bluetoothOn: Boolean)`, `cancelSignalLost(ctx)`, `warning(ctx, problems: List<String>)`
  - `class AlarmController(ctx)` with `start()` (sound + vibration + full-screen screen), `silence()`, `showScreen()`, `clear()`
  - `AlarmActivity` sends the intent actions `"com.hearthelp.IM_OK"` / `"com.hearthelp.DISMISS"` to `com.hearthelp.monitor.MonitoringService`. They're written as string literals so this task compiles before Task 9 creates the service. Task 9's constants use the same values.

- [ ] **Step 1: Write `monitor/MonitorStatus.kt`**

```kotlin
package com.hearthelp.monitor

import com.hearthelp.domain.AlertState
import kotlinx.coroutines.flow.MutableStateFlow

data class MonitorSnapshot(
    val running: Boolean = false,
    val state: AlertState = AlertState.Normal,
    val bpm: Int? = null,
)

/** Live status published by MonitoringService and read by the UI. */
object MonitorStatus {
    val flow = MutableStateFlow(MonitorSnapshot())
}
```

- [ ] **Step 2: Write `monitor/Notifications.kt`**

```kotlin
package com.hearthelp.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hearthelp.R
import com.hearthelp.ui.MainActivity

object Notifications {
    const val CH_MONITOR = "monitor"
    const val CH_ALARM = "alarm"
    const val CH_WARN = "warnings"
    const val ONGOING_ID = 1
    const val ALARM_ID = 2
    const val SIGNAL_ID = 3
    const val WARN_ID = 4

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MONITOR, "Monitoring", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALARM, "Heart-rate alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null) // AlarmController plays the sound itself
            setBypassDnd(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_WARN, "Warnings", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    fun ongoing(ctx: Context, text: String): Notification {
        val sos = PendingIntent.getForegroundService(
            ctx, 10,
            Intent("com.hearthelp.SOS").setClassName(ctx, "com.hearthelp.monitor.MonitoringService"),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(ctx, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle("HeartHelp")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "SOS", sos)
            .build()
    }

    fun updateOngoing(ctx: Context, text: String) { notify(ctx, ONGOING_ID, ongoing(ctx, text)) }

    fun signalLost(ctx: Context, bluetoothOn: Boolean) = notify(ctx, SIGNAL_ID,
        NotificationCompat.Builder(ctx, CH_WARN)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle("No heart-rate signal")
            .setContentText(if (bluetoothOn) "Check the band is on your wrist and 'Share heart rate' is on"
                else "Bluetooth is off — turn it on to resume monitoring")
            .setContentIntent(openApp(ctx))
            .build())

    fun cancelSignalLost(ctx: Context) = cancel(ctx, SIGNAL_ID)

    fun warning(ctx: Context, problems: List<String>) {
        if (problems.isEmpty()) return cancel(ctx, WARN_ID)
        notify(ctx, WARN_ID, NotificationCompat.Builder(ctx, CH_WARN)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle("Emergency alerts may not work")
            .setContentText(problems.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(problems.joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .build())
    }

    /** No-op if the notification permission was revoked. */
    fun notify(ctx: Context, id: Int, n: Notification) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(id, n) }
    }

    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
```

In `HeartHelpApp.onCreate()`, add `Notifications.createChannels(this)` after `super.onCreate()` (import `com.hearthelp.monitor.Notifications`).

- [ ] **Step 3: Write `monitor/AlarmController.kt`**

```kotlin
package com.hearthelp.monitor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.hearthelp.R
import com.hearthelp.ui.AlarmActivity

/** Loud looping alarm on the alarm stream (max volume, restored afterwards), vibration, full-screen screen. */
class AlarmController(private val ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        ctx.getSystemService(VibratorManager::class.java).defaultVibrator
    } else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
    private var player: MediaPlayer? = null
    private var savedVolume: Int? = null

    fun start() {
        showScreen()
        if (player != null) return
        runCatching {
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(ctx, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0))
    }

    fun silence() {
        player?.run { runCatching { stop() }; release() }
        player = null
        savedVolume?.let { v -> runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) } }
        savedVolume = null
        vibrator.cancel()
    }

    fun showScreen() {
        val intent = Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        Notifications.notify(ctx, Notifications.ALARM_ID,
            NotificationCompat.Builder(ctx, Notifications.CH_ALARM)
                .setSmallIcon(R.drawable.ic_heart)
                .setContentTitle("Heart-rate alert")
                .setContentText("Tap to respond")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .build())
        // If the app is in the foreground the full-screen intent only shows a heads-up; open directly too.
        runCatching { ctx.startActivity(intent) }
    }

    fun clear() {
        silence()
        Notifications.cancel(ctx, Notifications.ALARM_ID)
    }
}
```

- [ ] **Step 4: Write `ui/AlarmActivity.kt`**

```kotlin
package com.hearthelp.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.domain.AlertState
import com.hearthelp.domain.Trigger
import com.hearthelp.monitor.MonitorStatus
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            MaterialTheme {
                AlarmScreen(
                    onImOk = { send("com.hearthelp.IM_OK") },
                    onDismiss = { send("com.hearthelp.DISMISS") },
                    onDone = { finish() },
                )
            }
        }
    }

    private fun send(action: String) {
        startService(Intent(action).setClassName(this, "com.hearthelp.monitor.MonitoringService"))
    }
}

@Composable
private fun AlarmScreen(onImOk: () -> Unit, onDismiss: () -> Unit, onDone: () -> Unit) {
    val snap by MonitorStatus.flow.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFB71C1C)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = snap.state) {
            is AlertState.Alarm -> {
                val secs = ((s.deadline - now + 999) / 1000).coerceAtLeast(0)
                Text(headline(s.trigger, s.bpm), color = Color.White, fontSize = 28.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Contacting your emergency contacts in", color = Color.White, fontSize = 18.sp)
                Text("$secs", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onImOk,
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB71C1C)),
                ) { Text("I'M OK", fontSize = 32.sp, fontWeight = FontWeight.Bold) }
            }
            is AlertState.Dispatched -> {
                Text("Your emergency contacts are being notified", color = Color.White, fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("SMS sent, calling in order. Tap below once you're safe to stop further calls.",
                    color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB71C1C)),
                ) { Text("I'm safe — stop alert", fontSize = 22.sp) }
            }
            else -> LaunchedEffect(s) { onDone() }
        }
    }
}

private fun headline(trigger: Trigger, bpm: Int?): String {
    val b = bpm?.let { " — $it bpm" } ?: ""
    return when (trigger) {
        Trigger.HIGH -> "Heart rate HIGH$b"
        Trigger.LOW -> "Heart rate LOW$b"
        Trigger.SOS -> "SOS"
        Trigger.TEST -> "TEST ALERT$b"
    }
}
```

- [ ] **Step 5: Register the activity** inside `<application>`:

```xml
        <activity
            android:name=".ui.AlarmActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:launchMode="singleTask"
            android:taskAffinity=""
            android:showWhenLocked="true"
            android:turnScreenOn="true" />
```

- [ ] **Step 6: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main
git commit -m "feat: notifications, loud alarm and full-screen alarm screen"
```

---

### Task 9: Monitoring service, readiness checks and boot restart

**Files:**
- Create: `app/src/main/java/com/hearthelp/monitor/MonitoringService.kt`, `.../monitor/Readiness.kt`, `.../monitor/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: everything from Tasks 3–8.
- Produces:
  - `MonitoringService` with actions `ACTION_START = "com.hearthelp.START"`, `ACTION_STOP = "com.hearthelp.STOP"`, `ACTION_IM_OK = "com.hearthelp.IM_OK"`, `ACTION_DISMISS = "com.hearthelp.DISMISS"`, `ACTION_SOS = "com.hearthelp.SOS"`, `ACTION_TEST = "com.hearthelp.TEST"`, and `companion fun send(ctx: Context, action: String)`
  - `object Readiness { fun runtimePermissions(): List<String>; fun missingPermissions(ctx): List<String>; fun backgroundLocationGranted(ctx): Boolean; fun batteryExempt(ctx): Boolean; fun fullScreenAllowed(ctx): Boolean; suspend fun problems(ctx, prefs): List<String> }`

- [ ] **Step 1: Write `monitor/Readiness.kt`**

```kotlin
package com.hearthelp.monitor

import android.Manifest.permission.*
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.hearthelp.data.AppPrefs
import kotlinx.coroutines.flow.first

object Readiness {
    private val labels = mapOf(
        SEND_SMS to "Send SMS", RECEIVE_SMS to "Read SMS replies", CALL_PHONE to "Make calls",
        READ_PHONE_STATE to "Phone state", READ_CALL_LOG to "Call log", ACCESS_FINE_LOCATION to "Location",
        ACCESS_COARSE_LOCATION to "Location", BLUETOOTH_SCAN to "Find band", BLUETOOTH_CONNECT to "Connect to band",
        POST_NOTIFICATIONS to "Notifications",
    )

    fun runtimePermissions(): List<String> = buildList {
        addAll(listOf(SEND_SMS, RECEIVE_SMS, CALL_PHONE, READ_PHONE_STATE, READ_CALL_LOG,
            ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
        if (Build.VERSION.SDK_INT >= 31) { add(BLUETOOTH_SCAN); add(BLUETOOTH_CONNECT) }
        if (Build.VERSION.SDK_INT >= 33) add(POST_NOTIFICATIONS)
    }

    fun missingPermissions(ctx: Context): List<String> =
        runtimePermissions().filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    fun label(permission: String): String = labels[permission] ?: permission.substringAfterLast('.')

    fun backgroundLocationGranted(ctx: Context) =
        ctx.checkSelfPermission(ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun batteryExempt(ctx: Context) =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun fullScreenAllowed(ctx: Context) = Build.VERSION.SDK_INT < 34 ||
        ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** Human-readable reasons emergency alerts might fail. Empty = all good. */
    suspend fun problems(ctx: Context, prefs: AppPrefs): List<String> = buildList {
        val missing = missingPermissions(ctx).map(::label).distinct()
        if (missing.isNotEmpty()) add("Permissions missing: ${missing.joinToString()}")
        if (!backgroundLocationGranted(ctx)) add("Location isn't set to 'Allow all the time' — SMS may lack your location")
        if (!batteryExempt(ctx)) add("Battery optimization is on — Android may stop monitoring")
        if (!fullScreenAllowed(ctx)) add("Full-screen alarms are not allowed")
        if (prefs.contacts.first().isEmpty()) add("No emergency contacts added")
        if (prefs.bandAddress.first() == null) add("No Fitbit Air selected")
    }
}
```

- [ ] **Step 2: Write `monitor/MonitoringService.kt`**

```kotlin
package com.hearthelp.monitor

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hearthelp.app
import com.hearthelp.ble.HeartRateSource
import com.hearthelp.domain.AlertEvaluator
import com.hearthelp.domain.AlertSettings
import com.hearthelp.domain.AlertState
import com.hearthelp.domain.AlertState.*
import com.hearthelp.emergency.EmergencyDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Owns the alert state. Everything runs on the main thread via lifecycleScope. */
class MonitoringService : LifecycleService() {
    companion object {
        const val ACTION_START = "com.hearthelp.START"
        const val ACTION_STOP = "com.hearthelp.STOP"
        const val ACTION_IM_OK = "com.hearthelp.IM_OK"
        const val ACTION_DISMISS = "com.hearthelp.DISMISS"
        const val ACTION_SOS = "com.hearthelp.SOS"
        const val ACTION_TEST = "com.hearthelp.TEST"
        private val startsMonitoring = setOf(ACTION_START, ACTION_SOS, ACTION_TEST)

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, MonitoringService::class.java).setAction(action)
            if (action in startsMonitoring) ContextCompat.startForegroundService(ctx, i) else ctx.startService(i)
        }
    }

    private lateinit var evaluator: AlertEvaluator
    private lateinit var alarm: AlarmController
    private lateinit var dispatcher: EmergencyDispatcher
    private var started = false
    private var dispatchJob: Job? = null
    private var lastText = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: ACTION_START // null = restarted by the system (START_STICKY)
        if (!started) {
            if (action !in startsMonitoring) { stopSelf(); return START_NOT_STICKY }
            if (!begin()) return START_NOT_STICKY
        }
        when (action) {
            ACTION_STOP -> lifecycleScope.launch {
                app.prefs.setMonitoringEnabled(false)
                app.log.add("Monitoring", "Stopped")
                stopSelf()
            }
            ACTION_IM_OK -> transition { onImOk(now()) }
            ACTION_DISMISS -> { dispatchJob?.cancel(); transition { onDismiss() } }
            ACTION_SOS -> transition { onSos() }
            ACTION_TEST -> transition { startTest(now()) }
        }
        return START_STICKY
    }

    private fun begin(): Boolean {
        try {
            ServiceCompat.startForeground(this, Notifications.ONGOING_ID, Notifications.ongoing(this, "Connecting to band…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: Exception) {
            Notifications.warning(this, listOf("Monitoring could not start: ${e.message}"))
            stopSelf()
            return false
        }
        started = true
        alarm = AlarmController(this)
        dispatcher = EmergencyDispatcher(this, app.prefs, app.log)
        evaluator = AlertEvaluator(AlertSettings(), now())
        MonitorStatus.flow.value = MonitorSnapshot(running = true)

        lifecycleScope.launch {
            app.prefs.setMonitoringEnabled(true)
            app.log.add("Monitoring", "Started")
        }
        lifecycleScope.launch { app.prefs.settings.collect { evaluator.settings = it } }
        lifecycleScope.launch {
            app.prefs.bandAddress.filterNotNull().collectLatest { address ->
                HeartRateSource(this@MonitoringService).readings(address)
                    .retryWhen { cause, _ ->
                        app.log.add("Bluetooth", cause.message ?: "Connection error")
                        delay(10_000)
                        true
                    }
                    .collect { bpm -> transition { onReading(bpm, now()) } }
            }
        }
        lifecycleScope.launch {
            while (isActive) { transition { onTick(now()) }; delay(1_000) }
        }
        lifecycleScope.launch {
            while (isActive) { Notifications.warning(this@MonitoringService, Readiness.problems(this@MonitoringService, app.prefs)); delay(60_000) }
        }
        return true
    }

    private fun transition(event: AlertEvaluator.() -> AlertState) {
        val prev = evaluator.state
        val next = evaluator.event()
        if (next != prev) onStateChanged(prev, next)
        MonitorStatus.flow.value = MonitorSnapshot(running = true, state = next, bpm = evaluator.lastBpm)
        val text = statusText(next, evaluator.lastBpm)
        if (text != lastText) { lastText = text; Notifications.updateOngoing(this, text) }
    }

    private fun onStateChanged(prev: AlertState, next: AlertState) {
        if (prev is Alarm && next !is Alarm) { if (next is Dispatched) alarm.silence() else alarm.clear() }
        if (prev is Dispatched && next !is Dispatched) alarm.clear()
        if (prev is SignalLost && next !is SignalLost) Notifications.cancelSignalLost(this)
        when (next) {
            is Alarm -> {
                alarm.start()
                log("Alarm", "${next.trigger} ${next.bpm ?: "-"} bpm — countdown started")
            }
            is Snoozed -> log("I'm OK", "Alerts paused until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(next.until))}")
            is Dispatched -> {
                if (prev !is Alarm) alarm.showScreen()
                log("Emergency", "${next.trigger} ${next.bpm ?: "-"} bpm — notifying contacts")
                dispatchJob = lifecycleScope.launch { dispatcher.dispatch(next.trigger, next.bpm) }
            }
            SignalLost -> {
                val btOn = getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
                Notifications.signalLost(this, btOn)
                log("Signal lost", if (btOn) "No readings from band" else "Bluetooth is off")
            }
            Normal -> when (prev) {
                is Dispatched -> log("Dismissed", "User stopped the alert")
                is Alarm -> log("I'm OK", "Test alert cancelled")
                else -> {}
            }
            is BreachPending -> {}
        }
    }

    private fun statusText(s: AlertState, bpm: Int?): String {
        val b = bpm?.let { "$it bpm" } ?: "—"
        return when (s) {
            Normal, is BreachPending -> "Monitoring — $b"
            is Alarm -> "ALARM — $b"
            is Dispatched -> "Contacting emergency contacts"
            is Snoozed -> "Paused after I'm OK — $b"
            SignalLost -> "No signal from band"
        }
    }

    private fun log(type: String, detail: String) { lifecycleScope.launch { app.log.add(type, detail) } }
    private fun now() = System.currentTimeMillis()

    override fun onDestroy() {
        if (started) alarm.clear()
        Notifications.cancelSignalLost(this)
        MonitorStatus.flow.value = MonitorSnapshot(running = false)
        super.onDestroy()
    }
}
```

- [ ] **Step 3: Write `monitor/BootReceiver.kt`**

```kotlin
package com.hearthelp.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hearthelp.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (context.app.prefs.monitoringEnabled.first()) {
                    MonitoringService.send(context, MonitoringService.ACTION_START)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
```

- [ ] **Step 4: Register the service and receiver** inside `<application>`:

```xml
        <service
            android:name=".monitor.MonitoringService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />

        <receiver
            android:name=".monitor.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 5: Build and run all unit tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main
git commit -m "feat: foreground monitoring service, readiness warnings, restart on boot"
```

---

### Task 10: Screens — Home, Contacts, Settings, Log

**Files:**
- Modify (replace): `app/src/main/java/com/hearthelp/ui/MainActivity.kt`
- Create: `app/src/main/java/com/hearthelp/ui/HomeScreen.kt`, `.../ui/ContactsScreen.kt`, `.../ui/SettingsScreen.kt`, `.../ui/LogScreen.kt`

**Interfaces:**
- Consumes: `MonitoringService.send` + actions, `MonitorStatus`, `Readiness`, `AppPrefs`, `EventLog.recent`, `EmergencyContacts`, `AlertSettings`, `BandScanner`.

- [ ] **Step 1: Replace `ui/MainActivity.kt`**

```kotlin
package com.hearthelp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

private enum class Tab(val label: String) { Home("Home"), Contacts("Contacts"), Settings("Settings"), Log("Log") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var tab by rememberSaveable { mutableStateOf(Tab.Home) }
                Scaffold(bottomBar = {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(selected = tab == t, onClick = { tab = t },
                                icon = {}, label = { Text(t.label) })
                        }
                    }
                }) { padding ->
                    val m = Modifier.padding(padding)
                    when (tab) {
                        Tab.Home -> HomeScreen(m)
                        Tab.Contacts -> ContactsScreen(m)
                        Tab.Settings -> SettingsScreen(m)
                        Tab.Log -> LogScreen(m)
                    }
                }
            }
        }
    }
}
```

Add the import `androidx.compose.runtime.saveable.rememberSaveable`.

- [ ] **Step 2: Write `ui/HomeScreen.kt`**

```kotlin
package com.hearthelp.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.app
import com.hearthelp.domain.AlertState
import com.hearthelp.monitor.MonitorStatus
import com.hearthelp.monitor.MonitoringService
import com.hearthelp.monitor.Readiness

@Composable
fun HomeScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val snap by MonitorStatus.flow.collectAsStateWithLifecycle()
    val contacts by ctx.app.prefs.contacts.collectAsStateWithLifecycle(emptyList())
    val band by ctx.app.prefs.bandAddress.collectAsStateWithLifecycle(null)
    val profile by ctx.app.prefs.profile.collectAsStateWithLifecycle(com.hearthelp.data.Profile())

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose {} }
    val missing = remember(refresh) { Readiness.missingPermissions(ctx) }
    val bgLocation = remember(refresh) { Readiness.backgroundLocationGranted(ctx) }
    val battery = remember(refresh) { Readiness.batteryExempt(ctx) }
    val fullScreen = remember(refresh) { Readiness.fullScreenAllowed(ctx) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    var confirmSos by remember { mutableStateOf(false) }
    val pkg = Uri.parse("package:${ctx.packageName}")

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(statusLine(snap.running, snap.state, snap.bpm), style = MaterialTheme.typography.headlineSmall)

        if (missing.isNotEmpty()) SetupItem("Grant permissions: ${missing.map(Readiness::label).distinct().joinToString()}") {
            permLauncher.launch(missing.toTypedArray())
        }
        if (Manifest.permission.ACCESS_FINE_LOCATION !in missing && !bgLocation) SetupItem("Set location to 'Allow all the time'") {
            bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (!battery) SetupItem("Turn off battery optimization for HeartHelp") {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
        }
        if (!fullScreen && Build.VERSION.SDK_INT >= 34) SetupItem("Allow full-screen alarms") {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
        }
        if (contacts.isEmpty()) Text("Add at least one emergency contact in the Contacts tab.")
        if (band == null) Text("Choose your Fitbit Air in the Settings tab.")
        TextButton(onClick = {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/")))
        }) { Text("Phone-maker background settings (dontkillmyapp.com)") }

        val canStart = contacts.isNotEmpty() && band != null && missing.isEmpty()
        if (snap.running) {
            OutlinedButton(onClick = { MonitoringService.send(ctx, MonitoringService.ACTION_STOP) },
                modifier = Modifier.fillMaxWidth()) { Text("Stop monitoring") }
        } else {
            Button(onClick = { MonitoringService.send(ctx, MonitoringService.ACTION_START) }, enabled = canStart,
                modifier = Modifier.fillMaxWidth()) { Text("Start monitoring") }
        }
        Button(onClick = { confirmSos = true }, enabled = contacts.isNotEmpty() && missing.isEmpty(),
            modifier = Modifier.fillMaxWidth().height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) { Text("SOS") }
        OutlinedButton(onClick = { MonitoringService.send(ctx, MonitoringService.ACTION_TEST) },
            enabled = canStart && profile.ownNumber.isNotBlank(),
            modifier = Modifier.fillMaxWidth()) { Text("Test alert (texts and calls only you)") }
        if (profile.ownNumber.isBlank()) Text("Set your own number in Settings to use Test alert.")
    }

    if (confirmSos) AlertDialog(
        onDismissRequest = { confirmSos = false },
        title = { Text("Send emergency alert?") },
        text = { Text("Your emergency contacts will be texted and called now.") },
        confirmButton = { TextButton(onClick = {
            confirmSos = false; MonitoringService.send(ctx, MonitoringService.ACTION_SOS)
        }) { Text("Send SOS") } },
        dismissButton = { TextButton(onClick = { confirmSos = false }) { Text("Cancel") } },
    )
}

@Composable
private fun SetupItem(text: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("⚠ $text", Modifier.padding(16.dp))
    }
}

private fun statusLine(running: Boolean, s: AlertState, bpm: Int?): String {
    if (!running) return "Not monitoring"
    val b = bpm?.let { "$it bpm" } ?: "waiting for band…"
    return when (s) {
        AlertState.Normal, is AlertState.BreachPending -> "Monitoring — $b"
        is AlertState.Alarm -> "ALARM — $b"
        is AlertState.Dispatched -> "Contacting emergency contacts"
        is AlertState.Snoozed -> "Paused after I'm OK — $b"
        AlertState.SignalLost -> "No signal from band"
    }
}
```

- [ ] **Step 3: Write `ui/ContactsScreen.kt`**

```kotlin
package com.hearthelp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.app
import com.hearthelp.domain.EmergencyContact
import com.hearthelp.domain.EmergencyContacts
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val prefs = ctx.app.prefs
    val scope = rememberCoroutineScope()
    val contacts by prefs.contacts.collectAsStateWithLifecycle(emptyList())
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun save(list: List<EmergencyContact>) = scope.launch { prefs.saveContacts(list) }
    fun tryAdd(c: EmergencyContact) {
        runCatching { EmergencyContacts.add(contacts, c) }
            .onSuccess { save(it); name = ""; number = ""; error = null }
            .onFailure { error = it.message }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { uri -> readPicked(ctx, uri)?.let(::tryAdd) }
    }

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Emergency contacts (${contacts.size}/${EmergencyContacts.MAX})", style = MaterialTheme.typography.titleLarge)
            Text("All of them get the SMS. Calls go in this order until someone answers or replies OK.")
        }
        itemsIndexed(contacts) { i, c ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${c.name}" + if (i == 0) "  (called first)" else "")
                        Text(c.number, style = MaterialTheme.typography.bodySmall)
                    }
                    if (i > 0) TextButton(onClick = { save(EmergencyContacts.moveUp(contacts, i)) }) { Text("Up") }
                    TextButton(onClick = { save(EmergencyContacts.remove(contacts, i)) }) { Text("Remove") }
                }
            }
        }
        if (contacts.size < EmergencyContacts.MAX) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)) },
                    modifier = Modifier.fillMaxWidth()) { Text("Pick from phone contacts") }
                Text("…or type one in:")
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(number, { number = it }, label = { Text("Phone number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Button(onClick = { tryAdd(EmergencyContact(name, number)) }) { Text("Add") }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}

private fun readPicked(ctx: Context, uri: Uri): EmergencyContact? =
    ctx.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use {
        if (it.moveToFirst()) EmergencyContact(it.getString(0) ?: "", it.getString(1) ?: "") else null
    }
```

- [ ] **Step 4: Write `ui/SettingsScreen.kt`**

```kotlin
package com.hearthelp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.app
import com.hearthelp.ble.BandScanner
import com.hearthelp.data.Profile
import com.hearthelp.domain.AlertSettings
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun SettingsScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val prefs = ctx.app.prefs
    val scope = rememberCoroutineScope()

    // Load once into editable text fields.
    var fields by remember { mutableStateOf<Map<String, String>?>(null) }
    LaunchedEffect(Unit) {
        val s = prefs.settings.first(); val p = prefs.profile.first()
        fields = mapOf(
            "Your name" to p.name, "Your own number (for Test alert)" to p.ownNumber,
            "Low limit (bpm)" to "${s.lowBpm}", "High limit (bpm)" to "${s.highBpm}",
            "Must last (seconds)" to "${s.sustainSeconds}", "Countdown (seconds)" to "${s.countdownSeconds}",
            "Pause after I'm OK (minutes)" to "${s.snoozeMinutes}", "Signal-lost alert after (minutes)" to "${s.signalLostMinutes}",
        )
    }
    var message by remember { mutableStateOf<String?>(null) }
    val bandName by prefs.bandName.collectAsStateWithLifecycle(null)
    val found = remember { mutableStateListOf<BandScanner.Found>() }
    var scanning by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        val f = fields ?: return@Column
        f.forEach { (label, value) ->
            val numeric = label.contains("(")  && !label.startsWith("Your own")
            OutlinedTextField(value, { fields = f + (label to it) }, label = { Text(label) },
                keyboardOptions = KeyboardOptions(keyboardType = when {
                    label.startsWith("Your own") -> KeyboardType.Phone
                    numeric -> KeyboardType.Number
                    else -> KeyboardType.Text
                }),
                modifier = Modifier.fillMaxWidth())
        }
        Text("Ask your doctor what low and high limits suit your medication.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            runCatching {
                fun n(k: String) = f.getValue(k).trim().toIntOrNull() ?: throw IllegalArgumentException("$k must be a number")
                AlertSettings(n("Low limit (bpm)"), n("High limit (bpm)"), n("Must last (seconds)"),
                    n("Countdown (seconds)"), n("Pause after I'm OK (minutes)"), n("Signal-lost alert after (minutes)"))
            }.onSuccess { s ->
                scope.launch {
                    prefs.saveSettings(s)
                    prefs.saveProfile(Profile(f.getValue("Your name"), f.getValue("Your own number (for Test alert)")))
                }
                message = "Saved"
            }.onFailure { message = it.message }
        }) { Text("Save") }
        message?.let { Text(it) }

        HorizontalDivider()
        Text("Band: ${bandName ?: "none selected"}", style = MaterialTheme.typography.titleMedium)
        Text("Turn on Google Health app → Connections → Fitbit Air → Share heart rate, then scan.",
            style = MaterialTheme.typography.bodySmall)
        Button(enabled = !scanning, onClick = {
            found.clear(); scanning = true
            scope.launch {
                withTimeoutOrNull(15_000) {
                    BandScanner(ctx).scan()
                        .catch { message = it.message }
                        .collect { r -> if (found.none { it.address == r.address }) found.add(r) }
                }
                scanning = false
            }
        }) { Text(if (scanning) "Scanning…" else "Scan for Fitbit Air") }
        found.forEach { r ->
            OutlinedCard(onClick = { scope.launch { prefs.saveBand(r.address, r.name) }; found.clear() },
                modifier = Modifier.fillMaxWidth()) {
                Text("${r.name}\n${r.address}", Modifier.padding(12.dp))
            }
        }
    }
}
```

- [ ] **Step 5: Write `ui/LogScreen.kt`**

```kotlin
package com.hearthelp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.app
import java.text.DateFormat
import java.util.Date

@Composable
fun LogScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val entries by ctx.app.log.recent.collectAsStateWithLifecycle(emptyList())
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Event log", style = MaterialTheme.typography.titleLarge) }
        if (entries.isEmpty()) item { Text("Nothing yet.") }
        items(entries, key = { it.id }) { e ->
            Column {
                Text("${fmt.format(Date(e.time))} · ${e.type}", style = MaterialTheme.typography.labelMedium)
                Text(e.detail)
            }
        }
    }
}
```

- [ ] **Step 6: Build and run all unit tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main
git commit -m "feat: home, contacts, settings and log screens"
```

---

### Task 11: On-device verification (manual)

**Files:**
- Create: `docs/device-test.md`

The phone needs Developer options → USB debugging turned on.

- [ ] **Step 1: Install.** `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk`
- [ ] **Step 2: Setup.** Open HeartHelp and clear every ⚠ item on Home. In Settings, enter your name and own number, choose limits, Save, scan, and pick the Fitbit Air. In Contacts, add one trusted person who knows you're testing.
- [ ] **Step 3: Live readings.** Start monitoring. Check that the notification shows "Monitoring — N bpm" and that N updates every few seconds.
- [ ] **Step 4: Test alert.** Tap Test alert. Check that the alarm screen appears with a loud alarm even with the phone on silent, and that the countdown goes from 20 to 0. Then check that you get the "[TEST]" SMS and a call to your own number, and that Log lists Alarm → Emergency → SMS sent → Call.
- [ ] **Step 5: I'm OK.** Run Test alert again and tap I'M OK. The alarm should stop and nothing should be sent.
- [ ] **Step 6: Real breach.** Set the High limit just above your resting rate (for example resting + 5), Save, then walk briskly. Check that the alarm triggers after about 10 s above the limit. Tap I'M OK and check that Log says "Alerts paused until …". Set the limit back afterwards.
- [ ] **Step 7: Lock-screen alarm.** Repeat Step 6 with the phone locked and the screen off. Check that the alarm turns the screen on and shows over the lock screen.
- [ ] **Step 8: Signal lost.** Turn off the band's sharing, or take the band out of range, for 2+ minutes. You should get a "No heart-rate signal" notification and no SMS. Bring it back and check that readings resume.
- [ ] **Step 9: Call chain and OK reply.** With the trusted contact's agreement, press SOS → Send SOS. Check that they get the SMS and a call. Have them reply "OK" and check that Log says "OK from … — no more calls". Tap "I'm safe — stop alert".
- [ ] **Step 10: Reboot.** Reboot the phone while monitoring is on. Check that the monitoring notification comes back without opening the app.
- [ ] **Step 11: 24 h run.** Leave it monitoring for 24 h with normal use. Check the Log for unexpected signal-lost periods and note phone and band battery use.
- [ ] **Step 12:** Record the pass/fail for each step in `docs/device-test.md` and commit:

```bash
git add docs/device-test.md
git commit -m "docs: on-device verification results"
```
