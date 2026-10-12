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
        MonitorStatus.flow.value = MonitorSnapshot(running = true, state = next, bpm = evaluator.lastBpm)
        if (next != prev) onStateChanged(prev, next)
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
