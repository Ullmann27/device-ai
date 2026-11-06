package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.*
import org.junit.Assert.*
import org.junit.Test

class ToolWorkSceneLeaseTest {
    private val key = ToolWorkSceneLease.Key("session", "scope", 1L, "call-1")

    @Test fun stopBeforePostedStartCannotReopenWindow() {
        val scene = ToolWorkSceneLease()
        assertFalse(scene.offer(key, "session", null, 2, true, 100))
        assertNull(scene.active)
    }

    @Test fun oldSessionGenerationAndBackgroundAreRejected() {
        val scene = ToolWorkSceneLease()
        assertFalse(scene.offer(key, "other", "scope", 1, true, 100))
        assertFalse(scene.offer(key, "session", "new-scope", 1, true, 100))
        assertFalse(scene.offer(key, "session", "scope", 2, true, 100))
        assertFalse(scene.offer(key, "session", "scope", 1, false, 100))
    }

    @Test fun delayedOldCallCannotReplaceOrFinishCurrentCall() {
        val scene = ToolWorkSceneLease()
        assertTrue(scene.offer(key, "session", "scope", 1, true, 100))
        val next = key.copy(callId = "call-2")
        assertTrue(scene.offer(next, "session", "scope", 1, true, 200))
        assertFalse(scene.ended(key))
        assertFalse(scene.offer(key, "session", "scope", 1, true, 100))
        assertEquals(next, scene.active)
        assertTrue(scene.running)
    }

    @Test fun completionAndDismissalNeverReopenSameCall() {
        val scene = ToolWorkSceneLease()
        assertTrue(scene.offer(key, "session", "scope", 1, true, 100))
        assertTrue(scene.ended(key))
        assertFalse(scene.running)
        scene.closed(key)
        assertFalse(scene.offer(key, "session", "scope", 1, true, 300))
    }

    @Test fun closingThenStartingInSameMillisecondRetainsRetiredCalls() {
        val scene = ToolWorkSceneLease()
        assertTrue(scene.offer(key, "session", "scope", 1, true, 100))
        scene.ended(key)
        scene.closed(key)
        val next = key.copy(callId = "current")
        assertTrue(scene.offer(next, "session", "scope", 1, true, 100))
        assertFalse(scene.offer(key, "session", "scope", 1, true, 100))
        assertEquals(next, scene.active)
    }

    @Test fun newScopeStartsAfterPriorScopeWasClosed() {
        val scene = ToolWorkSceneLease()
        scene.offer(key, "session", "scope", 1, true, 100)
        scene.clear()
        val next = key.copy(scope = "next", generation = 2)
        assertTrue(scene.offer(next, "session", "next", 2, true, 200))
        assertFalse(scene.current(key, "session", "next", 2, true))
    }

    @Test fun workingGazeIsStrongerOnlyWithinExistingLimits() {
        val work = ToolWorkSceneLease.workingGaze(GazeTarget(.3f, -.2f))
        assertEquals(.405f, work.x, 1e-6f)
        assertEquals(-.25f, work.y, 1e-6f)
        val limit = ToolWorkSceneLease.workingGaze(GazeTarget(-.6f, .4f))
        assertEquals(-AvatarPoseReducer.MAX_DIRECTED_GAZE_X, limit.x, 1e-6f)
        assertEquals(AvatarPoseReducer.MAX_DIRECTED_GAZE_Y, limit.y, 1e-6f)
        assertEquals(GazeTarget(0f, 0f), ToolWorkSceneLease.workingGaze(GazeTarget(0f, 0f)))
    }

    @Test fun existingDirectorHoldsWorkBeyondGlanceThenReturns() {
        var now = 10_000L
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink(reducer::apply)) { now }
        val target = ToolWorkSceneLease.workingGaze(GazeTarget(.3f, -.2f))
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        director.windowOpened("window", target)
        assertTrue(director.windowWorkStarted("window", target))
        now += 45_000L
        assertTrue(reducer.pose(now).reading)
        assertEquals(target.x, reducer.pose(now).gazeX, 1e-6f)
        director.windowWorkEnded("window")
        assertFalse(reducer.pose(now).reading)
        assertFalse(reducer.pose(now).gazeDirected)
    }
}
