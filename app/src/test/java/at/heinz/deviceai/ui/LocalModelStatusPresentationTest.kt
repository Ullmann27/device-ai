package at.heinz.deviceai.ui

import at.heinz.deviceai.chief.ChiefWorkerPool
import at.heinz.deviceai.localmodel.LocalModelLoadAdmission
import at.heinz.deviceai.localmodel.LocalModelResourceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelStatusPresentationTest {
    private fun v(rows: List<ContinuumCockpitPresentation.Row>, l: String) = rows.single { it.label == l }.value

    @Test fun everyValueMappedFromItsSource() {
        val s = ContinuumDashboardState(
            model = ContinuumDashboardState.ModelStatus(name = "gemma-3n · 2", online = true, stateLabel = "BEREIT", latencyMs = 840),
            system = ContinuumDashboardState.SystemStatus(temperatureC = 31.5f),
            cockpit = CockpitStatus(workers = listOf(ChiefWorkerPool.WorkerSnapshot("w1", "g", true, false, 1, 9),
                ChiefWorkerPool.WorkerSnapshot("w2", "g", false, false, 0, 9)), workerCapacity = 4))
        val r = LocalModelStatusPresentation.rows(s, LocalModelResourceSnapshot(8L shl 30, 2L shl 30, false, 1), null)
        assertEquals("gemma-3n · 2", v(r, "Modell")); assertEquals("BEREIT", v(r, "Zustand"))
        assertEquals("840 ms", v(r, "Antwortzeit")); assertEquals("2.0 / 8.0 GB", v(r, "RAM frei"))
        assertEquals("leicht", v(r, "Thermik")); assertEquals("frei", v(r, "Ladefreigabe"))
        assertEquals("31.5 °C", v(r, "Akku-Temp.")); assertEquals("1 aktiv / 4", v(r, "Agenten"))
    }

    @Test fun admissionRefusalShownVerbatim() {
        val r = LocalModelStatusPresentation.rows(ContinuumDashboardState(),
            LocalModelResourceSnapshot(8L shl 30, 1L shl 30, true, 4), LocalModelLoadAdmission.REFUSED_THERMAL)
        assertEquals(LocalModelLoadAdmission.ownerMessage(LocalModelLoadAdmission.REFUSED_THERMAL), v(r, "Ladefreigabe"))
        assertEquals("kritisch", v(r, "Thermik"))
    }

    @Test fun missingSourcesShowFallback() {
        val r = LocalModelStatusPresentation.rows(ContinuumDashboardState())
        val na = LocalModelStatusPresentation.NOT_AVAILABLE; val sm = LocalModelStatusPresentation.SOURCE_MISSING
        assertEquals(na, v(r, "Modell")); assertEquals(na, v(r, "Antwortzeit"))
        assertEquals(sm, v(r, "RAM frei")); assertEquals(sm, v(r, "Thermik")); assertEquals(sm, v(r, "Ladefreigabe"))
        assertEquals(na, v(r, "Akku-Temp.")); assertEquals(na, v(r, "Agenten"))
        val noThermal = LocalModelStatusPresentation.rows(ContinuumDashboardState(), LocalModelResourceSnapshot(1, 1, false, null))
        assertEquals(na, v(noThermal, "Thermik"))
    }
}
