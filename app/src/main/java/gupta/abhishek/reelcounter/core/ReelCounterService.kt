package gupta.abhishek.reelcounter.core

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import gupta.abhishek.reelcounter.domain.domain.ReelRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint   // lets Hilt inject things into this service
class ReelCounterService : AccessibilityService() {

    @Inject lateinit var repository: ReelRepository               // given by Hilt
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)


    private var onReelScreen = false      // true only while a Reels/Shorts screen is open
    private var onCountingScreen = false  // true while the counter is visible and counting
    private var inSupportedApp = false    // true while Instagram/YouTube is on screen
    private var lastCheckTime = 0L
    private var lastExitTime = 0L          // when we last left a Reels/Shorts screen
    private var currentPkg = ""           // app we are in right now
    private lateinit var counterText: TextView
    private lateinit var windowManager: WindowManager

    private val lastIndex = HashMap<String, Int>()   // last item number, per app + view
    private var gesturePixels = 0                    // distance scrolled in the current swipe
    private var gesturePkg = ""                      // app of the current swipe
    private val handler = Handler(Looper.getMainLooper())

    // Delay (ms) after a count during which the SAME app can't count again.
    // Instagram needs it because it sometimes sends extra events for one reel.
    // YouTube works fine, so 0 = no delay. Change 600 if it's too short/long.
    private val cooldownMs = mapOf(
        "com.instagram.android" to 600L,
        "com.google.android.youtube" to 0L
    )
    private val lastCountTime = HashMap<String, Long>()
    private val finishSwipe = Runnable { finishSwipeAndCount() }

    // Only these two apps for now
    private val shortVideoApps = setOf(
        "com.instagram.android",
        "com.google.android.youtube"
    )

    // If ANY of these views exist on screen, we are on a Reels/Shorts screen.
    // These IDs are best guesses and can change when Instagram/YouTube update.
    private val reelScreenIds = listOf(
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/clips_swipe_refresh_container",
        "com.google.android.youtube:id/reel_recycler",
        "com.google.android.youtube:id/reel_player_page_container"
    )

    // true = after every counted reel, print everything visible on screen to Logcat (tag: ReelDump).
    // Used once to find where the creator name and caption live. Turn off afterwards.
    private val debugDump = true

    // A reel counts as WATCHED only if the user stays on it this long (in ms).
    // Swiping away sooner = not counted. Change 2000 to make it stricter or looser.
    private val minWatchMs = 800L
    private var countSeq = 0   // goes up with every count; tells us if the user already swiped again

    // true  = count ONLY on the Reels/Shorts screen. Feed scrolling (home feed, search,
    //         profile, etc.) is NOT counted and the counter is hidden there.
    // false = also count scrolling in the rest of Instagram/YouTube (feed too),
    //         and show the counter in the whole app.
    private val onlyCountReelsAndShorts = true

    // true  = count only swipes of the reels/shorts pager (comments, captions, etc. ignored)
    // false = count every scroll on the Reels/Shorts screen (old behavior)
    private val onlyCountReelSwipes = true

    // The scrolling view that moves from one reel to the next (best guesses, can change with updates)
    private val reelPagerIds = listOf(
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.google.android.youtube:id/reel_recycler"
    )

    override fun onServiceConnected() {
        showCounter()

        // the overlay shows today's count (starts again from 0 at 12:00 AM)
        scope.launch {
            repository.todayCount.collect { today ->
                counterText.text = "Today: $today"
            }
        }
    }

    private fun showCounter() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        counterText = TextView(this).apply {
            text = "Reels: 0"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(32, 16, 32, 16)
            visibility = View.GONE            // hidden until we reach Reels/Shorts
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.y = 200
        windowManager.addView(counterText, params)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> updateOverlay()

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> countScroll(event)
        }
    }

    // Shows/hides the counter depending on the screen and the variable above
    private fun updateOverlay() {
        val now = SystemClock.uptimeMillis()
        if (now - lastCheckTime < 200) return   // don't check too often
        lastCheckTime = now

        val nowOnReels = isOnReelScreen()       // also updates inSupportedApp
        val nowCounting = if (onlyCountReelsAndShorts) nowOnReels else inSupportedApp
        if (nowOnReels == onReelScreen && nowCounting == onCountingScreen) return

        val enteredReels = nowOnReels && !onReelScreen
        val leftReels = !nowOnReels && onReelScreen
        val enteredCounting = nowCounting && !onCountingScreen

        onReelScreen = nowOnReels
        onCountingScreen = nowCounting
        counterText.visibility = if (nowCounting) View.VISIBLE else View.GONE

        if (enteredCounting) {                   // fresh start each time we enter
            lastIndex.clear()
            gesturePixels = 0
        }
        if (enteredReels) {
            // the reel we land on counts too (even if the user never scrolls).
            // Skip if we only left for under 2 seconds (e.g. comments panel opening)
            if (now - lastExitTime > 2000) addReels(currentPkg, 1)
        }
        if (leftReels) lastExitTime = now

        Log.d("ReelCounter", "onReelScreen=$nowOnReels counting=$nowCounting")
    }

    private fun isOnReelScreen(): Boolean {
        inSupportedApp = false
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: return false
        if (pkg !in shortVideoApps) return false
        inSupportedApp = true
        currentPkg = pkg

        for (id in reelScreenIds) {
            if (root.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) return true
        }
        return false
    }

    private fun countScroll(event: AccessibilityEvent) {
        if (!onCountingScreen) return            // counter is hidden = don't count

        val pkg = event.packageName?.toString() ?: return
        if (pkg !in shortVideoApps) return

        // Which view scrolled? Comments list, caption, or the reel pager?
        val sourceId = event.source?.viewIdResourceName
        Log.d("ReelCounter", "scroll source = $sourceId")
        // pager filter only applies on the Reels/Shorts screen (feed scrolls pass through)
        if (onReelScreen && onlyCountReelSwipes && sourceId !in reelPagerIds) return

        val deltaY = if (Build.VERSION.SDK_INT >= 28) abs(event.scrollDeltaY) else 0
        Log.d("ReelCounter", "$pkg ${event.className} index=${event.fromIndex} deltaY=$deltaY")

        val index = event.fromIndex

        // WAY 1: the app tells us the item number -> use ONLY this (5 -> 15 = 10 reels)
        if (index >= 0) {
            val key = "$pkg|${event.className}"   // separate memory for each scrolling view
            val last = lastIndex[key]
            lastIndex[key] = index
            if (last != null && index != last) addReels(pkg, abs(index - last))
            return
        }

        // WAY 2: no item number -> use distance, but count ONCE when the swipe ends
        if (deltaY > 0) {
            gesturePkg = pkg
            gesturePixels += deltaY
            handler.removeCallbacks(finishSwipe)
            handler.postDelayed(finishSwipe, 250)   // 250 ms with no scroll = swipe finished
        }
    }

    // Called 250 ms after the last scroll event of a swipe
    private fun finishSwipeAndCount() {
        val screenHeight = resources.displayMetrics.heightPixels
        // round to nearest whole screen: 0.8 screen -> 1 reel, 9.7 screens -> 10 reels
        val reels = Math.round(gesturePixels.toFloat() / screenHeight)
        gesturePixels = 0
        addReels(gesturePkg, reels)
    }

    private fun addReels(pkg: String, n: Int) {
        if (n <= 0) return

        // delay check: ignore if this app counted too recently
        val now = SystemClock.uptimeMillis()
        val wait = cooldownMs[pkg] ?: 0L
        val last = lastCountTime[pkg] ?: 0L
        if (now - last < wait) {
            Log.d("ReelCounter", "skipped (cooldown) $pkg")
            return
        }
        lastCountTime[pkg] = now

        // read the screen first, save only if it is a real (non-ad) reel
        val seq = ++countSeq
        scope.launch {
            // wait for the new reel to load, then read what is on screen
            delay(minWatchMs)
            if (seq != countSeq) {
                Log.d("ReelInfo", "app=$pkg | NOT counted (swiped again before reading)")
                return@launch
            }
            val root = rootInActiveWindow ?: return@launch
            if (root.packageName?.toString() != pkg) return@launch

            val info = ReelKeyReader.read(root)

            // 1) ads and unidentified reels: nothing is saved
            if (info.isAd) {
                logReel(pkg, info, "NOT counted (ad)")
                return@launch
            }
            val key = info.key
            if (key == null) {
                logReel(pkg, info, "NOT counted (reel not identified)")
                return@launch
            }

            // 2) save it; a reel already in the database is not added again, counter stays the same
            val isNew = repository.saveReel(pkg, platformName(pkg), info.creator, info.caption, key)
            logReel(pkg, info, if (isNew) "COUNTED (new reel)" else "NOT counted (already saved)")
        }

        if (debugDump) handler.postDelayed({ dumpScreen() }, 800)  // wait for the new reel to load
    }

    // one line with everything, filter Logcat by: ReelInfo
    private fun logReel(pkg: String, info: ReelInfo, verdict: String) {
        Log.d(
            "ReelInfo",
            "app=$pkg | creator=${info.creator} | caption=${info.caption?.take(80)} " +
                    "| ad=${info.isAd} (${info.adLabel}) | key=${info.key?.take(12)} | $verdict"
        )
    }

    // ---------- debug: print the screen's views ----------
    private fun dumpScreen() {
        val root = rootInActiveWindow ?: return
        Log.d("ReelDump", "========== screen dump (${root.packageName}) ==========")
        dumpNode(root, 0)
    }

    private fun dumpNode(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null) return
        val id = node.viewIdResourceName
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (id != null || !text.isNullOrBlank() || !desc.isNullOrBlank()) {
            Log.d("ReelDump", "${" ".repeat(depth)}id=$id | text=${text?.take(60)} | desc=${desc?.take(60)}")
        }
        for (i in 0 until node.childCount) dumpNode(node.getChild(i), depth + 1)
    }

    private fun platformName(pkg: String): String =
        if (pkg == "com.instagram.android") "Instagram Reels" else "YouTube Shorts"

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(finishSwipe)
        scope.cancel()
        if (::counterText.isInitialized) windowManager.removeView(counterText)
    }
}