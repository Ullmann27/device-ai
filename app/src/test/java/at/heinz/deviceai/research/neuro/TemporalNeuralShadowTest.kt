package at.heinz.deviceai.research.neuro

import org.junit.Assert.*
import org.junit.Test

class TemporalNeuralShadowTest {
    private fun brain(): TemporalNeuralShadow = TemporalNeuralShadow(
        timeConstantsMs = listOf(10.0, 20.0),
        recurrentWeights = listOf(listOf(0.1, 0.05), listOf(0.05, 0.1))
    )

    @Test fun defaultCannotAnswerOrExecuteOrTrainWeights() {
        val b = brain()
        val first = b.observe(100, listOf(0.4, -0.2), evidenceAuthorized = true, evidenceCurrent = true)
        assertEquals(TemporalNeuralShadow.Status.INITIALIZED, first.status)
        val next = b.observe(110, listOf(0.4, -0.2), evidenceAuthorized = true, evidenceCurrent = true)
        assertEquals(TemporalNeuralShadow.Status.SHADOW_ONLY, next.status)
        assertFalse(next.answerAuthority)
        assertFalse(next.executable)
        assertFalse(next.learnsWeights)
        assertTrue(next.activity.all { it.isFinite() && it in -1.0..1.0 })
        assertTrue(next.maxStepContractionBound < 1.0)
    }

    @Test fun revocationResetsNeuronState() {
        val b = brain()
        b.observe(100, listOf(0.3, 0.2), true, true)
        b.observe(110, listOf(0.3, 0.2), true, true)
        val revoked = b.observe(111, listOf(0.3, 0.2), false, true)
        assertEquals(TemporalNeuralShadow.Status.REVOKED_RESET, revoked.status)
        assertEquals(listOf(0.0, 0.0), revoked.activity)
    }

    @Test fun thermalAndStaleResets() {
        val b = TemporalNeuralShadow(listOf(10.0), listOf(listOf(0.1)), maxGapMs = 20)
        b.observe(100, listOf(0.2), true, true)
        assertEquals(TemporalNeuralShadow.Status.THERMAL_RESET,
            b.observe(101, listOf(0.2), true, true, thermalAllowed = false).status)
        b.observe(120, listOf(0.2), true, true)
        assertEquals(TemporalNeuralShadow.Status.STALE_RESET,
            b.observe(150, listOf(0.2), true, true).status)
    }

    @Test fun rejectsUnstableNeuralWeights() {
        assertThrows(IllegalArgumentException::class.java) {
            TemporalNeuralShadow(listOf(10.0), listOf(listOf(0.95)))
        }
    }

    @Test fun rejectsClockRollbackAndMalformedInput() {
        val b = brain()
        b.observe(100, listOf(0.1, 0.0), true, true)
        assertThrows(IllegalArgumentException::class.java) {
            b.observe(99, listOf(0.0, 0.0), true, true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            b.observe(101, listOf(Double.NaN, 0.0), true, true)
        }
    }
}
