package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentEvaluator
import at.heinz.deviceai.cognitive.AgentLimits
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.AgentToolApprovalProvider
import at.heinz.deviceai.cognitive.BoundedAgentOrchestrator
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.cognitive.ExperienceStore

/** Thrown by a job to report BLOCKED (honest stop, e.g. approval denied) instead of FAILED. */
class WorkerBlocked(reason: String) : RuntimeException(reason)

/** Everything a job needs; device tools arrive only here (job scope), never through a global registry. */
data class ChiefJobDeps(
    val model: AgentModel,
    val runner: ToolRunner,
    val policy: ToolPolicy,
    val memory: ChiefMemoryPort?,
    val evaluator: AgentEvaluator,
    val experienceStore: ExperienceStore,
    val approvalProvider: AgentToolApprovalProvider,
    val clock: () -> Long = System::currentTimeMillis,
    val limits: AgentLimits = AgentLimits(),
    val eventObserver: (at.heinz.deviceai.cognitive.AgentCognitiveEvent) -> Unit = {}
)

/**
 * The task loop as a ChiefWorkerPool job: the EXISTING BoundedAgentOrchestrator (plan→act→verify),
 * with the brief's tool allowlist enforced at model and runner level, and memories from the Gedächtnis port.
 */
object ChiefJobs {
    fun agentJob(brief: WorkerBrief, deps: ChiefJobDeps): WorkerJob = WorkerJob {
        val orchestrator = BoundedAgentOrchestrator(
            model = WorkerScope.model(brief, deps.model),
            memories = { deps.memory?.let { ChiefMemory.recall(it, brief.goal, deps.clock()) } ?: emptyList() },
            toolGate = ToolGate(deps.policy, WorkerScope.runner(brief, deps.runner)),
            evaluator = deps.evaluator,
            experienceStore = deps.experienceStore,
            approvalProvider = deps.approvalProvider,
            limits = deps.limits,
            clock = deps.clock,
            callIdPrefix = "worker-" + brief.id,
            cancellation = DeliberationCancellation(),
            evaluationConfirmsOutcome = false,
            eventObserver = deps.eventObserver
        )
        val result = orchestrator.run(brief.goal)
        result.blocker?.let { throw WorkerBlocked(it) }
        result.response
    }
}
