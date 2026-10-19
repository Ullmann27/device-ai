package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumBimodalConditionerTest {
    @Test fun returnsBounded16Dimensions() {
        val lexical = DoubleArray(16) { if (it % 2 == 0) 0.5 else -0.25 }
        val question = DoubleArray(32) { if (it == 0) 1.0 else 0.0 }
        val evidence = DoubleArray(32) { if (it == 0) 1.0 else 0.0 }
        val out = ContinuumBimodalConditioner.combine(lexical, question, evidence)
        assertEquals(16, out.size)
        assertTrue(out.all { it.isFinite() && it in -1.0..1.0 })
        assertTrue(out[8] > 0.0)
        assertEquals(0.0, out[9], 1e-12)
    }

    @Test fun relationalSlotsRespondToDifferentEvidence() {
        val swb = DoubleArray(16)
        val q = DoubleArray(32) { if (it == 0) 1.0 else 0.0 }
        val matching = DoubleArray(32) { if (it == 0) 1.0 else 0.0 }
        val different = DoubleArray(32) { if (it == 1) 1.0 else 0.0 }
        val match = ContinuumBimodalConditioner.combine(swb, q, matching)
        val mismatch = ContinuumBimodalConditioner.combine(swb, q, different)
        assertFalse(match.contentEquals(mismatch))
        assertTrue(match[8] > mismatch[8])
        assertTrue(match.sliceArray(0..7).contentEquals(mismatch.sliceArray(0..7)))
    }

    @Test fun rejectsUnexpectedShapesAndNonFiniteVectors() {
        val x = DoubleArray(16)
        val y = DoubleArray(32)
        runCatching { ContinuumBimodalConditioner.combine(DoubleArray(15), y, y) }
            .onSuccess { throw AssertionError("Expected shape rejection") }
        val invalid = y.copyOf()
        invalid[0] = Double.NaN
        runCatching { ContinuumBimodalConditioner.combine(x, invalid, y) }
            .onSuccess { throw AssertionError("Expected NaN rejection") }
    }
}
