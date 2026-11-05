package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** Pure source-bound geometry gate; Android painter/Fold7 measurements remain separate. */
class JarvisTileEnergyTest {
    private fun innerRimError(s: JarvisTileEnergy.Sample, width: Float, height: Float): Float {
        // Outer tile radius 8 dp, inset 3 dp => inner quarter-circle radius 5 dp.
        val dx = when { s.x < 8f -> 8f - s.x; s.x > width - 8f -> s.x - (width - 8f); else -> 0f }
        val dy = when { s.y < 8f -> 8f - s.y; s.y > height - 8f -> s.y - (height - 8f); else -> 0f }
        return if (dx > 0f && dy > 0f) abs(hypot(dx, dy) - 5f)
            else minOf(abs(s.x - 3f), abs(s.x - (width - 3f)),
                abs(s.y - 3f), abs(s.y - (height - 3f)))
    }

    @Test fun followsRealRoundedRimAtAllFourCorners() {
        for ((w, h) in listOf(166f to 60f, 48f to 48f, 128f to 80f)) {
            for (time in 0L until JarvisTileEnergy.CYCLE_MS step 7) {
                val s = JarvisTileEnergy.sample(time, w, h, "chat")!!
                assertTrue("x=$w/$h/$time: ${s.x}", s.x in 3f..(w - 3f))
                assertTrue("y=$w/$h/$time: ${s.y}", s.y in 3f..(h - 3f))
                assertTrue("not on rounded rim, t=$time err=${innerRimError(s, w, h)}",
                    innerRimError(s, w, h) <= .002f)
            }
        }
    }

    @Test fun travelsContinuouslyAcrossCornersAndCycleBoundary() {
        val w = 166f; val h = 60f; val radius = 5f
        val perimeter = 2f * ((w - 6f - 2f * radius) + (h - 6f - 2f * radius)) +
            2f * Math.PI.toFloat() * radius
        val oneMsStep = perimeter / JarvisTileEnergy.CYCLE_MS
        for (time in 0L until JarvisTileEnergy.CYCLE_MS) {
            val a = JarvisTileEnergy.sample(time, w, h, "memory")!!
            val b = JarvisTileEnergy.sample(time + 1L, w, h, "memory")!!
            val actual = hypot(b.x - a.x, b.y - a.y)
            assertTrue("teleport/speed jump at $time: $actual", actual <= oneMsStep + .002f)
            assertTrue("not moving smoothly at $time: $actual", actual >= oneMsStep - .002f)
        }
    }

    @Test fun minSizeRadiusAndExtremeInputsRemainSafe() {
        for (t in listOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            val s = JarvisTileEnergy.sample(t, 48f, 48f, "device")!!
            assertTrue(s.x.isFinite() && s.y.isFinite() && s.phase in 0f..<1f)
        }
        assertNull(JarvisTileEnergy.sample(0L, 47f, 60f, "chat"))
        assertNull(JarvisTileEnergy.sample(0L, 70f, 47f, "chat"))
        assertNull(JarvisTileEnergy.sample(0L, Float.NaN, 70f, "chat"))
        assertNull(JarvisTileEnergy.sample(0L, 70f, Float.POSITIVE_INFINITY, "chat"))
        assertNull(JarvisTileEnergy.sample(0L, Float.MAX_VALUE, 70f, "chat"))
        assertNull(JarvisTileEnergy.sample(0L, 48f, 48f, "chat", Float.NaN))
        assertNull(JarvisTileEnergy.sample(0L, 48f, 48f, "chat", -1f))
        assertTrue(JarvisTileEnergy.sample(0L, 60f, 60f, "chat", 0f) != null)
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
