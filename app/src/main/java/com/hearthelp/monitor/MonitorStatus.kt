package com.hearthelp.monitor

import com.hearthelp.domain.AlertState
import kotlinx.coroutines.flow.MutableStateFlow

data class MonitorSnapshot(
    val running: Boolean = false,
    val state: AlertState = AlertState.Normal,
    val bpm: Int? = null,
)

/** Live status published by MonitoringService and read by the UI. */
object MonitorStatus {
    val flow = MutableStateFlow(MonitorSnapshot())
}
