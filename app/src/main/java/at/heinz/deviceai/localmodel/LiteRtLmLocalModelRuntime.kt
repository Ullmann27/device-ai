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

class LiteRtLmLocalModelRuntime(
    private val catalog: LocalModelCatalog,
    private val cacheDirectory: File,
    private val systemPrompt: String = "You are Device AI, an on-device assistant."
) : LocalModelRuntime {
    private val background = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "device-ai-local-model").apply { isDaemon = true }
    }
    private val lock = Any()
    private val inferenceProfiler = LocalInferenceProfiles()
    override val inferenceProfiles: List<LocalInferenceProfile>
        get() = inferenceProfiler.snapshots()

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
    private var activeInferenceProfile: LocalInferenceProfileRecorder? = null
    private var pendingUnload: ((Result<Unit>) -> Unit)? = null
    private var activeCancellation: AtomicBoolean? = null
    private var activeDeliveryLock: Any? = null

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
        require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARACTERS &&
            prompt.toByteArray(Charsets.UTF_8).size <= LocalToolContinuation.MAX_INPUT_BYTES)
        // Qwen3 soft switch is for direct owner messages only, not reasoning
        // workers or tool responses. Loaded model ID is already verified by catalog.
        val effectivePrompt = LocalQwenPromptPolicy.directUserPrompt(
            loadedModel?.id, prompt, MAX_PROMPT_CHARACTERS
        )
        streamMessage(Message.user(Contents.of(effectivePrompt)), effectivePrompt.length, callback, false,
            freshPrompt = true, payloadBytes = effectivePrompt.toByteArray(Charsets.UTF_8).size)
    }

    override fun streamThinking(prompt: String, callback: LocalInferenceCallback) {
        require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARACTERS &&
            prompt.toByteArray(Charsets.UTF_8).size <= LocalToolContinuation.MAX_INPUT_BYTES)
        streamMessage(Message.user(Contents.of(prompt)), prompt.length, callback, true,
            payloadBytes = prompt.toByteArray(Charsets.UTF_8).size)
    }

    override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) {
        val payload = LocalToolContinuation.text(responses) ?: run {
            deliver(callback, LocalInferenceEvent.Failed("LOCAL_TOOL_CONTEXT_EXCEEDS_BUDGET"))
            return
        }
        val contents: List<Content> = responses.map { Content.ToolResponse(it.toolName, it.response) }
        streamMessage(Message.tool(Contents.of(contents)), payload.length, callback, false,
            payloadBytes = payload.toByteArray(Charsets.UTF_8).size)
    }

    private fun streamMessage(
        message: Message,
        promptCharacters: Int,
        callback: LocalInferenceCallback,
        isolatedThinking: Boolean,
        freshPrompt: Boolean = false,
        payloadBytes: Int
    ) {
        val profile = inferenceProfiler.begin(when {
            isolatedThinking -> LocalInferenceKind.ISOLATED_THINKING
            freshPrompt -> LocalInferenceKind.CHAT
            else -> LocalInferenceKind.TOOL_CONTINUATION
        })
        val activeConversation: Conversation
        val startNanos = System.nanoTime()
        val inputTokens: Int?
        val generationCancelled = AtomicBoolean(false)
        val callbackLock = Any()
        synchronized(lock) {
            check(state == LocalModelState.READY) { "A local model is not ready." }
            profile.mark(LocalInferenceStage.CONVERSATION_SELECTION_ENTER)
            activeConversation = if (isolatedThinking) {
                profile.measure(LocalInferenceStage.CONVERSATION_CREATE_ENTER,
                    LocalInferenceStage.CONVERSATION_CREATE_EXIT) {
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
                }
            } else {
                try {
                    selectLocalConversation(conversation, freshPrompt, create = {
                        profile.measure(LocalInferenceStage.CONVERSATION_CREATE_ENTER,
                            LocalInferenceStage.CONVERSATION_CREATE_EXIT) {
                            checkNotNull(engine).createConversation(ConversationConfig(
                                systemInstruction = Contents.of(systemPrompt),
                                tools = ToolRegistry.all().map(::registeredToolProvider),
                                automaticToolCalling = false,
                                maxOutputToken = LocalModelGenerationPolicy.maxOutputTokens(loadedModel?.id)
                            ))
                        }
                    }, close = {
                        profile.measure(LocalInferenceStage.PREVIOUS_CONVERSATION_RELEASE_ENTER,
                            LocalInferenceStage.PREVIOUS_CONVERSATION_RELEASE_EXIT) {
                            if (it.isAlive) it.close()
                        }
                    }).also { conversation = it }
                } catch (failure: Exception) {
                    profile.mark(LocalInferenceStage.INVOCATION_THROW)
                    state = LocalModelState.FAILED
                    throw failure
                }
            }
            profile.mark(LocalInferenceStage.CONVERSATION_SELECTION_EXIT)
            inferenceConversation = activeConversation
            activeInferenceProfile = profile
            inferenceActive = true
            activeCancellation = generationCancelled
            activeDeliveryLock = callbackLock
            state = LocalModelState.GENERATING
            inputTokens = profile.measure(LocalInferenceStage.INPUT_TOKEN_COUNT_ENTER,
                LocalInferenceStage.INPUT_TOKEN_COUNT_EXIT) {
                runCatching { activeConversation.getTokenCount() }.getOrNull()
            }
        }
        // Serializes streamed chunks, terminal events and visible callback delivery.
        val terminalOverrideRequested = AtomicBoolean(false)
        val responseCharacters = intArrayOf(0)
        val chunks = intArrayOf(0)
        var firstTokenNanos: Long? = null
        val finished = AtomicBoolean()
        val outputLimitReached = AtomicBoolean()
        val toolTurnGate = LocalToolTurnGate()

        fun deliverTerminal(event: LocalInferenceEvent) {
            val terminal = when (event) {
                is LocalInferenceEvent.Completed -> LocalInferenceTerminal.COMPLETED
                LocalInferenceEvent.Cancelled -> LocalInferenceTerminal.CANCELLED
                else -> LocalInferenceTerminal.FAILED
            }
            profile.runtimeTerminal(terminal)
            profile.measure(LocalInferenceStage.RUNTIME_TERMINAL_DELIVERY_ENTER,
                LocalInferenceStage.RUNTIME_TERMINAL_DELIVERY_EXIT) {
                deliver(callback, event)
            }
        }

        fun finish(event: LocalInferenceEvent?) {
            if (!finished.compareAndSet(false, true)) return
            if (isolatedThinking) {
                // Keep the isolated conversation owned until its close completes.
                // Unload waits for finishInference before closing the parent engine.
                background.execute {
                    val closed = runCatching {
                        if (activeConversation.isAlive) activeConversation.close()
                    }
                    synchronized(lock) {
                        if (closed.isSuccess && inferenceConversation === activeConversation)
                            inferenceConversation = null
                        if (closed.isFailure && pendingUnload == null)
                            state = LocalModelState.FAILED
                    }
                    finishInference()
                    if (closed.isFailure) {
                        deliverTerminal(LocalInferenceEvent.Failed(
                            "Isolated conversation could not be released safely."))
                    } else if (event != null) deliverTerminal(
                        if (generationCancelled.get()) LocalInferenceEvent.Cancelled else event)
                }
            } else {
                synchronized(lock) {
                    if (inferenceConversation === activeConversation) inferenceConversation = null
                }
                finishInference()
                if (event != null) deliverTerminal(
                    if (generationCancelled.get()) LocalInferenceEvent.Cancelled else event)
            }
        }

        try {
            // Native context includes prior tool turns, system text and tool schemas.
            // Unknown capacity/count refuses admission instead of guessing a fit.
            if (!LocalToolContinuation.fitsContext(payloadBytes, inputTokens,
                    loadedModel?.capabilities?.maxContextTokens ?: 0,
                    LocalModelGenerationPolicy.maxOutputTokens(loadedModel?.id))) {
                finish(LocalInferenceEvent.Failed("LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL"))
                return
            }
            synchronized(lock) {
            if (generationCancelled.get() || state != LocalModelState.GENERATING) {
                finish(LocalInferenceEvent.Cancelled)
                return
            }
            profile.mark(LocalInferenceStage.NATIVE_DISPATCH_ENTER)
            activeConversation.sendMessageAsync(
                message,
                object : MessageCallback {
                    override fun onMessage(message: com.google.ai.edge.litertlm.Message) {
                        message.contents.contents.filterIsInstance<Content.Text>().forEach {
                            profile.nativeText(it.text.length)
                        }
                        synchronized(callbackLock) {
                            if (finished.get() || terminalOverrideRequested.get() || generationCancelled.get()) return
                            if (message.toolCalls.isNotEmpty()) {
                                if (!toolTurnGate.accept(message.toolCalls.size)) {
                                    // Signal the terminal failure before cancelling: some native
                                    // engines deliver onError reentrantly from cancelProcess().
                                    terminalOverrideRequested.set(true)
                                    profile.cancel(LocalInferenceCancelOrigin.TOOL_BATCH_REJECTED) {
                                        runCatching { activeConversation.cancelProcess() }
                                    }
                                    finish(LocalInferenceEvent.Failed(
                                        "More than one tool proposal occurred in a single model turn; none are authorized."))
                                    return
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
                                // Native callbacks are deltas; standalone spaces/newlines
                                // must survive or prose and generated code are corrupted.
                                if (text.text.isEmpty() || finished.get() || terminalOverrideRequested.get())
                                    return@forEach
                                if (responseCharacters[0] + text.text.length > MAX_RESPONSE_CHARACTERS) {
                                    if (outputLimitReached.compareAndSet(false, true)) {
                                        terminalOverrideRequested.set(true)
                                        profile.cancel(LocalInferenceCancelOrigin.OUTPUT_LIMIT) {
                                            runCatching { activeConversation.cancelProcess() }
                                        }
                                        finish(LocalInferenceEvent.Failed(
                                            "Local model response exceeded the output limit."))
                                    }
                                    return
                                }
                                responseCharacters[0] += text.text.length
                                chunks[0]++
                                if (firstTokenNanos == null) firstTokenNanos = System.nanoTime()
                                deliver(callback, LocalInferenceEvent.TextDelta(text.text))
                            }
                        }
                    }

                    override fun onDone() {
                        profile.nativeTerminal(LocalInferenceTerminal.COMPLETED)
                        try {
                            synchronized(callbackLock) {
                                if (finished.get() || terminalOverrideRequested.get()) return
                                val elapsed = (System.nanoTime() - startNanos).coerceAtLeast(0) / 1_000_000
                                val outputTokens = runCatching {
                                    activeConversation.getTokenCount() -
                                        (inputTokens ?: activeConversation.getTokenCount())
                                }.getOrNull()?.coerceAtLeast(0)
                                finish(
                                    LocalInferenceEvent.Completed(
                                        LocalInferenceMetrics(
                                            promptCharacters = promptCharacters,
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
                        } finally {
                            profile.mark(LocalInferenceStage.NATIVE_TERMINAL_CALLBACK_EXIT)
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        profile.nativeTerminal(if (throwable is CancellationException)
                            LocalInferenceTerminal.CANCELLED else LocalInferenceTerminal.FAILED)
                        try {
                            synchronized(callbackLock) {
                                if (finished.get() || terminalOverrideRequested.get()) return
                                finish(
                                    if (throwable is CancellationException) LocalInferenceEvent.Cancelled
                                    else LocalInferenceEvent.Failed(throwable.message.orEmpty().take(512))
                                )
                            }
                        } finally {
                            profile.mark(LocalInferenceStage.NATIVE_TERMINAL_CALLBACK_EXIT)
                        }
                    }
                }
            )
            profile.mark(LocalInferenceStage.NATIVE_DISPATCH_RETURN)
            }
        } catch (failure: Throwable) {
            profile.mark(LocalInferenceStage.NATIVE_DISPATCH_THROW)
            synchronized(callbackLock) {
                if (!finished.get() && !terminalOverrideRequested.get())
                    finish(LocalInferenceEvent.Failed(failure.message.orEmpty().take(512)))
            }
        }
    }

    override fun cancel() {
        val active = synchronized(lock) {
            activeCancellation?.set(true)
            if (state != LocalModelState.GENERATING && state != LocalModelState.UNLOADING) null
            else Triple(inferenceConversation ?: conversation, activeInferenceProfile, activeDeliveryLock)
        } ?: return
        // Join any delivery already in progress; later callbacks see the revoked
        // generation. Never hold the delivery lock while calling native cancel.
        active.third?.let { synchronized(it) {} }
        val cancelNative = { runCatching { active.first?.cancelProcess() } }
        val profile = active.second
        if (profile == null) cancelNative()
        else profile.cancel(LocalInferenceCancelOrigin.CALLER, cancelNative)
    }

    override fun unload(callback: (Result<Unit>) -> Unit) {
        val activeConversation: Conversation?
        val cancelProfile: LocalInferenceProfileRecorder?
        val deferredToInferenceCompletion: Boolean
        synchronized(lock) {
            check(state != LocalModelState.LOADING && state != LocalModelState.UNLOADING) {
                "Local model lifecycle operation is already in progress."
            }
            activeConversation = inferenceConversation ?: conversation
            cancelProfile = activeInferenceProfile
            deferredToInferenceCompletion = inferenceActive
            if (deferredToInferenceCompletion) activeCancellation?.set(true)
            state = LocalModelState.UNLOADING
            if (deferredToInferenceCompletion) pendingUnload = callback
        }
        if (deferredToInferenceCompletion) {
            val cancelNative = { runCatching { activeConversation?.cancelProcess() } }
            if (cancelProfile == null) cancelNative()
            else cancelProfile.cancel(LocalInferenceCancelOrigin.UNLOAD, cancelNative)
            return
        }
        background.execute { closeResources(callback) }
    }

    private fun finishInference() {
        val shouldUnload = synchronized(lock) {
            inferenceActive = false
            activeInferenceProfile = null
            activeDeliveryLock = null
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
            activeInferenceProfile = null
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
