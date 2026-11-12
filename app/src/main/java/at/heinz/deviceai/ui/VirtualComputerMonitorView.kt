package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.View
import at.heinz.deviceai.AndroidLiveFrame
import at.heinz.deviceai.avatar3d.CockpitStageFrame
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
    var frame: CockpitStageFrame? = null
        set(value) { field = value; invalidate() }
    var nativeFrameReady = false
        set(value) { field = value; invalidate() }
    private val tick = object : Runnable {
        override fun run() {
            if (!foreground || !isAttachedToWindow || !isShown || feed == null) return
            refresh()
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
        bitmap = null
        sourceSequence = -1
        sourceSession = -1
        sourceBinding = null
        refresh()
        invalidate()
        if (foreground && isAttachedToWindow && value != null) post(tick)
    }

    fun setForegroundActive(value: Boolean) {
        foreground = value
        removeCallbacks(tick)
        if (!value) { bitmap = null; sourceSequence = -1; live = false; invalidate() }
        else if (isAttachedToWindow && feed != null) post(tick)
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
        painter.draw(canvas, geometry, currentPixels?.takeIf { canDraw }, current?.label ?: status,
            current?.state == LiveMonitorState.LIVE && canDraw, nativeFrameReady, resources.displayMetrics.density)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (foreground && feed != null) post(tick)
    }
    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        bitmap = null
        sourceSequence = -1
        super.onDetachedFromWindow()
    }
}
