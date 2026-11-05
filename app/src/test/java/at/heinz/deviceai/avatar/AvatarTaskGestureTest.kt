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
}
