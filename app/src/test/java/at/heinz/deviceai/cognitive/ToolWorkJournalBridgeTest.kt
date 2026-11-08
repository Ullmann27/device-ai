package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolExecutionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ToolWorkJournalBridgeTest {
    @get:Rule val dir = TemporaryFolder()
    private val name = ToolWorkJournalBridge.FIRST_TOOL
    private val callId = "local-healthy-storage-1"
    private val hash = "a".repeat(64)

    private fun proof(status: ToolExecutionStatus = ToolExecutionStatus.SUCCEEDED,
                      fresh: Boolean = true, uncertain: Boolean = false,
                      replayed: Boolean = false, toolName: String = name) =
        AgentToolEvidence(callId, toolName, "b".repeat(64),
            approvalRequested = false, ownerApproved = null, status = status,
            outputSha256 = if (fresh) hash else null,
            freshOutput = fresh, outcomeUncertain = uncertain, replayed = replayed)

    @Test fun actualDispatchAndFreshReceiptAreDurableButNotIndependentProof() {
        var now = 1_000L
        val journal = CockpitWorkJournal(dir.root) { now }
        val started = journal.beginTool(callId, name)
        assertEquals(CockpitWorkJournal.Kind.TOOL, started.kind)
        assertEquals(CockpitWorkJournal.ToolState.EXECUTING, started.toolState)
        assertEquals(1, journal.all().size)
        assertTrue(CockpitWorkJournal.stateText(started, now).contains("läuft"))
        now = 2_000L
        ToolWorkJournalBridge.settle(journal, proof(), current = true)
        val restarted = CockpitWorkJournal(dir.root) { now }.all().single()
        assertEquals(CockpitWorkJournal.ToolState.SUCCEEDED, restarted.toolState)
        assertEquals(hash, restarted.toolOutputSha256)
        assertNull(CockpitWorkJournal.nextStep(restarted, now))
        assertTrue(CockpitWorkJournal.stateText(restarted, now).contains("nicht unabhängig bestätigt"))
        assertTrue(WorkDecisionRecall.items(listOf(restarted), emptyList(), now).isEmpty())
        val json = File(dir.root, "cockpit-work/journal-v1.jsonl").readText()
        assertFalse(json.contains("outputData"))
        assertFalse(json.contains("arguments"))
    }

    @Test fun repeatedCallIdDoesNotDuplicateOrChangeTerminalReceipt() {
        val journal = CockpitWorkJournal(dir.root)
        journal.beginTool(callId, name)
        journal.beginTool(callId, name)
        assertEquals(1, journal.all().size)
        ToolWorkJournalBridge.settle(journal, proof(), current = true)
        ToolWorkJournalBridge.settle(journal, proof(status = ToolExecutionStatus.FAILED, fresh = false), current = true)
        assertEquals(CockpitWorkJournal.ToolState.SUCCEEDED, journal.all().single().toolState)
    }

    @Test fun freshSuccessWithoutDurableDispatchCannotBecomeCompleted() {
        val journal = CockpitWorkJournal(dir.root)
        ToolWorkJournalBridge.settle(journal, proof(), current = true)
        val recorded = CockpitWorkJournal(dir.root).all().single()
        assertEquals(CockpitWorkJournal.ToolState.UNKNOWN, recorded.toolState)
        assertNull("No trusted start; no successful output fingerprint", recorded.toolOutputSha256)
        assertTrue(CockpitWorkJournal.nextStep(recorded, recorded.updatedAt)!!.contains("prüfen"))
    }

    @Test fun reusedCallIdCannotChangeTheToolIdentity() {
        val journal = CockpitWorkJournal(dir.root)
        journal.beginTool(callId, name)
        var blocked = false
        try {
            journal.beginTool(callId, "get_device_info")
        } catch (_: IllegalArgumentException) { blocked = true }
        assertTrue("A call ID cannot belong to two tools", blocked)
        blocked = false
        try {
            journal.finishTool(callId, "get_device_info", CockpitWorkJournal.ToolState.SUCCEEDED, hash)
        } catch (_: IllegalArgumentException) { blocked = true }
        assertTrue("A mismatched result cannot complete another tool", blocked)
        val recorded = journal.all().single()
        assertEquals(name, recorded.toolName)
        assertEquals(CockpitWorkJournal.ToolState.EXECUTING, recorded.toolState)
    }

    @Test fun rejectedCallMayBeJournalledWithoutAnyExecutionStartedEvent() {
        val journal = CockpitWorkJournal(dir.root)
        ToolWorkJournalBridge.settle(journal,
            proof(status = ToolExecutionStatus.REJECTED, fresh = false), current = true)
        val e = journal.all().single()
        assertEquals(CockpitWorkJournal.ToolState.REJECTED, e.toolState)
        assertNull(e.toolOutputSha256)
        assertTrue(CockpitWorkJournal.stateText(e, e.updatedAt).contains("abgelehnt"))
    }

    @Test fun stopOrChangedSessionCanNeverPromoteSuccess() {
        val journal = CockpitWorkJournal(dir.root)
        journal.beginTool(callId, name)
        ToolWorkJournalBridge.settle(journal, proof(), current = false)
        val e = journal.all().single()
        assertEquals(CockpitWorkJournal.ToolState.UNKNOWN, e.toolState)
        assertNull(e.toolOutputSha256)
        assertTrue(CockpitWorkJournal.nextStep(e, e.updatedAt)!!.contains("nicht automatisch"))
    }

    @Test fun uncertainReplayTimeoutAndMissingFreshOutputNeverCountAsSucceeded() {
        val cases = listOf(
            proof(uncertain = true),
            proof(replayed = true),
            proof(status = ToolExecutionStatus.TIMED_OUT, fresh = false),
            proof(fresh = false)
        )
        cases.forEach {
            assertEquals(CockpitWorkJournal.ToolState.UNKNOWN,
                ToolWorkJournalBridge.classify(it, current = true))
        }
    }

    @Test fun anyRealToolMayBeJournalledButSuccessStillIsNotIndependentProof() {
        val other = proof(toolName = "get_device_info")
        assertEquals(CockpitWorkJournal.ToolState.SUCCEEDED,
            ToolWorkJournalBridge.classify(other, current = true))
        val journal = CockpitWorkJournal(dir.root)
        journal.beginTool(other.callId, other.toolName)
        ToolWorkJournalBridge.settle(journal, other, current = true)
        val e = journal.all().single()
        assertEquals("get_device_info", e.toolName)
        assertEquals(CockpitWorkJournal.ToolState.SUCCEEDED, e.toolState)
        assertEquals(hash, e.toolOutputSha256)
        assertTrue(CockpitWorkJournal.stateText(e, e.updatedAt).contains("nicht unabhängig bestätigt"))
    }

    @Test fun aKilledProcessTurnsRunningToolIntoUnknownNotDone() {
        var now = 1_000L
        val journal = CockpitWorkJournal(dir.root) { now }
        journal.beginTool(callId, name)
        now = 3_000L
        assertEquals(1, journal.recoverInterrupted(2_000L))
        val recovered = CockpitWorkJournal(dir.root) { now }.all().single()
        assertEquals(CockpitWorkJournal.ToolState.UNKNOWN, recovered.toolState)
        assertTrue(CockpitWorkJournal.stateText(recovered, now).contains("unklar"))
        assertEquals(0, journal.recoverInterrupted(2_000L))
    }

    @Test fun documentJournalRemainsBackwardsCompatible() {
        val journal = CockpitWorkJournal(dir.root)
        val doc = journal.begin(CockpitWorkJournal.Kind.DOCUMENT, "PDF zusammenfassen",
            CockpitWorkJournal.State.DRAFTED, "Bericht.pdf")
        assertNull(CockpitWorkJournal.nextStep(doc.copy(state = CockpitWorkJournal.State.SAVED), doc.updatedAt))
        val old = CockpitWorkJournal(dir.root).all().single()
        assertEquals(CockpitWorkJournal.Kind.DOCUMENT, old.kind)
        assertNull(old.toolState)
    }

    @Test fun repeatedStorageChecksKeepTenRecentToolRowsWithoutErasingPdf() {
        var now = 1_000L
        val journal = CockpitWorkJournal(dir.root) { now }
        val documents = (1..CockpitWorkJournal.MAX_EPISODES).map { number ->
            now += 1_000
            journal.begin(CockpitWorkJournal.Kind.DOCUMENT, "Wichtiger PDF-Entwurf $number",
                CockpitWorkJournal.State.DRAFTED, "Wichtig-$number.pdf")
        }
        repeat(20) { index ->
            now += 1_000
            val id = "storage-check-$index"
            journal.beginTool(id, name)
            journal.finishTool(id, name, CockpitWorkJournal.ToolState.REJECTED)
        }
        val records = journal.all()
        assertEquals(documents.map { it.id }.toSet(),
            records.filter { it.kind == CockpitWorkJournal.Kind.DOCUMENT }.map { it.id }.toSet())
        assertEquals(CockpitWorkJournal.MAX_EPISODES,
            records.count { it.kind == CockpitWorkJournal.Kind.DOCUMENT })
        assertEquals(CockpitWorkJournal.MAX_TOOL_EPISODES,
            records.count { it.kind == CockpitWorkJournal.Kind.TOOL })
        assertEquals(CockpitWorkJournal.MAX_EPISODES + CockpitWorkJournal.MAX_TOOL_EPISODES, records.size)
    }

    @Test fun invalidToolIDsAndOutputHashesAreNotPersisted() {
        val journal = CockpitWorkJournal(dir.root)
        var refused = false
        try { journal.beginTool("not allowed /secret", name) } catch (_: IllegalArgumentException) { refused = true }
        assertTrue(refused)
        val e = journal.beginTool(callId, name)
        journal.finishTool(callId, name, CockpitWorkJournal.ToolState.SUCCEEDED, "invalid")
        assertNull(journal.all().first { it.id == e.id }.toolOutputSha256)
    }
}
