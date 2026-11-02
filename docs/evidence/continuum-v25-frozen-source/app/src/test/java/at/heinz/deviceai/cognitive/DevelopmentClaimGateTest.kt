package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class DevelopmentClaimGateTest {
    @Test fun failedDurableClaimNeverCallsModelAndReturnsTerminalFailure() {
        var calls = 0
        val coordinator = DevelopmentCoordinator(ContinuumReasoningAdapter {
            calls++; error("Model must never be invoked without a claim")
        }, {})
        val task = DevelopmentTask("claim-test", "Check input", listOf(SourceSnapshot.of("src/a.kt", "fun a() = 1")))
        val states = mutableListOf<DevelopmentJobState>()
        val (claim, outcome) = coordinator.runWithBacklog(task, { error("backlog storage failure") }, states::add)
        assertTrue(claim.isFailure)
        assertEquals(0, calls)
        assertEquals(0, outcome.modelCallsUsed)
        assertEquals(DevelopmentJobState.FAILED, outcome.state)
        assertEquals(listOf(DevelopmentJobState.FAILED), states)
        val ui = DevelopmentUiController { "claim-ui-test" }
        ui.setGoal(task.goal)
        ui.addSource("src/a.kt", "fun a() = 1")
        assertNotNull(ui.start(true))
        ui.onOutcome(outcome)
        assertEquals(DevelopmentUiPhase.FAILED, ui.state.phase)
        assertTrue(ui.state.canStart)
    }

    @Test fun successfulClaimPrecedesModelAndItsTokenReachesFinisher() {
        val events = mutableListOf<String>()
        val coordinator = DevelopmentCoordinator(ContinuumReasoningAdapter {
            events += "model"; error("controlled inference failure")
        }, {})
        val task = DevelopmentTask("claim-test", "Check input", listOf(SourceSnapshot.of("src/a.kt", "fun a() = 1")))
        val (claim, outcome) = coordinator.runWithBacklog(task, { events += "claim"; "owner-lease-token" })
        assertEquals(listOf("claim", "model"), events)
        assertEquals("owner-lease-token", claim.getOrThrow())
        assertEquals(1, outcome.modelCallsUsed)
    }
}
