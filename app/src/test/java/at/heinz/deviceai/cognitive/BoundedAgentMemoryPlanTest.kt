package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedAgentMemoryPlanTest {
    private fun record(id: String, content: String, at: Long) = MemoryRecord(id, MemoryType.SEMANTIC, content,
        ProvenanceType.USER_PROVIDED, 0.9, at, at, privacyClass = PrivacyClass.DEVICE_LOCAL_ONLY)

    private fun memoriesSeenByModel(task: String, store: List<MemoryRecord>): List<MemoryRecord> {
        val seen = mutableListOf<MemoryRecord>()
        BoundedAgentOrchestrator(
            model = AgentModel { _, memories, _, _ -> seen += memories; AgentModelOutput("test-model", "ok") },
            memories = { store },
            toolGate = ToolGate(ToolPolicy(), ToolRunner { error("No tool expected") }),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
            experienceStore = InMemoryExperienceStore(),
            modelReputation = ModelReputationStore(),
            clock = { 10_000L }
        ).run(task)
        return seen
    }

    @Test fun theModelReceivesTheCurrentSuccessorOfAnOutdatedMemory() {
        val seen = memoriesSeenByModel("Enns", listOf(
            record("old", "Ute wohnte bis 2022 in Enns.", 1_000L), record("new", "Ute wohnt seit 2023 in Tulln.", 2_000L),
            record("other", "Kurt mag Kaffee.", 1_000L)))
        assertEquals(listOf("new", "old"), seen.map { it.id })
    }

    @Test fun repeatedStatementsReachTheModelOnceAndRelationalQuestionsFindThePerson() {
        val repeated = memoriesSeenByModel("Wo wohnt Ute?", listOf(
            record("a", "Ute wohnt in Enns.", 1_000L), record("b", "Ute wohnt in Enns.", 1_500L)))
        assertEquals(1, repeated.size)
        val relational = memoriesSeenByModel("Welchen Beruf hat der Bruder von Ute?", listOf(
            record("rel", "Ute ist die Schwester von Kurt.", 1_000L), record("job", "Kurt arbeitet als Tischler.", 1_000L)))
        assertTrue(relational.any { it.id == "job" })
    }
}
