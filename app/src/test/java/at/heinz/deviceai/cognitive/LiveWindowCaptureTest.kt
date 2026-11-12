package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LiveWindowCaptureTest {
    private val binding = LiveFrameBinding("task", 8, "com.whatsapp", "at.heinz.deviceai",
        LiveFrameOrigin.ACCESSIBILITY_WINDOW)
    private val window = LiveWindowRecord(42, binding.targetPackage, LiveWindowKind.APPLICATION,
        LivePixelRect(0, 0, 600, 1000), 3, true, true)
    private val ticket = LiveWindowCapture(binding, 2, 10, window, 1000)
    private fun age(source: LiveWindowCapture = ticket, current: LiveFrameBinding = binding,
                    epoch: Long = 2, revision: Long = 10, currentWindow: LiveWindowRecord = window,
                    captured: Long = 1020, now: Long = 1040) =
        source.ageIfCurrent(current, epoch, revision, currentWindow, captured, now)

    @Test fun stableCaptureUsesAcquisitionTimeInsteadOfCallbackTime() {
        assertEquals(20L, age())
        assertEquals(480L, age(now = 1500))
        assertEquals(1500L, age(now = 2520))
        assertNull(age(now = 2521))
    }

    @Test fun newerCallbackObservationCannotRelabelOlderPixels() {
        assertNull(age(revision = 11))
        assertNull(age(revision = 9))
        assertNull(age(epoch = 3))
    }

    @Test fun changedOrReusedWindowDoesNotKeepTheSourceIdentity() {
        for (next in listOf(window.copy(id = 43), window.copy(packageName = "com.other.app"),
            window.copy(bounds = LivePixelRect(10, 0, 610, 1000)), window.copy(layer = 4),
            window.copy(focused = false), window.copy(kind = LiveWindowKind.SYSTEM))) {
            assertNull(age(currentWindow = next))
        }
        assertNull(age(current = binding.copy(captureSession = 9)))
        assertNull(age(current = binding.copy(taskId = "next")))
    }

    @Test fun preRequestFutureAndInvalidTimesAreRefused() {
        assertNull(age(captured = 999))
        assertNull(age(captured = 1041))
        assertNull(age(source = ticket.copy(requestedAtUptimeMillis = -1)))
        assertNull(age(source = ticket.copy(requestedAtUptimeMillis = 0), captured = 0))
        assertNull(age(captured = Long.MAX_VALUE, now = Long.MAX_VALUE))
    }

    @Test fun invalidInitialSourceCannotBecomeValidAtDelivery() {
        val wrong = window.copy(packageName = "com.other.app")
        assertNull(age(source = ticket.copy(window = wrong), currentWindow = wrong))
        assertNull(age(source = ticket.copy(window = window.copy(id = -1)), currentWindow = window.copy(id = -1)))
        val other = binding.copy(origin = LiveFrameOrigin.PROJECTION_DISPLAY)
        assertNull(age(source = ticket.copy(binding = other), current = other))
    }

    @Test fun expiredDuringConversionCannotReturnLive() {
        assertNotNull(age(now = 1100))
        assertNull(age(now = 2700))
        assertNull(age(now = 1200, revision = 11))
    }
}
