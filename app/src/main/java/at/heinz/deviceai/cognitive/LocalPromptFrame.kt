package at.heinz.deviceai.cognitive

/**
 * Final bounded local prompt assembly. The required body contains the current
 * owner request and selected complete evidence; neither may be shortened to
 * make room for an advisory or old conversation history. Untrusted history is
 * optional as one indivisible block. No tool or memory authorization lives here.
 */
internal object LocalPromptFrame {
    fun historyBudget(requiredBody: String, requiredTail: String, maxCharacters: Int): Int =
        minOf(maxCharacters - requiredBody.length - requiredTail.length,
            maxCharacters - requiredBody.toByteArray(Charsets.UTF_8).size -
                requiredTail.toByteArray(Charsets.UTF_8).size).coerceAtLeast(0)

    fun assemble(
        requiredBody: String,
        optionalHistory: String,
        advisory: String?,
        requiredTail: String,
        maxCharacters: Int,
        requireHistory: Boolean = false
    ): String? {
        if (maxCharacters !in 1..LocalChatPromptBudget.FULL_CHARACTERS ||
            requiredBody.isBlank()) return null

        // Character limits and UTF-8 sizes are not interchangeable: a
        // multilingual prompt may fit the former while exceeding the latter.
        // An intentionally conservative shared limit protects both.
        fun fits(value: String): Boolean =
            value.length <= maxCharacters &&
                value.toByteArray(Charsets.UTF_8).size <= maxCharacters

        val mandatory = requiredBody + requiredTail
        if (!fits(mandatory)) return null

        // Old history is lower priority than the current request and evidence.
        // Never send an arbitrary prefix of a contradictory older conversation.
        val withHistory = requiredBody + optionalHistory
        val historyFits = optionalHistory.isNotBlank() && fits(withHistory + requiredTail)
        // Owner-dependent follow-ups must never silently lose their earlier
        // turns: native fresh-chat inference does not carry model history.
        if (requireHistory && !historyFits) return null
        val body = if (historyFits) withHistory else requiredBody
        val candidate = ChatReasoningRouter.composePrompt(body, advisory, requiredTail, maxCharacters)
        if (candidate.startsWith(body) && candidate.endsWith(requiredTail) && fits(candidate))
            return candidate

        // Optional internal advice may be cut or too large in UTF-8. Remove it
        // before refusing an otherwise complete owner/evidence request.
        val withoutAdvisory = ChatReasoningRouter.composePrompt(body, null, requiredTail, maxCharacters)
        return withoutAdvisory.takeIf { it.startsWith(body) &&
            it.endsWith(requiredTail) && fits(it) }
    }
}
