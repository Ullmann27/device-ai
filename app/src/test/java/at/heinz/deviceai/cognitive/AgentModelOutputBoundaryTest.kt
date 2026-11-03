package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AgentModelOutputBoundaryTest {
    private val proposal = ToolProposal("get_device_info", emptyMap())
    private val correction = " NICHT AUSFÜHREN. Ö."

    @Test fun completeEightThousandCharacterResponseIsPreserved() {
        val answer = "x".repeat(8_000 - correction.length) + correction
        assertEquals(8_000, answer.length)
        val output = AgentModelOutput("fixture-model", answer, proposal)
        assertEquals(answer, output.response)
        assertSame(proposal, output.proposal)
    }

    @Test fun oversizedCompleteResponseWithProposalHasDistinctBudgetFailure() {
        val answer = "x".repeat(8_001 - correction.length) + correction
        assertEquals(8_001, answer.length)
        val failure = assertThrows(IllegalArgumentException::class.java) {
            AgentModelOutput("fixture-model", answer, proposal)
        }
        assertEquals("LOCAL_MODEL_RESPONSE_EXCEEDS_AGENT_LIMIT", failure.message)
    }

    @Test fun oversizedResponseCannotSubmitItsOtherwiseValidReadOnlyProposal() {
        var toolRuns = 0
        val agent = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, _ ->
                AgentModelOutput("fixture-model", "x".repeat(8_001), proposal)
            },
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { toolRuns++; "device" }),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
            experienceStore = InMemoryExperienceStore()
        )
        val failure = assertThrows(IllegalArgumentException::class.java) { agent.run("inspect device") }
        assertEquals("LOCAL_MODEL_RESPONSE_EXCEEDS_AGENT_LIMIT", failure.message)
        assertEquals(0, toolRuns)
    }
}
