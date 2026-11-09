package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HollywoodFuiWorkActivityTest {
    @Test fun settledRasterLivesOnlyWhileRealWorkIsActiveAndAnimationAllowed() {
        val policy = HollywoodFuiWorkActivity
        assertEquals(1f, policy.rasterOpacity(1f, 0f, false, true), 0f)
        assertEquals(0f, policy.rasterOpacity(1f, 1f, false, true), 0f)
        assertEquals(.09f, policy.rasterOpacity(1f, 1f, true, true), .00001f)
        assertEquals(0f, policy.rasterOpacity(1f, 1f, true, false), 0f)
        assertEquals(0f, policy.rasterOpacity(0f, 1f, true, true), 0f)
    }

    @Test fun gradualDataRevealNeverMakesTheWorkingRasterBrighterThanConstruction() {
        val policy = HollywoodFuiWorkActivity
        var prev = 1f
        for (i in 0..1000) {
            val data = i / 1000f
            val ongoing = policy.rasterOpacity(1f, data, true, true)
            val idle = policy.rasterOpacity(1f, data, false, true)
            assertTrue(ongoing in .08999f..1.00001f)
            assertTrue(ongoing <= prev + .00001f)
            assertTrue(ongoing >= idle)
            prev = ongoing
        }
    }

    @Test fun scanFadeAtWrapRespectsBothEdges() {
        val p = HollywoodFuiWorkActivity
        assertEquals(0f, p.scanEnvelope(0f), 0f)
        assertEquals(.5f, p.scanEnvelope(.04f), .00001f)
        assertEquals(1f, p.scanEnvelope(.08f), 0f)
        assertEquals(1f, p.scanEnvelope(.5f), 0f)
        assertEquals(.5f, p.scanEnvelope(.96f), .00001f)
        assertEquals(0f, p.scanEnvelope(1f), 0f)
    }

    @Test fun invalidInputsCannotDrawUnboundedRasterOrScan() {
        val p = HollywoodFuiWorkActivity
        assertEquals(0f, p.rasterOpacity(Float.NaN, 1f, true, true), 0f)
        assertEquals(0f, p.rasterOpacity(1f, Float.POSITIVE_INFINITY, true, true), 0f)
        assertEquals(0f, p.scanEnvelope(Float.NaN), 0f)
        assertEquals(0f, p.scanEnvelope(Float.NEGATIVE_INFINITY), 0f)
    }
}
