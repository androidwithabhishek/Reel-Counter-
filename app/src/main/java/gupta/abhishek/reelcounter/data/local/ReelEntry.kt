package gupta.abhishek.reelcounter.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


// One row = one COUNTED watch of a reel/short.
// The same reel can have several rows, one per repeat period (see ReelConfig).
// Indexes: reelKey makes the duplicate check fast, watchedAt makes the day counters fast.
@Entity(
    tableName = "reels",
    indices = [Index(value = ["reelKey"]), Index(value = ["watchedAt"])],
)
data class ReelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reelKey: String,   // SHA-256 identity of the reel (creator + caption + audio)
    val app: String,       // package name, e.g. com.instagram.android
    val platform: String,  // readable: "Instagram Reels" / "YouTube Shorts"
    val creator: String? = null,
    val caption: String? = null,
    val watchedAt: Long,   // when it was counted (AppClock time, used for all stats)
    val timesSeen: Int = 1, // 1 = counted once; +1 for every duplicate seen inside the period
)


