package com.hearthelp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "events")
data class EventEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val type: String,
    val detail: String,
)

@Dao
interface EventDao {
    @Insert suspend fun insert(e: EventEntry)
    @Query("SELECT * FROM events ORDER BY time DESC, id DESC LIMIT 300")
    fun recent(): Flow<List<EventEntry>>
}

@Database(entities = [EventEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun events(): EventDao
}

class EventLog(private val dao: EventDao) {
    val recent: Flow<List<EventEntry>> = dao.recent()
    suspend fun add(type: String, detail: String) =
        dao.insert(EventEntry(time = System.currentTimeMillis(), type = type, detail = detail))
}
