package at.heinz.deviceai.localmodel

import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.ToolSpec
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolCall
import com.google.ai.edge.litertlm.tool
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
    data class ToolProposalRejected(val name: String, val reason: String) : LocalInferenceEvent
    data class Completed(val metrics: LocalInferenceMetrics) : LocalInferenceEvent
    data object Cancelled : LocalInferenceEvent
    data class Failed(val message: String) : LocalInferenceEvent
}

fun interface LocalInferenceCallback {
    fun onEvent(event: LocalInferenceEvent)
}

interface LocalModelRuntime {
    val state: LocalModelState
    val loadedModel: ImportedLocalModel?
    fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit)
    fun unload(callback: (Result<Unit>) -> Unit = {})
    fun stream(prompt: String, callback: LocalInferenceCallback)
    fun cancel()
}

class LiteRtLmLocalModelRuntime(
    private val catalog: LocalModelCatalog,
    private val cacheDirectory: File,
    private val systemPrompt: String = "You are Device AI, an on-device assistant."
) : LocalModelRuntime {
    private val background = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "device-ai-local-model").apply { isDaemon = true }
    }
    private val lock = Any()

    @Volatile
    override var state: LocalModelState = LocalModelState.EMPTY
        private set

    @Volatile
    override var loadedModel: ImportedLocalModel? = null
        private set

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var inferenceActive = false
    private var pendingUnload: ((Result<Unit>) -> Unit)? = null

    init { require(systemPrompt.isNotBlank() && systemPrompt.length <= 4_096) }

    override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) {
        synchronized(lock) {
            check(state == LocalModelState.EMPTY || state == LocalModelState.FAILED) {
                "A local model is already loaded or loading."
            }
            state = LocalModelState.LOADING
        }
        background.execute {
            val result = runCatching {
                val model = catalog.get(id, version)
                    ?: error("Imported model is missing or failed its integrity check.")
                val file = catalog.modelFile(model)
                val capabilities = NativeLiteRtModelInspector().inspect(file)
                require(capabilities == model.capabilities) {
                    "Model capability metadata changed after import."
                }
                require(LocalModelBackend.CPU in capabilities.supportedBackends) {
                    "This runtime currently requires CPU support."
                }
                if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
                    error("Cannot create LiteRT-LM cache directory.")
                }
                val newEngine = Engine(
                    EngineConfig(
                        modelPath = file.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = cacheDirectory.absolutePath
                    )
                )
                try {
                    newEngine.initialize()
                    val newConversation = newEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(systemPrompt),
                            tools = ToolRegistry.all().map(::registeredToolProvider),
                            automaticToolCalling = false,
                            maxOutputToken = MAX_OUTPUT_TOKENS
                        )
                    )
                    synchronized(lock) {
                        engine = newEngine
                        conversation = newConversation
                        loadedModel = model
                        state = LocalModelState.READY
                    }
                } catch (failure: Throwable) {
                    runCatching { if (newEngine.isInitialized()) newEngine.close() }
                    throw failure
                }
                model
            }
            if (result.isFailure) {
                synchronized(lock) { state = LocalModelState.FAILED }
            }
            callback(result)
        }
    }

    override fun stream(prompt: String, callback: LocalInferenceCallback) {
        require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARACTERS)
        val activeConversation: Conversation
        val startNanos = System.nanoTime()
        val inputTokens: Int?
        synchronized(lock) {
            check(state == LocalModelState.READY) { "A local model is not ready." }
            activeConversation = checkNotNull(conversation)
            inferenceActive = true
            state = LocalModelState.GENERATING
            inputTokens = runCatching { activeConversation.getTokenCount() }.getOrNull()
        }
        val responseCharacters = intArrayOf(0)
        val chunks = intArrayOf(0)
        var firstTokenNanos: Long? = null
        val finished = AtomicBoolean()
        val outputLimitReached = AtomicBoolean()

        fun finish(event: LocalInferenceEvent?) {
            if (!finished.compareAndSet(false, true)) return
            if (event != null && !outputLimitReached.get()) deliver(callback, event)
            finishInference()
        }

        try {
            activeConversation.sendMessageAsync(
                Contents.of(prompt),
                object : MessageCallback {
                    override fun onMessage(message: com.google.ai.edge.litertlm.Message) {
                        if (message.toolCalls.isNotEmpty()) {
                            message.toolCalls.forEach { toolCall ->
                                val validation = ToolProposal(toolCall.name, toolCall.arguments).validate()
                                val event = if (validation == null) {
                                    LocalInferenceEvent.ToolProposal(
                                        at.heinz.deviceai.ToolProposal(toolCall.name, toolCall.arguments)
                                    )
                                } else {
                                    LocalInferenceEvent.ToolProposalRejected(toolCall.name, validation)
                                }
                                deliver(callback, event)
                            }
                        }
                        message.contents.contents.filterIsInstance<Content.Text>().forEach { text ->
                            if (text.text.isBlank()) return@forEach
                            if (responseCharacters[0] + text.text.length > MAX_RESPONSE_CHARACTERS) {
                                if (outputLimitReached.compareAndSet(false, true)) {
                                    deliver(callback, LocalInferenceEvent.Failed("Local model response exceeded the output limit."))
                                    runCatching { activeConversation.cancelProcess() }
                                }
                                return
                            }
                            responseCharacters[0] += text.text.length
                            chunks[0]++
                            if (firstTokenNanos == null) firstTokenNanos = System.nanoTime()
                            deliver(callback, LocalInferenceEvent.TextDelta(text.text))
                        }
                    }

                    override fun onDone() {
                        if (outputLimitReached.get()) {
                            finish(null)
                            return
                        }
                        val elapsed = (System.nanoTime() - startNanos).coerceAtLeast(0) / 1_000_000
                        val outputTokens = runCatching {
                            activeConversation.getTokenCount() -
                                (inputTokens ?: activeConversation.getTokenCount())
                        }.getOrNull()?.coerceAtLeast(0)
                        finish(
                            LocalInferenceEvent.Completed(
                                LocalInferenceMetrics(
                                    promptCharacters = prompt.length,
                                    responseCharacters = responseCharacters[0],
                                    streamedChunks = chunks[0],
                                    inputTokens = inputTokens,
                                    outputTokens = outputTokens,
                                    firstTokenLatencyMillis = firstTokenNanos?.let {
                                        (it - startNanos).coerceAtLeast(0) / 1_000_000
                                    },
                                    totalLatencyMillis = elapsed
                                )
                            )
                        )
                    }

                    override fun onError(throwable: Throwable) {
                        if (outputLimitReached.get()) {
                            finish(null)
                            return
                        }
                        finish(
                            if (throwable is CancellationException) LocalInferenceEvent.Cancelled
                            else LocalInferenceEvent.Failed(throwable.message.orEmpty().take(512))
                        )
                    }
                }
            )
        } catch (failure: Throwable) {
            if (outputLimitReached.get()) finish(null)
            else finish(LocalInferenceEvent.Failed(failure.message.orEmpty().take(512)))
        }
    }

    override fun cancel() {
        val active = synchronized(lock) {
            if (state != LocalModelState.GENERATING && state != LocalModelState.UNLOADING) null
            else conversation
        }
        runCatching { active?.cancelProcess() }
    }

    override fun unload(callback: (Result<Unit>) -> Unit) {
        val activeConversation: Conversation?
        synchronized(lock) {
            check(state != LocalModelState.LOADING && state != LocalModelState.UNLOADING) {
                "Local model lifecycle operation is already in progress."
            }
            activeConversation = conversation
            if (inferenceActive) {
                state = LocalModelState.UNLOADING
                pendingUnload = callback
            }
        }
        if (activeConversation != null && synchronized(lock) { inferenceActive }) {
            runCatching { activeConversation.cancelProcess() }
            return
        }
        background.execute { closeResources(callback) }
    }

    private fun finishInference() {
        val shouldUnload = synchronized(lock) {
            inferenceActive = false
            if (pendingUnload != null) true
            else {
                if (state == LocalModelState.GENERATING) state = LocalModelState.READY
                false
            }
        }
        if (shouldUnload) {
            val callback = synchronized(lock) {
                pendingUnload.also { pendingUnload = null }
            } ?: return
            background.execute { closeResources(callback) }
        }
    }

    private fun closeResources(callback: (Result<Unit>) -> Unit) {
        val resources = synchronized(lock) {
            val oldConversation = conversation
            val oldEngine = engine
            conversation = null
            engine = null
            loadedModel = null
            oldConversation to oldEngine
        }
        val result = runCatching {
            resources.first?.let { if (it.isAlive) it.close() }
            resources.second?.let { if (it.isInitialized()) it.close() }
            Unit
        }
        synchronized(lock) {
            state = if (result.isSuccess) LocalModelState.EMPTY else LocalModelState.FAILED
        }
        callback(result)
    }

    private fun deliver(callback: LocalInferenceCallback, event: LocalInferenceEvent) {
        runCatching { callback.onEvent(event) }
    }

    private fun registeredToolProvider(spec: ToolSpec) = tool(
        object : OpenApiTool {
            override fun getToolDescriptionJsonString(): String {
                val properties = JSONObject()
                (spec.required + spec.optional).sorted().forEach { argument ->
                    val type = when {
                        argument in setOf("lines", "limit", "seconds") -> "integer"
                        argument == "userOnly" -> "boolean"
                        argument == "roots" -> "array"
                        else -> "string"
                    }
                    val field = JSONObject().put("type", type)
                    if (type == "array") field.put("items", JSONObject().put("type", "string"))
                    properties.put(argument, field)
                }
                val parameters = JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                if (spec.required.isNotEmpty()) {
                    parameters.put("required", JSONArray(spec.required.sorted()))
                }
                return JSONObject()
                    .put("name", spec.name)
                    .put("description", "${spec.description} Risk: ${spec.risk.name}.")
                    .put("parameters", parameters)
                    .toString()
            }

            override fun execute(paramsJsonString: String): String =
                """{"error":"Tool execution must be submitted to Device AI's explicit ToolGate."}"""
        }
    )

    private companion object {
        const val MAX_PROMPT_CHARACTERS = 16_000
        const val MAX_RESPONSE_CHARACTERS = 64_000
        const val MAX_OUTPUT_TOKENS = 512
    }
}
