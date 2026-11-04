package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisMenuMotionTest {
    @Test fun patternBCentreFirstStaggeredSidesAndSpec() {
        val steps = JarvisMenuMotion.patternB(listOf(100f, 380f, 450f, 700f, 820f), 420f)
        assertEquals(listOf(3, 1, 0, 2, 4), steps.map { it.rank })
        assertEquals(listOf(true, true, false, false, false), steps.map { it.fromLeft })
        val delays = steps.sortedBy { it.rank }.map { it.delayMs }
        assertEquals(0L, delays.first())
        delays.zipWithNext().forEach { (a, b) -> assertTrue(b - a in 50L..100L) }
        steps.forEach { s ->
            assertTrue(s.spec.durationMs in 500L..600L); assertEquals(.8f, s.spec.fromScale, 0f); assertEquals(0f, s.spec.fromAlpha, 0f)
            assertEquals(.875f, s.spec.easing(.5f), 1e-6f) // cubic ease-out
        }
        assertEquals(4 * 80L + 600L, JarvisMenuMotion.patternBMs(5))
        assertEquals(0L, JarvisMenuMotion.patternBMs(5, 0f))
        assertTrue(JarvisMenuMotion.patternB(listOf(1f, 2f), 0f, 0f).all { it.delayMs == 0L })
    }

    @Test fun patternADepthEightHundredMs() {
        val start = JarvisMenuMotion.patternA(0)
        assertEquals(1f, start.oldScale, 1e-3f); assertEquals(1f, start.oldAlpha, 1e-3f); assertEquals(0f, start.newScale, 1e-3f)
        val mid = JarvisMenuMotion.patternA(400)
        assertTrue("exponential ease-in: slow start", mid.newScale < .1f)
        val end = JarvisMenuMotion.patternA(800)
        assertTrue(end.done); assertTrue(end.oldScale > 3f); assertEquals(0f, end.oldAlpha, 1e-6f)
        assertEquals(1f, end.newScale, 1e-6f); assertEquals(1f, end.newAlpha, 1e-6f)
        assertTrue(!JarvisMenuMotion.patternA(799).done)
    }

    @Test fun cameraDriftIsLinearDollyOneToOnePointZeroThree() {
        val c = JarvisMenuMotion.Camera
        assertEquals(1f, c.dollyScale(0), 1e-6f)
        assertEquals(1.015f, c.dollyScale(c.DOLLY_MS / 2), 1e-5f)       // linear
        assertEquals(1.03f, c.dollyScale(c.DOLLY_MS), 1e-5f)
        assertEquals(1f, c.dollyScale(c.PERIOD_MS), 1e-6f)              // loops without jump
        var prev = 0f
        for (t in 0L..c.DOLLY_MS step 500) { val s = c.dollyScale(t); assertTrue(s >= prev); prev = s }
        for (t in 0L..2 * c.PERIOD_MS step 250) {
            val st = c.at(t)
            assertTrue(st.scale in 1f..1.03f + 1e-6f)
            assertTrue(kotlin.math.abs(st.dxDp) <= 2f && kotlin.math.abs(st.dyDp) <= 2f)
            assertTrue(kotlin.math.abs(st.rotationX) <= 1f && kotlin.math.abs(st.rotationY) <= 1f)
        }
        assertEquals(c.REST, c.at(4_000, 0f))
    }

    @Test fun phoneSceneScalesFromCentreThenTiltsEaseInOutTwoSeconds() {
        val p = JarvisMenuMotion.PhoneScene
        val f0 = p.frame(0); assertEquals(0f, f0.scale, 0f); assertEquals(0f, f0.rotationY, 0f)
        assertEquals(1f, p.frame(600).scale, 1e-6f)
        assertEquals(p.TILT_Y_DEG / 2f, p.frame(1000).rotationY, 1e-4f) // ease-in-out midpoint
        assertTrue(p.frame(2000).done); assertEquals(p.TILT_Y_DEG, p.frame(2000).rotationY, 1e-6f)
        assertTrue(p.frame(0, 0f).done)
        val (phone, head) = p.layout(884f, 760f)
        assertEquals(p.PHONE_ASPECT, phone.width / phone.height, 1e-4f)
        assertTrue(head.left > phone.right && head.width > 0f && head.right <= 884f)
    }
}
