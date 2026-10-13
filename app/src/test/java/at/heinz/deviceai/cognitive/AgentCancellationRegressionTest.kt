package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class AgentCancellationRegressionTest {
    private fun gate() = ToolGate(ToolPolicy(), ToolRunner { error("No device action expected") })

    @Test fun cancelledBeforeRunDoesNotCallModelOrStoreExperience() {
        val store = InMemoryExperienceStore()
        var calls = 0
        val agent = BoundedAgentOrchestrator(
            AgentModel { _, _, _, _ -> calls++; AgentModelOutput("m", "unused") }, { emptyList() }, gate(),
            AgentEvaluator { _, _ -> Evaluation(true, 1.0) }, store
        )
        agent.cancel()
        assertThrows(CancellationException::class.java) { agent.run("cancelled request") }
        assertEquals(0, calls)
        assertTrue(store.all().isEmpty())
    }

    @Test fun cancelDuringModelDiscardsAnswerBeforeEvaluationAndLearning() {
        val store = InMemoryExperienceStore()
        var evaluations = 0
        lateinit var agent: BoundedAgentOrchestrator
        agent = BoundedAgentOrchestrator(
            AgentModel { _, _, _, _ -> agent.cancel(); AgentModelOutput("m", "late answer") }, { emptyList() }, gate(),
            AgentEvaluator { _, _ -> evaluations++; Evaluation(true, 1.0) }, store
        )
        assertThrows(CancellationException::class.java) { agent.run("cancel during generation") }
        assertEquals(0, evaluations)
        assertTrue(store.all().isEmpty())
    }

    @Test fun cancelDuringEvaluationDoesNotStoreSuccessfulExperience() {
        val store = InMemoryExperienceStore()
        val reputation = ModelReputationStore()
        lateinit var agent: BoundedAgentOrchestrator
        agent = BoundedAgentOrchestrator(
            AgentModel { _, _, _, _ -> AgentModelOutput("m", "answer") }, { emptyList() }, gate(),
            AgentEvaluator { _, _ -> agent.cancel(); Evaluation(true, 1.0) }, store,
            modelReputation = reputation
        )
        assertThrows(CancellationException::class.java) { agent.run("cancel during evaluation") }
        assertTrue(store.all().isEmpty())
        assertEquals(0, reputation.get("m", ModelCapability.TOOL_USE, "agent").taskCount)
    }
}
