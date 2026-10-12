package com.hearthelp.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearthelp.domain.AlertState
import com.hearthelp.domain.Trigger
import com.hearthelp.monitor.MonitorStatus
import com.hearthelp.ui.theme.HeartHelpTheme
import com.hearthelp.ui.theme.Hh
import com.hearthelp.ui.theme.HhType
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            HeartHelpTheme {
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
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(100) } }
    val openedAt = remember { System.currentTimeMillis() }
    var seenActive by remember { mutableStateOf(false) }
    val active = snap.state is AlertState.Alarm || snap.state is AlertState.Dispatched
    if (active) seenActive = true

    Column(
        Modifier.fillMaxSize().background(Hh.Alarm).systemBarsPadding().padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
        when (val s = snap.state) {
            is AlertState.Alarm -> {
                // Total length is fixed by the first deadline seen, so the ring drains smoothly.
                val total = remember(s.deadline) { (s.deadline - System.currentTimeMillis()).coerceAtLeast(1) }
                val left = (s.deadline - now).coerceAtLeast(0)
                Text(headline(s.trigger), style = HhType.Title, color = Hh.Moon)
                s.bpm?.let {
                    Text("$it bpm", style = HhType.Title.copy(fontFeatureSettings = "tnum"), color = Hh.Moon.copy(alpha = 0.8f))
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.align(Alignment.CenterHorizontally).size(220.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 6.dp.toPx()
                        drawArc(Color.White.copy(alpha = 0.2f), 0f, 360f, false, style = Stroke(w))
                        drawArc(Color.White, -90f, 360f * left / total, false, style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Text("${(left + 999) / 1000}", style = HhType.Reading, color = Hh.Moon)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    if (s.trigger == Trigger.TEST) "This is a test. Only you will be texted and called when this reaches 0."
                    else "Your emergency contacts will be texted and called when this reaches 0.",
                    style = HhType.Body, color = Hh.Moon,
                )
                Spacer(Modifier.weight(1f))
                AnswerButton("I'm OK", onImOk)
            }
            is AlertState.Dispatched -> {
                Text("Contacting your emergency contacts", style = HhType.Title, color = Hh.Moon)
                Spacer(Modifier.height(8.dp))
                Text("They've been texted your location. Calls go down your list until someone answers or replies OK.",
                    style = HhType.Body, color = Hh.Moon.copy(alpha = 0.85f))
                Spacer(Modifier.weight(1f))
                AnswerButton("I'm safe, stop the alert", onDismiss)
            }
            else -> LaunchedEffect(s, seenActive) {
                // The service may launch this screen just before it publishes the Alarm state.
                if (!seenActive) delay((openedAt + 3000 - System.currentTimeMillis()).coerceAtLeast(0))
                if (MonitorStatus.flow.value.state.let { it !is AlertState.Alarm && it !is AlertState.Dispatched }) onDone()
            }
        }
    }
}

@Composable
private fun AnswerButton(text: String, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Hh.Moon, contentColor = Hh.Alarm)) {
        Text(text, style = HhType.Title)
    }
}

private fun headline(trigger: Trigger) = when (trigger) {
    Trigger.HIGH -> "Heart rate high"
    Trigger.LOW -> "Heart rate low"
    Trigger.SOS -> "SOS"
    Trigger.TEST -> "Test alert"
}
