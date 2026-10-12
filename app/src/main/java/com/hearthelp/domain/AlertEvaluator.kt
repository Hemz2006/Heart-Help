package com.hearthelp.domain

import com.hearthelp.domain.AlertState.*

/** Pure alert state machine. Not thread-safe: call from one thread only. */
class AlertEvaluator(var settings: AlertSettings, startedAt: Long) {
    var state: AlertState = Normal
        private set
    var lastBpm: Int? = null
        private set
    private var lastReadingAt = startedAt

    fun onReading(bpm: Int, now: Long): AlertState {
        if (bpm <= 0) return state
        lastReadingAt = now
        lastBpm = bpm
        val s = state
        state = when (s) {
            is Alarm, is Dispatched, is Snoozed -> s
            Normal, SignalLost -> breachOf(bpm)?.let { BreachPending(it, now) } ?: Normal
            is BreachPending -> {
                val t = breachOf(bpm)
                when {
                    t == null -> Normal
                    t != s.trigger -> BreachPending(t, now)
                    now - s.since >= settings.sustainSeconds * 1000L ->
                        Alarm(t, bpm, now + settings.countdownSeconds * 1000L)
                    else -> s
                }
            }
        }
        return state
    }

    fun onTick(now: Long): AlertState {
        val s = state
        state = when {
            s is Alarm && now >= s.deadline -> Dispatched(s.trigger, s.bpm)
            s is Alarm || s is Dispatched -> s
            now - lastReadingAt >= settings.signalLostMinutes * 60_000L -> SignalLost
            s is Snoozed && now >= s.until -> Normal
            else -> s
        }
        return state
    }

    fun onImOk(now: Long): AlertState {
        val s = state
        if (s is Alarm) {
            state = if (s.trigger == Trigger.TEST) Normal
            else Snoozed(now + settings.snoozeMinutes * 60_000L)
        }
        return state
    }

    fun onSos(): AlertState {
        if (state !is Dispatched) state = Dispatched(Trigger.SOS, lastBpm)
        return state
    }

    fun onDismiss(): AlertState {
        if (state is Dispatched) state = Normal
        return state
    }

    fun startTest(now: Long): AlertState {
        if (state !is Alarm && state !is Dispatched) {
            state = Alarm(Trigger.TEST, lastBpm, now + settings.countdownSeconds * 1000L)
        }
        return state
    }

    /**
     * Re-enters an alert saved before the process was killed. Only Alarm and Dispatched are restored;
     * any other saved state leaves the current state unchanged. An Alarm whose deadline passed while
     * the app was dead becomes Dispatched (nobody answered it).
     */
    fun restore(saved: AlertState, now: Long): AlertState {
        state = when (saved) {
            is Alarm -> if (now >= saved.deadline) Dispatched(saved.trigger, saved.bpm) else saved
            is Dispatched -> saved
            else -> state
        }
        return state
    }

    private fun breachOf(bpm: Int): Trigger? = when {
        bpm < settings.lowBpm -> Trigger.LOW
        bpm > settings.highBpm -> Trigger.HIGH
        else -> null
    }
}
