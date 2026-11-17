package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Test

class AgentTaskIdentityTest {
    @Test fun distinctAuthorizedTaskNamespacesCannotCollideAtTheSameTime() {
        fun run(id: String) = BoundedAgentOrchestrator(
            AgentModel { _, _, _, _ -> AgentModelOutput("model", "response") }, { emptyList() },
            ToolGate(ToolPolicy(), ToolRunner { error("no tool") }),
            AgentEvaluator { _, _ -> Evaluation(true, 1.0) }, InMemoryExperienceStore(),
            clock = { 50L }, callIdPrefix = id).run("Identischer Auftrag")
        val first = run("authorized-task-a")
        val second = run("authorized-task-b")
        assertNotEquals("Different existing task/call namespaces need different experience IDs",
            first.experience!!.id, second.experience!!.id)
    }
}
