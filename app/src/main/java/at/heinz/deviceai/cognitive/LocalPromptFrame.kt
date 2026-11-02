package at.heinz.deviceai.cognitive

/**
 * Final bounded local prompt assembly. The required body contains the current
 * owner request and selected complete evidence; neither may be shortened to
 * make room for an advisory or old conversation history. Untrusted history is
 * optional as one indivisible block. No tool or memory authorization lives here.
 */
internal object LocalPromptFrame {
    fun assemble(
        requiredBody: String,
        optionalHistory: String,
        advisory: String?,
        requiredTail: String,
        maxCharacters: Int
    ): String? {
        if (maxCharacters !in 1..LocalChatPromptBudget.FULL_CHARACTERS ||
            requiredBody.isBlank() || requiredBody.length.toLong() +
            requiredTail.length > maxCharacters.toLong()) return null
        val canIncludeHistory = requiredBody.length.toLong() + optionalHistory.length +
            requiredTail.length <= maxCharacters.toLong()
        val body = if (canIncludeHistory) requiredBody + optionalHistory else requiredBody
        val prompt = ChatReasoningRouter.composePrompt(body, advisory, requiredTail, maxCharacters)
        // An internal reasoning advisory is never allowed to steal owner/evidence space.
        if (!prompt.startsWith(body) || !prompt.endsWith(requiredTail) ||
            prompt.length > maxCharacters) return null
        return prompt
    }
}
