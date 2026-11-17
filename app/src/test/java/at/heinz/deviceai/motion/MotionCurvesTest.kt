package at.heinz.deviceai.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MotionCurvesTest {
    private val all = listOf(Easings.linear, Easings.easeInCubic, Easings.easeOutCubic, Easings.easeInOutCubic,
        Easings.easeOutQuint, Easings.easeInExpo, Easings.easeInOutSine, Easings.easeOutBack, Easings.cubicBezier(.25f, .1f, .25f, 1f))

    @Test fun endpointsExact() = all.forEach { assertEquals(0f, it.at(0f), 1e-6f); assertEquals(1f, it.at(1f), 1e-5f) }

    @Test fun clampedOutsideRange() = all.forEach { assertEquals(0f, it.at(-1f), 1e-6f); assertEquals(1f, it.at(2f), 1e-5f) }

    @Test fun samplePoints() {
        assertEquals(.875f, Easings.easeOutCubic.at(.5f), 1e-6f)
        assertEquals(.125f, Easings.easeInCubic.at(.5f), 1e-6f)
        assertEquals(.5f, Easings.easeInOutCubic.at(.5f), 1e-6f)
        assertEquals(1f / 32f, Easings.easeInExpo.at(.5f), 1e-6f)
        assertEquals(.5f, Easings.easeInOutSine.at(.5f), 1e-6f)
        assertEquals(.25f, Easings.linear.at(.25f), 0f)
        // cubic-bezier(0,0,1,1) is linear; CSS "ease" at x=0.5 ≈ 0.8024
        assertEquals(.3f, Easings.cubicBezier(0f, 0f, 1f, 1f).at(.3f), 1e-4f)
        assertEquals(.8024f, Easings.cubicBezier(.25f, .1f, .25f, 1f).at(.5f), 1e-3f)
    }

    @Test fun monotoneExceptBack() {
        all.filter { it !== Easings.easeOutBack }.forEach { e ->
            var prev = 0f
            for (i in 1..200) { val v = e.at(i / 200f); assertTrue(v >= prev - 1e-6f); prev = v }
        }
        assertTrue(Easings.easeOutBack.at(.8f) > 1f)
    }

    @Test fun underdampedOvershootPeakAndSettle() {
        val s = DampedSpring(10f, .5f)
        val expectedPeak = 1f + kotlin.math.exp(-.5 * Math.PI / kotlin.math.sqrt(.75)).toFloat()
        assertEquals(expectedPeak, s.peakValue, 1e-5f)
        assertEquals(expectedPeak, s.valueAt(s.peakSec), 1e-4f)
        var max = 0f; var t = 0f
        while (t < s.settleSec) { max = maxOf(max, s.valueAt(t)); t += .001f }
        assertEquals(expectedPeak, max, 1e-3f)
        // settled: within tolerance for good, exactly 1 from settleSec
        t = s.settleSec * .999f
        assertTrue(abs(1f - s.valueAt(t)) <= s.tolerance)
        assertEquals(1f, s.valueAt(s.settleSec), 0f); assertEquals(1f, s.valueAt(100f), 0f)
        assertEquals(0f, s.valueAt(0f), 0f); assertEquals(0f, s.valueAt(-1f), 0f)
    }

    @Test fun criticalAndOverdampedAreMonotoneWithoutOvershoot() {
        listOf(DampedSpring(12f, 1f), DampedSpring(12f, 2f)).forEach { s ->
            var prev = 0f; var t = 0f
            while (t < s.settleSec + .1f) { val v = s.valueAt(t); assertTrue(v <= 1f && v >= prev - 1e-6f); prev = v; t += .002f }
            assertEquals(1f - s.tolerance, 1f - (1f - s.valueAt(s.settleSec * .99999f)), .002f)
            assertEquals(1f, s.peakValue, 0f)
        }
        // critical: 1 - e^{-τ}(1+τ) at τ = 1
        assertEquals(1f - kotlin.math.exp(-1f) * 2f, DampedSpring(1f, 1f).valueAt(1f), 1e-5f)
    }

    @Test fun forSettleAndVideoPresets() {
        val s = DampedSpring.forSettle(.5f, .7f)
        assertEquals(.5f, s.settleSec, 1e-4f)
        assertEquals(500L, s.settleMs)
        assertTrue("slight overshoot", MotionPresets.widgetSpring.peakValue in 1.01f..1.08f)
        assertTrue(MotionPresets.pillSpring.peakValue in 1.001f..1.03f)
        assertEquals(.5f, MotionPresets.widgetSpring.settleSec, 1e-4f)
        val k = DampedSpring.fromStiffness(100f, 10f)
        assertEquals(10f, k.naturalFrequency, 1e-5f); assertEquals(.5f, k.dampingRatio, 1e-5f)
        assertEquals(s.valueAt(.25f), s.asEasing().at(.5f), 1e-6f)
        assertEquals(s.valueAt(.1f), s.valueAtMs(100L), 1e-6f)
    }

    @Test fun staggerCenterFirst() {
        val st = Staggers.centerFirst(.08f)
        assertEquals(listOf(.16f, .08f, 0f, .08f, .16f), (0 until 5).map { st.delaySec(it, 5) })
        assertEquals(listOf(.08f, 0f, 0f, .08f), (0 until 4).map { st.delaySec(it, 4) })
        assertEquals(listOf(3, 1, 0, 2, 4), (0 until 5).map { Staggers.rank(it, 5) })
        assertTrue(MotionPresets.STAGGER_SEC in Staggers.MIN_STEP_SEC..Staggers.MAX_STEP_SEC)
        assertEquals(0f, st.delaySec(0, 1), 0f)
        assertEquals(.16f, Staggers.linear(.08f).delaySec(2, 5), 1e-6f)
    }

    @Test fun timelineEvaluatesBeforeDuringAfter() {
        val tl = MotionTimelines.of(MotionTrack("a", 100, 200, Easings.linear), MotionTrack("b", 0, 0, Easings.linear),
            MotionTrack("c", 0, 1000, Easings.easeOutCubic))
        assertEquals(1000L, tl.totalMs)
        val out = FloatArray(3)
        tl.evaluate(0, out); assertEquals(0f, out[0], 0f); assertEquals(1f, out[1], 0f); assertEquals(0f, out[2], 0f)
        tl.evaluate(200, out); assertEquals(.5f, out[0], 1e-6f)
        tl.evaluate(500, out); assertEquals(1f, out[0], 0f); assertEquals(.875f, out[2], 1e-6f)
        tl.evaluate(-50, out); assertEquals(0f, out[0], 0f)
        tl.evaluate(5000, out); assertTrue(out.all { it == 1f })
        assertEquals(0, tl.indexOf("a")); assertEquals(-1, tl.indexOf("x"))
    }

    @Test fun presetTimelines() {
        val panels = MotionTimelines.panelsFlyIn(listOf("l2", "l1", "c", "r1", "r2"))
        assertEquals(listOf(160L, 80L, 0L, 80L, 160L), panels.tracks.map { it.startMs })
        assertEquals(760L, panels.totalMs)
        val out = FloatArray(5); panels.evaluate(300, out)
        assertEquals(.875f, out[2], 1e-6f) // centre at half of 0,6 s
        val scene = MotionTimelines.sceneChange(); assertEquals(800L, scene.totalMs)
        val so = FloatArray(2); scene.evaluate(400, so); assertTrue(so[1] < .05f); scene.evaluate(800, so); assertEquals(1f, so[0], 0f)
        assertEquals(3.2f, MotionPresets.sceneOldScale(1f), 1e-6f)
        val env = MotionTimelines.envelope(); assertEquals(800L, env.totalMs); assertEquals(500L, env.tracks[1].startMs)
        val pills = MotionTimelines.pills(listOf("a", "b", "c", "d")); val po = FloatArray(4)
        pills.evaluate(pills.totalMs, po); assertTrue(po.all { it == 1f })
        val w = MotionTimelines.widgets(listOf("wetter", "kalender")); assertEquals(500L, w.tracks[0].durationMs)
        val sq = MotionTimelines.sequence(listOf("x", "y"), 300, Easings.linear, gapMs = 100)
        assertEquals(400L, sq.tracks[1].startMs)
    }

    @Test fun idleDollyWarpPulseShake() {
        assertEquals(0f, MotionPresets.idleFloat(0), 1e-6f); assertEquals(2.5f, MotionPresets.idleFloat(1000), 1e-4f)
        assertEquals(-2.5f, MotionPresets.idleFloat(3000), 1e-4f); assertEquals(MotionPresets.idleFloat(500), MotionPresets.idleFloat(4500), 1e-5f)
        assertEquals(1f, MotionPresets.dollyScale(0), 1e-6f); assertEquals(1.015f, MotionPresets.dollyScale(4000), 1e-5f)
        assertEquals(1.03f, MotionPresets.dollyScale(8000), 1e-5f); assertEquals(1f, MotionPresets.dollyScale(16000), 1e-6f)
        assertEquals(0f, MotionPresets.warpScale(0), 0f); assertEquals(5.2f, MotionPresets.warpScale(800), 1e-5f)
        assertTrue(MotionPresets.warpScale(400) < .2f)
        assertEquals(1f, MotionPresets.orbPulse(0), 1e-6f); assertEquals(1.08f, MotionPresets.orbPulse(1000), 1e-5f)
        var maxAbs = 0f
        for (t in 0L..5000L step 7) for (ax in 0..1) {
            val v = MotionPresets.shake(t, 42L, ax); maxAbs = maxOf(maxAbs, abs(v))
            assertEquals(v, MotionPresets.shake(t, 42L, ax), 0f)
        }
        assertTrue(maxAbs <= 2f && maxAbs > 1f)
        assertTrue(MotionPresets.shake(123, 1L, 0) != MotionPresets.shake(123, 2L, 0))
        assertEquals(3f, MotionPresets.ALARM_GLOW_FACTOR, 0f)
    }
}
