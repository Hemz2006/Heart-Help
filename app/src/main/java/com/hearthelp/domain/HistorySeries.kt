package com.hearthelp.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pure helpers that turn stored minutes into graph points, summaries and CSV. */
object HistorySeries {
    data class Summary(val avg: Int, val min: Int, val max: Int)

    /** Combines two samples of the same minute (count-weighted average). */
    fun merge(a: MinuteSample, b: MinuteSample): MinuteSample {
        val n = a.count + b.count
        return MinuteSample(a.minuteStart, minOf(a.min, b.min),
            ((a.avg.toLong() * a.count + b.avg.toLong() * b.count + n / 2) / n).toInt(),
            maxOf(a.max, b.max), n)
    }

    /**
     * Reduces [samples] (sorted by time) to at most [maxPoints] equal-width buckets over [from, to).
     * Each bucket keeps its lowest min, highest max and count-weighted average. Empty buckets are
     * omitted so gaps stay visible.
     */
    fun downsample(samples: List<MinuteSample>, from: Long, to: Long, maxPoints: Int): List<MinuteSample> {
        if (samples.size <= maxPoints) return samples
        val width = maxOf(60_000L, ((to - from + maxPoints - 1) / maxPoints + 59_999) / 60_000 * 60_000)
        return samples.groupBy { (it.minuteStart - from) / width }
            .toSortedMap()
            .map { (bucket, items) ->
                items.reduce(::merge).copy(minuteStart = from + bucket * width)
            }
    }

    /** Splits points into runs; a new run starts when two points are more than [maxGapMs] apart. */
    fun segments(points: List<MinuteSample>, maxGapMs: Long): List<List<MinuteSample>> {
        val out = mutableListOf<MutableList<MinuteSample>>()
        for (p in points) {
            val last = out.lastOrNull()?.last()
            if (last == null || p.minuteStart - last.minuteStart > maxGapMs) out.add(mutableListOf(p))
            else out.last().add(p)
        }
        return out
    }

    fun summary(samples: List<MinuteSample>): Summary? {
        if (samples.isEmpty()) return null
        val all = samples.reduce(::merge)
        return Summary(avg = all.avg, min = all.min, max = all.max)
    }

    fun csv(samples: List<MinuteSample>, zone: ZoneId): String {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
        return buildString {
            append("time,min_bpm,avg_bpm,max_bpm,readings\n")
            for (s in samples) {
                append(fmt.format(Instant.ofEpochMilli(s.minuteStart)))
                append(',').append(s.min).append(',').append(s.avg).append(',').append(s.max)
                append(',').append(s.count).append('\n')
            }
        }
    }
}
