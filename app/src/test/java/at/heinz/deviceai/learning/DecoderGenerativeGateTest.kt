package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class DecoderGenerativeGateTest {
    private val validation = DecoderEvidenceCorpus((1..20).map {
        DecoderEvidencePair("Person $it wohnt in Ort$it.", "Wo wohnt Person $it?", "Ort$it")
    } + (1..5).map {
        DecoderEvidencePair("Person $it wohnt in Ort$it.", "Welche Farbe mag Person $it?", "unbekannt")
    }, DecoderSplit.AUDIT)

    private fun quality(right: Set<Int>, badMissing: Boolean = false, ended: Boolean = true) =
        DecoderGenerativeGate.measure(validation) { pair ->
            val i = validation.pairs.indexOf(pair)
            DecoderGeneration(if (i < 20) {
                if (i in right) pair.answer else "falsch"
            } else if (badMissing) "a" else "unbekannt", 1, ended)
        }

    @Test fun enforcesSixteenOfTwentyAndFiveExplicitAbstentions() {
        val before = quality((0..14).toSet())
        assertTrue(DecoderGenerativeGate.improves(before, quality((0..15).toSet())))
        assertFalse(DecoderGenerativeGate.improves(before, quality((0..14).toSet())))
        assertFalse(DecoderGenerativeGate.improves(before, quality((0..15).toSet(), badMissing = true)))
        assertFalse(quality((0..19).toSet(), ended = false).qualified)
    }

    @Test fun aggregateGainCannotHideRegressionOnPreviouslyCorrectCase() {
        assertFalse(DecoderGenerativeGate.improves(quality((0..14).toSet()), quality((1..19).toSet())))
    }

    @Test fun knownOnlyValidationCannotProveAbstention() {
        val knownOnly = DecoderEvidenceCorpus(validation.pairs.take(20), DecoderSplit.AUDIT)
        val score = DecoderGenerativeGate.measure(knownOnly) { DecoderGeneration(it.answer, 1, true) }
        assertFalse(score.qualified)
        assertEquals(20, score.correctSupported)
        assertEquals(0, score.missing)
    }

    @Test fun normalizationAcceptsUnicodeAndExplicitAbstentionWithoutAcceptingEmptyText() {
        val corpus = DecoderEvidenceCorpus(listOf(
            DecoderEvidencePair("Ort ist Zürich.", "Welcher Ort?", "Zürich"),
            DecoderEvidencePair("Ort ist Zürich.", "Welche Farbe?", "unbekannt")
        ), DecoderSplit.AUDIT)
        val score = DecoderGenerativeGate.measure(corpus) {
            DecoderGeneration(if (it.answer == "unbekannt") "" else " Zu\u0308rich. ", 8, true)
        }
        assertEquals(1, score.correctSupported)
        assertEquals(0, score.correctAbstentions)
    }
}
