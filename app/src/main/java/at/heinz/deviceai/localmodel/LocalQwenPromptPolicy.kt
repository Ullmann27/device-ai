package at.heinz.deviceai.localmodel

/**
 * Experimental per-turn soft switch for compatible Qwen3 checkpoints.
 *
 * LiteRT-LM packages may embed their own chat templates, so this is a hint,
 * not a guarantee that thinking is disabled. No assistant role/prefix tokens
 * are injected: only the documented Qwen3 user-message soft switch is used.
 * It is never applied to isolated deeper-reasoning passes or tool results.
 */
internal object LocalQwenPromptPolicy {
    private const val SOFT_SWITCH = "/no_think"

    fun directUserPrompt(modelId: String?, prompt: String, maxCharacters: Int): String {
        require(prompt.isNotBlank() && maxCharacters > 0)
        require(prompt.length <= maxCharacters)
        val isQwen3 = modelId?.contains("qwen3", ignoreCase = true) == true
        if (!isQwen3) return prompt
        if (prompt.trimEnd().endsWith(SOFT_SWITCH)) return prompt
        // Fail closed instead of silently truncating the original user request.
        val suffix = "\n\n$SOFT_SWITCH"
        return if (prompt.length + suffix.length <= maxCharacters) prompt + suffix else prompt
    }
}
