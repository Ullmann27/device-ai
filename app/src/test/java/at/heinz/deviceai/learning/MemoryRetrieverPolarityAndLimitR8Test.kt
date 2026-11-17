package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R8 freeze before code changes: a negation is evidence, not a dispensable stop word.
 * Invalid top-k requests must not crash the retrieval path.
 */
class MemoryRetrieverPolarityAndLimitR8Test {
    private val positive = MemoryDoc("lamp-positive", "Die Lampe leuchtet", 1_000L, true)
    private val negative = MemoryDoc("lamp-negative", "Die Lampe leuchtet nicht", 1_000L, false)
    private fun variants(): List<ContinuumMemoryRetriever> = listOf(
        Bm25GermanRetriever(), CharNgramHashRetriever(dim = 1 shl 14),
    )

    @Test fun negationIsAContentStem() {
        assertFalse("nicht must not disappear from evidence", GermanText.contentStems("nicht").isEmpty())
    }

    @Test fun explicitlyNegatedMemoryWinsInBothRetrievers() {
        for (retriever in variants()) {
            retriever.index(listOf(positive, negative))
            val hit = retriever.search("Die Lampe leuchtet nicht", 2_000L, 1).single()
            assertEquals("negated statement must rank first", negative, hit.doc)
            assertFalse("unconfirmed status must be preserved", hit.confirmed)
        }
    }

    @Test fun singleNegationCanRetrieveItsOwnEvidence() {
        for (retriever in variants()) {
            retriever.index(listOf(positive, negative))
            val hit = retriever.search("nicht", 2_000L, 1).single()
            assertEquals(negative, hit.doc)
            assertFalse(hit.confirmed)
        }
    }

    @Test fun zeroAndNegativeKReturnEmptyRatherThanThrow() {
        for (retriever in variants()) {
            retriever.index(listOf(positive, negative))
            assertTrue(retriever.search("Lampe", 2_000L, 0).isEmpty())
            assertTrue(retriever.search("Lampe", 2_000L, -1).isEmpty())
        }
        val ngrams = CharNgramHashRetriever(dim = 1 shl 14)
        ngrams.index(listOf(positive, negative))
        assertTrue(ngrams.scoredParts("Lampe", 2_000L, -1).isEmpty())
    }

    @Test fun positiveRetrievalAndExplicitConfirmationRemainIntact() {
        for (retriever in variants()) {
            retriever.index(listOf(positive, negative))
            val hit = retriever.search("Die Lampe leuchtet", 2_000L, 1).single()
            assertEquals(positive, hit.doc)
            assertTrue(hit.confirmed)
        }
    }
}
