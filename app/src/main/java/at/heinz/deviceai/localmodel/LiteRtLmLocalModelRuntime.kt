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
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.tool
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors

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
    fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit)
    fun unload(callback: (Result<Unit>) -> Unit = {})
    fun stream(prompt: String, callback: LocalInferenceCallback)
    fun streamThinking(prompt: String, callback: LocalInferenceCallback) {
        error("This runtime does not provide isolated tool-free thinking.")
    }
    fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) {
        val continuation = responses.joinToString("\n") { "${it.toolName}: ${it.response}" }
        stream(continuation, callback)
    }
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
    private var inferenceConversation: Conversation? = null
    private var inferenceActive = false
    private var inferenceCancellation: (() -> Unit)? = null
    private var conversationCompletion: LocalInferenceCallbackGate? = null
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
                            maxOutputToken = LocalModelGenerationPolicy.maxOutputTokens(model.id)
                        )
                    )
                    synchronized(lock) {
                        engine = newEngine
                        conversation = newConversation
                        conversationCompletion = null
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
        // Qwen3 soft switch is for direct owner messages only, not reasoning
        // workers or tool responses. Loaded model ID is already verified by catalog.
        val effectivePrompt = LocalQwenPromptPolicy.directUserPrompt(
            loadedModel?.id, prompt, MAX_PROMPT_CHARACTERS
        )
        streamMessage(Message.user(Contents.of(effectivePrompt)), effectivePrompt.length, callback, false, freshPrompt = true)
    }

    override fun streamThinking(prompt: String, callback: LocalInferenceCallback) {
        require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARACTERS)
        streamMessage(Message.user(Contents.of(prompt)), prompt.length, callback, true)
    }

    override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) {
        require(responses.isNotEmpty() && responses.size <= MAX_TOOL_RESPONSES)
        val contents: List<Content> = responses.map { Content.ToolResponse(it.toolName, it.response) }
        val characterCount = responses.sumOf { it.response.length }
        require(characterCount <= MAX_PROMPT_CHARACTERS)
        streamMessage(Message.tool(Contents.of(contents)), characterCount, callback, false)
    }

    private fun streamMessage(
        message: Message,
        promptCharacters: Int,
        callback: LocalInferenceCallback,
        isolatedThinking: Boolean,
        freshPrompt: Boolean = false
    ) {
        lateinit var activeConversation: Conversation
        val startNanos = System.nanoTime()
        var inputTokens: Int? = null
        var responseCharacters = 0
        var chunks = 0
        var firstTokenNanos: Long? = null
        val toolTurnGate = LocalToolTurnGate()

        fun finish(event: LocalInferenceEvent) {
            if (isolatedThinking) {
                // Keep native ownership until its terminal callback and isolated close.
                // Unload must not close the parent engine while inference is active.
                background.execute {
                    val closed = runCatching {
                        if (activeConversation.isAlive) activeConversation.close()
                    }
                    synchronized(lock) {
                        if (closed.isSuccess && inferenceConversation === activeConversation) {
                            inferenceConversation = null
                        }
                        if (closed.isFailure && pendingUnload == null)
                            state = LocalModelState.FAILED
                    }
                    finishInference()
                    if (closed.isFailure) {
                        deliver(callback, LocalInferenceEvent.Failed(
                            "Isolated conversation could not be released safely."))
                    } else deliver(callback, event)
                }
            } else {
                synchronized(lock) {
                    if (inferenceConversation === activeConversation) {
                        inferenceConversation = null
                    }
                }
                finishInference()
                deliver(callback, event)
            }
        }

        val callbackGate = LocalInferenceCallbackGate(::finish)
        fun cancelInference(event: LocalInferenceEvent) {
            callbackGate.cancel(event) {
                runCatching { activeConversation.cancelProcess() }
            }
        }

        synchronized(lock) {
            check(state == LocalModelState.READY) { "A local model is not ready." }
            // LiteRT-LM 0.18.0 cannot reuse a cancelled native conversation.
            // A new full prompt replaces it; a tool reply cannot repair its state.
            check(isolatedThinking || freshPrompt ||
                conversationCompletion?.allowsToolContinuation == true) {
                "No successfully completed local turn is available for tool continuation."
            }
            activeConversation = if (isolatedThinking) {
                checkNotNull(engine).createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(
                            "You are a data-only reasoning worker for Device AI. " +
                                "You have no tools and no authority to act."
                        ),
                        tools = emptyList(),
                        automaticToolCalling = false,
                        maxOutputToken = LocalModelGenerationPolicy.maxOutputTokens(loadedModel?.id)
                    )
                )
            } else {
                try {
                    selectLocalConversation(conversation, freshPrompt, create = {
                        checkNotNull(engine).createConversation(ConversationConfig(
                            systemInstruction = Contents.of(systemPrompt),
                            tools = ToolRegistry.all().map(::registeredToolProvider),
                            automaticToolCalling = false,
                            maxOutputToken = LocalModelGenerationPolicy.maxOutputTokens(loadedModel?.id)
                        ))
                    }, close = { if (it.isAlive) it.close() }).also {
                        conversation = it
                        conversationCompletion = callbackGate
                    }
                } catch (failure: Exception) {
                    state = LocalModelState.FAILED
                    throw failure
                }
            }
            inferenceConversation = activeConversation
            inferenceCancellation = { cancelInference(LocalInferenceEvent.Cancelled) }
            inferenceActive = true
            state = LocalModelState.GENERATING
            inputTokens = runCatching { activeConversation.getTokenCount() }.getOrNull()
        }

        try {
            callbackGate.start {
                activeConversation.sendMessageAsync(
                    message,
                    object : MessageCallback {
                        override fun onMessage(message: com.google.ai.edge.litertlm.Message) {
                            callbackGate.message processMessage@{
                                if (message.toolCalls.isNotEmpty()) {
                                    if (!toolTurnGate.accept(message.toolCalls.size)) {
                                        cancelInference(LocalInferenceEvent.Failed(
                                            "More than one tool proposal occurred in a single model turn; none are authorized."))
                                        return@processMessage
                                    }
                                    val toolCall = message.toolCalls.single()
                                    val validation = ToolProposal(toolCall.name, toolCall.arguments).validate()
                                    val event = if (validation == null) {
                                        LocalInferenceEvent.ToolProposal(
                                            at.heinz.deviceai.ToolProposal(toolCall.name, toolCall.arguments)
                                        )
                                    } else {
                                        LocalInferenceEvent.ToolProposalRejected(
                                            toolCall.name, validation, toolCall.arguments
                                        )
                                    }
                                    deliver(callback, event)
                                }
                                message.contents.contents.filterIsInstance<Content.Text>().forEach { text ->
                                    callbackGate.text(text.text) { chunk ->
                                        if (responseCharacters + chunk.length > MAX_RESPONSE_CHARACTERS) {
                                            cancelInference(LocalInferenceEvent.Failed(
                                                "Local model response exceeded the output limit."))
                                        } else {
                                            responseCharacters += chunk.length
                                            chunks++
                                            if (firstTokenNanos == null) firstTokenNanos = System.nanoTime()
                                            deliver(callback, LocalInferenceEvent.TextDelta(chunk))
                                        }
                                    }
                                }
                            }
                        }

                        override fun onDone() {
                            callbackGate.terminal {
                                val elapsed = (System.nanoTime() - startNanos).coerceAtLeast(0) / 1_000_000
                                val outputTokens = runCatching {
                                    activeConversation.getTokenCount() -
                                        (inputTokens ?: activeConversation.getTokenCount())
                                }.getOrNull()?.coerceAtLeast(0)
                                LocalInferenceEvent.Completed(
                                    LocalInferenceMetrics(
                                        promptCharacters = promptCharacters,
                                        responseCharacters = responseCharacters,
                                        streamedChunks = chunks,
                                        inputTokens = inputTokens,
                                        outputTokens = outputTokens,
                                        firstTokenLatencyMillis = firstTokenNanos?.let {
                                            (it - startNanos).coerceAtLeast(0) / 1_000_000
                                        },
                                        totalLatencyMillis = elapsed
                                    )
                                )
                            }
                        }

                        override fun onError(throwable: Throwable) {
                            callbackGate.terminal {
                                if (throwable is CancellationException) LocalInferenceEvent.Cancelled
                                else LocalInferenceEvent.Failed(throwable.message.orEmpty().take(512))
                            }
                        }
                    }
                )
            }
        } catch (failure: Throwable) {
            callbackGate.terminal {
                LocalInferenceEvent.Failed(failure.message.orEmpty().take(512))
            }
        }
    }

    override fun cancel() {
        val cancelActive = synchronized(lock) {
            if (state == LocalModelState.GENERATING || state == LocalModelState.UNLOADING)
                inferenceCancellation
            else null
        }
        cancelActive?.invoke()
    }

    override fun unload(callback: (Result<Unit>) -> Unit) {
        val cancelActive: (() -> Unit)?
        val deferredToInferenceCompletion: Boolean
        synchronized(lock) {
            check(state != LocalModelState.LOADING && state != LocalModelState.UNLOADING) {
                "Local model lifecycle operation is already in progress."
            }
            deferredToInferenceCompletion = inferenceActive
            cancelActive = if (deferredToInferenceCompletion) checkNotNull(inferenceCancellation) else null
            state = LocalModelState.UNLOADING
            if (deferredToInferenceCompletion) pendingUnload = callback
        }
        if (deferredToInferenceCompletion) {
            cancelActive?.invoke()
            return
        }
        background.execute { closeResources(callback) }
    }

    private fun finishInference() {
        val shouldUnload = synchronized(lock) {
            inferenceActive = false
            inferenceCancellation = null
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
            val oldInference = inferenceConversation
            val oldEngine = engine
            conversation = null
            inferenceConversation = null
            inferenceCancellation = null
            conversationCompletion = null
            engine = null
            loadedModel = null
            Triple(oldConversation, oldInference, oldEngine)
        }
        val result = runCatching {
            val (oldConversation, oldInference, oldEngine) = resources
            if (oldInference != null && oldInference !== oldConversation && oldInference.isAlive) {
                oldInference.close()
            }
            if (oldConversation?.isAlive == true) oldConversation.close()
            if (oldEngine?.isInitialized() == true) oldEngine.close()
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
        const val MAX_TOOL_RESPONSES = 4
    }
}

/**
 * Serializes native callbacks with cancellation. A requested stop suppresses
 * output immediately, but lifecycle ownership ends only on native terminal.
 * No JNI/native resource is closed by this helper.
 */
internal class LocalInferenceCallbackGate(
    private val onTerminal: (LocalInferenceEvent) -> Unit
) {
    private val lock = Any()
    private var started = false
    private var finished = false
    private var requestedTerminal: LocalInferenceEvent? = null

    // Read under the runtime lifecycle lock without acquiring this callback
    // lock: terminal delivery acquires the locks in the opposite order.
    @Volatile
    var allowsToolContinuation: Boolean = false
        private set

    fun start(send: () -> Unit) {
        synchronized(lock) {
            if (finished) return
            started = true
            send()
        }
    }

    fun message(deliver: () -> Unit) {
        synchronized(lock) {
            if (!finished && requestedTerminal == null) deliver()
        }
    }

    fun text(chunk: String, deliver: (String) -> Unit) {
        message {
            // Each callback is a delta. Standalone spaces/newlines are content.
            if (chunk.isNotEmpty()) deliver(chunk)
        }
    }

    fun cancel(event: LocalInferenceEvent, cancelNative: () -> Unit) {
        synchronized(lock) {
            if (finished || requestedTerminal != null) return
            requestedTerminal = event
            if (!started) terminal { event }
            else cancelNative()
        }
    }

    fun terminal(event: () -> LocalInferenceEvent) {
        synchronized(lock) {
            if (finished) return
            val result = requestedTerminal ?: event()
            finished = true
            allowsToolContinuation = result is LocalInferenceEvent.Completed
            onTerminal(result)
        }
    }
}
