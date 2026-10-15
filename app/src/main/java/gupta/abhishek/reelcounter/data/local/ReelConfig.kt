package gupta.abhishek.reelcounter.data.local

// Settings you can tweak without touching any logic.
// A reel that was already counted is counted AGAIN if either rule below is satisfied.
// The timer starts at the last COUNTED watch; duplicates do not restart it.
object ReelConfig {
    // Rule 1: across days (calendar days, new day starts at 12:00 AM).
    // 1 = next day counts again, 2 = every second day, 0 = this rule is off.
    const val REPEAT_AFTER_DAYS = 1

    // Rule 2: within a day, in hours (decimals allowed, 0.5 = 30 min).
    // 3.0 = same reel may count again after 3 hours, 0.0 = this rule is off.
    const val REPEAT_AFTER_HOURS = 2.0 / 60

    val repeatAfterHoursMs: Long get() = (REPEAT_AFTER_HOURS * 60 * 60 * 1000).toLong()


    // Rule 3: leaving Instagram/YouTube and coming back starts a new session.
// A reel counted in an earlier session may be counted again.
    const val COUNT_AGAIN_AFTER_REOPEN = true

    // How long the user must be away from the app to count as "quit and reopen".
// Short on purpose, so the keyboard or notification shade doesn't trigger it.
    const val REOPEN_AFTER_SECONDS = 20L
}