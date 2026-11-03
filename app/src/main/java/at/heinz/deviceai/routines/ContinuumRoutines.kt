package at.heinz.deviceai.routines

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

class RoutineBook(private val maxRoutines: Int = 32) {
    private data class Entry(
        val routine: Routine,
        var nextAtMillis: Long,
        var lastDigest: String? = null,
        var inFlight: Boolean = false
    )
    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized fun add(routine: Routine): Boolean {
        if (!ID.matches(routine.id) || routine.goal.isBlank() || routine.goal.length > 2_000) return false
        if (routine.id in entries || entries.size >= maxRoutines) return false
        val next = when (val t = routine.trigger) {
            is RoutineTrigger.Every -> if (t.periodMillis < MIN_PERIOD_MILLIS || t.firstAtMillis < 0) return false else t.firstAtMillis
            is RoutineTrigger.OnEvent -> if (!ID.matches(t.name)) return false else Long.MAX_VALUE
        }
        entries[routine.id] = Entry(routine, next)
        return true
    }

    @Synchronized fun remove(id: String): Boolean = entries.remove(id) != null

    /** Atomically claims due routines: repeated scheduler polls cannot dispatch the same run twice. */
    @Synchronized fun due(nowMillis: Long): List<Routine> =
        entries.values.filter {
            !it.inFlight && it.routine.trigger is RoutineTrigger.Every && nowMillis >= it.nextAtMillis
        }.onEach { it.inFlight = true }.map { it.routine }

    /** Claim event-triggered routines once; the caller must report() or release() the claim. */
    @Synchronized fun triggered(event: String): List<Routine> =
        entries.values.filter {
            !it.inFlight && (it.routine.trigger as? RoutineTrigger.OnEvent)?.name == event
        }.onEach { it.inFlight = true }.map { it.routine }

    /** Explicit retry gate after a failed/cancelled run; never auto-redeliver an unfinished job. */
    @Synchronized fun release(id: String): Boolean {
        val entry = entries[id] ?: return false
        if (!entry.inFlight) return false
        entry.inFlight = false
        return true
    }

    /** Records a run; returns News only if the normalized result differs from the last one. */
    @Synchronized fun report(id: String, result: String, nowMillis: Long): RoutineOutcome {
        val e = entries[id] ?: return RoutineOutcome.Unchanged(id)
        if (!e.inFlight) return RoutineOutcome.Unchanged(id)
        e.inFlight = false
        (e.routine.trigger as? RoutineTrigger.Every)?.let { e.nextAtMillis = nowMillis + it.periodMillis }
        val digest = result.trim().replace(Regex("\\s+"), " ")
        if (digest.isEmpty() || digest == e.lastDigest) return RoutineOutcome.Unchanged(id)
        e.lastDigest = digest
        return RoutineOutcome.News(id, digest.take(1_000))
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
        // Outside the lock; the message remains in the queue even if the wake callback fails.
        if (message.priority == AgentPriority.URGENT) runCatching { wake(message) }
        return true
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
