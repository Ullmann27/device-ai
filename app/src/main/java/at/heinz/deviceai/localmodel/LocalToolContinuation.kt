package at.heinz.deviceai.localmodel

import org.json.JSONObject

/** Common admission policy for existing local adapters; grants no tool rights. */
internal object LocalToolContinuation {
    const val MAX_INPUT_BYTES = 16_000
    const val MAX_RESPONSES = 4
    private const val MESSAGE_RESERVE = 256

    /** Integrity only; source validation and permission checks remain with the caller. */
    fun containsCompleteOutput(prompt: String, output: String): Boolean = prompt.contains(JSONObject.quote(output))

    /** Select the existing engine channel; the caller has already checked the full prompt. */
    fun submit(runtime: LocalModelRuntime, prompt: String, forceFresh: Boolean,
               responses: () -> List<LocalToolResponse>, callback: LocalInferenceCallback) {
        // Screen evidence is already complete in the checked JSON prompt. It does
        // not enter the native tool-response channel or that channel's budget.
        if (forceFresh) {
            runtime.stream(prompt, callback)
            return
        }
        val toolResponses = responses()
        if (toolResponses.isEmpty()) runtime.stream(prompt, callback)
        else runtime.respondToTools(toolResponses, callback)
    }

    /** Complete field values only; role headers/newlines cannot escape the JSON. */
    fun observationData(toolName: String,status: String,output: String?): String =
        JSONObject().put("tool_name",toolName).put("status",status)
            .put("trust","untrusted_external_data")
            .put("output",output ?: JSONObject.NULL).toString()

    fun text(responses: List<LocalToolResponse>): String? {
        if (responses.isEmpty() || responses.size > MAX_RESPONSES) return null
        val text = responses.joinToString("\n") { "${it.toolName}: ${it.response}" }
        return text.takeIf { it.length <= MAX_INPUT_BYTES &&
            it.toByteArray(Charsets.UTF_8).size <= MAX_INPUT_BYTES }
    }

    /** Measure the serialized JSON, including escaping, before constructing it. */
    fun encode(toolName: String, status: String, output: String?): LocalToolResponse? {
        val encoded = observationData(toolName,status,output)
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

    fun allowsSubmission(payloadBytes: Int,existingTokens: Int?,contextTokens: Int,
                         outputReserve: Int,continuation: Boolean): Boolean =
        fitsContext(payloadBytes,existingTokens,contextTokens,outputReserve)
}
