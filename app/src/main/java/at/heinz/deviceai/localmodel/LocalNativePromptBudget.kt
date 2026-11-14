package at.heinz.deviceai.localmodel

import java.text.Normalizer

/** Native counts; estimated planning; strict continuation bounds. */
internal object LocalNativePromptBudget {
    const val ENGINE_CONTEXT_TOKENS = 4_096
    const val FRAMING_RESERVE = 256
    private const val MIN_OUTPUT_TOKENS = 128

    data class Plan(val plannedInputTokens: Int, val maxOutputTokens: Int,
                    val nativeFullInputCheckRequired: Boolean)

    fun contextCapacity(modelLimit: Int): Int {
        require(modelLimit >= 0)
        return if (modelLimit == 0) ENGINE_CONTEXT_TOKENS else minOf(modelLimit, ENGINE_CONTEXT_TOKENS)
    }

    /** Answer tokens a prompt must leave free (or the model's own smaller output limit). */
    const val MIN_ANSWER_RESERVE = 512
    // Conservative planning reserve, not a measured token count. Native checks remain final.
    const val TOOL_FREE_PREFACE_RESERVE = 512
    fun toolFreePromptByteBudget(contextTokens: Int, requestedOutput: Int): Int? =
        promptByteBudget(contextTokens, TOOL_FREE_PREFACE_RESERVE, requestedOutput)
            ?.coerceAtMost(LocalToolContinuation.MAX_INPUT_BYTES)


    /**
     * Code56 field P0: the app built prompts up to 15 500 characters although the measured preface (system
     * instruction + 33 tool declarations ≈ 7 kB JSON ≈ 1.8–2.4 k tokens on the host count) already fills about half
     * of the 4 096-token window; such prompts failed natively („passen nicht in das Kontextfenster“, overflow).
     * This returns the prompt UTF-8 bytes that keep [plan]'s own 3 bytes/token estimate inside the window while
     * leaving a usable answer. Planning bound, never proof: the native prefill check still decides.
     */
    fun promptByteBudget(contextTokens: Int, prefaceTokens: Int?, requestedOutput: Int): Int? {
        if (contextTokens <= 0 || prefaceTokens == null || prefaceTokens < 0 || requestedOutput < 1) return null
        val answer = minOf(requestedOutput, MIN_ANSWER_RESERVE)
        val tokens = contextTokens.toLong() - prefaceTokens - answer - FRAMING_RESERVE
        if (tokens <= 0) return 0
        return (tokens * 3).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun renderedUtf8Estimate(text: String): Int = maxOf(text.toByteArray(Charsets.UTF_8).size,
        Normalizer.normalize(text, Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8).size)

    fun plan(payloadBytes: Int, renderedMessageBytes: Int?, existingTokens: Int?, prefaceTokens: Int?,
             contextTokens: Int, requestedOutput: Int, fresh: Boolean): Plan? {
        if (payloadBytes !in 1..LocalToolContinuation.MAX_INPUT_BYTES || renderedMessageBytes == null ||
            renderedMessageBytes <= 0 || existingTokens == null || existingTokens < 0 ||
            prefaceTokens == null || prefaceTokens < 0 || contextTokens <= 0 || requestedOutput < 1) return null
        val capacity = contextTokens.toLong()
        if (prefaceTokens >= capacity || existingTokens >= capacity) return null
        if (fresh) {
            // One native prefill checks everything together.
            if (existingTokens != 0) return null
            // Three bytes/token is planning, never proof.
            val estimate = maxOf((renderedMessageBytes.toLong() + 2) / 3,
                prefaceTokens.toLong() + (payloadBytes.toLong() + 2) / 3)
            val ceiling = capacity - prefaceTokens - 1
            if (ceiling < MIN_OUTPUT_TOKENS) return null
            val output = minOf(requestedOutput.toLong(), ceiling,
                (capacity - estimate - FRAMING_RESERVE).coerceAtLeast(MIN_OUTPUT_TOKENS.toLong()))
            return Plan(estimate.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), output.toInt(), true)
        }
        if (existingTokens < prefaceTokens) return null
        // Older SDK lacks cumulative prefill validation.
        val inputBound = maxOf(payloadBytes, renderedMessageBytes).toLong()
        val available = capacity - existingTokens - inputBound - FRAMING_RESERVE
        if (available < MIN_OUTPUT_TOKENS) return null
        return Plan(inputBound.toInt(), minOf(requestedOutput.toLong(), available).toInt(), false)
    }

    fun contextFailure(message: String): Boolean = message.contains("Input token ids are too long", true) ||
        message.contains("maximum number of tokens", true) || message.contains("max_num_tokens", true) ||
        message.contains("kMaxNumTokensReached", true) || message.contains("remaining capacity", true) ||
        message.contains("Work groups not covering the entire prefill input", true)

    fun completeContext(nativeTokens: Int?, contextTokens: Int): Boolean =
        nativeTokens != null && contextTokens > 0 && nativeTokens in 1 until contextTokens
}
