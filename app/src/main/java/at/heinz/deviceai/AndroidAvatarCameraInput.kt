package at.heinz.deviceai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.media.Image
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Range
import android.util.Size
import at.heinz.deviceai.cognitive.AvatarCameraPolicy
import at.heinz.deviceai.cognitive.CameraFaceBox
import at.heinz.deviceai.cognitive.CameraFrameGeometry
import at.heinz.deviceai.cognitive.CameraGazeSample
import at.heinz.deviceai.cognitive.CameraTrackingState
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Foreground camera, explicit session purposes.
 * Default: ephemeral position metadata only.
 * Templates require separate explicit opt-in.
 * Pixel ownership transfers exactly once. */
internal class AndroidAvatarCameraInput(
    context: Context,
    private val onObservation: (CameraGazeSample) -> Unit,
    private val onClear: () -> Unit,
    private val onState: (CameraTrackingState) -> Unit,
    private val onFaceFrame: ((OwnerFaceFrame) -> Unit)? = null
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val policy = AvatarCameraPolicy(SystemClock::elapsedRealtime)
    private val current = AtomicReference<Run?>(null)
    private val deliveryGeneration = AtomicLong()
    @Volatile private var ownerEnabled = false
    @Volatile private var foreground = false
    private var lastState: CameraTrackingState? = null
    private var gazeDelivered = false
    val state: CameraTrackingState get() = policy.state

    private data class Plan(val id: String, val geometry: CameraFrameGeometry,
                            val size: Size, val fps: Range<Int>, val faceMode: Int)
    private class Run(val token: Long, val delivery: Long, val plan: Plan, val ownerFaceToken: Long) {
        val lock = Any()
        val thread = HandlerThread("continuum-camera-position")
        var reader: ImageReader? = null
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var closed = false
        var lastFrameNumber = -1L
        var lastFaceFrameAt = -1L
    }

    fun permissionGranted(): Boolean = app.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    fun hardwareSupported(): Boolean = plan(0, false) != null

    /** This method never requests Android permission or sets owner opt-in itself. */
    fun start(ownerOptIn: Boolean, isForeground: Boolean, displayRotationDegrees: Int,
              analyzeOwnerFace: Boolean = false, ownerFaceSessionToken: Long = 0): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        stop()
        ownerEnabled = ownerOptIn; foreground = isForeground
        if (analyzeOwnerFace && (onFaceFrame == null || ownerFaceSessionToken <= 0)) {
            policy.stop(CameraTrackingState.ERROR)
            publishState(deliveryGeneration.get())
            return false
        }
        val selected = plan(displayRotationDegrees, analyzeOwnerFace)
        val token = policy.start(ownerOptIn, isForeground, permissionGranted(), selected != null)
        if (token == null || selected == null) { publishState(deliveryGeneration.get()); return false }
        if (!lease.compareAndSet(null, this)) {
            policy.stop(CameraTrackingState.IN_USE); publishState(deliveryGeneration.get()); return false
        }
        val run = Run(token, deliveryGeneration.get(), selected, if (analyzeOwnerFace) ownerFaceSessionToken else 0)
        current.set(run)
        val worker = try { run.thread.start(); Handler(run.thread.looper) }
            catch (_: Exception) { finish(run, CameraTrackingState.ERROR); return false }
        publishState(run.delivery)
        main.post(watchdog)
        worker.post {
            try {
                if (!permitted(run)) { finish(run, gateReason()); return@post }
                val reader = ImageReader.newInstance(selected.size.width, selected.size.height, ImageFormat.YUV_420_888, 2)
                synchronized(run.lock) {
                    if (run.closed) { reader.close(); return@post }
                    run.reader = reader
                }
                reader.setOnImageAvailableListener({ source ->
                    var image: Image? = null
                    try {
                        image = source.acquireLatestImage()
                        if (run.ownerFaceToken > 0) image?.let { deliverOwnerFaceFrame(run, it) }
                    } catch (_: Exception) {
                        if (run.ownerFaceToken > 0) finish(run, CameraTrackingState.ERROR)
                    } finally { image?.close() }
                }, worker)
                val manager = app.getSystemService(CameraManager::class.java)
                    ?: throw IllegalStateException("CAMERA_SERVICE_UNAVAILABLE")
                if (!permitted(run)) { finish(run, gateReason()); return@post }
                manager.openCamera(selected.id, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        synchronized(run.lock) {
                            if (run.closed) { camera.close(); return }
                            run.device = camera
                        }
                        if (!permitted(run)) { finish(run, gateReason()); return }
                        try {
                            @Suppress("DEPRECATION")
                            camera.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    synchronized(run.lock) {
                                        if (run.closed) { session.close(); return }
                                        run.session = session
                                    }
                                    if (!permitted(run)) { finish(run, gateReason()); return }
                                    try {
                                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                            addTarget(reader.surface)
                                            set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, selected.faceMode)
                                            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, selected.fps)
                                        }.build()
                                        session.setRepeatingRequest(request, object : CameraCaptureSession.CaptureCallback() {
                                            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                                if (!permitted(run)) { finish(run, gateReason()); return }
                                                if (result.frameNumber <= run.lastFrameNumber) return
                                                run.lastFrameNumber = result.frameNumber
                                                val faces = result.get(CaptureResult.STATISTICS_FACES).orEmpty().take(9).map { face ->
                                                    val rect = face.bounds
                                                    CameraFaceBox(rect.left, rect.top, rect.right, rect.bottom, face.score)
                                                }
                                                // plan() requires REALTIME timestamps: these share
                                                // SystemClock.elapsedRealtimeNanos' clock domain.
                                                val sensorNanos = result.get(CaptureResult.SENSOR_TIMESTAMP)
                                                val observedAt = if (sensorNanos == null || sensorNanos < 0) -1L else sensorNanos / 1_000_000L
                                                val observed = policy.acceptFrame(run.token, faces, selected.geometry, observedAt)
                                                if (!policy.isCurrent(run.token)) { finish(run, CameraTrackingState.ERROR); return }
                                                deliver(run, observed)
                                            }
                                        }, worker)
                                    } catch (_: SecurityException) { finish(run, CameraTrackingState.PERMISSION_REQUIRED) }
                                    catch (_: Exception) { finish(run, CameraTrackingState.ERROR) }
                                }
                                override fun onConfigureFailed(session: CameraCaptureSession) { session.close(); finish(run, CameraTrackingState.ERROR) }
                            }, main)
                        } catch (_: SecurityException) { finish(run, CameraTrackingState.PERMISSION_REQUIRED) }
                        catch (_: Exception) { finish(run, CameraTrackingState.ERROR) }
                    }
                    override fun onDisconnected(camera: CameraDevice) { camera.close(); finish(run, CameraTrackingState.ERROR) }
                    override fun onError(camera: CameraDevice, error: Int) { camera.close(); finish(run, CameraTrackingState.ERROR) }
                // Resource-creating callbacks use the process main looper. Stopping
                // the image worker while open/configure is pending must not drop a
                // late callback and leave its native device/session unclosed.
                }, main)
            } catch (_: SecurityException) { finish(run, CameraTrackingState.PERMISSION_REQUIRED) }
            catch (_: Exception) { finish(run, CameraTrackingState.ERROR) }
        }
        return true
    }

    fun setForeground(value: Boolean) {
        check(Looper.myLooper() == Looper.getMainLooper())
        foreground = value
        if (!value) stop(CameraTrackingState.NOT_FOREGROUND)
    }

    fun stop(reason: CameraTrackingState = CameraTrackingState.OFF) {
        check(Looper.myLooper() == Looper.getMainLooper())
        ownerEnabled = false
        val delivery = deliveryGeneration.incrementAndGet()
        current.getAndSet(null)?.let(::closeResources)
        lease.compareAndSet(this, null)
        main.removeCallbacks(watchdog)
        policy.stop(reason)
        clearGaze(delivery); publishState(delivery)
    }

    private fun permitted(run: Run): Boolean = current.get() === run &&
        policy.updateGate(run.token, ownerEnabled, foreground, permissionGranted())

    private fun deliverOwnerFaceFrame(run: Run, image: Image) {
        if (!permitted(run)) return
        val now = SystemClock.elapsedRealtime()
        if (run.lastFaceFrameAt >= 0 && now - run.lastFaceFrameAt < 500) return
        val observedAt = image.timestamp.takeIf { it >= 0 }?.div(1_000_000L) ?: return
        if (observedAt > now || now - observedAt > OwnerFacePolicy.MAX_FRAME_AGE_MS) return
        if (image.format != ImageFormat.YUV_420_888 || image.cropRect.left != 0 || image.cropRect.top != 0 ||
            image.cropRect.width() != image.width || image.cropRect.height() != image.height) return
        val pixels = OwnerFaceYuvCopy.copy(image.width, image.height,
            image.planes.map { OwnerFaceYuvPlane(it.buffer, it.rowStride, it.pixelStride) }) ?: return
        val frame = OwnerFaceFrame(image.width, image.height,
            (run.plan.geometry.sensorOrientationDegrees + run.plan.geometry.displayRotationDegrees) % 360,
            observedAt, run.ownerFaceToken, pixels)
        run.lastFaceFrameAt = now
        if (!permitted(run)) { frame.close(); return }
        try {
            val consumer = onFaceFrame
            if (consumer == null) frame.close() else consumer(frame)
        } catch (_: Exception) {
            frame.close()
            finish(run, CameraTrackingState.ERROR)
        }
    }

    private fun gateReason(): CameraTrackingState = when {
        !ownerEnabled -> CameraTrackingState.OFF
        !foreground -> CameraTrackingState.NOT_FOREGROUND
        !permissionGranted() -> CameraTrackingState.PERMISSION_REQUIRED
        else -> CameraTrackingState.ERROR
    }

    private fun finish(run: Run, reason: CameraTrackingState) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { finish(run, reason) }; return
        }
        if (current.compareAndSet(run, null)) {
            val delivery = deliveryGeneration.incrementAndGet()
            ownerEnabled = false; policy.stop(reason)
            main.removeCallbacks(watchdog)
            closeResources(run)
            lease.compareAndSet(this, null)
            clearGaze(delivery); publishState(delivery)
        } else closeResources(run)
    }

    private fun closeResources(run: Run) {
        synchronized(run.lock) {
            if (run.closed) return
            run.closed = true
            runCatching { run.session?.stopRepeating() }
            runCatching { run.session?.close() }; run.session = null
            runCatching { run.device?.close() }; run.device = null
            runCatching { run.reader?.setOnImageAvailableListener(null, null) }
            runCatching { run.reader?.close() }; run.reader = null
            run.thread.quitSafely()
        }
    }

    private fun deliver(run: Run, observed: CameraGazeSample?) {
        main.post {
            if (!permitted(run)) { finish(run, gateReason()); return@post }
            // A busy UI queue cannot resurrect an older position after a fresh clear.
            if (policy.latest() != observed) return@post
            if (observed == null) clearGaze(run.delivery)
            else { gazeDelivered = true; runCatching { onObservation(observed) } }
            publishState(run.delivery)
        }
    }

    private fun clearGaze(delivery: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { clearGaze(delivery) }; return }
        if (delivery != deliveryGeneration.get()) return
        gazeDelivered = false; runCatching(onClear)
    }

    private fun publishState(delivery: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { publishState(delivery) }; return }
        if (delivery != deliveryGeneration.get()) return
        val actual = policy.state
        if (lastState != actual) { lastState = actual; runCatching { onState(actual) } }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            val run = current.get() ?: return
            if (!permitted(run)) { finish(run, gateReason()); return }
            if (policy.expireHeartbeat(run.token)) { finish(run, CameraTrackingState.ERROR); return }
            if (gazeDelivered && policy.latest() == null) { clearGaze(run.delivery); publishState(run.delivery) }
            main.postDelayed(this, 200)
        }
    }

    private fun plan(displayRotationDegrees: Int, analyzeOwnerFace: Boolean): Plan? = runCatching {
        if (displayRotationDegrees !in listOf(0, 90, 180, 270)) return null
        val manager = app.getSystemService(CameraManager::class.java) ?: return null
        for (id in manager.cameraIdList.sorted().take(8)) {
            val c = manager.getCameraCharacteristics(id)
            if (c.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_FRONT) continue
            val hasFaceMetadata = c.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
                ?.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE) == true &&
                (c.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT) ?: 0) >= 1
            if (!analyzeOwnerFace && !hasFaceMetadata) continue
            if (c.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) !=
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) continue
            val sensor = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: continue
            val orientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: continue
            if (orientation !in listOf(0, 90, 180, 270)) continue
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
            val size = map.getOutputSizes(ImageFormat.YUV_420_888)?.filter { it.width in 2..640 && it.height in 2..480 &&
                it.width % 2 == 0 && it.height % 2 == 0 }
                ?.minByOrNull { abs(it.width * it.height - 320 * 240) } ?: continue
            val fps = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?.filter { it.lower >= 1 && it.upper <= 30 }
                ?.minByOrNull { abs(it.upper - 15) * 100 + abs(it.lower - 15) } ?: continue
            return Plan(id, CameraFrameGeometry(sensor.left, sensor.top, sensor.right, sensor.bottom, orientation, displayRotationDegrees),
                size, fps, if (hasFaceMetadata) CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE else CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF)
        }
        null
    }.getOrNull()

    private companion object { val lease = AtomicReference<AndroidAvatarCameraInput?>(null) }
}
