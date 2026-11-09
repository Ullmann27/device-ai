package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisWindowOrbPulseTest {
    @Test fun oneAcceptedWindowProducesBoundedTransientEnergyOnly() {
        val p = JarvisWindowOrbPulse
        assertEquals(0f, p.energy(-1), 0f)
        assertEquals(0f, p.energy(0), 0f)
        assertTrue(p.energy(325L) in 0.70f..0.72f)
        assertTrue(p.energy(525L) in 0f..0.72f)
        assertEquals(0f, p.energy(p.DURATION_MS), 0f)
        assertEquals(0f, p.energy(Long.MAX_VALUE), 0f)
    }

    @Test fun energyIsPureForRepeatedFramesAndSafeForSkipAhead() {
        val p = JarvisWindowOrbPulse
        val sample = p.energy(190L)
        assertEquals(sample, p.energy(190L), 0f)
        assertTrue(sample in 0f..0.72f)
        assertEquals(0f, p.energy(8_000L), 0f)
    }
}
