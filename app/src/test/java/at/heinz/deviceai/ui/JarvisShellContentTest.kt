package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisShellContentTest {
    @Test fun everyContentSlotOfEveryFormFactorHasRealRowsOrNotAvailable() {
        val content = JarvisShellContent.forState({ ContinuumDashboardState() })
        for ((w, h) in listOf(412f to 915f, 884f to 760f, 1280f to 800f)) {
            val shell = JarvisShellLayout.create(w, h)
            for (slot in shell.boxes.keys.filter { it in JarvisShellContent.filled })
                assertTrue("$w x $h $slot has Kern content", slot in content)
            assertFalse("Avatar slot belongs to the live avatar", JarvisShellLayout.Slot.AVATAR in content)
        }
        val empty = ContinuumDashboardState()
        val system = JarvisShellContent.rows(JarvisShellLayout.Slot.STATUS, empty, 0L)
        assertTrue(system.all { it.value == ContinuumCockpitPresentation.NOT_AVAILABLE })
        assertEquals(ContinuumCockpitPresentation.NOT_AVAILABLE, JarvisShellContent.rows(JarvisShellLayout.Slot.ROW_MEMORY, empty, 0L).single().value)
        assertEquals("Noch kein Auftrag", JarvisShellContent.rows(JarvisShellLayout.Slot.ROW_MODEL, empty, 0L).first().value)
    }

    @Test fun tilesShowOnlyRealValuesAndRouteToExistingScreens() {
        val na = ContinuumCockpitPresentation.NOT_AVAILABLE
        val tiles = JarvisShellContent.tiles(ContinuumDashboardState(), 0L)
        assertEquals(JarvisTiles.reference.map { it.target }, tiles.map { it.target })
        for (t in tiles) {
            assertTrue("${t.target} routed", t.target in JarvisShellContent.routes)
            assertTrue("${t.target} has no digit without a source: ${t.displayText}", t.displayText!!.none(Char::isDigit))
        }
        assertEquals(na, tiles.first { it.target == "memory" }.displayText)
        val live = ContinuumDashboardState(system = ContinuumDashboardState.SystemStatus(batteryPercent = 64))
        assertEquals("Akku 64 %", JarvisShellContent.tileValue("device", live, 0L))
        for (item in JarvisTiles.railNav + JarvisTiles.bottomNav) assertTrue(item.target in JarvisShellContent.routes)
    }

    @Test fun hitTestFindsTileRailAndBottomNavTargets() {
        val tiles = JarvisTiles.reference
        val fold = JarvisShellLayout.create(884f, 760f)
        val first = fold.tileBoxes().first()
        assertEquals("chat", JarvisShellContent.hitTarget(fold, tiles, first.centerX, first.top + first.height / 2f))
        val rail = fold[JarvisShellLayout.Slot.NAV_RAIL]!!
        val firstRailRow = JarvisTiles.railItemBoxes(rail).first()
        assertEquals("home", JarvisShellContent.hitTarget(fold, tiles, firstRailRow.centerX, firstRailRow.centerY))
        assertEquals(null, JarvisShellContent.hitTarget(fold, tiles, rail.left, rail.top))
        val phone = JarvisShellLayout.create(412f, 915f, includeBottomNavigation = true)
        val nav = phone[JarvisShellLayout.Slot.BOTTOM_NAV]!!
        assertEquals(JarvisTiles.bottomNav.last().target, JarvisShellContent.hitTarget(phone, tiles, nav.right - 4f, nav.top + 10f))
    }

    @Test fun timelineTimingsArePinnedToJarvisMotion() {
        val t = at.heinz.deviceai.cockpit.CockpitTimeline
        assertEquals(JarvisMotion.Durations.PANEL_FLY_IN, t.PANEL_FLY_IN_MS)
        assertEquals(JarvisMotion.Durations.STAGGER, t.STAGGER_MS)
        assertEquals(JarvisMotion.Durations.SCENE_Z, t.SCENE_Z_MS)
        assertEquals(JarvisMotion.Durations.ENVELOPE_IN + JarvisMotion.Durations.ENVELOPE_FLAP, t.ENVELOPE_MS)
        assertEquals(JarvisMotion.Durations.PILL_FLY_IN, t.PILL_MS)
        assertEquals(JarvisMotion.Durations.STEP, t.STEP_MS)
    }
}
