package com.hearthelp.data

import androidx.room.*
import com.hearthelp.domain.HistorySeries
import com.hearthelp.domain.MinuteSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "hr_minutes")
data class HrMinute(
    @PrimaryKey val minuteStart: Long,
    val minBpm: Int,
    val avgBpm: Int,
    val maxBpm: Int,
    val count: Int,
)

@Dao
interface HrMinuteDao {
    @Query("SELECT * FROM hr_minutes WHERE minuteStart = :minute")
    suspend fun get(minute: Long): HrMinute?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(m: HrMinute)

    @Query("SELECT * FROM hr_minutes WHERE minuteStart >= :from AND minuteStart < :to ORDER BY minuteStart")
    fun range(from: Long, to: Long): Flow<List<HrMinute>>

    @Query("DELETE FROM hr_minutes WHERE minuteStart < :before")
    suspend fun deleteBefore(before: Long)
}

/** Stores one row per minute of heart rate and keeps [KEEP_DAYS] days. */
class HeartRateHistory(private val dao: HrMinuteDao) {
    companion object { const val KEEP_DAYS = 90 }

    /** Saves a minute, merging with a row already stored for it (e.g. after a service restart). */
    suspend fun save(s: MinuteSample) {
        val merged = dao.get(s.minuteStart)?.let { HistorySeries.merge(it.toSample(), s) } ?: s
        dao.put(HrMinute(merged.minuteStart, merged.min, merged.avg, merged.max, merged.count))
    }

    fun range(from: Long, to: Long): Flow<List<MinuteSample>> =
        dao.range(from, to).map { rows -> rows.map { it.toSample() } }

    suspend fun prune(now: Long) = dao.deleteBefore(now - KEEP_DAYS * 86_400_000L)

    private fun HrMinute.toSample() = MinuteSample(minuteStart, minBpm, avgBpm, maxBpm, count)
}
