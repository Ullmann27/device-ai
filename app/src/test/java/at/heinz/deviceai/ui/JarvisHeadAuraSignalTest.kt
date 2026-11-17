package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Glow responds to the actual app state, never inferred work. */
class JarvisHeadAuraSignalTest {
    @Test fun readyStateRetainsReferenceLighting() {
        assertEquals(0f, JarvisHeadAura.activityBoost(.18f, 0f), 0f)
        assertEquals(0f, JarvisHeadAura.activityBoost(0f, 0f), 0f)
        assertEquals(0f, JarvisHeadAura.activityBoost(Float.NaN, Float.NaN), 0f)
    }

    @Test fun realThinkingAndSpeechSignalsIncreaseButBoundTheGlow() {
        val thinking = JarvisHeadAura.activityBoost(.78f, 0f)
        val speaking = JarvisHeadAura.activityBoost(.55f, .7f)
        assertTrue(thinking > 0f)
        assertTrue(speaking > thinking)
        assertTrue(speaking <= .27f)
    }

    @Test fun extremeAndInvalidInputsCannotOverflowTheHalo() {
        for (e in listOf(-1f, 0f, 1f, 2f, Float.NaN, Float.POSITIVE_INFINITY))
            for (speech in listOf(-1f, 0f, 1f, 2f, Float.NaN, Float.POSITIVE_INFINITY)) {
                val v = JarvisHeadAura.activityBoost(e, speech)
                assertTrue("Invalid glow: $v", v.isFinite() && v in 0f..27f / 100f)
            }
    }
}
