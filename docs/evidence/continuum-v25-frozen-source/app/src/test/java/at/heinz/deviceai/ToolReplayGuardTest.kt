package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolReplayGuardTest {
    @Test
    fun gateRejectsReplayOfAnAlreadyConsumedCallId() {
        var executions = 0
        val gate = ToolGate(
            ToolPolicy(readOnlyEnabled = true, maxCalls = 4),
            ToolRunner { executions++; "ok" }
        )
        val proposal = ToolProposal("get_device_info", emptyMap())

        val first = gate.execute(proposal.invocation("same-call"))
        val replay = gate.execute(proposal.invocation("same-call"))

        assertEquals(ToolExecutionStatus.SUCCEEDED, first.record.status)
        assertEquals(ToolExecutionStatus.REJECTED, replay.record.status)
        assertTrue(replay.record.decisionReason.contains("replay", ignoreCase = true))
        assertEquals(1, executions)
    }

    @Test
    fun confirmationRejectionDoesNotConsumeCallId() {
        var executions = 0
        val gate = ToolGate(
            ToolPolicy(maxCalls = 4),
            ToolRunner { executions++; "stopped" }
        )
        val proposal = ToolProposal(
            "stop_app",
            mapOf("packageOrName" to "com.example.app")
        )

        val rejected = gate.execute(proposal.invocation("confirm-later"))
        val confirmed = gate.execute(
            proposal.invocation("confirm-later"),
            userConfirmed = true
        )

        assertEquals(ToolExecutionStatus.REJECTED, rejected.record.status)
        assertEquals(ToolExecutionStatus.SUCCEEDED, confirmed.record.status)
        assertEquals(1, executions)
    }
}
