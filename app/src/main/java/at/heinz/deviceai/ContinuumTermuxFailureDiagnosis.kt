package at.heinz.deviceai

/**
 * Fixed, non-sensitive diagnostics for Termux failures.
 * stderr is untrusted: do not display or persist its raw text, URLs or paths.
 * A recognized signature is a hint to repair the environment, never proof of success.
 */
internal object ContinuumTermuxFailureDiagnosis {
    fun explanation(operation: ContinuumMobileBuildPlan.Operation, stderr: String?): String? {
        if (operation !in setOf(
                ContinuumMobileBuildPlan.Operation.PROJECT_CREATE,
                ContinuumMobileBuildPlan.Operation.PROJECT_VERIFY,
                ContinuumMobileBuildPlan.Operation.PROJECT_BUILD
            )) return null
        val diagnostic = stderr?.take(8_192) ?: return null
        return when {
            diagnostic.contains("MODULE_NOT_FOUND") &&
                diagnostic.contains("device-ai/scripts/continuum-owned-app.mjs") ->
                "M4 blockiert: Das erforderliche Skript scripts/continuum-owned-app.mjs fehlt " +
                    "im lokalen Termux-Checkout ~/device-ai. Der GitHub-Quellstand und der lokale " +
                    "Checkout müssen zunächst abgeglichen werden. Kein Projekt- oder APK-Erfolg bestätigt."
            diagnostic.contains("not a git repository", ignoreCase = true) ->
                "M4 blockiert: Unter ~/device-ai wurde kein gültiger Git-Checkout gefunden. " +
                    "Bitte den lokalen Repository-Stand prüfen. Keine Dateien freigegeben."
            else -> null
        }
    }
}
