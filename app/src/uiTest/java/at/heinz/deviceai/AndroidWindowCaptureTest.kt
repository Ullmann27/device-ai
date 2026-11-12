package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.ColorSpace
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import at.heinz.deviceai.cognitive.LiveMonitorState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [AndroidWindowCaptureTest.CaptureServiceShadow::class,
    AndroidWindowCaptureTest.InfoShadow::class, AndroidLiveNodePortTest.NodeShadow::class,
    AndroidLiveNodePortTest.WindowShadow::class])
class AndroidWindowCaptureTest {
    @Implements(AccessibilityService::class)
    class CaptureServiceShadow : AndroidLiveNodePortTest.ServiceShadow() {
        companion object { var callback: AccessibilityService.TakeScreenshotCallback? = null }
        @Implementation protected fun getServiceInfo() = AccessibilityServiceInfo()
        @Implementation protected fun takeScreenshotOfWindow(windowId: Int, executor: Executor,
            next: AccessibilityService.TakeScreenshotCallback) {
            assertEquals(42, windowId)
            callback = next
        }
    }

    @Implements(AccessibilityServiceInfo::class)
    class InfoShadow {
        @Implementation protected fun getCapabilities() = AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT
    }

    private lateinit var service: ContinuumScreenObservationService
    private var acquiredAt = 0L
    private var revision = 0L

    @Before fun setup() {
        service = Robolectric.buildService(ContinuumScreenObservationService::class.java).get()
        AndroidLiveNodePortTest.ServiceShadow.ownPackage = service.packageName
        AndroidLiveNodePortTest.ServiceShadow.overlay = false
        AndroidLiveNodePortTest.ServiceShadow.target = AccessibilityNodeInfo.obtain().apply {
            packageName = "com.whatsapp"; isVisibleToUser = true; isEnabled = true
            setBoundsInScreen(Rect(620, 60, 1180, 900))
        }
        ScreenObservationBus.connect(service)
        assertTrue(AndroidLiveComputer.startWindowCapture(service, "capture", "com.whatsapp", true).started)
        shadowOf(Looper.getMainLooper()).idle()
        acquiredAt = SystemClock.uptimeMillis()
        revision = ScreenObservationBus.revision()
        assertNotNull(CaptureServiceShadow.callback)
    }

    @After fun cleanup() {
        AndroidLiveComputer.stop()
        ScreenObservationBus.disconnect(service)
        AndroidLiveNodePortTest.ServiceShadow.target = null
        CaptureServiceShadow.callback = null
    }

    private fun deliver(callback: AccessibilityService.TakeScreenshotCallback = CaptureServiceShadow.callback!!): HardwareBuffer {
        val buffer = HardwareBuffer.create(8, 8, HardwareBuffer.RGBA_8888, 1, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)
        val result = ReflectionHelpers.callConstructor(AccessibilityService.ScreenshotResult::class.java,
            ClassParameter.from(HardwareBuffer::class.java, buffer),
            ClassParameter.from(ColorSpace::class.java, ColorSpace.get(ColorSpace.Named.SRGB)),
            ClassParameter.from(Long::class.javaPrimitiveType, acquiredAt))
        callback.onSuccess(result)
        return buffer
    }

    @Test fun callbackKeepsOriginalRevisionAndActualCaptureTime() {
        ShadowSystemClock.advanceBy(Duration.ofMillis(250))
        deliver()
        val status = AndroidLiveComputer.feed.status(System.nanoTime())
        assertEquals(LiveMonitorState.LIVE, status.state)
        assertEquals(revision, status.meta!!.observationRevision)
        assertEquals(acquiredAt * 1_000_000, status.meta.frameTimeNanos)
        assertEquals(250L, status.meta.captureAgeMillis)
        assertTrue(status.ageMillis!! >= 250)
    }

    @Test fun newerTreeRevisionRejectsCapturedPixels() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.packageName = "com.whatsapp"
        try { service.onAccessibilityEvent(event) } finally { event.recycle() }
        assertTrue(deliver().isClosed)
        assertNotEquals(LiveMonitorState.LIVE, AndroidLiveComputer.feed.status(System.nanoTime()).state)
        assertNull(AndroidLiveComputer.feed.acquire())
    }

    @Test fun latePreviousSessionCannotPublishOrPauseReplacement() {
        val old = CaptureServiceShadow.callback!!
        val next = AndroidLiveComputer.startWindowCapture(service, "next", "com.whatsapp", true)
        assertTrue(next.started)
        assertTrue(deliver(old).isClosed)
        val status = AndroidLiveComputer.feed.status(System.nanoTime())
        assertEquals(next.sessionId, status.binding!!.captureSession)
        assertEquals(LiveMonitorState.WAITING, status.state)
    }

    @Test fun stopReleasesLateScreenshotInsteadOfRevivingLive() {
        val old = CaptureServiceShadow.callback!!
        AndroidLiveComputer.stop()
        assertTrue(deliver(old).isClosed)
        assertEquals(LiveMonitorState.ENDED, AndroidLiveComputer.feed.status(System.nanoTime()).state)
    }
}
