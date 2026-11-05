package at.heinz.deviceai.localmodel

import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.ToolSpec
import at.heinz.deviceai.ToolExecutionStartLatch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class LocalModelState { EMPTY, LOADING, READY, GENERATING, UNLOADING, FAILED }

data class LocalInferenceMetrics(
    val promptCharacters: Int,
    val responseCharacters: Int,
    val streamedChunks: Int,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val firstTokenLatencyMillis: Long?,
    val totalLatencyMillis: Long
)

sealed interface LocalInferenceEvent {
    data class TextDelta(val text: String) : LocalInferenceEvent
    data class ToolProposal(val proposal: at.heinz.deviceai.ToolProposal) : LocalInferenceEvent
    data class ToolProposalRejected(
        val name: String,
        val reason: String,
        val arguments: Map<String, Any?> = emptyMap()
    ) : LocalInferenceEvent
    data class ToolBatchRejected(val responses: List<LocalToolResponse>) : LocalInferenceEvent
    data class Completed(val metrics: LocalInferenceMetrics) : LocalInferenceEvent
    data object Cancelled : LocalInferenceEvent
    data class Failed(val message: String) : LocalInferenceEvent
}

fun interface LocalInferenceCallback {
    fun onEvent(event: LocalInferenceEvent)
}

data class LocalToolResponse(val toolName: String, val response: String) {
    init {
        require(toolName.isNotBlank() && toolName.length <= 128)
        require(response.length <= 8_000)
    }
}

interface LocalModelRuntime {
    val state: LocalModelState
    val loadedModel: ImportedLocalModel?
    /** Bounded numeric diagnostics; no native model/request/response data. */
    val inferenceProfiles: List<LocalInferenceProfile> get() = emptyList()
    fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit)
    fun unload(callback: (Result<Unit>) -> Unit = {})
    fun stream(prompt: String, callback: LocalInferenceCallback)
    fun streamThinking(prompt: String, callback: LocalInferenceCallback) {
        error("This runtime does not provide isolated tool-free thinking.")
    }
    fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) {
        val continuation = LocalToolContinuation.text(responses) ?: run {
            callback.onEvent(LocalInferenceEvent.Failed("LOCAL_TOOL_CONTEXT_EXCEEDS_BUDGET"))
            return
        }
        stream(continuation, callback)
    }
    fun cancel()
}

/** Host harness stand-in: the LiteRT-LM AAR is not reachable here (dl.google.com blocked). Never shipped. */
class LiteRtLmLocalModelRuntime(
    @Suppress("unused") private val catalog: LocalModelCatalog,
    @Suppress("unused") private val cacheDirectory: java.io.File,
    @Suppress("unused") private val systemPrompt: String = "",
) : LocalModelRuntime {
    override val state: LocalModelState get() = LocalModelState.EMPTY
    override val loadedModel: ImportedLocalModel? get() = null
    override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) = callback(Result.failure(UnsupportedOperationException("host")))
    override fun unload(callback: (Result<Unit>) -> Unit) = callback(Result.success(Unit))
    override fun stream(prompt: String, callback: LocalInferenceCallback) = Unit
    override fun cancel() = Unit
}
