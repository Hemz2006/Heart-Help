package com.hearthelp.domain

import com.hearthelp.domain.CallChain.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchLogicTest {
    // --- AlertMessage
    @Test fun highWithLocation() = assertEquals(
        "HeartHelp ALERT: Alex's heart rate is HIGH (152 bpm) at 14:32. " +
            "Location: https://maps.google.com/?q=12.97161,77.59456 Reply OK if you're handling this.",
        AlertMessage.build("Alex", Trigger.HIGH, 152, "14:32", 12.971612, 77.594561))

    @Test fun lowWithoutLocation() = assertEquals(
        "HeartHelp ALERT: Alex's heart rate is LOW (38 bpm) at 03:05. " +
            "Location unavailable. Reply OK if you're handling this.",
        AlertMessage.build("Alex", Trigger.LOW, 38, "03:05", null, null))

    @Test fun sosWithoutBpm() = assertEquals(
        "HeartHelp ALERT: Alex pressed the SOS button at 09:00. " +
            "Location unavailable. Reply OK if you're handling this.",
        AlertMessage.build("Alex", Trigger.SOS, null, "09:00", null, null))

    @Test fun testMessage() = assertEquals(
        "HeartHelp ALERT: TEST from Alex's HeartHelp app (75 bpm) at 10:00. " +
            "Location unavailable. No action needed.",
        AlertMessage.build("Alex", Trigger.TEST, 75, "10:00", null, null))

    @Test fun blankNameUsesFallback() = assertTrue(
        AlertMessage.build(" ", Trigger.HIGH, 150, "10:00", null, null).contains("Your contact's heart rate"))

    // --- SmsReplies
    @Test fun okVariantsAccepted() {
        listOf("OK", "ok on my way", "Okay", "OK.", "calling you, ok").forEach { assertTrue(it, SmsReplies.isOk(it)) }
    }
    @Test fun nonOkRejected() {
        listOf("book a taxi", "No", "", "token").forEach { assertFalse(it, SmsReplies.isOk(it)) }
    }

    // --- CallChain
    private val nums = listOf("a", "b", "c")

    @Test fun startsWithFirst() = assertEquals(Step.Call(0, "a"), CallChain(nums).start())

    @Test fun unansweredMovesOn() {
        val c = CallChain(nums); c.start()
        assertEquals(Step.Call(1, "b"), c.onCallEnded(0))
    }
    @Test fun shortPickupMovesOn() {
        val c = CallChain(nums); c.start()
        assertEquals(Step.Call(1, "b"), c.onCallEnded(19))
    }
    @Test fun answeredStops() {
        val c = CallChain(nums); c.start()
        assertEquals(Step.Done, c.onCallEnded(20))
        assertTrue(c.isStopped)
    }
    @Test fun exhaustedAfterLast() {
        val c = CallChain(nums); c.start(); c.onCallEnded(0); c.onCallEnded(0)
        assertEquals(Step.Done, c.onCallEnded(0))
    }
    @Test fun stopDuringCallEndsChain() {
        val c = CallChain(nums); c.start(); c.stop()
        assertEquals(Step.Done, c.onCallEnded(0))
    }
    @Test fun emptyListIsDone() = assertEquals(Step.Done, CallChain(emptyList()).start())
}
