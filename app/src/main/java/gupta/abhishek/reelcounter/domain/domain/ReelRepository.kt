package gupta.abhishek.reelcounter.domain.domain


import gupta.abhishek.reelcounter.data.local.AppClock
import gupta.abhishek.reelcounter.data.local.DayCount
import gupta.abhishek.reelcounter.data.local.DayTime
import gupta.abhishek.reelcounter.data.local.HourCount
import gupta.abhishek.reelcounter.data.local.PlatformCount
import gupta.abhishek.reelcounter.data.local.ReelConfig
import gupta.abhishek.reelcounter.data.local.ReelDao

import gupta.abhishek.reelcounter.data.local.ReelDatabase
import gupta.abhishek.reelcounter.data.local.ReelEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ReelRepository @Inject constructor(
    private val dao: ReelDao,
    private val db: ReelDatabase, // only needed to fully wipe the database in clear()
) {
    // Makes "check duplicate, then insert" one step, so two fast events can't both insert.
    private val saveLock = Mutex()

    val totalCount: Flow<Int> = dao.observeTotalCount()
    val reels: Flow<List<ReelEntry>> = dao.observeAll()

    // Re-checks the clock every minute (phone may sleep past midnight) and immediately
    // when the test offset changes ("Start new day").



    // When the current session started (app clock). A reel counted before this
// belongs to an older session. Memory only: 0 until the first reopen.
    @Volatile private var sessionStartedAt = 0L

    // Called by the service when the user comes back to Instagram/YouTube after being away.
    fun startNewSession() { sessionStartedAt = AppClock.now() }
    private val clockTick: Flow<Unit> = flow {
        while (true) { emit(Unit); delay(60_000) }
    }

    // Reels counted between 12:00 AM today and 12:00 AM tomorrow (app clock).
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayCount: Flow<Int> =
        combine(AppClock.offsetMs, clockTick) { _, _ -> DayTime.startOfDay() }
            .distinctUntilChanged()
            .flatMapLatest { start -> dao.observeCountBetween(start, DayTime.startOfDay(-1)) }






    // Returns true if the reel was COUNTED (counter goes up), false if it was a duplicate.
    suspend fun saveReel(
        app: String,
        platform: String,
        creator: String?,
        caption: String?,
        reelKey: String,
    ): Boolean = saveLock.withLock {
        val now = AppClock.now()
        val latest = dao.findLatest(reelKey)

        // Seen before and neither repeat rule allows counting it again -> duplicate
        if (latest != null && !canCountAgain(latest.watchedAt, now)) {
            dao.addDuplicateSeen(latest.id)
            return@withLock false
        }

        dao.insert(
            ReelEntry(
                reelKey = reelKey, app = app, platform = platform,
                creator = creator, caption = caption, watchedAt = now,
            )
        )
        true
    }

    // True if the reel counted at [lastCountedAt] may be counted again at [now].
    private fun canCountAgain(lastCountedAt: Long, now: Long): Boolean {
        // Rule 1: enough calendar days between the two watches
        if (ReelConfig.REPEAT_AFTER_DAYS > 0) {
            val daysApart = Math.round(
                (DayTime.startOfDayOf(now) - DayTime.startOfDayOf(lastCountedAt)) / AppClock.DAY_MS.toDouble()
            )
            if (daysApart >= ReelConfig.REPEAT_AFTER_DAYS) return true
        }
        // Rule 2: enough hours passed, even inside the same day
        if (ReelConfig.REPEAT_AFTER_HOURS > 0 && now - lastCountedAt >= ReelConfig.repeatAfterHoursMs) return true

        // Rule 3: the reel was counted in an earlier session (user left the app and reopened it)
        if (ReelConfig.COUNT_AGAIN_AFTER_REOPEN && lastCountedAt < sessionStartedAt) return true

        return false
    }







    // ---------- statistics (UI comes later) ----------
    fun hourlyCounts(daysAgo: Int = 0): Flow<List<HourCount>> =
        dao.observeHourlyCounts(DayTime.startOfDay(daysAgo), DayTime.startOfDay(daysAgo - 1))

    fun dailyCounts(days: Int = 7): Flow<List<DayCount>> =
        dao.observeDailyCounts(DayTime.startOfDay(days - 1), DayTime.startOfDay(-1))

    fun platformCounts(daysAgo: Int = 0): Flow<List<PlatformCount>> =
        dao.observePlatformCounts(DayTime.startOfDay(daysAgo), DayTime.startOfDay(daysAgo - 1))

    // ---------- testing tools ----------

    // Moves the app clock forward 24h. Today's counter restarts from 0 and then
    // grows normally as new reels are counted.
    fun startNewDay() = AppClock.skipDay()

    // Deletes everything: all rows, the id counter, and the test clock offset.
    // Deletes everything: all rows, the id counter, the test clock offset, and the session marker.
    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clear()
        db.openHelper.writableDatabase.execSQL("DELETE FROM sqlite_sequence WHERE name = 'reels'")
        AppClock.reset()
        sessionStartedAt = 0L
    }







}