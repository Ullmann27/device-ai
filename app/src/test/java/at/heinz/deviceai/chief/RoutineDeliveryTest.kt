package at.heinz.deviceai.chief

import at.heinz.deviceai.routines.AgentMailbox
import at.heinz.deviceai.routines.AgentMessage
import at.heinz.deviceai.routines.AgentPriority
import at.heinz.deviceai.routines.Routine
import at.heinz.deviceai.routines.RoutineBook
import at.heinz.deviceai.routines.RoutineTrigger
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class RoutineDeliveryTest {
    private fun terminalReportWaitsForMailbox(status: WorkerStatus) {
        val book = RoutineBook()
        assertTrue(book.add(Routine("event-report", "Report observed result", RoutineTrigger.OnEvent("ready"))))
        val mailbox = AgentMailbox(maxPerAgent = 1)
        assertTrue(mailbox.send(AgentMessage("occupied", "fixture", "chief", AgentPriority.INFO, "Already queued")))
        val executions = AtomicInteger()
        val pool = ChiefWorkerPool(1, 0) { WorkerJob {
            executions.incrementAndGet()
            when (status) {
                WorkerStatus.DONE -> "Original completed result"
                WorkerStatus.BLOCKED -> throw WorkerBlocked("Original approval blocker")
                else -> error("Original failure")
            }
        } }
        try {
            val dispatcher = RoutineDispatcher(book, pool, mailbox, clock = { 10L })
            assertEquals(1, dispatcher.onEvent("ready"))
            assertTrue(pool.awaitIdle(5_000))
            assertEquals(1, executions.get())
            assertEquals("occupied", mailbox.drain("chief").single().id)
            assertEquals("Retry only delivery, never the worker", 0, dispatcher.tick())
            val delivered = mailbox.drain("chief")
            assertEquals("Completed event result was lost after mailbox rejection", 1, delivered.size)
            assertTrue(delivered.single().body.contains(when (status) {
                WorkerStatus.DONE -> "Original completed result"
                WorkerStatus.BLOCKED -> "Original approval blocker"
                else -> "Fehlgeschlagen: IllegalStateException"
            }))
            assertEquals(if (status == WorkerStatus.BLOCKED) AgentPriority.URGENT else AgentPriority.INFO,
                delivered.single().priority)
            assertEquals(1, executions.get())
            assertTrue(dispatcher.inFlight().isEmpty())
            assertEquals(0, dispatcher.tick())
            assertEquals(0, mailbox.pending("chief"))
        } finally { pool.shutdown() }
    }

    @Test fun fullMailboxRetainsCompletedEventResult() = terminalReportWaitsForMailbox(WorkerStatus.DONE)
    @Test fun fullMailboxRetainsBlockedEventResult() = terminalReportWaitsForMailbox(WorkerStatus.BLOCKED)
    @Test fun fullMailboxRetainsFailedEventResult() = terminalReportWaitsForMailbox(WorkerStatus.FAILED)

    @Test fun pendingDeliveryKeepsRoutineClaimAndPreventsWorkerReplay() {
        val book = RoutineBook()
        assertTrue(book.add(Routine("held", "Read once", RoutineTrigger.OnEvent("ready"))))
        val mailbox = AgentMailbox(maxPerAgent = 1)
        assertTrue(mailbox.send(AgentMessage("occupied", "fixture", "chief", AgentPriority.INFO, "Already queued")))
        val executions = AtomicInteger()
        val pool = ChiefWorkerPool(1, 0) { WorkerJob { executions.incrementAndGet(); "Original result" } }
        try {
            val dispatcher = RoutineDispatcher(book, pool, mailbox, clock = { 10L })
            assertEquals(1, dispatcher.onEvent("ready"))
            assertTrue(pool.awaitIdle(5_000))
            assertEquals(setOf("held"), dispatcher.inFlight())
            assertEquals(0, dispatcher.onEvent("ready"))
            assertTrue(book.triggered("ready").isEmpty())
            assertEquals(1, executions.get())
            mailbox.drain("chief")
            assertEquals(0, dispatcher.tick())
            assertEquals("Original result", mailbox.drain("chief").single().body)
            assertEquals(1, executions.get())
        } finally { pool.shutdown() }
    }

    @Test fun allValidLongRoutineIdsDeliverWithinExistingMailboxBounds() {
        for (length in 57..64) {
            val id = "r".repeat(length)
            val book = RoutineBook()
            assertTrue(book.add(Routine(id, "Read once", RoutineTrigger.OnEvent("ready"))))
            val mailbox = AgentMailbox()
            val pool = ChiefWorkerPool(1, 0) { WorkerJob { "Result for $id" } }
            try {
                val dispatcher = RoutineDispatcher(book, pool, mailbox, clock = { 10L })
                assertEquals(1, dispatcher.onEvent("ready"))
                assertTrue(pool.awaitIdle(5_000))
                val delivered = mailbox.drain("chief")
                assertEquals("Valid $length-character routine ID must be deliverable", 1, delivered.size)
                assertTrue(delivered.single().from.length in 1..64)
                assertTrue(delivered.single().id.contains(id))
                assertEquals("Result for $id", delivered.single().body)
            } finally { pool.shutdown() }
        }
    }

    @Test fun removingAndReaddingRoutineDiscardsOnlyItsOldPendingReport() {
        val book = RoutineBook(maxRoutines = 1)
        val trigger = RoutineTrigger.OnEvent("ready")
        assertTrue(book.add(Routine("same", "Old result", trigger)))
        val mailbox = AgentMailbox(maxPerAgent = 1)
        assertTrue(mailbox.send(AgentMessage("occupied", "fixture", "chief", AgentPriority.INFO, "Already queued")))
        val executions = AtomicInteger()
        val pool = ChiefWorkerPool(1, 0) { brief -> WorkerJob { executions.incrementAndGet(); brief.goal } }
        try {
            val dispatcher = RoutineDispatcher(book, pool, mailbox, clock = { 10L })
            assertEquals(1, dispatcher.onEvent("ready"))
            assertTrue(pool.awaitIdle(5_000))
            assertTrue(book.remove("same"))
            assertTrue(book.add(Routine("same", "Replacement result", trigger)))
            mailbox.drain("chief")
            assertEquals(1, dispatcher.onEvent("ready"))
            assertTrue(pool.awaitIdle(5_000))
            assertEquals(listOf("Replacement result"), mailbox.drain("chief").map { it.body })
            assertEquals(2, executions.get())
            assertTrue(dispatcher.inFlight().isEmpty())
            assertEquals(0, dispatcher.tick())
        } finally { pool.shutdown() }
    }

    @Test fun lateRemovedWorkerCannotAcknowledgeReplacementClaim() {
        val book = RoutineBook(maxRoutines = 1)
        val trigger = RoutineTrigger.OnEvent("ready")
        assertTrue(book.add(Routine("same", "Old result", trigger)))
        val oldStarted = CountDownLatch(1)
        val newStarted = CountDownLatch(1)
        val oldRelease = CountDownLatch(1)
        val newRelease = CountDownLatch(1)
        val oldReportFinished = CountDownLatch(1)
        val pool = ChiefWorkerPool(2, 1) { brief -> WorkerJob {
            when (brief.goal) {
                "Old result" -> { oldStarted.countDown(); check(oldRelease.await(5, TimeUnit.SECONDS)) }
                "Replacement result" -> { newStarted.countDown(); check(newRelease.await(5, TimeUnit.SECONDS)) }
            }
            brief.goal
        } }
        try {
            val mailbox = AgentMailbox()
            val dispatcher = RoutineDispatcher(book, pool, mailbox, clock = { 10L })
            assertEquals(1, dispatcher.onEvent("ready"))
            assertTrue(oldStarted.await(5, TimeUnit.SECONDS))
            assertTrue(book.remove("same"))
            assertTrue(book.add(Routine("same", "Replacement result", trigger)))
            assertEquals(1, dispatcher.onEvent("ready"))
            assertTrue(newStarted.await(5, TimeUnit.SECONDS))
            assertTrue(pool.submit(WorkerBrief("after-old-report", "Sentinel", emptySet())) {
                oldReportFinished.countDown()
            })
            oldRelease.countDown()
            assertTrue(oldReportFinished.await(5, TimeUnit.SECONDS))
            assertEquals("Old result must not enter the replacement mailbox", 0, mailbox.pending("chief"))
            assertEquals(setOf("same"), dispatcher.inFlight())
            assertTrue("Replacement still owns its claim", book.triggered("ready").isEmpty())
            newRelease.countDown()
            assertTrue(pool.awaitIdle(5_000))
            assertEquals(listOf("Replacement result"), mailbox.drain("chief").map { it.body })
        } finally {
            oldRelease.countDown()
            newRelease.countDown()
            pool.shutdown()
        }
    }

    @Test fun urgentRetryKeepsStableMessageAndClaimThroughReentrantWake() {
        val book = RoutineBook()
        assertTrue(book.add(Routine("urgent", "Need approval", RoutineTrigger.OnEvent("ready"))))
        val now = AtomicLong(10L)
        val wakeEntered = CountDownLatch(1)
        val wakeRelease = CountDownLatch(1)
        val wakeCompleted = CountDownLatch(1)
        val woken = ConcurrentLinkedQueue<AgentMessage>()
        val reentrant = ConcurrentLinkedQueue<Int>()
        val claimsDuringWake = ConcurrentLinkedQueue<Set<String>>()
        lateinit var dispatcher: RoutineDispatcher
        val mailbox = AgentMailbox(maxPerAgent = 1, wake = {
            woken += it
            reentrant += dispatcher.tick()
            reentrant += dispatcher.onEvent("ready")
            claimsDuringWake += dispatcher.inFlight()
            wakeEntered.countDown()
            if (wakeRelease.await(5, TimeUnit.SECONDS)) wakeCompleted.countDown()
        })
        assertTrue(mailbox.send(AgentMessage("occupied", "fixture", "chief", AgentPriority.INFO, "Already queued")))
        val executions = AtomicInteger()
        val pool = ChiefWorkerPool(1, 0) { WorkerJob {
            executions.incrementAndGet()
            throw WorkerBlocked("Owner confirmation required")
        } }
        val callers = Executors.newFixedThreadPool(2)
        try {
            dispatcher = RoutineDispatcher(book, pool, mailbox, clock = now::get)
            assertEquals(1, dispatcher.onEvent("ready"))
            assertTrue(pool.awaitIdle(5_000))
            mailbox.drain("chief")
            now.set(50L)
            val firstRetry = callers.submit<Int> { dispatcher.tick() }
            assertTrue(wakeEntered.await(5, TimeUnit.SECONDS))
            val concurrent = callers.submit<Int> { dispatcher.onEvent("ready") }
            assertEquals(0, concurrent.get(2, TimeUnit.SECONDS))
            assertEquals(listOf(0, 0), reentrant.toList())
            assertEquals(listOf(setOf("urgent")), claimsDuringWake.toList())
            assertEquals(1, executions.get())
            wakeRelease.countDown()
            assertEquals(0, firstRetry.get(5, TimeUnit.SECONDS))
            assertTrue(wakeCompleted.await(1, TimeUnit.SECONDS))
            val delivered = mailbox.drain("chief").single()
            assertEquals(delivered, woken.single())
            assertTrue("Retry preserves the original report identity", delivered.id.contains("-10-"))
            assertTrue(dispatcher.inFlight().isEmpty())
            assertEquals(0, dispatcher.tick())
            assertEquals(0, mailbox.pending("chief"))
        } finally {
            wakeRelease.countDown()
            callers.shutdownNow()
            pool.shutdown()
        }
    }

    @Test fun delayedDeliveryAdvancesScheduleOnlyAfterMailboxAccepts() {
        val now = AtomicLong()
        val period = RoutineBook.MIN_PERIOD_MILLIS
        val book = RoutineBook()
        assertTrue(book.add(Routine("scheduled", "Read once per period", RoutineTrigger.Every(period, 0L))))
        val mailbox = AgentMailbox(maxPerAgent = 1)
        assertTrue(mailbox.send(AgentMessage("occupied", "fixture", "chief", AgentPriority.INFO, "Already queued")))
        val executions = AtomicInteger()
        val pool = ChiefWorkerPool(1, 0) { WorkerJob { executions.incrementAndGet(); "Unchanged observation" } }
        try {
            val dispatcher = RoutineDispatcher(book, pool, mailbox, clock = now::get)
            assertEquals(1, dispatcher.tick())
            assertTrue(pool.awaitIdle(5_000))
            now.set(period * 2)
            assertEquals(0, dispatcher.tick())
            assertEquals(1, executions.get())
            mailbox.drain("chief")
            assertEquals(0, dispatcher.tick())
            assertEquals("Unchanged observation", mailbox.drain("chief").single().body)
            now.set(period * 3 - 1)
            assertEquals(0, dispatcher.tick())
            now.incrementAndGet()
            assertEquals(1, dispatcher.tick())
            assertTrue(pool.awaitIdle(5_000))
            assertEquals(2, executions.get())
            assertEquals("Accepted digest still suppresses unchanged results", 0, mailbox.pending("chief"))
            assertTrue(dispatcher.inFlight().isEmpty())
        } finally { pool.shutdown() }
    }
}
