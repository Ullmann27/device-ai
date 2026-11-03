package at.heinz.deviceai.referencecheck

import at.heinz.deviceai.avatar.*
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Independent event/lifetime contract, frozen before inspecting the implementation.
 * Reflection allows the unchanged baseline to compile and report missing API as RED.
 * Synthetic input proves neither a camera connection nor real human tracking.
 */
class ObservedGazeChallengeTest {
    private fun observed(x: Float, y: Float, confidence: Int, at: Long): AvatarEvent =
        requiredClass("GazeObserved").getConstructor(GazeTarget::class.java, Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType).newInstance(GazeTarget(x, y), confidence, at) as AvatarEvent

    private fun cleared(at: Long): AvatarEvent = requiredClass("GazeCleared")
        .getConstructor(Long::class.javaPrimitiveType).newInstance(at) as AvatarEvent

    private fun requiredClass(name: String): Class<*> = try {
        Class.forName("at.heinz.deviceai.avatar.AvatarEvent\$$name")
    } catch (e: ClassNotFoundException) { throw AssertionError("MISSING_OBSERVED_GAZE_API:$name", e) }

    private fun isObserved(pose: AvatarPose): Boolean = try {
        pose.javaClass.getMethod("getGazeObserved").invoke(pose) as Boolean
    } catch (e: NoSuchMethodException) { throw AssertionError("MISSING_OBSERVED_GAZE_POSE_FLAG", e) }

    private fun submit(d: AvatarDirector, gaze: GazeTarget, confidence: Int, age: Long): Boolean = try {
        d.javaClass.getMethod("onObservedGaze", GazeTarget::class.java, Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType).invoke(d, gaze, confidence, age) as Boolean
    } catch (e: NoSuchMethodException) { throw AssertionError("MISSING_OBSERVED_GAZE_DIRECTOR_API", e) }

    private fun state(s: AvatarState, at: Long) = AvatarEvent.StateChanged(s, AvatarExpression.NEUTRAL,
        if (s == AvatarState.THINKING) GazeTarget.THINKING_AWAY else GazeTarget.OWNER, at)

    @Test fun legacyPoseDoesNotClaimObservedTracking() {
        val p = AvatarPose(AvatarState.IDLE, AvatarExpression.NEUTRAL, 0f, 0f, 0f, Viseme.SIL, 0f, 0f)
        assertFalse(isObserved(p))
    }

    @Test fun directorAcceptsBoundaryConfidenceAndAgeInItsOwnClock() {
        for (confidence in listOf(60, 100)) for (age in listOf(0L, 750L)) {
            val events = mutableListOf<AvatarEvent>(); val d = AvatarDirector(AvatarSink { events += it }) { 10_000L }
            assertTrue(submit(d, GazeTarget(.3f, -.2f), confidence, age))
            assertEquals(1, events.size); assertEquals(10_000L - age, events.single().atMillis)
            val r = AvatarPoseReducer(); r.apply(events.single()); assertTrue(isObserved(r.pose(10_000L)))
            assertEquals(.3f, r.pose(10_000L).gazeX, 0f)
        }
    }

    @Test fun invalidDirectorSampleClearsEarlierObservation() {
        for ((confidence, age) in listOf(59 to 0L, 101 to 0L, 60 to -1L, 100 to 751L, 100 to Long.MAX_VALUE)) {
            val r = AvatarPoseReducer(); val d = AvatarDirector(AvatarSink(r::apply)) { 10_000L }
            assertTrue(submit(d, GazeTarget(.4f, .2f), 90, 0)); assertTrue(isObserved(r.pose(10_000)))
            assertFalse(submit(d, GazeTarget(-.4f, -.2f), confidence, age)); assertFalse(isObserved(r.pose(10_000)))
        }
    }

    @Test fun observationExpiresExactlyAfterSevenHundredFiftyMilliseconds() {
        val r = AvatarPoseReducer(); r.apply(observed(.2f, -.2f, 90, 1_000))
        assertTrue(isObserved(r.pose(1_750))); assertFalse(isObserved(r.pose(1_751)))
        assertEquals(0f, r.pose(1_751).gazeX, 0f); assertEquals(0f, r.pose(1_751).gazeY, 0f)
    }

    @Test fun futureObservationIsNeverPresentedAsCurrent() {
        val r = AvatarPoseReducer(); r.apply(observed(.8f, -.7f, 80, 2_000))
        val p = r.pose(1_000); assertFalse(isObserved(p)); assertEquals(0f, p.gazeX, 0f)
    }

    /** Added after source inspection, before execution: not part of the original 25-case freeze. */
    @Test fun discardedFutureObservationCannotLockOutNewCurrentObservations() {
        for (future in listOf(2_000L, Long.MAX_VALUE)) {
            val r = AvatarPoseReducer(); r.apply(observed(.8f, -.7f, 80, future))
            assertFalse(isObserved(r.pose(1_000)))
            r.apply(observed(.2f, .1f, 90, 1_001))
            assertTrue("Discarded future timestamp locked out current observations: $future", isObserved(r.pose(1_002)))
            assertEquals(.2f, r.pose(1_002).gazeX, 0f)
        }
    }

    @Test fun outOfOrderOlderObservationCannotReplaceTheLatest() {
        val r = AvatarPoseReducer(); r.apply(observed(.4f, .2f, 70, 1_100))
        r.apply(observed(-.8f, -.7f, 100, 1_050))
        val p = r.pose(1_150); assertTrue(isObserved(p)); assertEquals(.4f, p.gazeX, 0f); assertEquals(.2f, p.gazeY, 0f)
    }

    @Test fun clearWatermarkRejectsStaleAndEqualTimestampReplay() {
        for (replayAt in listOf(1_000L, 1_100L)) {
            val r = AvatarPoseReducer(); r.apply(observed(.4f, .2f, 80, 1_000)); r.apply(cleared(1_100))
            r.apply(observed(-.4f, -.2f, 100, replayAt)); assertFalse(isObserved(r.pose(1_101)))
            r.apply(observed(.1f, .3f, 70, 1_101)); assertTrue(isObserved(r.pose(1_102)))
        }
    }

    @Test fun taskStatesRejectTrackingAndCannotReviveAnOldObservation() {
        for (s in listOf(AvatarState.THINKING, AvatarState.RESEARCHING, AvatarState.LEARNING,
            AvatarState.WAITING_FOR_APPROVAL, AvatarState.BLOCKED)) {
            val r = AvatarPoseReducer(); r.apply(observed(.7f, .6f, 100, 1_000)); r.apply(state(s, 1_100))
            r.apply(observed(-.7f, -.6f, 100, 1_101)); assertFalse(isObserved(r.pose(1_102)))
            r.apply(state(AvatarState.IDLE, 1_200)); assertFalse(isObserved(r.pose(1_201)))
            r.apply(observed(.2f, .1f, 80, 1_001)); assertFalse(isObserved(r.pose(1_202)))
        }
    }

    @Test fun directorAllowsOnlyIdleListeningAndSpeaking() {
        val configurations = listOf(
            AvatarRuntimeSignals() to true, AvatarRuntimeSignals(listening = true) to true,
            AvatarRuntimeSignals(speaking = true) to true, AvatarRuntimeSignals(busy = true) to false,
            AvatarRuntimeSignals(researching = true) to false,
            AvatarRuntimeSignals(busy = true, learning = true) to false,
            AvatarRuntimeSignals(awaitingApproval = true) to false, AvatarRuntimeSignals(blocked = true) to false)
        for ((signals, allowed) in configurations) {
            val r = AvatarPoseReducer(); val d = AvatarDirector(AvatarSink(r::apply)) { 5_000L }
            d.onRuntime(signals); assertEquals(allowed, submit(d, GazeTarget(.2f, .3f), 70, 0L))
            assertEquals(allowed, isObserved(r.pose(5_000)))
        }
    }

    @Test fun observedLookOverridesAllCosmeticIdleDrift() {
        val r = AvatarPoseReducer(); r.apply(observed(.3f, -.2f, 90, 1_000)); val p = r.pose(1_001)
        assertTrue(isObserved(p)); val m = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        for (t in listOf(0f, .75f, 4.5f, 21f, 459.99f)) {
            m.update(p, t, true)
            assertEquals(.3f * 8f, m.headYaw, .0001f); assertEquals(.2f * 5f, m.headPitch, .0001f)
            assertEquals(.3f * 17f, m.eyeYaw, .0001f); assertEquals(.2f * 10f, m.eyePitch, .0001f)
        }
    }

    @Test fun speechCallbacksDoNotFabricateOrEraseAValidLook() {
        val r = AvatarPoseReducer(); r.apply(state(AvatarState.SPEAKING, 1_000))
        r.apply(observed(.6f, -.3f, 90, 1_001)); r.apply(AvatarEvent.SpeechStarted("real", "synthetic", 1_002))
        r.apply(AvatarEvent.AudioLevel("real", .7f, 1_003)); assertTrue(isObserved(r.pose(1_004)))
        assertTrue(r.pose(1_004).jawOpen > 0f)
        r.apply(AvatarEvent.SpeechEnded("real", true, 1_005)); val p = r.pose(1_006)
        assertTrue(isObserved(p)); assertEquals(.6f, p.gazeX, 0f); assertEquals(0f, p.jawOpen, 0f)
    }

    @Test fun malformedGazeCannotBeConstructed() {
        for (x in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1.001f, 1.001f)) {
            try { GazeTarget(x, 0f); fail("Invalid x accepted: $x") } catch (_: IllegalArgumentException) { }
            try { GazeTarget(0f, x); fail("Invalid y accepted: $x") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun viewClockWrapDoesNotJumpHeadOrEyes() {
        val p = AvatarPose(AvatarState.IDLE, AvatarExpression.NEUTRAL, 0f, 0f, 0f, Viseme.SIL, 0f, 0f)
        val m = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        m.update(p, 460f - 1f / 60f, true); val before = listOf(m.headYaw, m.headPitch, m.eyeYaw, m.eyePitch)
        m.update(p, 0f, true); val after = listOf(m.headYaw, m.headPitch, m.eyeYaw, m.eyePitch)
        before.zip(after).forEachIndexed { i, (a, b) -> assertTrue("Clock wrap jump channel=$i delta=${abs(a-b)}", abs(a-b) <= .15f) }
    }
}
