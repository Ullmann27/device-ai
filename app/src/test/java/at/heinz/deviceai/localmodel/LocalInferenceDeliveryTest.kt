package at.heinz.deviceai.localmodel

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LocalInferenceDeliveryTest {
    @Test fun reentrantCancellationSuppressesQueuedTextWithoutHoldingAMonitor() {
        val queue=LocalInferenceDelivery();val cancelled=AtomicBoolean(false)
        val events=mutableListOf<String>()
        queue.enqueue {events+="first";cancelled.set(true);queue.enqueue {events+="cancelled"};queue.drain()}
        queue.enqueue {if(!cancelled.get())events+="late"}
        queue.drain()
        assertEquals(listOf("first","cancelled"),events)
    }

    @Test fun concurrentDrainersDoNotHoldAQueueMonitorAcrossCallerCode() {
        val queue=LocalInferenceDelivery();val entered=CountDownLatch(1);val resume=CountDownLatch(1)
        queue.enqueue {entered.countDown();assertTrue(resume.await(2,TimeUnit.SECONDS))}
        val thread=Thread {queue.drain()}.also {it.start()}
        assertTrue(entered.await(2,TimeUnit.SECONDS))
        // A reentrant/concurrent native callback can enqueue and return while
        // caller code is active; no opposing JVM monitor is held by the drainer.
        var delivered=false
        queue.enqueue {delivered=true};queue.drain();resume.countDown()
        thread.join(2_000);assertFalse(thread.isAlive);assertTrue(delivered)
    }
}
