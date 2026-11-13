package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCaptureDiagnosticsTest {
    private val d = LiveCaptureDiagnostics

    @Test fun consentSurvivesSessionStartButFrameCountsDoNotLeak() {
        d.prepareConsent()
        d.recordPending(LiveCaptureDiagnostics.Event.PROJECTION_CONSENT_ACCEPTED)
        d.begin(41)
        repeat(3) { d.record(41, LiveCaptureDiagnostics.Event.CAPTURE_FRAME_RECEIVED) }
        d.reject(41, LiveCaptureDiagnostics.Rejection.SOURCE_CHANGED_DURING_COPY)
        d.record(40, LiveCaptureDiagnostics.Event.MONITOR_PRESENTED) // stale session: ignored
        assertEquals(1, d.count(LiveCaptureDiagnostics.Event.PROJECTION_CONSENT_ACCEPTED))
        assertEquals(3, d.count(LiveCaptureDiagnostics.Event.CAPTURE_FRAME_RECEIVED))
        assertEquals(0, d.count(LiveCaptureDiagnostics.Event.MONITOR_PRESENTED))
        val line = d.summary()
        assertTrue(line, line.startsWith("Bildkette: Freigabe erteilt 1"))
        assertTrue(line, line.contains("Bilder empfangen 3"))
        assertTrue(line, line.contains("verworfen: Quelle während Kopie geändert 1"))

        d.begin(42)
        assertEquals(0, d.count(LiveCaptureDiagnostics.Event.CAPTURE_FRAME_RECEIVED))
        assertEquals(0, d.count(LiveCaptureDiagnostics.Rejection.SOURCE_CHANGED_DURING_COPY))
        assertFalse(d.summary().contains("verworfen"))
    }

    @Test fun nextTaskStartsFromZeroAndPendingEventsNeedNoSession() {
        d.begin(7)
        d.record(7, LiveCaptureDiagnostics.Event.ACTION_DISPATCHED)
        d.prepareConsent()
        assertEquals(0, d.count(LiveCaptureDiagnostics.Event.ACTION_DISPATCHED))
        d.record(7, LiveCaptureDiagnostics.Event.ACTION_DISPATCHED)
        assertEquals("old session cannot count after a new task began", 0,
            d.count(LiveCaptureDiagnostics.Event.ACTION_DISPATCHED))
        d.recordPending(LiveCaptureDiagnostics.Event.PROJECTION_CONSENT_REQUESTED)
        assertEquals(1, d.count(LiveCaptureDiagnostics.Event.PROJECTION_CONSENT_REQUESTED))
    }
}
