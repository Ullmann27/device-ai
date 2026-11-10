package at.heinz.deviceai.avatar

import at.heinz.deviceai.avatar3d.CinemaWindowLightState
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.WindowProjectedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaWindowGazeBridgeTest {
    private var now = 1_000L

    private fun reducerDirector(): Pair<AvatarPoseReducer, CinemaWindowGazeBridge> {
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }
        return reducer to CinemaWindowGazeBridge(director)
    }

    private fun motion(
        x: Float,
        y: Float = -.2f,
        phase: WindowProjectedMotion.Phase = WindowProjectedMotion.Phase.FLYING,
        windowId: String = "work-document",
        turnId: String = "turn-1",
        accent: Int? = 0xFFAA40FF.toInt(),
    ) = WindowProjectedMotion(turnId, now, windowId, x, y, accent, phase)

    @Test fun gazeFollowsEachProjectedSampleInsteadOfTheEndpoint() {
        CinemaWindowLightState.reset()
        val (reducer, bridge) = reducerDirector()
        val samples = listOf(-.6f, -.3f, .0f, .35f, .55f)
        for (x in samples) {
            bridge.render(motion(x))
            now += 80L
            val pose = reducer.pose(now)
            assertTrue(pose.gazeDirected)
            assertEquals(x, pose.gazeX, .0001f)
        }
        assertEquals(.55f, reducer.pose(now).gazeX, .0001f)
        CinemaWindowLightState.reset()
    }

    @Test fun closedSampleAndTurnEndReleaseGazeAndRestoreLight() {
        CinemaWindowLightState.reset()
        val (reducer, bridge) = reducerDirector()
        bridge.render(motion(.4f, accent = 0xFFAA40FF.toInt()))
        assertTrue(CinemaWindowLightState.open)
        assertEquals(.4f, CinemaWindowLightState.gazeX, 0f)
        assertEquals(0xFFAA40FF.toInt(), CinemaWindowLightState.accentArgb)
        now += 100L
        bridge.render(motion(.4f, phase = WindowProjectedMotion.Phase.CLOSED))
        assertFalse(reducer.pose(now).gazeDirected)
        assertFalse(CinemaWindowLightState.open)
        assertNull(CinemaWindowLightState.accentArgb)

        bridge.render(motion(-.5f, turnId = "turn-2", windowId = "work-process"))
        now += 50L
        bridge.render(CockpitAnimationEvent.TurnEnded("turn-2", now, delivered = true))
        assertFalse(reducer.pose(now).gazeDirected)
        assertFalse(CinemaWindowLightState.open)
        CinemaWindowLightState.reset()
    }

    @Test fun speechDoesNotRetireTheWindowIdWhenASampleArrives() {
        CinemaWindowLightState.reset()
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }
        val bridge = CinemaWindowGazeBridge(director)
        assertTrue(director.speechStarted("s1", "Ich lese das Dokument."))
        bridge.render(motion(.5f))
        assertFalse("opening during speech would retire the window", reducer.pose(now).gazeDirected)
        director.speechEnded("s1", interrupted = false)
        now += 20L
        bridge.render(motion(.5f))
        assertTrue("the same window can still open after speech", reducer.pose(now).gazeDirected)
        assertEquals(.5f, reducer.pose(now).gazeX, .0001f)
        CinemaWindowLightState.reset()
    }

    @Test fun nextTurnCanReuseTheSameWorkWindow() {
        CinemaWindowLightState.reset()
        val (reducer, bridge) = reducerDirector()
        bridge.render(motion(.4f))
        now += 100L
        bridge.render(CockpitAnimationEvent.TurnEnded("turn-1", now, true))
        now += 100L
        bridge.render(motion(-.4f, turnId = "turn-2"))
        assertTrue(reducer.pose(now).gazeDirected)
        assertEquals(-.4f, reducer.pose(now).gazeX, .0001f)
        bridge.render(motion(.4f, phase = WindowProjectedMotion.Phase.CLOSED))
        assertTrue(CinemaWindowLightState.open)
        assertEquals(-.4f, CinemaWindowLightState.gazeX, 0f)
    }

    @Test fun closingTheLitWindowRestoresAnotherOpenWindow() {
        CinemaWindowLightState.reset()
        val (_, bridge) = reducerDirector()
        bridge.render(motion(-.3f, y = .2f))
        now += 100L
        bridge.render(motion(.6f, y = -.4f, windowId = "work-process"))
        now += 100L
        bridge.render(motion(.6f, phase = WindowProjectedMotion.Phase.CLOSED, windowId = "work-process"))
        assertTrue(CinemaWindowLightState.open)
        assertEquals(-.3f, CinemaWindowLightState.gazeX, 0f)
        assertEquals(.2f, CinemaWindowLightState.gazeY, 0f)
    }

    @Test fun speakingRetainsWindowLightingAndTerminalEventsStillCloseIt() {
        CinemaWindowLightState.reset()
        val director = AvatarDirector(clock = { now })
        val bridge = CinemaWindowGazeBridge(director)
        director.speechStarted("speech", "Synthetischer Testsatz.")
        bridge.render(motion(.3f, y = -.7f))
        assertEquals(-.7f, CinemaWindowLightState.gazeY, 0f)
        now += 100L
        bridge.render(CockpitAnimationEvent.TurnEnded("turn-1", now, false))
        now += 100L
        bridge.render(motion(.9f))
        assertFalse(CinemaWindowLightState.open)
    }

    @Test fun delayedSamplesCannotMoveANewerWindow() {
        CinemaWindowLightState.reset()
        val (_, bridge) = reducerDirector()
        now = 2_000L
        bridge.render(motion(.6f, turnId = "turn-2"))
        now = 1_000L
        bridge.render(motion(-.6f))
        assertEquals(.6f, CinemaWindowLightState.gazeX, 0f)
        bridge.render(motion(Float.NaN))
        assertEquals(.6f, CinemaWindowLightState.gazeX, 0f)
    }

    @Test fun projectedTurnChangePreservesPhysicalDocumentReadingAndLight() {
        CinemaWindowLightState.reset()
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }
        val bridge = CinemaWindowGazeBridge(director)
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        bridge.render(motion(.2f, turnId = "document"))
        assertTrue(director.windowWorkStarted("work-document", GazeTarget(.2f, -.2f)))
        now += 80L
        assertTrue(director.windowMoved("work-document", GazeTarget(.55f, -.3f)))
        bridge.render(motion(.55f, -.3f, turnId = "local-draft"))
        assertTrue("Projection cannot replace real reading", reducer.pose(now).reading)
        assertEquals(.55f, reducer.pose(now).gazeX, .0001f)
        assertEquals(-.3f, reducer.pose(now).gazeY, .0001f)
        assertTrue("Projection still illuminates the face", CinemaWindowLightState.open)
        assertEquals(.55f, CinemaWindowLightState.gazeX, 0f)
        assertEquals(-.3f, CinemaWindowLightState.gazeY, 0f)

        now += 80L
        bridge.render(motion(.2f, phase = WindowProjectedMotion.Phase.CLOSED, turnId = "document"))
        bridge.render(CockpitAnimationEvent.TurnEnded("document", now, true))
        assertTrue("Old turn cannot end reading", reducer.pose(now).reading)
        assertTrue("Old turn cannot extinguish current light", CinemaWindowLightState.open)

        assertTrue(director.windowWorkEnded("work-document"))
        assertFalse(reducer.pose(now).reading)
        now += 80L
        bridge.render(motion(-.3f, turnId = "local-draft"))
        assertTrue("Deferred glance remains admissible", reducer.pose(now).gazeDirected)
        assertEquals(-.3f, reducer.pose(now).gazeX, .0001f)
        assertFalse(reducer.pose(now).reading)
        CinemaWindowLightState.reset()
    }

    @Test fun lostWorkEndCannotBlockNewGlancesAfterItsBound() {
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        assertTrue(director.windowWorkStarted("document", GazeTarget(.4f, -.2f)))
        assertFalse("Real work outranks a brief glance", director.windowOpened("projection", GazeTarget(-.4f, .2f)))
        now += AvatarPoseReducer.MAX_WINDOW_WORK_MS
        assertTrue("Existing expiry releases priority", director.windowOpened("projection", GazeTarget(-.4f, .2f)))
        assertFalse(reducer.pose(now).reading)
        assertEquals(-.4f, reducer.pose(now).gazeX, .0001f)
    }

    @Test fun concurrentUiAndWorkerMotionCannotCorruptTheOpenWindowRegistry() {
        CinemaWindowLightState.reset()
        val (_, bridge) = reducerDirector()
        val workers = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val jobs = (0 until 4).map { worker ->
                workers.submit {
                    repeat(150) { sample ->
                        val id = "work-${worker}"
                        bridge.render(motion(.45f, windowId = id))
                        if (sample % 5 == 0) {
                            bridge.render(motion(.45f, phase = WindowProjectedMotion.Phase.CLOSED, windowId = id))
                        }
                        if (sample % 13 == 0) {
                            bridge.render(CockpitAnimationEvent.TurnEnded("turn-1", now, delivered = true))
                        }
                    }
                }
            }
            jobs.forEach { it.get(10, java.util.concurrent.TimeUnit.SECONDS) }
            bridge.render(CockpitAnimationEvent.TurnEnded("turn-1", now + 1L, delivered = true))
            assertFalse("Every concurrent projected window must be closed", CinemaWindowLightState.open)
            assertNull(CinemaWindowLightState.accentArgb)
        } finally {
            workers.shutdownNow()
            CinemaWindowLightState.reset()
        }
    }
}
