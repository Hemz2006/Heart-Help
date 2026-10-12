package com.hearthelp.monitor

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
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
import com.hearthelp.domain.Trigger
import com.hearthelp.emergency.EmergencyDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
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
        /** Periodic tick from AlarmManager: keeps signal-lost/countdown checks running while the CPU sleeps. */
        const val ACTION_TICK = "com.hearthelp.TICK"
        private val startsMonitoring = setOf(ACTION_START, ACTION_SOS, ACTION_TEST)
        private const val TAG = "MonitoringService"
        private const val TICK_INTERVAL_MS = 60_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 30 * 60_000L

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
    private var lastRetryMsg: String? = null
    private var signalLostAt = 0L
    private var signalLostActive = false
    private var restoringDispatched = false
    private var wakeLock: PowerManager.WakeLock? = null

    /** Last line of defence: an unexpected error is logged instead of crashing the process mid-alert. */
    private val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "Unexpected error", e) }

    private fun launchSafe(block: suspend CoroutineScope.() -> Unit) = lifecycleScope.launch(crashGuard, block = block)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: ACTION_START // null = restarted by the system (START_STICKY)
        if (!started) {
            if (action !in startsMonitoring) { stopSelf(); return START_NOT_STICKY }
            if (!begin()) return START_NOT_STICKY
        }
        when (action) {
            ACTION_STOP -> {
                if (evaluator.state.isActiveAlert()) {
                    log("Monitoring", "Stop ignored — alert in progress (tap I'm OK or Dismiss first)")
                    return START_STICKY
                }
                launchSafe {
                    try {
                        app.prefs.setMonitoringEnabled(false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not save monitoring=off", e)
                    }
                    safeLog("Monitoring", "Stopped")
                    stopSelf()
                }
            }
            ACTION_IM_OK -> transition { onImOk(now()) }
            ACTION_DISMISS -> { dispatchJob?.cancel(); transition { onDismiss() } }
            ACTION_SOS -> transition { onSos() }
            ACTION_TEST -> transition { startTest(now()) }
            ACTION_TICK -> {
                transition { onTick(now()) }
                scheduleTick()
                if (evaluator.state.isActiveAlert()) holdWakeLock() // renews the 30-min timeout
            }
        }
        return START_STICKY
    }

    private fun begin(): Boolean {
        try {
            ServiceCompat.startForeground(this, Notifications.ONGOING_ID, Notifications.ongoing(this, "Connecting to band…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: Exception) {
            Notifications.warning(this, listOf("Monitoring could not start: ${e.message}"))
            // Own scope: lifecycleScope is cancelled once the service is destroyed after stopSelf().
            val prefs = app.prefs
            val log = app.log
            CoroutineScope(Dispatchers.IO + crashGuard).launch {
                prefs.setMonitoringEnabled(false)
                log.add("Monitoring", "Could not start: ${e.message}")
            }
            stopSelf()
            return false
        }
        started = true
        alarm = AlarmController(this)
        dispatcher = EmergencyDispatcher(this, app.prefs, app.log)
        evaluator = AlertEvaluator(AlertSettings(), now())
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartHelp:alert").apply { setReferenceCounted(false) }
        MonitorStatus.flow.value = MonitorSnapshot(running = true)

        launchSafe {
            try {
                app.prefs.setMonitoringEnabled(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not save monitoring=on", e)
            }
            safeLog("Monitoring", "Started")
        }
        launchSafe { restoreAlert() }
        launchSafe {
            app.prefs.settings
                .catch { e -> Log.e(TAG, "Settings unreadable — keeping current values", e) }
                .collect { evaluator.settings = it }
        }
        launchSafe {
            app.prefs.bandAddress.filterNotNull().collectLatest { address ->
                HeartRateSource(this@MonitoringService).readings(address)
                    .retryWhen { cause, _ ->
                        val msg = cause.message ?: "Connection error"
                        if (msg != lastRetryMsg) { lastRetryMsg = msg; safeLog("Bluetooth", msg) }
                        delay(10_000)
                        true
                    }
                    .collect { bpm -> lastRetryMsg = null; transition { onReading(bpm, now()) }; onSignalBack() }
            }
        }
        // Fast in-process tick for the countdown while awake; ACTION_TICK keeps checks going while the CPU sleeps.
        launchSafe {
            while (isActive) { transition { onTick(now()) }; delay(1_000) }
        }
        scheduleTick()
        launchSafe {
            while (isActive) {
                try {
                    Notifications.warning(this@MonitoringService, Readiness.problems(this@MonitoringService, app.prefs))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Readiness check failed", e)
                }
                delay(60_000)
            }
        }
        return true
    }

    /** Re-enters an Alarm/Dispatched alert that was active when the process was killed or updated. */
    private suspend fun restoreAlert() {
        val saved = try {
            app.prefs.activeAlert()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not read saved alert", e); null
        } ?: return
        if (evaluator.state.isActiveAlert()) return // a new alert (e.g. SOS) already took over
        if (saved is Dispatched && saved.trigger == Trigger.TEST) {
            // A test returns to monitoring when its dispatch ends anyway; don't resurrect it.
            log("Monitoring", "Test alert interrupted by a restart — not restored")
            persistAlert(Normal)
            return
        }
        log("Monitoring", "Alert restored after restart")
        // A restored Dispatched must not text the contacts a second time.
        restoringDispatched = saved is Dispatched
        try { transition { restore(saved, now()) } } finally { restoringDispatched = false }
    }

    private fun tickIntent() = PendingIntent.getService(this, 20,
        Intent(this, MonitoringService::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Inexact, Doze-tolerant wake-up; needs no exact-alarm permission. Re-armed on every fire. */
    private fun scheduleTick() {
        try {
            getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + TICK_INTERVAL_MS, tickIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Could not schedule tick", e)
        }
    }

    private fun holdWakeLock() { wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS) }
    private fun releaseWakeLock() { wakeLock?.let { if (it.isHeld) it.release() } }

    private fun AlertState.isActiveAlert() = this is Alarm || this is Dispatched

    /** Saves Alarm/Dispatched so a process kill doesn't lose the alert; any other state clears it. */
    private fun persistAlert(s: AlertState) {
        launchSafe {
            try {
                app.prefs.saveActiveAlert(s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not save alert state", e)
            }
        }
    }

    private fun startDispatch(d: Dispatched) {
        dispatchJob = launchSafe {
            try {
                dispatcher.dispatch(d.trigger, d.bpm)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                safeLog("Error", "dispatch: ${e.message ?: e.javaClass.simpleName}")
            }
            // A test goes back to monitoring by itself once its texts and calls are done.
            if (d.trigger == Trigger.TEST && evaluator.state == d) transition { onDismiss() }
        }
    }

    /** Clears the signal-lost notice once readings flow again outside an alert. */
    private fun onSignalBack() {
        val s = evaluator.state
        if (!signalLostActive || s is SignalLost || s.isActiveAlert()) return
        signalLostActive = false
        Notifications.cancelSignalLost(this)
        val secs = ((now() - signalLostAt) / 1000).coerceAtLeast(0)
        log("Signal restored", "Readings resumed after ${secs / 60} min ${secs % 60} s")
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
        if (next.isActiveAlert() || prev.isActiveAlert()) persistAlert(next)
        // Keep the CPU awake for the countdown and the dispatcher's waits while an alert is active.
        if (next.isActiveAlert()) holdWakeLock() else releaseWakeLock()
        when (next) {
            is Alarm -> {
                alarm.start()
                log("Alarm", "${next.trigger} ${next.bpm ?: "-"} bpm — countdown started")
            }
            is Snoozed -> log("I'm OK", "Alerts paused until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(next.until))}")
            is Dispatched -> {
                if (prev is Alarm) alarm.notifyAlert(dispatched = true) else alarm.showScreen(dispatched = true)
                if (restoringDispatched) {
                    log("Emergency", "Contacts were already notified before the restart — not sending again")
                } else {
                    log("Emergency", "${next.trigger} ${next.bpm ?: "-"} bpm — notifying contacts")
                    startDispatch(next)
                }
            }
            SignalLost -> {
                val btOn = Readiness.bluetoothOn(this)
                Notifications.signalLost(this, btOn)
                if (!signalLostActive) {
                    signalLostActive = true
                    signalLostAt = now()
                    log("Signal lost", if (btOn) "No readings from band" else "Bluetooth is off")
                }
            }
            Normal -> when (prev) {
                is Dispatched -> if (prev.trigger == Trigger.TEST) log("Test", "Test alert ended — monitoring resumed")
                    else log("Dismissed", "User stopped the alert")
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

    /** Event-log writes never throw: a failed insert must not take down an alert. */
    private suspend fun safeLog(type: String, detail: String) {
        try {
            app.log.add(type, detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Event log write failed: $type — $detail", e)
        }
    }

    private fun log(type: String, detail: String) { launchSafe { safeLog(type, detail) } }
    private fun now() = System.currentTimeMillis()

    override fun onDestroy() {
        if (started) {
            if (evaluator.state.isActiveAlert()) {
                val log = app.log
                CoroutineScope(Dispatchers.IO + crashGuard).launch {
                    log.add("Monitoring", "Service stopped while an alert was active — alert interrupted")
                }
            }
            alarm.clear()
            releaseWakeLock()
            runCatching { getSystemService(AlarmManager::class.java).cancel(tickIntent()) }
        }
        Notifications.cancelSignalLost(this)
        MonitorStatus.flow.value = MonitorSnapshot(running = false)
        super.onDestroy()
    }
}
