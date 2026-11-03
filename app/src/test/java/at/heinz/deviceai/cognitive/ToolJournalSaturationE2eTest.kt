package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/**
 * M5 milestone 1, end-to-end: owner text -> BootstrapBrain -> ReadOnlyToolPlan ->
 * BoundedAgentOrchestrator -> ToolGate -> durable receipt journal -> real
 * DeviceStorageProbe, on a long-lived installation whose journal already holds
 * 512 receipts (the production cap). Same wiring as AgentActivity's bootstrap path.
 */
class ToolJournalSaturationE2eTest {
    @Test fun storageQueryStillRunsOnLongLivedInstallationWithFullJournal() {
        val dir = Files.createTempDirectory("continuum-journal-saturation").toFile()
        try {
            val receipts = FileToolExecutionReceiptStore(File(dir, "tool-receipts"))
            // 512 earlier, definitely finished (rejected) tool turns from previous days.
            for (index in 1..512) {
                val at = index.toLong()
                receipts.propose(ToolExecutionReceipt("history-$index", "get_battery_info",
                    toolArgumentFingerprint(emptyMap()), emptyMap(), Risk.READ_ONLY, at,
                    listOf(ToolReceiptTransition(ToolReceiptState.PROPOSED, at))))
                assertNotNull(receipts.transition("history-$index", ToolReceiptState.PROPOSED,
                    ToolReceiptState.REJECTED, at))
            }
            assertEquals(512, receipts.all().size)

            val request = "Continuum, prüfe bitte den freien Speicher."
            val decision = BootstrapBrain.decide(request, emptyList())
            assertEquals("get_storage_info", decision.tool)
            val plan = ReadOnlyToolPlan.forRequest(request, ToolProposal(checkNotNull(decision.tool), decision.arguments))
            var probeRuns = 0
            val result = runCatching {
                BoundedAgentOrchestrator(
                    model = plan, memories = { emptyList() },
                    toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                        probeRuns++; DeviceStorageProbe.read(dir)
                    }, receiptStore = receipts),
                    evaluator = plan, experienceStore = InMemoryExperienceStore(),
                    evaluationConfirmsOutcome = true,
                    limits = AgentLimits(maxSteps = 24, maxToolCalls = plan.stepCount, maxRetries = 0),
                    callIdPrefix = "bootstrap-saturated"
                ).run(request)
            }
            assertTrue("orchestrator threw: ${result.exceptionOrNull()}", result.isSuccess)
            val run = result.getOrThrow()
            assertNull(run.blocker)
            assertEquals(1, probeRuns)
            assertEquals(ExperienceOutcome.SUCCESS, run.experience!!.outcome)
            assertEquals(ToolReceiptState.SUCCEEDED, receipts.get("bootstrap-saturated-1")?.state)
            assertEquals(512, receipts.all().size)
            assertNull(receipts.get("history-1"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
