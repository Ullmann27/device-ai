package at.heinz.deviceai.localmodel

import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.ToolSpec
import at.heinz.deviceai.ToolExecutionStartLatch
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
    /** CPU threads the loaded engine was configured with; null when unknown or not loaded. */
    val computeThreads: Int? get() = null
    /** Transport limit, not an exact tokenizer budget. */
    val maxPromptUtf8Bytes: Int? get() = null
    /** Prompt bytes that fit the loaded engine's window after its measured preface; null when unknown. */
    val tokenAwarePromptUtf8Bytes: Int? get() = null
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
    private val systemPrompt: String = "You are Device AI, an on-device assistant.",
    /** Evaluated at load time (thermal budget, owner cooling mode); null keeps the LiteRT-LM default (all cores). */
    private val cpuThreadCount: () -> Int? = { null }
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

    @Volatile
    override var computeThreads: Int? = null
        private set

    @Volatile private var configuredContextTokens = 0
    @Volatile private var prefaceTokenCount: Int? = null
    override val maxPromptUtf8Bytes: Int? get() = if (loadedModel == null) null else LocalToolContinuation.MAX_INPUT_BYTES
    override val tokenAwarePromptUtf8Bytes: Int? get() = loadedModel?.let { model ->
        LocalNativePromptBudget.promptByteBudget(configuredContextTokens, prefaceTokenCount,
            LocalModelGenerationPolicy.maxOutputTokens(model.id))
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    // Only the freshly initialized, never-dispatched chat conversation is reusable.
    // Consumed exactly once; subsequent full prompts always start fresh.
    private var startupConversationUnused = false
    private var conversationContextInvalid = false
    private var inferenceConversation: Conversation? = null
    private var inferenceActive = false
    private var activeInferenceProfile: LocalInferenceProfileRecorder? = null
    private var pendingUnload: ((Result<Unit>) -> Unit)? = null
    private var activeCancellation: AtomicBoolean? = null
    private var activeStart: ToolExecutionStartLatch? = null

    init { require(systemPrompt.isNotBlank() && systemPrompt.length <= 4_096) }

    override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) {
        val retainedResources = synchronized(lock) {
            check(state == LocalModelState.EMPTY || state == LocalModelState.FAILED) {
                "A local model is already loaded or loading."
            }
            // FAILED can also mean that a conversation could not be released.
            // Do not replace its engine with another multi-GB allocation. Only a
            // successful explicit unload establishes that the old handles are gone.
            val retained = engine != null || conversation != null || inferenceConversation != null ||
                loadedModel != null || inferenceActive
            if (!retained) state = LocalModelState.LOADING
            retained
        }
        if (retainedResources) {
            callback(Result.failure(IllegalStateException("LOCAL_MODEL_UNLOAD_REQUIRED")))
            return
        }
        background.execute {
            val result = runCatching {
                val (model, file) = catalog.sourceForLoad(id, version)
                    ?: error("Imported model is missing or failed its integrity check.")
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
                val threads = runCatching(cpuThreadCount).getOrNull()?.takeIf { it > 0 }
                val contextCapacity = LocalNativePromptBudget.contextCapacity(capabilities.maxContextTokens)
                val newEngine = Engine(
                    EngineConfig(
                        modelPath = file.absolutePath,
                        backend = Backend.CPU(threadCount = threads),
                        maxNumTokens = contextCapacity,
                        cacheDir = cacheDirectory.absolutePath
                    )
                )
                var createdConversation: Conversation? = null
                try {
                    newEngine.initialize()
                    val prefixConversation = newEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(systemPrompt),
                            tools = ToolRegistry.all().map(::registeredToolProvider),
                            automaticToolCalling = false,
                            prefillPrefaceOnInit = true,
                            maxOutputToken = LocalModelGenerationPolicy.maxOutputTokens(model.id)
                        )
                    )
                    createdConversation = prefixConversation
                    val prefixTokens = prefixConversation.getTokenCount()
                    check(prefixTokens in 0..contextCapacity) { "LOCAL_MODEL_PREFACE_COUNT_UNVERIFIED" }
                    prefixConversation.close()
                    createdConversation = null
                    val newConversation = newEngine.createConversation(ConversationConfig(
                        systemInstruction = Contents.of(systemPrompt),
                        tools = ToolRegistry.all().map(::registeredToolProvider),
                        automaticToolCalling = false,
                        maxOutputToken = LocalModelGenerationPolicy.maxOutputTokens(model.id)
                    ))
                    createdConversation = newConversation
                    synchronized(lock) {
                        engine = newEngine
                        conversation = newConversation
                        startupConversationUnused = true
                        conversationContextInvalid = false
                        loadedModel = model
                        computeThreads = threads
                        configuredContextTokens = contextCapacity
                        prefaceTokenCount = prefixTokens
                        state = LocalModelState.READY
                    }
                } catch (failure: Throwable) {
                    val releasedConversation = runCatching {
                        createdConversation?.takeIf { it.isAlive }?.close()
                    }
                    val releasedEngine = if (releasedConversation.isSuccess) runCatching {
                        if (newEngine.isInitialized()) newEngine.close()
                    } else Result.failure(releasedConversation.exceptionOrNull()!!)
                    if (releasedConversation.isFailure || releasedEngine.isFailure) synchronized(lock) {
                        engine = newEngine
                        conversation = createdConversation
                        loadedModel = model
                    }
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
        if (synchronized(lock) { conversationContextInvalid }) {
            deliver(callback, LocalInferenceEvent.Failed("LOCAL_TOOL_CONTEXT_EXCEEDS_BUDGET"))
            return
        }
        val payload = LocalToolContinuation.text(responses) ?: run {
            deliver(callback, LocalInferenceEvent.Failed("LOCAL_TOOL_CONTEXT_EXCEEDS_BUDGET"))
            return
        }
        val contents: List<Content> = responses.map { Content.ToolResponse(it.toolName, it.response) }
        streamMessage(Message.tool(Contents.of(contents)), payload.length, callback, false,
            payloadBytes = payload.toByteArray(Charsets.UTF_8).size)
    }

    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
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
        val nativeStart = ToolExecutionStartLatch()
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
                    // The initial conversation was created during load and has no
                    // native messages. Reusing it ONCE avoids closing and creating
                    // an identical Conversation for the first full chat prompt.
                    // Do not change the per-turn fresh history isolation policy.
                    if (!freshPrompt && startupConversationUnused)
                        error("No active tool conversation to continue.")
                    check(freshPrompt || !conversationContextInvalid) { "LOCAL_TOOL_CONTEXT_EXCEEDS_BUDGET" }
                    val reusePristine = freshPrompt && startupConversationUnused &&
                        conversation?.isAlive == true
                    startupConversationUnused = false
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
                    }, reusePristineOnFirstPrompt = reusePristine).also {
                        conversation = it
                        if (freshPrompt) conversationContextInvalid = false
                    }
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
            activeStart = nativeStart
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
        val deliveries = LocalInferenceDelivery()
        fun drainDeliveries() {
            if (!Thread.holdsLock(lock) && !Thread.holdsLock(callbackLock)) deliveries.drain()
        }
        fun deliverGeneration(event: LocalInferenceEvent) {
            deliveries.enqueue { if (!generationCancelled.get()) deliver(callback,event) }
        }

        fun deliverTerminal(event: LocalInferenceEvent) {
            deliveries.enqueue {
            val actual = if(generationCancelled.get()) LocalInferenceEvent.Cancelled else event
            val terminal = when (actual) {
                is LocalInferenceEvent.Completed -> LocalInferenceTerminal.COMPLETED
                LocalInferenceEvent.Cancelled -> LocalInferenceTerminal.CANCELLED
                else -> LocalInferenceTerminal.FAILED
            }
            profile.runtimeTerminal(terminal)
            // Keep the generation owned until preceding queued text is drained.
            // A new engine turn must not replace its cancellation lease early.
            finishInference()
            profile.measure(LocalInferenceStage.RUNTIME_TERMINAL_DELIVERY_ENTER,
                LocalInferenceStage.RUNTIME_TERMINAL_DELIVERY_EXIT) {
                deliver(callback, actual)
            }
            }
            drainDeliveries()
        }

        fun finish(event: LocalInferenceEvent) {
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
                    if (closed.isFailure) {
                        deliverTerminal(LocalInferenceEvent.Failed(
                            "Isolated conversation could not be released safely."))
                    } else deliverTerminal(
                        if (generationCancelled.get()) LocalInferenceEvent.Cancelled else event)
                }
            } else {
                synchronized(lock) {
                    if (inferenceConversation === activeConversation) inferenceConversation = null
                }
                deliverTerminal(
                    if (generationCancelled.get()) LocalInferenceEvent.Cancelled else event)
            }
        }

        try {
            val renderedBytes = runCatching {
                LocalNativePromptBudget.renderedUtf8Estimate(activeConversation.renderMessageIntoString(message))
            }.getOrNull()
            // Worker prefix is in its rendered input.
            val prefix = if (isolatedThinking) 0 else prefaceTokenCount
            val budget = LocalNativePromptBudget.plan(payloadBytes, renderedBytes, inputTokens, prefix,
                configuredContextTokens, LocalModelGenerationPolicy.maxOutputTokens(loadedModel?.id),
                fresh = freshPrompt || isolatedThinking)
            if (budget == null) {
                finish(LocalInferenceEvent.Failed("LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL"))
                return
            }
            // Atomic commitment, never a monitor held across JNI/caller callbacks.
            // Already committed work may continue; cancellation is reissued below.
            if (generationCancelled.get() || !nativeStart.commitIfPending()) {
                finish(LocalInferenceEvent.Cancelled)
                return
            }
            profile.mark(LocalInferenceStage.NATIVE_DISPATCH_ENTER)
            activeConversation.sendMessageAsync(
                message,
                object : MessageCallback {
                    override fun onMessage(message: com.google.ai.edge.litertlm.Message) {
                        try {
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
                                deliverGeneration(event)
                            }
                            message.contents.contents.filterIsInstance<Content.Text>().forEach { text ->
                                // Native callbacks are deltas; standalone spaces/newlines
                                // must survive or prose and generated code are corrupted.
                                if (text.text.isEmpty() || finished.get() || terminalOverrideRequested.get() || generationCancelled.get())
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
                                deliverGeneration(LocalInferenceEvent.TextDelta(text.text))
                            }
                        }
                        } finally { drainDeliveries() }
                    }

                    override fun onDone() {
                        profile.nativeTerminal(LocalInferenceTerminal.COMPLETED)
                        try {
                            synchronized(callbackLock) {
                                if (finished.get() || terminalOverrideRequested.get()) return
                                val elapsed = (System.nanoTime() - startNanos).coerceAtLeast(0) / 1_000_000
                                val nativeTokens = runCatching { activeConversation.getTokenCount() }.getOrNull()
                                if (!LocalNativePromptBudget.completeContext(nativeTokens, configuredContextTokens)) {
                                    if (!isolatedThinking) synchronized(lock) { conversationContextInvalid = true }
                                    finish(LocalInferenceEvent.Failed("LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL"))
                                    return
                                }
                                finish(
                                    LocalInferenceEvent.Completed(
                                        LocalInferenceMetrics(
                                            promptCharacters = promptCharacters,
                                            responseCharacters = responseCharacters[0],
                                            streamedChunks = chunks[0],
                                            // KV includes newly prefilled input.
                                            inputTokens = null,
                                            outputTokens = null,
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
                            drainDeliveries()
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        profile.nativeTerminal(if (throwable is CancellationException)
                            LocalInferenceTerminal.CANCELLED else LocalInferenceTerminal.FAILED)
                        try {
                            synchronized(callbackLock) {
                                if (finished.get() || terminalOverrideRequested.get()) return
                                val contextFailure = LocalNativePromptBudget.contextFailure(throwable.message.orEmpty())
                                if (contextFailure && !isolatedThinking) synchronized(lock) { conversationContextInvalid = true }
                                finish(
                                    if (throwable is CancellationException) LocalInferenceEvent.Cancelled
                                    else LocalInferenceEvent.Failed(if (contextFailure) "LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL"
                                        else throwable.message.orEmpty().take(512))
                                )
                            }
                        } finally {
                            profile.mark(LocalInferenceStage.NATIVE_TERMINAL_CALLBACK_EXIT)
                            drainDeliveries()
                        }
                    }
                },
                maxOutputToken = budget.maxOutputTokens
            )
            profile.mark(LocalInferenceStage.NATIVE_DISPATCH_RETURN)
            if (generationCancelled.get()) runCatching { activeConversation.cancelProcess() }
        } catch (failure: Throwable) {
            profile.mark(LocalInferenceStage.NATIVE_DISPATCH_THROW)
            synchronized(callbackLock) {
                if (!finished.get() && !terminalOverrideRequested.get()) {
                    val contextFailure = LocalNativePromptBudget.contextFailure(failure.message.orEmpty())
                    if (contextFailure && !isolatedThinking) synchronized(lock) { conversationContextInvalid = true }
                    finish(LocalInferenceEvent.Failed(if (contextFailure) "LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL"
                        else failure.message.orEmpty().take(512)))
                }
            }
        } finally {
            drainDeliveries()
        }
    }

    override fun cancel() {
        val active = synchronized(lock) {
            activeCancellation?.set(true)
            activeStart?.revokeIfPending()
            if (state != LocalModelState.GENERATING && state != LocalModelState.UNLOADING) null
            else (inferenceConversation ?: conversation) to activeInferenceProfile
        } ?: return
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
            activeCancellation?.set(true)
            activeStart?.revokeIfPending()
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
            activeStart = null
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
            // Retain ownership until every native close succeeds. A failed close
            // must leave handles available for a later explicit unload attempt.
            Triple(conversation, inferenceConversation, engine)
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
            if (result.isSuccess) {
                conversation = null
                startupConversationUnused = false
                conversationContextInvalid = false
                inferenceConversation = null
                activeInferenceProfile = null
                activeCancellation = null
                activeStart = null
                engine = null
                loadedModel = null
                computeThreads = null
                configuredContextTokens = 0
                prefaceTokenCount = null
            }
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
