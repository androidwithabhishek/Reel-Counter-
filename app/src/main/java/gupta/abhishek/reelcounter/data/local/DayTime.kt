package gupta.abhishek.reelcounter.data.local

import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// The app's own clock = phone time + a test offset.
// Offset is 0 in normal use. "Start new day" adds 24h so you can test day changes.
// Every time-based rule in the app must use AppClock.now(), never System.currentTimeMillis().
object AppClock {
    const val DAY_MS = 24L * 60 * 60 * 1000

    private val _offsetMs = MutableStateFlow(0L) // in memory only: resets if the process dies
    val offsetMs: StateFlow<Long> = _offsetMs

    fun now(): Long = System.currentTimeMillis() + _offsetMs.value
    fun skipDay() { _offsetMs.value += DAY_MS }
    fun reset() { _offsetMs.value = 0L }
}

// Day boundaries in local time. A new day starts at 12:00 AM (of the app clock).
object DayTime {
    // 12:00 AM of the day [daysAgo] days ago. 0 = today, -1 = tomorrow.
    fun startOfDay(daysAgo: Int = 0): Long = startOfDayOf(AppClock.now(), daysAgo)

    // 12:00 AM of the day that contains [timeMs]. Used to compare two timestamps by calendar day.
    fun startOfDayOf(timeMs: Long, daysAgo: Int = 0): Long =
        Calendar.getInstance().apply {
            timeInMillis = timeMs
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}