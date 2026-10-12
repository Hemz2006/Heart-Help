package com.hearthelp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.hearthelp.domain.AlertSettings
import com.hearthelp.domain.EmergencyContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "hearthelp")

data class Profile(val name: String = "", val ownNumber: String = "")

class AppPrefs(context: Context) {
    private val store = context.applicationContext.dataStore

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
    }

    val settings: Flow<AlertSettings> = store.data.map { p ->
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

    val contacts: Flow<List<EmergencyContact>> = store.data.map { p ->
        p[K.contacts]?.let { runCatching { Json.decodeFromString<List<EmergencyContact>>(it) }.getOrNull() }
            ?: emptyList()
    }.distinctUntilChanged()

    suspend fun saveContacts(list: List<EmergencyContact>) {
        store.edit { it[K.contacts] = Json.encodeToString(list) }
    }

    val profile: Flow<Profile> = store.data.map { Profile(it[K.name] ?: "", it[K.ownNumber] ?: "") }
        .distinctUntilChanged()

    suspend fun saveProfile(p: Profile) {
        store.edit { it[K.name] = p.name.trim(); it[K.ownNumber] = p.ownNumber.trim() }
    }

    val bandAddress: Flow<String?> = store.data.map { it[K.band] }.distinctUntilChanged()
    val bandName: Flow<String?> = store.data.map { it[K.bandName] }.distinctUntilChanged()

    suspend fun saveBand(address: String, name: String) {
        store.edit { it[K.band] = address; it[K.bandName] = name }
    }

    val monitoringEnabled: Flow<Boolean> = store.data.map { it[K.monitoring] ?: false }.distinctUntilChanged()

    suspend fun setMonitoringEnabled(on: Boolean) {
        store.edit { it[K.monitoring] = on }
    }
}
