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
