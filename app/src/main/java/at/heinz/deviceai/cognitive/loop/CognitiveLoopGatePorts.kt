package at.heinz.deviceai.cognitive.loop

import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.cockpit.AnswerPill
import at.heinz.deviceai.cockpit.AnswerPillKind
import at.heinz.deviceai.cockpit.PillActionGate
import java.util.concurrent.atomic.AtomicInteger

/**
 * [LoopToolPort] over the existing [ToolGate] (AgentToolLayer.kt): ToolProposal validation, policy, budget,
 * receipts and timeouts stay in the gate. [ownerConfirmed] becomes `userConfirmed`; `destructiveConfirmed` is
 * always false, so DESTRUCTIVE tools can never run through the loop. ToolGate.cancel() is sticky: one gate per loop.
 */
class ToolGateLoopToolPort(private val gate: ToolGate) : LoopToolPort {
    override fun execute(turnId: String, callId: String, step: PlannedStep, ownerConfirmed: Boolean, budget: LoopBudget): ToolOutcome {
        val proposal = ToolProposal(step.toolName, step.arguments)
        proposal.validate()?.let { return ToolOutcome(ToolOutcomeStatus.REJECTED_BY_GATE, it) }
        val result = gate.execute(proposal.invocation(callId), userConfirmed = ownerConfirmed, destructiveConfirmed = false)
        val status = when (result.record.status) {
            ToolExecutionStatus.SUCCEEDED -> ToolOutcomeStatus.SUCCEEDED
            ToolExecutionStatus.REJECTED -> ToolOutcomeStatus.REJECTED_BY_GATE
            ToolExecutionStatus.FAILED -> ToolOutcomeStatus.FAILED
            ToolExecutionStatus.TIMED_OUT -> ToolOutcomeStatus.TIMED_OUT
            ToolExecutionStatus.CANCELLED -> ToolOutcomeStatus.CANCELLED
        }
        val summary = if (status == ToolOutcomeStatus.SUCCEEDED) result.output.orEmpty() else result.record.decisionReason
        return ToolOutcome(status, summary.take(MAX_SUMMARY))
    }

    override fun cancel() = gate.cancel()

    private companion object { const val MAX_SUMMARY = 2_000 }
}

/**
 * Owner yes through the existing [PillActionGate] (the pill only requests; the owner decides). Waits until the
 * step deadline of [LoopBudget] on the injected clock; late decisions are ignored. [pillKind] is chosen by the
 * wiring side: PillActionGate knows only document pills so far (open point for Jarvis Sicherheit).
 */
class PillActionGateApprovalPort(
    private val gate: PillActionGate,
    private val pillKind: AnswerPillKind,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep
) : OwnerApprovalPort {
    override fun approve(turnId: String, toolName: String, question: String, budget: LoopBudget): OwnerDecision {
        if (budget.cancelled()) return OwnerDecision.CANCELLED
        val decision = AtomicInteger(OPEN)
        gate.request(AnswerPill(pillKind, toolName, true, null), turnId) { decision.compareAndSet(OPEN, if (it) YES else NO) }
        while (true) {
            when (decision.get()) { YES -> return OwnerDecision.APPROVED; NO -> return OwnerDecision.DENIED }
            if (budget.cancelled() && decision.compareAndSet(OPEN, CLOSED)) return OwnerDecision.CANCELLED
            // Close the window: a decision arriving after the deadline must not count.
            if (clock() >= budget.deadlineAtMillis && decision.compareAndSet(OPEN, CLOSED)) return OwnerDecision.TIMEOUT
            sleep(WAIT_SLICE_MILLIS)
        }
    }

    private companion object { const val WAIT_SLICE_MILLIS = 10L; const val OPEN = 0; const val YES = 1; const val NO = 2; const val CLOSED = 3 }
}
