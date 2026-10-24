package at.heinz.deviceai.learning.span

import at.heinz.deviceai.learning.span.ContinuumSpanAbstentionBench.Split
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Freeze and integrity gates of the CSX1 abstention benchmark (JVM only). */
class ContinuumSpanAbstentionBenchTest {
    @Test fun frozenTestSetIsUnchanged() {
        assertEquals(ContinuumSpanAbstentionBench.TEST_SHA256,
            ContinuumSpanAbstentionBench.sha256(ContinuumSpanAbstentionBench.generate(Split.TEST)))
    }

    @Test fun splitsShareNoEntityAndNoAnswerValue() {
        val split = Split.entries.associateWith { ContinuumSpanAbstentionBench.generate(it) }
        fun ids(s: Split) = split.getValue(s).flatMap {
            Regex("[A-Z]{2}\\d{2}").findAll(it.example.evidence + " " + it.example.question).map { m -> m.value }
        }.toSet()
        fun values(s: Split) = split.getValue(s).mapNotNull { it.example.answer }.toSet()
        for ((a, b) in listOf(Split.TRAIN to Split.TEST, Split.VALIDATION to Split.TEST, Split.TRAIN to Split.VALIDATION)) {
            assertTrue((ids(a) intersect ids(b)).isEmpty())
            assertTrue((values(a) intersect values(b)).isEmpty())
        }
    }

    @Test fun multiwordGoldsAreUniqueExactOffsetSpansOnlyWithMultiTokenSpans() {
        val spans = ContinuumResidualSpanCore(77, ContinuumResidualSpanCore.Features(positionFeatures = false,
            entityProximity = true, excludeCueWords = true, multiTokenSpans = true))
        val single = ContinuumResidualSpanCore(77)
        for (s in listOf(Split.TRAIN, Split.VALIDATION)) for (c in ContinuumSpanAbstentionBench.generate(s, multiword = true)) {
            spans.loss(c.example) // every gold, incl. "123 Kilogramm" / "Anna Huber", is one exact candidate span
            if (c.kind == ContinuumSpanAbstentionBench.Kind.UNIT_MULTIWORD)
                assertTrue(runCatching { single.loss(c.example) }.isFailure) // single-token model cannot represent it
        }
        assertEquals(0, spans.updates)
    }

    @Test fun everyTrainableGoldIsAUniqueCandidateOfTheRealModel() {
        val model = ContinuumResidualSpanCore(77)
        for (s in Split.entries) for (c in ContinuumSpanAbstentionBench.generate(s)) {
            if (c.kind != ContinuumSpanAbstentionBench.Kind.UNIT_MULTIWORD) model.loss(c.example) // throws on invalid gold
        }
        assertEquals(0, model.updates)
    }
}
