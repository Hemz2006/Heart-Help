package com.hearthelp.emergency

import android.content.Context
import android.location.Location
import com.hearthelp.data.AppPrefs
import com.hearthelp.data.EventLog
import com.hearthelp.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private suspend fun safeLog(type: String, detail: String) {
        try {
            log.add(type, detail)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun err(stage: String, e: Exception) =
        safeLog("Error", "$stage: ${e.message ?: e.javaClass.simpleName}")

    suspend fun dispatch(trigger: Trigger, bpm: Int?) = coroutineScope {
        val profile = prefs.profile.first()
        val all = if (trigger == Trigger.TEST) {
            listOf(EmergencyContact("You (test)", profile.ownNumber))
        } else prefs.contacts.first()
        val recipients = all.filter { it.number.isNotBlank() }
        all.filter { it.number.isBlank() }.forEach { safeLog("Emergency", "Skipped ${it.name}: no number") }
        safeLog("Emergency", "Dispatch started: $trigger, ${bpm ?: "-"} bpm, ${recipients.size} recipient(s)")
        if (recipients.isEmpty()) {
            safeLog("Emergency", "Nobody to notify — check Contacts / your own number")
            return@coroutineScope
        }

        val chain = CallChain(recipients.map { it.number })
        // UNDISPATCHED: subscribe before any SMS goes out so an early reply is not missed.
        val okWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
            var stopped = false
            ReplyBus.replies.collect { r ->
                val who = recipients.firstOrNull { PhoneNumbers.same(it.number, r.from) } ?: return@collect
                try {
                    safeLog("Reply", "${who.name}: ${r.body}")
                    if (!stopped && SmsReplies.isOk(r.body)) {
                        stopped = true
                        chain.stop()
                        safeLog("Reply", "OK from ${who.name} — no more calls")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    err("reply handling", e)
                }
            }
        }

        val loc: Location? = try {
            location.current()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err("location", e); null
        }
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val text = AlertMessage.build(profile.name, trigger, bpm, time, loc?.latitude, loc?.longitude)

        val smsJobs = recipients.map { c ->
            launch {
                try {
                    val ok = sms.send(c.number, text)
                    safeLog("SMS", "${c.name}: ${if (ok) "sent" else "FAILED after 3 tries"}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    err("SMS to ${c.name}", e)
                }
            }
        }
        withTimeoutOrNull(10_000) { smsJobs.joinAll() }

        try {
            var step = chain.start()
            while (step is CallChain.Step.Call) {
                val c = recipients[step.index]
                var secs = 0
                try {
                    safeLog("Call", "Calling ${c.name}")
                    val result = calls.callAndMeasure(c.number)
                    secs = result.connectedSeconds
                    result.note?.let { safeLog("Call", "${c.name}: $it") }
                    safeLog("Call", "${c.name}: connected ${secs}s")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    err("call to ${c.name}", e)
                }
                step = chain.onCallEnded(secs)
            }
            safeLog("Emergency", "Call chain finished")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err("call chain", e)
        } finally {
            okWatcher.cancel()
        }
    }
}
