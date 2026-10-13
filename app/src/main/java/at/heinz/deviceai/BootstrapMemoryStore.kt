package at.heinz.deviceai

import android.content.Context

/** Explicit, bounded app-private feedback and facts, never an autonomous memory promotion. */
class BootstrapMemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE)

    fun facts(): List<String> = prefs.getStringSet("facts", emptySet())
        .orEmpty().toList().sorted().take(MAX_FACTS)

    fun addFact(fact: String): Boolean {
        require(fact.length in 3..320)
        val old = facts()
        if (fact in old || old.size >= MAX_FACTS) return false
        return prefs.edit().putStringSet("facts", (old + fact).toSet()).commit()
    }

    fun clearFacts() {
        prefs.edit().remove("facts").commit()
    }

    var voiceEnabled: Boolean
        get() = prefs.getBoolean("voice_enabled", false)
        set(value) { prefs.edit().putBoolean("voice_enabled", value).apply() }

    val positiveCount: Int get() = prefs.getInt("positive", 0)
    val negativeCount: Int get() = prefs.getInt("negative", 0)

    fun recordFeedback(positive: Boolean) {
        val key = if (positive) "positive" else "negative"
        prefs.edit().putInt(key, (prefs.getInt(key, 0) + 1).coerceAtMost(100_000)).apply()
    }

    companion object { const val MAX_FACTS = 32 }
}
