package gupta.abhishek.reelcounter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


// Result shapes for the statistics queries
data class HourCount(val hour: Int, val count: Int)        // hour 0..23
data class DayCount(val day: String, val count: Int)        // "2026-10-13"
data class PlatformCount(val platform: String, val count: Int)

@Dao
interface ReelDao {

    @Insert
    suspend fun insert(reel: ReelEntry): Long

    // Newest row of this reel, used to decide "duplicate or count again?"
    @Query("SELECT * FROM reels WHERE reelKey = :key ORDER BY watchedAt DESC LIMIT 1")
    suspend fun findLatest(key: String): ReelEntry?

    // Duplicate inside the period: keep the row, just bump the seen counter
    @Query("UPDATE reels SET timesSeen = timesSeen + 1 WHERE id = :id")
    suspend fun addDuplicateSeen(id: Long)

    // ---------- counters ----------
    @Query("SELECT COUNT(*) FROM reels")
    fun observeTotalCount(): Flow<Int>

    // from inclusive, to exclusive. Used for "today" = [12:00 AM today, 12:00 AM tomorrow)
    @Query("SELECT COUNT(*) FROM reels WHERE watchedAt >= :from AND watchedAt < :to")
    fun observeCountBetween(from: Long, to: Long): Flow<Int>

    @Query("SELECT * FROM reels ORDER BY watchedAt DESC")
    fun observeAll(): Flow<List<ReelEntry>>

    // ---------- statistics ----------
    @Query(
        "SELECT CAST(strftime('%H', watchedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, " +
                "COUNT(*) AS count FROM reels WHERE watchedAt >= :from AND watchedAt < :to " +
                "GROUP BY hour ORDER BY hour"
    )
    fun observeHourlyCounts(from: Long, to: Long): Flow<List<HourCount>>

    @Query(
        "SELECT strftime('%Y-%m-%d', watchedAt / 1000, 'unixepoch', 'localtime') AS day, " +
                "COUNT(*) AS count FROM reels WHERE watchedAt >= :from AND watchedAt < :to " +
                "GROUP BY day ORDER BY day"
    )
    fun observeDailyCounts(from: Long, to: Long): Flow<List<DayCount>>

    @Query(
        "SELECT platform, COUNT(*) AS count FROM reels " +
                "WHERE watchedAt >= :from AND watchedAt < :to GROUP BY platform"
    )
    fun observePlatformCounts(from: Long, to: Long): Flow<List<PlatformCount>>

    @Query("DELETE FROM reels")
    suspend fun clear()
}