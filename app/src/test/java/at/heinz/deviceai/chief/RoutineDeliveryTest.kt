package at.heinz.deviceai.chief

import at.heinz.deviceai.routines.AgentMailbox
import at.heinz.deviceai.routines.AgentMessage
import at.heinz.deviceai.routines.AgentPriority
import at.heinz.deviceai.routines.Routine
import at.heinz.deviceai.routines.RoutineBook
import at.heinz.deviceai.routines.RoutineTrigger
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

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
}
