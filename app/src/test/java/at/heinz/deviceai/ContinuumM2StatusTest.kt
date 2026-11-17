package at.heinz.deviceai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumM2StatusTest {
    @Test fun emptyEvidenceNeverClaimsLocalModelOrAutonomousRepair() {
        val report = ContinuumM2Status.render(ContinuumM2Status.Snapshot(
            modelImported = false, modelLoaded = false, modelRuntimeReady = false,
            directInferenceObserved = false, directInferenceMillis = null
        ))
        assertTrue(report.contains("Gemma 4 E4B Originaldatei: NOCH NICHT NACHGEWIESEN"))
        assertTrue(report.contains("Direkte native Antwort in DIESER App-Sitzung: NICHT NACHGEWIESEN"))
        assertTrue(report.contains("Modellgenerierter CCC1-Patch: NICHT NACHGEWIESEN"))
        assertFalse(report.contains("Direkte native Antwort in DIESER App-Sitzung: GEMESSEN"))
    }

    @Test fun importedOriginalModelDoesNotImplyRuntimeOrInference() {
        val report = ContinuumM2Status.render(ContinuumM2Status.Snapshot(
            modelImported = true, modelLoaded = false, modelRuntimeReady = false,
            directInferenceObserved = true, directInferenceMillis = 12
        ))
        assertTrue(report.contains("Originaldatei: SHA-256 im lokalen Katalog bestätigt"))
        assertTrue(report.contains("Gemma lokal geladen: NEIN / NICHT BEREIT"))
        assertTrue(report.contains("Antwort in DIESER App-Sitzung: NICHT NACHGEWIESEN"))
    }

    @Test fun runtimeReadyAloneDoesNotProveNativeReply() {
        val report = ContinuumM2Status.render(ContinuumM2Status.Snapshot(
            modelImported = true, modelLoaded = true, modelRuntimeReady = true,
            directInferenceObserved = false, directInferenceMillis = null
        ))
        assertTrue(report.contains("Gemma lokal geladen: JA"))
        assertTrue(report.contains("Direkte native Antwort in DIESER App-Sitzung: NICHT NACHGEWIESEN"))
    }

    @Test fun onlyRealModelReadyAndDirectObservedReplyMarksSessionProof() {
        val report = ContinuumM2Status.render(ContinuumM2Status.Snapshot(
            modelImported = true, modelLoaded = true, modelRuntimeReady = true,
            directInferenceObserved = true, directInferenceMillis = 1396
        ))
        assertTrue(report.contains("Direkte native Antwort in DIESER App-Sitzung: GEMESSEN · 1396 ms"))
        assertTrue(report.contains("Offline-Test bei abgeschaltetem Internet: NICHT NACHGEWIESEN"))
        assertTrue(report.contains("RED → GREEN → frischer unabhängiger Test: NICHT NACHGEWIESEN"))
        assertTrue(report.contains("Lernen aus geprüfter Reparatur nach Neustart: NICHT NACHGEWIESEN"))
    }

    @Test fun inconsistentRuntimeAndBogusNegativeMeasurementCannotMarkPass() {
        for (bad in listOf(
            ContinuumM2Status.Snapshot(true, false, true, true, 55),
            ContinuumM2Status.Snapshot(true, true, false, true, 55),
            ContinuumM2Status.Snapshot(true, true, true, true, null),
            ContinuumM2Status.Snapshot(true, true, true, true, -1)
        )) {
            assertTrue(ContinuumM2Status.render(bad)
                .contains("Direkte native Antwort in DIESER App-Sitzung: NICHT NACHGEWIESEN"))
        }
    }
}
