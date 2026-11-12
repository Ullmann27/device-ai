package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.ContextWrapper
import android.content.ComponentName
import android.content.pm.ServiceInfo
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.Surface
import at.heinz.deviceai.cognitive.LiveMonitorState
import at.heinz.deviceai.cognitive.LiveFrameMeta
import at.heinz.deviceai.cognitive.LiveFrameOffer
import at.heinz.deviceai.cognitive.LiveMonitorReason
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.shadows.ShadowService
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE,
    shadows = [ContinuumLiveProjectionServiceUiTest.ServiceShadow::class,
        ContinuumLiveProjectionServiceUiTest.ObservationShadow::class,
        ContinuumLiveProjectionServiceUiTest.ProjectionManagerShadow::class,
        ContinuumLiveProjectionServiceUiTest.ProjectionShadow::class,
        ContinuumLiveProjectionServiceUiTest.DisplayShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumLiveProjectionServiceUiTest {
    @Implements(Service::class)
    class ServiceShadow : ShadowService() {
        companion object {
            val calls = mutableListOf<String>()
            var rejectPromotion = false
            var notification: Notification? = null
        }

        @Implementation
        public override fun startForeground(id: Int, value: Notification, type: Int) {
            calls += "startForeground:$id:$type"
            if (rejectPromotion) throw SecurityException("Synthetic promotion rejection")
            super.startForeground(id, value, type)
            notification = value
        }

        @Implementation
        public override fun stopSelf() {
            calls += "stopSelf"
            super.stopSelf()
        }

        @Implementation
        public override fun stopSelf(startId: Int) {
            calls += "stopSelf:$startId"
            super.stopSelf(startId)
        }

        @Implementation
        public override fun stopForeground(flags: Int) {
            calls += "stopForeground:$flags"
            super.stopForeground(flags)
        }
    }

    @Implements(AccessibilityService::class)
    class ObservationShadow : ShadowAccessibilityService() {
        @Implementation
        protected fun getServiceInfo(): AccessibilityServiceInfo = AccessibilityServiceInfo().also {
            ReflectionHelpers.setField(it, "mCapabilities", AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT)
        }
    }

    @Implements(MediaProjectionManager::class)
    class ProjectionManagerShadow {
        companion object { var creationCalls = 0 }

        @Implementation
        protected fun getMediaProjection(resultCode: Int, data: Intent): MediaProjection? {
            creationCalls++
            throw AssertionError("Unexpected projection creation: $resultCode, ${data.action}")
        }
    }

    @Implements(MediaProjection::class)
    class ProjectionShadow {
        companion object { var stopCalls = 0; var rejectStop = false }
        @Implementation protected fun unregisterCallback(callback: MediaProjection.Callback) {}
        @Implementation protected fun stop() {
            stopCalls++
            if (rejectStop) throw SecurityException("Synthetic revoked projection")
        }
    }

    @Implements(VirtualDisplay::class)
    class DisplayShadow {
        companion object { var assignments = 0; var releases = 0; var rejectSurface = false }
        @Implementation protected fun setSurface(surface: Surface?) {
            assignments++
            if (rejectSurface) throw IllegalStateException("Synthetic detached display")
        }
        @Implementation protected fun release() { releases++ }
    }

    private lateinit var application: Application
    private lateinit var observation: ContinuumScreenObservationService
    private lateinit var service: ContinuumLiveProjectionService

    @Before
    fun setup() {
        application = RuntimeEnvironment.getApplication()
        AndroidLiveComputer.stop()
        ServiceShadow.calls.clear()
        ServiceShadow.rejectPromotion = false
        ServiceShadow.notification = null
        ProjectionManagerShadow.creationCalls = 0
        ProjectionShadow.stopCalls = 0; ProjectionShadow.rejectStop = false
        DisplayShadow.assignments = 0; DisplayShadow.releases = 0; DisplayShadow.rejectSurface = false
        observation = Robolectric.buildService(ContinuumScreenObservationService::class.java).get()
        ScreenObservationBus.connect(observation)
        service = Robolectric.buildService(ContinuumLiveProjectionService::class.java).create().get()
    }

    @After
    fun cleanup() {
        AndroidLiveComputer.stop()
        ScreenObservationBus.disconnect(observation)
        service.onDestroy()
        ServiceShadow.rejectPromotion = false
        ProjectionShadow.rejectStop = false
        DisplayShadow.rejectSurface = false
    }

    private fun queuedCapture(task: String): Intent {
        val result = AndroidLiveComputer.startProjectionCapture(application, task, "com.whatsapp",
            Activity.RESULT_OK, Intent("synthetic-owner-consent"), ownerConfirmed = true)
        assertTrue(result.reason, result.started)
        assertNotNull(AndroidLiveComputer.projectionAccess(result.sessionId!!))
        return requireNotNull(shadowOf(application).nextStartedService).also {
            assertEquals(result.sessionId, it.getLongExtra(ContinuumLiveProjectionService.SESSION, 0))
            assertEquals(ContinuumLiveProjectionService::class.java.name, it.component?.className)
        }
    }

    private fun assertRejectedAfterPromotion(intent: Intent?, requested: Long) {
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(intent, 0, 7))
        assertEquals(listOf("startForeground:55:${ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION}",
            "stopSelf"), ServiceShadow.calls)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(0, ProjectionManagerShadow.creationCalls)
        val pending = requireNotNull(ServiceShadow.notification).actions.single().actionIntent
        assertTrue(pending.isImmutable)
        assertTrue(shadowOf(pending).isService)
        val stop = shadowOf(pending).savedIntent
        assertEquals("at.heinz.deviceai.STOP_LIVE_COMPUTER", stop.action)
        assertEquals(ContinuumLiveProjectionService::class.java.name, stop.component?.className)
        assertEquals(requested, stop.getLongExtra(ContinuumLiveProjectionService.SESSION, -1))
    }

    private fun offerFrame(session: Long, acquiredAtNanos: Long, captureAgeMillis: Long = 0):
        Pair<LiveFrameOffer, HardwareBuffer> {
        val buffer = HardwareBuffer.create(8, 8, HardwareBuffer.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)
        val frame = AndroidLiveFrame(buffer, ColorSpace.get(ColorSpace.Named.SRGB))
        val meta = LiveFrameMeta(session, acquiredAtNanos, 8, 8, "com.whatsapp", 42,
            ownWindowVisible = false, observationRevision = ScreenObservationBus.revision(),
            captureAgeMillis = captureAgeMillis)
        return AndroidLiveComputer.offerProjection(meta, frame) to buffer
    }

    @Test fun projectionDeliveryDoesNotRenewFrameFreshness() {
        val intent = queuedCapture("delayed-delivery")
        val session = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        val (offer, _) = offerFrame(session, System.nanoTime() - 700_000_000L)
        assertTrue(offer is LiveFrameOffer.Accepted)
        val delivered = AndroidLiveComputer.feed.status(System.nanoTime())
        assertTrue("Delivery delay belongs to frame age", delivered.meta!!.captureAgeMillis >= 700)
        assertEquals(LiveMonitorState.PAUSED,
            AndroidLiveComputer.feed.status(System.nanoTime() + 900_000_000L).state)
    }

    @Test fun alreadyStaleProjectionDeliveryReleasesBuffer() {
        val intent = queuedCapture("stale-delivery")
        val session = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        val (offer, buffer) = offerFrame(session, System.nanoTime() - 1_501_000_000L)
        assertTrue(offer is LiveFrameOffer.Refused)
        assertTrue(buffer.isClosed)
        assertNull(AndroidLiveComputer.feed.acquire())
    }

    @Test fun projectionDeliveryCannotReduceExistingCaptureAge() {
        val intent = queuedCapture("older-capture")
        val session = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        val (offer, _) = offerFrame(session, System.nanoTime() - 100_000_000L, 900)
        assertTrue(offer is LiveFrameOffer.Accepted)
        assertTrue(AndroidLiveComputer.feed.status(System.nanoTime()).meta!!.captureAgeMillis >= 900)
    }

    @Test fun futureProjectionAcquisitionReleasesBuffer() {
        val intent = queuedCapture("future-acquisition")
        val session = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        val (offer, buffer) = offerFrame(session, System.nanoTime() + 10_000_000_000L)
        assertTrue(offer is LiveFrameOffer.Refused)
        assertTrue(buffer.isClosed)
    }

    @Test fun observationInterruptEndsProjectionAndReleasesFrame() {
        val intent = queuedCapture("observation-revoked")
        val session = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        val (_, buffer) = offerFrame(session, System.nanoTime())
        observation.onInterrupt()
        assertNull(AndroidLiveComputer.projectionAccess(session))
        assertTrue(buffer.isClosed)
        val status = AndroidLiveComputer.feed.status(System.nanoTime())
        assertEquals(LiveMonitorState.ENDED, status.state)
        assertEquals(LiveMonitorReason.PERMISSION_REVOKED, status.reason)
    }

    @Test fun projectionCallbackRevocationEndsSessionAndForeground() {
        val intent = queuedCapture("projection-revoked")
        val session = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        ReflectionHelpers.setField(service, "sessionId", session)
        val (_, buffer) = offerFrame(session, System.nanoTime())
        ReflectionHelpers.getField<MediaProjection.Callback>(service, "callback").onStop()
        assertNull(AndroidLiveComputer.projectionAccess(session))
        assertTrue(buffer.isClosed)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertTrue(ServiceShadow.calls.contains("stopForeground:${Service.STOP_FOREGROUND_REMOVE}"))
    }

    @Test
    fun staleStartPromotesBeforeStoppingWithoutProjection() {
        val intent = Intent(application, ContinuumLiveProjectionService::class.java)
            .putExtra(ContinuumLiveProjectionService.SESSION, 41L)
        assertRejectedAfterPromotion(intent, 41L)
    }

    @Test
    fun nullStartPromotesBeforeStoppingWithoutProjection() {
        assertRejectedAfterPromotion(null, 0L)
    }

    @Test
    fun cancelledQueuedStartPromotesBeforeStoppingWithoutProjection() {
        val intent = queuedCapture("cancelled-capture")
        val requested = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        AndroidLiveComputer.stop()
        assertNull(AndroidLiveComputer.projectionAccess(requested))
        assertRejectedAfterPromotion(intent, requested)
    }

    @Test
    fun supersededQueuedStartKeepsReplacementCaptureBinding() {
        val oldIntent = queuedCapture("old-capture")
        val oldSession = oldIntent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        val newIntent = queuedCapture("new-capture")
        val newSession = newIntent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        assertNotEquals(oldSession, newSession)
        assertRejectedAfterPromotion(oldIntent, oldSession)
        assertNotNull(AndroidLiveComputer.projectionAccess(newSession))
    }

    @Test
    fun disconnectedObservationRejectsQueuedStartAfterPromotion() {
        val intent = queuedCapture("disconnected-capture")
        val requested = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        ScreenObservationBus.disconnect(observation)
        assertRejectedAfterPromotion(intent, requested)
        assertNull(AndroidLiveComputer.projectionAccess(requested))
    }

    @Test
    fun promotionFailureClosesAndUsesItsStartId() {
        val intent = queuedCapture("rejected-promotion")
        val requested = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        ServiceShadow.rejectPromotion = true
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(intent, 0, 23))
        assertEquals(listOf("startForeground:55:${ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION}",
            "stopForeground:${Service.STOP_FOREGROUND_REMOVE}", "stopSelf:23"), ServiceShadow.calls)
        assertEquals(23, shadowOf(service).stopSelfId)
        assertNull(AndroidLiveComputer.projectionAccess(requested))
        assertNull(ServiceShadow.notification)
        assertEquals(0, ProjectionManagerShadow.creationCalls)
    }

    @Test fun nullStartAcceptanceCannotLeaveAnActiveCapture() {
        val refused = object : ContextWrapper(application) {
            override fun startForegroundService(intent: Intent): ComponentName? = null
        }
        val result = AndroidLiveComputer.startProjectionCapture(refused, "null-start", "com.whatsapp",
            Activity.RESULT_OK, Intent("synthetic-consent"), true)
        assertFalse(result.started)
        assertEquals("SCREEN_CAPTURE_START_FAILED", result.reason)
        assertEquals(LiveMonitorState.ENDED, AndroidLiveComputer.feed.status(System.nanoTime()).state)
    }

    @Test fun deniedStartCannotLeaveAnActiveCapture() {
        val refused = object : ContextWrapper(application) {
            override fun startForegroundService(intent: Intent): ComponentName? {
                throw android.app.ForegroundServiceStartNotAllowedException("Synthetic background restriction")
            }
        }
        val result = AndroidLiveComputer.startProjectionCapture(refused, "denied-start", "com.whatsapp",
            Activity.RESULT_OK, Intent("synthetic-consent"), true)
        assertFalse(result.started)
        assertEquals(LiveMonitorState.ENDED, AndroidLiveComputer.feed.status(System.nanoTime()).state)
    }

    @Test fun projectionStopFailureStillRemovesForegroundAndStopsService() {
        val intent = queuedCapture("stop-failure")
        val session = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        ReflectionHelpers.setField(service, "sessionId", session)
        ReflectionHelpers.setField(service, "projection", Shadow.newInstanceOf(MediaProjection::class.java))
        ProjectionShadow.rejectStop = true
        service.onTaskRemoved(null)
        assertEquals(1, ProjectionShadow.stopCalls)
        assertTrue(ServiceShadow.calls.contains("stopForeground:${Service.STOP_FOREGROUND_REMOVE}"))
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertNull(AndroidLiveComputer.projectionAccess(session))
        service.onDestroy()
        assertEquals("cleanup is idempotent", 1, ProjectionShadow.stopCalls)
    }

    @Test fun resizeSurfaceFailureEndsCaptureInsideCallback() {
        val intent = queuedCapture("resize-failure")
        val session = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        ReflectionHelpers.setField(service, "sessionId", session)
        ReflectionHelpers.setField(service, "display", Shadow.newInstanceOf(VirtualDisplay::class.java))
        DisplayShadow.rejectSurface = true
        val callback = ReflectionHelpers.getField<MediaProjection.Callback>(service, "callback")
        callback.onCapturedContentResize(600, 1000)
        assertTrue(DisplayShadow.assignments > 0)
        assertEquals(1, DisplayShadow.releases)
        assertNull(ReflectionHelpers.getField<Any?>(service, "reader"))
        assertNull(AndroidLiveComputer.projectionAccess(session))
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test fun oldStopIntentCannotStopCurrentService() {
        val intent = queuedCapture("current-capture")
        val session = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        ReflectionHelpers.setField(service, "sessionId", session)
        val stop = Intent(application, ContinuumLiveProjectionService::class.java)
            .setAction("at.heinz.deviceai.STOP_LIVE_COMPUTER")
            .putExtra(ContinuumLiveProjectionService.SESSION, session - 1)
        service.onStartCommand(stop, 0, 8)
        assertNotNull(AndroidLiveComputer.projectionAccess(session))
        assertFalse(shadowOf(service).isStoppedBySelf)
        assertTrue(ServiceShadow.calls.isEmpty())
    }

    @Test fun matchingStopIntentEndsCaptureAndForeground() {
        val intent = queuedCapture("stopped-capture")
        val session = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        ReflectionHelpers.setField(service, "sessionId", session)
        val stop = Intent(application, ContinuumLiveProjectionService::class.java)
            .setAction("at.heinz.deviceai.STOP_LIVE_COMPUTER")
            .putExtra(ContinuumLiveProjectionService.SESSION, session)
        service.onStartCommand(stop, 0, 8)
        assertNull(AndroidLiveComputer.projectionAccess(session))
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertTrue(ServiceShadow.calls.contains("stopForeground:${Service.STOP_FOREGROUND_REMOVE}"))
    }
}
