package at.heinz.deviceai.cognitive.loop

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cockpit.AnswerPill
import at.heinz.deviceai.cockpit.AnswerPillKind
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.PillActionGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CognitiveLoopGatePortsTest {
    private val budget = LoopBudget(Long.MAX_VALUE) { false }

    @Test fun readOnlyToolRunsThroughTheRealToolGate() {
        val ran = mutableListOf<String>()
        val port = ToolGateLoopToolPort(ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { ran += it.name; "Akku 85 %" }))
        val out = port.execute("t", "loop-t-1", PlannedStep("Akku", "get_battery_info"), false, budget)
        assertEquals(ToolOutcome(ToolOutcomeStatus.SUCCEEDED, "Akku 85 %"), out)
        assertEquals(listOf("get_battery_info"), ran)
    }

    @Test fun gateRejectionsAreReportedAndNothingRuns() {
        val ran = mutableListOf<String>()
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = false), ToolRunner { ran += it.name; "x" })
        val port = ToolGateLoopToolPort(gate)
        assertEquals(ToolOutcomeStatus.REJECTED_BY_GATE,
            port.execute("t", "c1", PlannedStep("Akku", "get_battery_info"), false, budget).status)
        assertEquals(ToolOutcomeStatus.REJECTED_BY_GATE,
            port.execute("t", "c2", PlannedStep("Suche", "web_search", mapOf("query" to "x")), false, budget).status)
        assertEquals(ToolOutcomeStatus.REJECTED_BY_GATE,
            port.execute("t", "c3", PlannedStep("Shell", "shell", mapOf("command" to "rm")), true, budget).status)
        assertTrue(ran.isEmpty())
    }

    private class FakePills(val answer: Boolean?) : PillActionGate {
        val requested = mutableListOf<AnswerPill>()
        override fun pillsFor(event: CockpitAnimationEvent.DocumentQuestion) = emptyList<AnswerPill>()
        override fun request(pill: AnswerPill, turnId: String, onDecision: (approvedByOwner: Boolean) -> Unit) {
            requested += pill; answer?.let(onDecision)
        }
    }

    @Test fun ownerYesAndNoComeFromThePillGate() {
        val yes = FakePills(true)
        assertEquals(OwnerDecision.APPROVED, PillActionGateApprovalPort(yes, AnswerPillKind.AUSARBEITEN).approve("t", "web_search", "Darf ich?", budget))
        assertEquals(AnswerPill(AnswerPillKind.AUSARBEITEN, "web_search", true, null), yes.requested.single())
        assertEquals(OwnerDecision.DENIED, PillActionGateApprovalPort(FakePills(false), AnswerPillKind.AUSARBEITEN).approve("t", "web_search", "?", budget))
    }

    @Test fun noAnswerUntilTheDeadlineIsATimeoutAndCancelIsHonoured() {
        var now = 0L
        val port = PillActionGateApprovalPort(FakePills(null), AnswerPillKind.AUSARBEITEN, { now }, { now += it })
        assertEquals(OwnerDecision.TIMEOUT, port.approve("t", "web_search", "?", LoopBudget(1_000) { false }))
        assertEquals(OwnerDecision.CANCELLED, port.approve("t", "web_search", "?", LoopBudget(Long.MAX_VALUE) { true }))
    }
}
