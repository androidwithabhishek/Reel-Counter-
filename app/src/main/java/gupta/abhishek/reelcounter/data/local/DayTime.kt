package gupta.abhishek.reelcounter.data.local

import java.util.Calendar

// Day boundaries in the phone's local time. A new day starts at 12:00 AM.
object DayTime {
    // 12:00 AM of the day that was [daysAgo] days ago. daysAgo = 0 is today, -1 is tomorrow.
    fun startOfDay(daysAgo: Int = 0): Long =
        Calendar
            .getInstance()
            .apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
}
