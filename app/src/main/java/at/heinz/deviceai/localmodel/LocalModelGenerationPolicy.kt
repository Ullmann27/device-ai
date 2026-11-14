package at.heinz.deviceai.localmodel

import java.util.Locale

/**
 * Bounded generation limits selected by owner-supplied catalog model labels.
 * Family matching chooses a budget; it does not verify the model's identity.
 *
 * Tokens are a maximum, not a request to generate that many tokens. A different
 * native model pack may reject the requested budget; actual LiteRT-LM model
 * load and inference are still verified separately on the owner's device.
 */
internal object LocalModelGenerationPolicy {
    const val DEFAULT_MAX_OUTPUT_TOKENS = 1_024
    const val GEMMA4_MAX_OUTPUT_TOKENS = 2_048
    const val GEMMA4_TOOL_FREE_OUTPUT_TOKENS = 3_072
    const val MAX_RESPONSE_CHARACTERS = 64_000
    const val OUTPUT_TOKEN_LIMIT = "LOCAL_MODEL_OUTPUT_TOKEN_LIMIT"
    const val OUTPUT_CHARACTER_LIMIT = "Local model response exceeded the output limit."

    fun maxTextOnlyOutputTokens(modelId: String?, contextTokens: Int = 4_096): Int {
        val ordinaryLimit = maxOutputTokens(modelId)
        if (ordinaryLimit != GEMMA4_MAX_OUTPUT_TOKENS) return ordinaryLimit
        // Scale only an already-loaded window.
        val capacity = contextTokens.takeIf { it in 1..8_192 } ?: 4_096
        return (capacity * GEMMA4_TOOL_FREE_OUTPUT_TOKENS / 4_096).coerceAtLeast(1)
    }

    fun outputLimitReached(nativeOutputTokens: Int?, requestedOutput: Int, contextTokens: Int): Boolean =
        requestedOutput > 0 && contextTokens > 0 && nativeOutputTokens != null &&
            nativeOutputTokens in requestedOutput..contextTokens

    fun isOutputLimit(message: String?): Boolean =
        message == OUTPUT_TOKEN_LIMIT || message == OUTPUT_CHARACTER_LIMIT

    fun maxOutputTokens(modelId: String?): Int {
        val id = modelId?.lowercase(Locale.ROOT) ?: return DEFAULT_MAX_OUTPUT_TOKENS
        val isGemma4 = id.contains("gemma4") || id.contains("gemma-4")
        val compact = id.contains("e4b") || id.contains("e2b")
        return if (isGemma4 && compact) GEMMA4_MAX_OUTPUT_TOKENS else DEFAULT_MAX_OUTPUT_TOKENS
    }
}
