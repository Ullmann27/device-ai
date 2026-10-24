package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ContinuumOrbitalRingTest {
    @Test fun readySphereLiftIsVisibleWithinTheFirstSecondAndRemainsGentle() {
        val phaseAfterOneSecond = .4f + .28f + .18f * .72f
        assertTrue("READY float should be visible without waiting many seconds", abs(ContinuumOrbitalRing.lift(phaseAfterOneSecond, 101f)) >= 2f)
        assertEquals(0f, ContinuumOrbitalRing.lift(.4f, 101f), 0f)
    }
    @Test fun deterministicPerspectiveHasTwoContinuousBoundedDepthHalves() {
        val a = ContinuumOrbitalRing(1.42f, .34f, -11f)
        val b = ContinuumOrbitalRing(1.42f, .34f, -11f)
        val front = a.front; val back = a.back
        for (phase in listOf(.4f, 1.2192f, 2.0384f, 3.6768f, 99f)) {
            a.project(phase, 1f, 206f, 117f, 101f)
            b.project(phase, 1f, 206f, 117f, 101f)
            assertSame(front, a.front); assertSame(back, a.back)
            assertTrue(a.frontSize > 0 && a.backSize > 0)
            assertTrue((a.frontSize + a.backSize) / 4 in 72..74)
            assertEquals(a.frontSize, b.frontSize); assertEquals(a.backSize, b.backSize)
            assertArrayEquals(a.front, b.front, 0f); assertArrayEquals(a.back, b.back, 0f)
            // Every endpoint has its continuation in one of the two batches, including depth-plane crossings.
            val segments = a.front.take(a.frontSize).chunked(4) + a.back.take(a.backSize).chunked(4)
            for (segment in segments) {
                assertTrue(segment.all { it.isFinite() })
                assertTrue(abs(segment[0] - 206f) < 180f && abs(segment[1] - 117f) < 180f)
                assertTrue(segments.any { abs(it[0] - segment[2]) < .001f && abs(it[1] - segment[3]) < .001f })
            }
        }
    }

    @Test fun readyHasIndependentSpatialMotionAndBoundedFloatWithoutTelemetry() {
        val ring = ContinuumOrbitalRing(1.42f, .34f, -11f)
        ring.project(.4f, 1f, 206f, 117f, 101f)
        val start = ring.front.copyOf()
        val phaseAfterFourSeconds = .4f + 4f * (.28f + .18f * .72f)
        ring.project(phaseAfterFourSeconds, 1f, 206f, 117f, 101f)
        assertTrue(start.indices.any { abs(start[it] - ring.front[it]) > 4f })
        assertTrue(abs(ContinuumOrbitalRing.lift(phaseAfterFourSeconds, 101f)) > 1.5f)
        for (i in 0..1000) assertTrue(abs(ContinuumOrbitalRing.lift(i * .13f, 101f)) <= 4.546f)
        val plexus = ContinuumHologramPlexus(28)
        plexus.project(.064f, .3f, 206f, 117f, 101f)
        val x = plexus.sx.copyOf(); val links = plexus.edgeA.copyOf()
        plexus.project(.064f + (phaseAfterFourSeconds - .4f) * .52f, .3f, 206f, 117f, 101f)
        assertTrue(x.indices.count { abs(x[it] - plexus.sx[it]) > 4f } > x.size / 2)
        assertArrayEquals(links, plexus.edgeA)
        assertEquals(0f, ContinuumHologramRenderer.Frame.still().audio, 0f)
    }
}
