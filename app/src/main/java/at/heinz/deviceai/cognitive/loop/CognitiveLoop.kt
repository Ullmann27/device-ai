package at.heinz.deviceai.cognitive.loop

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolRegistry
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The cognitive loop: GOAL → (PLAN → TOOL → VERIFY_RESULT → MEMORY_WRITE → NEXT_STEP)* → DONE.
 *
 * Runs synchronously on the caller's worker thread, once per instance. Deterministic: all times come from the
 * injected [clock]; ports receive the step deadline in [LoopBudget] and late results are discarded. No LLM here,
 * only ports. Tools run only via [LoopPorts.tools] (production: the existing ToolGate); a Risk.CONFIRM tool only
 * after an owner yes; DESTRUCTIVE / unknown tools are refused. Ordinary errors end with a calm [CognitiveLoopEvent.Aborted].
 */
class CognitiveLoop(
    private val turnId: String,
    private val ports: LoopPorts,
    private val limits: LoopLimits = LoopLimits(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val listeners: List<CognitiveLoopListener> = emptyList(),
    private val riskOf: (String) -> Risk = ToolRegistry::risk
) {
    private val cancelled = AtomicBoolean()
    private val started = AtomicBoolean()

    private class Abort(val reason: AbortReason, val note: String) : RuntimeException(note)

    /** Sticky. Takes effect at the next checkpoint; a running tool is cancelled through its port. */
    fun cancel() {
        cancelled.set(true)
        runCatching { ports.tools.cancel() }
    }

    fun run(goal: String): LoopOutcome {
        check(started.compareAndSet(false, true)) { "A CognitiveLoop runs only once; create a new one per goal." }
        val loopStart = clock()
        val totalDeadline = loopStart + limits.totalTimeoutMillis
        val history = mutableListOf<StepRecord>()
        var stepIndex = 0
        var completed = 0
        var stepDeadline = minOf(loopStart + limits.stepTimeoutMillis, totalDeadline)

        fun emit(e: CognitiveLoopEvent) { for (l in listeners) runCatching { l.onEvent(e) } }
        fun budget() = LoopBudget(stepDeadline) { cancelled.get() }
        fun checkpoint() {
            if (cancelled.get()) throw Abort(AbortReason.CANCELLED, "Abgebrochen, wie gewünscht.")
            if (clock() >= stepDeadline) throw Abort(AbortReason.TIMEOUT, "Das hat zu lange gedauert, ich habe aufgehört.")
        }

        return try {
            checkpoint()
            emit(CognitiveLoopEvent.GoalSet(turnId, clock(), 0, goal))
            while (true) {
                stepIndex++
                if (stepIndex > limits.maxSteps) throw Abort(AbortReason.BUDGET,
                    "Ich habe nach ${limits.maxSteps} Schritten aufgehört, ohne fertig zu werden.")
                val stepStart = clock()
                stepDeadline = minOf(stepStart + limits.stepTimeoutMillis, totalDeadline)
                checkpoint()

                when (val plan = ports.planner.plan(goal, history.toList(), emptyList(), budget())) {
                    is PlanOutcome.Done -> {
                        checkpoint()
                        emit(CognitiveLoopEvent.Finished(turnId, clock(), stepIndex, plan.summary))
                        return LoopOutcome.Finished(plan.summary, completed)
                    }
                    is PlanOutcome.Failed -> { checkpoint(); throw Abort(AbortReason.ERROR, "Das hat nicht geklappt: ${plan.reason.take(200)}") }
                    is PlanOutcome.Uncertain -> {
                        checkpoint()
                        emit(CognitiveLoopEvent.AskOwner(turnId, clock(), stepIndex, plan.question, plan.options))
                        when (val answer = ports.question.ask(turnId, plan.question, plan.options, budget())) {
                            is OwnerAnswer.Chosen -> {
                                checkpoint()
                                require(answer.option in plan.options) { "Antwort gehört nicht zu den angebotenen Optionen." }
                                history += StepRecord(stepIndex, null, null, null, answer.option)
                                completed++
                            }
                            OwnerAnswer.NoAnswer -> { checkpoint(); throw Abort(AbortReason.TIMEOUT, "Ohne Antwort mache ich nicht weiter.") }
                            OwnerAnswer.Cancelled -> throw Abort(AbortReason.CANCELLED, "Abgebrochen, wie gewünscht.")
                        }
                    }
                    is PlanOutcome.Plan -> {
                        checkpoint()
                        emit(CognitiveLoopEvent.PlanMade(turnId, clock(), stepIndex, plan.steps))
                        val step = plan.steps.first()
                        val outcome = runTool(step, stepIndex, ::emit, ::budget, ::checkpoint)
                        checkpoint()
                        emit(CognitiveLoopEvent.ToolResult(turnId, clock(), stepIndex, outcome.ok, outcome.summary))
                        when (outcome.status) {
                            ToolOutcomeStatus.REJECTED_BY_GATE -> throw Abort(AbortReason.GATE_DENIED,
                                "Das Werkzeug „${ToolRegistry.label(step.toolName)}“ wurde nicht freigegeben.")
                            ToolOutcomeStatus.CANCELLED -> throw Abort(AbortReason.CANCELLED, "Abgebrochen, wie gewünscht.")
                            else -> Unit
                        }
                        val verification = ports.verifier.verify(goal, step, outcome, budget())
                        checkpoint()
                        emit(CognitiveLoopEvent.ResultVerified(turnId, clock(), stepIndex, verification.passed, verification.reason))
                        val record = StepRecord(stepIndex, step, outcome, verification)
                        checkpoint()
                        emit(CognitiveLoopEvent.MemoryWritten(turnId, clock(), stepIndex, null))
                        history += record
                        completed++
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        } catch (abort: Abort) {
            emit(CognitiveLoopEvent.Aborted(turnId, clock(), stepIndex, abort.reason, abort.note))
            LoopOutcome.Aborted(abort.reason, abort.note, completed)
        } catch (failure: Exception) {
            val note = "Das hat nicht geklappt: ${(failure.message ?: failure.javaClass.simpleName).take(200)}"
            emit(CognitiveLoopEvent.Aborted(turnId, clock(), stepIndex, AbortReason.ERROR, note))
            LoopOutcome.Aborted(AbortReason.ERROR, note, completed)
        }
    }

    private fun runTool(
        step: PlannedStep, stepIndex: Int, emit: (CognitiveLoopEvent) -> Unit,
        budget: () -> LoopBudget, checkpoint: () -> Unit
    ): ToolOutcome {
        emit(CognitiveLoopEvent.ToolRequested(turnId, clock(), stepIndex, step.toolName))
        val known = ToolRegistry.get(step.toolName) != null
        val risk = if (known) riskOf(step.toolName) else Risk.DESTRUCTIVE
        val label = ToolRegistry.label(step.toolName)
        val confirmed = when (risk) {
            Risk.READ_ONLY -> false
            Risk.DESTRUCTIVE -> throw Abort(AbortReason.GATE_DENIED,
                if (known) "„$label“ führe ich im Denk-Kreislauf nie selbst aus." else "Dieses Werkzeug kenne ich nicht.")
            Risk.CONFIRM -> {
                val question = "Darf ich „$label“ ausführen?"
                emit(CognitiveLoopEvent.AskOwner(turnId, clock(), stepIndex, question, APPROVAL_OPTIONS))
                when (ports.approval.approve(turnId, step.toolName, question, budget())) {
                    OwnerDecision.APPROVED -> true
                    OwnerDecision.DENIED -> throw Abort(AbortReason.GATE_DENIED, "Ohne dein Ja führe ich „$label“ nicht aus.")
                    OwnerDecision.TIMEOUT -> throw Abort(AbortReason.TIMEOUT, "Ohne Antwort mache ich nicht weiter.")
                    OwnerDecision.CANCELLED -> throw Abort(AbortReason.CANCELLED, "Abgebrochen, wie gewünscht.")
                }
            }
        }
        checkpoint()
        return ports.tools.execute(turnId, "loop-$turnId-$stepIndex".take(128), step, confirmed, budget())
    }

    companion object {
        val APPROVAL_OPTIONS = listOf(LoopOption("ja", "Ja, ausführen"), LoopOption("nein", "Nein"))
    }
}
