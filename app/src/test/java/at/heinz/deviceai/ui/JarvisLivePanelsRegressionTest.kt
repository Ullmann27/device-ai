package at.heinz.deviceai.ui

import at.heinz.deviceai.learning.ContinuumMemoryStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisLivePanelsRegressionTest {
    @Test fun internetConnectionCannotMakeAnUnloadedLocalModelLookOnline() {
        val p = JarvisPresentationRecordingPainter()
        CockpitLocalModelCard(1f).draw(p, 340f, 230f, ContinuumDashboardState(
            model = ContinuumDashboardState.ModelStatus(online = false, stateLabel = "Nicht geladen"),
            system = ContinuumDashboardState.SystemStatus(networkOnline = true)))
        assertTrue("The local-model pill must show Offline: ${p.texts}", "Offline" in p.texts)
        assertFalse("Internet is not evidence of local-model readiness", "Online" in p.texts)
    }

    @Test fun aReadyLocalModelRemainsOnlineWithoutInternet() {
        val p = JarvisPresentationRecordingPainter()
        CockpitLocalModelCard(1f).draw(p, 340f, 230f, ContinuumDashboardState(
            model = ContinuumDashboardState.ModelStatus(name = "Lokales Testmodell", online = true, stateLabel = "Bereit"),
            system = ContinuumDashboardState.SystemStatus(networkOnline = false)))
        assertTrue("The local model works offline from the network", "Online" in p.texts)
        assertFalse("Offline" in p.texts)
    }

    @Test fun actualGridUsesTheModelCardAndItsRealReadiness() {
        val grid = JarvisCockpitGrid.create(884f, 760f)
        val p = JarvisPresentationRecordingPainter()
        JarvisCockpitGridContent.draw(p, JarvisShellLayout.Slot.ROW_MODEL,
            grid[JarvisCockpitGrid.Area.ROW_MODEL], grid,
            ContinuumDashboardState(model = ContinuumDashboardState.ModelStatus(name = "Testmodell", online = true, stateLabel = "Bereit")), 0L)
        assertTrue("The rich local-model card must be the production grid content", "Modus:" in p.texts)
        assertTrue("Online" in p.texts)
        assertTrue("Missing routing details remain unknown", "—" in p.texts)
    }

    @Test fun actualGridShowsMeasuredMemoryCountsWithoutInventingActivity() {
        val grid = JarvisCockpitGrid.create(884f, 760f)
        val p = JarvisPresentationRecordingPainter()
        val stats = ContinuumMemoryStats(43, 7, null, 18, 3, 2, 1)
        JarvisCockpitGridContent.draw(p, JarvisShellLayout.Slot.ROW_MEMORY,
            grid[JarvisCockpitGrid.Area.ROW_MEMORY], grid, ContinuumDashboardState(memory = stats), 0L)
        assertTrue("Today's measured additions must reach the activity card", "+7" in p.texts)
        assertTrue("43" in p.texts)
        assertTrue("2" in p.texts)
        assertTrue("No event stream means no context-update count", "—" in p.texts)
    }

    @Test fun denseButtonsMeetWhileTouchRowsStaySeparateAndReachable() {
        for (height in listOf(649f, 760f, 884f)) {
            val rows = JarvisMenuRail.rows(height)
            val buttons = rows.map { JarvisMenuRail.button(122f, it) }
            assertEquals(11, buttons.size)
            rows.forEachIndexed { index, row ->
                assertTrue("Each independently selectable row stays at least 48dp", row.second - row.first >= 48f)
                assertEquals(index, JarvisMenuRail.hit((row.first + row.second) / 2f, height))
                assertEquals("Visual and touch centers must agree", (row.first + row.second) / 2f,
                    (buttons[index][1] + buttons[index][3]) / 2f, .001f)
            }
            buttons.zipWithNext().forEach { (a, b) -> assertEquals("No empty band between adjacent buttons", a[3], b[1], .001f) }
            assertTrue("All eleven controls fit the open display", buttons.last()[3] <= height)
        }
    }
}
