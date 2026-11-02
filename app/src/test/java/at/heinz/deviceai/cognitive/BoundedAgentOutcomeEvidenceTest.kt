package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class BoundedAgentOutcomeEvidenceTest {
    private fun model() = AgentModel { _, _, _, observations ->
        if (observations.isEmpty()) AgentModelOutput("test-planner", "", ToolProposal("get_storage_info", emptyMap()))
        else AgentModelOutput("test-planner", "Plausible answer alone is not evidence.")
    }

    @Test fun deniedToolCannotBecomeSuccessfulSkillThroughPermissiveEvaluator() {
        val reputation = ModelReputationStore()
        val result = BoundedAgentOrchestrator(
            model(), { emptyList() },
            ToolGate(ToolPolicy(readOnlyEnabled = false), ToolRunner { error("must not run") }),
            AgentEvaluator { _, _ -> Evaluation(true, 1.0) }, InMemoryExperienceStore(),
            modelReputation = reputation
        ).run("Prüfe den freien Speicher")
        assertEquals(ExperienceOutcome.PARTIAL, result.experience!!.outcome)
        assertTrue(result.experience.sourceIds.isEmpty())
        assertEquals(0, reputation.get("test-planner", ModelCapability.TOOL_USE, "agent").taskCount)
    }

    @Test fun replayedSucceededReceiptDoesNotCreateFreshPositiveEvidence() {
        val dir = Files.createTempDirectory("agent-replay-evidence").toFile()
        try {
            val store = FileToolExecutionReceiptStore(dir)
            val proposal = ToolProposal("get_storage_info", emptyMap()).invocation("repeat-1")
            val first = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { "actual old measurement" }, receiptStore = store)
                .execute(proposal)
            assertEquals(ToolExecutionStatus.SUCCEEDED, first.record.status)
            val result = BoundedAgentOrchestrator(
                model(), { emptyList() },
                ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { error("no duplicate execution") }, receiptStore = store),
                AgentEvaluator { _, _ -> Evaluation(true, 1.0) }, InMemoryExperienceStore(),
                callIdPrefix = "repeat"
            ).run("Prüfe den freien Speicher")
            assertNull(result.experience)
            assertTrue(result.taskEvidence!!.calls.single().replayed)
            assertNull(result.taskEvidence!!.learningCandidateExperienceId)
        } finally { dir.deleteRecursively() }
    }
}
