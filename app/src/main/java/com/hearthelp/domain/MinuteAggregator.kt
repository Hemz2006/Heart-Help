package com.hearthelp.domain

/** One minute of heart-rate history. [minuteStart] is epoch ms at the start of the minute. */
data class MinuteSample(val minuteStart: Long, val min: Int, val avg: Int, val max: Int, val count: Int)

/** Collects readings and emits a [MinuteSample] each time a minute is complete. Not thread-safe. */
class MinuteAggregator {
    private var minute = -1L
    private var sum = 0L
    private var count = 0
    private var min = 0
    private var max = 0

    /** Adds a reading; returns the previous minute once a reading arrives in a new minute. */
    fun add(bpm: Int, now: Long): MinuteSample? {
        if (bpm <= 0) return null
        val m = now - now % 60_000
        val done = if (m != minute) flush() else null
        minute = m
        sum += bpm
        min = if (count == 0) bpm else minOf(min, bpm)
        max = if (count == 0) bpm else maxOf(max, bpm)
        count++
        return done
    }

    /** Returns the minute in progress (if any) and starts afresh. */
    fun flush(): MinuteSample? {
        if (count == 0) return null
        val s = MinuteSample(minute, min, ((sum + count / 2) / count).toInt(), max, count)
        sum = 0; count = 0
        return s
    }
}
