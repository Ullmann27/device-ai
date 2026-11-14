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
    const val GEMMA4_MAX_OUTPUT_TOKENS = 3_072

    fun maxOutputTokens(modelId: String?): Int {
        val id = modelId?.lowercase(Locale.ROOT) ?: return DEFAULT_MAX_OUTPUT_TOKENS
        val isGemma4 = id.contains("gemma4") || id.contains("gemma-4")
        val compact = id.contains("e4b") || id.contains("e2b")
        return if (isGemma4 && compact) GEMMA4_MAX_OUTPUT_TOKENS else DEFAULT_MAX_OUTPUT_TOKENS
    }
}
