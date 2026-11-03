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
    private data class Entry(val routine: Routine, var nextAtMillis: Long, var lastDigest: String? = null)
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

    /** Scheduled routines whose time has come; each is due at most once until it reports. */
    @Synchronized fun due(nowMillis: Long): List<Routine> =
        entries.values.filter { it.routine.trigger is RoutineTrigger.Every && nowMillis >= it.nextAtMillis }.map { it.routine }

    @Synchronized fun triggered(event: String): List<Routine> =
        entries.values.filter { (it.routine.trigger as? RoutineTrigger.OnEvent)?.name == event }.map { it.routine }

    /** Records a run; returns News only if the normalized result differs from the last one. */
    @Synchronized fun report(id: String, result: String, nowMillis: Long): RoutineOutcome {
        val e = entries[id] ?: return RoutineOutcome.Unchanged(id)
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

class AgentMailbox(private val maxPerAgent: Int = 64, private val wake: (AgentMessage) -> Unit = {}) {
    private val queues = HashMap<String, ArrayDeque<AgentMessage>>()
    private val seen = LinkedHashSet<String>()

    fun send(message: AgentMessage): Boolean {
        synchronized(this) {
            if (message.id.isBlank() || message.to.isBlank() || message.body.isBlank() || message.body.length > 4_000) return false
            if (message.id in seen) return false
            val q = queues.getOrPut(message.to) { ArrayDeque() }
            if (q.size >= maxPerAgent) return false
            q.addLast(message); seen += message.id
            while (seen.size > 4_096) seen.remove(seen.first())
        }
        // Outside the lock; a failing wake hook never loses the queued message.
        if (message.priority == AgentPriority.URGENT) runCatching { wake(message) }
        return true
    }

    /** Urgent first, then arrival order. */
    @Synchronized fun drain(agent: String): List<AgentMessage> {
        val q = queues.remove(agent) ?: return emptyList()
        return q.sortedBy { if (it.priority == AgentPriority.URGENT) 0 else 1 }
    }

    @Synchronized fun pending(agent: String): Int = queues[agent]?.size ?: 0
}
