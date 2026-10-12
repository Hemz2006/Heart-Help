package com.hearthelp.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.hearthelp.domain.AlertSettings
import com.hearthelp.domain.AlertState
import com.hearthelp.domain.EmergencyContact
import com.hearthelp.domain.Trigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

// A corrupt file is replaced with empty preferences instead of throwing on every read.
private val Context.dataStore by preferencesDataStore(
    name = "hearthelp",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

data class Profile(val name: String = "", val ownNumber: String = "")

class AppPrefs(context: Context) {
    private val store = context.applicationContext.dataStore

    /** Reads never throw on I/O errors: they fall back to defaults so monitoring keeps running. */
    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    private object K {
        val low = intPreferencesKey("low_bpm")
        val high = intPreferencesKey("high_bpm")
        val sustain = intPreferencesKey("sustain_s")
        val countdown = intPreferencesKey("countdown_s")
        val snooze = intPreferencesKey("snooze_min")
        val signalLost = intPreferencesKey("signal_lost_min")
        val contacts = stringPreferencesKey("contacts")
        val name = stringPreferencesKey("name")
        val ownNumber = stringPreferencesKey("own_number")
        val band = stringPreferencesKey("band_address")
        val bandName = stringPreferencesKey("band_name")
        val monitoring = booleanPreferencesKey("monitoring")
        val alertKind = stringPreferencesKey("alert_kind")
        val alertTrigger = stringPreferencesKey("alert_trigger")
        val alertBpm = intPreferencesKey("alert_bpm")
        val alertDeadline = longPreferencesKey("alert_deadline")
    }

    val settings: Flow<AlertSettings> = data.map { p ->
        val d = AlertSettings()
        runCatching {
            AlertSettings(
                p[K.low] ?: d.lowBpm, p[K.high] ?: d.highBpm, p[K.sustain] ?: d.sustainSeconds,
                p[K.countdown] ?: d.countdownSeconds, p[K.snooze] ?: d.snoozeMinutes,
                p[K.signalLost] ?: d.signalLostMinutes,
            )
        }.getOrDefault(d)
    }.distinctUntilChanged()

    suspend fun saveSettings(s: AlertSettings) {
        store.edit {
            it[K.low] = s.lowBpm; it[K.high] = s.highBpm; it[K.sustain] = s.sustainSeconds
            it[K.countdown] = s.countdownSeconds; it[K.snooze] = s.snoozeMinutes
            it[K.signalLost] = s.signalLostMinutes
        }
    }

    val contacts: Flow<List<EmergencyContact>> = data.map { p ->
        p[K.contacts]?.let { runCatching { Json.decodeFromString<List<EmergencyContact>>(it) }.getOrNull() }
            ?: emptyList()
    }.distinctUntilChanged()

    suspend fun saveContacts(list: List<EmergencyContact>) {
        store.edit { it[K.contacts] = Json.encodeToString(list) }
    }

    val profile: Flow<Profile> = data.map { Profile(it[K.name] ?: "", it[K.ownNumber] ?: "") }
        .distinctUntilChanged()

    suspend fun saveProfile(p: Profile) {
        store.edit { it[K.name] = p.name.trim(); it[K.ownNumber] = p.ownNumber.trim() }
    }

    val bandAddress: Flow<String?> = data.map { it[K.band] }.distinctUntilChanged()
    val bandName: Flow<String?> = data.map { it[K.bandName] }.distinctUntilChanged()

    suspend fun saveBand(address: String, name: String) {
        store.edit { it[K.band] = address; it[K.bandName] = name }
    }

    val monitoringEnabled: Flow<Boolean> = data.map { it[K.monitoring] ?: false }.distinctUntilChanged()

    suspend fun setMonitoringEnabled(on: Boolean) {
        store.edit { it[K.monitoring] = on }
    }

    /** Saves an Alarm/Dispatched alert so it survives a process kill; any other state clears it. */
    suspend fun saveActiveAlert(s: AlertState) {
        store.edit {
            it.remove(K.alertKind); it.remove(K.alertTrigger); it.remove(K.alertBpm); it.remove(K.alertDeadline)
            val (kind, trigger, bpm) = when (s) {
                is AlertState.Alarm -> Triple("alarm", s.trigger, s.bpm)
                is AlertState.Dispatched -> Triple("dispatched", s.trigger, s.bpm)
                else -> return@edit
            }
            it[K.alertKind] = kind
            it[K.alertTrigger] = trigger.name
            bpm?.let { b -> it[K.alertBpm] = b }
            if (s is AlertState.Alarm) it[K.alertDeadline] = s.deadline
        }
    }

    /** The alert saved by [saveActiveAlert], or null if none (or it is unreadable). */
    suspend fun activeAlert(): AlertState? {
        val p = data.first()
        val trigger = p[K.alertTrigger]?.let { t -> Trigger.entries.firstOrNull { it.name == t } } ?: return null
        return when (p[K.alertKind]) {
            "alarm" -> p[K.alertDeadline]?.let { AlertState.Alarm(trigger, p[K.alertBpm], it) }
            "dispatched" -> AlertState.Dispatched(trigger, p[K.alertBpm])
            else -> null
        }
    }
}
