package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisMotion.Direction
import at.heinz.deviceai.ui.JarvisMotion.Presets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisMotionTest {
    private val spec = Presets.panelLeft

    @Test fun panelStartsAt80PercentInvisibleFromLeft() {
        val f = JarvisMotion.frame(spec, 0)
        assertTrue(f.dx < 0f); assertEquals(0f, f.alpha, .01f); assertEquals(.8f, f.scale, .001f)
        assertEquals(600L, spec.durationMs)
        assertTrue(JarvisMotion.frame(Presets.panelRight, 0).dx > 0f)
    }

    @Test fun cubicEaseOutAtHalfTime() {
        assertEquals(.8f + .2f * .875f, JarvisMotion.frame(spec, 300).scale, .002f)
    }

    @Test fun landsAtRestAndFlashesRim() {
        val landed = JarvisMotion.frame(spec, spec.durationMs + 10)
        assertEquals(0f, landed.dx, .01f); assertEquals(1f, landed.scale, .01f); assertEquals(1f, landed.alpha, .01f)
        assertTrue(landed.glow > .5f)
        assertEquals(JarvisMotion.REST, JarvisMotion.frame(spec, spec.totalMs))
    }

    @Test fun centreFirstAndSides() {
        assertArrayEquals(intArrayOf(2, 0, 1), JarvisMotion.centerFirstRanks(listOf(100f, 500f, 700f), 520f))
        assertEquals(Presets.panelLeft, JarvisMotion.panelSpecFor(100f, 500f))
        assertEquals(Presets.panelRight, JarvisMotion.panelSpecFor(900f, 500f))
        assertTrue(JarvisMotion.Durations.STAGGER in 50L..100L)
    }

    @Test fun pillsDoNotFadeAndDecelerateStrongly() {
        val pill = Presets.pill(Direction.LEFT)
        assertEquals(1f, JarvisMotion.frame(pill, 0).alpha, 0f)
        assertEquals(1f, JarvisMotion.frame(pill, 1).alpha, 0f)
        val half = JarvisMotion.frame(pill, pill.durationMs / 2)
        assertTrue("> 90 % of the way at half time", kotlin.math.abs(half.dx) < pill.distanceDp * .05f)
    }

    @Test fun widgetsGrowFromHalf() = assertEquals(.5f, JarvisMotion.frame(Presets.widget(Direction.TOP_RIGHT), 0).scale, .001f)

    @Test fun sceneZExponentialEaseIn() {
        val mid = JarvisMotion.sceneZ(400); val end = JarvisMotion.sceneZ(800)
        assertTrue("slow start", mid.newScale < .05f)
        assertEquals(1f, end.newScale, .001f); assertEquals(0f, end.oldAlpha, .001f); assertTrue(end.oldScale > 3f && end.done)
    }

    @Test fun laserAndEnvelope() {
        assertEquals(.5f, JarvisMotion.laser(150), .001f); assertEquals(1f, JarvisMotion.laser(300), 0f)
        assertEquals(0f, JarvisMotion.envelopeFlap(500), .001f); assertEquals(1f, JarvisMotion.envelopeFlap(800), .001f)
        assertEquals(0f, JarvisMotion.frame(Presets.envelope, 0).scale, 0f)
    }

    @Test fun idleFloat() {
        assertEquals(2.5f, JarvisMotion.idleOffset(1000), .01f); assertEquals(0f, JarvisMotion.idleOffset(2000), .01f)
    }

    @Test fun reducedMotionJumpsToRest() {
        assertEquals(JarvisMotion.REST, JarvisMotion.frame(spec, 0, animatorScale = 0f))
        assertEquals(0L, JarvisMotion.groupDurationMs(spec, 5, animatorScale = 0f))
        assertTrue(JarvisMotion.sceneZ(0, 0f).done); assertEquals(1f, JarvisMotion.laser(0, 0f), 0f)
        assertEquals(0f, JarvisMotion.idleOffset(1000, animatorScale = 0f), 0f)
    }

    @Test fun animatorScaleStretchesTime() =
        assertTrue(JarvisMotion.frame(spec, 200, 2f).alpha < JarvisMotion.frame(spec, 200, 1f).alpha)

    @Test fun easingsHitEndpoints() {
        with(JarvisMotion.Easing) { listOf(linear, easeOutCubic, easeInOutCubic, easeOutQuint, easeInExpo, easeInOutSine, easeOutBack) }
            .forEach { assertEquals(0f, it(0f), .001f); assertEquals(1f, it(1f), .001f) }
    }

    @Test fun depthLayersScaleBlurTiltDrift() {
        val D = JarvisMotion.Depth
        assertTrue(D.scale(JarvisMotion.Depth.Layer.FAR) < D.scale(JarvisMotion.Depth.Layer.MID) && D.scale(JarvisMotion.Depth.Layer.MID) < D.scale(JarvisMotion.Depth.Layer.NEAR))
        assertEquals(0f, D.blurDp(JarvisMotion.Depth.Layer.NEAR), 0f); assertTrue(D.blurDp(JarvisMotion.Depth.Layer.FAR) > D.blurDp(JarvisMotion.Depth.Layer.MID))
        assertTrue(D.tiltY(100f, 1000f) > 0f && D.tiltY(900f, 1000f) < 0f)
        assertTrue(kotlin.math.abs(D.tiltY(0f, 1000f)) <= D.MAX_TILT_DEG)
        assertTrue(D.drift(3500, JarvisMotion.Depth.Layer.NEAR).first > D.drift(3500, JarvisMotion.Depth.Layer.FAR).first)
        assertTrue(kotlin.math.abs(D.drift(3500, JarvisMotion.Depth.Layer.NEAR).first) <= 2f)
        assertEquals(0f to 0f, D.drift(3500, JarvisMotion.Depth.Layer.FAR, 0f))
    }

    @Test fun dollyInIsLinearOneToOnePointZeroThree() {
        val D = JarvisMotion.Depth
        assertEquals(1f, D.dolly(0), 1e-4f)
        assertEquals(1.015f, D.dolly(D.DOLLY_PERIOD_MS / 2), 1e-4f)
        assertEquals(1.03f, D.dolly(D.DOLLY_PERIOD_MS), 1e-4f)
        assertEquals(1f, D.dolly(2 * D.DOLLY_PERIOD_MS), 1e-4f)
        assertEquals(1f, D.dolly(D.DOLLY_PERIOD_MS, 0f), 0f)
    }
}

class JarvisMotionEntranceTiltTest {
    @Test
    fun entranceTiltSwingsTheWindowInFromItsNearEdgeAndSettlesFlat() {
        val right = JarvisMotion.Depth.entranceTilt(openness = 0f, side = 1f)
        val left = JarvisMotion.Depth.entranceTilt(openness = 0f, side = -1f)
        org.junit.Assert.assertEquals(HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES, kotlin.math.abs(right), 0.0001f)
        org.junit.Assert.assertEquals(-right, left, 0.0001f)
        org.junit.Assert.assertEquals(0f, JarvisMotion.Depth.entranceTilt(1f, 1f), 0.0001f)
        val mid = JarvisMotion.Depth.entranceTilt(.5f, 1f)
        org.junit.Assert.assertTrue(kotlin.math.abs(mid) in 0.0001f..HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES)
        org.junit.Assert.assertEquals(0f, JarvisMotion.Depth.entranceTilt(0f, 0f), 0.0001f)
        org.junit.Assert.assertEquals(0f, JarvisMotion.Depth.entranceTilt(0f, 1f, animatorScale = 0f), 0.0001f)
    }
}
