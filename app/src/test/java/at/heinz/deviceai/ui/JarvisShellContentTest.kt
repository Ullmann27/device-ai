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

    @Test fun hitTestRoutesAllFeatureCardsWithoutAFixedRailAcrossWindowSizes() {
        val tiles = JarvisTiles.reference
        for (width in listOf(412f, 692f, 884f, 1280f)) {
            val shell = JarvisShellLayout.create(width, 760f)
            assertEquals(null, shell[JarvisShellLayout.Slot.NAV_RAIL])
            if (shell.hasFeatureTiles) {
                assertEquals(10, shell.tileBoxes().size)
                shell.tileBoxes().forEachIndexed { index, tile ->
                    assertEquals(tiles[index].target,
                        JarvisShellContent.hitTarget(shell, tiles, tile.centerX, tile.centerY))
                }
            } else {
                val dock = requireNotNull(shell[JarvisShellLayout.Slot.BOTTOM_NAV])
                assertEquals(JarvisTiles.railNav.map { it.target },
                    JarvisTiles.bottomNavItemBoxes(dock).map { box ->
                        JarvisShellContent.hitTarget(shell, tiles, box.centerX, box.centerY)
                    })
            }
            assertEquals(null, JarvisShellContent.hitTarget(shell, tiles, 0f, 100f))
        }
        val phone = JarvisShellLayout.create(412f, 915f, includeBottomNavigation = true)
        assertFalse(phone.hasFeatureTiles)
        assertEquals(0, phone.tileBoxes().size)
        val nav = phone[JarvisShellLayout.Slot.BOTTOM_NAV]!!
        val cells = JarvisTiles.bottomNavItemBoxes(nav)
        assertEquals(JarvisTiles.bottomNav.map { it.target },
            cells.map { box -> JarvisShellContent.hitTarget(phone, tiles, box.centerX, box.centerY) })
        val last = cells.last()
        assertEquals(JarvisTiles.bottomNav.last().target,
            JarvisShellContent.hitTarget(phone, tiles, last.centerX, last.centerY))
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
