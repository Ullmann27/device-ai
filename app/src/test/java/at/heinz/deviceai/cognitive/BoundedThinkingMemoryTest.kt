package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.FileToolExecutionReceiptStore
import at.heinz.deviceai.learning.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    @Test fun ownerRecallRefreshesBetweenActualStepsAndJournalPreservesOutcomeScope() = withMemory { dir, core, memory ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val observed = mutableListOf<List<MemoryRecord>>()
        val output = "storage free=1024"
        val started = System.currentTimeMillis()
        val agent = BoundedAgentOrchestrator(
            model = AgentModel { _, memories, _, observations ->
                observed += memories.toList()
                if (observations.isEmpty()) AgentModelOutput("controlled", "",
                    ToolProposal("get_storage_info", emptyMap()))
                else AgentModelOutput("controlled", "Storage inspection completed")
            },
            memories = { emptyList() },
            thinkingMemory = { memory },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                entered.countDown()
                check(release.await(20, TimeUnit.SECONDS))
                output
            }),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0, "Response form accepted") },
            evaluationConfirmsOutcome = false,
            experienceStore = InMemoryExperienceStore(), callIdPrefix = "actual-steps"
        )
        try {
            val pending = executor.submit<AgentRunResult> { agent.run("Wo wohne ich? Prüfe den Speicher.") }
            assertTrue("Actual tool was not entered", entered.await(20, TimeUnit.SECONDS))
            val journal = File(dir, "learning-core-v2/" + ContinuumThinkingMemory.FILE_NAME)
            assertFalse("Unfinished scope must not persist partial steps", journal.isFile && journal.length() > 0L)
            core.confirmOwnerFact("Ich wohne in Linz.")
            val factsBeforeFinish = core.activeOwnerFacts()
            release.countDown()
            val result = pending.get(20, TimeUnit.SECONDS)
            assertNull(result.blocker)
            assertEquals(2, observed.size)
            assertTrue(observed[0].any { "thinking-recall" in it.tags && "Wien" in it.content })
            assertFalse(observed[0].any { "Linz" in it.content })
            assertTrue(observed[1].any { "owner-confirmed" in it.tags && "Linz" in it.content })
            val entries = memory.runJournal("actual-steps:0")
            val steps = entries.filter { it.kind == ThinkingEntryKind.STEP }
            assertEquals(listOf(ThinkingPhase.PLAN, ThinkingPhase.ACT, ThinkingPhase.PLAN, ThinkingPhase.CHECK),
                steps.map { it.phase })
            assertEquals(listOf(1, 2, 3, 4), steps.map { it.stepIndex })
            assertTrue(steps.all { it.atMillis in started..System.currentTimeMillis() })
            val action = steps.single { it.phase == ThinkingPhase.ACT }
            assertEquals(ThinkingEntryStatus.OK, action.status)
            assertTrue(action.text.contains("output-sha256=" + sha256Hex(output)))
            assertTrue(action.text.contains("fresh=true"))
            assertEquals(ThinkingEntryStatus.SKIPPED, steps.last().status)
            assertTrue(steps.last().text.contains("scope=response-form"))
            assertFalse(result.taskEvidence!!.outcomeVerified)
            assertTrue(entries.filter { it.kind == ThinkingEntryKind.REFLECTION }.single().let {
                it.status == ThinkingEntryStatus.HYPOTHESIS && "ungeprüft" in it.text
            })
            assertEquals(factsBeforeFinish, core.activeOwnerFacts())
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun deniedToolCannotBecomeSuccessfulThinkingOutcome() = withMemory { _, core, memory ->
        val before = core.activeOwnerFacts()
        val result = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, observations ->
                if (observations.isEmpty()) AgentModelOutput("controlled", "",
                    ToolProposal("get_storage_info", emptyMap()))
                else AgentModelOutput("controlled", "Claimed success")
            },
            memories = { emptyList() }, thinkingMemory = { memory },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = false), ToolRunner { error("Denied tool executed") }),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
            experienceStore = InMemoryExperienceStore(), limits = AgentLimits(maxRetries = 0),
            callIdPrefix = "denied-thinking"
        ).run("Inspect storage")
        assertNotNull(result.blocker)
        val entries = memory.runJournal("denied-thinking:0")
        assertEquals(ThinkingEntryStatus.BLOCKED, entries.single { it.phase == ThinkingPhase.ACT }.status)
        assertEquals(ThinkingEntryStatus.FAILED, entries.single { it.phase == ThinkingPhase.CHECK }.status)
        assertTrue(entries.filter { it.kind == ThinkingEntryKind.REFLECTION }.all {
            it.status == ThinkingEntryStatus.HYPOTHESIS
        })
        assertEquals(before, core.activeOwnerFacts())
    }

    @Test fun replayedToolReceiptCannotCreateFreshThinkingEvidence() = withMemory { dir, _, memory ->
        val receipts = FileToolExecutionReceiptStore(File(dir, "receipts"))
        val proposal = ToolProposal("get_storage_info", emptyMap())
        assertEquals(ToolExecutionStatus.SUCCEEDED,
            ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { "old measurement" }, receiptStore = receipts)
                .execute(proposal.invocation("replayed-thinking-1")).record.status)
        val result = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, _ -> AgentModelOutput("controlled", "", proposal) },
            memories = { emptyList() }, thinkingMemory = { memory },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { error("Replayed tool executed") },
                receiptStore = receipts),
            evaluator = AgentEvaluator { _, _ -> error("Replay cannot reach evaluator") },
            experienceStore = InMemoryExperienceStore(), callIdPrefix = "replayed-thinking"
        ).run("Inspect storage")
        assertNotNull(result.blocker)
        assertTrue(result.taskEvidence!!.calls.single().replayed)
        val action = memory.runJournal("replayed-thinking:0").single { it.phase == ThinkingPhase.ACT }
        assertEquals(ThinkingEntryStatus.BLOCKED, action.status)
        assertTrue(action.text.contains("fresh=false"))
        assertTrue(action.text.contains("output-sha256=none"))
    }

    @Test fun lateScreenObservationDiscardsEarlierBufferedThinkingSteps() = withMemory { dir, _, memory ->
        val store = InMemoryExperienceStore()
        val dispatched = mutableListOf<String>()
        val result = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, observations ->
                when (observations.size) {
                    0 -> AgentModelOutput("controlled", "", ToolProposal("get_storage_info", emptyMap()))
                    1 -> AgentModelOutput("controlled", "", ToolProposal("read_app_screen",
                        mapOf("packageOrName" to "com.android.settings")))
                    else -> AgentModelOutput("controlled", "PRIVATE_SCREEN_RESULT")
                }
            },
            memories = { emptyList() }, thinkingMemory = { memory },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                dispatched += it.name
                if (it.name == "read_app_screen") "PRIVATE_SCREEN_RESULT" else "storage free=1024"
            }),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0, "PRIVATE_SCREEN_RESULT") },
            approvalProvider = AgentToolApprovalProvider { _, _ -> AgentToolApproval(true) },
            experienceStore = store, callIdPrefix = "screen-thinking"
        ).run("Inspect storage and this screen")
        assertNull(result.blocker)
        assertEquals(listOf("get_storage_info", "read_app_screen"), dispatched)
        assertTrue(store.all().isEmpty())
        assertTrue(memory.runJournal("screen-thinking:0").isEmpty())
        val journal = File(dir, "learning-core-v2/" + ContinuumThinkingMemory.FILE_NAME)
        assertFalse(journal.isFile && journal.length() > 0L)
    }

    @Test fun incompleteOwnerResponseCreatesNoThinkingJournal() = withMemory { _, _, memory ->
        val store = InMemoryExperienceStore()
        val result = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, _ -> AgentModelOutput.ownerDialogue("controlled", "Partial response",
                LocalGenerationCompletion.OUTPUT_LIMIT) },
            memories = { emptyList() }, thinkingMemory = { memory },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { error("No tool proposed") }),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
            experienceStore = store, callIdPrefix = "partial-thinking"
        ).run("Wo wohne ich?")
        assertFalse(result.responseComplete)
        assertNotNull(result.blocker)
        assertTrue(store.all().isEmpty())
        assertTrue(memory.runJournal("partial-thinking:0").isEmpty())
    }

    @Test fun revocationDuringPausedModelRejectsLateToolAndBufferedJournal() = withMemory { dir, core, memory ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val agent = BoundedAgentOrchestrator(
            model = AgentModel { _, memories, _, _ ->
                check(memories.any { "Wien" in it.content })
                entered.countDown()
                check(release.await(20, TimeUnit.SECONDS))
                AgentModelOutput("controlled", "", ToolProposal("get_storage_info", emptyMap()))
            },
            memories = { emptyList() }, thinkingMemory = { memory },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { error("Revoked tool dispatched") }),
            evaluator = AgentEvaluator { _, _ -> error("Revoked response evaluated") },
            experienceStore = InMemoryExperienceStore(), callIdPrefix = "revoked-thinking"
        )
        try {
            val pending = executor.submit<AgentRunResult> { agent.run("Wo wohne ich? Prüfe den Speicher.") }
            assertTrue(entered.await(20, TimeUnit.SECONDS))
            core.revokeAllOwnerFacts()
            release.countDown()
            val failure = assertThrows(ExecutionException::class.java) { pending.get(20, TimeUnit.SECONDS) }
            assertTrue(failure.cause is CancellationException)
            val fresh = ContinuumThinkingMemory(dir, ContinuumLearningCore(dir))
            assertTrue(fresh.runJournal("revoked-thinking:0").isEmpty())
            assertTrue(fresh.recall(ThinkingStep("new-run", 1, ThinkingPhase.RECALL,
                "Wohnort", "Wohnort Wien")).abstained)
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test fun journalWriteFailureRetainsRealTaskResultWithoutClaimingPersistence() = withMemory { dir, _, memory ->
        val journal = File(dir, "learning-core-v2/" + ContinuumThinkingMemory.FILE_NAME)
        assertTrue(journal.mkdirs())
        val result = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, observations ->
                if (observations.isEmpty()) AgentModelOutput("controlled", "", ToolProposal("get_storage_info", emptyMap()))
                else AgentModelOutput("controlled", "Actual storage result")
            },
            memories = { emptyList() }, thinkingMemory = { memory },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { "storage free=1024" }),
            evaluator = AgentEvaluator { _, record -> Evaluation(record?.status == ToolExecutionStatus.SUCCEEDED, 1.0) },
            experienceStore = InMemoryExperienceStore(), callIdPrefix = "write-failed-thinking"
        ).run("Inspect storage")
        assertNull(result.blocker)
        assertEquals("Actual storage result", result.response)
        assertTrue(result.taskEvidence!!.outcomeVerified)
        assertTrue(result.auditTrail.any { "Thinking journal not fully persisted" in it.detail })
        assertTrue(journal.isDirectory)
    }

    @Test fun ownerRecallAliasesCannotDisplaceExistingProviderEvidence() = withMemory { _, core, memory ->
        for (i in 1..8) core.confirmOwnerFact("Mein Speicherverzeichnis Nummer $i liegt im Archiv." +
            if (i <= 4) "" else " Darin liegen Belege zu Werkstatt, Fahrzeug, Reisen, Reparaturen, " +
                "Haushalt, Projekten, Plänen und Terminen.")
        val provider = core.activeOwnerFacts().map { fact ->
            MemoryRecord(fact.id, MemoryType.SEMANTIC, fact.content, ProvenanceType.USER_PROVIDED,
                0.9, fact.createdAt, fact.updatedAt, tags = listOf("owner-confirmed"),
                privacyClass = PrivacyClass.DEVICE_LOCAL_ONLY)
        }
        val expected = provider.filter { "Speicherverzeichnis" in it.content }.map { it.id }.toSet()
        assertEquals(8, expected.size)
        var received = emptyList<MemoryRecord>()
        val result = BoundedAgentOrchestrator(
            model = AgentModel { _, memories, _, _ ->
                received = memories
                AgentModelOutput("controlled", "Verzeichnisse geprüft")
            },
            memories = { provider }, thinkingMemory = { memory },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { error("No tool proposed") }),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
            experienceStore = InMemoryExperienceStore(), callIdPrefix = "alias-thinking"
        ).run("Prüfe Speicherverzeichnis und Archiv")
        assertNull(result.blocker)
        assertEquals(8, received.size)
        assertEquals("Recall aliases must not consume the eight evidence slots", expected, received.map { it.id }.toSet())
        assertTrue(received.all { it in provider })
    }
}
