package at.heinz.deviceai.chief

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.cognitive.AgentCognitiveEvent
import at.heinz.deviceai.cognitive.AgentToolApproval
import at.heinz.deviceai.cognitive.AgentToolApprovalProvider
import at.heinz.deviceai.learning.ContinuumMemoryLayers
import at.heinz.deviceai.learning.JournalStatus
import at.heinz.deviceai.routines.AgentMailbox
import at.heinz.deviceai.routines.AgentMessage
import at.heinz.deviceai.routines.AgentPriority
import at.heinz.deviceai.routines.RoutineBook
import at.heinz.deviceai.routines.RoutineClaim
import at.heinz.deviceai.routines.RoutineOutcome
import at.heinz.deviceai.routines.RoutineReport
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Integration seams (Jarvis Integration, 2026-11-03). Only adapters – no core logic of
 * Kern (#379), Gedächtnis (#371), Sicherheit (#372) or Routinen (#376) is changed here.
 *
 * Fluss: Ziel (WorkerBrief) → Plan/Handeln (ChiefJobs.agentJob = BoundedAgentOrchestrator, Tools nur im
 * Job-Scope) → Erinnern (LayersMemoryPort → ChiefMemory.recall, Hypothesen bleiben UNVERIFIED) →
 * Freigabe (ToolGate + S4 ExternalContentActionGuard im Orchestrator + AgentToolApprovalProvider) →
 * Prüfen (AgentEvaluator) → Bericht (WorkerReport) → Routine/Mailbox.
 */

/** Read-only adapter Gedächtnis → Kern, exactly as specified in docs/CHIEF-PATTERN.md. */
class LayersMemoryPort(private val layers: ContinuumMemoryLayers) : ChiefMemoryPort {
    override fun sharedFacts(): List<ChiefMemoryItem> =
        layers.shared().facts().map { ChiefMemoryItem(it.id, it.text, it.sinceMillis, confirmed = true) }

    override fun search(query: String, nowMillis: Long, limit: Int): List<ChiefMemoryItem> =
        layers.search(query, nowMillis, limit).hits
            .filter { it.entry.status != JournalStatus.REVOKED }
            .map { ChiefMemoryItem(it.entry.id, it.entry.text, it.entry.atMillis, it.confirmed) }
}

/** Maps orchestrator events to owner-visible TurnLifecycle milestones (deduplicated, rate limited there). */
object ChiefTurnBridge {
    fun observer(
        lifecycle: TurnLifecycle,
        show: (String) -> Unit,
        delegate: (AgentCognitiveEvent) -> Unit = {}
    ): (AgentCognitiveEvent) -> Unit = { event ->
        runCatching { delegate(event) }
        TurnLifecycle.milestoneOf(event.state)?.let { kind ->
            lifecycle.milestone(kind, "")?.let { line -> runCatching { show(line) } }
        }
    }
}

/**
 * Approval for work nobody is watching (routines, agent messages): read-only may run, everything
 * else is never auto-confirmed → the orchestrator stops honestly and the job reports BLOCKED.
 */
object UnattendedApproval : AgentToolApprovalProvider {
    override fun requestApproval(proposal: ToolProposal, risk: Risk): AgentToolApproval =
        AgentToolApproval(userConfirmed = risk == Risk.READ_ONLY, destructiveConfirmed = false)
}

object ChiefWiring {
    /** Worker pool for unattended routines: job-scoped tools, owner consent never implied. */
    fun unattendedPool(deps: ChiefJobDeps, maxConcurrent: Int = 1, maxQueued: Int = 4): ChiefWorkerPool =
        ChiefWorkerPool(maxConcurrent, maxQueued) { brief ->
            ChiefJobs.agentJob(brief, deps.copy(approvalProvider = UnattendedApproval))
        }
}

/**
 * Routinen (#376) → ChiefWorkerPool (#379). Only news is reported (INFO to the chief's mailbox);
 * a BLOCKED run (needs the owner's approval) is URGENT; failures advance the schedule and report once.
 */
class RoutineDispatcher(
    private val book: RoutineBook,
    private val pool: ChiefWorkerPool,
    private val mailbox: AgentMailbox,
    private val chiefId: String = "chief",
    private val clock: () -> Long = System::currentTimeMillis
) {
    private class Delivery(val report: RoutineReport, val message: AgentMessage?) {
        val attempting = AtomicBoolean()
    }
    private val inFlight = LinkedHashMap<String, RoutineClaim>()
    private val pending = LinkedHashMap<String, Delivery>()

    init { require(chiefId.length in 1..64) }

    fun tick(): Int = poll(book.claimDue(clock()))
    fun onEvent(name: String): Int = poll(book.claimTriggered(name))
    fun inFlight(): Set<String> = synchronized(book) { inFlight.keys.toSet() }

    private fun poll(claims: List<RoutineClaim>): Int {
        val waiting = synchronized(book) {
            discardRemovedClaims()
            pending.values.toList()
        }
        waiting.forEach(::deliver)
        return dispatch(claims)
    }

    private fun discardRemovedClaims() {
        inFlight.values.filterNot(book::isCurrent).forEach(::retire)
    }

    private fun dispatch(claims: List<RoutineClaim>): Int {
        var submitted = 0
        for (claim in claims) {
            val routine = claim.routine
            val admitted = synchronized(book) {
                discardRemovedClaims()
                when {
                    !book.isCurrent(claim) || inFlight[routine.id] === claim -> false
                    routine.id !in inFlight && inFlight.size >= book.capacity -> {
                        book.release(claim)
                        false
                    }
                    else -> {
                        inFlight[routine.id] = claim
                        pending.remove(routine.id)
                        true
                    }
                }
            }
            if (!admitted) continue
            val brief = runCatching {
                WorkerBrief("rt-" + claim.runId, routine.goal, routine.allowedTools)
            }.getOrNull()
            if (brief == null) {
                retire(claim)
                book.release(claim)
                continue
            }
            val accepted = runCatching { pool.submit(brief) { report -> onReport(claim, report) } }.getOrDefault(false)
            if (accepted) submitted++ else {
                retire(claim)
                book.release(claim)
            }
        }
        return submitted
    }

    private fun onReport(claim: RoutineClaim, report: WorkerReport) {
        val now = clock()
        val delivery = synchronized(book) {
            val routine = claim.routine
            if (inFlight[routine.id] !== claim || pending[routine.id] != null) return
            val prepared = book.prepareReport(claim, if (report.status == WorkerStatus.DONE) report.summary else "")
            if (prepared == null) {
                retire(claim)
                return
            }
            val message = when (report.status) {
                WorkerStatus.DONE -> (prepared.outcome as? RoutineOutcome.News)?.let {
                    message(claim, AgentPriority.INFO, it.summary, now)
                }
                WorkerStatus.BLOCKED -> message(claim, AgentPriority.URGENT,
                    "Routine ${routine.id} braucht deine Freigabe: " + report.summary.take(500), now)
                else -> message(claim, AgentPriority.INFO,
                    "Routine ${routine.id}: ${report.status} – " + report.summary.take(300), now)
            }
            Delivery(prepared, message).also { pending[routine.id] = it }
        }
        deliver(delivery)
    }

    private fun deliver(delivery: Delivery) {
        if (!delivery.attempting.compareAndSet(false, true)) return
        try {
            val now = clock()
            when (book.offerReport(delivery.report) {
                delivery.message?.let(mailbox::enqueue) ?: true
            }) {
                null -> retire(delivery.report.claim)
                false -> Unit
                true -> {
                    delivery.message?.let(mailbox::notifyAccepted)
                    book.acknowledgeReport(delivery.report, now)
                    retire(delivery.report.claim)
                }
            }
        } finally {
            delivery.attempting.set(false)
        }
    }

    private fun retire(claim: RoutineClaim) = synchronized(book) {
        if (inFlight[claim.routine.id] === claim) {
            inFlight.remove(claim.routine.id)
            pending.remove(claim.routine.id)
        }
    }

    private fun message(claim: RoutineClaim, priority: AgentPriority, body: String, now: Long): AgentMessage =
        AgentMessage("routine-${claim.routine.id}-$now-${claim.runId.replace("-", "")}",
            "routine:${claim.routine.id}".take(64), chiefId, priority, body.take(4_000))
}
