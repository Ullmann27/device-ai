package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test

class PerimeterWingChoreographerTest {
    private val box = JarvisShellLayout.Box(120f, 40f, 270f, 145f)
    private val cool = JarvisRenderQuality.resolve(true, false, 0, false, false)
    private val warm = JarvisRenderQuality.resolve(true, false, 2, false, true)

    @Test fun onlyActualClearingTilesGetSpatialMotion() {
        val wing = PerimeterWingChoreographer
        val rest = wing.depth(null, box, 850f, 0f, true, cool)
        assertEquals(JarvisMotion.Depth.FLAT, rest)
        val active = wing.depth(null, box, 850f, .5f, true, cool)
        assertTrue(active.rotationX < 0f)
        assertTrue(active.rotationY > 0f)
        assertTrue(active.blurDp > 0f)
        assertEquals(JarvisMotion.Depth.FLAT, wing.depth(null, box, 850f, .5f, false, cool))
        assertEquals(JarvisMotion.Depth.FLAT, wing.depth(JarvisShellLayout.Slot.AVATAR,
            box, 850f, 1f, true, cool))
        assertEquals(JarvisMotion.Depth.FLAT, wing.depth(JarvisShellLayout.Slot.DIALOG,
            box, 850f, .8f, true, cool))
    }

    @Test fun thermalAndReducedMotionDisableOrReduceDecorativeDepth() {
        val wing = PerimeterWingChoreographer
        val hot = JarvisRenderQuality.resolve(true, false, 3, false, true)
        val reduced = JarvisRenderQuality.resolve(true, false, 0, true, false)
        assertEquals(JarvisMotion.Depth.FLAT, wing.depth(null, box, 850f, .6f, true, hot))
        assertEquals(JarvisMotion.Depth.FLAT, wing.depth(null, box, 850f, .6f, true, reduced))
        assertTrue(wing.depth(null, box, 850f, .6f, true, warm).blurDp <= 1.5f)
        assertEquals(JarvisMotion.Depth.FLAT, wing.depth(null, box, 850f, Float.NaN, true, cool))
        assertEquals(JarvisMotion.Depth.FLAT, wing.depth(null, box, 0f, .6f, true, cool))
    }
}
