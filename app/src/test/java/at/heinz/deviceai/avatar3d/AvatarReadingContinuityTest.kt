package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.*
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class AvatarReadingContinuityTest {
    private fun pose(reading: Boolean) = AvatarPose(AvatarState.THINKING, AvatarExpression.FOCUSED,
        .6f, -.2f, 0f, Viseme.SIL, 0f, 0f, gazeDirected = true, reading = reading)

    @Test fun verticalWorkMotionHasNoLineOrCycleJumps() {
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        var previous = 0f
        var largest = 0f
        for (frame in 0..1920) {
            mapper.update(pose(true), frame / 120f, true)
            if (frame > 120) largest = maxOf(largest, abs(mapper.eyePitch - previous))
            previous = mapper.eyePitch
        }
        assertTrue("120 Hz work motion must stay continuous, measured $largest degrees/frame", largest < .15f)
        println("READING max_eye_pitch_step_120hz_degrees=$largest")
    }

    @Test fun eyesLeadASmallDelayedHeadFollowThroughoutRealWork() {
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val heads = mutableListOf<Float>()
        val eyes = mutableListOf<Float>()
        for (frame in 0..960) {
            mapper.update(pose(true), frame / 60f, true)
            if (frame > 120) { heads += mapper.headYaw; eyes += mapper.eyeYaw }
        }
        val headRange = heads.max() - heads.min()
        val eyeRange = eyes.max() - eyes.min()
        assertTrue("head follows instead of freezing: $headRange", headRange > .5f)
        assertTrue("ocular motion still leads", headRange < eyeRange * .3f)
        assertTrue(heads.all { it in 13f..16f })
        println("READING head_range_degrees=$headRange eye_range_degrees=$eyeRange")
    }

    @Test fun endingWorkFadesTheCosmeticOffsetAndDuplicateFramesAreIdempotent() {
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        for (frame in 0..80) mapper.update(pose(true), frame / 60f, true)
        val before = mapper.eyeYaw
        mapper.update(pose(false), 81f / 60f, true)
        assertTrue("no abrupt reading-offset removal", abs(mapper.eyeYaw - before) < .5f)
        val once = mapper.eyeYaw to mapper.headYaw
        mapper.update(pose(false), 81f / 60f, true)
        assertEquals(once, mapper.eyeYaw to mapper.headYaw)
        mapper.update(pose(true), 2f, false)
        val static = mapper.eyeYaw to mapper.headYaw
        mapper.update(pose(true), 4f, false)
        assertEquals(static, mapper.eyeYaw to mapper.headYaw)
    }
}
