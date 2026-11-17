package at.heinz.deviceai.referencecheck

import at.heinz.deviceai.avatar.*
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Independent presentation contracts, frozen before the new motion candidate.
 * These execute the actual production mapper/reducer, never a model or device.
 * Degree limits are conservative engineering bounds, not video-derived motion capture.
 */
class MapperMotionChallengeTest {
    private fun mapper() = AvatarMorphMapper(AvatarGlbContract.TARGETS)
    private fun pose(state: AvatarState = AvatarState.IDLE, gx: Float = 0f, gy: Float = 0f,
                     jaw: Float = 0f, viseme: Viseme = Viseme.SIL, weight: Float = 0f) =
        AvatarPose(state, AvatarExpression.NEUTRAL, gx, gy, jaw, viseme, weight, jaw)
    private fun value(m: AvatarMorphMapper, name: String) = m.weights[AvatarGlbContract.TARGETS.indexOf(name)]
    private fun angles(m: AvatarMorphMapper) = listOf(m.headYaw, m.headPitch, m.eyeYaw, m.eyePitch, m.jawPitch)

    @Test fun idleHeadHasBoundedNaturalMovementAcrossAFullMinute() {
        val m = mapper(); val samples = (0..240).map { m.update(pose(), it / 4f, true); m.headYaw to m.headPitch }
        assertTrue("Idle head never moves", samples.any { abs(it.first - samples[0].first) > .1f || abs(it.second - samples[0].second) > .1f })
        assertTrue(samples.all { abs(it.first) <= 3f && abs(it.second) <= 2f })
    }

    @Test fun idleEyesMakeSmallGazeChangesWithoutOwnerTextOrCamera() {
        val m = mapper(); val samples = (0..240).map { m.update(pose(), it / 4f, true); m.eyeYaw to m.eyePitch }
        assertTrue("Idle gaze never changes", samples.any { abs(it.first - samples[0].first) > .1f || abs(it.second - samples[0].second) > .1f })
        assertTrue(samples.all { abs(it.first) <= 5f && abs(it.second) <= 4f })
    }

    @Test fun reducedMotionIsTimeInvariantAndClosesCosmeticSpeech() {
        val m = mapper(); val p = pose(AvatarState.SPEAKING, .35f, -.4f, .8f, Viseme.O, 1f)
        m.update(p, 0f, false); val reference = angles(m); val weights = m.weights.copyOf()
        for (t in listOf(1f, 4.51f, 9.2f, 100f, Float.NaN, Float.POSITIVE_INFINITY)) {
            m.update(p, t, false); assertEquals(reference, angles(m)); assertArrayEquals(weights, m.weights, 0f)
            assertEquals(0f, m.jawPitch, 0f); assertEquals(0f, value(m, "JawOpen"), 0f)
        }
    }

    @Test fun eventGazeRemainsTheBaseForEveryState() {
        val m = mapper()
        for (s in AvatarState.entries) for (gx in listOf(-1f, 0f, .35f, 1f)) for (gy in listOf(-1f, -.4f, 0f, 1f)) {
            val p = pose(s, gx, gy)
            for (t in listOf(0f, 1.7f, 4.51f, 22.7f)) {
                m.update(p, t, true)
                assertTrue(abs(m.headYaw - gx * 8f) <= 3.001f)
                assertTrue(abs(m.headPitch + gy * 5f) <= 2.001f)
                assertTrue(abs(m.eyeYaw - gx * 17f) <= 5.001f)
                assertTrue(abs(m.eyePitch + gy * 10f) <= 4.001f)
                assertEquals(s, p.state)
            }
        }
    }

    @Test fun idleNeverManufacturesSpeechMorphs() {
        val m = mapper()
        for (i in 0..480) {
            m.update(pose(), i / 8f, true)
            for (v in Viseme.entries) assertEquals(0f, value(m, v.name), 0f)
            assertEquals(0f, value(m, "JawOpen"), 0f); assertEquals(0f, m.jawPitch, 0f)
        }
    }

    @Test fun speakingLabelAloneDoesNotInventAudibleSpeech() {
        val m = mapper()
        for (t in listOf(0f, 1f, 4.51f, 8f)) {
            m.update(pose(AvatarState.SPEAKING), t, true)
            assertEquals(0f, value(m, "JawOpen"), 0f); assertEquals(0f, m.jawPitch, 0f)
            for (v in Viseme.entries) assertEquals(0f, value(m, v.name), 0f)
        }
    }

    @Test fun originalUtteranceBoundMouthMappingIsPreserved() {
        val m = mapper()
        for (v in Viseme.entries.filter { it != Viseme.SIL }) for (jaw in listOf(0f, .25f, .5f, 1f)) {
            m.update(pose(AvatarState.SPEAKING, jaw = jaw, viseme = v, weight = .73f), 0f, true)
            assertEquals(jaw, value(m, "JawOpen"), 0f); assertEquals(jaw * 12f, m.jawPitch, .0001f)
            assertEquals(.73f, value(m, v.name), 0f)
            for (other in Viseme.entries.filter { it != v }) assertEquals(0f, value(m, other.name), 0f)
        }
    }

    @Test fun stoppedAndStaleSpeechCannotKeepTheMouthMoving() {
        val r = AvatarPoseReducer(); val m = mapper()
        r.apply(AvatarEvent.SpeechStarted("active", "synthetic", 100))
        r.apply(AvatarEvent.AudioLevel("active", 1f, 101))
        r.apply(AvatarEvent.VisemeCue("active", Viseme.O, 1f, 500, 101))
        m.update(r.pose(102), 1f, true); assertTrue(m.jawPitch > 0f)
        r.apply(AvatarEvent.SpeechEnded("active", true, 103))
        r.apply(AvatarEvent.AudioLevel("active", 1f, 104))
        r.apply(AvatarEvent.VisemeCue("active", Viseme.O, 1f, 500, 104))
        m.update(r.pose(105), 2f, true)
        assertEquals(0f, m.jawPitch, 0f); for (v in Viseme.entries) assertEquals(0f, value(m, v.name), 0f)
    }

    @Test fun blinkActuallyClosesAndReopensWithoutChangingState() {
        val m = mapper(); var minimum = 1f; var maximum = 0f
        for (i in 0..2000) {
            m.update(pose(), i / 200f, true)
            val left = value(m, "BlinkLeft"); val right = value(m, "BlinkRight")
            assertTrue(left in 0f..1f && right in 0f..1f)
            minimum = minOf(minimum, left, right); maximum = maxOf(maximum, left, right)
        }
        assertEquals(0f, minimum, 0f); assertTrue("No actual blink closure", maximum > .8f)
    }

    @Test fun consecutiveFramesDoNotJumpHeadOrEyes() {
        val m = mapper(); m.update(pose(), 0f, true); var previous = angles(m)
        for (i in 1..3600) {
            m.update(pose(), i / 60f, true); val current = angles(m)
            for (j in 0..3) assertTrue("Motion jump channel=$j frame=$i", abs(current[j] - previous[j]) <= 1.5f)
            previous = current
        }
    }

    @Test fun nonfiniteTimeOrPoseDoesNotPoisonAnyTransform() {
        val m = mapper()
        for (t in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1000f, Float.MAX_VALUE)) {
            m.update(pose(AvatarState.SPEAKING, Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, Viseme.AA, Float.NaN), t, true)
            assertTrue(angles(m).all { it.isFinite() }); assertTrue(m.weights.all { it.isFinite() && it in 0f..1f })
        }
    }

    @Test fun repeatedUpdatesReuseTheBoundedMorphBufferAndDoNotRetainOldSpeech() {
        val m = mapper(); val original = m.weights
        m.update(pose(AvatarState.SPEAKING, jaw = 1f, viseme = Viseme.AA, weight = 1f), 0f, true)
        repeat(10_000) { m.update(pose(), it / 60f, true); assertSame(original, m.weights) }
        assertEquals(AvatarGlbContract.TARGETS.size, m.weights.size)
        assertEquals(0f, value(m, "AA"), 0f); assertEquals(0f, value(m, "JawOpen"), 0f)
    }
}
