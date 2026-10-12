package com.hearthelp.emergency

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager
import com.hearthelp.domain.PhoneNumbers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

data class CallResult(val connectedSeconds: Int, val note: String? = null)

class CallPlacer(private val ctx: Context) {
    private val telecom = ctx.getSystemService(TelecomManager::class.java)

    /** Places a call, waits for it to end, reports seconds it was connected (0 = not answered). */
    @SuppressLint("MissingPermission")
    suspend fun callAndMeasure(number: String): CallResult {
        val startedAt = System.currentTimeMillis()
        try {
            telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return CallResult(0, "placeCall failed: ${e.message ?: e.javaClass.simpleName}")
        }
        try {
            withTimeoutOrNull(20_000) { while (!telecom.isInCall) delay(500) }
                ?: return CallResult(0, "call did not start within 20 s")
            withTimeoutOrNull(30 * 60_000L) { while (telecom.isInCall) delay(1_000) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            delay(60_000)
            return CallResult(0, "call state unavailable (READ_PHONE_STATE?) — waited 60 s")
        }
        delay(2_000) // give the system time to write the call-log entry
        return CallResult(connectedSeconds(number, startedAt))
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
