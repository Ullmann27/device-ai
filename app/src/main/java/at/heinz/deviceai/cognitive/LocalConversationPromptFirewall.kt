package at.heinz.deviceai.cognitive

/**
 * Fail-closed, tool-free protection against recycling a previously leaked
 * internal research/workflow prompt as next-turn conversation instructions.
 * Only affects ephemeral conversation history; never deletes owner memories.
 * This is a narrow lexical safety gate, not proof against all prompt injection.
 */
internal object LocalConversationPromptFirewall {
    private val leakedFrames = listOf(
        "UNTRUSTED EVIDENCE DATA",
        "REQUEST AT MOST ONE REGISTERED STRUCTURED TOOL",
        "[P6 UNVERIFIED ADVISORY",
        "SYNTHESIZE THE RESEARCH DATA BELOW"
    )

    fun safeHistory(history: String): String {
        if (history.isBlank()) return ""
        if (history.length > 16_000) return ""
        return if (leakedFrames.any { history.contains(it, ignoreCase = true) }) "" else history
    }
}
