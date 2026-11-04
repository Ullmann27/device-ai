package at.heinz.deviceai.cognitive.loop

/*
 * Contract of the cognitive loop (owner: Jarvis Kern; docs/ARCHITEKTUR-KOGNITION.md).
 * Pure Kotlin: no Android, no LLM in the core. Planner, verifier, memory writer, tools and the owner are ports.
 * Safety: tools only through the existing ToolGate ([LoopToolPort], production: ToolGateLoopToolPort); every tool
 * with an external effect (Risk.CONFIRM) only after an explicit owner yes ([OwnerApprovalPort], production:
 * PillActionGateApprovalPort); DESTRUCTIVE or unknown tools are never run by the loop (GATE_DENIED).
 * Ordinary errors end calmly ([CognitiveLoopEvent.Aborted] with [CognitiveLoopEvent.Aborted.calmNote]); the loop
 * never raises an alarm (no AlarmRaised / SECURITY_FINDING).
 */

/** Steps of one loop iteration. NEXT_STEP re-plans with the history; DONE ends the loop. */
enum class LoopStep { GOAL, PLAN, TOOL, VERIFY_RESULT, MEMORY_WRITE, NEXT_STEP, DONE }

/** Answer option shown as a pill. Local type: the branch has no general ActionOption (only AnswerPill for documents). */
data class LoopOption(val id: String, val label: String) {
    init { require(id.isNotBlank() && id.length <= 64 && label.isNotBlank() && label.length <= 120) }
}

enum class AbortReason { CANCELLED, TIMEOUT, BUDGET, GATE_DENIED, ERROR }

sealed class CognitiveLoopEvent {
    abstract val turnId: String
    abstract val atMillis: Long
    /** 0 = goal; 1..n = loop iteration. */
    abstract val stepIndex: Int

    data class GoalSet(override val turnId: String, override val atMillis: Long, override val stepIndex: Int,
                       val goal: String) : CognitiveLoopEvent()
    data class PlanMade(override val turnId: String, override val atMillis: Long, override val stepIndex: Int,
                        val steps: List<PlannedStep>) : CognitiveLoopEvent()
    data class ToolRequested(override val turnId: String, override val atMillis: Long, override val stepIndex: Int,
                             val toolName: String) : CognitiveLoopEvent()
    data class ToolResult(override val turnId: String, override val atMillis: Long, override val stepIndex: Int,
                          val ok: Boolean, val summary: String) : CognitiveLoopEvent()
    data class ResultVerified(override val turnId: String, override val atMillis: Long, override val stepIndex: Int,
                              val passed: Boolean, val reason: String) : CognitiveLoopEvent()
    data class MemoryWritten(override val turnId: String, override val atMillis: Long, override val stepIndex: Int,
                             val entryId: String?) : CognitiveLoopEvent()
    data class AskOwner(override val turnId: String, override val atMillis: Long, override val stepIndex: Int,
                        val question: String, val options: List<LoopOption>) : CognitiveLoopEvent()
    /** Calm end. [calmNote] is the short German notice for the owner (CalmNotice does not exist yet). */
    data class Aborted(override val turnId: String, override val atMillis: Long, override val stepIndex: Int,
                       val reason: AbortReason, val calmNote: String) : CognitiveLoopEvent()
    data class Finished(override val turnId: String, override val atMillis: Long, override val stepIndex: Int,
                        val summary: String) : CognitiveLoopEvent()
}

/** Called on the loop thread. Failures of one listener are isolated. */
fun interface CognitiveLoopListener { fun onEvent(event: CognitiveLoopEvent) }

/** One planned tool step. [toolName] must be a ToolRegistry tool; arguments are checked by ToolProposal/ToolGate. */
data class PlannedStep(val description: String, val toolName: String, val arguments: Map<String, Any?> = emptyMap())

/** What happened in one finished iteration; the planner sees the full history. */
data class StepRecord(
    val stepIndex: Int,
    val step: PlannedStep?,
    val tool: ToolOutcome?,
    val verification: Verification?,
    val ownerAnswer: LoopOption? = null
)

sealed class PlanOutcome {
    /** Remaining plan; the loop runs only the first step, then re-plans (NEXT_STEP). */
    data class Plan(val steps: List<PlannedStep>) : PlanOutcome() { init { require(steps.isNotEmpty()) } }
    data class Done(val summary: String) : PlanOutcome()
    /** The planner is uncertain: ask the owner (pills), the answer goes into the history. */
    data class Uncertain(val question: String, val options: List<LoopOption>) : PlanOutcome() {
        init { require(options.isNotEmpty()) }
    }
    data class Failed(val reason: String) : PlanOutcome()
}

/** Absolute deadline (injected clock) and cancellation that every blocking port must honour. */
class LoopBudget(val deadlineAtMillis: Long, private val cancelledFlag: () -> Boolean) {
    fun cancelled(): Boolean = cancelledFlag()
}

fun interface LoopPlanner {
    fun plan(goal: String, history: List<StepRecord>, budget: LoopBudget): PlanOutcome
}

enum class ToolOutcomeStatus { SUCCEEDED, FAILED, TIMED_OUT, CANCELLED, REJECTED_BY_GATE }

data class ToolOutcome(val status: ToolOutcomeStatus, val summary: String) {
    val ok: Boolean get() = status == ToolOutcomeStatus.SUCCEEDED
}

/** Only implementation in production: ToolGateLoopToolPort (existing ToolGate). */
interface LoopToolPort {
    fun execute(turnId: String, callId: String, step: PlannedStep, ownerConfirmed: Boolean, budget: LoopBudget): ToolOutcome
    fun cancel() = Unit
}

data class Verification(val passed: Boolean, val reason: String)

fun interface ResultVerifier {
    fun verify(goal: String, step: PlannedStep, outcome: ToolOutcome, budget: LoopBudget): Verification
}

/** Writes one experience entry; returns its entry id or null when nothing was stored. */
fun interface LoopMemoryWriter {
    fun write(goal: String, record: StepRecord, budget: LoopBudget): String?
}

enum class OwnerDecision { APPROVED, DENIED, TIMEOUT, CANCELLED }

/** Owner yes for a tool with external effect. Production: PillActionGateApprovalPort. */
fun interface OwnerApprovalPort {
    fun approve(turnId: String, toolName: String, question: String, budget: LoopBudget): OwnerDecision
}

sealed class OwnerAnswer {
    data class Chosen(val option: LoopOption) : OwnerAnswer()
    data object NoAnswer : OwnerAnswer()
    data object Cancelled : OwnerAnswer()
}

/** Question with pills when the planner is uncertain. No production implementation yet (open). */
fun interface OwnerQuestionPort {
    fun ask(turnId: String, question: String, options: List<LoopOption>, budget: LoopBudget): OwnerAnswer
}

/** Limits in milliseconds of the injected clock. */
data class LoopLimits(val maxSteps: Int = 8, val stepTimeoutMillis: Long = 60_000, val totalTimeoutMillis: Long = 300_000) {
    init { require(maxSteps in 1..64 && stepTimeoutMillis in 1..600_000 && totalTimeoutMillis in 1..3_600_000) }
}

data class LoopPorts(
    val planner: LoopPlanner,
    val tools: LoopToolPort,
    val verifier: ResultVerifier,
    val memory: LoopMemoryWriter,
    val approval: OwnerApprovalPort,
    val question: OwnerQuestionPort
)

sealed class LoopOutcome {
    data class Finished(val summary: String, val steps: Int) : LoopOutcome()
    data class Aborted(val reason: AbortReason, val calmNote: String, val steps: Int) : LoopOutcome()
}
