package at.heinz.deviceai.routines

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RoutineStateStoreTest {
    private fun routine() = Routine("battery", "Prüfe den Akku",
        RoutineTrigger.Every(RoutineBook.MIN_PERIOD_MILLIS, 100), setOf("get_battery_info"))
    private fun entry(): StoredRoutine {
        val routine = routine()
        return StoredRoutine(routine, RoutineOwnerApproval.confirmed(routine, 90), 100)
    }
    private fun report(): StoredRoutineReport {
        val run = "12345678-1234-1234-1234-123456789abc"
        return StoredRoutineReport("battery", run, "Akku: 50 %",
            AgentMessage("routine-battery-110-${run.replace("-", "")}", "routine:battery", "chief",
                AgentPriority.INFO, "Akku: 50 %\nZeitpunkt: 110"), "Akku: 50 %\nZeitpunkt: 110", 110)
    }
    private fun withStore(block: (File, FileRoutineStateStore) -> Unit) {
        val root = Files.createTempDirectory("routine-state-").toFile()
        try { block(root, FileRoutineStateStore(File(root, "routines"))) }
        finally { root.deleteRecursively() }
    }
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected rejected routine state") }
        catch (expected: IllegalArgumentException) { }
    }

    @Test fun approvedScopeAndCompletedReportSurviveReopen() = withStore { root, store ->
        val report = report()
        val next = RoutineStoredState(1, listOf(entry().copy(runId = report.runId, report = report)))
        store.replace(0, next)
        assertEquals(next, FileRoutineStateStore(File(root, "routines")).load())
        val completed = RoutineStoredState(2,
            listOf(entry().copy(nextAtMillis = 900_110, lastDigest = report.digest,
                lastResult = report.result, lastResultAtMillis = report.atMillis)), listOf(report))
        store.replace(1, completed)
        assertEquals(completed, FileRoutineStateStore(File(root, "routines")).load())
    }

    @Test fun changedGoalScopeOrTriggerInvalidatesOriginalApproval() = withStore { _, store ->
        val approved = entry()
        for (changed in listOf(approved.routine.copy(goal = "Prüfe den Speicher"),
            approved.routine.copy(allowedTools = setOf("get_storage_info")),
            approved.routine.copy(trigger = RoutineTrigger.OnEvent("app-start")))) {
            rejects { store.replace(0, RoutineStoredState(1, listOf(approved.copy(routine = changed)))) }
        }
        assertEquals(RoutineStoredState(), store.load())
    }

    @Test fun staleWriterCannotRestoreStoppedRoutine() = withStore { root, first ->
        first.replace(0, RoutineStoredState(1, listOf(entry())))
        val stale = first.load()
        FileRoutineStateStore(File(root, "routines")).replace(1, RoutineStoredState(2))
        try {
            first.replace(stale.revision, stale.copy(revision = 2))
            fail("Stale approval must stay revoked")
        } catch (expected: IllegalStateException) { }
        assertEquals(RoutineStoredState(2), first.load())
    }

    @Test fun alteredPayloadFailsClosedWithoutDeletingEvidence() = withStore { root, store ->
        store.replace(0, RoutineStoredState(1, listOf(entry())))
        val file = File(root, "routines/owner-routines-v1.bin")
        val damaged = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        file.writeBytes(damaged)
        rejects { FileRoutineStateStore(File(root, "routines")).load() }
        assertArrayEquals(damaged, file.readBytes())
    }

    @Test fun symlinkCannotRedirectRoutineWrites() = withStore { root, store ->
        store.load()
        val target = File(root, "other-data").apply { writeText("keep") }
        Files.createSymbolicLink(File(root, "routines/owner-routines-v1.bin").toPath(), target.toPath())
        rejects { store.replace(0, RoutineStoredState(1, listOf(entry()))) }
        assertEquals("keep", target.readText())
    }

    @Test fun nonReadOnlyScopeAndMismatchedRunAreRejected() = withStore { _, store ->
        val dangerous = routine().copy(allowedTools = setOf("delete_file"))
        rejects { store.replace(0, RoutineStoredState(1, listOf(
            StoredRoutine(dangerous, RoutineOwnerApproval.confirmed(dangerous, 90), 100)))) }
        val report = report()
        rejects { store.replace(0, RoutineStoredState(1, listOf(entry().copy(
            runId = "ffffffff-ffff-ffff-ffff-ffffffffffff", report = report)))) }
        assertEquals(RoutineStoredState(), store.load())
    }

    @Test fun pendingMessageAndAppendCheckpointRemainBoundToOriginalReport() = withStore { root, store ->
        val pending = report().copy(chatAppend = RoutineChatAppend(
            "11111111-1111-1111-1111-111111111111", 45,
            org.json.JSONObject().put("u", false).put("t", report().message!!.body).put("at", 110).toString() + "\n"))
        store.replace(0, RoutineStoredState(1, outbox = listOf(pending)))
        assertEquals(pending, FileRoutineStateStore(File(root, "routines")).load().outbox.single())
        rejects { store.replace(1, RoutineStoredState(2, outbox = listOf(
            pending.copy(message = pending.message!!.copy(to = "private-bot"))))) }
        rejects { store.replace(1, RoutineStoredState(2, outbox = listOf(
            pending.copy(chatAppend = pending.chatAppend!!.copy(text =
                "{\"u\":false,\"t\":\"different report\",\"at\":110}\n"))))) }
        assertEquals(1L, store.load().revision)
    }
}
