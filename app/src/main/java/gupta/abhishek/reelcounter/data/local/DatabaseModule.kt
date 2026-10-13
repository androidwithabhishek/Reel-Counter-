package gupta.abhishek.reelcounter.di

import android.content.Context
import androidx.room.Room
import gupta.abhishek.reelcounter.data.local.ReelDao
import gupta.abhishek.reelcounter.data.local.ReelDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Tells Hilt how to build the database and the DAO
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReelDatabase =
        Room.databaseBuilder(context, ReelDatabase::class.java, "reel_db")
            .fallbackToDestructiveMigration()   // testing only: wipes data when version number goes up
            .build()

    @Provides
    fun provideReelDao(db: ReelDatabase): ReelDao = db.reelDao()
}