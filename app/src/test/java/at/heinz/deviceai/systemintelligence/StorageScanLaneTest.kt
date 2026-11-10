package at.heinz.deviceai.systemintelligence

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class StorageScanLaneTest {
    @Test fun stuckProviderTimesOutWithoutReplacementWorkers() {
        StorageScanLane().use { lane ->
            val release = CountDownLatch(1)
            val entered = CountDownLatch(1)
            val finished = CountDownLatch(1)
            val revoked = AtomicBoolean()
            val start = System.nanoTime()
            try {
                val failure = assertThrows(IllegalStateException::class.java) {
                    lane.run(100, { false }) { stopped ->
                        entered.countDown()
                        try {
                            while (release.count > 0) {
                                try { release.await() } catch (_: InterruptedException) { }
                            }
                            revoked.set(stopped())
                            "late result"
                        } finally { finished.countDown() }
                    }
                }
                assertEquals("STORAGE_SCAN_TIMEOUT", failure.message)
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2_000)
                assertEquals(0L, entered.count)
                repeat(5) {
                    val busy = assertThrows(IllegalStateException::class.java) { lane.run(100, { false }) { "replacement" } }
                    assertEquals("STORAGE_SCAN_BUSY", busy.message)
                }
            } finally {
                release.countDown()
            }
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertTrue(revoked.get())
        }
    }

    @Test fun stopReturnsWhileProviderIgnoresInterruption() {
        StorageScanLane().use { lane ->
            val release = CountDownLatch(1)
            val entered = CountDownLatch(1)
            val stop = AtomicBoolean()
            val outcome = AtomicReference<Throwable?>()
            val caller = Thread {
                try {
                    lane.run(2_000, stop::get) {
                        entered.countDown()
                        while (release.count > 0) {
                            try { release.await() } catch (_: InterruptedException) { }
                        }
                        "late"
                    }
                } catch (failure: Throwable) { outcome.set(failure) }
            }
            try {
                caller.start()
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                stop.set(true)
                caller.join(1_000)
                assertFalse(caller.isAlive)
                assertEquals("STORAGE_SCAN_CANCELLED", outcome.get()?.message)
            } finally {
                release.countDown()
                caller.join(2_000)
            }
        }
    }

    @Test fun cancelledAdmissionDoesNotRunProvider() {
        StorageScanLane().use { lane ->
            val called = AtomicBoolean()
            assertThrows(IllegalStateException::class.java) { lane.run(100, { true }) { called.set(true) } }
            assertFalse(called.get())
            assertEquals("ok", lane.run(100, { false }) { "ok" })
        }
    }
}
