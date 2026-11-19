package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolRegistry

/**
 * What the owner is told when a READ-ONLY tool ended without a result after it had started (time-out, failure or
 * cancellation). Such a tool changes nothing, so there is nothing to review and nothing is blocked – unlike a
 * changing tool, whose unknown outcome still needs the owner (see [BoundedAgentOrchestrator]).
 */
internal object ReadOnlyToolFailure {
    /**
     * The file tools read the storage themselves since 19.11.2026 (Android's „Zugriff auf alle Dateien“, no Shizuku);
     * a missing grant or bridge is refused before the start with its own plain reason. What still ends here is a
     * walk over very many files that ran out of time, or a backend that broke.
     */
    private val fileTools = setOf("list_files", "find_files", "find_latest_apk", "get_large_files", "search_device",
        "list_recent_files", "get_folder_overview", "search_file_text")

    fun message(toolName: String, status: ToolExecutionStatus): String {
        val label = ToolRegistry.label(toolName)
        val what = when (status) {
            ToolExecutionStatus.TIMED_OUT -> "„$label“ hat nicht rechtzeitig geantwortet und wurde abgebrochen."
            ToolExecutionStatus.CANCELLED -> "„$label“ wurde abgebrochen."
            else -> "„$label“ hat kein Ergebnis geliefert."
        }
        val hint = if (toolName in fileTools)
            " Dateien lese ich direkt auf dem Gerät, ohne Shizuku; bei sehr vielen Dateien kann das zu lange dauern. " +
                "Nenne dann einen Ordner oder einen genaueren Namen."
        else ""
        return "$what Das Werkzeug liest nur – es wurde nichts verändert, und es ist nichts gesperrt.$hint " +
            "Du kannst es noch einmal versuchen."
    }
}
