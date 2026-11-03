package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentModel

data class WorkerBrief(val id: String, val goal: String, val allowedTools: Set<String>, val deadlineMillis: Long = 120_000)
enum class WorkerStatus { DONE, BLOCKED, FAILED, TIMED_OUT, CANCELLED }
data class WorkerReport(val id: String, val status: WorkerStatus, val summary: String, val durationMillis: Long)
fun interface WorkerJob { fun run(): String }

/** RED stub. */
class ChiefWorkerPool(private val maxConcurrent: Int = 2, private val maxQueued: Int = 4, private val factory: (WorkerBrief) -> WorkerJob) {
    fun submit(brief: WorkerBrief, onReport: (WorkerReport) -> Unit): Boolean = false
    fun cancel(id: String): Boolean = false
    fun awaitIdle(timeoutMillis: Long): Boolean = true
    fun shutdown() = Unit
}

object WorkerScope {
    fun runner(brief: WorkerBrief, delegate: ToolRunner): ToolRunner = delegate
    fun model(brief: WorkerBrief, delegate: AgentModel): AgentModel = delegate
}
