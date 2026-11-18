package at.heinz.deviceai.localmodel

import at.heinz.deviceai.ToolProposal
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.core.LogLevel
import com.anthropic.core.Timeout
import com.anthropic.core.http.StreamResponse
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.helpers.BetaMessageAccumulator
import com.anthropic.models.ErrorType
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral
import com.anthropic.models.beta.messages.BetaContentBlock
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaMessageParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTextBlockParam
import com.anthropic.models.beta.messages.BetaTool
import com.anthropic.models.beta.messages.BetaToolChoiceAuto
import com.anthropic.models.beta.messages.BetaToolResultBlockParam
import com.anthropic.models.beta.messages.MessageCreateParams
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Claude as an optional brain behind the same [LocalModelRuntime] interface the on-device model uses, so every
 * existing caller (chat turn, tool continuation, isolated reasoning pass, STOP) works unchanged. It uses the
 * official Anthropic Java SDK with the owner's own API key and streams every answer.
 *
 * What this class guarantees on its own, whatever a caller does:
 *  - no request without the stored consent and a stored key; no request once the owner's monthly limit is reached;
 *  - data the app marks as device-local only is refused before any network use ([ClaudeLocalOnlyGuard]);
 *  - a tool call from Claude is only ever a [LocalInferenceEvent.ToolProposal] (or a rejection): nothing is
 *    executed here, so validation, ToolGate and the owner's approval stay in force;
 *  - failures are stable codes of [ClaudeBrainFailure]; the API key and upstream error texts never leave this class;
 *  - nothing falls back to another provider or to the local model.
 *
 * There is no local computation: the thermal guard and the memory guard of the on-device model are not applied.
 * [cancel] ends the turn immediately for the caller and closes the HTTP stream.
 */
class ClaudeModelRuntime internal constructor(
    private val settings: () -> ClaudeBrainSettings,
    private val apiKey: () -> OwnerSecret?,
    private val ledger: ClaudeUsageLedger,
    private val endpoint: Endpoint = Endpoint(),
    private val tools: () -> List<ClaudeToolCatalog.Definition> = { ClaudeToolCatalog.definitions() },
    private val nanoTime: () -> Long = System::nanoTime
) : LocalModelRuntime {

    /**
     * Production uses the SDK's own base URL (api.anthropic.com); [baseUrl] exists for the local fake endpoint of
     * the JVM tests only. One automatic SDK retry for a transient 429/5xx/connection failure.
     */
    data class Endpoint(
        val baseUrl: String? = null,
        val maxRetries: Int = 1,
        val connectTimeout: Duration = Duration.ofSeconds(ClaudeGenerationPolicy.CONNECT_TIMEOUT_SECONDS),
        val readTimeout: Duration = Duration.ofSeconds(ClaudeGenerationPolicy.READ_TIMEOUT_SECONDS),
        val requestTimeout: Duration = Duration.ofSeconds(ClaudeGenerationPolicy.REQUEST_TIMEOUT_SECONDS)
    ) {
        init { require(maxRetries in 0..3) }
    }

    /** Result of the owner's "Verbindung testen". */
    sealed interface Probe {
        data class Reachable(val servedModel: String, val promptTokens: Long, val outputTokens: Long) : Probe
        data class Failed(val problem: String) : Probe
    }

    private enum class Kind(val work: Boolean, val withTools: Boolean, val isolatedWorker: Boolean) {
        TOOLS(true, true, false), DIALOGUE(false, false, false), WORKER(true, false, true), PROBE(false, false, false)
    }

    /** One request/response exchange with the API; append-only, so replayed thinking blocks stay valid. */
    private class Conversation(
        val model: ClaudeModel,
        val effort: ClaudeEffort,
        val maxTokens: Long,
        val system: List<BetaTextBlockParam>,
        val tools: List<BetaTool>,
        val messages: List<BetaMessageParam>,
        /** tool_use blocks of the last assistant turn that still need a tool_result: id to tool name. */
        val pendingToolUses: List<Pair<String, String>>,
        val rounds: Int,
        val approximateCharacters: Int
    )

    private class Run(val kind: Kind, val callback: LocalInferenceCallback, val promptCharacters: Int,
                      val startNanos: Long) {
        val cancelled = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        @Volatile var terminalDelivered = false
        @Volatile var stream: StreamResponse<BetaRawMessageStreamEvent>? = null
        val delivery = LocalInferenceDelivery()
        val responseCharacters = AtomicInteger(0)
        var chunks = 0
        var firstTextNanos: Long? = null
    }

    private val lock = Any()
    private val clientLock = Any()
    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "device-ai-claude").apply { isDaemon = true }
    }
    private var cachedClient: Pair<OwnerSecret, AnthropicClient>? = null
    private var activeRun: Run? = null
    private var toolConversation: Conversation? = null
    /** Set when the API rejected the server-side fallback beta for this account; then it is no longer sent. */
    @Volatile private var fallbackRejected = false

    @Volatile
    override var state: LocalModelState = LocalModelState.READY
        private set

    /**
     * A descriptor of the selected cloud model in the shape callers expect. It is not an imported file: the
     * hash identifies the model id only, and the CPU backend entry merely satisfies the descriptor's invariant.
     */
    override val loadedModel: ImportedLocalModel get() = descriptor(settings().model)

    /** Same transport bound as the on-device runtime; Claude's own window is far larger than any app prompt. */
    override val maxPromptUtf8Bytes: Int get() = LocalToolContinuation.MAX_INPUT_BYTES
    override val tokenAwarePromptUtf8Bytes: Int get() = LocalToolContinuation.MAX_INPUT_BYTES
    override val toolFreePromptUtf8Bytes: Int get() = LocalToolContinuation.MAX_INPUT_BYTES

    /** Nothing is loaded for a cloud model; the on-device model's lifecycle belongs to its own runtime. */
    override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
        callback(Result.failure(IllegalStateException(BRAIN_ACTIVE)))

    override fun unload(callback: (Result<Unit>) -> Unit) {
        cancel()
        callback(Result.success(Unit))
    }

    override fun stream(prompt: String, callback: LocalInferenceCallback) =
        startPrompt(Kind.TOOLS, prompt, callback)

    override fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) =
        startPrompt(Kind.DIALOGUE, prompt, callback)

    override fun streamThinking(prompt: String, callback: LocalInferenceCallback) =
        startPrompt(Kind.WORKER, prompt, callback)

    override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) {
        val payload = LocalToolContinuation.text(responses)
        val run = begin(Kind.TOOLS, callback, payload?.length ?: 0)
        if (payload == null) return finish(run, LocalInferenceEvent.Failed("LOCAL_TOOL_CONTEXT_EXCEEDS_BUDGET"))
        ClaudeLocalOnlyGuard.toolResponseRefusal(responses)?.let { return finish(run, LocalInferenceEvent.Failed(it)) }
        val previous = synchronized(lock) { toolConversation }
        if (previous == null || previous.pendingToolUses.isEmpty() ||
            previous.rounds >= ClaudeGenerationPolicy.MAX_TOOL_ROUNDS)
            return finish(run, LocalInferenceEvent.Failed(ClaudeBrainFailure.NO_TOOL_CONVERSATION))
        preflight(previous.model)?.let { return finish(run, LocalInferenceEvent.Failed(it)) }
        val next = Conversation(previous.model, previous.effort, previous.maxTokens, previous.system, previous.tools,
            previous.messages + toolResultMessage(previous.pendingToolUses, responses), emptyList(),
            previous.rounds + 1, previous.approximateCharacters + payload.length)
        // The continuation is committed only when its answer arrived; a failed one ends this tool conversation.
        synchronized(lock) { toolConversation = null }
        workers.execute { exchange(run, next) }
    }

    /**
     * STOP. The caller receives Cancelled at once and the runtime is READY again; the HTTP stream is closed, which
     * cancels the underlying call. If the response has not started yet, it is closed the moment it arrives.
     */
    override fun cancel() {
        val run = synchronized(lock) { activeRun } ?: return
        if (!run.cancelled.compareAndSet(false, true)) return
        runCatching { run.stream?.close() }
        finish(run, LocalInferenceEvent.Cancelled)
    }

    /** The owner deleted or replaced the key: drop the client that holds the old one. */
    fun forgetCredentials() {
        cancel()
        synchronized(clientLock) {
            cachedClient?.second?.let { runCatching { it.close() } }
            cachedClient = null
        }
        synchronized(lock) { toolConversation = null }
    }

    /**
     * "Verbindung testen": one tiny real request, started only by the owner's tap. It sends a fixed sentence and
     * nothing from the device. It needs a stored key but not the data-transfer consent, and it counts toward the
     * monthly estimate like every other request.
     */
    fun probe(result: (Probe) -> Unit) {
        val model = settings().model
        val run = try {
            begin(Kind.PROBE, LocalInferenceCallback { event ->
                when (event) {
                    is LocalInferenceEvent.Completed -> result(Probe.Reachable(lastServedModel ?: model.id,
                        (event.metrics.inputTokens ?: 0).toLong(), (event.metrics.outputTokens ?: 0).toLong()))
                    is LocalInferenceEvent.Failed ->
                        // Reaching the output limit still proves that key, model and connection work.
                        if (LocalModelGenerationPolicy.isOutputLimit(event.message))
                            result(Probe.Reachable(lastServedModel ?: model.id, 0, 0))
                        else result(Probe.Failed(event.message))
                    LocalInferenceEvent.Cancelled -> result(Probe.Failed(CANCELLED))
                    else -> Unit
                }
            }, PROBE_TEXT.length)
        } catch (_: IllegalStateException) {
            return result(Probe.Failed(BUSY))
        }
        preflight(model, requireConsent = false)?.let { return finish(run, LocalInferenceEvent.Failed(it)) }
        workers.execute {
            exchange(run, Conversation(model, ClaudeEffort.LOW, ClaudeGenerationPolicy.PROBE_MAX_TOKENS,
                emptyList(), emptyList(), listOf(BetaMessageParam.ofUser(PROBE_TEXT)), emptyList(), 0,
                PROBE_TEXT.length))
        }
    }

    @Volatile private var lastServedModel: String? = null

    private fun startPrompt(kind: Kind, prompt: String, callback: LocalInferenceCallback) {
        require(prompt.isNotBlank() && prompt.length <= ClaudeGenerationPolicy.MAX_PROMPT_CHARACTERS &&
            prompt.toByteArray(Charsets.UTF_8).size <= LocalToolContinuation.MAX_INPUT_BYTES)
        val run = begin(kind, callback, prompt.length)
        // A new full prompt always starts a new tool conversation, like the on-device runtime.
        if (kind == Kind.TOOLS) synchronized(lock) { toolConversation = null }
        ClaudeLocalOnlyGuard.promptRefusal(prompt)?.let { return finish(run, LocalInferenceEvent.Failed(it)) }
        val current = settings()
        preflight(current.model)?.let { return finish(run, LocalInferenceEvent.Failed(it)) }
        val frame = ClaudePromptFrame.frame(prompt, kind.isolatedWorker)
        val declared = if (kind.withTools) tools() else emptyList()
        val conversation = Conversation(
            model = current.model,
            effort = current.effortFor(kind.work),
            maxTokens = if (kind.work) ClaudeGenerationPolicy.WORK_MAX_TOKENS else ClaudeGenerationPolicy.DIALOGUE_MAX_TOKENS,
            system = systemBlocks(frame.systemBlocks),
            tools = declared.map(::toolParam),
            messages = listOf(BetaMessageParam.ofUser(frame.user)),
            pendingToolUses = emptyList(),
            rounds = 0,
            approximateCharacters = prompt.length + ClaudePromptFrame.RUNTIME_PREFACE.length +
                declared.sumOf { definition -> definition.description.length + definition.arguments.sumOf { it.description.length + 40 } })
        workers.execute { exchange(run, conversation) }
    }

    /** Checks that need no network. Returns a problem code or null. */
    private fun preflight(model: ClaudeModel, requireConsent: Boolean = true): String? {
        val current = settings()
        return when {
            requireConsent && !current.consentGiven -> ClaudeBrainFailure.NO_CONSENT
            apiKey() == null -> ClaudeBrainFailure.NO_API_KEY
            ledger.limitReached(current.monthlyLimitCents) -> ClaudeBrainFailure.SPEND_LIMIT_REACHED
            model != current.model -> LocalTurnModelBinding.CHANGED
            else -> null
        }
    }

    private fun begin(kind: Kind, callback: LocalInferenceCallback, promptCharacters: Int): Run =
        synchronized(lock) {
            check(state == LocalModelState.READY && activeRun == null) { "A local model is not ready." }
            Run(kind, callback, promptCharacters, nanoTime()).also {
                activeRun = it
                state = LocalModelState.GENERATING
            }
        }

    /** Exactly one terminal event per run; the runtime is READY before the caller sees it. */
    private fun finish(run: Run, event: LocalInferenceEvent) {
        if (!run.finished.compareAndSet(false, true)) return
        run.delivery.enqueue {
            val actual = if (run.cancelled.get()) LocalInferenceEvent.Cancelled else event
            synchronized(lock) {
                if (activeRun === run) {
                    activeRun = null
                    state = LocalModelState.READY
                }
            }
            run.terminalDelivered = true
            deliver(run.callback, actual)
        }
        run.delivery.drain()
    }

    private fun emit(run: Run, event: LocalInferenceEvent) {
        if (run.finished.get()) return
        run.delivery.enqueue {
            if (!run.cancelled.get() && !run.terminalDelivered) deliver(run.callback, event)
        }
        run.delivery.drain()
    }

    private fun deliver(callback: LocalInferenceCallback, event: LocalInferenceEvent) {
        runCatching { callback.onEvent(event) }
    }

    /**
     * Sends [conversation] and maps the streamed answer to runtime events. Runs on a worker thread. The usage of
     * the attempt is booked before the caller sees the terminal event.
     */
    private fun exchange(run: Run, conversation: Conversation) {
        val attempt = Attempt()
        val terminal = outcome(run, conversation, attempt, allowFallbackDowngrade = true)
        run.stream = null
        // A response that started but never reported its final usage is still billed for what it produced.
        val seen = attempt.promptUsage
        if (!attempt.settled && seen != null) ledger.record(conversation.model.id, conversation.model.id,
            seen.copy(outputTokens = (run.responseCharacters.get() + 1L) / 2), estimated = true)
        terminal?.let { finish(run, it) }
    }

    private class Attempt {
        var streamOpened = false
        var promptUsage: ClaudeTokenUsage? = null
        /** The final usage of this attempt was booked (or the attempt was booked as an estimate). */
        var settled = false
    }

    /** The terminal event of one attempt, or null when the run already ended (STOP, character limit). */
    private fun outcome(run: Run, conversation: Conversation, attempt: Attempt,
                        allowFallbackDowngrade: Boolean): LocalInferenceEvent? {
        try {
            val key = apiKey() ?: return LocalInferenceEvent.Failed(ClaudeBrainFailure.NO_API_KEY)
            if (run.cancelled.get()) return LocalInferenceEvent.Cancelled
            val withFallback = conversation.model.serverSideFallback && !fallbackRejected && run.kind != Kind.PROBE
            val opened = client(key).beta().messages().createStreaming(request(conversation, withFallback))
            run.stream = opened
            attempt.streamOpened = true
            return opened.use { stream ->
                if (run.cancelled.get()) {
                    // STOP arrived before the response: the request may still be billed, so it is estimated.
                    ledger.record(conversation.model.id, conversation.model.id,
                        ClaudeTokenUsage((conversation.approximateCharacters + 2L) / 3, 0), estimated = true)
                    attempt.settled = true
                    return@use null
                }
                val accumulator = BetaMessageAccumulator.create()
                var stopped = false
                for (event in stream) {
                    accumulator.accumulate(event)
                    event.messageStart().ifPresent { start ->
                        val usage = start.message().usage()
                        attempt.promptUsage = ClaudeTokenUsage(usage.inputTokens(), 0,
                            usage.cacheReadInputTokens().orElse(0L), usage.cacheCreationInputTokens().orElse(0L))
                    }
                    event.contentBlockDelta().ifPresent { delta ->
                        delta.delta().text().ifPresent { text -> acceptText(run, text.text()) }
                    }
                    if (event.isMessageStop()) stopped = true
                    if (run.cancelled.get() || run.finished.get()) break
                }
                if (run.cancelled.get() || run.finished.get()) return@use null
                if (!stopped) return@use LocalInferenceEvent.Failed(ClaudeBrainFailure.STREAM_INTERRUPTED)
                val message = accumulator.message()
                recordUsage(conversation.model, message)
                attempt.settled = true
                complete(run, conversation, message)
            }
        } catch (failure: RateLimitException) {
            return LocalInferenceEvent.Failed(ClaudeBrainFailure.RATE_LIMITED)
        } catch (failure: UnauthorizedException) {
            return LocalInferenceEvent.Failed(ClaudeBrainFailure.INVALID_API_KEY)
        } catch (failure: PermissionDeniedException) {
            return LocalInferenceEvent.Failed(ClaudeBrainFailure.PERMISSION_DENIED)
        } catch (failure: NotFoundException) {
            return LocalInferenceEvent.Failed(ClaudeBrainFailure.MODEL_UNAVAILABLE)
        } catch (failure: BadRequestException) {
            val detail = errorMessage(failure)
            if (!attempt.streamOpened && allowFallbackDowngrade && conversation.model.serverSideFallback &&
                !fallbackRejected && (detail.contains("anthropic-beta") || detail.contains("fallbacks"))) {
                // The account does not have the server-side fallback beta: send the same request once without it.
                fallbackRejected = true
                return outcome(run, conversation, attempt, allowFallbackDowngrade = false)
            }
            return LocalInferenceEvent.Failed(badRequestProblem(failure, detail))
        } catch (failure: InternalServerException) {
            return LocalInferenceEvent.Failed(ClaudeBrainFailure.OVERLOADED)
        } catch (failure: AnthropicServiceException) {
            // Any other non-2xx status and error events inside a stream.
            return LocalInferenceEvent.Failed(serviceProblem(failure, attempt.streamOpened))
        } catch (failure: AnthropicIoException) {
            return LocalInferenceEvent.Failed(connectionProblem(failure, attempt.streamOpened))
        } catch (failure: AnthropicException) {
            return LocalInferenceEvent.Failed(ClaudeBrainFailure.UNEXPECTED_RESPONSE)
        } catch (failure: Exception) {
            return LocalInferenceEvent.Failed(if (attempt.streamOpened) ClaudeBrainFailure.STREAM_INTERRUPTED
                else ClaudeBrainFailure.UNEXPECTED_RESPONSE)
        }
    }

    /** Forwards streamed text; ends the run when the response reaches the app's character limit. */
    private fun acceptText(run: Run, text: String) {
        if (text.isEmpty() || run.finished.get() || run.cancelled.get()) return
        val remaining = (LocalModelGenerationPolicy.MAX_RESPONSE_CHARACTERS - run.responseCharacters.get()).coerceAtLeast(0)
        val accepted = LocalModelGenerationPolicy.responsePrefix(text, remaining)
        if (accepted.isNotEmpty()) {
            run.responseCharacters.addAndGet(accepted.length)
            run.chunks++
            if (run.firstTextNanos == null) run.firstTextNanos = nanoTime()
            emit(run, LocalInferenceEvent.TextDelta(accepted))
        }
        if (text.length > remaining) {
            runCatching { run.stream?.close() }
            finish(run, LocalInferenceEvent.Failed(LocalModelGenerationPolicy.OUTPUT_CHARACTER_LIMIT))
        }
    }

    /** The stop reason decides before any content is used. */
    private fun complete(run: Run, conversation: Conversation, message: BetaMessage): LocalInferenceEvent {
        lastServedModel = message.model().asString()
        return when (message.stopReason().orElse(null)) {
            BetaStopReason.END_TURN, BetaStopReason.STOP_SEQUENCE -> completed(run, message)
            BetaStopReason.TOOL_USE -> proposeTools(run, conversation, message)
            // Thinking and answer share the limit; a cut-off tool call is never proposed.
            BetaStopReason.MAX_TOKENS -> LocalInferenceEvent.Failed(LocalModelGenerationPolicy.OUTPUT_TOKEN_LIMIT)
            // Text that was streamed before a refusal is not an answer; callers discard it.
            BetaStopReason.REFUSAL -> LocalInferenceEvent.Failed(ClaudeBrainFailure.REFUSAL)
            BetaStopReason.MODEL_CONTEXT_WINDOW_EXCEEDED -> LocalInferenceEvent.Failed(ClaudeBrainFailure.CONTEXT_TOO_LONG)
            // pause_turn belongs to server-side tools, which this runtime never declares.
            else -> LocalInferenceEvent.Failed(ClaudeBrainFailure.UNEXPECTED_RESPONSE)
        }
    }

    /** Emits the proposal (or its rejection) and returns the turn's terminal event. */
    private fun proposeTools(run: Run, conversation: Conversation, message: BetaMessage): LocalInferenceEvent {
        val calls = message.content().mapNotNull { it.toolUse().orElse(null) }
        if (!run.kind.withTools || calls.isEmpty())
            return LocalInferenceEvent.Failed(ClaudeBrainFailure.UNEXPECTED_RESPONSE)
        if (calls.size > 1) return LocalInferenceEvent.Failed(
            "More than one tool proposal occurred in a single model turn; none are authorized.")
        val call = calls.single()
        val arguments = toArguments(call._input())
        val rejection = when {
            call.name() in ClaudeLocalOnlyGuard.LOCAL_ONLY_TOOLS ->
                "Dieses Werkzeug steht mit Claude als Gehirn nicht zur Verfügung; seine Daten bleiben auf dem Gerät."
            arguments == null -> "Tool arguments must be a JSON object."
            else -> ToolProposal(call.name(), arguments).validate()
        }
        // Remember the assistant turn first: a rejection is answered through respondToTools as well.
        synchronized(lock) {
            if (!run.cancelled.get() && !run.finished.get()) toolConversation = Conversation(
                conversation.model, conversation.effort, conversation.maxTokens, conversation.system,
                conversation.tools, conversation.messages + assistantTurn(message),
                listOf(call.id() to call.name()), conversation.rounds, conversation.approximateCharacters)
        }
        emit(run, if (rejection == null) LocalInferenceEvent.ToolProposal(ToolProposal(call.name(), arguments!!))
            else LocalInferenceEvent.ToolProposalRejected(call.name(), rejection, arguments ?: emptyMap()))
        return completed(run, message)
    }

    private fun completed(run: Run, message: BetaMessage): LocalInferenceEvent.Completed {
        val usage = message.usage()
        val promptTokens = usage.inputTokens() + usage.cacheReadInputTokens().orElse(0L) +
            usage.cacheCreationInputTokens().orElse(0L)
        return LocalInferenceEvent.Completed(LocalInferenceMetrics(
            promptCharacters = run.promptCharacters,
            responseCharacters = run.responseCharacters.get(),
            streamedChunks = run.chunks,
            inputTokens = promptTokens.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            outputTokens = usage.outputTokens().coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            firstTokenLatencyMillis = run.firstTextNanos?.let { (it - run.startNanos).coerceAtLeast(0) / 1_000_000 },
            totalLatencyMillis = (nanoTime() - run.startNanos).coerceAtLeast(0) / 1_000_000))
    }

    /** Every attempt is counted for the model that ran it (a refusal fallback is a second attempt). */
    private fun recordUsage(requested: ClaudeModel, message: BetaMessage) {
        val usage = message.usage()
        val attempts = usage.iterations().orElse(emptyList())
        if (attempts.isEmpty()) {
            ledger.record(message.model().asString(), requested.id, ClaudeTokenUsage(usage.inputTokens(),
                usage.outputTokens(), usage.cacheReadInputTokens().orElse(0L),
                usage.cacheCreationInputTokens().orElse(0L)))
            return
        }
        attempts.forEachIndexed { index, attempt ->
            ledger.record(attempt.model().map { it.asString() }.orElse(message.model().asString()), requested.id,
                ClaudeTokenUsage(attempt.inputTokens(), attempt.outputTokens(), attempt.cacheReadInputTokens(),
                    attempt.cacheCreationInputTokens()), countsAsRequest = index == 0)
        }
    }

    private fun request(conversation: Conversation, withFallback: Boolean): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(conversation.model.id)
            .maxTokens(conversation.maxTokens)
            // Thinking is adaptive on these models and is left to the API; effort is the only depth control.
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.of(conversation.effort.wire)).build())
        if (conversation.system.isNotEmpty()) builder.systemOfBetaTextBlockParams(conversation.system)
        if (conversation.tools.isNotEmpty()) {
            conversation.tools.forEach { builder.addTool(it) }
            // Never forced: "auto" with at most one call per turn, as the app accepts one proposal per turn.
            builder.toolChoice(BetaToolChoiceAuto.builder().disableParallelToolUse(true).build())
        }
        conversation.messages.forEach { builder.addMessage(it) }
        if (withFallback) {
            // A declined request is retried server-side on the model Anthropic recommends for that decline.
            builder.addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
            builder.fallbacksDefault()
        }
        return builder.build()
    }

    /**
     * Only constant text reaches `system` ([ClaudePromptFrame]) and the tool list is rendered in a fixed order, so
     * tools plus system are byte-stable; the cache breakpoint sits on the last system block.
     */
    private fun systemBlocks(blocks: List<String>): List<BetaTextBlockParam> = blocks.mapIndexed { index, text ->
        val block = BetaTextBlockParam.builder().text(text)
        if (index == blocks.lastIndex) block.cacheControl(BetaCacheControlEphemeral.builder().build())
        block.build()
    }

    private fun toolParam(definition: ClaudeToolCatalog.Definition): BetaTool {
        val properties = BetaTool.InputSchema.Properties.builder()
        definition.arguments.forEach { argument ->
            val schema = linkedMapOf<String, Any>("type" to argument.jsonType, "description" to argument.description)
            if (argument.jsonType == "array") schema["items"] = mapOf("type" to "string")
            properties.putAdditionalProperty(argument.name, JsonValue.from(schema))
        }
        val input = BetaTool.InputSchema.builder().properties(properties.build())
        if (definition.required.isNotEmpty()) input.required(definition.required)
        return BetaTool.builder().name(definition.name).description(definition.description)
            .inputSchema(input.build()).build()
    }

    /**
     * The assistant turn exactly as it arrived (thinking blocks included). After a server-side fallback inside the
     * turn, the blocks of the declined attempt that must not be echoed are left out and the fallback marker stays.
     */
    private fun assistantTurn(message: BetaMessage): BetaMessageParam {
        val content = message.content()
        val boundary = content.indexOfLast { it.fallback().isPresent }
        if (boundary < 0) return message.toParam()
        val kept = content.filterIndexed { index, block ->
            index >= boundary || !(block.thinking().isPresent || block.redactedThinking().isPresent ||
                block.toolUse().isPresent)
        }
        return BetaMessageParam.ofAssistantOfContentBlockParams(kept.map(BetaContentBlock::toParam))
    }

    private fun toolResultMessage(pending: List<Pair<String, String>>, responses: List<LocalToolResponse>): BetaMessageParam {
        val unused = responses.toMutableList()
        val blocks = mutableListOf<BetaContentBlockParam>()
        pending.forEach { (id, name) ->
            val response = unused.firstOrNull { it.toolName == name } ?: unused.firstOrNull().takeIf { pending.size == 1 }
            if (response != null) unused.remove(response)
            blocks += BetaContentBlockParam.ofToolResult(BetaToolResultBlockParam.builder()
                .toolUseId(id)
                .content(response?.response ?: "{\"status\":\"FAILED\",\"reason\":\"No result was produced.\"}")
                .isError(response == null || !succeeded(response.response))
                .build())
        }
        // A result without a matching call is still data of this turn, never an instruction.
        unused.forEach { blocks += BetaContentBlockParam.ofText("Additional tool data (${it.toolName}): ${it.response}") }
        return BetaMessageParam.ofUserOfContentBlockParams(blocks)
    }

    private fun succeeded(response: String): Boolean =
        runCatching { JSONObject(response).optString("status") == "SUCCEEDED" }.getOrDefault(false)

    private fun toArguments(input: JsonValue): Map<String, Any?>? {
        val fields = input.asObject().orElse(null) ?: return null
        return fields.mapValues { (_, value) -> plain(value) }
    }

    private fun plain(value: JsonValue): Any? = when {
        value.isNull() || value.isMissing() -> null
        value.asString().isPresent -> value.asString().get()
        value.asBoolean().isPresent -> value.asBoolean().get()
        value.asNumber().isPresent -> value.asNumber().get().let { number ->
            val exact = number.toDouble()
            if (exact % 1.0 != 0.0 || !exact.isFinite()) exact
            else if (exact >= Int.MIN_VALUE && exact <= Int.MAX_VALUE) number.toInt() else number.toLong()
        }
        value.asArray().isPresent -> value.asArray().get().map(::plain)
        // A nested object is no registered argument type; validation rejects it.
        else -> value.asObject().orElse(emptyMap()).mapValues { (_, nested) -> plain(nested) }
    }

    private fun errorMessage(failure: AnthropicServiceException): String = runCatching {
        failure.body().asObject().orElse(null)?.get("error")?.asObject()?.orElse(null)?.get("message")
            ?.asString()?.orElse(null)
    }.getOrNull().orEmpty().lowercase()

    private fun badRequestProblem(failure: BadRequestException, detail: String): String = when {
        failure.errorType().orElse(null) == ErrorType.BILLING_ERROR ||
            detail.contains("credit balance is too low") -> ClaudeBrainFailure.NO_CREDIT
        detail.contains("data retention") -> ClaudeBrainFailure.DATA_RETENTION_REQUIRED
        detail.contains("prompt is too long") -> ClaudeBrainFailure.CONTEXT_TOO_LONG
        else -> ClaudeBrainFailure.BAD_REQUEST
    }

    private fun serviceProblem(failure: AnthropicServiceException, streamOpened: Boolean): String = when (failure.errorType().orElse(null)) {
        ErrorType.BILLING_ERROR -> ClaudeBrainFailure.NO_CREDIT
        ErrorType.AUTHENTICATION_ERROR -> ClaudeBrainFailure.INVALID_API_KEY
        ErrorType.PERMISSION_ERROR -> ClaudeBrainFailure.PERMISSION_DENIED
        ErrorType.NOT_FOUND_ERROR -> ClaudeBrainFailure.MODEL_UNAVAILABLE
        ErrorType.RATE_LIMIT_ERROR -> ClaudeBrainFailure.RATE_LIMITED
        ErrorType.OVERLOADED_ERROR, ErrorType.API_ERROR -> ClaudeBrainFailure.OVERLOADED
        ErrorType.TIMEOUT_ERROR -> ClaudeBrainFailure.TIMEOUT
        else -> when (failure.statusCode()) {
            402 -> ClaudeBrainFailure.NO_CREDIT
            413 -> ClaudeBrainFailure.CONTEXT_TOO_LONG
            in 500..599 -> ClaudeBrainFailure.OVERLOADED
            // An unclassified error event inside an already running stream.
            else -> if (streamOpened) ClaudeBrainFailure.STREAM_INTERRUPTED else ClaudeBrainFailure.BAD_REQUEST
        }
    }

    private fun connectionProblem(failure: AnthropicIoException, streamOpened: Boolean): String {
        val causes = generateSequence<Throwable>(failure) { it.cause }.take(8).toList()
        val timedOut = causes.any { it is SocketTimeoutException ||
            (it is InterruptedIOException && it.message.orEmpty().contains("timeout", ignoreCase = true)) }
        return when {
            timedOut -> ClaudeBrainFailure.TIMEOUT
            streamOpened -> ClaudeBrainFailure.STREAM_INTERRUPTED
            else -> ClaudeBrainFailure.OFFLINE
        }
    }

    private fun client(key: OwnerSecret): AnthropicClient = synchronized(clientLock) {
        cachedClient?.takeIf { it.first == key }?.second ?: run {
            cachedClient?.second?.let { runCatching { it.close() } }
            val builder = AnthropicOkHttpClient.builder()
                .apiKey(key.reveal())
                .timeout(Timeout.builder().connect(endpoint.connectTimeout).read(endpoint.readTimeout)
                    .request(endpoint.requestTimeout).build())
                .maxRetries(endpoint.maxRetries)
                // Requests, responses and headers are never logged or traced.
                .logLevel(LogLevel.OFF)
                .openTelemetry(false)
            endpoint.baseUrl?.let { builder.baseUrl(it) }
            builder.build().also { cachedClient = key to it }
        }
    }

    override fun toString(): String = "ClaudeModelRuntime(state=$state, credentials=redacted)"

    companion object {
        /** load() while the cloud brain is selected. */
        const val BRAIN_ACTIVE = "CLAUDE_BRAIN_ACTIVE"
        /** The connection test was asked for while another request runs. */
        const val BUSY = "CLAUDE_BUSY"
        const val CANCELLED = "CLAUDE_CANCELLED"
        const val CLOUD_VERSION = "cloud"
        private const val PROBE_TEXT = "Antworte nur mit dem Wort OK."

        private val DESCRIPTORS: Map<ClaudeModel, ImportedLocalModel> = ClaudeModel.entries.associateWith { model ->
            ImportedLocalModel(
                id = model.id, version = CLOUD_VERSION,
                sha256 = MessageDigest.getInstance("SHA-256").digest("anthropic-cloud:${model.id}".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                byteCount = 1L,
                capabilities = LocalModelCapabilities(text = true, vision = false, audio = false, video = false,
                    functionCalling = true, maxContextTokens = 1_000_000, minimumRuntimeVersion = null,
                    supportedBackends = setOf(LocalModelBackend.CPU)))
        }

        fun descriptor(model: ClaudeModel): ImportedLocalModel = DESCRIPTORS.getValue(model)

        fun isCloud(model: ImportedLocalModel?): Boolean =
            model != null && model.version == CLOUD_VERSION && ClaudeModel.fromId(model.id) != null
    }
}
