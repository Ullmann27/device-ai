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
    /** Backend the loaded engine really runs on; null when unknown or not loaded. */
    val activeBackend: LocalModelBackend? get() = null
    /** Native context window (tokens) the loaded engine was configured with; null when unknown or not loaded. */
    val contextTokens: Int? get() = null
    /** Owner-facing sentence when the last load did not start on the requested backend; null otherwise. */
    val backendNotice: String? get() = null
    /** Transport limit, not an exact tokenizer budget. */
    val maxPromptUtf8Bytes: Int? get() = null
    /** Prompt bytes that fit the loaded engine's window after its measured preface; null when unknown. */
    val tokenAwarePromptUtf8Bytes: Int? get() = null
    val toolFreePromptUtf8Bytes: Int? get() = null
    fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) = stream(prompt, callback)
    /**
     * Owner switch „Gespräch im Modell behalten“ (experimental, off by default): a plain text dialogue turn that
     * MAY continue the native conversation kept under the request's key. A runtime that cannot keep a
     * conversation (this default) answers exactly like [streamTextOnly] from the complete prompt and says so in
     * the receipt. A wrapper that gates generations must override this like the other generation entries.
     */
    fun streamDialogue(request: LocalDialogueRequest, callback: LocalInferenceCallback) {
        request.receipt.dispatched(reused = false, reason = LocalDialogueReuse.RUNTIME_WITHOUT_REUSE, tokensBefore = null)
        streamTextOnly(request.freshPrompt, callback)
    }
    /** Ends a kept native dialogue conversation, if this runtime holds one; a no-op otherwise. */
    fun releaseDialogue() {}
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
    /** Evaluated at load time (thermal budget, owner cooling mode); null keeps the LiteRT-LM default (4 threads in 0.18.0). */
    private val cpuThreadCount: () -> Int? = { null },
    /** Read-only load-time capacity choice; the conservative 4K remains the default. */
    private val contextCapacitySelector: (Int) -> Int = LocalNativePromptBudget::contextCapacity,
    /** Owner switch CPU | GPU (experimental, native GPU start NOT_RUN on a device); null keeps CPU only. */
    private val acceleration: LocalModelAccelerationController? = null
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

    @Volatile
    override var activeBackend: LocalModelBackend? = null
        private set

    @Volatile
    override var backendNotice: String? = null
        private set

    override val contextTokens: Int? get() = configuredContextTokens.takeIf { it > 0 && loadedModel != null }

    @Volatile private var configuredContextTokens = 0
    @Volatile private var prefaceTokenCount: Int? = null
    override val maxPromptUtf8Bytes: Int? get() = if (loadedModel == null) null else LocalToolContinuation.MAX_INPUT_BYTES
    override val tokenAwarePromptUtf8Bytes: Int? get() = loadedModel?.let { model ->
        LocalNativePromptBudget.promptByteBudget(configuredContextTokens, prefaceTokenCount,
            LocalModelGenerationPolicy.maxOutputTokens(model.id))
    }

    override val toolFreePromptUtf8Bytes: Int? get() = loadedModel?.let { model ->
        LocalNativePromptBudget.toolFreePromptByteBudget(configuredContextTokens,
            LocalModelGenerationPolicy.maxTextOnlyOutputTokens(model.id, configuredContextTokens))
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    // Only the freshly initialized, never-dispatched chat conversation is reusable.
    // Consumed exactly once; subsequent full prompts always start fresh.
    private var startupConversationUnused = false
    private var conversationContextInvalid = false
    private var inferenceConversation: Conversation? = null
    // Owner switch „Gespräch im Modell behalten“: the idle tool-free conversation of the last completed dialogue
    // turn and its key. Non-null only between two generations; every generation entry takes or releases it first.
    private var keptDialogue: Conversation? = null
    private var keptDialogueKey: String? = null
    private var dialogueEpoch = 0
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
                val conservative = LocalNativePromptBudget.contextCapacity(capabilities.maxContextTokens)
                val requestedContext = runCatching {
                    contextCapacitySelector(capabilities.maxContextTokens)
                }.getOrDefault(conservative)
                val maxSafeContext = if (capabilities.maxContextTokens == 0) conservative
                    else minOf(capabilities.maxContextTokens, 8_192)
                val contextCapacity = requestedContext.takeIf {
                    it in conservative..maxSafeContext
                } ?: conservative
                // The owner's accelerator switch decides here; without it (tests, other hosts) this is CPU only.
                // A failed GPU attempt that could not release its handles keeps them owned (see startEngine)
                // and must not be followed by a second multi-GB engine.
                val attempt = { target: LocalModelBackend ->
                    startEngine(model, file, if (target == LocalModelBackend.GPU) Backend.GPU()
                        else Backend.CPU(threadCount = threads), contextCapacity)
                }
                val started = acceleration?.load(capabilities.supportedBackends,
                    fallbackAllowed = { synchronized(lock) { engine == null && conversation == null } },
                    attempt = attempt)
                    ?: LocalModelAcceleration.Loaded(attempt(LocalModelBackend.CPU), LocalModelBackend.CPU, null)
                synchronized(lock) {
                    engine = started.value.engine
                    conversation = started.value.conversation
                    startupConversationUnused = true
                    conversationContextInvalid = false
                    loadedModel = model
                    activeBackend = started.backend
                    // The CPU thread budget is not applied to a GPU engine.
                    computeThreads = threads.takeIf { started.backend == LocalModelBackend.CPU }
                    backendNotice = started.ownerNotice()
                    configuredContextTokens = contextCapacity
                    prefaceTokenCount = started.value.prefaceTokens
                    state = LocalModelState.READY
                }
                model
            }
            if (result.isFailure) {
                synchronized(lock) { state = LocalModelState.FAILED }
            }
            callback(result)
        }
    }

    private class StartedEngine(val engine: Engine, val conversation: Conversation, val prefaceTokens: Int)

    /**
     * One initialized engine on [backend] with its measured preface and a pristine chat conversation.
     * A failure releases what this attempt created; if that release fails, the handles stay owned by the
     * runtime (FAILED until an explicit unload) exactly as before the accelerator switch existed.
     */
    private fun startEngine(model: ImportedLocalModel, file: File, backend: Backend, contextCapacity: Int): StartedEngine {
        val newEngine = Engine(
            EngineConfig(
                modelPath = file.absolutePath,
                backend = backend,
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
            return StartedEngine(newEngine, newConversation, prefixTokens)
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

    override fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) {
        require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARACTERS &&
            prompt.toByteArray(Charsets.UTF_8).size <= LocalToolContinuation.MAX_INPUT_BYTES)
        val effectivePrompt = LocalQwenPromptPolicy.directUserPrompt(
            loadedModel?.id, prompt, MAX_PROMPT_CHARACTERS)
        // One existing engine, but an isolated text-only native Conversation with zero tools.
        streamMessage(Message.user(Contents.of(effectivePrompt)), effectivePrompt.length, callback, true,
            payloadBytes = effectivePrompt.toByteArray(Charsets.UTF_8).size, textOnlyDialogue = true)
    }

    override fun streamThinking(prompt: String, callback: LocalInferenceCallback) {
        require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARACTERS &&
            prompt.toByteArray(Charsets.UTF_8).size <= LocalToolContinuation.MAX_INPUT_BYTES)
        streamMessage(Message.user(Contents.of(prompt)), prompt.length, callback, true,
            payloadBytes = prompt.toByteArray(Charsets.UTF_8).size)
    }

    override fun streamDialogue(request: LocalDialogueRequest, callback: LocalInferenceCallback) {
        fun requireBounded(prompt: String) = require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARACTERS &&
            prompt.toByteArray(Charsets.UTF_8).size <= LocalToolContinuation.MAX_INPUT_BYTES)
        requireBounded(request.freshPrompt)
        request.followUp?.let(::requireBounded)
        val effectivePrompt = LocalQwenPromptPolicy.directUserPrompt(
            loadedModel?.id, request.freshPrompt, MAX_PROMPT_CHARACTERS)
        // The same isolated tool-free conversation as streamTextOnly; it is only not closed after a completed
        // answer, and a later request with the same key may append its follow-up instead of the full prompt.
        streamMessage(Message.user(Contents.of(effectivePrompt)), effectivePrompt.length, callback, true,
            payloadBytes = effectivePrompt.toByteArray(Charsets.UTF_8).size, textOnlyDialogue = true,
            dialogue = request)
    }

    override fun releaseDialogue() {
        synchronized(lock) {
            // An in-flight dialogue turn is not retained; a key that was released can never be continued.
            dialogueEpoch++
            keptDialogueKey = null
            val stale = keptDialogue ?: return
            // Outside READY the explicit unload (or the next generation entry) owns the handle.
            if (state != LocalModelState.READY) return
            if (runCatching { if (stale.isAlive) stale.close() }.isSuccess) keptDialogue = null
            else state = LocalModelState.FAILED
        }
    }

    /**
     * Under [lock] while READY, before a generation selects its conversation. Returns the kept dialogue
     * conversation and the output budget when [request] may append its follow-up; in every other case a kept
     * conversation is closed first (fail closed), so the native peak is the same as without the switch.
     */
    private fun takeKeptDialogue(
        request: LocalDialogueRequest?, profile: LocalInferenceProfileRecorder
    ): Pair<Conversation, Int>? {
        val kept = keptDialogue
        val followUp = request?.followUp
        var existing: Int? = null
        var output: Int? = null
        val refusal = when {
            request == null || followUp == null -> null
            kept == null || keptDialogueKey != request.conversationKey ||
                !runCatching { kept.isAlive }.getOrDefault(false) -> LocalDialogueReuse.RUNTIME_NO_KEPT_CONVERSATION
            else -> {
                existing = runCatching { kept.getTokenCount() }.getOrNull()
                output = LocalDialogueReuse.outputTokens(followUp.toByteArray(Charsets.UTF_8).size, existing,
                    configuredContextTokens,
                    LocalModelGenerationPolicy.maxTextOnlyOutputTokens(loadedModel?.id, configuredContextTokens))
                if (output == null) LocalDialogueReuse.RUNTIME_CONTEXT_NEAR_LIMIT else null
            }
        }
        val admitted = output
        if (request != null && kept != null && admitted != null) {
            keptDialogue = null
            request.receipt.dispatched(reused = true, reason = null, tokensBefore = existing)
            return kept to admitted
        }
        if (kept != null) {
            try {
                profile.measure(LocalInferenceStage.PREVIOUS_CONVERSATION_RELEASE_ENTER,
                    LocalInferenceStage.PREVIOUS_CONVERSATION_RELEASE_EXIT) {
                    if (kept.isAlive) kept.close()
                }
            } catch (failure: Exception) {
                // The handle stays owned for an explicit unload; no second conversation next to an unreleased one.
                profile.mark(LocalInferenceStage.INVOCATION_THROW)
                state = LocalModelState.FAILED
                throw failure
            }
            keptDialogue = null
        }
        keptDialogueKey = null
        request?.receipt?.dispatched(reused = false, reason = refusal, tokensBefore = existing)
        return null
    }

    /** Keeps the conversation of a completed dialogue turn open under the request's key; false: close it as before. */
    private fun retainDialogue(request: LocalDialogueRequest, finished: Conversation, epoch: Int): Boolean =
        synchronized(lock) {
            if (dialogueEpoch != epoch || pendingUnload != null || state != LocalModelState.GENERATING ||
                keptDialogue != null) return false
            val tokens = runCatching { finished.getTokenCount() }.getOrNull()
            if (!LocalNativePromptBudget.completeContext(tokens, configuredContextTokens)) return false
            keptDialogue = finished
            keptDialogueKey = request.conversationKey
            request.receipt.retained(tokens)
            true
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
        payloadBytes: Int,
        textOnlyDialogue: Boolean = false,
        dialogue: LocalDialogueRequest? = null
    ) {
        val profile = inferenceProfiler.begin(when {
            textOnlyDialogue -> LocalInferenceKind.CHAT
            isolatedThinking -> LocalInferenceKind.ISOLATED_THINKING
            freshPrompt -> LocalInferenceKind.CHAT
            else -> LocalInferenceKind.TOOL_CONTINUATION
        })
        val activeConversation: Conversation
        // Output budget of an appended follow-up; null for every generation that starts from a complete prompt.
        val reuseOutput: Int?
        val dialogueEpochAtStart: Int
        val startNanos = System.nanoTime()
        val inputTokens: Int?
        val generationCancelled = AtomicBoolean(false)
        val nativeStart = ToolExecutionStartLatch()
        val callbackLock = Any()
        synchronized(lock) {
            check(state == LocalModelState.READY) { "A local model is not ready." }
            profile.mark(LocalInferenceStage.CONVERSATION_SELECTION_ENTER)
            val keptForFollowUp = takeKeptDialogue(dialogue, profile)
            reuseOutput = keptForFollowUp?.second
            dialogueEpochAtStart = dialogueEpoch
            activeConversation = keptForFollowUp?.first ?: if (isolatedThinking) {
                profile.measure(LocalInferenceStage.CONVERSATION_CREATE_ENTER,
                    LocalInferenceStage.CONVERSATION_CREATE_EXIT) {
                    checkNotNull(engine).createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(if (textOnlyDialogue) systemPrompt else
                                "You are a data-only reasoning worker for Device AI. " +
                                    "You have no tools and no authority to act."),
                        tools = emptyList(),
                        automaticToolCalling = false,
                        maxOutputToken = if (textOnlyDialogue)
                            LocalModelGenerationPolicy.maxTextOnlyOutputTokens(loadedModel?.id, configuredContextTokens)
                        else LocalModelGenerationPolicy.maxOutputTokens(loadedModel?.id)
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
        val appendedFollowUp = dialogue?.followUp?.takeIf { reuseOutput != null }
        val outgoing = appendedFollowUp?.let { Message.user(Contents.of(it)) } ?: message
        val outgoingCharacters = appendedFollowUp?.length ?: promptCharacters
        val outgoingBytes = appendedFollowUp?.toByteArray(Charsets.UTF_8)?.size ?: payloadBytes
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
            // A completed, uncancelled dialogue turn keeps its conversation; everything else closes it as before.
            val retained = dialogue != null && event is LocalInferenceEvent.Completed &&
                !generationCancelled.get() && retainDialogue(dialogue, activeConversation, dialogueEpochAtStart)
            if (isolatedThinking && !retained) {
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
            // A follow-up was admitted under the lock with the strict byte bound; it is not rendered again.
            val renderedBytes = if (reuseOutput != null) null else runCatching {
                LocalNativePromptBudget.renderedUtf8Estimate(activeConversation.renderMessageIntoString(message))
            }.getOrNull()
            // Worker prefix is in its rendered input.
            val prefix = if (textOnlyDialogue) LocalNativePromptBudget.TOOL_FREE_PREFACE_RESERVE
                else if (isolatedThinking) 0 else prefaceTokenCount
            val requestedOutput = if (textOnlyDialogue)
                LocalModelGenerationPolicy.maxTextOnlyOutputTokens(loadedModel?.id, configuredContextTokens)
                else LocalModelGenerationPolicy.maxOutputTokens(loadedModel?.id)
            val budget = if (reuseOutput != null) LocalNativePromptBudget.Plan(outgoingBytes, reuseOutput, false)
                else LocalNativePromptBudget.plan(payloadBytes, renderedBytes, inputTokens, prefix,
                configuredContextTokens, requestedOutput,
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
                outgoing,
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
                                val remaining = (MAX_RESPONSE_CHARACTERS - responseCharacters[0]).coerceAtLeast(0)
                                val accepted = LocalModelGenerationPolicy.responsePrefix(text.text, remaining)
                                if (accepted.isNotEmpty()) {
                                    responseCharacters[0] += accepted.length
                                    chunks[0]++
                                    if (firstTokenNanos == null) firstTokenNanos = System.nanoTime()
                                    deliverGeneration(LocalInferenceEvent.TextDelta(accepted))
                                }
                                if (text.text.length > remaining) {
                                    if (outputLimitReached.compareAndSet(false, true)) {
                                        terminalOverrideRequested.set(true)
                                        profile.cancel(LocalInferenceCancelOrigin.OUTPUT_LIMIT) {
                                            runCatching { activeConversation.cancelProcess() }
                                        }
                                        finish(LocalInferenceEvent.Failed(
                                            LocalModelGenerationPolicy.OUTPUT_CHARACTER_LIMIT))
                                    }
                                    return
                                }
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
                                val benchmark = runCatching { activeConversation.getBenchmarkInfo() }.getOrNull()
                                val decodedTokens = benchmark?.lastDecodeTokenCount
                                    ?.takeIf { it in 0..configuredContextTokens }
                                if (!LocalNativePromptBudget.completeContext(nativeTokens, configuredContextTokens)) {
                                    if (!isolatedThinking) synchronized(lock) { conversationContextInvalid = true }
                                    finish(LocalInferenceEvent.Failed("LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL"))
                                    return
                                }
                                if (LocalModelGenerationPolicy.outputLimitReached(
                                        decodedTokens, budget.maxOutputTokens, configuredContextTokens)) {
                                    if (!isolatedThinking) synchronized(lock) { conversationContextInvalid = true }
                                    finish(LocalInferenceEvent.Failed(LocalModelGenerationPolicy.OUTPUT_TOKEN_LIMIT))
                                    return
                                }
                                finish(
                                    LocalInferenceEvent.Completed(
                                        LocalInferenceMetrics(
                                            promptCharacters = outgoingCharacters,
                                            responseCharacters = responseCharacters[0],
                                            streamedChunks = chunks[0],
                                            inputTokens = benchmark?.lastPrefillTokenCount
                                                ?.takeIf { it in 0..configuredContextTokens },
                                            outputTokens = decodedTokens,
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
        val oldDialogue = synchronized(lock) { keptDialogue }
        val result = runCatching {
            val (oldConversation, oldInference, oldEngine) = resources
            if (oldInference != null && oldInference !== oldConversation && oldInference.isAlive) {
                oldInference.close()
            }
            if (oldDialogue != null && oldDialogue !== oldConversation && oldDialogue !== oldInference &&
                oldDialogue.isAlive) oldDialogue.close()
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
                keptDialogue = null
                keptDialogueKey = null
                activeInferenceProfile = null
                activeCancellation = null
                activeStart = null
                engine = null
                loadedModel = null
                computeThreads = null
                activeBackend = null
                backendNotice = null
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
        const val MAX_RESPONSE_CHARACTERS = LocalModelGenerationPolicy.MAX_RESPONSE_CHARACTERS
        const val MAX_TOOL_RESPONSES = 4
    }
}
