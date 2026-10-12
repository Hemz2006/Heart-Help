package com.hearthelp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.app
import com.hearthelp.domain.AlertSettings
import com.hearthelp.domain.HistorySeries
import com.hearthelp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Range(val label: String, val ms: Long) {
    H6("6 h", 6 * 3_600_000L), H24("24 h", 24 * 3_600_000L), D7("7 d", 7 * 86_400_000L),
    D30("30 d", 30 * 86_400_000L), D90("90 d", 90 * 86_400_000L),
}

@Composable
fun HistoryScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var range by rememberSaveable { mutableStateOf(Range.H24) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }

    val from = now - range.ms
    val samples by remember(range, now) { ctx.app.history.range(from, now) }
        .collectAsStateWithLifecycle(emptyList())
    val settings by ctx.app.prefs.settings.collectAsStateWithLifecycle(AlertSettings())
    val points = remember(samples, from, now) { HistorySeries.downsample(samples, from, now, 300) }
    val summary = remember(samples) { HistorySeries.summary(samples) }
    var sharing by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("History", style = HhType.Title, color = Hh.Moon)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Range.entries.forEach { r ->
                val selected = r == range
                Text(r.label, style = HhType.Label, color = if (selected) Hh.Moon else Hh.Mist,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (selected) Hh.Dusk else Color.Transparent)
                        .selectable(selected, role = Role.Tab) { range = r }
                        .padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        val s = summary
        if (s != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(top = 4.dp)) {
                Stat("${s.avg}", "average")
                Stat("${s.min}", "lowest")
                Stat("${s.max}", "highest")
            }
        } else {
            Text("No readings in this period yet. History fills in while monitoring is on.",
                style = HhType.Body, color = Hh.Moon)
        }
        Canvas(Modifier.fillMaxWidth().height(260.dp)) {
            drawIntoCanvas {
                HeartRateChart.draw(it.nativeCanvas, size.width, size.height, density,
                    points, from, now, settings.lowBpm, settings.highBpm, dark = true)
            }
        }
        Text("The line is your average each minute; the shaded area spans your lowest to highest. " +
            "Dashed lines are your limits. Gaps mean the band wasn't sending.", style = HhType.Small, color = Hh.Mist)
        QuietButton(if (sharing) "Preparing…" else "Share ${range.label} with your doctor",
            enabled = samples.isNotEmpty() && !sharing,
            onClick = {
                sharing = true
                scope.launch {
                    HistoryShare.share(ctx, samples, points, from, now, settings.lowBpm, settings.highBpm, range.label)
                    sharing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Sends a spreadsheet (CSV) and a picture of the graph.", style = HhType.Small, color = Hh.Mist)
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = HhType.Title.copy(fontFeatureSettings = "tnum"), color = Hh.Moon)
        Text(label, style = HhType.Small, color = Hh.Mist)
    }
}
