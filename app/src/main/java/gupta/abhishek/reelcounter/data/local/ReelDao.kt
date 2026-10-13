package gupta.abhishek.reelcounter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {

    // returns -1 when a reel with the same reelKey is already saved
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reel: ReelEntry): Long

    // same reel seen again: update the row, do NOT add a new one
    @Query("UPDATE reels SET lastWatchedAt = :now, timesSeen = timesSeen + 1 WHERE reelKey = :key")
    suspend fun markSeenAgain(key: String, now: Long)

    // ---------- counters ----------
    @Query("SELECT COUNT(*) FROM reels")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM reels WHERE firstWatchedAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT * FROM reels ORDER BY firstWatchedAt DESC")
    fun observeAll(): Flow<List<ReelEntry>>

    // ---------- statistics ----------
    // reels per hour of the day, e.g. 1 AM -> 10
    @Query(
        "SELECT CAST(strftime('%H', firstWatchedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, " +
                "COUNT(*) AS count FROM reels WHERE firstWatchedAt >= :from AND firstWatchedAt < :to " +
                "GROUP BY hour ORDER BY hour"
    )
    fun observeHourlyCounts(from: Long, to: Long): Flow<List<HourCount>>

    // reels per day (for the weekly chart)
    @Query(
        "SELECT strftime('%Y-%m-%d', firstWatchedAt / 1000, 'unixepoch', 'localtime') AS day, " +
                "COUNT(*) AS count FROM reels WHERE firstWatchedAt >= :from AND firstWatchedAt < :to " +
                "GROUP BY day ORDER BY day"
    )
    fun observeDailyCounts(from: Long, to: Long): Flow<List<DayCount>>

    // reels vs shorts
    @Query(
        "SELECT platform, COUNT(*) AS count FROM reels " +
                "WHERE firstWatchedAt >= :from AND firstWatchedAt < :to GROUP BY platform"
    )
    fun observePlatformCounts(from: Long, to: Long): Flow<List<PlatformCount>>

    @Query("DELETE FROM reels")
    suspend fun clear()
}