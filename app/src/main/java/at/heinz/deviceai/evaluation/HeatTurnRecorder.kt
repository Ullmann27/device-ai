package at.heinz.deviceai.evaluation

import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalModelThermalGuard

/**
 * What happened in ONE injected owner turn, as the app's own turn path reported it through thin hooks. All times
 * are milliseconds after the hand-over ([begin]) on the measurement clock. Pure and thread-safe: the hooks arrive
 * on the UI thread and on the inference thread.
 *
 * Timestamps, precisely:
 *  - first token: the first non-empty text piece the runtime handed to the turn's inference callback (first model
 *    call of the turn);
 *  - first visible text: the earlier of (a) the first time the answer draft was set on the native stream preview
 *    while that view is shown, (b) the moment the finished answer was added to the chat – in the Command03 stage
 *    that is the hand-over of the message to the page. Which of the two it was is recorded with it, and whether
 *    the receiving view was shown at all;
 *  - total: the moment the app released the turn for good (busy → not busy). A normal owner turn has more than one
 *    busy phase – memory and context first, then the model – with no pause between them, so the turn counts as
 *    released only when the app stayed free for [QUIET_END_MS]; the time written down is the release itself.
 */
class HeatTurnRecorder(private val clock: () -> Long) {
    enum class State { IDLE, RUNNING, ENDED }

    private class Call(val startMs: Long, val promptCharacters: Int?) {
        var firstTokenMs: Long? = null
        var endMs: Long? = null
        var completion: String? = null
        var failure: String? = null
        var characters = 0
        var inputTokens: Int? = null
        var outputTokens: Int? = null
        var runtimeFirstTokenMs: Long? = null
        var runtimeTotalMs: Long? = null
    }

    private var state = State.IDLE
    /** Counts the measured turns, so that a late event of an earlier turn can never land in the next one. */
    private var serial = 0
    private var handoverMs = 0L
    private var acceptedMs: Long? = null
    private var sawBusy = false
    /** Since when the app is free again after a busy phase; null while it is busy. */
    private var idleSinceMs: Long? = null
    private var endMs: Long? = null
    private val calls = ArrayList<Call>()
    private var previewMs: Long? = null
    private var previewShown = false
    private var stageDraftMs: Long? = null
    private var answerMs: Long? = null
    private var answerEvent: String? = null
    private var answerShown = false
    private var answerCharacters: Int? = null
    private var reused: Boolean? = null
    private var reuseReason: String? = null
    private var toolFree: Boolean? = null
    private var ownerStopped = false
    private var timedOut = false
    private var notAccepted: String? = null

    @get:Synchronized val active: Boolean get() = state == State.RUNNING

    /** The moment the fixed text is handed to the app's send path. */
    @Synchronized fun begin() {
        state = State.RUNNING
        serial++
        handoverMs = clock()
        acceptedMs = null; sawBusy = false; idleSinceMs = null; endMs = null; calls.clear()
        previewMs = null; previewShown = false; stageDraftMs = null; answerMs = null; answerEvent = null; answerShown = false
        answerCharacters = null; reused = null; reuseReason = null; toolFree = null
        ownerStopped = false; timedOut = false; notAccepted = null
    }

    private fun elapsed(): Long = (clock() - handoverMs).coerceAtLeast(0L)

    /** A chat bubble was added. [user]: the owner's order (the turn was accepted); otherwise an answer. */
    @Synchronized fun bubble(user: Boolean, characters: Int, event: String, shown: Boolean) {
        if (state != State.RUNNING) return
        if (user) { if (acceptedMs == null) acceptedMs = elapsed(); return }
        if (acceptedMs == null) return
        if (answerMs == null) { answerMs = elapsed(); answerEvent = event; answerShown = shown }
        answerCharacters = characters
    }

    /** The answer draft was set on the native stream preview; [shown]: that view is really on screen. */
    @Synchronized fun previewTextSet(shown: Boolean) {
        if (state != State.RUNNING || previewMs != null) return
        previewMs = elapsed()
        previewShown = shown
    }

    /** The answer draft was handed to the stage page while that page is really on screen (Command03). */
    @Synchronized fun stageDraftSet(shown: Boolean) {
        if (state != State.RUNNING || !shown || stageDraftMs != null) return
        stageDraftMs = elapsed()
    }

    /** A model call of the turn starts; null while no measured turn is running. */
    @Synchronized private fun generationStarted(promptCharacters: Int?): Pair<Int, Int>? {
        if (state != State.RUNNING) return null
        calls += Call(elapsed(), promptCharacters)
        return serial to calls.lastIndex
    }

    private fun call(turn: Int, index: Int): Call? =
        if (state == State.RUNNING && turn == serial) calls.getOrNull(index) else null

    @Synchronized private fun textPiece(turn: Int, index: Int, characters: Int) {
        val call = call(turn, index) ?: return
        if (characters <= 0) return
        if (call.firstTokenMs == null) call.firstTokenMs = elapsed()
    }

    @Synchronized private fun runtimeCompleted(turn: Int, index: Int, inputTokens: Int?, outputTokens: Int?, firstTokenMs: Long?, totalMs: Long) {
        val call = call(turn, index) ?: return
        call.inputTokens = inputTokens; call.outputTokens = outputTokens
        call.runtimeFirstTokenMs = firstTokenMs; call.runtimeTotalMs = totalMs
    }

    /** How the turn's blocking generation helper ended this model call ([completion]: its completion name). */
    @Synchronized private fun generationFinished(turn: Int, index: Int, completion: String, failure: String?, characters: Int?) {
        val call = call(turn, index) ?: return
        if (call.endMs != null) return
        call.endMs = elapsed()
        call.completion = completion
        call.failure = failure?.take(MAX_MESSAGE)
        call.characters = characters ?: 0
    }

    @Synchronized fun dialogueReuse(wasReused: Boolean, reason: String?) {
        if (state != State.RUNNING) return
        reused = wasReused
        reuseReason = reason?.take(80)
    }

    /** How the turn's first model call is addressed: without tool schemas (a plain text question) or with them. */
    @Synchronized fun route(withoutTools: Boolean) {
        if (state == State.RUNNING && toolFree == null) toolFree = withoutTools
    }

    @Synchronized fun busy(value: Boolean) {
        if (state != State.RUNNING) return
        if (value) { sawBusy = true; idleSinceMs = null }
        else if (sawBusy) idleSinceMs = elapsed()
    }

    @Synchronized fun ownerStop() { if (state == State.RUNNING) ownerStopped = true }
    @Synchronized fun timeout() { if (state == State.RUNNING) timedOut = true }

    /** The send path did not take the text (input still there, nothing started). */
    /**
     * Called regularly while the turn runs, on the thread the busy changes arrive on. [appBusy]: the app's own busy
     * flag right now. The turn ends when the app stayed free for [QUIET_END_MS] after its last busy phase – or,
     * for a reply without any work phase, after that reply – and when the app never accepted the order.
     */
    @Synchronized fun poll(appBusy: Boolean): State {
        if (state != State.RUNNING) return state
        val accepted = acceptedMs
        if (accepted == null) {
            if (elapsed() >= ACCEPT_TIMEOUT_MS) { notAccepted = "Der Auftrag wurde nicht angenommen."; end(elapsed()) }
        } else if (!appBusy) {
            // A busy flag that is off without a reported change (never busy at all) counts from the reply on.
            val since = idleSinceMs ?: (if (sawBusy) elapsed() else answerMs ?: accepted).also { idleSinceMs = it }
            if (elapsed() - since >= QUIET_END_MS) end(since)
        }
        return state
    }

    /** Ends the bookkeeping whatever the app did, e.g. after an abort the app did not answer. */
    @Synchronized fun forceEnd() { if (state == State.RUNNING) end(idleSinceMs ?: elapsed()) }

    private fun end(atMs: Long) {
        endMs = atMs
        state = State.ENDED
    }

    @Synchronized fun result(): HeatTurnResult {
        val first = calls.firstOrNull()
        val last = calls.lastOrNull()
        val thermal = calls.mapNotNull { it.failure }.firstOrNull(LocalModelThermalGuard::isThermal)
        // The earliest answer text on a surface that is really shown: a draft (native row or stage page) or the answer.
        val draft = listOfNotNull(previewMs?.takeIf { previewShown }?.let { it to EVENT_NATIVE_PREVIEW },
            stageDraftMs?.let { it to EVENT_STAGE_DRAFT }).minByOrNull { it.first }
        val visibleDraft = draft?.takeIf { answerMs == null || it.first <= answerMs!! }
        val outcome: String
        val message: String?
        when {
            ownerStopped -> { outcome = CANCELLED_BY_OWNER; message = null }
            timedOut -> { outcome = TIMEOUT; message = null }
            notAccepted != null -> { outcome = NOT_ACCEPTED; message = notAccepted }
            thermal == LocalModelThermalGuard.STOPPED_THERMAL -> { outcome = STOPPED_BY_THERMAL_GUARD; message = thermal }
            thermal == LocalModelThermalGuard.PAUSED_THERMAL -> { outcome = PAUSED_BY_THERMAL_GUARD; message = thermal }
            last == null -> { outcome = NO_MODEL_CALL; message = "Der Auftrag wurde ohne Modellaufruf beantwortet." }
            last.completion == null -> { outcome = FAILED; message = "Der Modellaufruf wurde nicht abgeschlossen." }
            last.completion == "COMPLETE" && answerMs == null -> {
                outcome = FAILED; message = "Die Antwort des Modells wurde nicht angezeigt."
            }
            last.completion == "COMPLETE" -> { outcome = COMPLETE; message = null }
            last.completion == "STALLED" -> { outcome = STALLED; message = last.failure }
            last.completion == "OUTPUT_LIMIT" -> { outcome = OUTPUT_LIMIT; message = last.failure }
            last.completion == "CANCELLED" -> { outcome = CANCELLED_BY_APP; message = last.failure }
            else -> { outcome = FAILED; message = last.failure ?: last.completion }
        }
        val writingMs = calls.sumOf { call ->
            val start = call.firstTokenMs
            val end = call.endMs
            if (start != null && end != null && end > start) end - start else 0L
        }
        val generated = calls.sumOf { it.characters }
        return HeatTurnResult(
            outcome = outcome, message = message,
            acceptedMs = acceptedMs,
            firstTokenMs = first?.firstTokenMs,
            firstVisibleTextMs = visibleDraft?.first ?: answerMs,
            firstVisibleTextEvent = visibleDraft?.second ?: answerEvent,
            firstVisibleTextShown = if (visibleDraft != null) true else if (answerMs != null) answerShown else null,
            nativePreviewMs = previewMs, nativePreviewShown = if (previewMs != null) previewShown else null,
            answerHandoverMs = answerMs,
            totalMs = endMs,
            modelCalls = calls.size,
            completions = calls.map { it.completion ?: "OPEN" },
            promptCharacters = first?.promptCharacters,
            generatedCharacters = generated,
            answerCharacters = answerCharacters,
            charsPerSecond = if (writingMs > 0 && generated > 0) generated * 1_000.0 / writingMs else null,
            inputTokens = first?.inputTokens, outputTokens = calls.mapNotNull { it.outputTokens }.takeIf { it.isNotEmpty() }?.sum(),
            runtimeFirstTokenMs = first?.runtimeFirstTokenMs, runtimeTotalMs = calls.mapNotNull { it.runtimeTotalMs }.takeIf { it.isNotEmpty() }?.sum(),
            dialogueReused = reused, dialogueReuseReason = reuseReason, toolFreeRoute = toolFree)
    }

    /**
     * Sees the events of one model call without changing them: it stands between the runtime and the turn's own
     * callback and forwards every event unchanged. A failure inside the bookkeeping never reaches the turn.
     */
    inner class GenerationProbe internal constructor(private val turn: Int, private val index: Int) {
        fun observe(delegate: LocalInferenceCallback): LocalInferenceCallback = LocalInferenceCallback { event ->
            runCatching {
                when (event) {
                    is LocalInferenceEvent.TextDelta -> textPiece(turn, index, event.text.length)
                    is LocalInferenceEvent.Completed -> runtimeCompleted(turn, index, event.metrics.inputTokens,
                        event.metrics.outputTokens, event.metrics.firstTokenLatencyMillis, event.metrics.totalLatencyMillis)
                    else -> Unit
                }
            }
            delegate.onEvent(event)
        }

        fun finished(completion: String, failure: String?, characters: Int?) {
            runCatching { generationFinished(turn, index, completion, failure, characters) }
        }
    }

    /** Null while no turn of a measurement is running: the app's turn path then stays exactly as it is. */
    fun probe(promptCharacters: Int?): GenerationProbe? =
        generationStarted(promptCharacters)?.let { (turn, index) -> GenerationProbe(turn, index) }

    companion object {
        const val COMPLETE = "COMPLETE"
        const val STOPPED_BY_THERMAL_GUARD = "STOPPED_BY_THERMAL_GUARD"
        const val PAUSED_BY_THERMAL_GUARD = "PAUSED_BY_THERMAL_GUARD"
        const val TIMEOUT = "TIMEOUT"
        const val FAILED = "FAILED"
        const val STALLED = "STALLED"
        const val OUTPUT_LIMIT = "OUTPUT_LIMIT"
        const val CANCELLED_BY_OWNER = "CANCELLED_BY_OWNER"
        const val CANCELLED_BY_APP = "CANCELLED_BY_APP"
        const val NO_MODEL_CALL = "NO_MODEL_CALL"
        const val NOT_ACCEPTED = "NOT_ACCEPTED"

        /** Outcomes that are an interruption of an answer by the app, the model or the device („Abbrüche“). */
        val ABORTS: Set<String> = setOf(STOPPED_BY_THERMAL_GUARD, PAUSED_BY_THERMAL_GUARD, TIMEOUT, FAILED, STALLED,
            OUTPUT_LIMIT, CANCELLED_BY_APP)

        const val EVENT_NATIVE_PREVIEW = "NATIVE_STREAM_PREVIEW_TEXT_SET"
        const val EVENT_STAGE_DRAFT = "ANSWER_DRAFT_HANDED_TO_STAGE_PAGE"
        const val EVENT_STAGE_MESSAGE = "ANSWER_HANDED_TO_STAGE_PAGE"
        const val EVENT_NATIVE_BUBBLE = "ANSWER_BUBBLE_ADDED_TO_NATIVE_CHAT"

        const val ACCEPT_TIMEOUT_MS = 20_000L
        const val QUIET_END_MS = 1_000L
        const val MAX_MESSAGE = 200
    }
}

/** One turn as observed; null where the turn did not get that far. Milliseconds after the hand-over. */
data class HeatTurnResult(
    val outcome: String,
    val message: String?,
    val acceptedMs: Long?,
    val firstTokenMs: Long?,
    val firstVisibleTextMs: Long?,
    val firstVisibleTextEvent: String?,
    val firstVisibleTextShown: Boolean?,
    val nativePreviewMs: Long?,
    val nativePreviewShown: Boolean?,
    val answerHandoverMs: Long?,
    val totalMs: Long?,
    val modelCalls: Int,
    val completions: List<String>,
    val promptCharacters: Int?,
    val generatedCharacters: Int,
    val answerCharacters: Int?,
    val charsPerSecond: Double?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val runtimeFirstTokenMs: Long?,
    val runtimeTotalMs: Long?,
    val dialogueReused: Boolean?,
    val dialogueReuseReason: String?,
    /** True: the model got the order as a plain text question without tool schemas; null: not reported. */
    val toolFreeRoute: Boolean?
)
