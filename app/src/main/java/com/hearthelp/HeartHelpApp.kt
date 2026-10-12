package com.hearthelp

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.hearthelp.data.AppDatabase
import com.hearthelp.data.AppPrefs
import com.hearthelp.data.EventLog
import com.hearthelp.monitor.Notifications

class HeartHelpApp : Application() {
    lateinit var prefs: AppPrefs
        private set
    lateinit var log: EventLog
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        prefs = AppPrefs(this)
        log = EventLog(Room.databaseBuilder(this, AppDatabase::class.java, "hearthelp.db").build().events())
    }
}

val Context.app: HeartHelpApp get() = applicationContext as HeartHelpApp
