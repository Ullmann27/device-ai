package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frozen evidence tests: a single digit is an identifier, not an empty query. */
class MemoryOneDigitEvidenceR11Test {
    @Test fun singleDigitsRemainTokensWithoutAdmittingSingleLetters() {
        assertEquals(listOf("2", "0"), GermanText.rawTokens("a 2 b 0 C"))
        assertEquals(listOf("2", "0"), GermanText.contentStems("Ich habe 2 und 0"))
    }

    @Test fun bm25ExactOneDigitRetrievesOnlyMatchingRecord() {
        val r = Bm25GermanRetriever()
        val wrong = MemoryDoc("three", "3", 100L, true)
        val right = MemoryDoc("two", "2", 200L, false)
        r.index(listOf(wrong, right))
        val hits = r.search("2", 1_000L, 3)
        assertEquals(listOf(right), hits.map { it.doc })
        assertTrue(!hits.single().confirmed)
    }

    @Test fun ngramExactOneDigitRetrievesMatchingRecord() {
        val r = CharNgramHashRetriever(dim = 1 shl 14)
        val wrong = MemoryDoc("three", "3", 100L, true)
        val right = MemoryDoc("two", "2", 200L, false)
        r.index(listOf(wrong, right))
        assertEquals(listOf(right), r.search("2", 1_000L, 3).map { it.doc })
    }

    @Test fun bm25DisambiguatesOtherwiseIdenticalChildCounts() {
        val r = Bm25GermanRetriever()
        val wrong = MemoryDoc("three", "Ich habe 3 Kinder", 100L, true)
        val right = MemoryDoc("two", "Ich habe 2 Kinder", 200L, false)
        r.index(listOf(wrong, right))
        assertEquals(right, r.search("2 Kinder", 1_000L, 1).single().doc)
    }

    @Test fun multiDigitTokensAndSingleLetterFilteringRemain() {
        assertEquals(listOf("13", "20"), GermanText.rawTokens("a 13 b 20 c"))
    }
}
