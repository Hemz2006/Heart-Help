package com.hearthelp.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateParserTest {
    @Test fun eightBitValue() = assertEquals(72, HeartRateParser.parse(byteArrayOf(0x00, 72)))
    @Test fun eightBitValueAbove127IsUnsigned() =
        assertEquals(180, HeartRateParser.parse(byteArrayOf(0x00, 0xB4.toByte())))
    @Test fun sixteenBitValue() =
        assertEquals(300, HeartRateParser.parse(byteArrayOf(0x01, 0x2C, 0x01)))
    @Test fun contactSupportedButNotDetectedIsNull() =
        assertNull(HeartRateParser.parse(byteArrayOf(0x04, 70)))
    @Test fun contactSupportedAndDetected() =
        assertEquals(70, HeartRateParser.parse(byteArrayOf(0x06, 70)))
    @Test fun emptyIsNull() = assertNull(HeartRateParser.parse(byteArrayOf()))
    @Test fun truncatedSixteenBitIsNull() = assertNull(HeartRateParser.parse(byteArrayOf(0x01, 0x2C)))
}
