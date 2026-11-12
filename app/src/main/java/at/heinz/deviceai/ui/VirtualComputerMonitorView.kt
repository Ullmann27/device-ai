package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import at.heinz.deviceai.AndroidLiveFrame
import at.heinz.deviceai.avatar3d.CockpitStageFrame
import at.heinz.deviceai.cognitive.CockpitWorkEvent
import at.heinz.deviceai.cognitive.LiveMonitorFeed

/** Native status; pixels belong to Filament. */
internal class VirtualComputerMonitorView(context: Context) : View(context) {
    private var feed: LiveMonitorFeed<AndroidLiveFrame>? = null
    private var foreground = false
    private var status = "Keine Bildquelle verbunden"
    private var ready = false
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(166, 218, 232)
        textSize = 12.5f * resources.displayMetrics.scaledDensity
        setShadowLayer(2f * resources.displayMetrics.density, 0f, 1f, Color.BLACK)
    }
    var frame: CockpitStageFrame? = null
        set(value) { field = value; invalidate() }
    var onFeedChanged: ((LiveMonitorFeed<AndroidLiveFrame>?) -> Unit)? = null
    var onWorkEvent: ((CockpitWorkEvent) -> Unit)? = null

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = status
    }

    fun bind(value: LiveMonitorFeed<AndroidLiveFrame>?) {
        if (feed !== value) feed?.monitorSubmission?.invalidate()
        feed = value
        updatePlayback()
    }

    fun setForegroundActive(value: Boolean) {
        foreground = value
        updatePlayback()
    }

    private fun canPresent() = foreground && isAttachedToWindow && windowVisibility == VISIBLE && isShown

    private fun updatePlayback() {
        if (!canPresent()) feed?.monitorSubmission?.invalidate()
        onFeedChanged?.invoke(feed?.takeIf { canPresent() })
        if (!canPresent()) updateStatus(feed?.status(System.nanoTime())?.label ?: "Keine Bildquelle verbunden", false)
        invalidate()
    }

    fun updateStatus(label: String, sceneReady: Boolean) {
        val honest = if (!sceneReady && label.startsWith("Live", ignoreCase = true)) "3D-Bild nicht verfügbar" else label
        if (honest == status && ready == sceneReady) return
        status = honest
        ready = sceneReady
        contentDescription = "Virtueller Computer. $honest"
        invalidate()
    }

    fun workEvent(event: CockpitWorkEvent) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            post { workEvent(event) }
            return
        }
        if (canPresent()) onWorkEvent?.invoke(event)
    }

    override fun onDraw(canvas: Canvas) {
        val geometry = frame ?: return
        val padding = 10f * resources.displayMetrics.density
        val label = android.text.TextUtils.ellipsize(status, android.text.TextPaint(textPaint),
            (geometry.width - 2 * padding).coerceAtLeast(1f), android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(label, padding, geometry.height - padding, textPaint)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updatePlayback() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) updatePlayback()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) updatePlayback()
    }
    override fun onDetachedFromWindow() {
        feed?.monitorSubmission?.invalidate()
        onFeedChanged?.invoke(null)
        ready = false
        super.onDetachedFromWindow()
    }
}
