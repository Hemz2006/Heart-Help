package com.hearthelp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.app
import com.hearthelp.ble.BandScanner
import com.hearthelp.data.Profile
import com.hearthelp.domain.AlertSettings
import com.hearthelp.ui.theme.*
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
    var fields by rememberSaveable(stateSaver = FieldsSaver) { mutableStateOf<Map<String, String>?>(null) }
    LaunchedEffect(Unit) {
        if (fields != null) return@LaunchedEffect
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

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = HhType.Title, color = Hh.Moon)
        val f = fields ?: return@Column

        @Composable
        fun field(label: String, type: KeyboardType, modifier: Modifier = Modifier.fillMaxWidth()) =
            TextField(f.getValue(label), { fields = f + (label to it) }, label = { Text(label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = type),
                colors = hhFieldColors(), shape = FieldShape, modifier = modifier)

        Group("You", "Your name appears in the alert text. Test alert goes only to your own number.")
        field("Your name", KeyboardType.Text)
        field("Your own number (for Test alert)", KeyboardType.Phone)

        Group("Heart-rate limits", "Ask your doctor what suits your medication.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            field("Low limit (bpm)", KeyboardType.Number, Modifier.weight(1f))
            field("High limit (bpm)", KeyboardType.Number, Modifier.weight(1f))
        }

        Group("Timing", "How long out of range before the alarm, and how long you have to answer it.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            field("Must last (seconds)", KeyboardType.Number, Modifier.weight(1f))
            field("Countdown (seconds)", KeyboardType.Number, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            field("Pause after I'm OK (minutes)", KeyboardType.Number, Modifier.weight(1f))
            field("Signal-lost alert after (minutes)", KeyboardType.Number, Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton("Save settings", {
            runCatching {
                fun n(k: String) = f.getValue(k).trim().toIntOrNull() ?: throw IllegalArgumentException("$k must be a number")
                AlertSettings(n("Low limit (bpm)"), n("High limit (bpm)"), n("Must last (seconds)"),
                    n("Countdown (seconds)"), n("Pause after I'm OK (minutes)"), n("Signal-lost alert after (minutes)"))
            }.onSuccess { s ->
                scope.launch {
                    prefs.saveSettings(s)
                    prefs.saveProfile(Profile(f.getValue("Your name"), f.getValue("Your own number (for Test alert)")))
                }
                message = "Settings saved"
            }.onFailure { message = it.message }
        })
        message?.let { Text(it, style = HhType.Body, color = if (it == "Settings saved") Hh.Mist else Hh.Pulse) }

        Group("Band", bandName?.let { "Connected to $it." } ?: "No band chosen yet.")
        Text("In the Google Health app, go to Connections, then Fitbit Air, and turn on Share heart rate. Then scan.",
            style = HhType.Small, color = Hh.Mist)
        QuietButton(if (scanning) "Scanning…" else "Scan for Fitbit Air", enabled = !scanning, onClick = {
            found.clear(); scanning = true
            scope.launch {
                withTimeoutOrNull(15_000) {
                    BandScanner(ctx).scan()
                        .catch { message = it.message }
                        .collect { r -> if (found.none { it.address == r.address }) found.add(r) }
                }
                scanning = false
            }
        }, modifier = Modifier.fillMaxWidth())
        found.forEach { r ->
            Row(Modifier.fillMaxWidth().clickable { scope.launch { prefs.saveBand(r.address, r.name) }; found.clear() }
                .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = HhType.Body, color = Hh.Moon)
                    Text(r.address, style = HhType.Small, color = Hh.Mist)
                }
                Text("Use", style = HhType.Label, color = Hh.Pulse)
            }
        }
    }
}

@Composable
private fun Group(title: String, note: String) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(title, style = HhType.Heading, color = Hh.Moon)
        Text(note, style = HhType.Small, color = Hh.Mist)
    }
}

private val FieldsSaver = listSaver<Map<String, String>?, String>(
    save = { m -> m?.flatMap { listOf(it.key, it.value) } ?: emptyList() },
    restore = { l -> if (l.isEmpty()) null else l.chunked(2).associate { it[0] to it[1] } },
)
