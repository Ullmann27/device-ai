package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** No production repair pipeline is exercised. All data are authored synthetic examples. */
class Cdx1PatchShadowStudyTest {
    @Test fun isolatedCounterfactualScoresAreFiniteAndNeverPromote() {
        val model = TinyContextDecoder(seed = 91L)
        val before = model.updates
        val study = Cdx1PatchShadowStudy(model)
        val report = study.observe(Cdx1PatchShadowStudy.ReviewedPair(
            original = "fun sum(a: Int, b: Int) = a + b",
            counterfactual = "fun sum(a: Int, b: Int) = a - b",
            reviewId = "synthetic-test-a"
        ))
        assertEquals("SHADOW_ONLY_NO_SAFETY_VERDICT", report.mode)
        assertEquals(before, model.updates)
        assertTrue(report.modelUpdatesUnchanged)
        assertFalse(report.canApprovePatch)
        assertFalse(report.canOverrideVerifier)
        assertTrue(report.originalMeanSurprisalBits.isFinite())
        assertTrue(report.counterfactualMeanSurprisalBits.isFinite())
        assertTrue(report.differenceCounterfactualMinusOriginalBits.isFinite())
        assertNotEquals(report.originalSha256, report.counterfactualSha256)
    }

    @Test fun rejectsUnreviewedOrOversizedCounterfactuals() {
        val study = Cdx1PatchShadowStudy(TinyContextDecoder())
        fun rejected(input: Cdx1PatchShadowStudy.ReviewedPair): Boolean =
            try { study.observe(input); false } catch (_: IllegalArgumentException) { true }
        assertTrue(rejected(Cdx1PatchShadowStudy.ReviewedPair("a", "b", "")))
        assertTrue(rejected(Cdx1PatchShadowStudy.ReviewedPair("same", "same", "synthetic")))
        assertTrue(rejected(Cdx1PatchShadowStudy.ReviewedPair("a".repeat(513), "b", "synthetic")))
        assertTrue(rejected(Cdx1PatchShadowStudy.ReviewedPair("abc", "bad\u0000bytes", "synthetic")))
    }
}
