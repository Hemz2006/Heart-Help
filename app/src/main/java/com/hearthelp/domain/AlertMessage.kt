package com.hearthelp.domain

import java.util.Locale

object AlertMessage {
    fun build(name: String, trigger: Trigger, bpm: Int?, time: String, lat: Double?, lng: Double?): String {
        val who = name.trim().ifBlank { "Your contact" }
        val what = when (trigger) {
            Trigger.HIGH -> "$who's heart rate is HIGH"
            Trigger.LOW -> "$who's heart rate is LOW"
            Trigger.SOS -> "$who pressed the SOS button"
            Trigger.TEST -> "TEST from $who's HeartHelp app"
        }
        val bpmPart = bpm?.let { " ($it bpm)" } ?: ""
        val location = if (lat != null && lng != null) {
            " Location: https://maps.google.com/?q=" + String.format(Locale.US, "%.5f,%.5f", lat, lng)
        } else " Location unavailable."
        val ask = if (trigger == Trigger.TEST) " No action needed." else " Reply OK if you're handling this."
        return "HeartHelp ALERT: $what$bpmPart at $time.$location$ask"
    }
}
