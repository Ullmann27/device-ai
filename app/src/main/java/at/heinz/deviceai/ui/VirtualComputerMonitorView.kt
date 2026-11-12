package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.View
import at.heinz.deviceai.AndroidLiveFrame
import at.heinz.deviceai.avatar3d.CockpitStageFrame
import at.heinz.deviceai.cognitive.CockpitWorkEvent
import at.heinz.deviceai.cognitive.LiveFrameBinding
import at.heinz.deviceai.cognitive.LiveMonitorFeed
import at.heinz.deviceai.cognitive.LiveMonitorState

/** Read-only display; actions use ToolGate. */
internal class VirtualComputerMonitorView(context: Context) : View(context) {
    private var feed: LiveMonitorFeed<AndroidLiveFrame>? = null
    private var bitmap: Bitmap? = null
    private var sourceSequence = -1L
    private var sourceSession = -1L
    private var sourceBinding: LiveFrameBinding? = null
    private var importedAtNanos = 0L
    private var live = false
    private var status = "Keine Bildquelle verbunden"
    private val painter = CockpitMonitorPainter()
    private var foreground = false
    private var click: CockpitWorkEvent? = null
    private var workSequence = 0L
    var frame: CockpitStageFrame? = null
        set(value) { field = value; invalidate() }
    var nativeFrameReady = false
        set(value) { field = value; invalidate() }
    private val tick = object : Runnable {
        override fun run() {
            if (!canPresent() || feed == null) return
            refresh()
            if (click != null) invalidate()
            postOnAnimation(this)
        }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = status
    }

    fun bind(value: LiveMonitorFeed<AndroidLiveFrame>?) {
        removeCallbacks(tick)
        feed = value
        click = null
        workSequence = 0
        bitmap = null
        sourceSequence = -1
        sourceSession = -1
        sourceBinding = null
        updatePlayback()
    }

    fun setForegroundActive(value: Boolean) {
        foreground = value
        updatePlayback()
    }

    private fun canPresent() = foreground && isAttachedToWindow && windowVisibility == VISIBLE && isShown

    private fun clearPixels() {
        bitmap = null
        click = null
        sourceSequence = -1
        sourceSession = -1
        sourceBinding = null
        importedAtNanos = 0L
        live = false
    }

    private fun updatePlayback() {
        removeCallbacks(tick)
        if (canPresent() && feed != null) {
            refresh()
            post(tick)
        } else {
            clearPixels()
            status = feed?.status(System.nanoTime())?.label ?: "Keine Bildquelle verbunden"
            contentDescription = "Virtueller Computer. $status"
        }
        invalidate()
    }

    fun workEvent(event: CockpitWorkEvent) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            post { workEvent(event) }
            return
        }
        val state = feed?.status(System.nanoTime()) ?: return
        val binding = state.binding ?: return
        if (event.taskId != binding.taskId || event.sessionId != binding.captureSession || event.sequence <= workSequence) return
        workSequence = event.sequence
        if (event.stage in setOf(CockpitWorkEvent.Stage.STOPPED, CockpitWorkEvent.Stage.ERROR,
                CockpitWorkEvent.Stage.PAUSED, CockpitWorkEvent.Stage.ASK_OWNER, CockpitWorkEvent.Stage.COMPLETED)) {
            click = null
            invalidate()
            return
        }
        val age = System.nanoTime() - event.occurredAtNanos
        if (event.stage == CockpitWorkEvent.Stage.DISPATCHED && event.action == CockpitWorkEvent.Action.CLICK &&
            event.target != null && age in 0..150_000_000L && event.frameSequence > 0 &&
            state.state == LiveMonitorState.LIVE && event.frameSequence == sourceSequence && sourceBinding == binding) {
            click = event
            invalidate()
        }
    }

    private fun refresh() {
        val previousBitmap = bitmap
        val previousStatus = status
        val previousLive = live
        val source = feed
        val state = source?.status(System.nanoTime())
        status = state?.label ?: "Keine Bildquelle verbunden"
        live = state?.state == LiveMonitorState.LIVE
        val session = state?.binding?.captureSession
        if (!live || session == null) {
            bitmap = null
            sourceSequence = -1
            sourceSession = -1
            sourceBinding = null
        } else if (sourceSequence != state.sequence || sourceSession != session) {
            val producer = source ?: return
            producer.acquire()?.use { lease ->
                val current = producer.status(System.nanoTime())
                if (current.state != LiveMonitorState.LIVE || current.binding != lease.binding ||
                    current.sequence != lease.sequence || Build.VERSION.SDK_INT < 29) return@use
                val wrapped = runCatching {
                    Bitmap.wrapHardwareBuffer(lease.frame.hardwareBuffer, lease.frame.colorSpace)
                }.getOrNull()
                if (wrapped != null) {
                    // Bitmap owns its native reference.
                    bitmap = wrapped
                    sourceSequence = lease.sequence
                    sourceSession = lease.binding.captureSession
                    sourceBinding = lease.binding
                    importedAtNanos = System.nanoTime()
                } else { bitmap = null; live = false; status = "Bild kann nicht dargestellt werden" }
            }
        }
        if (previousStatus != status) contentDescription = "Virtueller Computer. $status"
        if (previousBitmap !== bitmap || previousStatus != status || previousLive != live) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val geometry = frame ?: return
        val current = feed?.status(System.nanoTime())
        val currentPixels = bitmap?.takeIf { current?.state == LiveMonitorState.LIVE &&
            current.binding == sourceBinding && System.nanoTime() - importedAtNanos <=
            (sourceBinding?.staleAfterMillis ?: 0L) * 1_000_000L }
        val canDraw = currentPixels?.config != Bitmap.Config.HARDWARE || canvas.isHardwareAccelerated
        val pointer = click?.let {
            val elapsed = System.nanoTime() - it.occurredAtNanos
            if (elapsed !in 0..220_000_000L || currentPixels == null || it.frameSequence != sourceSequence ||
                current?.binding?.captureSession != it.sessionId) { click = null; null }
            else it.target?.let { target -> CockpitMonitorPainter.Pointer(target.x, target.y, elapsed / 220_000_000f) }
        }
        painter.draw(canvas, geometry, currentPixels?.takeIf { canDraw }, current?.label ?: status,
            current?.state == LiveMonitorState.LIVE && canDraw, nativeFrameReady, resources.displayMetrics.density, pointer)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePlayback()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) updatePlayback()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) updatePlayback()
    }
    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        clearPixels()
        super.onDetachedFromWindow()
    }
}
