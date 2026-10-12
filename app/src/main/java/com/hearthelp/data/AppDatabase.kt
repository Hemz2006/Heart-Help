package com.hearthelp.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [EventEntry::class, HrMinute::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun heartRate(): HrMinuteDao

    companion object {
        /** v1 → v2 adds the per-minute heart-rate table; the event log is kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hr_minutes` (`minuteStart` INTEGER NOT NULL, " +
                        "`minBpm` INTEGER NOT NULL, `avgBpm` INTEGER NOT NULL, `maxBpm` INTEGER NOT NULL, " +
                        "`count` INTEGER NOT NULL, PRIMARY KEY(`minuteStart`))"
                )
            }
        }
    }
}
