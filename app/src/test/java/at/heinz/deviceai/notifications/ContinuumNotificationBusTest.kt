package at.heinz.deviceai.notifications

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContinuumNotificationBusTest {
    private class Recorder : ContinuumNotificationBus.Subscriber {
        val posted = ArrayList<String>()
        val removed = ArrayList<String>()
        override fun onNotificationPosted(notification: IncomingNotification) { posted += notification.key }
        override fun onNotificationRemoved(key: String) { removed += key }
    }

    private fun notification(key: String, text: String = "Hallo") =
        IncomingNotification(key, "com.whatsapp", "WhatsApp", "Anna", text, "msg", false, false, 1L, false)

    @Before fun clean() = ContinuumNotificationBus.resetForTest()

    @After fun cleanAgain() = ContinuumNotificationBus.resetForTest()

    @Test fun subscribersReceivePostedAndRemovedUntilTheyUnsubscribe() {
        val first = Recorder()
        val second = Recorder()
        ContinuumNotificationBus.subscribe(first)
        ContinuumNotificationBus.subscribe(first) // registering twice does not deliver twice
        ContinuumNotificationBus.subscribe(second)
        ContinuumNotificationBus.publish(notification("a"))
        ContinuumNotificationBus.publishRemoved("a")
        ContinuumNotificationBus.unsubscribe(second)
        ContinuumNotificationBus.publish(notification("b"))
        assertEquals(listOf("a", "b"), first.posted)
        assertEquals(listOf("a"), first.removed)
        assertEquals(listOf("a"), second.posted)
        assertEquals(listOf("a"), second.removed)
    }

    @Test fun aFailingSubscriberDoesNotAffectTheOthersOrThePublisher() {
        val recorder = Recorder()
        ContinuumNotificationBus.subscribe(object : ContinuumNotificationBus.Subscriber {
            override fun onNotificationPosted(notification: IncomingNotification) = throw IllegalStateException("boom")
            override fun onNotificationRemoved(key: String) = throw StackOverflowError()
        })
        ContinuumNotificationBus.subscribe(recorder)
        ContinuumNotificationBus.publish(notification("a"))
        ContinuumNotificationBus.publishRemoved("a")
        assertEquals(listOf("a"), recorder.posted)
        assertEquals(listOf("a"), recorder.removed)
    }

    @Test fun recentBufferIsBoundedAndFollowsUpdatesAndRemovals() {
        for (i in 1..40) ContinuumNotificationBus.publish(notification("k$i"))
        assertEquals((25..40).map { "k$it" }, ContinuumNotificationBus.recent().map { it.key })
        assertEquals(ContinuumNotificationBus.MAX_RECENT, ContinuumNotificationBus.recent().size)

        // An update replaces the buffered entry instead of keeping the old content next to it.
        ContinuumNotificationBus.publish(notification("k30", text = "neu"))
        val recent = ContinuumNotificationBus.recent()
        assertEquals(ContinuumNotificationBus.MAX_RECENT, recent.size)
        assertEquals("k30", recent.last().key)
        assertEquals("neu", recent.last().text)
        assertEquals(1, recent.count { it.key == "k30" })

        ContinuumNotificationBus.publishRemoved("k30")
        assertFalse(ContinuumNotificationBus.recent().any { it.key == "k30" })
        ContinuumNotificationBus.clear()
        assertTrue(ContinuumNotificationBus.recent().isEmpty())
    }

    @Test fun concurrentPublishersKeepTheBufferBoundedAndDeliverEverything() {
        val delivered = java.util.concurrent.atomic.AtomicInteger()
        ContinuumNotificationBus.subscribe(object : ContinuumNotificationBus.Subscriber {
            override fun onNotificationPosted(notification: IncomingNotification) { delivered.incrementAndGet() }
        })
        val start = CountDownLatch(1)
        val workers = (0 until 8).map { worker ->
            thread {
                start.await()
                repeat(250) { ContinuumNotificationBus.publish(notification("w$worker-$it")) }
            }
        }
        start.countDown()
        workers.forEach { it.join(TimeUnit.SECONDS.toMillis(20)) }
        assertEquals(2_000, delivered.get())
        val recent = ContinuumNotificationBus.recent()
        assertEquals(ContinuumNotificationBus.MAX_RECENT, recent.size)
        assertEquals(recent.size, recent.map { it.key }.toSet().size)
    }
}
