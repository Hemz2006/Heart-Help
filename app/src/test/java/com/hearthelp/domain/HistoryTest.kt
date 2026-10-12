package com.hearthelp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class HistoryTest {
    private val min = 60_000L

    // --- MinuteAggregator
    @Test fun firstMinuteIsNotEmittedUntilNextMinuteStarts() {
        val a = MinuteAggregator()
        assertNull(a.add(70, 0))
        assertNull(a.add(80, 30_000))
        assertEquals(MinuteSample(0, 70, 75, 80, 2), a.add(90, min))
    }

    @Test fun averageIsRounded() {
        val a = MinuteAggregator()
        a.add(70, 0); a.add(71, 1000)
        assertEquals(MinuteSample(0, 70, 71, 71, 2), a.flush())
    }

    @Test fun minuteBoundaryUsesWallClockMinute() {
        val a = MinuteAggregator()
        a.add(60, 125_000) // 2 min 5 s → minute starting at 120_000
        assertEquals(120_000L, a.flush()!!.minuteStart)
    }

    @Test fun zeroBpmIgnored() {
        val a = MinuteAggregator()
        assertNull(a.add(0, 0))
        assertNull(a.flush())
    }

    @Test fun flushEmptiesAggregator() {
        val a = MinuteAggregator()
        a.add(70, 0); a.flush()
        assertNull(a.flush())
    }

    @Test fun gapEmitsLastMinuteBeforeGap() {
        val a = MinuteAggregator()
        a.add(70, 0)
        assertEquals(0L, a.add(75, 60 * min)!!.minuteStart)
    }

    // --- HistorySeries.merge
    @Test fun mergeWeightsAverageByCount() =
        assertEquals(MinuteSample(0, 60, 70, 100, 4),
            HistorySeries.merge(MinuteSample(0, 60, 65, 70, 3), MinuteSample(0, 80, 85, 100, 1)))

    // --- HistorySeries.downsample
    private fun s(minute: Int, avg: Int, count: Int = 1) = MinuteSample(minute * min, avg, avg, avg, count)

    @Test fun shortSeriesUnchanged() {
        val list = listOf(s(0, 70), s(1, 72))
        assertEquals(list, HistorySeries.downsample(list, 0, 10 * min, 300))
    }

    @Test fun longSeriesBucketedToMaxPoints() {
        val list = (0 until 1000).map { s(it, 60 + it % 10) }
        val out = HistorySeries.downsample(list, 0, 1000 * min, 100)
        assertEquals(100, out.size)
        assertEquals(10 * min, out[1].minuteStart - out[0].minuteStart)
    }

    @Test fun bucketKeepsExtremesAndWeightedAverage() {
        val list = listOf(MinuteSample(0, 50, 60, 70, 1), MinuteSample(min, 90, 100, 140, 3))
        val out = HistorySeries.downsample(list + (2 until 10).map { s(it, 80) }, 0, 10 * min, 5)
        assertEquals(MinuteSample(0, 50, 90, 140, 4), out[0])
    }

    @Test fun emptyBucketsLeaveGaps() {
        val list = (0 until 10).map { s(it, 70) } + (90 until 100).map { s(it, 80) }
        val out = HistorySeries.downsample(list, 0, 100 * min, 10)
        assertEquals(listOf(0L, 90 * min), out.map { it.minuteStart })
    }

    // --- HistorySeries.segments
    @Test fun segmentsSplitOnGaps() {
        val list = listOf(s(0, 70), s(1, 70), s(5, 70), s(6, 70))
        assertEquals(listOf(2, 2), HistorySeries.segments(list, maxGapMs = 2 * min).map { it.size })
    }

    @Test fun noSegmentsForEmpty() = assertEquals(emptyList<List<MinuteSample>>(), HistorySeries.segments(emptyList(), min))

    // --- HistorySeries.summary
    @Test fun summaryAcrossSamples() =
        assertEquals(HistorySeries.Summary(avg = 75, min = 50, max = 140),
            HistorySeries.summary(listOf(MinuteSample(0, 50, 60, 70, 1), MinuteSample(min, 90, 80, 140, 3))))

    @Test fun summaryOfNothingIsNull() = assertNull(HistorySeries.summary(emptyList()))

    // --- HistorySeries.csv
    @Test fun csvFormat() = assertEquals(
        "time,min_bpm,avg_bpm,max_bpm,readings\n" +
            "1970-01-01 00:00,60,65,70,3\n" +
            "1970-01-01 00:01,80,85,100,1\n",
        HistorySeries.csv(listOf(MinuteSample(0, 60, 65, 70, 3), MinuteSample(min, 80, 85, 100, 1)), ZoneOffset.UTC))
}
