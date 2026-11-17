package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** M2.13: bounded drop-oldest queue (256) and a listener-scoped named daemon dispatch thread. */
class ContinuumMemoryEventQueueTest {
    private fun withHub(block:(File,ContinuumMemoryEventHub)->Unit) {
        val dir=Files.createTempDirectory("m2-queue").toFile()
        try { block(dir,ContinuumMemoryEventHub.forApp(dir)) } finally { dir.deleteRecursively() }
    }
    private fun ev(i:Int)=MemoryStored("lx-%024d".format(i),MemoryEntryStatus.UNCONFIRMED,i.toLong())

    @Test fun overflowDropsOldestKeepsExactly256AndCounts() = withHub { dir,hub ->
        assertEquals(256,ContinuumMemoryEventHub.DEFAULT_QUEUE_CAPACITY)
        val entered=CountDownLatch(1); val release=CountDownLatch(1); val got=CopyOnWriteArrayList<ContinuumMemoryEvent>()
        val h=hub.addListener { e -> got+=e; entered.countDown(); release.await(10,TimeUnit.SECONDS) }
        try {
            hub.emit(ev(0)); assertTrue(entered.await(5,TimeUnit.SECONDS))
            val t0=System.nanoTime()
            (1..300).forEach { hub.emit(ev(it)) }
            assertTrue("emit must never block",(System.nanoTime()-t0)/1e6<500)
            assertEquals(256,hub.queuedForTest()); assertEquals(44L,hub.droppedEvents)
            assertEquals(44L,ContinuumMemoryEventHub.droppedEventCount(dir))
            release.countDown(); ContinuumMemoryEventHub.awaitIdleForTest(dir)
            assertEquals(listOf(ev(0))+(45..300).map(::ev),got.toList())
        } finally { release.countDown();h.close() }
    }

    @Test fun threadIsNamedDaemonStopsAfterLastCloseAndRestarts() = withHub { dir,hub ->
        assertNull("no listener, no thread",hub.dispatchThreadForTest())
        val a=hub.addListener { }; val b=hub.addListener { }
        val t=hub.dispatchThreadForTest()!!
        assertTrue(t.isDaemon); assertTrue(t.name.matches(Regex("continuum-memory-events-[0-9a-f]{8}")))
        a.close(); assertTrue("still one listener",t.isAlive)
        b.close(); t.join(2000); assertFalse(t.isAlive); assertNull(hub.dispatchThreadForTest())
        val got=CopyOnWriteArrayList<ContinuumMemoryEvent>()
        val c=hub.addListener { got+=it }
        try {
            val t2=hub.dispatchThreadForTest()!!; assertTrue(t2!==t && t2.isAlive)
            hub.emit(ev(7)); ContinuumMemoryEventHub.awaitIdleForTest(dir)
            assertEquals(listOf(ev(7)),got.toList())
        } finally { c.close() }
    }

    @Test fun closeAddRaceNeverLosesTheNewListener() = withHub { dir,hub ->
        repeat(200) { i ->
            val got=CopyOnWriteArrayList<ContinuumMemoryEvent>()
            hub.addListener { }.close()
            val h=hub.addListener { got+=it }
            hub.emit(ev(i)); ContinuumMemoryEventHub.awaitIdleForTest(dir)
            assertEquals(listOf(ev(i)),got.toList()); h.close()
        }
    }
}
