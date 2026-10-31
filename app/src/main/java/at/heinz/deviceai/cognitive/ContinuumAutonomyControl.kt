package at.heinz.deviceai.cognitive

import android.content.Context

/**
 * Owner-owned, persistent bounds for recurring local work.
 * These values NEVER grant shell, repository, APK-signing, private-data upload,
 * agent spawning or production-branch write permission.
 */
enum class ContinuumAutonomyLevel {
    PAUSED,
    PUBLIC_RESEARCH,
    RESEARCH_AND_PROPOSALS
}

object ContinuumAutonomyControl {
    private const val PREFS = "continuum-autonomy-v32"
    private const val LEVEL = "level"

    fun level(context: Context): ContinuumAutonomyLevel {
        val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LEVEL, null)
        return runCatching { ContinuumAutonomyLevel.valueOf(saved ?: "RESEARCH_AND_PROPOSALS") }
            .getOrDefault(ContinuumAutonomyLevel.PAUSED)
    }

    fun setLevel(context: Context, value: ContinuumAutonomyLevel): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(LEVEL, value.name).commit()

    fun mayResearch(level: ContinuumAutonomyLevel): Boolean =
        level != ContinuumAutonomyLevel.PAUSED

    fun mayPropose(level: ContinuumAutonomyLevel): Boolean =
        level == ContinuumAutonomyLevel.RESEARCH_AND_PROPOSALS

    fun explain(level: ContinuumAutonomyLevel): String = when (level) {
        ContinuumAutonomyLevel.PAUSED ->
            "NOT-STOPP: Keine neuen autonomen Forschungs- oder Teamleiterzyklen. Bestehende Daten bleiben gespeichert."
        ContinuumAutonomyLevel.PUBLIC_RESEARCH ->
            "Öffentliche HTTPS-Forschung und geprüfte Lernkandidaten. Keine autonomen Coding-/Agentenaufträge."
        ContinuumAutonomyLevel.RESEARCH_AND_PROPOSALS ->
            "Öffentliche HTTPS-Forschung, geprüfte Lernkandidaten und begrenzte Entwicklungs-/Agentenvorschläge. Keine ungeprüfte Ausführung oder Selbstvermehrung."
    }
}
