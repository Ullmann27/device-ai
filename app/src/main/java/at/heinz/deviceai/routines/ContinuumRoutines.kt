package at.heinz.deviceai.routines

import java.util.Collections
import java.util.UUID

/**
 * Chief-Muster Punkt 6 (Routinen) und 7 (Agent-zu-Agent-Nachrichten) – reiner, JVM-testbarer Kern.
 *
 * Routinen: gespeicherte Aufträge mit Zeitplan (≥ 15 min, Android-JobScheduler-Minimum) oder
 * Auslöser-Ereignis. Die Ausführung übernimmt später der Chief/Arbeiter-Pool von HAUPT
 * (WorkerBrief mit allowedTools) über das bestehende ToolGate; dieser Kern entscheidet nur,
 * WAS fällig ist und OB ein Ergebnis neu ist (nur Neues wird gemeldet).
 *
 * Nachrichten: URGENT ruft sofort den Weck-Hook, INFO wartet bis zum nächsten drain().
 * Inhalte sind Daten, nie Befehle oder Freigaben. Begrenzt, Duplikate verworfen.
 */
sealed interface RoutineTrigger {
    data class Every(val periodMillis: Long, val firstAtMillis: Long) : RoutineTrigger
    data class OnEvent(val name: String) : RoutineTrigger
}
data class Routine(val id: String, val goal: String, val trigger: RoutineTrigger, val allowedTools: Set<String> = emptySet())
sealed interface RoutineOutcome {
    data class News(val routineId: String, val summary: String) : RoutineOutcome
    data class Unchanged(val routineId: String) : RoutineOutcome
}

internal class RoutineClaim(val routine: Routine, val runId: String = UUID.randomUUID().toString())
internal class RoutineReport(
    val claim: RoutineClaim,
    val outcome: RoutineOutcome,
    val digest: String
)

class RoutineBook(private val maxRoutines: Int = 32) {
    private data class Entry(
        val routine: Routine,
        var nextAtMillis: Long,
        var lastDigest: String? = null,
        var claim: RoutineClaim? = null
    )
    private val entries = LinkedHashMap<String, Entry>()
    internal val capacity: Int get() = maxRoutines

    @Synchronized fun add(routine: Routine): Boolean {
        if (!ID.matches(routine.id) || routine.goal.isBlank() || routine.goal.length > 2_000) return false
        if (routine.id in entries || entries.size >= maxRoutines) return false
        val next = when (val t = routine.trigger) {
            is RoutineTrigger.Every -> if (t.periodMillis < MIN_PERIOD_MILLIS || t.firstAtMillis < 0) return false else t.firstAtMillis
            is RoutineTrigger.OnEvent -> if (!ID.matches(t.name)) return false else Long.MAX_VALUE
        }
        // Admission owns the approved scope; neither the caller's mutable set nor
        // a returned claim may add tools to this registered routine later.
        val admitted = routine.copy(allowedTools = Collections.unmodifiableSet(LinkedHashSet(routine.allowedTools)))
        entries[routine.id] = Entry(admitted, next)
        return true
    }

    @Synchronized fun remove(id: String): Boolean = entries.remove(id) != null

    /** Atomically claims due routines: repeated scheduler polls cannot dispatch the same run twice. */
    @Synchronized fun due(nowMillis: Long): List<Routine> =
        entries.values.filter {
            it.claim == null && it.routine.trigger is RoutineTrigger.Every && nowMillis >= it.nextAtMillis
        }.onEach { it.claim = RoutineClaim(it.routine) }.map { it.routine }

    /** Claim event-triggered routines once; the caller must report() or release() the claim. */
    @Synchronized fun triggered(event: String): List<Routine> =
        entries.values.filter {
            it.claim == null && (it.routine.trigger as? RoutineTrigger.OnEvent)?.name == event
        }.onEach { it.claim = RoutineClaim(it.routine) }.map { it.routine }

    /** Explicit retry gate after a failed/cancelled run; never auto-redeliver an unfinished job. */
    @Synchronized fun release(id: String): Boolean {
        val entry = entries[id] ?: return false
        if (entry.claim == null) return false
        entry.claim = null
        return true
    }

    @Synchronized internal fun claimed(routine: Routine): RoutineClaim? =
        entries[routine.id]?.takeIf { it.routine === routine }?.claim

    @Synchronized internal fun claimDue(nowMillis: Long): List<RoutineClaim> =
        due(nowMillis).mapNotNull(::claimed)

    @Synchronized internal fun claimTriggered(event: String): List<RoutineClaim> =
        triggered(event).mapNotNull(::claimed)

    @Synchronized internal fun isCurrent(claim: RoutineClaim): Boolean =
        entries[claim.routine.id]?.claim === claim

    @Synchronized internal fun release(claim: RoutineClaim): Boolean =
        if (isCurrent(claim)) release(claim.routine.id) else false

    @Synchronized internal fun prepareReport(claim: RoutineClaim, result: String): RoutineReport? {
        if (!isCurrent(claim)) return null
        val e = entries.getValue(claim.routine.id)
        val digest = result.trim().replace(Regex("\\s+"), " ")
        val outcome = if (digest.isEmpty() || digest == e.lastDigest) RoutineOutcome.Unchanged(e.routine.id)
            else RoutineOutcome.News(e.routine.id, digest.take(1_000))
        return RoutineReport(claim, outcome, digest)
    }

    // Queue insertion only; no callbacks.
    @Synchronized internal fun offerReport(report: RoutineReport, enqueue: () -> Boolean): Boolean? =
        if (isCurrent(report.claim)) enqueue() else null

    @Synchronized internal fun acknowledgeReport(report: RoutineReport, nowMillis: Long): Boolean {
        if (!isCurrent(report.claim)) return false
        val e = entries.getValue(report.claim.routine.id)
        e.claim = null
        (e.routine.trigger as? RoutineTrigger.Every)?.let {
            e.nextAtMillis = if (nowMillis > Long.MAX_VALUE - it.periodMillis) Long.MAX_VALUE
                else nowMillis + it.periodMillis
        }
        if (report.digest.isNotEmpty()) e.lastDigest = report.digest
        return true
    }

    /** Records a run; returns News only if the normalized result differs from the last one. */
    @Synchronized fun report(id: String, result: String, nowMillis: Long): RoutineOutcome {
        val claim = entries[id]?.claim ?: return RoutineOutcome.Unchanged(id)
        val report = prepareReport(claim, result) ?: return RoutineOutcome.Unchanged(id)
        acknowledgeReport(report, nowMillis)
        return report.outcome
    }

    companion object {
        const val MIN_PERIOD_MILLIS = 15 * 60_000L
        private val ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    }
}

enum class AgentPriority { URGENT, INFO }
data class AgentMessage(val id: String, val from: String, val to: String, val priority: AgentPriority, val body: String)

class AgentMailbox(
    private val maxPerAgent: Int = 64,
    private val maxRecipients: Int = 32,
    private val maxQueuedMessages: Int = 512,
    private val maxQueuedBytes: Int = 262_144,
    private val wake: (AgentMessage) -> Unit = {}
) {
    init {
        require(maxPerAgent in 1..256)
        require(maxRecipients in 1..128)
        require(maxQueuedMessages in 1..4_096)
        require(maxQueuedBytes in 4_096..2_097_152)
    }

    private val queues = HashMap<String, ArrayDeque<AgentMessage>>()
    private val seen = LinkedHashSet<String>()
    private var totalMessages = 0
    private var totalBytes = 0

    fun send(message: AgentMessage): Boolean {
        if (!enqueue(message)) return false
        notifyAccepted(message)
        return true
    }

    internal fun enqueue(message: AgentMessage): Boolean {
        synchronized(this) {
            if (message.id.length !in 1..128 || message.from.length !in 1..64 ||
                message.to.length !in 1..64 || message.body.isBlank() || message.body.length > 4_000) return false
            if (message.id in seen) return false
            if (message.to !in queues && queues.size >= maxRecipients) return false
            val size = message.body.toByteArray(Charsets.UTF_8).size
            if (totalMessages >= maxQueuedMessages || size > maxQueuedBytes - totalBytes) return false
            val q = queues.getOrPut(message.to) { ArrayDeque() }
            if (q.size >= maxPerAgent) return false
            q.addLast(message)
            totalMessages++
            totalBytes += size
            seen += message.id
            while (seen.size > 4_096) seen.remove(seen.first())
        }
        return true
    }

    internal fun notifyAccepted(message: AgentMessage) {
        // Outside the lock; the message remains in the queue even if the wake callback fails.
        if (message.priority == AgentPriority.URGENT) runCatching { wake(message) }
    }

    /** Urgent first, then arrival order. */
    @Synchronized fun drain(agent: String): List<AgentMessage> {
        val q = queues.remove(agent) ?: return emptyList()
        totalMessages -= q.size
        totalBytes -= q.sumOf { it.body.toByteArray(Charsets.UTF_8).size }
        return q.sortedBy { if (it.priority == AgentPriority.URGENT) 0 else 1 }
    }

    @Synchronized fun pending(agent: String): Int = queues[agent]?.size ?: 0
}
