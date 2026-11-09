package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentEvaluator
import at.heinz.deviceai.cognitive.AgentLimits
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.AgentModelOutput
import at.heinz.deviceai.cognitive.AgentReflection
import at.heinz.deviceai.cognitive.AgentToolObservation
import at.heinz.deviceai.cognitive.CancellableAgentModel
import at.heinz.deviceai.cognitive.MemoryRecord
import java.util.concurrent.atomic.AtomicReference
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
    val eventObserver: (at.heinz.deviceai.cognitive.AgentCognitiveEvent) -> Unit = {},
    // Only an independently allocated model/session may receive cancelGeneration().
    // A shared LiteRT-LM runtime may have other Chief/worker turns in flight and sticky cancellation state.
    val modelOwnedByJob: Boolean = false,
    val onResult: (at.heinz.deviceai.cognitive.AgentRunResult) -> Unit = {}
)

/**
 * The task loop as a ChiefWorkerPool job: the EXISTING BoundedAgentOrchestrator (plan→act→verify),
 * with the brief's tool allowlist enforced at model and runner level, and memories from the Gedächtnis port.
 */
object ChiefJobs {
    fun agentJob(brief: WorkerBrief, deps: ChiefJobDeps): WorkerJob {
        val cancellation = DeliberationCancellation()
        val active = AtomicReference<BoundedAgentOrchestrator?>()
        val scoped = WorkerScope.model(brief, deps.model)
        val model = object : CancellableAgentModel {
            override fun generate(
                task: String, memories: List<MemoryRecord>, reflection: AgentReflection?,
                toolObservations: List<AgentToolObservation>
            ): AgentModelOutput = scoped.generate(task, memories, reflection, toolObservations)

            override fun cancelGeneration() {
                if (deps.modelOwnedByJob)
                    (deps.model as? CancellableAgentModel)?.cancelGeneration()
            }
        }
        return object : WorkerJob {
            override fun run(): String {
                cancellation.throwIfCancelled()
                val orchestrator = BoundedAgentOrchestrator(
                    model = model,
                    memories = { deps.memory?.let { ChiefMemory.recall(it, brief.goal, deps.clock()) } ?: emptyList() },
                    toolGate = ToolGate(deps.policy, WorkerScope.runner(brief, deps.runner)),
                    evaluator = deps.evaluator,
                    experienceStore = deps.experienceStore,
                    approvalProvider = deps.approvalProvider,
                    limits = deps.limits,
                    clock = deps.clock,
                    callIdPrefix = "worker-" + brief.id,
                    cancellation = cancellation,
                    evaluationConfirmsOutcome = false,
                    eventObserver = deps.eventObserver
                )
                active.set(orchestrator)
                try {
                    // A cancellation between construction and publication must never start a model call.
                    cancellation.throwIfCancelled()
                    val result = orchestrator.run(brief.goal)
                    result.blocker?.let { throw WorkerBlocked(it) }
                    deps.onResult(result)
                    return result.response
                } finally {
                    active.compareAndSet(orchestrator, null)
                }
            }

            override fun cancel() {
                // Cancel the shared token first, then the live model generation AND ToolGate.
                cancellation.cancel()
                active.get()?.cancel()
            }
        }
    }
}
