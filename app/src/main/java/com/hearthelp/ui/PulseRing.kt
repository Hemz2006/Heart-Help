package com.hearthelp.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hearthelp.ui.theme.Hh

/**
 * A thin ring that breathes once per heartbeat at the live [bpm]. Still when [bpm] is null
 * (no signal) or when the phone's animations are turned off.
 */
@Composable
fun PulseRing(bpm: Int?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val motionOff = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val beat = remember { Animatable(1f) }
    val currentBpm by rememberUpdatedState(bpm)
    val animate = bpm != null && !motionOff
    LaunchedEffect(animate) {
        if (!animate) { beat.snapTo(1f); return@LaunchedEffect }
        while (true) {
            val period = (60_000 / (currentBpm ?: 60).coerceIn(30, 220))
            beat.snapTo(0f)
            beat.animateTo(1f, tween(period, easing = LinearOutSlowInEasing))
        }
    }
    Canvas(modifier) {
        val r = size.minDimension / 2 * 0.78f
        val stroke = 2.dp.toPx()
        drawCircle(Hh.Dusk, radius = r, style = Stroke(stroke))
        if (animate) {
            val p = beat.value
            drawCircle(Hh.Pulse.copy(alpha = (1 - p) * 0.7f), radius = r * (1 + 0.22f * p), style = Stroke(stroke))
        }
    }
}
