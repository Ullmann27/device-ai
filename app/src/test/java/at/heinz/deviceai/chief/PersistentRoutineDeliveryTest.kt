package at.heinz.deviceai.chief

import at.heinz.deviceai.FileToolExecutionReceiptStore
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolReceiptState
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentLimits
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.cognitive.ReadOnlyToolPlan
import at.heinz.deviceai.routines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class PersistentRoutineDeliveryTest {
    private fun withDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("routine-delivery-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    private fun store(root: File) = FileRoutineStateStore(File(root, "routines"))
    private fun approved(book: RoutineBook, event: Boolean = false) {
        val routine = Routine("battery", "Prüfe den Akku",
            if (event) RoutineTrigger.OnEvent("app-start") else RoutineTrigger.Every(RoutineBook.MIN_PERIOD_MILLIS, 0),
            setOf("get_battery_info"))
        assertFalse(book.add(routine))
        assertTrue(book.addApproved(routine, RoutineOwnerApproval.confirmed(routine, 0)))
    }
    private fun pool(root: File, observed: () -> String): ChiefWorkerPool = ChiefWorkerPool(1, 0) { brief ->
        val plan = ReadOnlyToolPlan(ToolProposal("get_battery_info", emptyMap()))
        ChiefJobs.agentJob(brief, ChiefJobDeps(plan, ToolRunner { observed() },
            ToolPolicy(readOnlyEnabled = true, maxCalls = 1), null, plan, InMemoryExperienceStore(),
            UnattendedApproval, limits = AgentLimits(maxSteps = 24, maxToolCalls = 1, maxRetries = 0),
            receiptStore = FileToolExecutionReceiptStore(File(root, "tool-receipts"))))
    }

    @Test fun completedResultRestoresWithoutRepeatingTool() = withDirectory { root ->
        val calls = AtomicInteger()
        val first = RoutineBook(stateStore = store(root)); approved(first)
        val firstBox = AgentMailbox()
        val worker = pool(root) { calls.incrementAndGet(); "Akku: 50 %" }
        val message: AgentMessage
        try {
            val dispatcher = RoutineDispatcher(first, worker, firstBox, clock = { 10L })
            assertEquals(1, dispatcher.tick()); assertTrue(worker.awaitIdle(5_000))
            message = firstBox.peek("chief").single()
            assertEquals(message, first.pendingChatReports().single().message)
            assertEquals(ToolReceiptState.SUCCEEDED,
                FileToolExecutionReceiptStore(File(root, "tool-receipts")).all().single().state)
        } finally { worker.shutdown() }
        val restored = RoutineBook(stateStore = store(root))
        val restoredBox = AgentMailbox()
        val afterRestart = pool(root) { calls.incrementAndGet(); "Unexpected replay" }
        try {
            val dispatcher = RoutineDispatcher(restored, afterRestart, restoredBox, clock = { 20L })
            assertEquals(0, dispatcher.tick()); assertTrue(afterRestart.awaitIdle(5_000))
            assertEquals(listOf(message), restoredBox.peek("chief"))
            assertEquals(1, calls.get())
            assertEquals(900_010L, restored.registrations().single().nextAtMillis)
            assertFalse("Chat bytes must precede acknowledgment", restored.acknowledgeChat(message.id))
        } finally { afterRestart.shutdown() }
    }

    @Test fun fullMailboxRetainsOriginalResultAndClaimAcrossReopen() = withDirectory { root ->
        val calls = AtomicInteger()
        val first = RoutineBook(stateStore = store(root)); approved(first, event = true)
        val full = AgentMailbox(maxPerAgent = 1)
        assertTrue(full.send(AgentMessage("occupied", "fixture", "chief", AgentPriority.INFO, "occupied")))
        val worker = pool(root) { calls.incrementAndGet(); "Original measured battery result" }
        val saved: StoredRoutineReport
        try {
            val dispatcher = RoutineDispatcher(first, worker, full, clock = { 10L })
            assertEquals(1, dispatcher.onEvent("app-start")); assertTrue(worker.awaitIdle(5_000))
            saved = store(root).load().entries.single().report!!
            assertNotNull(first.registrations().single().runId)
            assertEquals(1, full.pending("chief"))
        } finally { worker.shutdown() }
        val restored = RoutineBook(stateStore = store(root))
        val mailbox = AgentMailbox()
        val afterRestart = pool(root) { calls.incrementAndGet(); "Unexpected replay" }
        try {
            val dispatcher = RoutineDispatcher(restored, afterRestart, mailbox, clock = { 50L })
            assertEquals(0, dispatcher.onEvent("app-start"))
            assertEquals(saved.message, mailbox.peek("chief").single())
            assertEquals(saved.result, restored.pendingChatReports().single().result)
            assertEquals(1, calls.get())
            assertNull(restored.registrations().single().runId)
        } finally { afterRestart.shutdown() }
    }

    @Test fun restoredRunningClaimIsReportedUncertainWithoutRedispatch() = withDirectory { root ->
        val original = RoutineBook(stateStore = store(root)); approved(original)
        val claim = original.claimDue(0).single()
        assertEquals(claim.runId, store(root).load().entries.single().runId)
        val calls = AtomicInteger()
        val restored = RoutineBook(stateStore = store(root))
        val mailbox = AgentMailbox()
        val worker = pool(root) { calls.incrementAndGet(); "Unexpected recovery execution" }
        try {
            val dispatcher = RoutineDispatcher(restored, worker, mailbox, clock = { 30L })
            assertEquals(0, dispatcher.tick())
            assertTrue(worker.awaitIdle(5_000))
            val report = mailbox.peek("chief").single()
            assertEquals(AgentPriority.URGENT, report.priority)
            assertTrue(report.body.contains("Ergebnis nicht bestätigt"))
            assertTrue(report.body.contains(claim.runId))
            assertEquals(0, calls.get())
            assertFalse("Old instance cannot regain this claim", original.isCurrent(claim))
        } finally { worker.shutdown() }
    }

    @Test fun completionWriteFailureRetriesOnlyTheFinishedReport() = withDirectory { root ->
        val disk = store(root)
        val reject = AtomicBoolean()
        val port = object : RoutineStateStore {
            override fun load() = disk.load()
            override fun replace(expectedRevision: Long, state: RoutineStoredState) {
                if (reject.compareAndSet(true, false)) throw IOException("controlled write failure")
                disk.replace(expectedRevision, state)
            }
        }
        val book = RoutineBook(stateStore = port); approved(book)
        val calls = AtomicInteger()
        val failures = ConcurrentLinkedQueue<Exception>()
        val worker = pool(root) { calls.incrementAndGet(); reject.set(true); "Original completed observation" }
        val mailbox = AgentMailbox()
        try {
            val dispatcher = RoutineDispatcher(book, worker, mailbox, clock = { 10L }, onStorageFailure = { failures += it })
            assertEquals(1, dispatcher.tick()); assertTrue(worker.awaitIdle(5_000))
            assertEquals(1, failures.size)
            assertTrue(book.pendingChatReports().isEmpty())
            assertNotNull(disk.load().entries.single().runId)
            assertEquals(0, dispatcher.tick())
            assertTrue(mailbox.peek("chief").single().body.contains("Original completed observation"))
            assertEquals(1, calls.get())
            assertEquals(1, disk.load().outbox.size)
        } finally { worker.shutdown() }
    }

    @Test fun ownerStopRevokesRunningClaimBeforeLateResult() = withDirectory { root ->
        val book = RoutineBook(stateStore = store(root)); approved(book)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val worker = pool(root) {
            entered.countDown()
            try {
                while (release.count > 0) try { release.await(100, TimeUnit.MILLISECONDS) }
                catch (_: InterruptedException) { }
                "Late original result"
            } finally { returned.countDown() }
        }
        val mailbox = AgentMailbox()
        try {
            val dispatcher = RoutineDispatcher(book, worker, mailbox, clock = { 10L })
            assertEquals(1, dispatcher.tick()); assertTrue(entered.await(5, TimeUnit.SECONDS))
            val run = book.registrations().single().runId!!
            assertTrue(book.remove("battery"))
            assertNull(book.authorizedRun(run))
            assertTrue(worker.cancel("rt-$run"))
            release.countDown(); assertTrue(returned.await(5, TimeUnit.SECONDS))
            assertTrue(worker.awaitIdle(5_000))
            assertTrue(book.pendingChatReports().isEmpty())
            assertTrue(mailbox.peek("chief").isEmpty())
            assertTrue(RoutineBook(stateStore = store(root)).registrations().isEmpty())
        } finally { release.countDown(); worker.shutdown() }
    }

    @Test fun nextPeriodUsesFreshPlanAndUniqueDurableReceipts() = withDirectory { root ->
        val book = RoutineBook(stateStore = store(root)); approved(book)
        val now = AtomicLong(10)
        val calls = AtomicInteger()
        val worker = pool(root) { "Akku: ${calls.incrementAndGet()} %" }
        val mailbox = AgentMailbox()
        try {
            val dispatcher = RoutineDispatcher(book, worker, mailbox, clock = now::get)
            assertEquals(1, dispatcher.tick()); assertTrue(worker.awaitIdle(5_000))
            now.addAndGet(RoutineBook.MIN_PERIOD_MILLIS)
            assertEquals(1, dispatcher.tick()); assertTrue(worker.awaitIdle(5_000))
            assertEquals(2, calls.get())
            val receipts = FileToolExecutionReceiptStore(File(root, "tool-receipts")).all()
            assertEquals(2, receipts.size)
            assertEquals(2, receipts.map { it.callId }.distinct().size)
            assertTrue(receipts.all { it.state == ToolReceiptState.SUCCEEDED })
            assertEquals(2, mailbox.peek("chief").size)
            assertEquals(2, RoutineBook(stateStore = store(root)).pendingChatReports().size)
        } finally { worker.shutdown() }
    }

    @Test fun restoredOutboxRechecksRevocationAfterUrgentWake() = withDirectory { root ->
        val entries = listOf("first", "second").map { id ->
            val routine = Routine(id, "Prüfe den Akku", RoutineTrigger.Every(RoutineBook.MIN_PERIOD_MILLIS, 1_000),
                setOf("get_battery_info"))
            StoredRoutine(routine, RoutineOwnerApproval.confirmed(routine, 0), 1_000)
        }
        val reports = entries.mapIndexed { index, entry ->
            val run = if (index == 0) "11111111-1111-1111-1111-111111111111"
                else "22222222-2222-2222-2222-222222222222"
            val id = entry.routine.id
            StoredRoutineReport(id, run, "original", AgentMessage("routine-$id-5-${run.replace("-", "")}",
                "routine:$id", "chief", if (index == 0) AgentPriority.URGENT else AgentPriority.INFO,
                "Original report $id"), "original", 5)
        }
        store(root).replace(0, RoutineStoredState(1, entries, reports))
        val book = RoutineBook(stateStore = store(root))
        val mailbox = AgentMailbox(wake = { book.removeAll() })
        val calls = AtomicInteger()
        val worker = pool(root) { calls.incrementAndGet(); "Unexpected execution" }
        try {
            val dispatcher = RoutineDispatcher(book, worker, mailbox, clock = { 10L })
            assertEquals(0, dispatcher.tick())
            assertEquals("Only the pre-revocation enqueue is allowed", listOf(reports.first().message),
                mailbox.peek("chief"))
            assertTrue(book.pendingChatReports().isEmpty())
            assertTrue(book.registrations().isEmpty())
            assertEquals(0, calls.get())
        } finally { worker.shutdown() }
    }

    @Test fun digestAdapterFailureStillProducesDurableCompletion() = withDirectory { root ->
        val book = RoutineBook(stateStore = store(root)); approved(book)
        val calls = AtomicInteger()
        val mailbox = AgentMailbox()
        val worker = pool(root) { calls.incrementAndGet(); "Original successful observation" }
        try {
            val dispatcher = RoutineDispatcher(book, worker, mailbox, clock = { 10L },
                resultDigest = { _, _ -> error("controlled digest failure") })
            assertEquals(1, dispatcher.tick()); assertTrue(worker.awaitIdle(5_000))
            assertEquals(1, calls.get())
            assertEquals("Finished worker must retain a terminal report", 1, book.pendingChatReports().size)
            assertTrue(mailbox.peek("chief").single().body.contains("Ergebnisvergleich fehlgeschlagen"))
            assertTrue(book.pendingChatReports().single().result.contains("Original successful observation"))
            assertNull(book.registrations().single().runId)
            assertNull(book.registrations().single().lastDigest)
        } finally { worker.shutdown() }
    }
}
