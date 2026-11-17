package at.heinz.deviceai.cockpit

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolExecutionRecord
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.cognitive.AgentToolApprovalProvider
import at.heinz.deviceai.cognitive.ExternalContentActionGuard
import java.util.concurrent.atomic.AtomicInteger

/**
 * Muster G safety layer (Jarvis Sicherheit) for Kern's [PillActionGate].
 *
 * - A pill is enabled only when its action maps to a REAL registered tool. Today only STUDIE (research) maps to
 *   `web_search`; mail send, local drafting and phone calls have no tool → disabled, "Quelle fehlt". No tool is invented.
 * - [request] never executes directly: pill must be the canonical pill of a document seen via [pillsFor] for that
 *   turn → S4 [ExternalContentActionGuard] (document content is untrusted, owner task empty) → explicit owner yes via
 *   [approvals] → exactly one [ToolGate.execute]. No yes → no execution.
 * - Document text (question, name) is untrusted data: it never enables a pill and never becomes a tool target; the
 *   research query is built from the sanitized file name only.
 */
class ToolGatePillActionGate(
    private val toolGate: ToolGate,
    private val approvals: AgentToolApprovalProvider,
    private val callIdPrefix: String = "pill"
) : PillActionGate {
    private val documents = object : LinkedHashMap<String, CockpitAnimationEvent.DocumentQuestion>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CockpitAnimationEvent.DocumentQuestion>) = size > 16
    }
    private val calls = AtomicInteger()

    override fun pillsFor(event: CockpitAnimationEvent.DocumentQuestion): List<AnswerPill> {
        synchronized(documents) { documents[event.turnId] = event }
        return canonicalPills()
    }

    override fun request(pill: AnswerPill, turnId: String, onDecision: (approvedByOwner: Boolean) -> Unit) {
        val doc = synchronized(documents) { documents[turnId] }
        if (doc == null || pill !in canonicalPills() || !pill.enabled) return onDecision(false)
        val tool = pill.toolName ?: return onDecision(false)
        val spec = ToolRegistry.get(tool) ?: return onDecision(false)
        val proposal = ToolProposal(tool, argumentsFor(pill.kind, doc) ?: return onDecision(false))
        if (proposal.validate() != null) return onDecision(false)
        // S4: the document is external content; the owner tapped a pill but typed no target.
        if (ExternalContentActionGuard.refusal("", tool, spec.risk, proposal.arguments, untrustedObservationsSeen = true) != null)
            return onDecision(false)
        val approval = approvals.requestApproval(proposal, spec.risk)
        if (!approval.userConfirmed || (spec.risk == Risk.DESTRUCTIVE && !approval.destructiveConfirmed)) return onDecision(false)
        val result = toolGate.execute(proposal.invocation("$callIdPrefix-$turnId-${calls.incrementAndGet()}"),
            userConfirmed = true, destructiveConfirmed = approval.destructiveConfirmed)
        onDecision(result.record.status == ToolExecutionStatus.SUCCEEDED)
    }

    private fun argumentsFor(kind: AnswerPillKind, doc: CockpitAnimationEvent.DocumentQuestion): Map<String, Any?>? = when (kind) {
        AnswerPillKind.STUDIE -> mapOf("query" to researchQuery(doc))
        else -> null
    }

    companion object {
        const val SOURCE_MISSING = "Quelle fehlt"
        /** Pill → real tool. Null = no tool exists in ToolRegistry yet (see handoff note for Kern). */
        val TOOL_FOR: Map<AnswerPillKind, String?> = mapOf(
            AnswerPillKind.DIREKT_ANTWORTEN to null,   // no mail-send tool
            AnswerPillKind.AUSARBEITEN to null,        // no local drafting tool
            AnswerPillKind.STUDIE to "web_search",     // research via existing consent-gated web search
            AnswerPillKind.ANRUFEN to null             // no call tool
        )
        val LABELS = mapOf(AnswerPillKind.DIREKT_ANTWORTEN to "Direkt antworten?", AnswerPillKind.AUSARBEITEN to "Ausarbeiten?",
            AnswerPillKind.STUDIE to "Juristische Studie?", AnswerPillKind.ANRUFEN to "Anrufen?")

        fun canonicalPills(): List<AnswerPill> = AnswerPillKind.values().map { kind ->
            val tool = TOOL_FOR[kind]?.takeIf { ToolRegistry.get(it) != null }
            AnswerPill(kind, tool, tool != null, if (tool == null) SOURCE_MISSING else null)
        }

        /** Research query from the sanitized document file name only (never from document text). */
        fun researchQuery(doc: CockpitAnimationEvent.DocumentQuestion): String {
            val name = doc.documentName.substringAfterLast('/').substringBeforeLast('.')
                .filter { it.isLetter() || it == ' ' || it == '-' }.trim().take(60)
            return "Juristische Einordnung " + name.ifEmpty { "Dokument" }
        }
    }
}

/**
 * Muster F source for Kern's [AccountEventSource]: emits [CockpitAnimationEvent.AccountCardFlyIn] ONLY after a
 * real, owner-approved, successful ToolGate call of an account tool. Production has no account tool yet
 * ([accountTools] empty) → never emits ("Quelle fehlt"). The caller passes each finished ToolGate record.
 */
class ApprovedAccountEventSource(
    private val accountTools: Map<String, String> = emptyMap(),
    private val clock: () -> Long = System::currentTimeMillis
) : AccountEventSource {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(CockpitAnimationEvent.AccountCardFlyIn) -> Unit>()

    override fun addListener(l: (CockpitAnimationEvent.AccountCardFlyIn) -> Unit): AutoCloseable {
        val entry: (CockpitAnimationEvent.AccountCardFlyIn) -> Unit = { l(it) }  // unique identity per registration
        listeners += entry
        return AutoCloseable { listeners.remove(entry) }
    }

    val listenerCount: Int get() = listeners.size

    fun onToolCompleted(turnId: String, record: ToolExecutionRecord, ownerApproved: Boolean, accountLabel: String) {
        val provider = accountTools[record.toolName] ?: return
        if (!ownerApproved || record.status != ToolExecutionStatus.SUCCEEDED) return
        val event = CockpitAnimationEvent.AccountCardFlyIn(turnId, clock(), CockpitAnimationTriggers.clean(provider, 24),
            CockpitAnimationTriggers.clean(accountLabel, 64))
        for (l in listeners) runCatching { l(event) }
    }
}
