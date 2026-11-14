package at.heinz.deviceai.routines

import at.heinz.deviceai.FileToolExecutionReceiptStore
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolReceiptState
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.chief.ChiefJobDeps
import at.heinz.deviceai.chief.ChiefJobs
import at.heinz.deviceai.chief.ChiefWorkerPool
import at.heinz.deviceai.chief.RoutineDispatcher
import at.heinz.deviceai.chief.UnattendedApproval
import at.heinz.deviceai.cognitive.AgentLimits
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.cognitive.LocalChatTranscriptStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class OwnerRoutineConditionalDeliveryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun batteryThresholdKeepsSilentReadingsAndReportsOnlyNewLowReadings() {
        val root = temporary.newFolder()
        val book = RoutineBook(stateStore = FileRoutineStateStore(File(root, "routines")))
        val target = OwnerRoutineTarget.LOW_BATTERY
        val routine = OwnerRoutineDraft(target, RoutineBook.MIN_PERIOD_MILLIS).routine("battery", 0)
        assertTrue(book.addApproved(routine, RoutineOwnerApproval.confirmed(routine, 0)))
        val now = AtomicLong(1)
        val readings = listOf(50, 25, 19, 18, 18, 25, 18)
        val calls = AtomicInteger()
        val receipts = FileToolExecutionReceiptStore(File(root, "tool-receipts"))
        val mailbox = AgentMailbox()
        val transcript = LocalChatTranscriptStore(File(root, "chat-history"))
        val conversation = UUID.randomUUID().toString()
        val delivery = RoutineChatDelivery(book, mailbox, transcript)
        val pool = ChiefWorkerPool(1, 0) { brief ->
            val plan = target.plan()
            ChiefJobs.agentJob(brief, ChiefJobDeps(plan, ToolRunner {
                "Aktueller Akkustand: ${readings[calls.getAndIncrement()]} % (Android-Batteriedienst).\nLadezustand: entlädt sich."
            }, ToolPolicy(readOnlyEnabled = true, maxCalls = 1), null, plan, InMemoryExperienceStore(),
                UnattendedApproval, limits = AgentLimits(maxSteps = 24, maxToolCalls = 1, maxRetries = 0),
                receiptStore = receipts))
        }
        try {
            val dispatcher = RoutineDispatcher(book, pool, mailbox, clock = now::get,
                resultDigest = { approved, result -> requireNotNull(OwnerRoutineTarget.from(approved)).digest(result) },
                reportWhen = { approved, result -> requireNotNull(OwnerRoutineTarget.from(approved)).reports(result) })
            val expectedCounts = listOf(0, 0, 1, 2, 2, 2, 3)
            for (index in readings.indices) {
                assertEquals(1, dispatcher.tick())
                assertTrue(pool.awaitIdle(5_000))
                mailbox.peek("chief").forEach { delivery.deliver(it.id, conversation) }
                assertEquals("Reading ${readings[index]}", expectedCounts[index], transcript.load(conversation).size)
                assertTrue(book.registrations().single().lastResult!!.contains("${readings[index]} %"))
                now.addAndGet(RoutineBook.MIN_PERIOD_MILLIS)
            }
            assertEquals(7, calls.get())
            assertEquals(7, receipts.all().map { it.callId }.distinct().size)
            assertTrue(receipts.all().all { it.state == ToolReceiptState.SUCCEEDED })
            assertTrue(book.pendingChatReports().isEmpty())
            assertEquals(listOf(19, 18, 18), transcript.load(conversation).map {
                Regex("Akkustand: ([0-9]+)").find(it.text)!!.groupValues[1].toInt()
            })
        } finally { pool.shutdown() }
    }
}
