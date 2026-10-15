package gupta.abhishek.reelcounter.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// One row = one DIFFERENT reel/short the user really watched.
// The same reel is never saved twice (reelKey is unique).
@Entity(
    tableName = "reels",
    indices = [Index(value = ["reelKey"], unique = true)],
)
data class ReelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reelKey: String, // SHA-256 of creator + caption, never empty
    val app: String, // package name, e.g. com.instagram.android
    val platform: String, // readable: "Instagram Reels" / "YouTube Shorts"
    val creator: String? = null, // creator name as shown on screen
    val caption: String? = null, // caption as shown on screen
    val firstWatchedAt: Long = System.currentTimeMillis(), // when it was counted (used for stats)
    val lastWatchedAt: Long = System.currentTimeMillis(), // last time we saw it again
    val timesSeen: Int = 1,
    // how many times the user came across it
)

// Result shapes for the statistics queries

data class HourCount(
    val hour: Int,
    val count: Int,
) // hour 0..23 -> reels in that hour

data class DayCount(
    val day: String,
    val count: Int,
) // "2026-10-13" -> reels that day

data class PlatformCount(
    val platform: String,
    val count: Int,
) // "Instagram Reels" -> reels
