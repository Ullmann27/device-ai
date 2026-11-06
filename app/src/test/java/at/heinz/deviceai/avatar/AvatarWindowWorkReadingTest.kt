package at.heinz.deviceai.avatar

import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner brief 06.11.2026: while the local model really processes the document in the work window, the avatar looks at
 * the document and reads it – the eyes on the window, the head following – instead of a 2.5 s glance followed by the
 * generic "thinking away" look. The gaze is held only by the real work and ends with it (result, failure, STOP, speech).
 */
class AvatarWindowWorkReadingTest {
    private var now = 10_000L
    private val reducer = AvatarPoseReducer()
    private val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }
    private val window = GazeTarget(.5f, -.2f)

    private fun busy() = director.onRuntime(AvatarRuntimeSignals(busy = true))

    @Test fun theOpeningGlanceAloneEndsAfterItsTtlAndLeavesTheThinkingLook() {
        assertTrue(director.windowOpened("doc-1", window))
        busy()
        now += AvatarPoseReducer.WINDOW_GAZE_TTL_MS + 100
        val pose = reducer.pose(now)
        assertFalse("without real work the window glance is over", pose.gazeDirected)
        assertFalse(pose.reading)
    }

    @Test fun realWorkHoldsTheGazeOnTheWindowAndReadsUntilItEnds() {
        assertTrue(director.windowOpened("doc-1", window))
        now += AvatarPoseReducer.WINDOW_GAZE_TTL_MS + 100
        busy()
        assertTrue("work starts after the glance expired – a new real event", director.windowWorkStarted("doc-1", window))
        now += 45_000
        val working = reducer.pose(now)
        assertTrue("45 s into the work the eyes are still on the document", working.gazeDirected)
        assertTrue(working.reading)
        assertEquals(window.x, working.gazeX, 1e-6f)
        assertEquals(window.y, working.gazeY, 1e-6f)
        assertEquals(AvatarState.THINKING, working.state)
        assertEquals("thinking keeps its focused face", AvatarExpression.FOCUSED, working.expression)
        assertTrue(director.windowWorkEnded("doc-1"))
        val done = reducer.pose(now)
        assertFalse("the end of the work releases the gaze at once", done.gazeDirected)
        assertFalse(done.reading)
        assertFalse("ending twice changes nothing", director.windowWorkEnded("doc-1"))
    }

    @Test fun stopAndSpeechEndTheReadingAndAMovedWindowKeepsIt() {
        busy()
        assertTrue(director.windowWorkStarted("doc-1", window))
        assertTrue(director.windowMoved("doc-1", GazeTarget(-.3f, .1f)))
        val moved = reducer.pose(now)
        assertTrue("a refitted window keeps being read", moved.reading)
        assertEquals(-.3f, moved.gazeX, 1e-6f)
        director.clearAnimationIntents()
        assertFalse("STOP ends the reading", reducer.pose(now).reading)

        assertTrue(director.windowWorkStarted("doc-2", window))
        director.onRuntime(AvatarRuntimeSignals(speaking = true))
        assertFalse("speaking turns to the owner", reducer.pose(now).reading)
        assertFalse("no work gaze can start while speaking", director.windowWorkStarted("doc-3", window))
    }

    @Test fun theEyesSweepTheLinesWhileTheHeadStaysOnTheWindow() {
        busy()
        assertTrue(director.windowWorkStarted("doc-1", window))
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val eyes = mutableListOf<Float>()
        val heads = mutableListOf<Float>()
        var t = 20f
        while (t < 20f + 2 * AvatarMorphMapper.READ_LINE_S) {
            now = (t * 1000).toLong()
            mapper.update(reducer.pose(now), t, true)
            if (t > 21f) { eyes += mapper.eyeYaw; heads += mapper.headYaw }
            t += 1f / 30f
        }
        val eyeRange = eyes.max() - eyes.min()
        val headRange = heads.max() - heads.min()
        assertTrue("the eyes sweep a line (range $eyeRange°)", eyeRange > 1.5f * AvatarMorphMapper.READ_SWEEP_DEGREES)
        assertTrue("the head stays on the window (range $headRange°)", headRange < .3f * eyeRange)

        director.windowWorkEnded("doc-1")
        val calm = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        val held = mutableListOf<Float>()
        now = 40_000L
        assertTrue(director.windowOpened("doc-2", window))
        t = 40f
        while (t < 40f + AvatarMorphMapper.READ_LINE_S) {
            now = (t * 1000).toLong()
            calm.update(reducer.pose(now), t, true)
            if (t > 40.6f && reducer.pose(now).gazeDirected) held += calm.eyeYaw
            t += 1f / 30f
        }
        assertTrue("a plain glance does not read", held.isNotEmpty() && held.max() - held.min() < .5f)
    }

    @Test fun reducedMotionNeverSweeps() {
        busy()
        assertTrue(director.windowWorkStarted("doc-1", window))
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        mapper.update(reducer.pose(now), 20.2f, false)
        val first = mapper.eyeYaw
        mapper.update(reducer.pose(now), 21.0f, false)
        assertEquals(first, mapper.eyeYaw, 1e-6f)
    }

    @Test fun theReadingSweepIsSlowRightAndQuickBack() {
        val a = AvatarMorphMapper.READ_SWEEP_DEGREES
        assertEquals(-a, AvatarMorphMapper.readingYaw(0f), 1e-4f)
        val turn = AvatarMorphMapper.READ_LINE_S * (1f - AvatarMorphMapper.READ_RETURN_SHARE)
        assertEquals(a, AvatarMorphMapper.readingYaw(turn - 1e-4f), 1e-2f)
        assertTrue(AvatarMorphMapper.readingYaw(turn * .5f) in -.2f..2f)
        assertEquals(-a, AvatarMorphMapper.readingYaw(AvatarMorphMapper.READ_LINE_S - 1e-4f), 2e-2f)
    }

    /**
     * Owner brief A (06.11.): an interruption during real work. The owner speaks while JARVIS reads the document: he
     * turns to the owner (listening, eyes on the owner); when the owner is done and the model still works, he goes back
     * to reading – not to an idle stare – until the work really ends. STOP during the interruption ends it for good.
     */
    @Test fun afterAnInterruptionJarvisGoesBackToReadingWhileTheWorkStillRuns() {
        busy()
        assertTrue(director.windowWorkStarted("doc-1", window))
        now += 3_000
        director.onRuntime(AvatarRuntimeSignals(listening = true, busy = true))
        val listening = reducer.pose(now)
        assertEquals(AvatarState.LISTENING, listening.state)
        assertFalse("while the owner speaks JARVIS looks at the owner", listening.gazeDirected)
        now += 2_000
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        val back = reducer.pose(now)
        assertEquals(AvatarState.THINKING, back.state)
        assertTrue("the work still runs: back to reading", back.reading)
        assertEquals(window.x, back.gazeX, 1e-6f)
        assertTrue(director.windowWorkEnded("doc-1"))
        assertFalse(reducer.pose(now).reading)

        assertTrue(director.windowWorkStarted("doc-2", window))
        director.onRuntime(AvatarRuntimeSignals(listening = true, busy = true))
        director.clearAnimationIntents()
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        assertFalse("STOP during the interruption ends the reading for good", reducer.pose(now).reading)
        director.onRuntime(AvatarRuntimeSignals())
        assertTrue(director.windowWorkStarted("doc-3", window))
        director.onRuntime(AvatarRuntimeSignals(speaking = true))
        assertTrue("ending the work during speech is accepted", director.windowWorkEnded("doc-3"))
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        assertFalse("ended work never comes back", reducer.pose(now).reading)
    }
}
