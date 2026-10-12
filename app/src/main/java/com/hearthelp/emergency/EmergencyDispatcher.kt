package com.hearthelp.emergency

import android.content.Context
import com.hearthelp.data.AppPrefs
import com.hearthelp.data.EventLog
import com.hearthelp.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmergencyDispatcher(
    ctx: Context,
    private val prefs: AppPrefs,
    private val log: EventLog,
    private val sms: SmsSender = SmsSender(ctx),
    private val location: LocationFetcher = LocationFetcher(ctx),
    private val calls: CallPlacer = CallPlacer(ctx),
) {
    suspend fun dispatch(trigger: Trigger, bpm: Int?) = coroutineScope {
        val profile = prefs.profile.first()
        val recipients = if (trigger == Trigger.TEST) {
            listOf(EmergencyContact("You (test)", profile.ownNumber))
        } else prefs.contacts.first()
        if (recipients.isEmpty() || recipients.any { it.number.isBlank() }) {
            log.add("Emergency", "Nobody to notify — check Contacts / your own number")
            return@coroutineScope
        }

        val chain = CallChain(recipients.map { it.number })
        // Start listening before any SMS goes out so an early reply is not missed.
        val okWatcher = launch {
            val reply = ReplyBus.replies.first { r ->
                SmsReplies.isOk(r.body) && recipients.any { PhoneNumbers.same(it.number, r.from) }
            }
            chain.stop()
            log.add("Reply", "OK from ${reply.from} — no more calls")
        }

        val loc = location.current()
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val text = AlertMessage.build(profile.name, trigger, bpm, time, loc?.latitude, loc?.longitude)
        recipients.map { c ->
            async { c to sms.send(c.number, text) }
        }.awaitAll().forEach { (c, ok) ->
            log.add("SMS", "${c.name}: ${if (ok) "sent" else "FAILED after 3 tries"}")
        }

        var step = chain.start()
        while (step is CallChain.Step.Call) {
            val c = recipients[step.index]
            log.add("Call", "Calling ${c.name}")
            val secs = calls.callAndMeasure(c.number)
            log.add("Call", "${c.name}: connected ${secs}s")
            step = chain.onCallEnded(secs)
        }
        log.add("Emergency", "Call chain finished")
        okWatcher.cancel()
    }
}
