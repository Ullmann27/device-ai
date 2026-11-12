package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
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
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE,
    shadows = [ContinuumLiveProjectionServiceUiTest.ServiceShadow::class,
        ContinuumLiveProjectionServiceUiTest.ObservationShadow::class,
        ContinuumLiveProjectionServiceUiTest.ProjectionManagerShadow::class])
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
}
