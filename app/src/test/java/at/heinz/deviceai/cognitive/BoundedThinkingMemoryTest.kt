package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.learning.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

class BoundedThinkingMemoryTest {
    private fun withMemory(block: (File, ContinuumLearningCore, ContinuumThinkingMemory) -> Unit) {
        val dir = Files.createTempDirectory("bounded-thinking-memory").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Ich wohne in Wien.")
            block(dir, core, ContinuumThinkingMemory(dir, core))
        } finally { dir.deleteRecursively() }
    }

    @Test fun modelStepReadsChangedProviderAfterRealToolResult() {
        fun record(id: String) = MemoryRecord(id, MemoryType.SEMANTIC,
            "Storage directory rule: $id", ProvenanceType.USER_PROVIDED, 0.9, 1L, 1L)
        var available = listOf(record("before"))
        var reads = 0
        val observed = mutableListOf<List<String>>()
        val dispatched = mutableListOf<String>()
        val result = BoundedAgentOrchestrator(
            model = AgentModel { _, memories, _, observations ->
                observed += memories.map { it.id }
                if (observations.isEmpty()) AgentModelOutput("controlled", "",
                    ToolProposal("get_storage_info", emptyMap()))
                else AgentModelOutput("controlled", "Storage inspection completed")
            },
            memories = { reads++; available },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { request ->
                dispatched += request.name
                available = listOf(record("after"))
                "storage free=1024"
            }),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
            experienceStore = InMemoryExperienceStore(),
            clock = { 100L }
        ).run("Inspect storage directory")
        assertNull(result.blocker)
        assertEquals(listOf("get_storage_info"), dispatched)
        assertEquals(listOf(listOf("before"), listOf("after")), observed)
        assertEquals(2, reads)
    }

    @Test fun ownerRevocationClearsThinkingJournalBeforeFreshRecall() = withMemory { dir, core, memory ->
        memory.reflect(ThinkingRun("old-run", "Wohnort", listOf("Wohnort Wien aus dem Besitzerprofil.")))
        val journal = File(dir, "learning-core-v2/" + ContinuumThinkingMemory.FILE_NAME)
        assertTrue(journal.length() > 0L)
        core.revokeAllOwnerFacts()
        assertTrue(core.activeOwnerFacts().isEmpty())
        assertFalse("No durable derived owner text may survive revocation",
            journal.isFile && journal.length() > 0L)
        val fresh = ContinuumThinkingMemory(dir, ContinuumLearningCore(dir))
        assertTrue(fresh.recall(ThinkingStep("fresh-run", 1, ThinkingPhase.RECALL,
            "Wohnort", "Wohnort Wien")).abstained)
    }

    @Test fun revokedInstancesCannotAppendAndNewOwnerRunsRemainAllowed() = withMemory { dir, core, memory ->
        val sibling = ContinuumThinkingMemory(dir, ContinuumLearningCore(dir))
        val step = ThinkingStep("old-run", 1, ThinkingPhase.CHECK, "Wohnort", "Wohnort")
        memory.journal(step, StepOutcome(StepStatus.OK, "Earlier owner result"))
        core.revokeAllOwnerFacts()
        assertThrows(CancellationException::class.java) {
            memory.journal(step.copy(stepIndex = 2), StepOutcome(StepStatus.OK, "Late old outcome"))
        }
        assertThrows(CancellationException::class.java) {
            sibling.reflect(ThinkingRun("old-run", "Wohnort", listOf("Late old reflection")))
        }
        core.confirmOwnerFact("Ich wohne in Linz.")
        val fresh = ContinuumThinkingMemory(dir, ContinuumLearningCore(dir))
        fresh.journal(step.copy(runId = "new-run"), StepOutcome(StepStatus.OK, "New explicit owner run"))
        assertEquals(1, fresh.runJournal("new-run").size)
        assertTrue(fresh.runJournal("old-run").isEmpty())
        assertTrue(fresh.recall(ThinkingStep("new-run", 2, ThinkingPhase.RECALL,
            "Wohnort", "Wo wohne ich?")).items.any { it.confirmed && "Linz" in it.text })
    }

    @Test fun unclearedThinkingJournalPreventsLedgerRevocation() = withMemory { dir, core, _ ->
        val before = core.activeOwnerFacts()
        val journal = File(dir, "learning-core-v2/" + ContinuumThinkingMemory.FILE_NAME)
        assertTrue(journal.mkdirs())
        val obstruction = File(journal, "existing-entry").apply { writeText("Cannot truncate this directory") }
        assertThrows(Exception::class.java) { core.revokeAllOwnerFacts() }
        assertEquals(before, ContinuumLearningCore(dir).activeOwnerFacts())
        assertTrue(obstruction.isFile)
    }
}
