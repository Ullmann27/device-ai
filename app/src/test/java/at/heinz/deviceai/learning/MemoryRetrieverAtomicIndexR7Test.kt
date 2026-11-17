package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** R7 freeze: a reader must see one COMPLETE index generation, never mixed doc IDs, scores or sizes. */
class MemoryRetrieverAtomicIndexR7Test {
    @Test fun bm25ConcurrentReindexPublishesOneCompleteGeneration() {
        checkConcurrentPublication(Bm25GermanRetriever()) { Bm25GermanRetriever() }
    }

    @Test fun charNgramConcurrentReindexPublishesOneCompleteGeneration() {
        checkConcurrentPublication(CharNgramHashRetriever(dim=1 shl 14)) { CharNgramHashRetriever(dim=1 shl 14) }
    }

    private fun checkConcurrentPublication(
        retriever:ContinuumMemoryRetriever,
        newReference:()->ContinuumMemoryRetriever,
    ) {
        val old=listOf(MemoryDoc("old","sonnenfunken",1000L,true))
        val next=(0 until 240).map { i ->
            MemoryDoc("fresh-$i","sonnenfunken "+"wasserleuchten ".repeat(30)+"ziffer$i",2000L,false)
        }
        retriever.index(old)
        val oldHit=retriever.search("sonnenfunken",3000L,1).single()
        val oldBytes=retriever.approxIndexBytes()
        val reference=newReference()
        reference.index(next)
        val nextHit=reference.search("sonnenfunken",3000L,1).single()
        val nextBytes=reference.approxIndexBytes()
        assertTrue("the two complete generations must have distinguishable scores",abs(oldHit.score-nextHit.score)>1e-5)
        assertTrue("the two complete generations must have distinguishable sizes",oldBytes!=nextBytes)

        val ready=CountDownLatch(3)
        val go=CountDownLatch(1)
        val completed=AtomicBoolean(false)
        val writerError=AtomicReference<Throwable?>(null)
        val samples=AtomicInteger(0)
        val invalid=AtomicInteger(0)
        val exceptions=AtomicInteger(0)
        val writer=Thread {
            try { go.await(); retriever.index(next) }
            catch (e:Throwable) { writerError.set(e) }
            finally { completed.set(true) }
        }
        val readers=(0 until 3).map {
            Thread {
                ready.countDown()
                go.await()
                while (!completed.get()) {
                    try {
                        val hit=retriever.search("sonnenfunken",3000L,1).singleOrNull()
                        val bytes=retriever.approxIndexBytes()
                        val matchesOld=hit?.doc==oldHit.doc && abs(hit.score-oldHit.score)<1e-7
                        val matchesNext=hit?.doc==nextHit.doc && abs(hit.score-nextHit.score)<1e-5
                        if ((!matchesOld && !matchesNext) || (bytes!=oldBytes && bytes!=nextBytes)) invalid.incrementAndGet()
                        samples.incrementAndGet()
                    } catch (_:Throwable) { exceptions.incrementAndGet() }
                }
            }
        }
        writer.start()
        readers.forEach { it.start() }
        assertTrue("readers must start",ready.await(5,TimeUnit.SECONDS))
        go.countDown()
        writer.join(15000)
        assertTrue("writer did not finish",!writer.isAlive)
        readers.forEach { it.join(5000); assertTrue("reader did not finish",!it.isAlive) }
        assertEquals("writer failed",null,writerError.get())
        assertTrue("the concurrent test must sample live searches",samples.get()>0)
        assertEquals("a score was attributed to a foreign or partial generation",0,invalid.get())
        assertEquals("search or index-size read threw during reindex",0,exceptions.get())
        assertEquals("the complete new generation must be visible",nextHit,retriever.search("sonnenfunken",3000L,1).single())
        assertEquals(nextBytes,retriever.approxIndexBytes())
    }
}
