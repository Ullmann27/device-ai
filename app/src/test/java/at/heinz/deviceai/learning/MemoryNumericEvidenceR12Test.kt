package at.heinz.deviceai.learning

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frozen before production: a numeral in a factual query needs exact numeric evidence. */
class MemoryNumericEvidenceR12Test {
    private val now = 1_000_000L

    @Test fun bm25DoesNotInventTwoChildrenFromThree() {
        val model = Bm25GermanRetriever()
        model.index(listOf(MemoryDoc("three", "Ich habe 3 Kinder", 1L, true)))
        assertTrue(model.search("2 Kinder", now, 5).isEmpty())
    }

    @Test fun ngramDoesNotInventTwoChildrenFromThree() {
        val model = CharNgramHashRetriever(dim = 1 shl 14)
        model.index(listOf(MemoryDoc("three", "Ich habe 3 Kinder", 1L, true)))
        assertTrue(model.search("2 Kinder", now, 5).isEmpty())
    }

    @Test fun bm25NeverReturnsWrongCountAlongsideCorrectCount() {
        val model = Bm25GermanRetriever()
        val right = MemoryDoc("two", "Ich habe 2 Kinder", 2L, false)
        model.index(listOf(MemoryDoc("three", "Ich habe 3 Kinder", 1L, true), right))
        assertEquals(listOf(right), model.search("2 Kinder", now, 5).map { it.doc })
    }

    @Test fun ngramNeverReturnsWrongCountAlongsideCorrectCount() {
        val model = CharNgramHashRetriever(dim = 1 shl 14)
        val right = MemoryDoc("two", "Ich habe 2 Kinder", 2L, false)
        model.index(listOf(MemoryDoc("three", "Ich habe 3 Kinder", 1L, true), right))
        assertEquals(listOf(right), model.search("2 Kinder", now, 5).map { it.doc })
    }

    @Test fun bm25RequiresAllNumericTerms() {
        val model = Bm25GermanRetriever()
        model.index(listOf(MemoryDoc("wrong-year", "2 Kinder seit 2025", 1L, true)))
        assertTrue(model.search("2 Kinder seit 2024", now, 5).isEmpty())
    }

    @Test fun ngramRequiresAllNumericTerms() {
        val model = CharNgramHashRetriever(dim = 1 shl 14)
        model.index(listOf(MemoryDoc("wrong-year", "2 Kinder seit 2025", 1L, true)))
        assertTrue(model.search("2 Kinder seit 2024", now, 5).isEmpty())
    }

    @Test fun temporalBeforeDaysIsNotAnExactFactFilterForBm25() {
        val model = Bm25GermanRetriever(TemporalCue(ZoneId.of("Europe/Vienna")))
        val doc = MemoryDoc("day", "3 Tagen Urlaub", 1L, false)
        model.index(listOf(doc))
        assertEquals(listOf(doc), model.search("vor 2 Tagen", now, 5).map { it.doc })
    }

    @Test fun temporalBeforeDaysIsNotAnExactFactFilterForNgram() {
        val model = CharNgramHashRetriever(TemporalCue(ZoneId.of("Europe/Vienna")), dim = 1 shl 14)
        val doc = MemoryDoc("day", "3 Tagen Urlaub", 1L, false)
        model.index(listOf(doc))
        assertEquals(listOf(doc), model.search("vor 2 Tagen", now, 5).map { it.doc })
    }

    @Test fun temporalAndFactualNumbersRemainSeparate() {
        assertEquals(setOf("4"), GermanText.numericEvidenceTerms("vor 2 Tagen habe ich 4 Kinder"))
        assertEquals(setOf("2", "2024"), GermanText.numericEvidenceTerms("2 Kinder seit 2024"))
        assertTrue(GermanText.numericEvidenceTerms("vor 2 Tagen").isEmpty())
    }
}
