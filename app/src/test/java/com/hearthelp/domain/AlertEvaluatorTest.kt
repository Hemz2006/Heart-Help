package com.hearthelp.domain

import com.hearthelp.domain.AlertState.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {
    private val defaults = AlertSettings()
    private fun ev(s: AlertSettings = defaults) = AlertEvaluator(s, startedAt = 0)

    /** Feeds one reading every 2 s from [fromSec] to [toSec] inclusive. */
    private fun AlertEvaluator.feed(bpm: Int, fromSec: Int, toSec: Int) {
        for (t in fromSec..toSec step 2) onReading(bpm, t * 1000L)
    }

    private fun alarmAtTenSeconds(): AlertEvaluator = ev().apply { feed(160, 0, 10) }

    @Test fun inRangeStaysNormal() {
        val e = ev(); e.feed(80, 0, 30)
        assertEquals(Normal, e.state)
    }

    @Test fun singleHighReadingIsPendingThenResets() {
        val e = ev()
        assertEquals(BreachPending(Trigger.HIGH, 0), e.onReading(160, 0))
        assertEquals(Normal, e.onReading(80, 2000))
    }

    @Test fun sustainedHighForTenSecondsRaisesAlarm() {
        val e = ev(); e.feed(160, 0, 8)
        assertTrue(e.state is BreachPending)
        assertEquals(Alarm(Trigger.HIGH, 160, deadline = 30_000), e.onReading(160, 10_000))
    }

    @Test fun glitchInsideWindowRestartsTimer() {
        val e = ev()
        e.feed(160, 0, 6); e.onReading(80, 8000); e.feed(160, 10, 18)
        assertEquals(BreachPending(Trigger.HIGH, 10_000), e.state)
        assertTrue(e.onReading(160, 20_000) is Alarm)
    }

    @Test fun switchingHighToLowRestartsTimer() {
        val e = ev(); e.feed(160, 0, 6)
        assertEquals(BreachPending(Trigger.LOW, 8000), e.onReading(40, 8000))
    }

    @Test fun sustainedLowRaisesAlarm() {
        val e = ev(); e.feed(40, 0, 10)
        assertEquals(Alarm(Trigger.LOW, 40, deadline = 30_000), e.state)
    }

    @Test fun zeroBpmIsIgnored() {
        val e = ev(); e.feed(0, 0, 30)
        assertEquals(Normal, e.state)
        assertNull(e.lastBpm)
    }

    @Test fun countdownExpiryDispatches() {
        val e = alarmAtTenSeconds()
        assertTrue(e.onTick(29_999) is Alarm)
        assertEquals(Dispatched(Trigger.HIGH, 160), e.onTick(30_000))
    }

    @Test fun imOkSnoozesAndIgnoresBreachesUntilExpiry() {
        val e = alarmAtTenSeconds()
        assertEquals(Snoozed(until = 315_000), e.onImOk(15_000))
        e.feed(160, 16, 300)
        assertTrue(e.state is Snoozed)
        assertEquals(Normal, e.onTick(315_000))
    }

    @Test fun signalLostAfterTwoMinutesWithoutReadings() {
        val e = ev(); e.onReading(80, 0)
        assertEquals(Normal, e.onTick(119_999))
        assertEquals(SignalLost, e.onTick(120_000))
        assertEquals(Normal, e.onReading(80, 121_000))
    }

    @Test fun signalLossDoesNotCancelAlarmCountdown() {
        val e = ev(defaults.copy(countdownSeconds = 300, signalLostMinutes = 1))
        e.feed(160, 0, 10)
        assertTrue(e.onTick(100_000) is Alarm)
        assertEquals(Dispatched(Trigger.HIGH, 160), e.onTick(310_000))
    }

    @Test fun sosDispatchesImmediatelyWithLastBpm() {
        val e = ev(); e.onReading(80, 0)
        assertEquals(Dispatched(Trigger.SOS, 80), e.onSos())
    }

    @Test fun sosWhileDispatchedDoesNothing() {
        val e = alarmAtTenSeconds(); e.onTick(30_000)
        assertEquals(Dispatched(Trigger.HIGH, 160), e.onSos())
    }

    @Test fun dismissReturnsToNormal() {
        val e = ev(); e.onSos()
        assertEquals(Normal, e.onDismiss())
    }

    @Test fun dismissOutsideDispatchedDoesNothing() {
        val e = alarmAtTenSeconds()
        assertTrue(e.onDismiss() is Alarm)
    }

    @Test fun testAlarmDispatchesAsTest() {
        val e = ev(); e.onReading(75, 0)
        assertEquals(Alarm(Trigger.TEST, 75, deadline = 21_000), e.startTest(1000))
        assertEquals(Dispatched(Trigger.TEST, 75), e.onTick(21_000))
    }

    @Test fun imOkOnTestAlarmDoesNotSnooze() {
        val e = ev(); e.startTest(0)
        assertEquals(Normal, e.onImOk(1000))
    }

    @Test fun newSettingsApplyToNextReading() {
        val e = ev()
        e.settings = defaults.copy(highBpm = 100)
        assertEquals(BreachPending(Trigger.HIGH, 0), e.onReading(110, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun lowMustBeBelowHigh() { AlertSettings(lowBpm = 100, highBpm = 90) }
}
