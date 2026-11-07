package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frozen evidence tests: duplicate negative tokens must not bypass polarity gate. */
class MemoryRepeatedNegationR11Test {
    @Test fun duplicatedNichtCannotRetrievePositiveLicht() {
        val r = CharNgramHashRetriever(dim = 1 shl 14)
        val positive = MemoryDoc("light", "Licht", 100L, true)
        val negative = MemoryDoc("not", "Nicht", 200L, false)
        r.index(listOf(positive, negative))
        assertEquals(listOf(negative), r.search("nicht nicht", 1_000L, 5).map { it.doc })
    }

    @Test fun duplicatedOhneCannotRetrievePositiveBohnen() {
        val r = CharNgramHashRetriever(dim = 1 shl 14)
        val positive = MemoryDoc("beans", "Bohnen", 100L, true)
        val negative = MemoryDoc("without", "Ohne", 200L, false)
        r.index(listOf(positive, negative))
        assertEquals(listOf(negative), r.search("ohne ohne", 1_000L, 5).map { it.doc })
    }

    @Test fun duplicatedNichtAfterReindexMustNotReusePriorMembership() {
        val r = CharNgramHashRetriever(dim = 1 shl 14)
        r.index(listOf(MemoryDoc("not", "Nicht", 100L, false)))
        assertEquals(1, r.search("nicht nicht", 1_000L, 3).size)
        r.index(listOf(MemoryDoc("light", "Licht", 200L, true)))
        assertTrue(r.search("nicht nicht", 1_000L, 3).isEmpty())
    }

    @Test fun oneNegationAndMixedContentRetainExistingBehavior() {
        val r = CharNgramHashRetriever(dim = 1 shl 14)
        val positive = MemoryDoc("light", "Licht brennt", 100L, true)
        val negative = MemoryDoc("not", "Licht brennt nicht", 200L, false)
        r.index(listOf(positive, negative))
        assertEquals(listOf(negative), r.search("nicht", 1_000L, 3).map { it.doc })
        assertEquals(2, r.search("Licht brennt nicht", 1_000L, 3).size)
    }
}
