package at.heinz.deviceai.avatar3d

import org.junit.Assert.*
import org.junit.Test

class CockpitStudioEnvironmentTest {
    private fun rgb(x: Float, y: Float, z: Float) = FloatArray(3).also { CockpitStudioEnvironment.radiance(x, y, z, it) }

    @Test fun theRoomIsDarkAndTheSoftBoxesAreTheOnlyBrightAreas() {
        val faces = CockpitStudioEnvironment.faces()
        assertEquals(6 * 64 * 64 * 3, faces.size)
        assertTrue(faces.all { it.isFinite() && it >= 0f && it <= CockpitStudioEnvironment.PEAK * 1.2f })
        val mean = faces.average()
        assertTrue("mean $mean", mean in .02..(.30))            // mostly dark: metals stay dark with tinted highlights
        assertTrue(faces.max() > 1f)                           // but there is something to reflect
        val floor = rgb(0f, -1f, 0f)
        assertTrue(floor.max() < .06f)
    }

    @Test fun cyanSitsFrontLeftVioletRightWarmKeyAboveAndARimBehindJarvis() {
        val cyan = rgb(-.80f, .25f, .55f); assertTrue(cyan[2] > cyan[0] * 4f && cyan[1] > cyan[0] * 3f)
        val violet = rgb(.85f, .20f, .30f); assertTrue(violet[2] > violet[1] * 3f && violet[0] > violet[1] * 2f)
        val key = rgb(.20f, .75f, .65f); assertTrue(key[0] > key[2])
        val rim = rgb(0f, .60f, -.80f); assertTrue(rim[2] > .5f && rim[2] > rim[0] * 2f)
        // Straight at the viewer and straight behind the monitor stay dark: no light source inside the app pixels' mirror.
        assertTrue(rgb(0f, 0f, 1f).max() < .08f)
    }

    @Test fun facesFollowTheCubemapConventionAndAreDeterministic() {
        val size = 16
        val a = CockpitStudioEnvironment.faces(size)
        assertArrayEquals(a, CockpitStudioEnvironment.faces(size), 0f)
        fun centre(face: Int): FloatArray {
            val at = (face * size * size + (size / 2) * size + size / 2) * 3
            return floatArrayOf(a[at], a[at + 1], a[at + 2])
        }
        // +Y centre is "up" (blue gradient, brighter than the floor at -Y centre).
        assertTrue(centre(2)[2] > centre(3)[2])
        assertEquals(7, CockpitStudioEnvironment.levels(64))
        assertEquals(5, CockpitStudioEnvironment.levels(16))
    }

    @Test fun monitorGlowRisesSmoothlyFallsAndOnlyUploadsOnChange() {
        val glow = CockpitMonitorGlow()
        var now = 0L
        assertEquals(0f, glow.step(false, now)!!, 0f)          // first upload: dark
        now += 16_000_000; assertNull(glow.step(false, now))   // idle: nothing to upload
        val seen = ArrayList<Float>()
        repeat(60) { now += 16_000_000; glow.step(true, now)?.let(seen::add) }
        assertTrue(seen.size > 8)                              // a ramp, not a jump
        assertTrue(seen.zipWithNext().all { (p, n) -> n > p })
        assertEquals(CockpitMonitorGlow.MAX_LUMENS, seen.last(), 0f)
        repeat(10) { now += 16_000_000; assertNull(glow.step(true, now)) }
        val down = ArrayList<Float>()
        repeat(90) { now += 16_000_000; glow.step(false, now)?.let(down::add) }
        assertEquals(0f, down.last(), 0f)
        assertTrue(down.zipWithNext().all { (p, n) -> n < p })
        glow.reset(); assertEquals(0f, glow.step(false, now)!!, 0f)
    }
}
