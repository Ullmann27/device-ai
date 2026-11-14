package at.heinz.deviceai.routines

import at.heinz.deviceai.routines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RoutineStoreIndependentContractTest {
    private val run = "12345678-1234-1234-1234-123456789abc"
    private fun routine(id: String = "battery", goal: String = "Pruefe Akku") =
        Routine(id, goal, RoutineTrigger.Every(RoutineBook.MIN_PERIOD_MILLIS, 100),
            setOf("get_battery_info"))

    private fun stored(routine: Routine) = StoredRoutine(routine,
        RoutineOwnerApproval.confirmed(routine, 90), 100)

    private fun report() = StoredRoutineReport("battery", run, "Akku: 50 %",
        AgentMessage("routine-battery-110-" + run.replace("-", ""), "routine:battery",
            "chief", AgentPriority.INFO, "Akku: 50 %"), "Akku: 50 %", 110)

    private fun line() = JSONObject().put("u", false).put("t", report().message!!.body)
        .put("at", 110).toString() + "\n"

    private fun append(text: String) = report().copy(chatAppend = RoutineChatAppend(
        "11111111-1111-1111-1111-111111111111", 45, text))

    private fun withStore(block: (FileRoutineStateStore) -> Unit) {
        val root = Files.createTempDirectory("routine-independent-").toFile()
        try { block(FileRoutineStateStore(File(root, "routines"))) }
        finally { root.deleteRecursively() }
    }

    private fun requireRejected(store: FileRoutineStateStore, state: RoutineStoredState) {
        val failure = runCatching { store.replace(0, state) }.exceptionOrNull()
        assertTrue("Invalid state was accepted: " + store.load(),
            failure is IllegalArgumentException)
        assertEquals(RoutineStoredState(), store.load())
    }

    @Test fun secondJsonLineCannotPiggybackOnBoundAssistantLine() = withStore { store ->
        val text = line() + "{\"u\":true,\"t\":\"unbound extra line\",\"at\":111}\n"
        requireRejected(store, RoutineStoredState(1, outbox = listOf(append(text))))
    }

    @Test fun trailingTextCannotPiggybackOnBoundAssistantLine() = withStore { store ->
        requireRejected(store, RoutineStoredState(1, outbox = listOf(append(line() + "unbound text"))))
    }

    @Test fun senderMustMatchStoredRoutineIdentity() = withStore { store ->
        val r = report()
        requireRejected(store, RoutineStoredState(1,
            outbox = listOf(r.copy(message = r.message!!.copy(from = "routine:other")))))
    }

    @Test fun activeRunsCannotShareDurableIdentity() = withStore { store ->
        val entries = listOf(stored(routine()).copy(runId = run),
            stored(routine("storage")).copy(runId = run))
        requireRejected(store, RoutineStoredState(1, entries = entries))
    }

    @Test fun deliveryCannotBeAcknowledgedWithoutBoundAppendBytes() = withStore { store ->
        val completed = report().copy(chatDelivered = true)
        requireRejected(store, RoutineStoredState(1, entries = listOf(
            stored(routine()).copy(runId = run, report = completed))))
    }

    @Test fun invalidSurrogateIsRejectedBeforeReplacingReadableState() = withStore { store ->
        val original = RoutineStoredState(1, entries = listOf(stored(routine())))
        store.replace(0, original)
        val malformed = routine("other", "bad" + 0xd800.toChar())
        val replacement = original.copy(revision = 2,
            entries = original.entries + stored(malformed))
        val save = runCatching { store.replace(1, replacement) }
        val reopen = runCatching { store.load() }
        assertTrue("replace succeeded=" + save.isSuccess + "; reopen failure=" +
            reopen.exceptionOrNull()?.javaClass?.name, save.exceptionOrNull() is IllegalArgumentException)
        assertEquals(original, reopen.getOrThrow())
    }
}
