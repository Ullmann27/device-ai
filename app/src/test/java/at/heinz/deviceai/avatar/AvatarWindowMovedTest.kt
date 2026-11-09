package at.heinz.deviceai.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #565 review (Gitar): a work window replanned on the real size after a rotation, fold or keyboard resize moves – the
 * avatar's gaze has to follow it to its new place instead of looking where the window would have been on the old screen.
 */
class AvatarWindowMovedTest {
    private class Recorder : AvatarSink {
        val events = mutableListOf<AvatarEvent>()
        override fun onAvatarEvent(event: AvatarEvent) { events += event }
    }
    private var now = 1_000L

    private fun gazeOf(r: Recorder) = r.events.filterIsInstance<AvatarEvent.AnimationIntentsChanged>().last()

    @Test fun theGazeFollowsTheMovedWindowWithoutANewIntentOrALongerLife() {
        val r = Recorder()
        val d = AvatarDirector(r) { now }
        assertTrue(d.windowOpened("w-1", GazeTarget(.5f, -.2f)))
        val opened = gazeOf(r)
        now = 1_120L
        assertTrue("the open window moved", d.windowMoved("w-1", GazeTarget(-.3f, .1f)))
        val moved = gazeOf(r)
        assertEquals(GazeTarget(-.3f, .1f), moved.windowGaze)
        assertEquals("same intent: its start (and so its lifetime) is unchanged", opened.windowAtMillis, moved.windowAtMillis)
        assertTrue(moved.revision > opened.revision)
    }

    @Test fun onlyTheOpenWindowCanMoveTheGaze() {
        val r = Recorder()
        val d = AvatarDirector(r) { now }
        assertFalse("nothing open", d.windowMoved("w-1", GazeTarget(.2f, 0f)))
        assertTrue(d.windowOpened("w-1", GazeTarget(.5f, 0f)))
        assertFalse("another window", d.windowMoved("w-2", GazeTarget(.2f, 0f)))
        assertTrue(d.windowClosed("w-1"))
        assertFalse("a closed window cannot steer the gaze any more", d.windowMoved("w-1", GazeTarget(.2f, 0f)))
    }

    @Test fun flightDuringSpeechUpdatesTheResumedTargetButNeverTakesAttentionFromTheOwner() {
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        director.windowWorkStarted("w-1", GazeTarget(.1f, 0f))
        director.speechStarted("speech-1", "Die Datei ist offen.")
        for (x in listOf(.2f, .4f, .7f)) {
            now += 80L
            assertFalse(director.windowMoved("w-1", GazeTarget(x, -.2f)))
            assertFalse(reducer.pose(now).gazeDirected)
        }
        director.speechEnded("speech-1", interrupted = false)
        assertTrue(reducer.pose(now).reading)
        assertEquals(.7f, reducer.pose(now).gazeX, .0001f)

        director.clearAnimationIntents()
        assertFalse(director.windowMoved("w-1", GazeTarget(-.7f, 0f)))
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        assertFalse(reducer.pose(now).reading)
    }

    @Test fun continuousFlightDoesNotRefreshTheOpeningLease() {
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }
        director.windowOpened("w-1", GazeTarget(.1f, 0f))
        val start = now
        while (now - start < AvatarPoseReducer.WINDOW_GAZE_TTL_MS) {
            director.windowMoved("w-1", GazeTarget(.5f, -.2f))
            now += 16L
        }
        assertFalse(director.windowMoved("w-1", GazeTarget(.7f, -.2f)))
        assertFalse(reducer.pose(now).gazeDirected)
    }
}
