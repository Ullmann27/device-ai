package at.heinz.deviceai.systemintelligence

import at.heinz.deviceai.systemintelligence.ThermalPhaseRecorder.Phase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThermalPhaseRecorderTest {
    private var now = 0L
    private val recorder = ThermalPhaseRecorder { now }

    @Test fun longInferenceIsRefusedWhenWarmOrUnknownAndNothingElseIsBlocked() {
        assertTrue(recorder.start(Phase.LONG_INFERENCE, 2) is ThermalPhaseRecorder.Start.Refused)
        assertTrue(recorder.start(Phase.LONG_INFERENCE, null) is ThermalPhaseRecorder.Start.Refused)
        assertNull(recorder.active)
        assertEquals(ThermalPhaseRecorder.Start.Started, recorder.start(Phase.LONG_INFERENCE, 1))
        assertEquals(ThermalPhaseRecorder.Start.Started, recorder.start(Phase.CINEMA_ONLY, 4))
    }

    @Test fun samplesBelongToTheActivePhaseAndInvalidValuesStayUnknown() {
        assertFalse("no phase, no sample", recorder.sample(1, .3f, 330))
        recorder.start(Phase.IDLE_APP_OPEN, 0)
        assertTrue(recorder.sample(9, Float.NaN, 9_999, -5))
        val s = recorder.samples().single()
        assertNull(s.thermalStatus); assertNull(s.headroom); assertNull(s.batteryTenthsC); assertNull(s.modelRunMs)
        assertEquals(Phase.IDLE_APP_OPEN, s.phase)
    }

    @Test fun summaryReportsPerPhaseDeltasAndNeverClaimsGpu() {
        recorder.start(Phase.SHORT_INFERENCE, 1)
        now = 1_000; recorder.sample(1, .40f, 330, 2_800)
        now = 4_000; recorder.sample(2, .35f, 345, 3_100)
        recorder.stop()
        recorder.start(Phase.AFTER_STOP_REST, 2)
        now = 9_000; recorder.sample(1, .50f, 340)
        val summary = recorder.summary()
        assertFalse(summary.getBoolean("gpuMeasured"))
        val phases = summary.getJSONArray("phases")
        assertEquals(2, phases.length())
        val short = phases.getJSONObject(0)
        assertEquals("SHORT_INFERENCE", short.getString("phase"))
        assertEquals(2, short.getInt("maxThermalStatus"))
        assertEquals(15, short.getInt("batteryDeltaTenthsC"))
        assertEquals(3_100L, short.getLong("maxModelRunMs"))
        assertTrue(phases.getJSONObject(1).isNull("batteryDeltaTenthsC"))
    }

    @Test fun sessionIsBounded() {
        recorder.start(Phase.CINEMA_ONLY, 0)
        repeat(ThermalPhaseRecorder.MAX_SAMPLES) { assertTrue(recorder.sample(0, .9f, 300)) }
        assertFalse(recorder.sample(0, .9f, 300))
        assertEquals(ThermalPhaseRecorder.MAX_SAMPLES, recorder.samples().size)
    }

    @Test fun diagnosisRequiresOptInAndCannotResumeAfterStop() {
        val snapshot = diagnosis(100)
        assertFalse(recorder.sampleDiagnosis(snapshot, 90, 110))
        recorder.start(Phase.IDLE_APP_OPEN, 0)
        assertFalse(recorder.sampleDiagnosis(snapshot, 90, 110))
        recorder.start(Phase.DIAGNOSTIC_SNAPSHOT, null)
        assertTrue(recorder.sampleDiagnosis(snapshot, 90, 110))
        recorder.stop()
        assertFalse(recorder.sampleDiagnosis(diagnosis(200), 190, 210))
        assertNull(recorder.active)
        assertEquals(1, recorder.samples().size)
    }

    @Test fun diagnosisRejectsStaleFutureReplayedAndOutOfOrderSnapshots() {
        recorder.start(Phase.DIAGNOSTIC_SNAPSHOT, null)
        assertFalse(recorder.sampleDiagnosis(diagnosis(99), 100, 110))
        assertFalse(recorder.sampleDiagnosis(diagnosis(111), 100, 110))
        assertFalse(recorder.sampleDiagnosis(diagnosis(100), 101, 100))
        assertFalse(recorder.sampleDiagnosis(diagnosis(100), -1, 110))
        assertTrue(recorder.sampleDiagnosis(diagnosis(100), 90, 110))
        assertFalse(recorder.sampleDiagnosis(diagnosis(100), 90, 120))
        assertFalse(recorder.sampleDiagnosis(diagnosis(99), 90, 120))
        assertEquals(1, recorder.samples().size)
        assertTrue(recorder.sampleDiagnosis(diagnosis(120), 110, 130))
        assertEquals(2, recorder.samples().size)
    }

    @Test fun diagnosisOnlyUsesReportedThermalAndBatteryNumbers() {
        recorder.start(Phase.DIAGNOSTIC_SNAPSHOT, null)
        now = 500
        assertTrue(recorder.sampleDiagnosis(diagnosis(100, 3, 37.2), 90, 110))
        val actual = recorder.samples().single()
        assertEquals(500L, actual.atMs)
        assertEquals(Phase.DIAGNOSTIC_SNAPSHOT, actual.phase)
        assertEquals(3, actual.thermalStatus)
        assertEquals(372, actual.batteryTenthsC)
        assertNull(actual.headroom)
        assertNull(actual.modelRunMs)
        assertTrue(recorder.sampleDiagnosis(diagnosis(120, 1, 36.5), 110, 130))
        val report = recorder.diagnosticReportGerman()
        assertTrue(report, report.contains("Messpunkte: 2/2000"))
        assertTrue(report, report.contains("Thermalstatus (höchster): 3"))
        assertTrue(report, report.contains("36.5 °C"))
        assertTrue(report, report.contains("-0.7 °C"))
        val phase = recorder.summary().getJSONArray("phases").getJSONObject(0)
        assertEquals("DIAGNOSTIC_SNAPSHOT", phase.getString("phase"))
        assertEquals(-7, phase.getInt("batteryDeltaTenthsC"))
        assertTrue(phase.isNull("minHeadroom"))
        assertTrue(phase.isNull("maxModelRunMs"))
        assertFalse(recorder.summary().getBoolean("gpuMeasured"))
        recorder.stop()
        assertTrue(recorder.diagnosticReportGerman().contains("gestoppt"))
    }

    @Test fun unknownAndInvalidDiagnosisValuesStayUnknown() {
        recorder.start(Phase.DIAGNOSTIC_SNAPSHOT, null)
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, 80.1, -20.1).forEachIndexed { index, temperature ->
            val timestamp = index + 1L
            assertTrue(recorder.sampleDiagnosis(diagnosis(timestamp, 9, temperature), 0, timestamp))
        }
        recorder.samples().forEach {
            assertNull(it.thermalStatus)
            assertNull(it.batteryTenthsC)
            assertNull(it.headroom)
            assertNull(it.modelRunMs)
        }
        val phase = recorder.summary().getJSONArray("phases").getJSONObject(0)
        assertTrue(phase.isNull("maxThermalStatus"))
        assertTrue(phase.isNull("batteryDeltaTenthsC"))
        val report = recorder.diagnosticReportGerman()
        assertTrue(report, report.contains("Letzte Akkutemperatur: unbekannt"))
        assertTrue(report, report.contains("Akkuänderung: unbekannt"))
        assertFalse(report.contains("NaN"))
        assertFalse(report.contains("Infinity"))
    }

    @Test fun diagnosisCapacityStopsRecordingUntilANewOwnerSession() {
        recorder.start(Phase.DIAGNOSTIC_SNAPSHOT, null)
        repeat(ThermalPhaseRecorder.MAX_SAMPLES) { index ->
            val timestamp = index + 1L
            assertTrue(recorder.sampleDiagnosis(diagnosis(timestamp), 0, timestamp))
        }
        assertNull(recorder.active)
        assertEquals(ThermalPhaseRecorder.MAX_SAMPLES, recorder.samples().size)
        assertFalse(recorder.sampleDiagnosis(diagnosis(3_000), 2_999, 3_001))
        recorder.start(Phase.DIAGNOSTIC_SNAPSHOT, null)
        assertFalse(recorder.sampleDiagnosis(diagnosis(3_000), 2_999, 3_001))
        assertNull(recorder.active)
        val next = ThermalPhaseRecorder { now }
        assertFalse(next.sampleDiagnosis(diagnosis(3_000), 2_999, 3_001))
        next.start(Phase.DIAGNOSTIC_SNAPSHOT, null)
        assertTrue(next.sampleDiagnosis(diagnosis(3_000), 2_999, 3_001))
        assertEquals(1, next.samples().size)
    }

    private fun diagnosis(atMillis: Long, status: Int? = 1, batteryC: Double? = 33.0) = SystemDiagnosis(
        observedAtMillis = atMillis, androidSdk = 35, storageTotalBytes = null, storageAvailableBytes = null,
        ramTotalBytes = null, ramAvailableBytes = null, lowMemory = null, ownPssBytes = null,
        batteryPercent = null, batteryTemperatureC = batteryC, charging = null, thermalStatus = status,
        ownCpuCorePercent = null, cpuSampleMillis = 250
    )
}
