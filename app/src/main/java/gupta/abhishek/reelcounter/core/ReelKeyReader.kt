package gupta.abhishek.reelcounter.core

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

// Everything we learned about the reel that is on screen right now
data class ReelInfo(
    val creator: String?,    // creator name, null if not found
    val caption: String?,    // caption text, null if not found
    val isAd: Boolean,       // true if a "Sponsored"/"Ad" label is visible
    val adLabel: String?,    // the label text that made us think it's an ad
    val key: String?         // SHA-256 of creator + caption, null if both missing
)

// Reads the Reels/Shorts screen and returns a ReelInfo.
object ReelKeyReader {

    // A view whose ID contains one of these words is treated as creator / caption.
    // Guesses - after looking at the ReelDump log we can replace them with exact IDs.
    private val creatorWords = listOf("username", "author", "channel_name", "creator")
    private val captionWords = listOf("caption", "description", "reel_title", "title")

    // Visible text that marks an ad (English only).
    private val adTexts = setOf("sponsored", "ad", "advertisement", "promoted", "visit advertiser")
    private val adIdWords = listOf("sponsored", "ad_badge")

    fun read(root: AccessibilityNodeInfo): ReelInfo {
        var creator: String? = null
        var caption: String? = null
        var adLabel: String? = null

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            // isVisibleToUser: the pager also keeps the NEXT reel in memory, skip that one
            if (node.isVisibleToUser) {
                val id = node.viewIdResourceName?.lowercase()
                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()

                // --- creator + caption (by view id) ---
                if (id != null && !text.isNullOrEmpty()) {
                    if (creator == null && creatorWords.any { id.contains(it) }) creator = text
                    if (caption == null && captionWords.any { id.contains(it) }) caption = text
                }

                // --- ad detection (by visible label or view id) ---
                if (adLabel == null) {
                    adLabel = listOf(text, desc).firstOrNull { it != null && isAdText(it) }
                        ?: id?.takeIf { v -> adIdWords.any { v.contains(it) } }
                }
            }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)

        val key = if (creator == null && caption == null) null
        else hash("${creator.orEmpty()}|${caption.orEmpty()}")

        return ReelInfo(creator, caption, adLabel != null, adLabel, key)
    }

    // exact word like "Ad", or text that starts with "Sponsored" (e.g. "Sponsored · Brand")
    private fun isAdText(t: String): Boolean {
        val s = t.lowercase().trim()
        return s in adTexts || s.startsWith("sponsored")
    }

    // same text in -> same hash out
    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
}