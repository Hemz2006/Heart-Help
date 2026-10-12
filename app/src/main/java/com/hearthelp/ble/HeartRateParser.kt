package com.hearthelp.ble

/** Parses the Bluetooth Heart Rate Measurement characteristic (0x2A37). */
object HeartRateParser {
    fun parse(value: ByteArray): Int? {
        if (value.isEmpty()) return null
        val flags = value[0].toInt()
        val contactSupported = (flags and 0x04) != 0
        val contactDetected = (flags and 0x02) != 0
        if (contactSupported && !contactDetected) return null
        return if ((flags and 0x01) == 0) {
            if (value.size < 2) null else value[1].toInt() and 0xFF
        } else {
            if (value.size < 3) null
            else (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        }
    }
}
