package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ToolWorkJournalIntegrationTest {
    @get:Rule val dir = TemporaryFolder()

    @Test fun actualReadOnlyDispatchAndReceiptReachTheSamePersistentWorkJournal() {
        val journal = CockpitWorkJournal(dir.root)
        val plan = ReadOnlyToolPlan(ToolProposal("get_storage_info", emptyMap()))
        val started = mutableListOf<String>()
        var executions = 0
        val result = BoundedAgentOrchestrator(
            plan, { emptyList() },
            ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                executions++
                "Gesamt: 100 Byte\nFür die App nutzbar: 80 Byte"
            }), plan, InMemoryExperienceStore(),
            evaluationConfirmsOutcome = true,
            callIdPrefix = "storage-journal-actual",
            limits = AgentLimits(maxToolCalls = 1, maxRetries = 0),
            onToolStarted = { id, name ->
                assertEquals(0, executions)
                started += id
                journal.beginTool(id, name)
            }
        ).run("Prüfe den freien Speicher")
        assertEquals(1, executions)
        val proof = requireNotNull(result.taskEvidence).calls.single()
        assertEquals(listOf(proof.callId), started)
        assertEquals(ToolExecutionStatus.SUCCEEDED, proof.status)
        assertTrue(proof.freshOutput)
        ToolWorkJournalBridge.settle(journal, proof, current = true)
        val persisted = CockpitWorkJournal(dir.root).all().single()
        assertEquals(CockpitWorkJournal.ToolState.SUCCEEDED, persisted.toolState)
        assertEquals(proof.callId, persisted.toolCallId)
        assertEquals(proof.outputSha256, persisted.toolOutputSha256)
        assertFalse("Never store raw tool result", persisted.toString().contains("Gesamt:"))
    }

    @Test fun secondReadOnlyToolUsesTheSameReceiptJournalPath() {
        val journal = CockpitWorkJournal(dir.root)
        val plan = ReadOnlyToolPlan(ToolProposal("get_device_info", emptyMap()))
        var executions = 0
        val result = BoundedAgentOrchestrator(
            plan, { emptyList() },
            ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                executions++
                "Gerät: Testgerät"
            }), plan, InMemoryExperienceStore(),
            callIdPrefix = "device-info-journal",
            limits = AgentLimits(maxToolCalls = 1, maxRetries = 0),
            onToolStarted = { id, name -> journal.beginTool(id, name) }
        ).run("Prüfe Geräteinformationen")
        assertEquals(1, executions)
        val proof = requireNotNull(result.taskEvidence).calls.single()
        assertEquals("get_device_info", proof.toolName)
        ToolWorkJournalBridge.settle(journal, proof, current = true)
        val persisted = journal.all().single()
        assertEquals("get_device_info", persisted.toolName)
        assertEquals(CockpitWorkJournal.ToolState.SUCCEEDED, persisted.toolState)
        assertEquals(proof.outputSha256, persisted.toolOutputSha256)
    }

    @Test fun deniedReadOnlyToolDoesNotEmitDispatchOrFakeSuccessfulJournal() {
        val journal = CockpitWorkJournal(dir.root)
        val plan = ReadOnlyToolPlan(ToolProposal("get_storage_info", emptyMap()))
        var starts = 0
        var executions = 0
        val result = BoundedAgentOrchestrator(
            plan, { emptyList() },
            ToolGate(ToolPolicy(readOnlyEnabled = false), ToolRunner {
                executions++
                "unexpected"
            }), plan, InMemoryExperienceStore(),
            callIdPrefix = "denied-storage-journal",
            limits = AgentLimits(maxToolCalls = 1, maxRetries = 0),
            onToolStarted = { _, _ -> starts++ }
        ).run("Prüfe den freien Speicher")
        assertEquals(0, executions)
        assertEquals(0, starts)
        assertTrue(journal.all().isEmpty())
        val proof = requireNotNull(result.taskEvidence).calls.single()
        assertEquals(ToolExecutionStatus.REJECTED, proof.status)
        ToolWorkJournalBridge.settle(journal, proof, current = true)
        val stored = journal.all().single()
        assertEquals(CockpitWorkJournal.ToolState.REJECTED, stored.toolState)
        assertNotNull(stored.toolCallId)
    }
}
