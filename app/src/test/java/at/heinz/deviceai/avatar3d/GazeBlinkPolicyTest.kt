package at.heinz.deviceai.avatar3d

import org.junit.Assert.*
import org.junit.Test

class GazeBlinkPolicyTest {
    @Test fun directedEyeJumpProducesOneBriefBlinkThenRespectsCooldown() {
        val p = GazeBlinkPolicy()
        assertEquals(0f, p.sample(-.6f, 0f, 1f, true), 0f)
        assertEquals(0f, p.sample(+.6f, 0f, 1.10f, true), 0f)
        assertTrue(p.sample(+.6f, 0f, 1.155f, true) > .9f)
        assertEquals(0f, p.sample(+.6f, 0f, 1.211f, true), 0f)
        assertEquals(0f, p.sample(-.6f, 0f, 1.5f, true), 0f)
        assertEquals(0f, p.sample(+.6f, 0f, 1.7f, true), 0f)
        assertEquals(0f, p.sample(-.6f, 0f, 3.3f, true), 0f)
        assertTrue(p.sample(-.6f, 0f, 3.355f, true) > .9f)
    }

    @Test fun noMotionOrInvalidGazeCannotTriggerSyntheticBlink() {
        val p = GazeBlinkPolicy()
        assertEquals(0f, p.sample(0f, 0f, 1f, false), 0f)
        assertEquals(0f, p.sample(.9f, .9f, 1.1f, false), 0f)
        assertEquals(0f, p.sample(.9f, Float.NaN, 1.2f, true), 0f)
        assertEquals(0f, p.sample(Float.POSITIVE_INFINITY, 0f, 1.3f, true), 0f)
        assertEquals(0f, p.sample(0f, 0f, Float.NaN, true), 0f)
    }

    @Test fun animationClockWrapAdmitsFreshGazeBlinks() {
        val p = GazeBlinkPolicy()
        p.sample(-.6f, 0f, 459f, true)
        p.sample(.6f, 0f, 459.1f, true)
        assertTrue(p.sample(.6f, 0f, 459.155f, true) > .9f)
        assertEquals(0f, p.sample(.6f, 0f, .02f, true), 0f)
        assertEquals(0f, p.sample(-.6f, 0f, .1f, true), 0f)
        assertTrue(p.sample(-.6f, 0f, .155f, true) > .9f)
    }

    @Test fun clockRollbackCannotReplayAnExpiredBlink() {
        val p = GazeBlinkPolicy()
        p.sample(-.6f, 0f, 1f, true)
        p.sample(.6f, 0f, 1.1f, true)
        assertTrue(p.sample(.6f, 0f, 1.155f, true) > .9f)
        assertEquals(0f, p.sample(.6f, 0f, 2f, true), 0f)
        assertEquals(0f, p.sample(-.6f, 0f, .2f, true), 0f)
        assertEquals(0f, p.sample(-.6f, 0f, 1.155f, true), 0f)
    }

    @Test fun disablingWithinTheSameEpochPreservesCooldown() {
        val p = GazeBlinkPolicy()
        p.sample(-.6f, 0f, 1f, true)
        p.sample(.6f, 0f, 1.1f, true)
        assertEquals(0f, p.sample(.6f, 0f, 1.2f, false), 0f)
        p.sample(.6f, 0f, 1.3f, true)
        p.sample(-.6f, 0f, 1.4f, true)
        assertEquals(0f, p.sample(-.6f, 0f, 1.455f, true), 0f)
        p.sample(.6f, 0f, 3f, true)
        assertTrue(p.sample(.6f, 0f, 3.055f, true) > .9f)
    }

    @Test fun wrapDuringDisabledFramesDoesNotRetainTheOldCooldown() {
        val p = GazeBlinkPolicy()
        p.sample(-.6f, 0f, 459f, true)
        p.sample(.6f, 0f, 459.1f, true)
        p.sample(.6f, 0f, 459.2f, false)
        p.sample(.6f, 0f, .02f, false)
        assertEquals(0f, p.sample(.6f, 0f, .1f, true), 0f)
        p.sample(-.6f, 0f, .2f, true)
        assertTrue(p.sample(-.6f, 0f, .255f, true) > .9f)
    }

    @Test fun invalidSamplesCannotConcealAClockRollback() {
        for ((x, time) in listOf(Float.NaN to 459.2f, .6f to Float.NaN,
            Float.POSITIVE_INFINITY to 459.2f, .6f to Float.POSITIVE_INFINITY)) {
            val p = GazeBlinkPolicy()
            p.sample(-.6f, 0f, 459f, true)
            p.sample(.6f, 0f, 459.1f, true)
            assertEquals(0f, p.sample(x, 0f, time, true), 0f)
            assertEquals(0f, p.sample(.6f, 0f, .1f, true), 0f)
            p.sample(-.6f, 0f, .2f, true)
            assertTrue(p.sample(-.6f, 0f, .255f, true) > .9f)
        }
    }
}
