package at.heinz.deviceai.cognitive

/**
 * How an answer that stopped at the local output limit is shown and spoken.
 *
 * Before: the technical blocker was placed in front of the text („Das lokale Ausgabelimit wurde erreicht …“
 * followed by an answer ending in the middle of a sentence), so the spoken reply began with an error and
 * the visible reply gave no way forward. The text itself is never changed, shortened or completed here:
 * the context keeps the exact generated words, and the existing continuation path („weiter“) resumes them.
 */
internal object LocalPartialAnswerPresentation {
    const val CONTINUE_HINT = "— Die Antwort wurde an der lokalen Ausgabegrenze unterbrochen, nicht beendet. " +
        "Sag oder schreibe „weiter“, dann setze ich genau an dieser Stelle fort."
    private val sentenceEnd = Regex("[.!?…:;\"”“’»)\\]]$")

    /**
     * [continuationAvailable] must only be true when this partial answer is recorded in the session context,
     * because only then „weiter“ can resume it. Every other interruption keeps the previous presentation.
     */
    fun present(response: String, blocker: String?, continuationAvailable: Boolean): String {
        // BoundedAgentOrchestrator reports an accepted partial text as „Antwort unvollständig: <cause>“.
        val outputLimit = blocker != null && blocker.startsWith("Antwort unvollständig:") &&
            blocker.endsWith(LocalGenerationFailureCause.OUTPUT_LIMIT.ownerMessage)
        if (!outputLimit || !continuationAvailable || response.isBlank())
            return (blocker ?: "Antwort unvollständig.") + "\n\n" + response
        val text = response.trimEnd()
        val open = if (sentenceEnd.containsMatchIn(text)) "" else " …"
        return text + open + "\n\n" + CONTINUE_HINT
    }
}
