package at.heinz.deviceai.cognitive.loop

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.cockpit.AnswerPillKind
import at.heinz.deviceai.cockpit.PillActionGate

/** RED stub: LoopToolPort over the existing ToolGate. */
class ToolGateLoopToolPort(private val gate: ToolGate) : LoopToolPort {
    override fun execute(turnId: String, callId: String, step: PlannedStep, ownerConfirmed: Boolean, budget: LoopBudget): ToolOutcome =
        TODO("ToolGateLoopToolPort")
}

/** RED stub: owner yes via the existing PillActionGate. */
class PillActionGateApprovalPort(
    private val gate: PillActionGate,
    private val pillKind: AnswerPillKind,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep
) : OwnerApprovalPort {
    override fun approve(turnId: String, toolName: String, question: String, budget: LoopBudget): OwnerDecision =
        TODO("PillActionGateApprovalPort")
}
