package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DeviceDevelopmentBacklogTest {
    private var now = 1_000_000L

    private fun withBacklog(block: (DeviceDevelopmentBacklog, File) -> Unit) {
        val dir = Files.createTempDirectory("device-backlog").toFile()
        try { block(DeviceDevelopmentBacklog(dir) { now }, dir) } finally { dir.deleteRecursively() }
    }

    private fun task(id: String = "dev-123e4567-e89b-12d3-a456-426614174000") =
        DevelopmentTask(id, "Fehlerbehandlung im Importdialog verbessern", listOf(SourceSnapshot.of("app/src/main/A.kt", "class A")))

    private fun proposal(taskId: String) = DevelopmentProposal(taskId, "summary", emptyList(), emptyList(),
        CriticResult(CriticVerdict.ACCEPT, emptyList()), emptyList(), JudgeResult(JudgeDecision.RECOMMEND, "ok"),
        setOf("local-model"), 4, "--- preview")

    private fun state(b: DeviceDevelopmentBacklog, id: String) = b.views().single { it.id == id }

    @Test fun completedJobWaitsForTestEvidenceInsteadOfBeingDone() = withBacklog { b, _ ->
        val t = task()
        val token = b.startJob(t)
        assertEquals("IN_PROGRESS", state(b, t.taskId).state)
        now += 30_000
        b.finishJob(t.taskId, token, DevelopmentOutcome(DevelopmentJobState.COMPLETED, proposal(t.taskId), null, 4))
        val view = state(b, t.taskId)
        assertEquals("VERIFYING", view.state)
        val doc = b.read().doc
        @Suppress("UNCHECKED_CAST")
        val submission = ((doc["tasks"] as List<Map<String, Any?>>).single()["submission"] as Map<String, Any?>)["artifact"] as Map<String, Any?>
        assertEquals("PROPOSAL", submission["type"])
        assertEquals(DevelopmentBacklog.sha256Of(proposal(t.taskId).render().toByteArray(Charsets.UTF_8)), submission["sha256"])
    }

    @Test fun failedAndCancelledJobsKeepTheirReasonAndBudget() = withBacklog { b, _ ->
        val t = task()
        var token = b.startJob(t)
        b.finishJob(t.taskId, token, DevelopmentOutcome(DevelopmentJobState.FAILED, null,
            DevelopmentFailure(DevelopmentFailureKind.NO_MODEL, "No local model loaded."), 0))
        assertEquals("READY" to "NO_MODEL", state(b, t.taskId).let { it.state to it.errorCode })
        // the same job id claimed again (user retries the same task)
        token = b.apply(listOf(mapOf("op" to "claim", "id" to t.taskId, "owner" to "android-app", "token" to "a".repeat(32), "leaseMs" to 300_000L)))
            .let { "a".repeat(32) }
        b.finishJob(t.taskId, token, DevelopmentOutcome(DevelopmentJobState.CANCELLED, null,
            DevelopmentFailure(DevelopmentFailureKind.MODEL_FAILURE, "Cancelled."), 1))
        val view = state(b, t.taskId)
        assertEquals(listOf("READY", "CANCELLED", "2"), listOf(view.state, view.errorCode, view.attempts.toString()))
    }

    @Test fun jobInterruptedByAppDeathIsRecoveredWhenItsLeaseEnds() = withBacklog { b, dir ->
        val t = task()
        b.startJob(t)
        // the process dies: a new instance on the next app start
        val restarted = DeviceDevelopmentBacklog(dir) { now }
        assertTrue("lease still running", restarted.recover().isEmpty())
        now += DeviceDevelopmentBacklog.JOB_LEASE_MS + 1
        assertEquals(listOf(t.taskId), restarted.recover())
        val view = state(restarted, t.taskId)
        assertEquals("READY" to "LEASE_EXPIRED", view.state to view.errorCode)
        assertTrue("nothing else to recover", restarted.recover().isEmpty())
    }

    @Test fun humanDecisionsUseTheSharedRulesAndReportRejections() = withBacklog { b, _ ->
        b.apply(listOf(mapOf("op" to "propose", "task" to mapOf("id" to "task-review", "title" to "Prüfen", "kind" to "INVESTIGATE", "source" to "android"))))
        val rejectWithoutReason = b.decide("task-review", "reject", null)
        assertFalse(rejectWithoutReason.ok)
        assertEquals("INVALID_FIELD", rejectWithoutReason.code)
        assertTrue(b.decide("task-review", "accept", null).ok)
        val again = b.decide("task-review", "accept", null)
        assertEquals("INVALID_TRANSITION", again.code)
        assertTrue(b.decide("task-review", "abort", "nicht mehr nötig").ok)
        assertEquals("ABORTED", state(b, "task-review").state)
    }

    @Test fun operationLogIsExactlyOnceAndImportRebasesLocalWork() = withBacklog { b, _ ->
        val t = task()
        val token = b.startJob(t)
        b.finishJob(t.taskId, token, DevelopmentOutcome(DevelopmentJobState.COMPLETED, proposal(t.taskId), null, 4))
        b.decide(t.taskId, "accept", null) // rejected (INVALID_TRANSITION): never journaled
        @Suppress("UNCHECKED_CAST")
        val log = at.heinz.deviceai.learning.StrictJson.parse(String(b.exportOperationLog(), Charsets.UTF_8)) as List<Map<String, Any?>>
        assertEquals(listOf("propose", "accept", "claim", "submit"), log.map { it["op"] })
        assertTrue(log.all { (it["opId"] as String).startsWith("android:") })

        // the canonical backlog already applied the first two operations (e.g. via `backlog apply`)
        var canonical: Map<String, Any?> = DevelopmentBacklog.emptyDocument(1L, "continuum-agent")
        canonical = DevelopmentBacklog.applyOps(canonical, log.take(2)).first
        canonical = DevelopmentBacklog.applyOp(canonical, mapOf("op" to "propose", "actor" to "code-analyzer", "at" to 5L,
            "task" to mapOf("id" to "inv-0000000000000001", "title" to "Befund", "kind" to "INVESTIGATE", "source" to "code-analyzer"))).doc
        val result = b.importBacklog(DevelopmentBacklog.encode(canonical))
        assertEquals(2, result.alreadyIncluded)
        assertEquals(2, result.replayed)
        assertTrue(result.rejected.isEmpty())
        assertEquals(setOf(t.taskId, "inv-0000000000000001"), b.views().map { it.id }.toSet())
        assertEquals("VERIFYING", state(b, t.taskId).state)
        @Suppress("UNCHECKED_CAST")
        val remaining = at.heinz.deviceai.learning.StrictJson.parse(String(b.exportOperationLog(), Charsets.UTF_8)) as List<Map<String, Any?>>
        assertEquals("operations the canonical backlog already has leave the log", listOf("claim", "submit"), remaining.map { it["op"] })
        // importing the same file again changes nothing that was already there
        val second = b.importBacklog(DevelopmentBacklog.encode(canonical))
        assertEquals(2, second.replayed)
        assertEquals("VERIFYING", state(b, t.taskId).state)
    }

    @Test fun importRejectsTamperedFilesAndKeepsLocalState() = withBacklog { b, _ ->
        b.startJob(task())
        val before = b.exportBacklog()
        val tampered = String(before, Charsets.UTF_8).replace("Fehlerbehandlung", "Fehlerbehendlung").toByteArray(Charsets.UTF_8)
        val code = try { b.importBacklog(tampered); null } catch (e: BacklogException) { e.code }
        assertEquals("INTEGRITY_MISMATCH", code)
        assertTrue(before.contentEquals(b.exportBacklog()))
    }

    @Test fun corruptLocalBacklogIsRecoveredFromBackupAndReported() = withBacklog { b, dir ->
        b.startJob(task())
        b.decide(task().taskId, "abort", "Test")
        val file = File(dir, "development-backlog.json")
        file.writeText("kaputt")
        val change = b.apply(listOf(mapOf("op" to "expire")))
        assertNotNull(change.recovered)
        assertNotNull(change.recovered!!.quarantined)
        assertEquals("kaputt", File(change.recovered!!.quarantined!!).readText())
        assertNull(b.read().recovered)
    }

    @Test fun jobIdsMapToValidBacklogIds() {
        assertEquals("dev-123e4567-e89b-12d3-a456-426614174000", DeviceDevelopmentBacklog.taskIdOf("dev-123E4567-E89B-12D3-A456-426614174000"))
        val hashed = DeviceDevelopmentBacklog.taskIdOf("Job #1 with spaces")
        assertTrue(hashed.startsWith("dev-") && hashed.length == 36)
    }
}
