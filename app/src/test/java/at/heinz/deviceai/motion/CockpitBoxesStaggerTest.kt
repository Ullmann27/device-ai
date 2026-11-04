package at.heinz.deviceai.motion

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CockpitBoxesStaggerTest {
    // Synthetic normalised centres for the test only (real ones come from Kern's view bounds).
    private val boxes = listOf(
        MotionBox("far", .05f, .9f),
        MotionBox("left", .3f, .5f),
        MotionBox("near", .5f, .62f),
        MotionBox("right", .7f, .5f),
    )

    @Test fun spatialRanksNearestFirstSymmetricTogether() {
        val r = Staggers.spatialRanks(boxes, .5f, .5f)
        assertArrayEquals(intArrayOf(2, 1, 0, 1), r)
        val st = Staggers.spatialCenterFirst(boxes, .5f, .5f)
        assertEquals(listOf(.16f, .08f, 0f, .08f), boxes.indices.map { st.delaySec(it, boxes.size) })
    }

    @Test fun toleranceGroupsAlmostEqualDistances() {
        val b = listOf(MotionBox("a", .3f, .5f), MotionBox("b", .7005f, .5f), MotionBox("c", .9f, .5f))
        assertArrayEquals(intArrayOf(0, 0, 1), Staggers.spatialRanks(b, .5f, .5f))
        assertArrayEquals(intArrayOf(0, 1, 2), Staggers.spatialRanks(b, .5f, .5f, tolerance = 0f))
    }

    @Test fun cockpitBoxesTimeline() {
        val tl = MotionTimelines.cockpitBoxes(boxes, startMs = 1000L)
        assertEquals(boxes.map { it.key }, tl.tracks.map { it.key })
        assertEquals(listOf(1160L, 1080L, 1000L, 1080L), tl.tracks.map { it.startMs })
        assertEquals(listOf(80L, 80L), listOf(tl.tracks[1].startMs - tl.tracks[2].startMs, tl.tracks[0].startMs - tl.tracks[1].startMs))
        tl.tracks.forEach { assertEquals(MotionPresets.FLY_IN_MS, it.durationMs) }
        assertEquals(1160L + 600L, tl.totalMs)
        val out = FloatArray(4)
        tl.evaluate(0, out); out.forEach { assertEquals(0f, it, 0f) }
        tl.evaluate(1000, out); assertEquals(0f, out[2], 0f)
        tl.evaluate(1300, out); assertEquals(Easings.easeOutCubic.at(.5f), out[2], 1e-6f)
        assertEquals(.875f, out[2], 1e-6f)
        // Muster B scale 0,8 → 1 at half time of the nearest box
        assertEquals(.975f, MotionPresets.FLY_IN_FROM_SCALE + (1f - MotionPresets.FLY_IN_FROM_SCALE) * out[2], 1e-6f)
        tl.evaluate(tl.totalMs, out); out.forEach { assertEquals(1f, it, 0f) }
    }
}
