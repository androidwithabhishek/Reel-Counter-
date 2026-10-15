package gupta.abhishek.reelcounter.data.local



import androidx.room.Database
import androidx.room.RoomDatabase

// version 2: new columns + unique reelKey (old data is wiped by fallbackToDestructiveMigration)
@Database(entities = [ReelEntry::class], version = 3, exportSchema = false)
abstract class ReelDatabase : RoomDatabase() {
    abstract fun reelDao(): ReelDao
}