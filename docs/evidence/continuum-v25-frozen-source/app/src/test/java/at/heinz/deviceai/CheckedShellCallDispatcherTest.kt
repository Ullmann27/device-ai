package at.heinz.deviceai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class CheckedShellCallDispatcherTest {
    @Test fun blockedBinderNeverStartsASecondCheckedCommand() {
        val dispatcher = CheckedShellCallDispatcher()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val first = AtomicReference<Result<String>?>(null)
        val second = AtomicReference<Result<String>?>(null)
        val actualStarts = AtomicInteger()
        try {
            dispatcher.execute({
                actualStarts.incrementAndGet()
                entered.countDown()
                check(release.await(3, TimeUnit.SECONDS))
                "verified"
            }, { first.set(it); completed.countDown() })
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            dispatcher.execute({
                actualStarts.incrementAndGet()
                "should-never-run"
            }, second::set)
            assertEquals(1, actualStarts.get())
            assertTrue(second.get()!!.isFailure)
            assertNull(first.get())
        } finally {
            release.countDown()
        }
        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals("verified", first.get()!!.getOrThrow())
    }

    @Test fun repeatedOverlappingCallsCannotCreateUnboundedWorkerThreads() {
        val dispatcher = CheckedShellCallDispatcher()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val actualStarts = AtomicInteger()
        val rejected = AtomicInteger()
        val complete = CountDownLatch(1)
        try {
            dispatcher.execute({
                actualStarts.incrementAndGet()
                started.countDown()
                check(release.await(3, TimeUnit.SECONDS))
                "first"
            }, { complete.countDown() })
            assertTrue(started.await(1, TimeUnit.SECONDS))
            repeat(100) {
                dispatcher.execute({ actualStarts.incrementAndGet(); "forbidden" }) {
                    if (it.isFailure) rejected.incrementAndGet()
                }
            }
            assertEquals(1, actualStarts.get())
            assertEquals(100, rejected.get())
        } finally {
            release.countDown()
        }
        assertTrue(complete.await(2, TimeUnit.SECONDS))
    }

    @Test fun sessionInvalidationBeforeBinderReplyForcesFailure() {
        val dispatcher = CheckedShellCallDispatcher()
        val generation = AtomicLong(7)
        val expected = generation.get()
        val started = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val complete = CountDownLatch(1)
        val observed = AtomicReference<Result<String>?>(null)
        try {
            dispatcher.execute({
                started.countDown()
                check(resume.await(3, TimeUnit.SECONDS))
                check(generation.get() == expected) { "service changed" }
                "stale success"
            }, { observed.set(it); complete.countDown() })
            assertTrue(started.await(1, TimeUnit.SECONDS))
            generation.incrementAndGet()
        } finally {
            resume.countDown()
        }
        assertTrue(complete.await(2, TimeUnit.SECONDS))
        assertTrue(observed.get()!!.isFailure)
    }

    @Test fun failedTaskReleasesTheSingleFlightSlot() {
        val dispatcher = CheckedShellCallDispatcher()
        val failed = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val observed = AtomicReference<Result<String>?>(null)
        dispatcher.execute({ error("transport failed") }, { observed.set(it); failed.countDown() })
        assertTrue(failed.await(1, TimeUnit.SECONDS))
        // Completion is delivered only after the previous Binder slot is free.
        dispatcher.execute({ "recovered" }) {
            if (it.isSuccess) finished.countDown()
        }
        assertTrue(finished.await(1, TimeUnit.SECONDS))
        assertTrue(observed.get()!!.isFailure)
    }

    @Test fun threadCreationFailureIsRejectedAndSlotIsReclaimed() {
        val failures = AtomicInteger()
        val dispatcher = CheckedShellCallDispatcher { task ->
            if (failures.getAndIncrement() == 0) throw IllegalStateException("thread denied")
            Thread(task, "checked-test").apply { isDaemon = true }
        }
        val first = AtomicReference<Result<String>?>(null)
        dispatcher.execute({ "not-run" }, first::set)
        assertTrue(first.get()!!.isFailure)
        val second = AtomicReference<Result<String>?>(null)
        val complete = CountDownLatch(1)
        dispatcher.execute({ "now-runs" }, { second.set(it); complete.countDown() })
        assertTrue(complete.await(1, TimeUnit.SECONDS))
        assertEquals("now-runs", second.get()!!.getOrThrow())
    }
}
