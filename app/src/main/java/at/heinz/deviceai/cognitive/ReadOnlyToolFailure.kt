package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolRegistry

/**
 * What the owner is told when a READ-ONLY tool ended without a result after it had started (time-out, failure or
 * cancellation). Such a tool changes nothing, so there is nothing to review and nothing is blocked – unlike a
 * changing tool, whose unknown outcome still needs the owner (see [BoundedAgentOrchestrator]).
 */
internal object ReadOnlyToolFailure {
    /** The file tools run through the device bridge; without it they fail right after their start. */
    private val bridgeFileTools = setOf("list_files", "find_files", "find_latest_apk", "get_large_files")

    fun message(toolName: String, status: ToolExecutionStatus): String {
        val label = ToolRegistry.label(toolName)
        val what = when (status) {
            ToolExecutionStatus.TIMED_OUT -> "„$label“ hat nicht rechtzeitig geantwortet und wurde abgebrochen."
            ToolExecutionStatus.CANCELLED -> "„$label“ wurde abgebrochen."
            else -> "„$label“ hat kein Ergebnis geliefert."
        }
        val hint = if (toolName in bridgeFileTools)
            " Die Dateiwerkzeuge laufen über die Shizuku-Verbindung; läuft Shizuku nicht (nach einem Neustart des " +
                "Geräts muss es neu gestartet werden), bekomme ich keine Antwort. Ein PDF finde ich auch ohne Shizuku: " +
                "Sag „Such das PDF“ und den Namen."
        else ""
        return "$what Das Werkzeug liest nur – es wurde nichts verändert, und es ist nichts gesperrt.$hint " +
            "Du kannst es noch einmal versuchen."
    }
}
