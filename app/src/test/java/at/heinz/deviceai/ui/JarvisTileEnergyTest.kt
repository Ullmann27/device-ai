package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure source-bound geometry gate; native painter/Fold7 measurements are separate gates. */
class JarvisTileEnergyTest {
    @Test fun innerRimAndMinimumSizeRemainValid() {
        for (target in listOf("chat", "memory", "research", "agents", "system")) {
            for (time in listOf(0L, 1L, 1000L, 12999L, -1L, -13000L, Long.MIN_VALUE, Long.MAX_VALUE)) {
                val s = JarvisTileEnergy.sample(time, 166f, 60f, target)!!
                assertTrue("x $target/$time", s.x in 3f..163f)
                assertTrue("y $target/$time", s.y in 3f..57f)
                assertTrue(s.phase >= 0f && s.phase < 1f)
                assertTrue(s.x == 3f || s.x == 163f || s.y == 3f || s.y == 57f)
            }
        }
        assertNull(JarvisTileEnergy.sample(0L, 47f, 60f, "chat"))
        assertNull(JarvisTileEnergy.sample(0L, 70f, 47f, "chat"))
        assertNull(JarvisTileEnergy.sample(0L, Float.NaN, 70f, "chat"))
        assertNull(JarvisTileEnergy.sample(0L, 70f, Float.POSITIVE_INFINITY, "chat"))
    }

    @Test fun repeatsWithoutJumpAfterCompleteCycle() {
        for (target in listOf("chat", "web", "device")) {
            for (time in listOf(-13000L, -1L, 0L, 1L, 1000L, 12999L)) {
                val a = JarvisTileEnergy.sample(time, 166f, 60f, target)!!
                val b = JarvisTileEnergy.sample(time + JarvisTileEnergy.CYCLE_MS, 166f, 60f, target)!!
                assertEquals(a.x, b.x, .0005f)
                assertEquals(a.y, b.y, .0005f)
                assertEquals(a.phase, b.phase, .0005f)
            }
        }
    }

    @Test fun differentTilesAreStaggeredWithoutStateInference() {
        val chat = JarvisTileEnergy.sample(2_000L, 166f, 60f, "chat")
        val memory = JarvisTileEnergy.sample(2_000L, 166f, 60f, "memory")
        assertFalse("Destination-neutral animation must not synchronise all cards", chat == memory)
        assertEquals("Same input is deterministic; no simulated event changes",
            chat, JarvisTileEnergy.sample(2_000L, 166f, 60f, "chat"))
    }
}
