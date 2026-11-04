package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisMotion.Presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisMotionTest {
    private val spec = Presets.command

    @Test fun startsOffsetAndInvisible() {
        val f = JarvisMotion.frame(spec, 0)
        assertEquals(48f, f.dy, .01f); assertEquals(0f, f.alpha, .01f); assertEquals(.92f, f.scale, .01f)
    }

    @Test fun landsAtRestAndFlashesRim() {
        val landed = JarvisMotion.frame(spec, spec.durationMs + 10)
        assertEquals(0f, landed.dy, .01f); assertEquals(1f, landed.scale, .01f); assertEquals(1f, landed.alpha, .01f)
        assertTrue("rim flares after landing", landed.glow > .5f)
        assertEquals(JarvisMotion.REST, JarvisMotion.frame(spec, spec.totalMs))
    }

    @Test fun alphaMonotonic() {
        var last = -1f
        for (t in 0..spec.durationMs step 20) { val a = JarvisMotion.frame(spec, t).alpha; assertTrue(a >= last); last = a }
    }

    @Test fun reducedMotionJumpsToRest() {
        assertEquals(JarvisMotion.REST, JarvisMotion.frame(spec, 0, animatorScale = 0f))
        assertEquals(0L, JarvisMotion.groupDurationMs(spec, 5, animatorScale = 0f))
        assertTrue(JarvisMotion.staggered(spec, 4, 0, animatorScale = 0f).all { it == JarvisMotion.REST })
    }

    @Test fun staggerOrdersCards() {
        val frames = JarvisMotion.staggered(spec, 3, 150)
        assertTrue(frames[0].alpha > frames[1].alpha && frames[1].alpha > frames[2].alpha)
        assertEquals(2 * JarvisMotion.Durations.STAGGER + spec.totalMs, JarvisMotion.groupDurationMs(spec, 3))
    }

    @Test fun animatorScaleStretchesTime() {
        assertTrue(JarvisMotion.frame(spec, 200, 2f).alpha < JarvisMotion.frame(spec, 200, 1f).alpha)
    }

    @Test fun easingsHitEndpoints() {
        listOf(JarvisMotion.Easing.linear, JarvisMotion.Easing.easeOutCubic, JarvisMotion.Easing.easeInOutCubic,
            JarvisMotion.Easing.easeOutBack).forEach { assertEquals(0f, it(0f), .001f); assertEquals(1f, it(1f), .001f) }
    }
}
