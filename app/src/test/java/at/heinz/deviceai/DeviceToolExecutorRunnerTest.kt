package at.heinz.deviceai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class DeviceToolExecutorRunnerTest {
    private fun request() = ToolCallRequest("checked-callback", "get_device_info", emptyMap())

    @Test fun successfulCallbackReturnsOnlyItsActualValue() {
        val runner = DeviceToolExecutorRunner(
            { _, callback -> callback(Result.success("measured-output")) }, 100
        )
        assertEquals("measured-output", runner.execute(request()))
    }

    @Test fun failedCallbackCannotBecomeSuccessfulOutput() {
        val runner = DeviceToolExecutorRunner(
            { _, callback -> callback(Result.failure(IllegalStateException("broken backend"))) }, 100
        )
        val failure = assertThrows(IllegalStateException::class.java) {
            runner.execute(request())
        }
        assertEquals("broken backend", failure.message)
    }

    @Test fun missingCallbackTimesOutWithoutUnboundedWait() {
        val runner = DeviceToolExecutorRunner({ _, _ -> }, 50)
        val start = System.nanoTime()
        val failure = assertThrows(ToolBackendFailure::class.java) {
            runner.execute(request())
        }
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        assertEquals(ToolExecutionStatus.TIMED_OUT, failure.status)
        assertTrue("callback wait exceeded deadline: $elapsed ms", elapsed < 1_000)
    }

    @Test fun lateSuccessCannotChangeAnAlreadyReportedTimeout() {
        val callback = AtomicReference<((Result<String>) -> Unit)?>(null)
        val runner = DeviceToolExecutorRunner(
            { _, completion -> callback.set(completion) }, 25
        )
        val failure = assertThrows(ToolBackendFailure::class.java) {
            runner.execute(request())
        }
        assertEquals(ToolExecutionStatus.TIMED_OUT, failure.status)
        assertNotNull(callback.get())
        callback.get()!!.invoke(Result.success("too-late-to-certify"))
    }

    @Test fun repeatedCallbacksKeepTheFirstResultOnly() {
        val runner = DeviceToolExecutorRunner({ _, callback ->
            callback(Result.failure(IllegalStateException("first result failed")))
            callback(Result.success("forged retry"))
        }, 100)
        val failure = assertThrows(IllegalStateException::class.java) {
            runner.execute(request())
        }
        assertEquals("first result failed", failure.message)
    }

    @Test fun interruptRestoresFlagAndDoesNotBecomeSuccess() {
        val callbackInstalled = CountDownLatch(1)
        val observed = AtomicReference<ToolExecutionStatus?>(null)
        val interrupted = AtomicReference<Boolean?>(null)
        val runner = DeviceToolExecutorRunner({ _, _ ->
            callbackInstalled.countDown()
        }, 5_000)
        val thread = Thread {
            try {
                runner.execute(request())
            } catch (error: ToolBackendFailure) {
                observed.set(error.status)
                interrupted.set(Thread.currentThread().isInterrupted)
            }
        }
        thread.start()
        assertTrue(callbackInstalled.await(1, TimeUnit.SECONDS))
        thread.interrupt()
        thread.join(1_000)
        assertFalse("cancelled waiter is still active", thread.isAlive)
        assertEquals(ToolExecutionStatus.CANCELLED, observed.get())
        assertEquals(true, interrupted.get())
    }
}
