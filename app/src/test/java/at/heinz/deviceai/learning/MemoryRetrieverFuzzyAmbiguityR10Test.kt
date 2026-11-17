package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frozen before R10 production changes: never promote an ambiguous correction to remembered evidence. */
class MemoryRetrieverFuzzyAmbiguityR10Test {
    @Test fun equalDistanceAndFrequencyMustAbstain() {
        val retriever = Bm25GermanRetriever()
        retriever.index(listOf(
            MemoryDoc("ball", "Ball", 1_000L, true),
            MemoryDoc("bald", "Bald", 1_000L, false),
        ))
        assertTrue("balk is equally close to ball and bald", retriever.search("balk", 2_000L, 3).isEmpty())
    }

    @Test fun uniqueTypoStillRetrievesOriginalConfirmation() {
        val retriever = Bm25GermanRetriever()
        val doc = MemoryDoc("tisch", "Tisch", 1_000L, false)
        retriever.index(listOf(doc))
        val actual = retriever.search("tisck", 2_000L, 1).single()
        assertEquals(doc, actual.doc)
        assertTrue(!actual.confirmed)
    }

    @Test fun documentFrequencyBreaksOtherwiseAmbiguousTie() {
        val retriever = Bm25GermanRetriever()
        retriever.index(listOf(
            MemoryDoc("ball-1", "Ball", 1_000L, true),
            MemoryDoc("ball-2", "Ball", 2_000L, false),
            MemoryDoc("bald-1", "Bald", 3_000L, true),
        ))
        val hits = retriever.search("balk", 4_000L, 3)
        assertEquals(2, hits.size)
        assertTrue(hits.all { it.doc.id.startsWith("ball-") })
    }

    @Test fun exactTermsStillOverrideTypoDisambiguation() {
        val retriever = Bm25GermanRetriever()
        val exact = MemoryDoc("exact", "Balk", 1_000L, true)
        retriever.index(listOf(exact, MemoryDoc("ball", "Ball", 2_000L, false), MemoryDoc("bald", "Bald", 3_000L, false)))
        assertEquals(exact, retriever.search("balk", 4_000L, 1).single().doc)
    }
}
