package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentEvaluator
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.AgentToolApprovalProvider
import at.heinz.deviceai.cognitive.ExperienceStore

class WorkerBlocked(reason: String) : RuntimeException(reason)
data class ChiefJobDeps(val model: AgentModel, val runner: ToolRunner, val policy: ToolPolicy, val memory: ChiefMemoryPort?,
    val evaluator: AgentEvaluator, val experienceStore: ExperienceStore, val approvalProvider: AgentToolApprovalProvider,
    val clock: () -> Long = System::currentTimeMillis)
/** RED stub. */
object ChiefJobs { fun agentJob(brief: WorkerBrief, deps: ChiefJobDeps): WorkerJob = WorkerJob { error("stub") } }
