package gupta.abhishek.reelcounter.domain.domain

import gupta.abhishek.reelcounter.core.DayTime
import gupta.abhishek.reelcounter.data.local.DayCount
import gupta.abhishek.reelcounter.data.local.HourCount
import gupta.abhishek.reelcounter.data.local.PlatformCount
import gupta.abhishek.reelcounter.data.local.ReelDao
import gupta.abhishek.reelcounter.data.local.ReelEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

@Singleton
class ReelRepository @Inject constructor(
    private val dao: ReelDao
) {
    val totalCount: Flow<Int> = dao.observeTotalCount()
    val reels: Flow<List<ReelEntry>> = dao.observeAll()

    // Reels watched since 12:00 AM today. When the day changes it starts again from 0.
    // (checks the clock once a minute, so it also works after the phone slept past midnight)
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayCount: Flow<Int> = flow {
        while (true) {
            emit(DayTime.startOfDay())
            delay(60_000)
        }
    }.distinctUntilChanged().flatMapLatest { dao.observeCountSince(it) }

    // Saves a reel. Returns true if it is NEW (counter goes up),
    // false if it was already saved (no new row, counter stays the same).
    suspend fun saveReel(
        app: String,
        platform: String,
        creator: String?,
        caption: String?,
        reelKey: String
    ): Boolean {
        val id = dao.insert(
            ReelEntry(reelKey = reelKey, app = app, platform = platform, creator = creator, caption = caption)
        )
        if (id == -1L) {
            dao.markSeenAgain(reelKey, System.currentTimeMillis())
            return false
        }
        return true
    }

    // ---------- statistics (UI comes later) ----------

    // reels per hour on one day. daysAgo = 0 -> today, 1 -> yesterday ...
    fun hourlyCounts(daysAgo: Int = 0): Flow<List<HourCount>> =
        dao.observeHourlyCounts(DayTime.startOfDay(daysAgo), DayTime.startOfDay(daysAgo - 1))

    // reels per day for the last [days] days including today (7 = weekly stats)
    fun dailyCounts(days: Int = 7): Flow<List<DayCount>> =
        dao.observeDailyCounts(DayTime.startOfDay(days - 1), DayTime.startOfDay(-1))

    // reels vs shorts for one day
    fun platformCounts(daysAgo: Int = 0): Flow<List<PlatformCount>> =
        dao.observePlatformCounts(DayTime.startOfDay(daysAgo), DayTime.startOfDay(daysAgo - 1))

    suspend fun clear() = dao.clear()
}