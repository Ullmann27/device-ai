package at.heinz.deviceai.cognitive

import java.io.File
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

    /**
     * Host source-contract: the production ticket is checked twice (before/after GPU copy).
     * The owner-visible target-window counter must only be incremented after successful
     * PRE-copy validation; no change to the fail-closed post-copy source check is allowed.
     */
    @Test fun liveTargetVisibilityIsCountedOncePerReceivedFrame() {
        val relative = "at/heinz/deviceai/ContinuumLiveProjectionService.kt"
        val file = listOf(File("src/main/java", relative), File("app/src/main/java", relative))
            .firstOrNull(File::isFile) ?: error("Live projection source unavailable for regression contract")
        val source = file.readText()
        val ticket = source.substringAfter("private fun ticket(currentReader: ImageReader): Ticket?")
            .substringBefore("private fun probeProtection")
        val onImage = source.substringAfter("private fun onImage(source: ImageReader)")
            .substringBefore("private fun retire")
        val targetCounter = "LiveCaptureDiagnostics.Event.TARGET_WINDOW_VISIBLE"
        assertFalse("ticket() runs both before and after copy; it must never count a frame", ticket.contains(targetCounter))
        assertEquals("exactly one verified-target counter per onImage callback", 1,
            onImage.split(targetCounter).size - 1)
        val beforeGuard = onImage.indexOf("if (before == null)")
        val confirmed = onImage.indexOf(targetCounter)
        val afterTicket = onImage.indexOf("val after =")
        assertTrue("count only after successful pre-copy ticket", beforeGuard >= 0 && confirmed > beforeGuard)
        assertTrue("do not count on the second ticket", afterTicket > confirmed)
        assertTrue("retain the fail-closed post-copy check", onImage.contains("before.revision != after.revision"))
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
