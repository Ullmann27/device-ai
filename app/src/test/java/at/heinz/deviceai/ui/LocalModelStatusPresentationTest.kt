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
        assertEquals("gemma-3n · 2", v(r, "Model")); assertEquals("BEREIT", v(r, "Status"))
        assertEquals("840 ms", v(r, "Latency")); assertEquals("6.0 / 8.0 GB", v(r, "RAM"))
        assertEquals("Light", v(r, "Thermal")); assertEquals("Admitted", v(r, "Load"))
        assertEquals("31.5 °C", v(r, "Battery")); assertEquals("1 / 4", v(r, "Agents"))
    }

    @Test fun connectivityPillOnlyFromCollector() {
        fun c(v: Boolean?) = LocalModelStatusPresentation.connectivity(ContinuumDashboardState(system = ContinuumDashboardState.SystemStatus(networkOnline = v))).first
        assertEquals("Online", c(true)); assertEquals("Offline", c(false)); assertEquals(LocalModelStatusPresentation.MISSING, c(null))
    }

    @Test fun admissionRefusalShownVerbatim() {
        val r = LocalModelStatusPresentation.rows(ContinuumDashboardState(),
            LocalModelResourceSnapshot(8L shl 30, 1L shl 30, true, 4), LocalModelLoadAdmission.REFUSED_THERMAL)
        assertEquals(LocalModelLoadAdmission.ownerMessage(LocalModelLoadAdmission.REFUSED_THERMAL), v(r, "Load"))
        assertEquals("Critical", v(r, "Thermal"))
    }

    @Test fun missingSourcesShowFallback() {
        val r = LocalModelStatusPresentation.rows(ContinuumDashboardState())
        val na = LocalModelStatusPresentation.MISSING; val sm = na
        assertEquals(na, v(r, "Model")); assertEquals(na, v(r, "Latency"))
        assertEquals(sm, v(r, "RAM")); assertEquals(sm, v(r, "Thermal")); assertEquals(sm, v(r, "Load"))
        assertEquals(na, v(r, "Battery")); assertEquals(na, v(r, "Agents"))
        val noThermal = LocalModelStatusPresentation.rows(ContinuumDashboardState(), LocalModelResourceSnapshot(1, 1, false, null))
        assertEquals(na, v(noThermal, "Thermal"))
    }
}
