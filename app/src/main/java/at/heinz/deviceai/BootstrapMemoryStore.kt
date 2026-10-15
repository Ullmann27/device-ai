package at.heinz.deviceai

import android.content.Context
import org.json.JSONObject

/** Compare-and-swap rule for neural snapshots: the stored state must still be [expected] (null = none stored). */
internal fun neuralSnapshotUnchanged(current: ByteArray?, expected: ByteArray?): Boolean =
    if (current == null) expected == null else expected != null && current.contentEquals(expected)

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

    @Synchronized fun reviseFact(previous: String, replacement: String?): Boolean {
        val revised = at.heinz.deviceai.learning.KnowledgeRevision.replace(facts(), previous, replacement)
            ?: return false
        return prefs.edit().putStringSet("facts", revised.toSet()).commit()
    }

    fun neuralSnapshot(): ByteArray? = synchronized(NEURAL_LOCK) {
        val encoded = prefs.getString("neural_dialogue_v1", null) ?: return@synchronized null
        require(encoded.length <= 90_000) { "Neuronaler Speicher ist zu groß" }
        java.util.Base64.getDecoder().decode(encoded)
    }

    fun saveNeuralSnapshot(bytes: ByteArray, report: String? = null): Boolean = synchronized(NEURAL_LOCK) {
        at.heinz.deviceai.learning.NeuralDialogue.restore(bytes) // Validate before writing.
        val edit = prefs.edit().putString("neural_dialogue_v1", java.util.Base64.getEncoder().encodeToString(bytes))
        if (report != null) edit.putString("neural_report", report.take(4000))
        edit.commit()
    }

    /**
     * Compare-and-swap: saves only while the stored snapshot is still [expected]
     * (null = nothing stored yet), so background practice and a foreground
     * experiment never overwrite each other's newer weights.
     */
    fun compareAndSaveNeuralSnapshot(expected: ByteArray?, next: ByteArray, report: String?): Boolean =
        synchronized(NEURAL_LOCK) {
            if (!neuralSnapshotUnchanged(neuralSnapshot(), expected)) return@synchronized false
            saveNeuralSnapshot(next, report)
        }

    fun neuralReport(): String = prefs.getString("neural_report", null)
        ?: "Noch kein neuronaler Messlauf."

    fun clearFacts(): Boolean = prefs.edit().remove("facts").commit()

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

    companion object {
        const val MAX_FACTS = 32
        private val NEURAL_LOCK = Any()
    }
}

