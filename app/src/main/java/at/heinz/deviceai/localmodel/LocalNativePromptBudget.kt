package at.heinz.deviceai.localmodel

import java.text.Normalizer

/** Native prefix count plus a conservative rendered-input estimate. */
internal object LocalNativePromptBudget {
    const val ENGINE_CONTEXT_TOKENS = 4_096
    const val FRAMING_RESERVE = 256

    fun contextCapacity(modelLimit: Int): Int {
        require(modelLimit >= 0)
        return if (modelLimit == 0) ENGINE_CONTEXT_TOKENS else minOf(modelLimit, ENGINE_CONTEXT_TOKENS)
    }

    fun renderedUtf8Estimate(text: String): Int = maxOf(text.toByteArray(Charsets.UTF_8).size,
        Normalizer.normalize(text, Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8).size)

    fun promptBytes(contextTokens: Int, prefaceTokens: Int?, outputReserve: Int): Int {
        if (contextTokens <= 0 || prefaceTokens == null || prefaceTokens < 0 || outputReserve < 0) return 0
        return (contextTokens.toLong() - prefaceTokens - outputReserve - FRAMING_RESERVE)
            .coerceIn(0, LocalToolContinuation.MAX_INPUT_BYTES.toLong()).toInt()
    }

    fun allows(payloadBytes: Int, renderedMessageBytes: Int?, existingTokens: Int?, prefaceTokens: Int?,
               contextTokens: Int, outputReserve: Int, fresh: Boolean): Boolean {
        if (payloadBytes !in 1..LocalToolContinuation.MAX_INPUT_BYTES || renderedMessageBytes == null ||
            renderedMessageBytes <= 0 || existingTokens == null || existingTokens < 0 ||
            prefaceTokens == null || prefaceTokens < 0 || contextTokens <= 0 || outputReserve < 0) return false
        // Fresh KV may contain the prefilled prefix already.
        val retained = if (fresh) maxOf(existingTokens, prefaceTokens) else existingTokens
        if (!fresh && retained < prefaceTokens) return false
        return retained.toLong() + maxOf(payloadBytes, renderedMessageBytes) + outputReserve + FRAMING_RESERVE <= contextTokens
    }
}
