package com.hearthelp.emergency

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.flow.MutableSharedFlow

data class SmsReply(val from: String, val body: String)

object ReplyBus {
    val replies = MutableSharedFlow<SmsReply>(extraBufferCapacity = 16)
}

class SmsReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val from = messages.firstOrNull()?.originatingAddress ?: return
        ReplyBus.replies.tryEmit(SmsReply(from, messages.joinToString("") { it.messageBody ?: "" }))
    }
}
