package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HollywoodFuiDepthPlaneTest {
    @Test fun angleSeparatesRearGlassFromFrontBracketsWithoutMovingText() {
        val policy = HollywoodFuiConstruct
        val back = policy.layerShiftDp(14f, -24f, true)
        val front = policy.layerShiftDp(14f, 20f, true)
        assertTrue(back < 0f)
        assertTrue(front > 0f)
        assertTrue(front - back in 6f..12f)
        assertEquals(0f, policy.layerShiftDp(14f, 0f, true), 0f)
    }

    @Test fun noMotionAndInvalidAnglesKeepNativeContentOnItsOriginalPlane() {
        val policy = HollywoodFuiConstruct
        assertEquals(0f, policy.layerShiftDp(14f, -24f, false), 0f)
        assertEquals(0f, policy.layerShiftDp(0f, 20f, true), 0f)
        assertEquals(0f, policy.layerShiftDp(Float.NaN, 20f, true), 0f)
        assertEquals(0f, policy.layerShiftDp(14f, Float.POSITIVE_INFINITY, true), 0f)
        assertTrue(policy.layerShiftDp(80f, -100f, true) in -12f..12f)
    }
}
