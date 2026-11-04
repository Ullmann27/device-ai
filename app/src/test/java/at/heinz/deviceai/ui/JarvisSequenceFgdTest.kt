package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.AnswerPill
import at.heinz.deviceai.cockpit.AnswerPillKind
import at.heinz.deviceai.cockpit.ToolGatePillActionGate
import org.junit.Assert.*
import org.junit.Test

/** Muster F/G/D motion and look parameters (Jarvis Sicherheit, VIDEO-SPEC F/G/D). */
class JarvisSequenceFgdTest {
    private val pose = JarvisSequenceEffects.Companion::envelopePose

    @Test fun envelopeComesFromDepthInHalfASecondWithPerspective() {
        assertEquals(0f, pose(0L, 1f).depthScale, 1e-3f)
        assertTrue(pose(250L, 1f).depthScale in .2f..0.99f)
        assertEquals(1f, pose(500L, 1f).depthScale, 1e-3f)
        assertTrue("tilted while in depth", pose(100L, 1f).tiltDeg > 5f)
        assertEquals(0f, pose(500L, 1f).tiltDeg, 1e-3f)
        assertEquals("flap closed on arrival", 0f, pose(500L, 1f).flapAngleDeg, 1e-3f)
    }

    @Test fun flapOpensIn300msAsXRotationWithForeshortening() {
        assertEquals(180f, pose(800L, 1f).flapAngleDeg, 1e-3f)
        val mid = pose(650L, 1f)
        assertTrue(mid.flapAngleDeg in 60f..120f)
        assertTrue("near 90° the flap is seen edge-on", kotlin.math.abs(mid.flapProjected) < .5f)
        assertTrue("open flap points up", pose(800L, 1f).flapProjected < -.9f)
        assertTrue("perspective widens the flap toward the camera", mid.flapWidth > 1f)
    }

    @Test fun documentRisesAfterFlapThenLasersDrawIn300ms() {
        assertEquals(0f, pose(800L, 1f).docRise, 1e-3f)
        val laserStart = 800L + JarvisSequenceEffects.DOC_RISE_MS
        assertEquals(1f, pose(laserStart, 1f).docRise, 1e-3f)
        assertEquals(0f, pose(laserStart, 1f).laser, 1e-3f)
        assertEquals(1f, pose(laserStart + 300L, 1f).laser, 1e-3f)
    }

    @Test fun reducedMotionShowsFinalPoseAndNoGlowHasAFallback() {
        val p = pose(0L, 0f)
        assertEquals(1f, p.depthScale, 0f); assertEquals(180f, p.flapAngleDeg, 0f); assertEquals(1f, p.laser, 0f)
        assertEquals(0f, JarvisSequenceEffects.bloomStrength(JarvisDesign.Glow.OFF, 1f), 0f)
        assertTrue(JarvisSequenceEffects.bloomStrength(JarvisDesign.Glow.NORMAL, 1f) > 0f)
    }

    @Test fun pillsHaveNoAlphaFadeAndHardDeceleration() {
        val spec = JarvisMotion.Presets.pill(JarvisMotion.Direction.LEFT)
        for (t in 0L..spec.durationMs step 25) assertEquals(1f, JarvisMotion.frame(spec, t).alpha, 0f)
        assertTrue("hard deceleration: >80 % of the way after 40 % time", spec.easing(.4f) > .8f)
    }

    @Test fun sparksAndLasersAreBounded() {
        assertTrue(JarvisSequenceEffects.SPARKS_PER_LASER in 1..4)
        assertTrue(JarvisSequenceEffects.MAX_LASERS * JarvisSequenceEffects.SPARKS_PER_LASER <= 16)
    }

    @Test fun pillLabelsComeFromTheGateAndDisabledOnesSaySourceMissing() {
        val labels = JarvisSequenceEffects.pillLabels(ToolGatePillActionGate.canonicalPills())
        assertEquals(listOf("Direkt antworten? · Quelle fehlt", "Ausarbeiten? · Quelle fehlt", "Juristische Studie?",
            "Anrufen? · Quelle fehlt"), labels)
    }

    @Test fun tapHitsOnlyAnEnabledLandedPill() {
        val fx = JarvisSequenceEffects()
        val pills = ToolGatePillActionGate.canonicalPills()
        fx.answerPills(pills, nowMillis = 0L)
        val w = 1000f; val h = 600f
        val studie = fx.pillBounds(w, h, 2_000L).getValue(2)
        assertEquals(AnswerPillKind.STUDIE, fx.pillAt((studie.first + studie.third) / 2f, (studie.second + studie.fourth) / 2f, w, h, 2_000L)?.kind)
        val call = fx.pillBounds(w, h, 2_000L).getValue(3)
        assertNull("disabled pill is not tappable", fx.pillAt((call.first + call.third) / 2f, (call.second + call.fourth) / 2f, w, h, 2_000L))
        assertNull("not tappable while flying", fx.pillAt((studie.first + studie.third) / 2f, (studie.second + studie.fourth) / 2f, w, h, 10L))
    }
}
