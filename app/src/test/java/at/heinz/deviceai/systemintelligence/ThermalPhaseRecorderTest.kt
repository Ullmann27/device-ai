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
}
