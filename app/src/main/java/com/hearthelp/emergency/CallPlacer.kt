package com.hearthelp.emergency

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager
import com.hearthelp.domain.PhoneNumbers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class CallPlacer(private val ctx: Context) {
    private val telecom = ctx.getSystemService(TelecomManager::class.java)

    /** Places a call, waits for it to end, returns seconds it was connected (0 = not answered). */
    @SuppressLint("MissingPermission")
    suspend fun callAndMeasure(number: String): Int {
        val startedAt = System.currentTimeMillis()
        runCatching { telecom.placeCall(Uri.fromParts("tel", number, null), Bundle()) }.onFailure { return 0 }
        withTimeoutOrNull(20_000) { while (!telecom.isInCall) delay(500) } ?: return 0
        withTimeoutOrNull(30 * 60_000L) { while (telecom.isInCall) delay(1_000) }
        delay(2_000) // give the system time to write the call-log entry
        return connectedSeconds(number, startedAt)
    }

    @SuppressLint("MissingPermission")
    private fun connectedSeconds(number: String, since: Long): Int {
        val cursor = runCatching {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?",
                arrayOf(CallLog.Calls.OUTGOING_TYPE.toString(), (since - 5_000).toString()),
                "${CallLog.Calls.DATE} DESC",
            )
        }.getOrNull() ?: return 0
        cursor.use {
            while (it.moveToNext()) {
                if (PhoneNumbers.same(it.getString(0) ?: "", number)) return it.getInt(1)
            }
        }
        return 0
    }
}
