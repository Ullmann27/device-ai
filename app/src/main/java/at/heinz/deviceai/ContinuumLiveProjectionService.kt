package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.hardware.DataSpace
import android.hardware.HardwareBuffer
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.annotation.RequiresApi
import at.heinz.deviceai.cognitive.*
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** OS-consented default display, GPU-cropped to the visible foreign window. */
@RequiresApi(34)
class ContinuumLiveProjectionService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val gpuExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ContinuumCaptureFence").apply { isDaemon = true }
    }
    private var sessionId = 0L
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var copyingReader: ImageReader? = null
    private val retiredReaders = mutableSetOf<ImageReader>()
    private var copying = false
    private var closed = false
    private var visible = false
    private var visibilityReported = false
    private var capturedWidth = 0
    private var capturedHeight = 0
    private var protectionWindow: Int? = null
    private var protectionAt = 0L
    private var protectionPending = false
    private var sourceResetRequired = true
    private var sourceGeometry: Geometry? = null

    private data class Geometry(val target: LiveWindowRecord, val own: LiveWindowRecord,
                                val crop: LivePixelRect, val windowRevision: Long)

    private data class Ticket(val target: LiveWindowRecord, val own: LiveWindowRecord,
                              val crop: LivePixelRect, val viewport: LiveTargetBounds,
                              val revision: Long, val generation: Long)

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            AndroidLiveComputer.endProjection(sessionId, LiveMonitorReason.PERMISSION_REVOKED)
            closeCapture()
            stopSelf()
        }
        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            if (closed) return
            visibilityReported = true
            visible = isVisible
            LiveCaptureDiagnostics.record(sessionId, if (isVisible) LiveCaptureDiagnostics.Event.CAPTURED_CONTENT_VISIBLE
                else LiveCaptureDiagnostics.Event.CAPTURED_CONTENT_HIDDEN)
            if (!visible) {
                sourceResetRequired = true
                AndroidLiveComputer.pauseProjection(sessionId, LiveMonitorReason.SOURCE_HIDDEN)
            }
        }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (closed) return
            capturedWidth = width
            capturedHeight = height
            if (!visibilityReported) visible = true
            protectionWindow = null
            sourceResetRequired = true
            AndroidLiveComputer.pauseProjection(sessionId, LiveMonitorReason.SOURCE_UNVERIFIED)
            try { updateSurface() } catch (_: Exception) { failCapture() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.getLongExtra(SESSION, 0) ?: 0
        if (intent?.action == STOP) {
            if (sessionId == 0L || requested == sessionId) {
                AndroidLiveComputer.endProjection(requested, LiveMonitorReason.CAPTURE_STOPPED)
                closeCapture()
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        try {
            createNotification(requested)
        } catch (_: Exception) {
            AndroidLiveComputer.endProjection(requested, LiveMonitorReason.SOURCE_UNVERIFIED)
            closeCapture()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (requested == sessionId && sessionId != 0L && !closed) return START_NOT_STICKY
        if (sessionId != 0L || requested == 0L || AndroidLiveComputer.projectionAccess(requested) == null) {
            AndroidLiveComputer.endProjection(requested, LiveMonitorReason.SOURCE_UNVERIFIED)
            stopSelf()
            return START_NOT_STICKY
        }
        sessionId = requested
        try {
            val code = intent!!.getIntExtra(RESULT_CODE, 0)
            val data = intent.getParcelableExtra(RESULT_DATA, Intent::class.java) ?: error("CAPTURE_CONSENT_MISSING")
            check(code == android.app.Activity.RESULT_OK) { "CAPTURE_CONSENT_DENIED" }
            check(AndroidLiveComputer.installProjectionStop(sessionId) { handler.post { closeCapture(); stopSelf() } }) {
                "CAPTURE_SESSION_CHANGED"
            }
            val manager = getSystemService(MediaProjectionManager::class.java)
            val granted = manager.getMediaProjection(code, data) ?: error("CAPTURE_GRANT_UNAVAILABLE")
            projection = granted
            granted.registerCallback(callback, handler)
            val bounds = displayBounds()
            val size = bufferSize(bounds)
            val captureReader = newReader(size.first, size.second)
            reader = captureReader
            display = granted.createVirtualDisplay("Continuum owner app view", size.first, size.second,
                resources.displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                captureReader.surface, null, handler)
            LiveCaptureDiagnostics.record(sessionId, LiveCaptureDiagnostics.Event.CAPTURE_SERVICE_STARTED)
            // Resize callback proves the actual captured extent.
        } catch (_: Exception) {
            AndroidLiveComputer.endProjection(sessionId, LiveMonitorReason.SOURCE_UNVERIFIED)
            closeCapture()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createNotification(captureSession: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Virtueller Computer", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 55, Intent(this, javaClass).setAction(STOP).putExtra(SESSION, captureSession),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_device_ai).setContentTitle("Virtueller Computer aktiv")
            .setContentText("Freigegebenes App-Fenster wird im Cockpit angezeigt.")
            .setOngoing(true).addAction(Notification.Action.Builder(null, "Beenden", stop).build()).build()
        startForeground(55, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun displayBounds(): LivePixelRect = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.liveRect()
    private fun bufferSize(bounds: LivePixelRect): Pair<Int, Int> {
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        val scale = minOf(1.0, 1920.0 / maxOf(width, height))
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    private fun newReader(width: Int, height: Int): ImageReader = ImageReader.Builder(width, height)
        .setDefaultHardwareBufferFormat(HardwareBuffer.RGBA_8888)
        .setDefaultDataSpace(DataSpace.DATASPACE_SRGB)
        .setMaxImages(3).setUsage(HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE).build().also { created ->
        try { created.setOnImageAvailableListener({ onImage(it) }, handler) }
        catch (error: Exception) { runCatching { created.close() }; throw error }
    }

    private fun updateSurface(force: Boolean = false) {
        if (closed || display == null) return
        val size = bufferSize(displayBounds())
        if (!force && reader?.width == size.first && reader?.height == size.second) return
        val previous = reader
        val replacement = newReader(size.first, size.second)
        reader = replacement
        try {
            display?.surface = null
            if (previous?.width != size.first || previous.height != size.second)
                display?.resize(size.first, size.second, resources.displayMetrics.densityDpi)
            display?.surface = replacement.surface
        } finally { retire(previous) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (closed) return
        protectionWindow = null
        sourceResetRequired = true
        AndroidLiveComputer.pauseProjection(sessionId, LiveMonitorReason.SOURCE_UNVERIFIED)
        try { updateSurface() } catch (_: Exception) { failCapture() }
    }

    private fun ticket(currentReader: ImageReader): Ticket? {
        if (closed || currentReader !== reader) return null
        fun unverified(reason: LiveCaptureDiagnostics.Rejection): Ticket? {
            LiveCaptureDiagnostics.reject(sessionId, reason)
            sourceResetRequired = true
            return null
        }
        if (!visible) return unverified(LiveCaptureDiagnostics.Rejection.CONTENT_NOT_VISIBLE)
        val (binding, access) = AndroidLiveComputer.projectionAccess(sessionId) ?: return unverified(LiveCaptureDiagnostics.Rejection.OBSERVATION_UNAVAILABLE)
        val windows = access.service.liveWindows()
        val target = LiveWindowSafety.select(windows, binding.targetPackage, binding.ownPackage) ?: return unverified(LiveCaptureDiagnostics.Rejection.TARGET_WINDOW_UNVERIFIED)
        val own = windows.singleOrNull { it.packageName == binding.ownPackage && it.kind == LiveWindowKind.APPLICATION } ?: return unverified(LiveCaptureDiagnostics.Rejection.OWN_WINDOW_UNVERIFIED)
        val sourceDisplay = displayBounds()
        val crop = ProjectionCropGeometry.crop(sourceDisplay, capturedWidth, capturedHeight,
            currentReader.width, currentReader.height, target, own) ?: return unverified(LiveCaptureDiagnostics.Rejection.CROP_UNVERIFIED)
        val viewport = ProjectionCropGeometry.viewport(sourceDisplay, currentReader.width, currentReader.height,
            target.target.bounds, crop) ?: return unverified(LiveCaptureDiagnostics.Rejection.CROP_UNVERIFIED)
        val now = SystemClock.elapsedRealtime()
        if (!protectionPending && (protectionWindow != target.target.id || now - protectionAt > 1_000))
            probeProtection(access, target.target.id)
        if (protectionWindow != target.target.id || now - protectionAt > 2_000) return unverified(LiveCaptureDiagnostics.Rejection.PROTECTION_UNCONFIRMED)
        val geometry = Geometry(target.target, own, crop, ScreenObservationBus.liveWindowRevision(access))
        if (sourceResetRequired || sourceGeometry != geometry) {
            sourceGeometry = geometry
            sourceResetRequired = false
            LiveCaptureDiagnostics.reject(sessionId, LiveCaptureDiagnostics.Rejection.GEOMETRY_RESET)
            // New queue excludes frames from previous geometry.
            try { updateSurface(force = true) }
            catch (_: Exception) { failCapture() }
            return null
        }
        // ticket() also runs after the GPU copy; recording here would count the same frame twice.
        return Ticket(target.target, own, crop, viewport, ScreenObservationBus.liveRevision(access), binding.captureSession)
    }

    private fun probeProtection(access: LiveObservationAccess, windowId: Int) {
        protectionPending = true
        try {
            access.service.takeScreenshotOfWindow(windowId, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    result.hardwareBuffer.close()
                    protectionPending = false
                    if (closed || AndroidLiveComputer.projectionAccess(sessionId)?.second != access) return
                    protectionWindow = windowId
                    protectionAt = SystemClock.elapsedRealtime()
                }
                override fun onFailure(errorCode: Int) {
                    protectionPending = false
                    protectionWindow = null
                    AndroidLiveComputer.pauseProjection(sessionId,
                        if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW)
                            LiveMonitorReason.SECURE_CONTENT else LiveMonitorReason.SOURCE_UNVERIFIED)
                }
            })
        } catch (_: Exception) { protectionPending = false; protectionWindow = null }
    }

    private fun onImage(source: ImageReader) {
        val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return
        if (closed || copying || !AndroidLiveComputer.wantsProjectionFrame(sessionId)) { image.close(); return }
        LiveCaptureDiagnostics.record(sessionId, LiveCaptureDiagnostics.Event.CAPTURE_FRAME_RECEIVED)
        val before = runCatching { ticket(source) }.getOrNull()
        if (before == null) {
            image.close()
            AndroidLiveComputer.pauseProjection(sessionId, LiveMonitorReason.SOURCE_UNVERIFIED)
            return
        }
        // Count one verified target per received frame, only after the PRE-copy ticket succeeded.
        // The post-copy validation must not inflate the owner's image-chain diagnostics.
        LiveCaptureDiagnostics.record(sessionId, LiveCaptureDiagnostics.Event.TARGET_WINDOW_VISIBLE)
        copying = true
        copyingReader = source
        val crop = before.crop
        val time = System.nanoTime()
        try { gpuExecutor.execute {
          AndroidGpuFrameCopier.copy(image, Rect(crop.left, crop.top, crop.right, crop.bottom), gpuExecutor) { frame ->
            handler.post {
                copying = false
                copyingReader = null
                if (closed) gpuExecutor.shutdown()
                retiredReaders.toList().forEach { runCatching { it.close() } }
                retiredReaders.clear()
                if (frame == null) {
                    LiveCaptureDiagnostics.reject(sessionId, LiveCaptureDiagnostics.Rejection.COPY_FAILED)
                    AndroidLiveComputer.pauseProjection(sessionId, LiveMonitorReason.FRAME_STALE)
                    return@post
                }
                val after = runCatching { ticket(source) }.getOrNull()
                if (System.nanoTime() - time > 750_000_000L || after == null || before.target != after.target || before.own != after.own || before.crop != after.crop ||
                    before.viewport != after.viewport || before.generation != after.generation ||
                    before.revision != after.revision) {
                    LiveCaptureDiagnostics.reject(sessionId, LiveCaptureDiagnostics.Rejection.SOURCE_CHANGED_DURING_COPY)
                    frame.close()
                    AndroidLiveComputer.pauseProjection(sessionId, LiveMonitorReason.SOURCE_UNVERIFIED)
                    return@post
                }
                val state = AndroidLiveComputer.projectionAccess(sessionId)
                if (state == null) { frame.close(); return@post }
                AndroidLiveComputer.offerProjection(LiveFrameMeta(sessionId, time, frame.width, frame.height,
                    state.first.targetPackage, before.target.id, ownWindowVisible = false,
                    observationRevision = before.revision, sourceViewport = before.viewport), frame)
            }
          }
        } } catch (_: Exception) {
            copying = false
            copyingReader = null
            runCatching { image.close() }
            failCapture()
        }
    }

    private fun retire(old: ImageReader?) {
        if (old == null) return
        if (old === copyingReader) retiredReaders += old
        runCatching { old.setOnImageAvailableListener(null, null) }
        if (old !== copyingReader) runCatching { old.close() }
    }

    private fun closeCapture() {
        if (closed) return
        closed = true
        visible = false
        protectionWindow = null
        val oldDisplay = display
        display = null
        val oldReader = reader
        reader = null
        val oldProjection = projection
        projection = null
        runCatching { oldDisplay?.surface = null }
        runCatching { oldDisplay?.release() }
        retire(oldReader)
        runCatching { oldProjection?.unregisterCallback(callback) }
        runCatching { oldProjection?.stop() }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        if (!copying) runCatching { gpuExecutor.shutdown() }
    }

    private fun failCapture() {
        try { AndroidLiveComputer.endProjection(sessionId, LiveMonitorReason.SOURCE_UNVERIFIED) }
        finally { closeCapture(); stopSelf() }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        AndroidLiveComputer.endProjection(sessionId, LiveMonitorReason.CAPTURE_STOPPED)
        closeCapture()
        stopSelf()
    }
    override fun onDestroy() {
        AndroidLiveComputer.endProjection(sessionId, LiveMonitorReason.CAPTURE_STOPPED)
        closeCapture()
        super.onDestroy()
    }

    companion object {
        const val SESSION = "capture_session"
        const val RESULT_CODE = "capture_result_code"
        const val RESULT_DATA = "capture_result_data"
        private const val CHANNEL = "continuum_live_computer"
        private const val STOP = "at.heinz.deviceai.STOP_LIVE_COMPUTER"
    }
}
