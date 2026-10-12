package com.hearthelp.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.app
import com.hearthelp.data.Profile
import com.hearthelp.domain.AlertSettings
import com.hearthelp.domain.AlertState
import com.hearthelp.monitor.MonitorStatus
import com.hearthelp.monitor.MonitoringService
import com.hearthelp.monitor.Readiness
import com.hearthelp.ui.theme.*
import java.text.DateFormat
import java.util.Date

private class SetupStep(val text: String, val fix: () -> Unit)

@Composable
internal fun HomeScreen(modifier: Modifier, onGoTo: (Tab) -> Unit) {
    val ctx = LocalContext.current
    val snap by MonitorStatus.flow.collectAsStateWithLifecycle()
    val contacts by ctx.app.prefs.contacts.collectAsStateWithLifecycle(emptyList())
    val band by ctx.app.prefs.bandAddress.collectAsStateWithLifecycle(null)
    val profile by ctx.app.prefs.profile.collectAsStateWithLifecycle(Profile())
    val settings by ctx.app.prefs.settings.collectAsStateWithLifecycle(AlertSettings())

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose {} }
    val missing = remember(refresh) { Readiness.missingPermissions(ctx) }
    val sosPermitted = remember(refresh) { Readiness.sosPermitted(ctx) }
    val serviceCanStart = remember(refresh) { Readiness.serviceCanStart(ctx) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    var confirmSos by remember { mutableStateOf(false) }
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    val pkg = Uri.parse("package:${ctx.packageName}")

    val steps = remember(refresh, contacts.isEmpty(), band) {
        buildList {
            if (missing.isNotEmpty()) add(SetupStep("Allow ${missing.map(Readiness::label).distinct().joinToString()}") {
                permLauncher.launch(missing.toTypedArray())
            })
            if (Manifest.permission.ACCESS_FINE_LOCATION !in missing && !Readiness.backgroundLocationGranted(ctx))
                add(SetupStep("Set location to “Allow all the time”") {
                    bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                })
            if (!Readiness.batteryExempt(ctx)) add(SetupStep("Turn off battery optimisation") {
                startSettings(ctx, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            })
            if (!Readiness.fullScreenAllowed(ctx) && Build.VERSION.SDK_INT >= 34) add(SetupStep("Allow full-screen alarms") {
                startSettings(ctx, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            })
            if (!Readiness.notificationsEnabled(ctx) || Readiness.alarmChannelBlocked(ctx))
                add(SetupStep("Turn on alarm notifications") { startNotificationSettings(ctx, pkg) })
            if (!Readiness.bluetoothOn(ctx)) add(SetupStep("Turn on Bluetooth") {
                startSettings(ctx, Settings.ACTION_BLUETOOTH_SETTINGS, pkg)
            })
            if (contacts.isEmpty()) add(SetupStep("Add an emergency contact") { onGoTo(Tab.Contacts) })
            if (band == null) add(SetupStep("Choose your Fitbit Air") { onGoTo(Tab.Settings) })
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        val (headline, subline) = status(snap.running, snap.state, settings)
        Text(headline, style = HhType.Title, color = Hh.Moon)
        Text(subline, style = HhType.Small, color = Hh.Mist)

        val live = snap.running && snap.state.let { it is AlertState.Normal || it is AlertState.BreachPending || it is AlertState.Snoozed }
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            PulseRing(if (live) snap.bpm else null, Modifier.size(280.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (snap.running) snap.bpm?.toString() ?: "–" else "–", style = HhType.Reading)
                Text("bpm", style = HhType.Small, color = Hh.Mist)
            }
        }

        // Answer an alert from here too, in case the alarm screen didn't appear.
        when (snap.state) {
            is AlertState.Alarm -> AlarmAnswer("I'm OK") { MonitoringService.send(ctx, MonitoringService.ACTION_IM_OK) }
            is AlertState.Dispatched -> AlarmAnswer("I'm safe, stop the alert") {
                MonitoringService.send(ctx, MonitoringService.ACTION_DISMISS)
            }
            else -> {}
        }

        if (steps.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().animateContentSize()) {
                Row(
                    Modifier.fillMaxWidth().clickable { setupOpen = !setupOpen }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (steps.size == 1) "1 step left to finish setup" else "${steps.size} steps left to finish setup",
                        style = HhType.Body, color = Hh.Pulse, modifier = Modifier.weight(1f))
                    Text(if (setupOpen) "Hide" else "Show", style = HhType.Small, color = Hh.Mist)
                }
                if (setupOpen) steps.forEach { step ->
                    Row(Modifier.fillMaxWidth().clickable(onClick = step.fix).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(step.text, style = HhType.Body, color = Hh.Moon, modifier = Modifier.weight(1f))
                        Text("Fix", style = HhType.Label, color = Hh.Pulse)
                    }
                }
                LinkButton("Stop your phone closing the app in the background", onClick = {
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/")))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(ctx, "No browser found. Visit dontkillmyapp.com", Toast.LENGTH_LONG).show()
                    }
                }, modifier = Modifier.offset(x = (-12).dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        val canStart = contacts.isNotEmpty() && band != null && missing.isEmpty()
        if (snap.running) {
            QuietButton("Stop monitoring", { MonitoringService.send(ctx, MonitoringService.ACTION_STOP) },
                modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton("Start monitoring", { MonitoringService.send(ctx, MonitoringService.ACTION_START) }, enabled = canStart)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val sosEnabled = contacts.isNotEmpty() && sosPermitted && (snap.running || serviceCanStart)
            OutlinedButton(
                onClick = { confirmSos = true }, enabled = sosEnabled, shape = RoundedCornerShape(50),
                border = BorderStroke(1.5.dp, if (sosEnabled) Hh.Alarm else Hh.Dusk),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Hh.Pulse, disabledContentColor = Hh.Mist),
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("SOS", style = HhType.Label) }
            Spacer(Modifier.width(12.dp))
            LinkButton("Test alert", { MonitoringService.send(ctx, MonitoringService.ACTION_TEST) },
                color = if (canStart && profile.ownNumber.isNotBlank()) Hh.Moon else Hh.Mist,
                modifier = Modifier.weight(1f).height(56.dp))
        }
        val hint = when {
            contacts.isNotEmpty() && !sosPermitted -> "SOS needs permission to send texts or make calls."
            contacts.isNotEmpty() && !snap.running && !serviceCanStart -> "SOS needs the “Connect to band” permission."
            profile.ownNumber.isBlank() -> "Test alert texts and calls only you. Add your number in Settings to use it."
            else -> "Test alert texts and calls only you."
        }
        Text(hint, style = HhType.Small, color = Hh.Mist, modifier = Modifier.padding(top = 8.dp))
    }

    if (confirmSos) AlertDialog(
        onDismissRequest = { confirmSos = false },
        containerColor = Hh.Dusk, titleContentColor = Hh.Moon, textContentColor = Hh.Mist,
        title = { Text("Send SOS now?", style = HhType.Heading) },
        text = { Text("Your emergency contacts will be texted your location and called in order.", style = HhType.Body) },
        confirmButton = { LinkButton("Send SOS", {
            confirmSos = false; MonitoringService.send(ctx, MonitoringService.ACTION_SOS)
        }, color = Hh.Pulse) },
        dismissButton = { LinkButton("Cancel", { confirmSos = false }) },
    )
}

@Composable
private fun AlarmAnswer(text: String, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Hh.Alarm, contentColor = Hh.Moon)) {
        Text(text, style = HhType.Heading)
    }
    Spacer(Modifier.height(16.dp))
}

private fun status(running: Boolean, s: AlertState, settings: AlertSettings): Pair<String, String> {
    if (!running) return "Not monitoring" to "Start monitoring to watch your heart rate."
    return when (s) {
        AlertState.Normal, is AlertState.BreachPending ->
            "Monitoring" to "You'll be alerted below ${settings.lowBpm} or above ${settings.highBpm} bpm."
        is AlertState.Alarm -> "Alert in progress" to "Tap I'm OK if you're fine."
        is AlertState.Dispatched -> "Contacting your emergency contacts" to "They've been texted. Calls go down your list."
        is AlertState.Snoozed -> "Alerts paused" to
            "Back on at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.until))}."
        AlertState.SignalLost -> "No signal from your band" to "Check it's on your wrist and sharing heart rate."
    }
}

private fun startSettings(ctx: Context, action: String, pkg: Uri) {
    try {
        ctx.startActivity(Intent(action, pkg))
    } catch (e: ActivityNotFoundException) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(ctx, "Open HeartHelp in your phone's app settings", Toast.LENGTH_LONG).show()
        }
    }
}

private fun startNotificationSettings(ctx: Context, pkg: Uri) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
    } catch (e: ActivityNotFoundException) {
        startSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
    }
}
