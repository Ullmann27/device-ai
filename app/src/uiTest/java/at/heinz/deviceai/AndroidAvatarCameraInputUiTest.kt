package at.heinz.deviceai

import android.Manifest
import android.app.Application
import android.os.Looper
import at.heinz.deviceai.cognitive.CameraGazeSample
import at.heinz.deviceai.cognitive.CameraTrackingState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Android permission/lifecycle admission on a host with no camera hardware.
 * These tests do not emulate a successful face observation or device capture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class AndroidAvatarCameraInputUiTest {
    private val observations = mutableListOf<CameraGazeSample>()
    private val states = mutableListOf<CameraTrackingState>()
    private var clearCount = 0
    private lateinit var camera: AndroidAvatarCameraInput
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun setup() {
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        camera = AndroidAvatarCameraInput(app, observations::add, { clearCount++ }, states::add)
    }

    @After fun cleanup() { camera.stop() }

    @Test fun constructionAndCapabilityProbeDoNotOpenCameraOrInventObservation() {
        assertEquals(CameraTrackingState.OFF, camera.state)
        assertFalse(camera.permissionGranted())
        assertFalse(camera.hardwareSupported())
        assertTrue(observations.isEmpty())
        assertTrue(states.isEmpty())
        assertEquals(0, clearCount)
    }

    @Test fun androidPermissionWithoutOwnerGestureStaysOff() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        assertTrue(camera.permissionGranted())
        assertFalse(camera.start(false, true, 0))
        assertEquals(CameraTrackingState.OFF, camera.state)
        assertTrue(observations.isEmpty())
    }

    @Test fun ownerGestureCannotStartInBackground() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        assertFalse(camera.start(true, false, 0))
        assertEquals(CameraTrackingState.NOT_FOREGROUND, camera.state)
        assertTrue(observations.isEmpty())
    }

    @Test fun androidPermissionIsCheckedByNativeAdapter() {
        assertFalse(camera.start(true, true, 0))
        assertEquals(CameraTrackingState.PERMISSION_REQUIRED, camera.state)
        assertFalse(camera.permissionGranted())
        assertTrue(observations.isEmpty())
    }

    @Test fun absentHardwareBlocksAndDoesNotScheduleAHiddenFallback() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        assertFalse(camera.start(true, true, 0))
        shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.SECONDS)
        assertEquals(CameraTrackingState.UNSUPPORTED, camera.state)
        assertTrue(observations.isEmpty())
        assertFalse(states.contains(CameraTrackingState.TRACKING))
    }

    @Test fun pauseAndReturnClearButNeverStartAnUnrequestedSession() {
        camera.setForeground(false)
        assertEquals(CameraTrackingState.NOT_FOREGROUND, camera.state)
        val clears = clearCount
        camera.setForeground(true)
        shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.SECONDS)
        assertEquals(CameraTrackingState.NOT_FOREGROUND, camera.state)
        assertEquals(clears, clearCount)
        assertTrue(observations.isEmpty())
    }

    @Test fun repeatedCancellationIsSafeAndCallbacksStayOnMainLooper() {
        camera.stop()
        camera.stop()
        assertEquals(CameraTrackingState.OFF, camera.state)
        assertEquals(listOf(CameraTrackingState.OFF), states)
        assertEquals(2, clearCount)
        assertEquals(Looper.getMainLooper(), Looper.myLooper())
        assertTrue(observations.isEmpty())
    }

    @Test fun backgroundThreadCannotAcquireCameraLifecycleOwnership() {
        val failure = AtomicReference<Throwable?>()
        val thread = Thread {
            try { camera.start(true, true, 0) } catch (error: Throwable) { failure.set(error) }
        }
        thread.start()
        thread.join(2_000)
        assertFalse(thread.isAlive)
        assertTrue(failure.get() is IllegalStateException)
        assertEquals(CameraTrackingState.OFF, camera.state)
        assertTrue(states.isEmpty())
        assertEquals(0, clearCount)
    }
}
