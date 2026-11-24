package com.burstbrainconcepts.aura

import android.content.Context
import java.util.Locale

/**
 * The names the user can call AURA by. Stored on the device and editable in Settings.
 * Matching is forgiving of small speech-recognition slips but deliberately strict about
 * position, so saying "aura" in the middle of a sentence does not wake the assistant.
 */
object WakePhrases {
    private const val PREFS = "aura_presence"
    private const val KEY = "wake_phrases"
    const val MAX_PHRASES = 6

    val DEFAULTS: List<String> = listOf("hey aura", "ok aura", "okay aura", "aura", "hey aurora")

    fun normalise(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val parsed = raw?.split(",")?.map { normalise(it) }?.filter { it.isNotEmpty() }.orEmpty()
        return if (parsed.isEmpty()) DEFAULTS else parsed
    }

    /** Text shown to the user, e.g. "hey aura, aura". */
    fun display(context: Context): String = load(context).joinToString(", ")

    /** Saves a comma-separated list. Returns a plain-language problem, or null when saved. */
    fun save(context: Context, raw: String): String? {
        val phrases = raw.split(",").map { normalise(it) }.filter { it.isNotEmpty() }.distinct()
        if (phrases.isEmpty()) return "Enter at least one name, for example: Hey Aura, Aura."
        if (phrases.size > MAX_PHRASES) return "Use at most $MAX_PHRASES names."
        phrases.forEach {
            if (it.length < 3) return "“$it” is too short. Use at least 3 letters."
            if (it.length > 30) return "“$it” is too long. Keep each name under 30 characters."
            if (it.split(" ").size > 4) return "“$it” has too many words. Use up to 4."
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, phrases.joinToString(",")).apply()
        return null
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    /**
     * Returns the words spoken after a wake phrase (possibly empty), or null if the
     * transcript does not start with one of the phrases.
     */
    fun match(transcript: String, phrases: List<String>): String? {
        val words = normalise(transcript).split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        for (phrase in phrases.sortedByDescending { it.length }) {
            val target = phrase.split(" ").filter { it.isNotEmpty() }
            if (target.isEmpty() || target.size > words.size) continue
            // A one-word name must be the first word; longer phrases may follow one filler word.
            val latestStart = if (target.size == 1) 0 else 1
            for (start in 0..minOf(latestStart, words.size - target.size)) {
                if (target.indices.all { similar(words[start + it], target[it]) }) {
                    return words.drop(start + target.size).joinToString(" ")
                }
            }
        }
        return null
    }

    private fun similar(heard: String, wanted: String): Boolean =
        heard == wanted || (heard.length >= 5 && wanted.length >= 5 && editDistance(heard, wanted) <= 1)

    private fun editDistance(a: String, b: String): Int {
        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            System.arraycopy(current, 0, previous, 0, current.size)
        }
        return previous[b.length]
    }
}
