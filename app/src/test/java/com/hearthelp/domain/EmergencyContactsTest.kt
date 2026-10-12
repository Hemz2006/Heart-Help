package com.hearthelp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyContactsTest {
    private val a = EmergencyContact("A", "9876543210")
    private val b = EmergencyContact("B", "9123456780")

    @Test fun sameNumberAcrossFormats() {
        assertTrue(PhoneNumbers.same("+91 98765 43210", "09876543210"))
        assertTrue(PhoneNumbers.same("+1 (555) 123-4567", "5551234567"))
    }
    @Test fun differentNumbers() = assertFalse(PhoneNumbers.same("9876543210", "9876543211"))
    @Test fun tooShortNeverMatches() = assertFalse(PhoneNumbers.same("123", "123"))

    @Test fun addAppends() = assertEquals(listOf(a, b), EmergencyContacts.add(listOf(a), b))

    @Test fun blankNameFallsBackToNumber() =
        assertEquals(listOf(EmergencyContact("9876543210", "9876543210")),
            EmergencyContacts.add(emptyList(), EmergencyContact("  ", "9876543210")))

    @Test(expected = IllegalArgumentException::class)
    fun sixthContactRejected() {
        val five = (0 until 5).map { EmergencyContact("C$it", "98765432${it}0") }
        EmergencyContacts.add(five, EmergencyContact("X", "9000000000"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidNumberRejected() { EmergencyContacts.add(emptyList(), EmergencyContact("X", "12ab")) }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateInOtherFormatRejected() {
        EmergencyContacts.add(listOf(a), EmergencyContact("A2", "+91 98765 43210"))
    }

    @Test fun moveUpSwaps() = assertEquals(listOf(b, a), EmergencyContacts.moveUp(listOf(a, b), 1))
    @Test fun moveUpFirstUnchanged() = assertEquals(listOf(a, b), EmergencyContacts.moveUp(listOf(a, b), 0))
    @Test fun removeByIndex() = assertEquals(listOf(b), EmergencyContacts.remove(listOf(a, b), 0))
}
