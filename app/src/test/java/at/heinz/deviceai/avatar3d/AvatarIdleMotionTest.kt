package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import kotlin.math.hypot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Selected behavioural checks from PR502's motion probe, plus signed-zero regression. */
class AvatarIdleMotionTest {
    @Test fun negativeZeroAndNegativeWholeCyclesMatchPositiveZero() {
        val motion = AvatarIdleMotion()
        motion.update(0f)
        val expected = sample(motion)
        for (seconds in floatArrayOf(-0.0f, -460f, -920f)) {
            motion.update(13.425f)
            motion.update(seconds)
            assertArrayEquals("time=$seconds", expected, sample(motion), 0f)
        }
    }

    @Test fun idleGazeHoldsItsFixationThenMovesToANewTarget() {
        val map = mapper()
        map.update(pose(), .5f, true)
        val yaw = map.eyeYaw
        val pitch = map.eyePitch
        for (seconds in floatArrayOf(1f, 2.8f)) {
            map.update(pose(), seconds, true)
            assertEquals(yaw, map.eyeYaw, 0f)
            assertEquals(pitch, map.eyePitch, 0f)
        }
        map.update(pose(), 3.4f, true)
        assertTrue(hypot((map.eyeYaw - yaw).toDouble(), (map.eyePitch - pitch).toDouble()) > .1)
    }

    @Test fun doubleBlinkReopensBothLidsBetweenClosures() {
        val map = mapper()
        for (seconds in floatArrayOf(13.425f, 13.725f)) {
            map.update(pose(), seconds, true)
            assertTrue("closure at $seconds", weight(map, "BlinkLeft") > .99f)
            assertEquals(weight(map, "BlinkLeft"), weight(map, "BlinkRight"), 0f)
            if (seconds == 13.425f) {
                map.update(pose(), 13.575f, true)
                assertEquals(0f, weight(map, "BlinkLeft"), 0f)
                assertEquals(0f, weight(map, "BlinkRight"), 0f)
            }
        }
    }

    @Test fun cosmeticSamplingIsIndependentOfPriorFrames() {
        for (seconds in floatArrayOf(.04f, 3.09f, 13.425f, 84.22f, 459.99f)) {
            val frequent = mapper()
            val discontinuous = mapper()
            for (frame in 0..30) frequent.update(pose(), frame * .013f, true)
            for (old in floatArrayOf(12f, 459.5f, -50f, 4f, Float.NaN)) {
                discontinuous.update(pose(), old, true)
            }
            frequent.update(pose(), seconds, true)
            discontinuous.update(pose(), seconds, true)
            assertArrayEquals(angles(frequent), angles(discontinuous), 0f)
            assertArrayEquals(frequent.weights, discontinuous.weights, 0f)
        }
    }

    @Test fun cosmeticMotionRemainsContinuousAcrossClockWrap() {
        val map = mapper()
        map.update(pose(), 459.999f, true)
        val before = angles(map)
        map.update(pose(), 0f, true)
        assertArrayEquals(before, angles(map), .003f)
        assertEquals(0f, weight(map, "BlinkLeft"), 0f)
        assertEquals(0f, weight(map, "BlinkRight"), 0f)
    }

    @Test fun wholeCycleOffsetsPreserveTheCosmeticPose() {
        for (seconds in floatArrayOf(.5f, 3.5f, 13.5f, 84.5f)) {
            val first = mapper()
            val next = mapper()
            first.update(pose(), seconds, true)
            next.update(pose(), seconds + 460f, true)
            assertArrayEquals(angles(first), angles(next), .001f)
            assertArrayEquals(first.weights, next.weights, .001f)
        }
    }

    @Test fun observedGazeOverridesEveryCosmeticIdleOffset() {
        val map = mapper()
        val observed = pose().copy(gazeX = .25f, gazeY = -.4f, gazeObserved = true)
        for (seconds in floatArrayOf(1f, 13.425f, 200f)) {
            map.update(observed, seconds, true)
            assertArrayEquals(floatArrayOf(2f, 2f, 4.25f, 4f), angles(map), 0f)
        }
    }

    @Test fun everyNonIdleStateRetainsItsActualGaze() {
        for (state in AvatarState.entries.filter { it != AvatarState.IDLE }) {
            val map = mapper()
            map.update(pose().copy(state = state, gazeX = .2f, gazeY = .3f), 13.425f, true)
            assertArrayEquals(state.name, floatArrayOf(1.6f, -1.5f, 3.4f, -3f), angles(map), .00001f)
        }
    }

    @Test fun reducedMotionStopsCosmeticMovementAndBlinkingImmediately() {
        val map = mapper()
        map.update(pose(), 13.425f, true)
        assertTrue(weight(map, "BlinkLeft") > .99f)
        assertTrue(angles(map).any { it != 0f })
        map.update(pose(), 13.425f, false)
        assertArrayEquals(FloatArray(4), angles(map), 0f)
        assertArrayEquals(FloatArray(map.weights.size), map.weights, 0f)
        assertEquals(0f, map.jawPitch, 0f)
    }

    private fun mapper() = AvatarMorphMapper(AvatarGlbContract.TARGETS)
    private fun pose() = AvatarPose(AvatarState.IDLE, AvatarExpression.NEUTRAL, 0f, 0f, 0f, Viseme.SIL, 0f, 0f)
    private fun weight(map: AvatarMorphMapper, name: String) = map.weights[AvatarGlbContract.TARGETS.indexOf(name)]
    private fun angles(map: AvatarMorphMapper) = floatArrayOf(map.headYaw, map.headPitch, map.eyeYaw, map.eyePitch)
    private fun sample(motion: AvatarIdleMotion) =
        floatArrayOf(motion.headYaw, motion.headPitch, motion.eyeYaw, motion.eyePitch, motion.blinkWeight)
}
