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
}
