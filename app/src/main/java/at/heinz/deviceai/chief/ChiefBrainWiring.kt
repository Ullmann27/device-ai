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
import at.heinz.deviceai.routines.Routine
import at.heinz.deviceai.routines.RoutineBook
import at.heinz.deviceai.routines.RoutineOutcome
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val seq = AtomicLong()

    fun tick(): Int = dispatch(book.due(clock()))
    fun onEvent(name: String): Int = dispatch(book.triggered(name))
    fun inFlight(): Set<String> = inFlight.toSet()

    private fun dispatch(routines: List<Routine>): Int {
        var submitted = 0
        for (routine in routines) {
            if (!inFlight.add(routine.id)) {
                // The original dispatch still owns this claim: never release it here,
                // otherwise a second scheduler poll could submit a duplicate worker.
                continue
            }
            val brief = runCatching {
                WorkerBrief(briefId(routine.id), routine.goal, routine.allowedTools)
            }.getOrNull()
            if (brief == null) {
                inFlight.remove(routine.id)
                book.release(routine.id)
                continue
            }
            val accepted = runCatching { pool.submit(brief) { report -> onReport(routine, report) } }.getOrDefault(false)
            if (accepted) submitted++ else {
                inFlight.remove(routine.id)
                book.release(routine.id)
            }
        }
        return submitted
    }

    private fun briefId(routineId: String): String =
        ("rt-" + routineId.replace('.', '_')).take(48) + "-" + seq.incrementAndGet()

    private fun onReport(routine: Routine, report: WorkerReport) {
        try {
            val now = clock()
            when (report.status) {
                WorkerStatus.DONE -> {
                    val outcome = book.report(routine.id, report.summary, now)
                    if (outcome is RoutineOutcome.News) send(routine, AgentPriority.INFO, outcome.summary, now)
                }
                WorkerStatus.BLOCKED -> {
                    book.report(routine.id, "", now)
                    send(routine, AgentPriority.URGENT, "Routine ${routine.id} braucht deine Freigabe: " + report.summary.take(500), now)
                }
                else -> {
                    book.report(routine.id, "", now)
                    send(routine, AgentPriority.INFO, "Routine ${routine.id}: ${report.status} – " + report.summary.take(300), now)
                }
            }
        } finally {
            inFlight.remove(routine.id)
        }
    }

    private fun send(routine: Routine, priority: AgentPriority, body: String, now: Long) {
        if (body.isBlank()) return
        mailbox.send(AgentMessage("routine-${routine.id}-$now-${seq.incrementAndGet()}", "routine:${routine.id}", chiefId, priority, body.take(4_000)))
    }
}
