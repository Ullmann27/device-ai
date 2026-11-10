package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AvatarOcularTransitionTest {
    private fun pose(state: AvatarState) = AvatarPose(
        state, AvatarExpression.NEUTRAL, 0f, 0f, 0f, Viseme.SIL, 0f, 0f)

    @Test fun stateChangesCannotTeleportEyesWithoutElapsedTime() {
        for (active in listOf(AvatarState.LISTENING, AvatarState.THINKING, AvatarState.SPEAKING)) {
            for ((from, to) in listOf(AvatarState.IDLE to active, active to AvatarState.IDLE)) {
                val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
                mapper.update(pose(from), 2.4f, true)
                val yaw = mapper.eyeYaw
                val pitch = mapper.eyePitch
                mapper.update(pose(to), 2.4f, true)
                assertEquals("$from -> $to yaw without time", yaw, mapper.eyeYaw, 1e-5f)
                assertEquals("$from -> $to pitch without time", pitch, mapper.eyePitch, 1e-5f)
                mapper.update(pose(to), 2.4f + 1f / 60f, true)
                assertTrue("$from -> $to yaw first frame", abs(mapper.eyeYaw - yaw) < .5f)
                assertTrue("$from -> $to pitch first frame", abs(mapper.eyePitch - pitch) < .5f)
            }
        }
    }

    @Test fun reducedMotionStillSettlesImmediately() {
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        mapper.update(pose(AvatarState.IDLE), 2.4f, true)
        mapper.update(pose(AvatarState.LISTENING).copy(
            gazeX = .6f, gazeY = -.4f, gazeObserved = true), 2.4f, false)
        assertEquals(.6f * AvatarMorphMapper.EYE_YAW_DEGREES, mapper.eyeYaw, 0f)
        assertEquals(.4f * AvatarMorphMapper.EYE_PITCH_DEGREES, mapper.eyePitch, 0f)
        assertEquals(.6f * AvatarMorphMapper.HEAD_YAW_DEGREES, mapper.headYaw, 0f)
        assertEquals(.4f * AvatarMorphMapper.HEAD_PITCH_DEGREES, mapper.headPitch, 0f)
        assertEquals(0f, mapper.jawPitch, 0f)
    }
}
