package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTurnLatencyTraceTest {
    @Test fun recordsFirstEventOnlyAndMeasuresMonotonicIntervals() {
        var now = 1_000_000_000L
        val trace = VoiceTurnLatencyTrace { now }
        assertTrue(trace.mark(VoiceTurnLatencyTrace.Stage.MIC_TAPPED))
        now += 125_000_000L
        assertTrue(trace.mark(VoiceTurnLatencyTrace.Stage.ASR_READY))
        now += 1_000_000L
        assertFalse(trace.mark(VoiceTurnLatencyTrace.Stage.MIC_TAPPED))
        assertEquals(125.0, trace.durationMillis(
            VoiceTurnLatencyTrace.Stage.MIC_TAPPED,
            VoiceTurnLatencyTrace.Stage.ASR_READY
        )!!, 0.0001)
        assertEquals(2, trace.snapshot().size)
    }

    @Test fun missingAndReorderedEventsCannotCreateFalseLatency() {
        var now = 5_000_000L
        val trace = VoiceTurnLatencyTrace { now }
        assertTrue(trace.mark(VoiceTurnLatencyTrace.Stage.MODEL_STARTED))
        assertNull(trace.durationMillis(VoiceTurnLatencyTrace.Stage.MODEL_STARTED,
            VoiceTurnLatencyTrace.Stage.FIRST_TEXT_DELTA))
        now -= 1_000_000L
        assertTrue(trace.mark(VoiceTurnLatencyTrace.Stage.FIRST_TEXT_DELTA))
        assertNull(trace.durationMillis(VoiceTurnLatencyTrace.Stage.MODEL_STARTED,
            VoiceTurnLatencyTrace.Stage.FIRST_TEXT_DELTA))
        assertFalse(trace.recorded(VoiceTurnLatencyTrace.Stage.TTS_STARTED_CALLBACK))
    }

    @Test fun ttsStartCallbackIsNotAnAudibleFrameClaim() {
        assertTrue(VoiceTurnLatencyTrace.Stage.entries.any { it.name == "TTS_STARTED_CALLBACK" })
        assertFalse(VoiceTurnLatencyTrace.Stage.entries.any { it.name == "FIRST_AUDIBLE_FRAME" })
    }

    @Test fun percentilesRejectInvalidSamplesAndUseNearestRank() {
        assertEquals(200.0, VoiceTurnLatencyPercentiles.percentileMillis(
            listOf(100.0, 200.0, 300.0, Double.NaN), 50)!!, 0.001)
        assertEquals(300.0, VoiceTurnLatencyPercentiles.percentileMillis(
            listOf(100.0, 200.0, 300.0), 95)!!, 0.001)
        assertNull(VoiceTurnLatencyPercentiles.percentileMillis(
            listOf(-1.0, Double.POSITIVE_INFINITY), 95))
    }
}
