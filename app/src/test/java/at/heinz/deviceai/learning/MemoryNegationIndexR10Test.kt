package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R10 index-generation and polarity invariants; frozen against unchanged R9 source. */
class MemoryNegationIndexR10Test {
    @Test fun soleNegationSelectsOnlyExactStemAcrossReindex() {
        val retriever = CharNgramHashRetriever(dim = 1 shl 14)
        val a = MemoryDoc("negative-nicht", "Licht leuchtet nicht", 1_000L, false)
        val b = MemoryDoc("positive-light", "Licht leuchtet", 2_000L, true)
        retriever.index(listOf(a, b))
        assertEquals(listOf(a), retriever.search("nicht", 3_000L, 3).map { it.doc })
        val c = MemoryDoc("negative-ohne", "Ohne Licht", 4_000L, true)
        retriever.index(listOf(c, b))
        assertTrue(retriever.search("nicht", 5_000L, 3).isEmpty())
        assertEquals(listOf(c), retriever.search("ohne", 5_000L, 3).map { it.doc })
    }

    @Test fun distinctNegationsMustRemainDistinct() {
        val retriever = CharNgramHashRetriever(dim = 1 shl 14)
        val onlyKein = MemoryDoc("kein", "Kein Licht", 1_000L, false)
        val onlyNicht = MemoryDoc("nicht", "Nicht hell", 2_000L, true)
        retriever.index(listOf(onlyKein, onlyNicht))
        assertEquals(listOf(onlyKein), retriever.search("kein", 3_000L, 3).map { it.doc })
        assertEquals(listOf(onlyNicht), retriever.search("nicht", 3_000L, 3).map { it.doc })
    }

    @Test fun mixedTextRetainsExistingCosineAndTimeWeight() {
        val retriever = CharNgramHashRetriever(TemporalCue(java.time.ZoneId.of("Europe/Vienna")), dim = 1 shl 14)
        val docs = listOf(
            MemoryDoc("negative", "Die Lampe leuchtet nicht", 1_700_000_000_000L, false),
            MemoryDoc("positive", "Die Lampe leuchtet", 1_700_000_000_001L, true),
        )
        retriever.index(docs)
        val scored = retriever.scoredParts("Lampe leuchtet nicht", 1_700_000_000_100L, 3)
        assertEquals(2, scored.size)
        assertTrue(scored.all { it.hit.score > 0.0 && it.cosine > 0.0 && it.timeWeight == 1.0 })
        assertEquals(scored.map { it.hit }, retriever.search("Lampe leuchtet nicht", 1_700_000_000_100L, 3))
    }
}
