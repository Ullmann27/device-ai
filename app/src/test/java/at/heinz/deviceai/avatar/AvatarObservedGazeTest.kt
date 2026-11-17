package at.heinz.deviceai.avatar

import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper
import org.junit.Assert.*
import org.junit.Test

/** Actual reducer/Director contracts; no camera, face detection or device claim. */
class AvatarObservedGazeTest {
    private var now = 10_000L
    private val reducer = AvatarPoseReducer()
    private val director = AvatarDirector(AvatarSink(reducer::apply)) { now }
    private val gaze = GazeTarget(.4f, -.3f)

    @Test fun freshHardwareObservationExpiresWithoutAnotherCallback() {
        assertTrue(director.onObservedGaze(gaze, 60, 100))
        assertEquals(gaze.x, reducer.pose(now).gazeX, 0f)
        assertTrue(reducer.pose(now + 650).gazeObserved)
        assertFalse(reducer.pose(now + 651).gazeObserved)
        assertEquals(GazeTarget.OWNER.x, reducer.pose(now + 652).gazeX, 0f)
        // A later backwards clock read cannot resurrect the expired sample.
        assertFalse(reducer.pose(now).gazeObserved)
    }

    @Test fun invalidConfidenceAndAgeClearAnEarlierObservation() {
        for ((confidence, age) in listOf(-1 to 0L, 59 to 0L, 101 to 0L,
            60 to -1L, 60 to 751L, 60 to Long.MIN_VALUE, 60 to Long.MAX_VALUE)) {
            now += 10
            assertTrue(director.onObservedGaze(gaze, 80, 0))
            assertTrue(reducer.pose(now).gazeObserved)
            assertFalse(director.onObservedGaze(gaze, confidence, age))
            assertFalse(reducer.pose(now).gazeObserved)
        }
    }

    @Test fun coordinatesRejectNaNInfinityAndOutOfBounds() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1.01f, 1.01f)) {
            try { GazeTarget(bad, 0f); fail("invalid x admitted") } catch (_: IllegalArgumentException) { }
            try { GazeTarget(0f, bad); fail("invalid y admitted") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(director.onObservedGaze(GazeTarget(-1f, 1f), 100, 750))
        assertTrue(reducer.pose(now).gazeObserved)
    }

    @Test fun clearDoesNotLetQueuedOlderObservationsReviveCameraGaze() {
        assertTrue(director.onObservedGaze(gaze, 80, 0))
        now += 100
        director.clearObservedGaze()
        reducer.apply(AvatarEvent.GazeObserved(gaze, 80, now - 50))
        assertFalse(reducer.pose(now).gazeObserved)
        now++
        assertTrue(director.onObservedGaze(gaze, 80, 0))
        assertTrue(reducer.pose(now).gazeObserved)
        reducer.clearObservedGaze(now) // Actual view pause/detach hook.
        assertFalse(reducer.pose(now).gazeObserved)
    }

    @Test fun outOfOrderSamplesCannotReplaceTheLatestPosition() {
        director.onObservedGaze(gaze, 80, 0)
        now += 10
        val newer = GazeTarget(-.6f, .2f)
        director.onObservedGaze(newer, 80, 0)
        director.onObservedGaze(gaze, 80, 20)
        assertEquals(newer.x, reducer.pose(now).gazeX, 0f)
    }

    @Test fun taskApprovalAndBlockedGazeRemainAuthoritative() {
        for (signals in listOf(AvatarRuntimeSignals(busy = true), AvatarRuntimeSignals(researching = true),
            AvatarRuntimeSignals(busy = true, learning = true), AvatarRuntimeSignals(awaitingApproval = true),
            AvatarRuntimeSignals(blocked = true))) {
            director.onRuntime(AvatarRuntimeSignals())
            now += 10
            director.onObservedGaze(gaze, 80, 0)
            now++
            director.onRuntime(signals)
            assertFalse(reducer.pose(now).gazeObserved)
            assertFalse(director.onObservedGaze(gaze, 80, 0))
            now++
            director.onRuntime(AvatarRuntimeSignals())
            assertFalse(reducer.pose(now).gazeObserved)
        }
    }

    @Test fun listeningAndSpeakingCanUseFreshObservationWithoutCreatingSpeech() {
        for (signals in listOf(AvatarRuntimeSignals(), AvatarRuntimeSignals(listening = true),
            AvatarRuntimeSignals(speaking = true))) {
            director.onRuntime(signals)
            now++
            assertTrue(director.onObservedGaze(gaze, 80, 0))
            val pose = reducer.pose(now)
            assertTrue(pose.gazeObserved)
            assertEquals(0f, pose.jawOpen, 0f)
            assertEquals(Viseme.SIL, pose.viseme)
        }
    }

    @Test fun observedPositionSuppressesCosmeticIdleWandering() {
        director.onObservedGaze(gaze, 80, 0)
        val pose = reducer.pose(now)
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        for (seconds in listOf(0f, 1.7f, 10f, 60f)) {
            mapper.update(pose, seconds, true)
            // Observed gaze maps exactly onto the (owner-widened) head/eye radius, without idle sway.
            assertEquals(gaze.x * AvatarMorphMapper.HEAD_YAW_DEGREES, mapper.headYaw, 0f)
            assertEquals(-gaze.y * AvatarMorphMapper.HEAD_PITCH_DEGREES, mapper.headPitch, 0f)
            assertEquals(gaze.x * AvatarMorphMapper.EYE_YAW_DEGREES, mapper.eyeYaw, 0f)
            assertEquals(-gaze.y * AvatarMorphMapper.EYE_PITCH_DEGREES, mapper.eyePitch, 0f)
        }
    }

    @Test fun directEventsCannotBypassConfidenceOrTaskPriority() {
        reducer.apply(AvatarEvent.GazeObserved(gaze, 59, now))
        assertFalse(reducer.pose(now).gazeObserved)
        reducer.apply(AvatarEvent.StateChanged(AvatarState.RESEARCHING, AvatarExpression.CURIOUS,
            GazeTarget.READING_DOWN, now))
        reducer.apply(AvatarEvent.GazeObserved(gaze, 100, now + 1))
        val pose = reducer.pose(now + 1)
        assertFalse(pose.gazeObserved)
        assertEquals(GazeTarget.READING_DOWN.y, pose.gazeY, 0f)
    }

    @Test fun sinkReplacementDoesNotReplayObservedOrOldCameraState() {
        director.onObservedGaze(gaze, 80, 0)
        val replacement = AvatarPoseReducer()
        director.attach(AvatarSink(replacement::apply))
        assertFalse(replacement.pose(now).gazeObserved)
        now++
        director.onObservedGaze(gaze, 80, 0)
        assertTrue(replacement.pose(now).gazeObserved)
        director.clearObservedGaze()
        assertFalse(replacement.pose(now).gazeObserved)
    }

    @Test fun idleMotionIsContinuousAcrossTheExistingViewClockWrap() {
        val pose = reducer.pose(now)
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        fun angles() = listOf(mapper.headYaw, mapper.headPitch, mapper.eyeYaw, mapper.eyePitch)
        mapper.update(pose, 460f - 1f / 60f, true)
        val before = angles()
        mapper.update(pose, 0f, true)
        val after = angles()
        before.zip(after).forEach { (a, b) -> assertTrue(kotlin.math.abs(a - b) < .1f) }
    }

    @Test fun rejectedFutureTimestampCannotPoisonNewSamplesOrUndoPreviousOrdering() {
        reducer.apply(AvatarEvent.GazeObserved(gaze, 80, now))
        assertTrue(reducer.pose(now).gazeObserved)
        reducer.apply(AvatarEvent.GazeObserved(GazeTarget.OWNER, 80, Long.MAX_VALUE))
        assertFalse(reducer.pose(now + 1).gazeObserved)
        reducer.apply(AvatarEvent.GazeObserved(GazeTarget.OWNER, 80, now - 1))
        assertFalse(reducer.pose(now + 1).gazeObserved)
        reducer.apply(AvatarEvent.GazeObserved(gaze, 80, now + 2))
        assertTrue(reducer.pose(now + 2).gazeObserved)
        reducer.apply(AvatarEvent.GazeObserved(GazeTarget.OWNER, 80, Long.MAX_VALUE))
        reducer.clearObservedGaze(now + 3)
        reducer.apply(AvatarEvent.GazeObserved(gaze, 80, now + 4))
        assertTrue(reducer.pose(now + 4).gazeObserved)
    }
}
