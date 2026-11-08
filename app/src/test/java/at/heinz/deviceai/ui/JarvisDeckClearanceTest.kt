package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisDeckClearanceTest {
    @Test fun onlyTilesAndLowerRowsMakeRoom() {
        assertTrue(JarvisDeckClearance.clears(null))
        for (row in listOf(Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL)) assertTrue(JarvisDeckClearance.clears(row))
        assertTrue(JarvisDeckClearance.clears(Slot.COCKPIT_SECONDARY))
        for (stay in listOf(Slot.DIALOG, Slot.AVATAR, Slot.NAV_RAIL, Slot.STATUS, Slot.TOP_BAR))
            assertFalse(stay.name, JarvisDeckClearance.clears(stay))
    }

    @Test fun centreTilesLeaveFirstAndEveryCardIsGoneAtTheEnd() {
        val centre = JarvisDeckClearance.order(null, 500f, 1000f)
        val edge = JarvisDeckClearance.order(null, 950f, 1000f)
        val row = JarvisDeckClearance.order(Slot.ROW_AGENTS, 500f, 1000f)
        assertTrue(centre < edge && edge <= row)
        val mid = .5f
        assertTrue(JarvisDeckClearance.local(mid, centre) > JarvisDeckClearance.local(mid, edge))
        for (order in listOf(0f, .3f, .7f, 1f)) {
            assertEquals(0f, JarvisDeckClearance.local(0f, order), 0f)
            assertEquals(1f, JarvisDeckClearance.local(1f, order), 0f)
            assertEquals(0f, JarvisDeckClearance.alpha(JarvisDeckClearance.local(1f, order)), 0f)
        }
        assertEquals(JarvisDeckClearance.TRAVEL_DP, JarvisDeckClearance.dy(1f), 0f)
        assertEquals(1f, JarvisDeckClearance.scale(0f), 0f)
    }

    @Test fun mediumAndFullFootprintsUseSeparateClearanceTracks() {
        assertEquals(1f, JarvisDeckClearance.clearance(null, 1f, 0f), 0f)
        assertEquals(1f, JarvisDeckClearance.clearance(Slot.COCKPIT_SECONDARY, 1f, 0f), 0f)
        assertEquals(0f, JarvisDeckClearance.clearance(Slot.ROW_AGENTS, 1f, 0f), 0f)
        assertEquals(1f, JarvisDeckClearance.clearance(Slot.ROW_AGENTS, 1f, 1f), 0f)
        assertEquals(0f, JarvisDeckClearance.clearance(Slot.DIALOG, 1f, 1f), 0f)
        assertEquals(.5f, JarvisDeckClearance.step(0f, .5f, 360L, motion = true), 0f)
    }

    @Test fun progressFollowsTheFrameClockAndReducedMotionSnaps() {
        var p = 0f
        repeat(30) { p = JarvisDeckClearance.step(p, cleared = true, elapsedMs = 16L, motion = true) }
        assertEquals(1f, p, 0f) // 30 frames x 16 ms > 360 ms
        assertEquals(.75f, JarvisDeckClearance.step(1f, cleared = false, elapsedMs = 90L, motion = true), 1e-4f)
        assertEquals(0f, JarvisDeckClearance.step(.7f, cleared = false, elapsedMs = 1L, motion = false), 0f)
        assertEquals("a long frame gap cannot jump more than 100 ms", 100f / 360f,
            JarvisDeckClearance.step(0f, cleared = true, elapsedMs = 5_000L, motion = true), 1e-4f)
    }
}
