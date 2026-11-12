package at.heinz.deviceai.cognitive

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolExecutionReceipt
import at.heinz.deviceai.ToolReceiptState
import at.heinz.deviceai.toolArgumentFingerprint
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/*
 * Live-computer operation contract (Code55, Agent C). Pure Kotlin, host-tested.
 *
 * observe -> one real input -> new observation -> effect CONFIRMED or UNCLEAR. An accepted action is not a successful
 * action: only a newer observation of the same task/app with the expected change confirms it. UNCLEAR is final for
 * that action in that task: it is never repeated automatically. Sending/publishing is impossible without an exact,
 * single-use approval minted from the existing ToolGate receipt path (no such tool is registered today).
 */

private val ACTION_PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")
private val ACTION_ID = Regex("[A-Za-z0-9._:-]{1,128}")

internal fun liveSha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** One observation an action decision is bound to (accessibility node tree and/or frame). */
internal data class ObservationStamp(
    val taskId: String,
    val epoch: Long,
    val revision: Long,
    val packageName: String,
    val windowId: Int?,
    val digest: String,
    val observedAtNanos: Long,
    /** Sequence of the LatestLiveFrameHolder frame shown at that moment; 0 = no frame. */
    val frameSequence: Long = 0,
    val captureSession: Long = 0
)

internal enum class EffectVerdict {
    CONFIRMED,
    /** Android refused the action before dispatch; nothing reached the app. Retry only after a fresh observation. */
    NOT_DISPATCHED,
    /** The dispatch itself failed after hand-off: the input may or may not have reached the app. */
    UNCLEAR_DISPATCH,
    UNCLEAR_NO_NEW_OBSERVATION,
    UNCLEAR_CONTEXT_CHANGED,
    UNCLEAR_UNCHANGED,
    UNCLEAR_EXPECTATION_NOT_MET;

    val confirmed: Boolean get() = this == CONFIRMED
    val unclear: Boolean get() = this != CONFIRMED && this != NOT_DISPATCHED
}

internal object ActionEffectVerifier {
    /**
     * [accepted]: true = Android accepted the action, false = refused before dispatch, null = dispatch threw.
     * [expectationMet] is evaluated by the caller on [after] only (never on [before]).
     */
    fun verify(before: ObservationStamp, accepted: Boolean?, after: ObservationStamp?, expectationMet: Boolean): EffectVerdict = when {
        accepted == false -> EffectVerdict.NOT_DISPATCHED
        accepted == null -> EffectVerdict.UNCLEAR_DISPATCH
        after == null -> EffectVerdict.UNCLEAR_NO_NEW_OBSERVATION
        after.taskId != before.taskId || after.epoch != before.epoch || after.packageName != before.packageName ||
            after.windowId != before.windowId || after.captureSession != before.captureSession ->
            EffectVerdict.UNCLEAR_CONTEXT_CHANGED
        after.revision <= before.revision || after.observedAtNanos <= before.observedAtNanos ->
            EffectVerdict.UNCLEAR_NO_NEW_OBSERVATION
        after.digest == before.digest -> EffectVerdict.UNCLEAR_UNCHANGED
        !expectationMet -> EffectVerdict.UNCLEAR_EXPECTATION_NOT_MET
        else -> EffectVerdict.CONFIRMED
    }
}

/** Normalized (0..1) target bounds measured in one observation. Stale bounds are discarded, never re-used. */
internal data class LiveTargetBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init { require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f && left < right && top < bottom) }
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Real execution events for the cockpit (gaze, click ring, scroll follow). Emitted only after a real dispatch or a
 * real observation. Adapter point for Agent D's shared `CockpitWorkEvent` contract; not a second event system.
 */
internal sealed interface LiveActionEvent {
    val taskId: String
    val stepId: String
    data class Dispatched(override val taskId: String, override val stepId: String, val kind: NodeActionKind,
                          val packageName: String, val target: LiveTargetBounds?, val observationRevision: Long,
                          val frameSequence: Long) : LiveActionEvent
    data class Observed(override val taskId: String, override val stepId: String, val stamp: ObservationStamp) : LiveActionEvent
    data class Verified(override val taskId: String, override val stepId: String, val kind: NodeActionKind,
                        val verdict: EffectVerdict) : LiveActionEvent
}

internal fun interface LiveActionEventSink { fun emit(event: LiveActionEvent) }

/**
 * Per-task step journal: no duplicate action on the same observation, nothing after an UNCLEAR effect without a newer
 * observation, and never the same action again after it ended UNCLEAR. Bounded; not a task manager.
 */
internal class LiveActionJournal(val taskId: String, private val capacity: Int = 64) {
    enum class Admission { ALLOWED, IN_FLIGHT, BLOCKED_AFTER_UNCLEAR, NEEDS_FRESH_OBSERVATION, DUPLICATE_ON_SAME_OBSERVATION }
    private data class Entry(val fingerprint: String, val revision: Long, val digest: String, val verdict: EffectVerdict)
    private val entries = ArrayDeque<Entry>()
    private val unclearFingerprints = LinkedHashSet<String>()
    private var lastUnclearRevision = Long.MIN_VALUE
    private var uncertaintyCapacityExhausted = false
    private var inFlight: String? = null

    init { require(taskId.matches(ACTION_ID) && capacity in 1..256) }

    @Synchronized fun admit(fingerprint: String, before: ObservationStamp): Admission {
        require(before.taskId == taskId) { "TASK_MISMATCH" }
        return when {
            inFlight != null -> Admission.IN_FLIGHT
            uncertaintyCapacityExhausted || fingerprint in unclearFingerprints -> Admission.BLOCKED_AFTER_UNCLEAR
            before.revision <= lastUnclearRevision -> Admission.NEEDS_FRESH_OBSERVATION
            entries.any { it.fingerprint == fingerprint && it.revision == before.revision && it.digest == before.digest } ->
                Admission.DUPLICATE_ON_SAME_OBSERVATION
            else -> { inFlight = fingerprint; Admission.ALLOWED }
        }
    }

    @Synchronized fun finish(fingerprint: String, before: ObservationStamp, verdict: EffectVerdict, after: ObservationStamp?) {
        check(inFlight == fingerprint) { "NO_ACTION_IN_FLIGHT" }
        inFlight = null
        entries.addLast(Entry(fingerprint, before.revision, before.digest, verdict))
        while (entries.size > capacity) entries.removeFirst()
        if (verdict.unclear) {
            if (unclearFingerprints.size < capacity) unclearFingerprints += fingerprint
            else uncertaintyCapacityExhausted = true
            lastUnclearRevision = maxOf(lastUnclearRevision, before.revision, after?.revision ?: Long.MIN_VALUE)
        }
    }
}

/** Conservative deny-list for irreversible communication/publishing targets (DE/EN labels and view ids). */
internal object SendActionGuard {
    private val words = setOf(
        "senden", "send", "absenden", "abschicken", "verschicken", "nachricht senden", "send message",
        "posten", "post", "veröffentlichen", "publish", "teilen", "share", "antworten", "reply",
        "kommentieren", "comment", "gefällt mir", "like", "beitreten", "join", "anrufen", "call"
    )
    private val idParts = listOf("send", "post", "publish", "share", "reply", "comment", "like", "join", "call")

    fun isSendLike(label: String?, viewId: String? = null): Boolean {
        val text = label?.trim()?.lowercase()?.trimEnd('.', '!', '…')
        if (text != null && (text in words || words.any { text.startsWith("$it ") })) return true
        val id = viewId?.substringAfter(":id/", viewId)?.lowercase() ?: return false
        return idParts.any { id == it || id.startsWith("${it}_") || id.endsWith("_$it") || id.contains("_${it}_") }
    }
}

/**
 * Exact, single-use permission for ONE send of ONE draft in ONE app and task. It can only be minted from a ToolGate
 * receipt that passed the owner's strong confirmation (DESTRUCTIVE, journaled APPROVED and committed dispatch) for a
 * registered send tool with exactly these arguments. [SEND_TOOLS] is empty: Continuum registers no message-send
 * tool, so production can mint nothing and every send is refused.
 */
internal class SendApproval private constructor(
    val callId: String, val taskId: String, val packageName: String, private val draftDigest: String
) {
    private val used = AtomicBoolean(false)

    /** True once, for exactly the approved task/app/draft. */
    fun consume(taskId: String, packageName: String, draft: String): Boolean =
        taskId == this.taskId && packageName == this.packageName && liveSha256(draft) == draftDigest &&
            used.compareAndSet(false, true)

    internal companion object {
        val SEND_TOOLS: Set<String> = emptySet()

        fun mint(receipt: ToolExecutionReceipt, request: ToolCallRequest, taskId: String,
                 sendTools: Set<String> = SEND_TOOLS): SendApproval? {
            if (receipt.toolName !in sendTools || request.name != receipt.toolName || request.callId != receipt.callId) return null
            if (receipt.risk != Risk.DESTRUCTIVE) return null
            if (receipt.state != ToolReceiptState.DISPATCH_COMMITTED) return null
            if (receipt.transitions.none { it.state == ToolReceiptState.APPROVED }) return null
            if (toolArgumentFingerprint(request.arguments) != receipt.argumentFingerprint) return null
            val pkg = request.arguments["packageOrName"] as? String ?: return null
            val draft = request.arguments["text"] as? String ?: return null
            if (!pkg.matches(ACTION_PACKAGE) || draft.isEmpty() || !taskId.matches(ACTION_ID)) return null
            return SendApproval(receipt.callId, taskId, pkg, liveSha256(draft))
        }
    }
}

internal enum class NodeActionKind { CLICK, SCROLL_FORWARD, SCROLL_BACKWARD, SET_TEXT, SEND }

internal data class ActionNode(
    val label: String,
    val viewId: String?,
    val visible: Boolean,
    val enabled: Boolean,
    val clickable: Boolean,
    val scrollable: Boolean,
    val editable: Boolean,
    val password: Boolean,
    val bounds: LiveTargetBounds?,
    /** SHA-256 of an editable, non-password field's text; used only to confirm the own draft, never surfaced. */
    val textDigest: String? = null,
    val heading: Boolean = false
)

internal data class NodeActionFrame(val stamp: ObservationStamp, val safeWindows: Boolean, val complete: Boolean,
                                    val nodes: List<ActionNode>, val scrollEvidence: LiveScrollEvidence? = null)

/** Exactly one of: label, viewId, or the single visible scrollable container. */
internal data class NodeSelector(val label: String? = null, val viewId: String? = null, val singleScrollable: Boolean = false) {
    init {
        require(listOf(label != null, viewId != null, singleScrollable).count { it } == 1) { "AMBIGUOUS_SELECTOR" }
        fun valid(s: String) = s.length in 1..200 && s == s.trim() && s.none { it.isISOControl() }
        require(label == null || valid(label)) { "INVALID_LABEL" }
        require(viewId == null || valid(viewId)) { "INVALID_VIEW_ID" }
    }
    fun matches(node: ActionNode): Boolean = when {
        label != null -> node.label == label
        viewId != null -> node.viewId == viewId
        else -> node.scrollable
    }
    override fun toString() = label?.let { "label:$it" } ?: viewId?.let { "id:$it" } ?: "single-scrollable"
}

internal data class NodeActionRequest(
    val taskId: String, val stepId: String, val packageName: String, val epoch: Long, val revision: Long,
    val kind: NodeActionKind, val selector: NodeSelector, val text: String? = null
) {
    init {
        require(taskId.matches(ACTION_ID) && stepId.matches(ACTION_ID)) { "INVALID_ID" }
        require(packageName.length <= 200 && packageName.matches(ACTION_PACKAGE)) { "INVALID_PACKAGE" }
        val needsText = kind == NodeActionKind.SET_TEXT || kind == NodeActionKind.SEND
        require(needsText == (text != null)) { "TEXT_ARGUMENT_MISMATCH" }
        require(text == null || text.length in 1..MAX_TEXT && text.none { it.isISOControl() }) { "INVALID_TEXT" }
    }
    /** Same action, selector and text; the observation it applies to is tracked by the journal. */
    val fingerprint: String get() = liveSha256("$packageName|$kind|$selector|${text?.let(::liveSha256) ?: ""}")
    companion object { const val MAX_TEXT = 500 }
}

internal interface NodeActionPort {
    /** Throws on revocation, cancellation, interruption or the overall deadline. */
    fun checkActive()
    fun current(): NodeActionFrame
    /** Must revalidate package, window, grant, revision and the unique node right before ONE dispatch. */
    fun performOnce(request: NodeActionRequest, target: ActionNode): Boolean
    /** A newer observation after the dispatch, or null when none arrived within the caller's deadline. */
    fun awaitPostcondition(): NodeActionFrame?
    /** Release this action's observation binding. */
    fun finished() {}
}

internal data class NodeActionResult(
    val kind: NodeActionKind,
    val verdict: EffectVerdict,
    val nodeFound: Boolean,
    /** null = never attempted; false = Android refused; true = Android accepted (NOT yet a success). */
    val accepted: Boolean?,
    val postObserved: Boolean,
    val before: ObservationStamp?,
    val after: ObservationStamp?,
    val reason: String
) {
    val confirmed: Boolean get() = verdict.confirmed
}

/**
 * Generic verified node action for scroll, text entry and (gated) send. CLICK stays with the existing, stricter
 * [VerifiedNodeClick] protocol and is refused here. Exceptions before dispatch propagate (nothing happened);
 * anything after dispatch becomes an UNCLEAR result and is never retried.
 */
internal class VerifiedNodeAction(private val journal: LiveActionJournal, private val events: LiveActionEventSink = LiveActionEventSink {}) {
    private val active = AtomicBoolean(false)

    fun execute(request: NodeActionRequest, port: NodeActionPort, sendApproval: SendApproval? = null): NodeActionResult {
        require(request.taskId == journal.taskId) { "TASK_MISMATCH" }
        fun refused(reason: String, found: Boolean = false, before: ObservationStamp? = null) =
            NodeActionResult(request.kind, EffectVerdict.NOT_DISPATCHED, found, null, false, before, null, reason)
        if (request.kind == NodeActionKind.CLICK) return refused("CLICK_USES_VERIFIED_NODE_CLICK")
        if (!active.compareAndSet(false, true)) return refused("ANOTHER_DEVICE_ACTION_ACTIVE")
        try {
            port.checkActive()
            val before = port.current()
            val stamp = before.stamp
            if (stamp.taskId != request.taskId || stamp.packageName != request.packageName || stamp.epoch != request.epoch ||
                stamp.revision != request.revision || !before.safeWindows || !before.complete || before.nodes.size > 256 ||
                stamp.digest.isBlank()) return refused("STALE_OR_UNSAFE_SCREEN", before = stamp)
            val matches = before.nodes.filter { it.visible && request.selector.matches(it) }
            if (matches.size != 1) return refused(if (matches.isEmpty()) "TARGET_NOT_FOUND" else "AMBIGUOUS_TARGET", before = stamp)
            val target = matches.single()
            val sendLike = SendActionGuard.isSendLike(target.label, target.viewId)
            when (request.kind) {
                NodeActionKind.SCROLL_FORWARD, NodeActionKind.SCROLL_BACKWARD ->
                    if (!target.scrollable || !target.enabled || target.password || sendLike)
                        return refused("UNSAFE_SCROLL_TARGET", true, stamp)
                NodeActionKind.SET_TEXT -> {
                    if (!target.editable || !target.enabled || target.password || sendLike) return refused("UNSAFE_TEXT_TARGET", true, stamp)
                    if (target.textDigest == liveSha256(request.text!!)) return refused("POSTCONDITION_ALREADY_PRESENT", true, stamp)
                }
                NodeActionKind.SEND -> {
                    // Exact owner approval first; without it nothing about the target is acted on.
                    if (sendApproval == null) return refused("SEND_REQUIRES_EXACT_OWNER_APPROVAL", true, stamp)
                    if (!sendLike || !target.enabled || !target.clickable || target.editable) return refused("UNSAFE_SEND_TARGET", true, stamp)
                    if (!sendApproval.consume(request.taskId, request.packageName, request.text!!))
                        return refused("SEND_APPROVAL_DOES_NOT_COVER_THIS_DRAFT", true, stamp)
                }
                NodeActionKind.CLICK -> error("unreachable")
            }
            val admission = journal.admit(request.fingerprint, stamp)
            if (admission != LiveActionJournal.Admission.ALLOWED) return refused("JOURNAL_" + admission.name, true, stamp)
            var accepted: Boolean? = null
            var after: ObservationStamp? = null
            var verdict = EffectVerdict.UNCLEAR_DISPATCH
            var reason = "DISPATCH_FAILED"
            try {
                port.checkActive()
                accepted = try { port.performOnce(request, target) } catch (e: Exception) { reason = "DISPATCH_FAILED:" + (e.message ?: e.javaClass.simpleName).take(80); null }
                if (accepted == true) {
                    events.emit(LiveActionEvent.Dispatched(request.taskId, request.stepId, request.kind, request.packageName,
                        target.bounds, stamp.revision, stamp.frameSequence))
                    val post = try { port.checkActive(); port.awaitPostcondition()?.also { port.checkActive() } }
                    catch (e: Exception) { reason = "POSTCONDITION_INTERRUPTED:" + (e.message ?: e.javaClass.simpleName).take(80); null }
                    after = post?.stamp?.takeIf { post.safeWindows && post.complete }
                    if (after != null) events.emit(LiveActionEvent.Observed(request.taskId, request.stepId, after))
                    val expectation = post != null && expected(request, stamp, target, post)
                    verdict = ActionEffectVerifier.verify(stamp, true, after, expectation)
                    if (post == null && reason == "DISPATCH_FAILED") reason = "NO_NEW_OBSERVATION"
                    else if (post != null) reason = verdict.name
                } else if (accepted == false) {
                    verdict = EffectVerdict.NOT_DISPATCHED
                    reason = "ACTION_NOT_ACCEPTED"
                }
            } finally {
                journal.finish(request.fingerprint, stamp, verdict, after)
            }
            events.emit(LiveActionEvent.Verified(request.taskId, request.stepId, request.kind, verdict))
            return NodeActionResult(request.kind, verdict, true, accepted, after != null, stamp, after, reason)
        } finally {
            try { port.finished() } finally { active.set(false) }
        }
    }

    /** Expected change, judged on the NEW observation only. */
    private fun expected(request: NodeActionRequest, before: ObservationStamp, target: ActionNode,
                         post: NodeActionFrame): Boolean = when (request.kind) {
        // A changed badge proves nothing.
        NodeActionKind.SCROLL_FORWARD, NodeActionKind.SCROLL_BACKWARD ->
            post.scrollEvidence?.confirms(request, before, target, post) == true
        // The very field now holds exactly the requested draft (compared by digest, text is not surfaced).
        NodeActionKind.SET_TEXT -> post.nodes.filter { it.visible && request.selector.matches(it) && it.editable && !it.password }
            .singleOrNull()?.textDigest == liveSha256(request.text!!)
        // After a send the compose field is no longer showing the draft; the send target itself must not be ambiguous.
        NodeActionKind.SEND -> post.nodes.none { it.editable && it.textDigest == liveSha256(request.text!!) }
        NodeActionKind.CLICK -> false
    }
}
