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

    @Test fun sparseTopKAndRevocationTraceReset() {
        val weights=listOf(
            listOf(0.1,0.02,0.01,0.0),listOf(0.02,0.1,0.01,0.0),
            listOf(0.01,0.01,0.1,0.0),listOf(0.0,0.0,0.0,0.1))
        val sparse=TemporalNeuralShadow(
            listOf(10.0,15.0,20.0,25.0),weights,
            activationThreshold=0.15,maxActiveUnits=2,plasticityDecay=0.8,plasticityRate=0.2)
        val initialized=sparse.observe(100,listOf(0.0,0.0,0.0,0.0),true,true)
        assertEquals(TemporalNeuralShadow.Status.INITIALIZED,initialized.status)
        assertEquals(0,initialized.skippedUnits)
        val step=sparse.observe(110,listOf(0.8,0.5,0.3,0.05),true,true)
        assertEquals(2,step.activeUnits)
        assertEquals(2,step.skippedUnits)
        assertEquals(0.0,step.activity[2],1e-12)
        assertTrue(step.maxStepContractionBound < 1.0)
        assertTrue(step.stepDurationNanos >= 0L)
        assertTrue(sparse.observe(120,listOf(0.8,0.5,0.3,0.05),true,true).eligibilityNorm > 0.0)
        val revoked=sparse.observe(125,listOf(0.8,0.5,0.3,0.05),false,true)
        assertEquals(TemporalNeuralShadow.Status.REVOKED_RESET,revoked.status)
        assertEquals(0,revoked.activeUnits)
        assertEquals(0,revoked.skippedUnits)
        assertEquals(0.0,revoked.eligibilityNorm,0.0)
        assertTrue(revoked.activity.all { it == 0.0 })
        assertFalse(revoked.answerAuthority)
        assertFalse(revoked.learnsWeights)
    }

}
