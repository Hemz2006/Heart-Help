package com.hearthelp

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.hearthelp.data.AppDatabase
import com.hearthelp.data.AppPrefs
import com.hearthelp.data.EventLog
import com.hearthelp.data.HeartRateHistory
import com.hearthelp.monitor.Notifications

class HeartHelpApp : Application() {
    lateinit var prefs: AppPrefs
        private set
    lateinit var log: EventLog
        private set
    lateinit var history: HeartRateHistory
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        prefs = AppPrefs(this)
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "hearthelp.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        log = EventLog(db.events())
        history = HeartRateHistory(db.heartRate())
    }
}

val Context.app: HeartHelpApp get() = applicationContext as HeartHelpApp
