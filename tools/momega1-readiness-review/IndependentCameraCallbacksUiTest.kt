package at.heinz.deviceai

import android.Manifest
import android.app.Application
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Handler
import android.os.Looper
import android.util.Range
import android.util.Size
import android.view.Surface
import at.heinz.deviceai.cognitive.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowCameraManager
import org.robolectric.shadows.ShadowCameraCharacteristics
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Actual adapter with deterministic Camera2 callbacks; verifies close requests, not physical release. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class,
    shadows=[IndependentCameraCallbacksUiTest.Manager::class,IndependentCameraCallbacksUiTest.MapShadow::class,
        IndependentCameraCallbacksUiTest.Device::class,IndependentCameraCallbacksUiTest.Session::class])
class IndependentCameraCallbacksUiTest {
    companion object {
        var opened=CountDownLatch(1)
        var openCallback=AtomicReference<CameraDevice.StateCallback?>()
        var configureCallback:CameraCaptureSession.StateCallback?=null
        var deviceCloses=0;var sessionCloses=0;var opens=0
    }
    @Implements(CameraManager::class)
    class Manager:ShadowCameraManager() {
        @Implementation fun openCamera(id:String,callback:CameraDevice.StateCallback,handler:Handler?) {
            opens++;openCallback.set(callback);opened.countDown()
        }
    }
    @Implements(StreamConfigurationMap::class)
    class MapShadow {
        @Implementation fun getOutputSizes(format:Int):Array<Size> = if(format==ImageFormat.YUV_420_888)
            arrayOf(Size(1920,1080),Size(640,480),Size(320,240)) else emptyArray()
    }
    @Implements(className="android.hardware.camera2.impl.CameraDeviceImpl")
    class Device {
        @Implementation fun createCaptureSession(outputs:List<Surface>,callback:CameraCaptureSession.StateCallback,handler:Handler?) {
            configureCallback=callback
        }
        @Implementation fun close() {deviceCloses++}
    }
    @Implements(className="android.hardware.camera2.impl.CameraCaptureSessionImpl")
    class Session {
        @Implementation fun close() {sessionCloses++}
        @Implementation fun stopRepeating() {}
    }
    private val app get()=RuntimeEnvironment.getApplication()
    private val observations=mutableListOf<CameraGazeSample>()
    private val states=mutableListOf<CameraTrackingState>()
    private val cameras=mutableListOf<AndroidAvatarCameraInput>()
    private fun camera()=AndroidAvatarCameraInput(app,observations::add,{},states::add).also {cameras+=it}
    @Before fun prepare() {
        opened=CountDownLatch(1);openCallback.set(null);configureCallback=null;deviceCloses=0;sessionCloses=0;opens=0
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        installCamera(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME)
    }
    private fun installCamera(clock:Int) {
        val characteristics=ShadowCameraCharacteristics.newCameraCharacteristics()
        val c=Shadow.extract<ShadowCameraCharacteristics>(characteristics)
        c.set(CameraCharacteristics.LENS_FACING,CameraCharacteristics.LENS_FACING_FRONT)
        c.set(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES,intArrayOf(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE))
        c.set(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT,4)
        c.set(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE,clock)
        c.set(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE,Rect(0,0,1000,1000))
        c.set(CameraCharacteristics.SENSOR_ORIENTATION,0)
        c.set(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP,Shadow.newInstanceOf(StreamConfigurationMap::class.java))
        c.set(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES,arrayOf(Range(1,60),Range(15,15),Range(30,30)))
        Shadow.extract<Manager>(app.getSystemService(CameraManager::class.java)).addCamera("front",characteristics)
    }
    @After fun clean() {cameras.forEach {it.stop()};shadowOf(Looper.getMainLooper()).idle()}
    private fun start(c:AndroidAvatarCameraInput):CameraDevice.StateCallback {
        assertTrue("Fixture must advertise supported front metadata",c.hardwareSupported())
        assertTrue(c.start(true,true,0))
        assertTrue("Actual worker must reach CameraManager.openCamera",opened.await(3,TimeUnit.SECONDS))
        return checkNotNull(openCallback.get())
    }
    private fun device()=Shadow.newInstanceOf("android.hardware.camera2.impl.CameraDeviceImpl") as CameraDevice
    private fun session()=Shadow.newInstanceOf("android.hardware.camera2.impl.CameraCaptureSessionImpl") as CameraCaptureSession
    @Test fun lateOpenedHandleAfterStopIsClosedAndCannotDeliver() {
        val c=camera();val callback=start(c)
        c.stop();callback.onOpened(device())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,deviceCloses);assertEquals(CameraTrackingState.OFF,c.state)
        assertNull(configureCallback);assertTrue(observations.isEmpty())
    }
    @Test fun lateConfiguredHandleAfterStopIsClosedAndCannotDeliver() {
        val c=camera();start(c).onOpened(device())
        val configured=checkNotNull(configureCallback)
        c.stop();configured.onConfigured(session())
        assertEquals(1,deviceCloses);assertEquals(1,sessionCloses)
        assertTrue(observations.isEmpty());assertEquals(CameraTrackingState.OFF,c.state)
    }
    @Test fun permissionRevocationDuringConfigurationClosesBothHandles() {
        val c=camera();start(c).onOpened(device())
        val configured=checkNotNull(configureCallback)
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        configured.onConfigured(session())
        assertEquals(1,deviceCloses);assertEquals(1,sessionCloses)
        assertEquals(CameraTrackingState.PERMISSION_REQUIRED,c.state);assertTrue(observations.isEmpty())
    }
    @Test fun secondAdapterCannotAcquireLeaseWhileFirstOpenIsPending() {
        val first=camera();start(first)
        val second=camera();assertFalse(second.start(true,true,0))
        assertEquals(CameraTrackingState.IN_USE,second.state);assertEquals(1,opens)
        assertEquals(CameraTrackingState.STARTING,first.state)
    }
    @Test fun staleErrorFromOldRunCannotReleaseNewRunsLease() {
        val first=camera();val stale=start(first);first.stop()
        opened=CountDownLatch(1);openCallback.set(null)
        start(first)
        stale.onError(device(),CameraDevice.StateCallback.ERROR_CAMERA_DEVICE)
        val second=camera();assertFalse(second.start(true,true,0))
        assertEquals(CameraTrackingState.IN_USE,second.state)
        assertEquals(CameraTrackingState.STARTING,first.state);assertTrue(observations.isEmpty())
    }
    @Test fun unknownSensorClockCannotBeAdmittedAsRealtimeEvidence() {
        Shadow.extract<Manager>(app.getSystemService(CameraManager::class.java)).removeCamera("front")
        installCamera(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN)
        val c=camera();assertFalse(c.hardwareSupported());assertFalse(c.start(true,true,0))
        assertEquals(CameraTrackingState.UNSUPPORTED,c.state);assertEquals(0,opens)
    }
    @Test fun returnToForegroundCannotResumeAnOldExplicitSession() {
        val c=camera();val old=start(c)
        c.setForeground(false);c.setForeground(true)
        old.onOpened(device());shadowOf(Looper.getMainLooper()).idle()
        assertEquals(CameraTrackingState.NOT_FOREGROUND,c.state)
        assertEquals(1,opens);assertEquals(1,deviceCloses);assertTrue(observations.isEmpty())
    }
    @Test fun selectedCapturePlanUsesBoundedAdvertisedBufferAndRate() {
        val c=camera()
        val plan=AndroidAvatarCameraInput::class.java.getDeclaredMethod("plan",Int::class.javaPrimitiveType).apply {isAccessible=true}.invoke(c,0)!!
        val size=plan.javaClass.getDeclaredField("size").apply {isAccessible=true}.get(plan) as Size
        val fps=plan.javaClass.getDeclaredField("fps").apply {isAccessible=true}.get(plan) as Range<*>
        assertEquals(Size(320,240),size);assertEquals(15,fps.lower);assertEquals(15,fps.upper)
        assertEquals(0,opens);assertTrue(observations.isEmpty())
    }
}
