package at.heinz.deviceai.chief

import at.heinz.deviceai.routines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RoutineCancelledClaimTest {
    @Test fun interruptedPeriodicClaimPreservesStoreAndAllowsFreshRun() = exercise(false, true)
    @Test fun interruptedEventClaimPreservesStoreAndAllowsFreshRun() = exercise(true, true)
    @Test fun periodicAdmissionRechecksGenerationAfterBookWait() = exercise(false, false)
    @Test fun eventAdmissionRechecksGenerationAfterBookWait() = exercise(true, false)

    private fun exercise(event: Boolean, interrupt: Boolean) {
        val root = Files.createTempDirectory("cancelled-claim-").toFile()
        val store = FileRoutineStateStore(root)
        val book = RoutineBook(stateStore = store)
        val routine = Routine("battery", "Pruefe Akku",
            if (event) RoutineTrigger.OnEvent("owner-app-start")
            else RoutineTrigger.Every(RoutineBook.MIN_PERIOD_MILLIS, 0), setOf("get_battery_info"))
        assertTrue(book.addApproved(routine, RoutineOwnerApproval.confirmed(routine, 0)))
        val before = store.load()
        val allowed = AtomicBoolean(true)
        val calls = AtomicInteger()
        val failure = AtomicReference<Throwable?>()
        val result = AtomicInteger(-1)
        val workers = ChiefWorkerPool(1, 0) { WorkerJob { calls.incrementAndGet(); "Akku 18 %" } }
        val dispatcher = RoutineDispatcher(book, workers, AgentMailbox(), clock = { 10L },
            onAdmission = { if (!allowed.get()) throw WorkerBlocked("Stopped generation") })
        fun dispatch(): Int = if (event) dispatcher.onEvent("owner-app-start", allowed::get)
            else dispatcher.tick(allowed::get)
        val caller = Thread {
            try { result.set(dispatch()) } catch (error: Throwable) { failure.set(error) }
        }
        try {
            synchronized(book) {
                caller.start()
                val end = System.nanoTime() + 5_000_000_000L
                while (caller.state != Thread.State.BLOCKED && System.nanoTime() < end) Thread.sleep(1)
                assertEquals(Thread.State.BLOCKED, caller.state)
                if (interrupt) caller.interrupt() else allowed.set(false)
            }
            caller.join(5_000)
            assertFalse(caller.isAlive)
            assertNull(failure.get())
            assertEquals(0, result.get())
            assertEquals(0, calls.get())
            assertEquals(before, store.load())
            assertEquals(before.entries, book.registrations())
            assertTrue(dispatcher.inFlight().isEmpty())
            allowed.set(true)
            assertEquals(1, dispatch())
            assertTrue(workers.awaitIdle(5_000))
            assertEquals(1, calls.get())
            assertNull(book.registrations().single().runId)
            assertEquals("Akku 18 %", book.pendingChatReports().single().result)
        } finally {
            caller.join(5_000)
            workers.shutdown()
            root.deleteRecursively()
        }
    }
}
