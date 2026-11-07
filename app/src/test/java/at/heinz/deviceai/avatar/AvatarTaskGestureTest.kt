package at.heinz.deviceai.avatar

import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Real Director→Reducer→Mapper path, with scripted event times. */
class AvatarTaskGestureTest {
    private var now = 1_000L
    private val reducer = AvatarPoseReducer()
    private val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }

    @Test fun windowGazeExpiresAndNeverSpoofsCameraObservation() {
        director.onWindowPresented()
        val opening = reducer.pose(now)
        assertEquals(AvatarGestureIntent.WINDOW_FOCUS, opening.gesture)
        assertFalse(opening.gazeObserved)
        assertTrue(opening.gazeX > .3f)
        now += 1_101L
        val expired = reducer.pose(now)
        assertNull(expired.gesture)
        assertEquals(0f, expired.gazeX, 0f)
        assertEquals(0f, expired.gestureProgress, 0f)
    }

    @Test fun realCameraObservationsStillWinOverWindowGaze() {
        director.onRuntime(AvatarRuntimeSignals(listening = true))
        director.onObservedGaze(GazeTarget(-.8f, .2f), 95, 0)
        director.onWindowPresented()
        val pose = reducer.pose(now)
        assertTrue(pose.gazeObserved)
        assertEquals(-.8f, pose.gazeX, 0f)
        assertEquals(.2f, pose.gazeY, 0f)
    }

    @Test fun confirmationNodHasFiniteMotionWithoutSpeechOrFalseSuccess() {
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        assertEquals(AvatarGestureIntent.THINK_MICRO, reducer.pose(now).gesture)
        director.onActionConfirmed()
        now += 220L
        val nod = reducer.pose(now)
        assertEquals(AvatarGestureIntent.CONFIRM_NOD, nod.gesture)
        assertEquals(0f, nod.jawOpen, 0f)
        val active = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val neutral = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        active.update(nod, now / 1000f, true)
        neutral.update(nod.copy(gesture = null), now / 1000f, true)
        assertTrue(abs(active.headPitch - neutral.headPitch) > 2f)
        assertEquals(0f, active.jawPitch, 0f)
        now += 570L
        assertNull(reducer.pose(now).gesture)
    }

    @Test fun interruptedSpeechClearsGestureAndRejectsOldCue() {
        director.speechStarted("utterance", "Hallo")
        director.onWindowPresented()
        val old = now
        director.speechEnded("utterance", interrupted = true)
        assertNull(reducer.pose(now).gesture)
        reducer.apply(AvatarEvent.Gesture(AvatarGestureIntent.CONFIRM_NOD, GazeTarget.OWNER, old))
        assertNull(reducer.pose(now).gesture)
        assertEquals(0f, reducer.pose(now).jawOpen, 0f)
    }

    @Test fun gestureHonoursReducedMotionAndIdleJaw() {
        director.onActionConfirmed()
        now += 190
        val pose = reducer.pose(now)
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        mapper.update(pose, now / 1000f, false)
        assertEquals(0f, mapper.headPitch, 0f)
        assertEquals(0f, mapper.jawPitch, 0f)
        assertTrue(mapper.weights.all { it == 0f })
    }
    @Test fun thinkingGestureIsNotShownAfterSpeechStateBegins() {
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        assertEquals(AvatarGestureIntent.THINK_MICRO, reducer.pose(now).gesture)
        now += 80
        director.onRuntime(AvatarRuntimeSignals(speaking = true))
        assertNull("Think motion must stop as soon as speaking begins", reducer.pose(now).gesture)
        now += 50
        assertNull("Former thought cue cannot reappear", reducer.pose(now).gesture)
    }

    @Test fun longWorkKeepsSubtleBoundedMotionWithoutTalking() {
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        now += 3_000L
        val pose = reducer.pose(now)
        assertNull("Short thinking intent has expired", pose.gesture)
        assertEquals(0f, pose.jawOpen, 0f)
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        mapper.update(pose, 3f, true)
        val firstYaw = mapper.headYaw
        val firstPitch = mapper.headPitch
        mapper.update(pose, 4.2f, true)
        val change = abs(mapper.headYaw - firstYaw) + abs(mapper.headPitch - firstPitch)
        assertTrue("Long work needs calm, non-frozen movement: $change", change > .03f)
        assertTrue("Long work must not have a head snap: $change", change < 1.5f)
        assertEquals(0f, mapper.jawPitch, 0f)
        val fixed = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        fixed.update(pose, 4.2f, false)
        assertTrue("Reduced motion suppresses ambient movement",
            abs(mapper.headYaw - fixed.headYaw) + abs(mapper.headPitch - fixed.headPitch) > .02f)
    }

}
