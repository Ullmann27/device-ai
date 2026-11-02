package at.heinz.deviceai.localmodel

import org.json.JSONObject

/** Common admission policy for existing local adapters; grants no tool rights. */
internal object LocalToolContinuation {
    const val MAX_INPUT_BYTES = 16_000
    const val MAX_RESPONSES = 4
    private const val MESSAGE_RESERVE = 256

    fun text(responses: List<LocalToolResponse>): String? {
        if (responses.isEmpty() || responses.size > MAX_RESPONSES) return null
        val text = responses.joinToString("\n") { "${it.toolName}: ${it.response}" }
        return text.takeIf { it.length <= MAX_INPUT_BYTES &&
            it.toByteArray(Charsets.UTF_8).size <= MAX_INPUT_BYTES }
    }

    /** Measure the serialized JSON, including escaping, before constructing it. */
    fun encode(toolName: String, status: String, output: String?): LocalToolResponse? {
        val encoded = JSONObject().put("status", status)
            .put("output", output ?: JSONObject.NULL).toString()
        if (encoded.length > 8_000 || encoded.toByteArray(Charsets.UTF_8).size > MAX_INPUT_BYTES)
            return null
        return LocalToolResponse(toolName, encoded)
    }

    /** Conservative UTF-8 upper estimate, not a tokenizer or a device token proof. */
    fun fitsContext(payloadBytes: Int, existingTokens: Int?, contextTokens: Int,
                    outputReserve: Int): Boolean {
        if (payloadBytes !in 1..MAX_INPUT_BYTES || existingTokens == null ||
            existingTokens < 0 || contextTokens <= 0 || outputReserve < 0) return false
        return existingTokens.toLong() + payloadBytes + MESSAGE_RESERVE + outputReserve <= contextTokens
    }
}
