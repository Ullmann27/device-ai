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
import android.graphics.PixelFormat
import android.graphics.Rect
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

    private data class Ticket(val target: LiveWindowRecord, val own: LiveWindowRecord,
                              val crop: LivePixelRect, val revision: Long, val generation: Long)

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            AndroidLiveComputer.endProjection(sessionId, LiveMonitorReason.PERMISSION_REVOKED)
            closeCapture()
            stopSelf()
        }
        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            visibilityReported = true
            visible = isVisible
            if (!visible) AndroidLiveComputer.pauseProjection(sessionId, LiveMonitorReason.SOURCE_HIDDEN)
        }
        override fun onCapturedContentResize(width: Int, height: Int) {
            capturedWidth = width
            capturedHeight = height
            if (!visibilityReported) visible = true
            protectionWindow = null
            AndroidLiveComputer.pauseProjection(sessionId, LiveMonitorReason.SOURCE_UNVERIFIED)
            updateSurface()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.getLongExtra(SESSION, 0) ?: 0
        if (intent?.action == STOP) {
            AndroidLiveComputer.endProjection(requested, LiveMonitorReason.CAPTURE_STOPPED)
            stopSelf()
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
            createNotification()
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
            // Resize callback proves the actual captured extent.
        } catch (_: Exception) {
            AndroidLiveComputer.endProjection(sessionId, LiveMonitorReason.SOURCE_UNVERIFIED)
            closeCapture()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Virtueller Computer", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 55, Intent(this, javaClass).setAction(STOP).putExtra(SESSION, sessionId),
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

    private fun newReader(width: Int, height: Int): ImageReader = ImageReader.newInstance(width, height,
        PixelFormat.RGBA_8888, 3, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE).also { created ->
        created.setOnImageAvailableListener({ onImage(it) }, handler)
    }

    private fun updateSurface() {
        if (closed || display == null) return
        val size = bufferSize(displayBounds())
        if (reader?.width == size.first && reader?.height == size.second) return
        val previous = reader
        val replacement = newReader(size.first, size.second)
        reader = replacement
        display?.surface = null
        display?.resize(size.first, size.second, resources.displayMetrics.densityDpi)
        display?.surface = replacement.surface
        retire(previous)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        protectionWindow = null
        AndroidLiveComputer.pauseProjection(sessionId, LiveMonitorReason.SOURCE_UNVERIFIED)
        updateSurface()
    }

    private fun ticket(currentReader: ImageReader): Ticket? {
        if (closed || !visible || currentReader !== reader) return null
        val (binding, access) = AndroidLiveComputer.projectionAccess(sessionId) ?: return null
        val windows = access.service.liveWindows()
        val target = LiveWindowSafety.select(windows, binding.targetPackage, binding.ownPackage) ?: return null
        val own = windows.singleOrNull { it.packageName == binding.ownPackage && it.kind == LiveWindowKind.APPLICATION } ?: return null
        val crop = ProjectionCropGeometry.crop(displayBounds(), capturedWidth, capturedHeight,
            currentReader.width, currentReader.height, target, own) ?: return null
        val now = SystemClock.elapsedRealtime()
        if (!protectionPending && (protectionWindow != target.target.id || now - protectionAt > 1_000))
            probeProtection(access, target.target.id)
        if (protectionWindow != target.target.id || now - protectionAt > 2_000) return null
        return Ticket(target.target, own, crop, ScreenObservationBus.liveRevision(access), binding.captureSession)
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
        val before = runCatching { ticket(source) }.getOrNull()
        if (before == null) {
            image.close()
            AndroidLiveComputer.pauseProjection(sessionId, LiveMonitorReason.SOURCE_UNVERIFIED)
            return
        }
        copying = true
        copyingReader = source
        val crop = before.crop
        val time = System.nanoTime()
        AndroidGpuFrameCopier.copy(image, Rect(crop.left, crop.top, crop.right, crop.bottom), gpuExecutor) { frame ->
            handler.post {
                copying = false
                copyingReader = null
                if (closed) gpuExecutor.shutdown()
                retiredReaders.toList().forEach { it.close() }
                retiredReaders.clear()
                if (frame == null) {
                    AndroidLiveComputer.pauseProjection(sessionId, LiveMonitorReason.FRAME_STALE)
                    return@post
                }
                val after = runCatching { ticket(source) }.getOrNull()
                if (System.nanoTime() - time > 750_000_000L || after == null || before.target != after.target || before.own != after.own || before.crop != after.crop ||
                    before.generation != after.generation) {
                    frame.close()
                    AndroidLiveComputer.pauseProjection(sessionId, LiveMonitorReason.SOURCE_UNVERIFIED)
                    return@post
                }
                val state = AndroidLiveComputer.projectionAccess(sessionId)
                if (state == null) { frame.close(); return@post }
                AndroidLiveComputer.offerProjection(LiveFrameMeta(sessionId, time, frame.width, frame.height,
                    state.first.targetPackage, before.target.id, ownWindowVisible = false,
                    observationRevision = before.revision), frame)
            }
        }
    }

    private fun retire(old: ImageReader?) {
        if (old == null) return
        old.setOnImageAvailableListener(null, null)
        if (old === copyingReader) retiredReaders += old else old.close()
    }

    private fun closeCapture() {
        if (closed) return
        closed = true
        visible = false
        display?.surface = null
        display?.release()
        display = null
        retire(reader)
        reader = null
        projection?.unregisterCallback(callback)
        projection?.stop()
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (!copying) gpuExecutor.shutdown()
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
