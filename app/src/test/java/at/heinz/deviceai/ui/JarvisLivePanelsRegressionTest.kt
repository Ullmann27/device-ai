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
        val shell = JarvisShellLayout.create(884f, 760f)
        val p = JarvisPresentationRecordingPainter()
        val state = ContinuumDashboardState(model = ContinuumDashboardState.ModelStatus(name = "Testmodell", online = true, stateLabel = "Bereit"))
        JarvisCockpitGridContent.draw(p, JarvisShellLayout.Slot.COCKPIT_SECONDARY,
            shell[JarvisShellLayout.Slot.COCKPIT_SECONDARY]!!, shell.grid, state, 0L, shell)
        assertTrue("Lokales Modell" in p.texts)
        assertTrue("The compact module reports real readiness", "online" in p.texts)
        CockpitLocalModelCard(1f).draw(p, 340f, 230f, state)
        assertTrue("The model card still uses the wide area for actual telemetry", "App-CPU" in p.texts)
        assertTrue("Geräte-RAM" in p.texts)
        assertTrue("Missing measurements remain unknown", "—" in p.texts)
    }

    @Test fun measuredCpuRamAndBatteryTemperatureReachTheDiagramCard() {
        val p = JarvisPresentationRecordingPainter()
        CockpitLocalModelCard(1f).draw(p, 340f, 190f, ContinuumDashboardState(
            system = ContinuumDashboardState.SystemStatus(appCpuPercent = 23, memoryPercent = 55,
                temperatureC = 35.4f, activity = listOf(12f, 18f, 23f))))
        assertTrue("23 %" in p.texts)
        assertTrue("55 %" in p.texts)
        assertTrue("Akku-Temperatur" in p.texts)
        assertTrue("35,4 °C" in p.texts)
        assertFalse("Measured history is available", "Verlauf noch nicht verfügbar" in p.texts)
    }

    @Test fun absentMeasurementsNeverBecomeInventedZeroes() {
        val p = JarvisPresentationRecordingPainter()
        CockpitLocalModelCard(1f).draw(p, 340f, 190f, ContinuumDashboardState(
            system = ContinuumDashboardState.SystemStatus()))
        assertEquals("CPU, RAM and battery temperature are independently unknown", 3, p.texts.count { it == "—" })
        assertTrue("Verlauf noch nicht verfügbar" in p.texts)
        assertFalse("0 %" in p.texts)
        assertFalse("0,0 °C" in p.texts)
    }

    @Test fun actualGridShowsMeasuredMemoryCountsWithoutInventingActivity() {
        val shell = JarvisShellLayout.create(884f, 760f)
        val p = JarvisPresentationRecordingPainter()
        val stats = ContinuumMemoryStats(43, 7, null, 18, 3, 2, 1)
        val state = ContinuumDashboardState(memory = stats)
        JarvisCockpitGridContent.draw(p, JarvisShellLayout.Slot.COCKPIT_SECONDARY,
            shell[JarvisShellLayout.Slot.COCKPIT_SECONDARY]!!, shell.grid, state, 0L, shell)
        assertTrue("Gedächtnis" in p.texts)
        assertTrue("aktiv" in p.texts)
        JarvisMemoryActivityPanel.draw(p, 0f, 0f, 340f, 190f, MemoryActivitySnapshot.from(stats, null))
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
