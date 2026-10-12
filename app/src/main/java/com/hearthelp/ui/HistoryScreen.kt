package com.hearthelp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.app
import com.hearthelp.domain.AlertSettings
import com.hearthelp.domain.HistorySeries
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

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Heart-rate history", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Range.entries.forEach { r ->
                if (r == range) Button(onClick = {}) { Text(r.label) }
                else OutlinedButton(onClick = { range = r }) { Text(r.label) }
            }
        }
        Text(summary?.let { "Average ${it.avg} bpm · lowest ${it.min} · highest ${it.max}" }
            ?: "No readings in this period yet.")
        Canvas(Modifier.fillMaxWidth().height(260.dp)) {
            drawIntoCanvas {
                HeartRateChart.draw(it.nativeCanvas, size.width, size.height, density,
                    points, from, now, settings.lowBpm, settings.highBpm)
            }
        }
        Text("Red line: average per minute. Shaded: lowest to highest. Dashed: your limits. " +
            "Gaps mean no readings (band off or out of range).", style = MaterialTheme.typography.bodySmall)
        Button(
            enabled = samples.isNotEmpty() && !sharing,
            onClick = {
                sharing = true
                scope.launch {
                    HistoryShare.share(ctx, samples, points, from, now, settings.lowBpm, settings.highBpm, range.label)
                    sharing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (sharing) "Preparing…" else "Share ${range.label} (CSV + graph image)") }
    }
}
