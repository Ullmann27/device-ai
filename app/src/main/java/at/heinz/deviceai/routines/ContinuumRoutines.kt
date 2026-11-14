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

class RoutineBook(
    private val maxRoutines: Int = 32,
    private val stateStore: RoutineStateStore? = null
) {
    private data class Entry(
        val routine: Routine,
        val nextAtMillis: Long,
        val lastDigest: String? = null,
        val approval: RoutineOwnerApproval? = null,
        val claim: RoutineClaim? = null,
        val report: StoredRoutineReport? = null,
        val lastResult: String? = null,
        val lastResultAtMillis: Long = 0
    )
    private var entries = LinkedHashMap<String, Entry>()
    private var outbox = emptyList<StoredRoutineReport>()
    private var revision = 0L
    private var restoredClaims = emptyList<RoutineClaim>()
    internal val capacity: Int get() = maxRoutines
    internal val persistent: Boolean get() = stateStore != null

    init {
        require(maxRoutines in 1..RoutineStoredState.MAX_ROUTINES)
        stateStore?.load()?.let { state ->
            state.validate()
            require(state.entries.size <= maxRoutines)
            revision = state.revision
            outbox = state.outbox
            for (stored in state.entries) {
                val routine = freeze(stored.routine)
                entries[routine.id] = Entry(routine, stored.nextAtMillis, stored.lastDigest,
                    stored.approval, stored.runId?.let { RoutineClaim(routine, it) }, stored.report,
                    stored.lastResult, stored.lastResultAtMillis)
            }
            restoredClaims = entries.values.mapNotNull { it.claim }
        }
    }

    @Synchronized fun add(routine: Routine): Boolean = admit(routine, null)

    @Synchronized fun addApproved(routine: Routine, approval: RoutineOwnerApproval): Boolean =
        admit(routine, approval)

    private fun admit(routine: Routine, approval: RoutineOwnerApproval?): Boolean {
        if (!ID.matches(routine.id) || routine.goal.isBlank() || routine.goal.length > 2_000) return false
        if (routine.id in entries || entries.size >= maxRoutines) return false
        val next = when (val t = routine.trigger) {
            is RoutineTrigger.Every -> if (t.periodMillis < MIN_PERIOD_MILLIS || t.firstAtMillis < 0) return false else t.firstAtMillis
            is RoutineTrigger.OnEvent -> if (!ID.matches(t.name)) return false else Long.MAX_VALUE
        }
        val admitted = freeze(routine)
        if (approval != null && !approval.matches(admitted) || persistent && approval == null) return false
        val changed = LinkedHashMap(entries)
        changed[admitted.id] = Entry(admitted, next, approval = approval)
        commit(changed)
        return true
    }

    @Synchronized fun remove(id: String): Boolean {
        if (id !in entries) return false
        val changed = LinkedHashMap(entries).also { it.remove(id) }
        commit(changed, outbox.filterNot { it.routineId == id })
        return true
    }

    @Synchronized fun removeAll(): List<String> {
        val removed = entries.keys.toList()
        if (removed.isNotEmpty() || outbox.isNotEmpty()) commit(LinkedHashMap(), emptyList())
        return removed
    }

    @Synchronized fun registrations(): List<StoredRoutine> {
        requireCurrentStore()
        return entries.values.mapNotNull { e -> e.approval?.let { stored(e, it) } }
    }

    @Synchronized fun due(nowMillis: Long): List<Routine> = claim {
        it.routine.trigger is RoutineTrigger.Every && nowMillis >= it.nextAtMillis
    }

    @Synchronized fun triggered(event: String): List<Routine> = claim {
        (it.routine.trigger as? RoutineTrigger.OnEvent)?.name == event
    }

    private fun claim(matches: (Entry) -> Boolean): List<Routine> {
        requireCurrentStore()
        val slots = if (persistent) (RoutineStoredState.MAX_OUTBOX - outbox.size -
            entries.values.count { it.claim != null }).coerceAtLeast(0) else maxRoutines
        val due = entries.values.filter { it.claim == null && matches(it) }.take(slots)
        if (due.isEmpty()) return emptyList()
        val changed = LinkedHashMap(entries)
        due.forEach { changed[it.routine.id] = it.copy(claim = RoutineClaim(it.routine)) }
        commit(changed)
        return due.map { it.routine }
    }

    @Synchronized fun release(id: String): Boolean {
        val entry = entries[id] ?: return false
        if (entry.claim == null || entry.report != null) return false
        commit(LinkedHashMap(entries).also { it[id] = entry.copy(claim = null) })
        return true
    }

    @Synchronized internal fun claimed(routine: Routine): RoutineClaim? =
        entries[routine.id]?.takeIf { it.routine === routine }?.claim

    @Synchronized internal fun claimDue(nowMillis: Long): List<RoutineClaim> =
        due(nowMillis).mapNotNull(::claimed)

    @Synchronized internal fun claimTriggered(event: String): List<RoutineClaim> =
        triggered(event).mapNotNull(::claimed)

    @Synchronized internal fun isCurrent(claim: RoutineClaim): Boolean =
        entries[claim.routine.id]?.claim === claim &&
            (stateStore == null || stateStore.load().revision == revision)

    @Synchronized internal fun release(claim: RoutineClaim): Boolean =
        if (isCurrent(claim)) release(claim.routine.id) else false

    @Synchronized fun authorizedRun(runId: String): StoredRoutine? {
        requireCurrentStore()
        val entry = entries.values.singleOrNull { it.claim?.runId == runId && it.report == null } ?: return null
        val approval = entry.approval?.takeIf { it.matches(entry.routine) } ?: return null
        return stored(entry, approval)
    }

    @Synchronized internal fun takeRestoredClaims(): List<Pair<RoutineClaim, StoredRoutineReport?>> =
        restoredClaims.map { it to entries[it.routine.id]?.report }.also { restoredClaims = emptyList() }

    @Synchronized internal fun prepareReport(claim: RoutineClaim, result: String): RoutineReport? {
        if (entries[claim.routine.id]?.claim !== claim) return null
        val entry = entries.getValue(claim.routine.id)
        val digest = result.trim().replace(Regex("\\s+"), " ")
        val outcome = if (digest.isEmpty() || digest == entry.lastDigest) RoutineOutcome.Unchanged(entry.routine.id)
            else RoutineOutcome.News(entry.routine.id, digest.take(1_000))
        return RoutineReport(claim, outcome, digest)
    }

    @Synchronized internal fun retainReport(
        report: RoutineReport, message: AgentMessage?, result: String, atMillis: Long
    ): Boolean {
        if (!isCurrent(report.claim)) return false
        if (!persistent) return true
        val entry = entries.getValue(report.claim.routine.id)
        val retained = StoredRoutineReport(entry.routine.id, report.claim.runId, report.digest,
            message, result, atMillis)
        entry.report?.let { return it.copy(chatAppend = null, chatDelivered = false) == retained }
        commit(LinkedHashMap(entries).also { it[entry.routine.id] = entry.copy(report = retained) })
        return true
    }

    @Synchronized internal fun chatDelivered(report: RoutineReport): Boolean =
        entries[report.claim.routine.id]?.takeIf { it.claim === report.claim }?.report?.chatDelivered == true

    // Queue insertion only; no callbacks.
    @Synchronized internal fun offerReport(report: RoutineReport, enqueue: () -> Boolean): Boolean? =
        if (isCurrent(report.claim)) enqueue() else null

    @Synchronized internal fun acknowledgeReport(report: RoutineReport, nowMillis: Long): Boolean {
        if (!isCurrent(report.claim)) return false
        val e = entries.getValue(report.claim.routine.id)
        if (persistent && e.report?.digest != report.digest) return false
        val next = (e.routine.trigger as? RoutineTrigger.Every)?.let {
            if (nowMillis > Long.MAX_VALUE - it.periodMillis) Long.MAX_VALUE else nowMillis + it.periodMillis
        } ?: e.nextAtMillis
        val finished = e.copy(claim = null, report = null, nextAtMillis = next,
            lastDigest = report.digest.takeIf { it.isNotEmpty() } ?: e.lastDigest,
            lastResult = e.report?.result ?: e.lastResult,
            lastResultAtMillis = e.report?.atMillis ?: e.lastResultAtMillis)
        val queued = e.report?.takeIf { it.message != null && !it.chatDelivered }
        commit(LinkedHashMap(entries).also { it[e.routine.id] = finished },
            if (queued == null) outbox else outbox + queued)
        return true
    }

    @Synchronized fun pendingChatReports(): List<StoredRoutineReport> {
        requireCurrentStore()
        return (outbox + entries.values.mapNotNull { it.report }).filter { it.message != null && !it.chatDelivered }
    }

    @Synchronized internal fun offerChatReport(report: StoredRoutineReport, enqueue: () -> Boolean): Boolean? =
        if (report in pendingChatReports()) enqueue() else null

    @Synchronized fun prepareChatAppend(messageId: String, append: RoutineChatAppend): Boolean =
        updateChatReport(messageId) { current ->
            if (current.chatAppend != null && current.chatAppend != append) null
            else current.copy(chatAppend = append)
        }

    @Synchronized fun acknowledgeChat(messageId: String): Boolean = updateChatReport(messageId) {
        if (it.chatAppend == null) null else it.copy(chatDelivered = true)
    }

    private fun updateChatReport(messageId: String, update: (StoredRoutineReport) -> StoredRoutineReport?): Boolean {
        val inEntry = entries.values.firstOrNull { it.report?.message?.id == messageId }
        if (inEntry != null) {
            val changed = update(inEntry.report!!) ?: return false
            commit(LinkedHashMap(entries).also { it[inEntry.routine.id] = inEntry.copy(report = changed) })
            return true
        }
        val index = outbox.indexOfFirst { it.message?.id == messageId }
        if (index < 0) return false
        val changed = update(outbox[index]) ?: return false
        commit(entries, outbox.toMutableList().also {
            if (changed.chatDelivered) it.removeAt(index) else it[index] = changed
        })
        return true
    }

    @Synchronized fun report(id: String, result: String, nowMillis: Long): RoutineOutcome {
        check(!persistent) { "Persistent routines require a retained worker report." }
        val claim = entries[id]?.claim ?: return RoutineOutcome.Unchanged(id)
        val report = prepareReport(claim, result) ?: return RoutineOutcome.Unchanged(id)
        acknowledgeReport(report, nowMillis)
        return report.outcome
    }

    private fun requireCurrentStore() {
        check(stateStore == null || stateStore.load().revision == revision) { "Routine state changed; reload required." }
    }

    private fun commit(changed: Map<String, Entry>, messages: List<StoredRoutineReport> = outbox) {
        stateStore?.let { store ->
            check(revision < Long.MAX_VALUE)
            val next = RoutineStoredState(revision + 1, changed.values.map {
                stored(it, requireNotNull(it.approval))
            }, messages.toList())
            store.replace(revision, next)
            revision = next.revision
        }
        entries = LinkedHashMap(changed)
        outbox = messages.toList()
    }

    private fun stored(e: Entry, approval: RoutineOwnerApproval) = StoredRoutine(e.routine, approval,
        e.nextAtMillis, e.lastDigest, e.lastResult, e.lastResultAtMillis, e.claim?.runId, e.report)

    private fun freeze(routine: Routine): Routine = routine.copy(
        allowedTools = Collections.unmodifiableSet(LinkedHashSet(routine.allowedTools)))

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

    @Synchronized fun peek(agent: String): List<AgentMessage> =
        queues[agent]?.sortedBy { if (it.priority == AgentPriority.URGENT) 0 else 1 }.orEmpty()

    @Synchronized fun acknowledge(agent: String, messageId: String): Boolean {
        val queue = queues[agent] ?: return false
        val message = queue.firstOrNull { it.id == messageId } ?: return false
        queue.remove(message)
        totalMessages--
        totalBytes -= message.body.toByteArray(Charsets.UTF_8).size
        if (queue.isEmpty()) queues.remove(agent)
        return true
    }
}
