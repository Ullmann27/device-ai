package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test

class ContinuumWorkspaceSummaryTest {
    @Test fun missingMeasurementsStayUnknown() {
        val lines = ContinuumWorkspaceSummary.lines(ContinuumDashboardState())
        assertTrue(lines.contains("Kein Dokument geöffnet"))
        assertTrue(lines.contains("CPU —  ·  RAM —"))
        assertTrue(lines.any { it.contains("Offline") })
        assertFalse(lines.any { it.contains("%") })
    }

    @Test fun observedProjectsWorkAndMetricsArePreserved() {
        val state = ContinuumDashboardState(
            development = ContinuumDashboardState.DevelopmentStatus(projects = listOf(
                ContinuumDashboardState.Project("Eigener Auftrag", "Wartet auf Datei",
                    ContinuumDashboardState.ProjectState.IDLE, "gerade eben"))),
            cockpit = CockpitStatus(openWork = listOf("Originalbeleg prüfen")),
            system = ContinuumDashboardState.SystemStatus(appCpuPercent = 7, memoryPercent = 42))
        assertEquals("Eigener Auftrag", ContinuumWorkspaceSummary.projects(state).single().label)
        assertEquals("Wartet auf Datei", ContinuumWorkspaceSummary.projects(state).single().value)
        assertEquals("Originalbeleg prüfen", ContinuumWorkspaceSummary.work(state).single().value)
        assertTrue(ContinuumWorkspaceSummary.lines(state).contains("CPU 7%  ·  RAM 42%"))
    }
}
