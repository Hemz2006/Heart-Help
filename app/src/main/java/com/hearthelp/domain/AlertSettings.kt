package com.hearthelp.domain

data class AlertSettings(
    val lowBpm: Int = 45,
    val highBpm: Int = 140,
    val sustainSeconds: Int = 10,
    val countdownSeconds: Int = 20,
    val snoozeMinutes: Int = 5,
    val signalLostMinutes: Int = 2,
) {
    init {
        require(lowBpm in 20..120) { "Low limit must be between 20 and 120 bpm" }
        require(highBpm in 60..250) { "High limit must be between 60 and 250 bpm" }
        require(lowBpm < highBpm) { "Low limit must be below the high limit" }
        require(sustainSeconds in 2..300) { "Sustain time must be 2–300 seconds" }
        require(countdownSeconds in 5..300) { "Countdown must be 5–300 seconds" }
        require(snoozeMinutes in 1..60) { "Pause after I'm OK must be 1–60 minutes" }
        require(signalLostMinutes in 1..60) { "Signal-lost time must be 1–60 minutes" }
    }
}
