package at.heinz.deviceai.cockpit

import at.heinz.deviceai.learning.CharNgramHashRetriever
import at.heinz.deviceai.learning.MemoryDoc
import at.heinz.deviceai.learning.TemporalCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** M2.14: CalculationShown only from a real retriever score computation (cosine × time weight). */
class RetrievalCalculationSourceTest {
    private val now=1_800_000_000_000L
    private val day=86_400_000L
    private fun setup():Pair<RetrievalCalculationSource,CalculationEmittingRetriever> {
        val source=RetrievalCalculationSource()
        val r=CalculationEmittingRetriever(CharNgramHashRetriever(TemporalCue(ZoneId.of("Europe/Vienna"))),source)
        r.index(listOf(MemoryDoc("a","Ich habe gestern beim Bäcker Brot gekauft.",now-day,true),
            MemoryDoc("b","Brot habe ich vor zehn Tagen beim Spar gekauft.",now-10*day,true),
            MemoryDoc("h","Hypothese: Vielleicht kaufe ich ein Haus.",now,false)))
        return source to r
    }

    @Test fun realSearchEmitsExactlyOneEventWithTrueStepsAndResult() {
        val (source,r)=setup(); val got=CopyOnWriteArrayList<CockpitAnimationEvent.CalculationShown>()
        source.addListener { got+=it }.use {
            val hits=r.search("Wo habe ich gestern Brot gekauft?",now,3)
            source.awaitIdleForTest()
            val e=got.single()
            val cos=e.steps.first { it.label=="Kosinus" }.value; val w=e.steps.first { it.label=="Zeitgewicht" }.value
            assertEquals(hits.first().score,e.result,1e-12)
            assertEquals(cos*w,e.result,1e-12)
            assertEquals(1.0,w,0.0) // "gestern" window contains doc a
            assertEquals(hits.size.toDouble(),e.steps.first { it.label=="Treffer" }.value,0.0)
            assertEquals(ChartKind.DONUT,e.chart); assertEquals(MEMORY_TURN,e.turnId); assertEquals(now,e.atMillis)
            val text=e.expression+e.steps.joinToString { it.label }+e.unit
            listOf("Brot","Bäcker","Spar","Haus","gestern").forEach { assertFalse("plaintext $it",text.contains(it,true)) }
        }
    }

    @Test fun noCalculationNoEvent() {
        val (source,r)=setup(); val got=CopyOnWriteArrayList<CockpitAnimationEvent.CalculationShown>()
        source.addListener { got+=it }.use {
            r.index(emptyList()); r.search("Brot",now,3)            // nothing computed
            r.search("xyzzyq",now,3)                                  // computed but no hit -> nothing shown
            source.awaitIdleForTest(); assertTrue(got.isEmpty())
        }
    }

    @Test fun nothingAfterCloseAndThrowingListenerIsIsolated() {
        val (source,r)=setup(); val got=CopyOnWriteArrayList<CockpitAnimationEvent.CalculationShown>()
        val bad=source.addListener { throw IllegalStateException("boom") }
        val good=source.addListener { got+=it }
        r.search("Brot gekauft",now,3); source.awaitIdleForTest()
        assertEquals(1,got.size)
        val worker=checkNotNull(source.dispatchThreadForTest())
        good.close(); bad.close()
        r.search("Brot gekauft",now,3); source.awaitIdleForTest()
        assertEquals(1,got.size)
        // An empty queue is not thread termination: close only notifies the dispatcher.
        // Join the actual worker outside the source monitor before asserting shutdown.
        worker.join(5_000L)
        assertFalse("dispatcher must terminate after the final listener closes",worker.isAlive)
        assertEquals(null,source.dispatchThreadForTest())
    }

    @Test fun blockedListenerNeverBlocksSearchAndQueueIsBounded() {
        val (source,r)=setup(); val entered=CountDownLatch(1); val release=CountDownLatch(1)
        val h=source.addListener { entered.countDown(); release.await(10,TimeUnit.SECONDS) }
        try {
            r.search("Brot gekauft",now,3); assertTrue(entered.await(5,TimeUnit.SECONDS))
            val t0=System.nanoTime(); repeat(300) { r.search("Brot gekauft",now,3) }
            assertTrue((System.nanoTime()-t0)/1e6<2000)
            assertEquals(256,source.queuedForTest()); assertEquals(44L,source.droppedEvents)
            val t=source.dispatchThreadForTest()!!; assertTrue(t.isDaemon && t.name.startsWith("continuum-calc-events-"))
        } finally { release.countDown();h.close() }
    }
}
