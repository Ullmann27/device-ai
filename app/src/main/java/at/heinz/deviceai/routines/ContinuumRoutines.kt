package at.heinz.deviceai.routines

/** RED stub (Chief-Muster Punkt 6/7). */
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
    fun add(routine: Routine): Boolean = false
    fun remove(id: String): Boolean = false
    fun due(nowMillis: Long): List<Routine> = emptyList()
    fun triggered(event: String): List<Routine> = emptyList()
    fun report(id: String, result: String, nowMillis: Long): RoutineOutcome = RoutineOutcome.News(id, result)
}

enum class AgentPriority { URGENT, INFO }
data class AgentMessage(val id: String, val from: String, val to: String, val priority: AgentPriority, val body: String)

class AgentMailbox(private val maxPerAgent: Int = 64, private val wake: (AgentMessage) -> Unit = {}) {
    fun send(message: AgentMessage): Boolean = false
    fun drain(agent: String): List<AgentMessage> = emptyList()
    fun pending(agent: String): Int = 0
}
