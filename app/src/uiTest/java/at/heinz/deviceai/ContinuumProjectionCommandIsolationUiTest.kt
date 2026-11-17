package at.heinz.deviceai

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Intent
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
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE,
    shadows = [ContinuumLiveProjectionServiceUiTest.ServiceShadow::class,
        ContinuumLiveProjectionServiceUiTest.ObservationShadow::class,
        ContinuumLiveProjectionServiceUiTest.ProjectionManagerShadow::class,
        ContinuumLiveProjectionServiceUiTest.ProjectionShadow::class,
        ContinuumLiveProjectionServiceUiTest.DisplayShadow::class,
        AndroidLiveNodePortTest.NodeShadow::class,
        AndroidLiveNodePortTest.WindowShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumProjectionCommandIsolationUiTest {
    private lateinit var application: Application
    private lateinit var observation: ContinuumScreenObservationService
    private lateinit var service: ContinuumLiveProjectionService

    @Before fun setup() {
        application = RuntimeEnvironment.getApplication()
        AndroidLiveComputer.stop()
        ContinuumLiveProjectionServiceUiTest.ServiceShadow.calls.clear()
        ContinuumLiveProjectionServiceUiTest.ServiceShadow.rejectPromotion = false
        ContinuumLiveProjectionServiceUiTest.ServiceShadow.notification = null
        ContinuumLiveProjectionServiceUiTest.ProjectionManagerShadow.creationCalls = 0
        ContinuumLiveProjectionServiceUiTest.ProjectionShadow.stopCalls = 0
        ContinuumLiveProjectionServiceUiTest.ProjectionShadow.rejectStop = false
        observation = Robolectric.buildService(ContinuumScreenObservationService::class.java).get()
        AndroidLiveNodePortTest.ServiceShadow.ownPackage = observation.packageName
        AndroidLiveNodePortTest.ServiceShadow.target = null
        AndroidLiveNodePortTest.ServiceShadow.overlay = false
        AndroidLiveNodePortTest.ServiceShadow.resetGeometry()
        ScreenObservationBus.connect(observation)
        service = Robolectric.buildService(ContinuumLiveProjectionService::class.java).create().get()
    }

    @After fun cleanup() {
        AndroidLiveComputer.stop()
        ScreenObservationBus.disconnect(observation)
        service.onDestroy()
        ContinuumLiveProjectionServiceUiTest.ServiceShadow.rejectPromotion = false
        AndroidLiveNodePortTest.ServiceShadow.resetGeometry()
    }

    private fun queued(task: String): Intent {
        val result = AndroidLiveComputer.startProjectionCapture(application, task, "com.whatsapp",
            Activity.RESULT_OK, Intent("synthetic-owner-consent"), ownerConfirmed = true)
        assertTrue(result.reason, result.started)
        return requireNotNull(shadowOf(application).nextStartedService)
    }

    private fun bindCurrent(intent: Intent): Long {
        val current = intent.getLongExtra(ContinuumLiveProjectionService.SESSION, 0)
        assertNotNull(AndroidLiveComputer.projectionAccess(current))
        ReflectionHelpers.setField(service, "sessionId", current)
        return current
    }

    private fun assertPreserved(current: Long, delivery: Intent?) {
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(delivery, 0, 30))
        assertNotNull(AndroidLiveComputer.projectionAccess(current))
        assertFalse(shadowOf(service).isStoppedBySelf)
        assertFalse(ReflectionHelpers.getField<Boolean>(service, "closed"))
        assertTrue(ContinuumLiveProjectionServiceUiTest.ServiceShadow.calls.isEmpty())
        assertEquals(0, ContinuumLiveProjectionServiceUiTest.ProjectionManagerShadow.creationCalls)
    }

    @Test fun staleStartCannotStopAlreadyBoundCapture() {
        val stale = queued("stale")
        val current = bindCurrent(queued("current"))
        assertPreserved(current, stale)
    }

    @Test fun nullStartCannotStopAlreadyBoundCapture() {
        val current = bindCurrent(queued("current-null"))
        assertPreserved(current, null)
    }

    @Test fun duplicateStartCannotRepromoteOrReuseConsent() {
        val intent = queued("current-duplicate")
        val current = bindCurrent(intent)
        ContinuumLiveProjectionServiceUiTest.ServiceShadow.rejectPromotion = true
        assertPreserved(current, intent)
    }

    @Test fun staleStopStillCannotStopAlreadyBoundCapture() {
        val intent = queued("current-stop")
        val current = bindCurrent(intent)
        assertPreserved(current, Intent(application, ContinuumLiveProjectionService::class.java)
            .setAction("at.heinz.deviceai.STOP_LIVE_COMPUTER")
            .putExtra(ContinuumLiveProjectionService.SESSION, current - 1))
    }

    @Test fun matchingStopStillEndsAlreadyBoundCapture() {
        val current = bindCurrent(queued("current-owner-stop"))
        service.onStartCommand(Intent(application, ContinuumLiveProjectionService::class.java)
            .setAction("at.heinz.deviceai.STOP_LIVE_COMPUTER")
            .putExtra(ContinuumLiveProjectionService.SESSION, current), 0, 31)
        assertNull(AndroidLiveComputer.projectionAccess(current))
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertTrue(ReflectionHelpers.getField<Boolean>(service, "closed"))
    }
}
