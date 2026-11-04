package at.heinz.deviceai.cockpit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Scratch diagnostic, outside the application source sets; its original assumption must fail. */
class ShutdownRaceProofTest {
    @Test fun idleCannotPromiseWorkerTerminationBeforeTheWorkerReacquiresItsMonitor() {
        val source = RetrievalCalculationSource()
        val subscription = source.addListener { }
        val worker = checkNotNull(source.dispatchThreadForTest())
        val field = RetrievalCalculationSource::class.java.getDeclaredField("lock")
        field.isAccessible = true
        val monitor = field.get(source)
        try {
            // Control only thread scheduling: the source itself still owns removal, notification,
            // idle detection and shutdown. Intrinsic locks are reentrant on this test thread.
            synchronized(monitor) {
                subscription.close()
                source.awaitIdleForTest()
                assertNull("Idle returned before the notified worker could terminate", source.dispatchThreadForTest())
            }
        } finally {
            subscription.close()
            worker.join(5_000L)
            assertFalse("The original worker must still terminate once scheduling is released", worker.isAlive)
            assertNull(source.dispatchThreadForTest())
        }
    }
}
