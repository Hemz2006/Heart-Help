package com.hearthelp.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
    val openedAt = remember { System.currentTimeMillis() }
    var seenActive by remember { mutableStateOf(false) }
    val active = snap.state is AlertState.Alarm || snap.state is AlertState.Dispatched
    if (active) seenActive = true

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
            else -> LaunchedEffect(s, seenActive) {
                // The service may launch this screen just before it publishes the Alarm state.
                if (!seenActive) delay((openedAt + 3000 - System.currentTimeMillis()).coerceAtLeast(0))
                if (MonitorStatus.flow.value.state.let { it !is AlertState.Alarm && it !is AlertState.Dispatched }) onDone()
            }
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
