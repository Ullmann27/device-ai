package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frozen before R9 source repair: negation-only evidence is not fuzzy-positive evidence. */
class MemoryNegationExactnessR9Test {
    private val light = MemoryDoc("light-positive", "Das Licht leuchtet", 1_000L, true)
    private val negation = MemoryDoc("negation-negative", "Das Licht leuchtet nicht", 1_000L, false)

    @Test fun bm25DoesNotFuzzyRewriteNichtAsLicht() {
        val retriever = Bm25GermanRetriever()
        retriever.index(listOf(light))
        assertTrue(retriever.search("nicht", 2_000L, 3).isEmpty())
    }

    @Test fun ngramDoesNotTreatBareNegationAsPartialLichtMatch() {
        val retriever = CharNgramHashRetriever(dim = 1 shl 14)
        retriever.index(listOf(light))
        assertTrue(retriever.search("nicht", 2_000L, 3).isEmpty())
        assertTrue(retriever.scoredParts("nicht", 2_000L, 3).isEmpty())
    }

    @Test fun exactNegativeEvidenceRemainsRetrievable() {
        for (retriever in listOf(Bm25GermanRetriever(), CharNgramHashRetriever(dim = 1 shl 14))) {
            retriever.index(listOf(light, negation))
            val hit = retriever.search("nicht", 2_000L, 1).single()
            assertEquals(negation, hit.doc)
            assertFalse(hit.confirmed)
        }
    }

    @Test fun ordinaryTypoRecoveryAndPositiveEvidenceRemain() {
        val retriever = Bm25GermanRetriever()
        val original = MemoryDoc("table", "Der Tisch", 1_000L, true)
        retriever.index(listOf(original))
        val hit = retriever.search("tisck", 2_000L, 1).single()
        assertEquals(original, hit.doc)
        assertTrue(hit.confirmed)
    }

    @Test fun aNormalSentenceMayStillUsePositiveTerms() {
        val retriever = Bm25GermanRetriever()
        retriever.index(listOf(light))
        val hit = retriever.search("Das Licht leuchtet nicht", 2_000L, 1).single()
        assertEquals(light, hit.doc)
    }
}
