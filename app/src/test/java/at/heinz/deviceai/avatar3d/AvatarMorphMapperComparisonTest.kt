package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarMorphMapperComparisonTest {
    @Test fun criticallyDampedSpringsReachPinnedEightyPercentTimesWithoutOvershoot() {
        val eye = CriticalDampedSpring()
        val head = CriticalDampedSpring()

        eye.snap(0f)
        head.snap(0f)
        val eyeAt55 = eye.step(1f, .055f, AvatarMorphMapper.EYE_SPRING_OMEGA)
        val headAt220 = head.step(1f, .220f, AvatarMorphMapper.HEAD_SPRING_OMEGA)

        assertEquals(.80f, eyeAt55, .015f)
        assertEquals(.80f, headAt220, .015f)
        assertTrue(eyeAt55 in 0f..1f)
        assertTrue(headAt220 in 0f..1f)
    }

    @Test fun repeatedTimestampIsStrictlyIdempotent() {
        val spring = CriticalDampedSpring()
        spring.snap(0f)
        spring.step(1f, .016f, AvatarMorphMapper.HEAD_SPRING_OMEGA)
        val value = spring.value
        val velocity = spring.velocity

        repeat(20) {
            assertEquals(value, spring.step(-1f, 0f, AvatarMorphMapper.HEAD_SPRING_OMEGA), 0f)
            assertEquals(value, spring.value, 0f)
            assertEquals(velocity, spring.velocity, 0f)
        }
    }

    @Test fun eyesLeadHeadAndReducedMotionSnapsExactly() {
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        mapper.update(pose(0f), 10f, true)
        mapper.update(pose(.8f), 10.055f, true)

        assertTrue("eye spring must lead the slower head spring",
            kotlin.math.abs(mapper.eyeYaw) > kotlin.math.abs(mapper.headYaw))
        assertTrue(mapper.headYaw in 0f..(AvatarMorphMapper.HEAD_YAW_DEGREES * .8f))

        mapper.update(pose(-1f), 10.071f, false)
        assertEquals(-AvatarMorphMapper.HEAD_YAW_DEGREES, mapper.headYaw, 0f)
        assertEquals(-AvatarMorphMapper.EYE_YAW_DEGREES, mapper.eyeYaw, 0f)
    }

    @Test fun silenceAndBilabialSealStillCloseMouthImmediately() {
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        mapper.update(speechPose(Viseme.AA, .7f), 1f, true)
        assertTrue(mapper.jawPitch > 0f)

        mapper.update(speechPose(Viseme.SIL, 0f), 1.016f, true)
        assertEquals(0f, mapper.jawPitch, 0f)
        assertEquals(0f, weight(mapper, "JawOpen"), 0f)

        mapper.update(speechPose(Viseme.AA, .7f), 1.032f, true)
        mapper.update(speechPose(Viseme.PP, .7f), 1.048f, true)
        assertEquals(0f, mapper.jawPitch, 0f)
        assertEquals(1f, weight(mapper, "PP"), 0f)
        assertEquals(0f, weight(mapper, "AA"), 0f)
    }

    private fun pose(x: Float) = AvatarPose(
        AvatarState.LISTENING, AvatarExpression.NEUTRAL, x, 0f,
        0f, Viseme.SIL, 0f, 0f, gazeObserved = true
    )

    private fun speechPose(viseme: Viseme, jaw: Float) = AvatarPose(
        AvatarState.SPEAKING, AvatarExpression.NEUTRAL, 0f, 0f,
        jaw, viseme, 1f, jaw
    )

    private fun weight(mapper: AvatarMorphMapper, name: String) =
        mapper.weights[AvatarGlbContract.TARGETS.indexOf(name)]
}
