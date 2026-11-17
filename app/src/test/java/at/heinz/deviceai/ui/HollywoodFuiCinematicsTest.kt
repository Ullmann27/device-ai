package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HollywoodFuiCinematicsTest {
    private val box = HollywoodFuiCinematics.Box(200f, 120f, 520f, 360f)

    @Test
    fun renderBudgetBoundsParticlesFrameDelayAndBloom() {
        val high = HollywoodFuiCinematics.Budget.from(JarvisRenderQuality.resolve(true, false, 0, false, false))
        val warm = HollywoodFuiCinematics.Budget.from(JarvisRenderQuality.resolve(true, false, 2, false, true))
        val low = HollywoodFuiCinematics.Budget.from(JarvisRenderQuality.resolve(true, true, 0, false, false))
        val off = HollywoodFuiCinematics.Budget.from(JarvisRenderQuality.resolve(false, false, 0, false, false))

        assertEquals(HollywoodFuiCinematics.MAX_MOTES, high.motes)
        assertTrue(high.bloom)
        assertEquals(3, high.glassLayers)
        assertEquals(2, warm.glassLayers)
        assertEquals(1, low.glassLayers)
        assertEquals(16L, high.frameDelayMs)
        assertTrue(warm.motes in 1 until high.motes)
        assertFalse(warm.bloom)
        // Warm + inference: the cockpit budget slows the cadence (50 ms today); the overlay must follow, not race it.
        assertTrue(warm.frameDelayMs > high.frameDelayMs && warm.frameDelayMs >= 33L)
        assertTrue(warm.animate)
        assertEquals(0, low.motes)
        assertFalse(low.animate)
        assertFalse(off.animate)
        assertTrue(off.frameDelayMs >= 16L)
    }

    @Test fun liveFramesRequireRealWorkVisibilityMotionAndEnabledEffects() {
        val high = HollywoodFuiCinematics.Budget.FULL
        assertTrue(high.needsLiveFrames(true, false, true, true, false))
        assertFalse(high.needsLiveFrames(false, false, true, true, false))
        assertFalse(high.needsLiveFrames(true, true, true, true, false))
        assertFalse(high.needsLiveFrames(true, false, false, true, false))
        assertFalse(high.needsLiveFrames(true, false, true, false, false))
        assertFalse(high.needsLiveFrames(true, false, true, true, true))
        for (budget in listOf(
            JarvisRenderQuality.resolve(true, false, 3, false, true),
            JarvisRenderQuality.resolve(false, false, 0, false, false),
            JarvisRenderQuality.resolve(true, true, 0, false, false))) {
            assertFalse(HollywoodFuiCinematics.Budget.from(budget).needsLiveFrames(true, false, true, true, false))
        }
    }

    @Test
    fun glassLayersSlideInFromDepthAndSettleBehindTheFront() {
        val start = HollywoodFuiCinematics.glassLayer(box, index = 1, glass = 0f, layers = 3)
        val end = HollywoodFuiCinematics.glassLayer(box, index = 1, glass = 1f, layers = 3)
        val deep = HollywoodFuiCinematics.glassLayer(box, index = 3, glass = 1f, layers = 3)

        assertTrue(start.alpha < 0.0001f)
        assertTrue(end.alpha > start.alpha)
        // Settled layers stand inside the front frame, offset towards the depth (up/right), deeper ones more.
        assertTrue(end.box.left > box.left && end.box.top < box.top)
        assertTrue(deep.box.left > end.box.left && deep.box.top < end.box.top)
        assertTrue(deep.alpha < end.alpha)
        // Unsettled layers are further away than settled ones (they come from the depth).
        assertTrue(start.box.left > end.box.left)
        assertTrue(start.scale < end.scale)
    }

    @Test
    fun motesAreDeterministicInsideTheRimAndRespectTheBudget() {
        val a = HollywoodFuiCinematics.motes(box, timeMs = 1_234L, count = 12, seed = 7)
        val b = HollywoodFuiCinematics.motes(box, timeMs = 1_234L, count = 12, seed = 7)
        val other = HollywoodFuiCinematics.motes(box, timeMs = 1_234L, count = 12, seed = 8)
        assertEquals(a, b)
        assertTrue(a != other)
        assertEquals(12, a.size)
        assertTrue(a.all { it.x in box.left - 8f..box.right + 8f && it.y in box.top - 8f..box.bottom + 8f })
        assertTrue(a.all { it.alpha in 0f..1f && it.radius > 0f })
        assertEquals(0, HollywoodFuiCinematics.motes(box, 0L, count = 0, seed = 1).size)
        // Motes move with time.
        assertTrue(a != HollywoodFuiCinematics.motes(box, timeMs = 1_734L, count = 12, seed = 7))
    }

    @Test
    fun anchorBeamRunsFromTheOriginToTheNearestEdgeOfTheConstruct() {
        val fromLeft = HollywoodFuiCinematics.anchorBeam(originX = 40f, originY = 240f, box = box, progress = 1f)
        assertEquals(40f, fromLeft.x0, 0.0001f)
        assertEquals(box.left, fromLeft.x1, 0.0001f)
        assertTrue(fromLeft.y1 in box.top..box.bottom)

        val fromAbove = HollywoodFuiCinematics.anchorBeam(originX = 360f, originY = 20f, box = box, progress = 1f)
        assertEquals(box.top, fromAbove.y1, 0.0001f)

        val half = HollywoodFuiCinematics.anchorBeam(originX = 40f, originY = 240f, box = box, progress = .5f)
        assertEquals((40f + box.left) / 2f, half.x1, 0.5f)
        val inside = HollywoodFuiCinematics.anchorBeam(originX = box.centerX, originY = box.centerY, box = box, progress = 1f)
        assertEquals(0f, inside.length, 0.0001f)
    }
}
