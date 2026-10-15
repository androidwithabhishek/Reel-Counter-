package gupta.abhishek.reelcounter.core

import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

// Everything we learned about the reel that is on screen right now
data class ReelInfo(
    val creator: String?, // creator name, null if not found
    val caption: String?, // caption text, null if not found
    val audio: String?, // true if a "Sponsored"/"Ad" label is visible
    val isAd: Boolean, // the label text that made us think it's an ad
    val adLabel: String?, // SHA-256 of creator + caption, null if both missing
    val key: String?,
    val weak: Boolean,    // true = key built from creator only
)

object ReelKeyReader {
    private val creatorWords = listOf("username", "author", "channel_name", "creator", "channel_bar")
    private val captionWords = listOf("caption", "description", "reel_title", "title")
    private val audioWords = listOf("audio", "music", "sound")

    private val adTexts = setOf("sponsored", "ad", "advertisement", "promoted", "visit advertiser")
    private val adIdWords = listOf("sponsored", "ad_badge")

    fun read(root: AccessibilityNodeInfo): ReelInfo {
        var creator: String? = null
        var caption: String? = null
        var audio: String? = null
        var adLabel: String? = null
        val allTexts = mutableListOf<String>() // every visible text, used for fallbacks

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isVisibleToUser) {
                val id = node.viewIdResourceName?.lowercase()
                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()

                if (!text.isNullOrEmpty()) allTexts.add(text)

                if (id != null && !text.isNullOrEmpty()) {
                    if (creator == null && creatorWords.any { id.contains(it) }) creator = text
                    if (caption == null && captionWords.any { id.contains(it) }) caption = text
                    if (audio == null && audioWords.any { id.contains(it) }) audio = text
                }

                if (adLabel == null) {
                    adLabel = listOf(text, desc).firstOrNull { it != null && isAdText(it) }
                        ?: id?.takeIf { v -> adIdWords.any { v.contains(it) } }
                }
            }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)

        // ---- fallbacks that don't need view IDs ----
        // YouTube shows the channel as a handle: "@name"
        if (creator == null) creator = allTexts.firstOrNull { it.startsWith("@") && it.length > 2 }

        // Audio line, e.g. "Original audio · name"
        if (audio == null) audio = allTexts.firstOrNull { it.contains("audio", ignoreCase = true) && it.length > 8 }

        // Caption: longest visible text that is not the creator, audio, or an ad label
        if (caption == null) {
            caption = allTexts
                .filter { it != creator && it != audio && it.length >= 15 && !isAdText(it) }
                .maxByOrNull { it.length }
        }

        val strongParts = listOfNotNull(caption, audio)
        val key: String?
        val weak: Boolean
        when {
            creator != null && strongParts.isNotEmpty() -> {
                key = hash("${creator}|${caption.orEmpty()}|${audio.orEmpty()}"); weak = false
            }
            strongParts.isNotEmpty() -> { // no creator, but caption/audio exists
                key = hash("|${caption.orEmpty()}|${audio.orEmpty()}"); weak = false
            }
            creator != null -> { key = hash("$creator||"); weak = true } // creator only
            else -> { key = null; weak = false }
        }

        return ReelInfo(creator, caption, audio, adLabel != null, adLabel, key, weak)
    }

    private fun isAdText(t: String): Boolean {
        val s = t.lowercase().trim()
        return s in adTexts || s.startsWith("sponsored")
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
}