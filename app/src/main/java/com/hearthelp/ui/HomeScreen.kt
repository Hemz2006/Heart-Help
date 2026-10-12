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
