package com.hearthelp.domain

enum class Trigger { HIGH, LOW, SOS, TEST }

sealed interface AlertState {
    data object Normal : AlertState
    data class BreachPending(val trigger: Trigger, val since: Long) : AlertState
    data class Alarm(val trigger: Trigger, val bpm: Int?, val deadline: Long) : AlertState
    data class Dispatched(val trigger: Trigger, val bpm: Int?) : AlertState
    data class Snoozed(val until: Long) : AlertState
    data object SignalLost : AlertState
}
