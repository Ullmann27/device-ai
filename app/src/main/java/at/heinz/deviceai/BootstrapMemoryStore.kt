package at.heinz.deviceai

import android.content.Context
import org.json.JSONObject

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

    fun learnedCommands(): Map<String, String> {
        val raw = prefs.getString("trained_commands", "{}") ?: "{}"
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().take(32).mapNotNull { key ->
                val cleaned = BootstrapTraining.normalize(key)
                val tool = obj.optString(key)
                if (cleaned != key || BootstrapTraining.validate(key, tool) == null) null
                else cleaned to tool
            }.take(24).toMap()
        }.getOrDefault(emptyMap())
    }

    fun teach(phrase: String, tool: String): Boolean {
        val approved = BootstrapTraining.validate(phrase, tool) ?: return false
        val old = learnedCommands().toMutableMap()
        if (approved.first !in old && old.size >= 24) return false
        if (old[approved.first] == approved.second) return false
        old[approved.first] = approved.second
        return prefs.edit().putString("trained_commands", JSONObject(old).toString()).commit()
    }

    fun clearTraining(): Boolean = prefs.edit().remove("trained_commands").commit()

    fun recordAssessment(passed: Int, total: Int) {
        require(total > 0 && passed in 0..total)
        prefs.edit().putInt("curriculum_passed", passed)
            .putInt("curriculum_total", total).apply()
    }

    val curriculumPassed: Int get() = prefs.getInt("curriculum_passed", 0)
    val curriculumTotal: Int get() = prefs.getInt("curriculum_total", 0)


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
