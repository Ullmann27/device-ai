package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Owner requirement: eyes lead, the head follows smoothly; no hard jumps, idle breathing only at rest. */
class AvatarHeadFollowTest {
    private fun pose(x: Float, y: Float = 0f, state: AvatarState = AvatarState.LISTENING, observed: Boolean = true) =
        AvatarPose(state, AvatarExpression.NEUTRAL, x, y, 0f, Viseme.SIL, 0f, 0f, gazeObserved = observed)
    private val totalYaw = AvatarMorphMapper.HEAD_YAW_DEGREES + AvatarMorphMapper.EYE_YAW_DEGREES

    @Test fun eyesReachTheTargetAtOnceWhileTheHeadStillTurns() {
        val m = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        m.update(pose(0f), 10f, true)
        assertEquals(0f, m.headYaw, 0f)
        m.update(pose(.5f), 10.016f, true)
        assertTrue("head must lag behind a sudden gaze change", m.headYaw > 0f && m.headYaw < AvatarMorphMapper.HEAD_YAW_DEGREES * .1f)
        assertEquals("eyes compensate: total gaze is on target in the first frame", .5f * totalYaw, m.headYaw + m.eyeYaw, 1e-4f)
    }

    @Test fun largeJumpsSaturateTheEyesAtTheHumanLimitWhileTheHeadCatchesUp() {
        val m = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        m.update(pose(-1f), 20f, true)
        m.update(pose(1f), 20.016f, true)
        assertEquals(AvatarMorphMapper.EYE_MAX_YAW_DEGREES, m.eyeYaw, 0f)
        assertTrue(m.headYaw + m.eyeYaw < totalYaw)
    }

    @Test fun headConvergesSmoothlyWithoutOvershoot() {
        val m = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        m.update(pose(0f), 0f, true)
        var previous = 0f
        var t = 0f
        repeat(60) {
            t += 1f / 60f
            m.update(pose(1f), t, true)
            assertTrue("monotonic, no overshoot", m.headYaw >= previous && m.headYaw <= AvatarMorphMapper.HEAD_YAW_DEGREES)
            assertTrue("no hard jump per frame", m.headYaw - previous < 3f)
            previous = m.headYaw
        }
        assertTrue("settled after one second", abs(m.headYaw - AvatarMorphMapper.HEAD_YAW_DEGREES) < .1f)
        assertEquals(AvatarMorphMapper.EYE_YAW_DEGREES, m.eyeYaw, .1f)
    }

    @Test fun firstFrameClockJumpsAndReducedMotionSnapExactly() {
        val m = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        m.update(pose(.5f, -.5f), 3f, true)
        assertEquals(.5f * AvatarMorphMapper.HEAD_YAW_DEGREES, m.headYaw, 0f)
        m.update(pose(-1f), 4.5f, true) // > 1 s gap: snap
        assertEquals(-AvatarMorphMapper.HEAD_YAW_DEGREES, m.headYaw, 0f)
        m.update(pose(1f), 4.52f, false) // reduced motion: no animation, exact target
        assertEquals(AvatarMorphMapper.HEAD_YAW_DEGREES, m.headYaw, 0f)
    }

    @Test fun breathingIsSmallAndOnlyAtRest() {
        val idle = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val talking = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        var maxDiff = 0f
        for (i in 0..276) {
            val t = 100f + i / 60f
            idle.update(pose(0f, state = AvatarState.IDLE, observed = false), t, true)
            talking.update(pose(0f, state = AvatarState.SPEAKING, observed = false), t, true)
            assertEquals("no idle motion while speaking", 0f, talking.headPitch, 0f)
            maxDiff = maxOf(maxDiff, abs(idle.headPitch))
        }
        assertTrue("breathing and idle sway stay subtle", maxDiff > 0f && maxDiff < 4f)
    }
}
