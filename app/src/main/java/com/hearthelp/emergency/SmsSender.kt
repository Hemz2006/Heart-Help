package com.hearthelp.emergency

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

class SmsSender(private val ctx: Context) {
    /** Sends [text], retrying up to 3 times. Returns true once the carrier accepts every part. */
    suspend fun send(number: String, text: String): Boolean {
        repeat(3) { attempt ->
            if (sendOnce(number, text)) return true
            if (attempt < 2) delay(5_000)
        }
        return false
    }

    private suspend fun sendOnce(number: String, text: String): Boolean = withTimeoutOrNull(30_000) {
        suspendCancellableCoroutine<Boolean> { cont ->
            val action = "com.hearthelp.SMS_SENT." + UUID.randomUUID()
            val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sms.divideMessage(text)
            var remaining = parts.size
            var ok = true
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (resultCode != Activity.RESULT_OK) ok = false
                    if (--remaining == 0) {
                        runCatching { ctx.unregisterReceiver(this) }
                        if (cont.isActive) cont.resume(ok)
                    }
                }
            }
            ContextCompat.registerReceiver(ctx, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            cont.invokeOnCancellation { runCatching { ctx.unregisterReceiver(receiver) } }
            val sentIntents = ArrayList(parts.indices.map {
                PendingIntent.getBroadcast(ctx, it, Intent(action).setPackage(ctx.packageName), PendingIntent.FLAG_IMMUTABLE)
            })
            runCatching { sms.sendMultipartTextMessage(number, null, parts, sentIntents, null) }.onFailure {
                runCatching { ctx.unregisterReceiver(receiver) }
                if (cont.isActive) cont.resume(false)
            }
        }
    } ?: false
}
