package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
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
    private var atRest = false
    private var workspace = emptyList<String>()
    private val projection = Matrix()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val workspacePaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
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
        updateDescription()
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

    fun updateWorkspace(lines: List<String>) {
        if (workspace == lines) return
        workspace = lines.toList()
        updateDescription()
        invalidate()
    }

    private fun updateDescription() {
        contentDescription = "Virtueller Computer. $status" +
            if (feed == null) ". Arbeitsbereich. ${workspace.joinToString(". ")}" else ""
    }

    fun updateStatus(label: String, sceneReady: Boolean, cameraAtRest: Boolean = true) {
        val honest = if (!sceneReady && label.startsWith("Live", ignoreCase = true)) "3D-Bild nicht verfügbar" else label
        if (honest == status && ready == sceneReady && atRest == cameraAtRest) return
        status = honest
        ready = sceneReady
        atRest = cameraAtRest
        updateDescription()
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
        if (showWorkspace(ready, atRest, feed != null)) drawWorkspace(canvas, geometry)
        val padding = 10f * resources.displayMetrics.density
        val label = android.text.TextUtils.ellipsize(status, android.text.TextPaint(textPaint),
            (geometry.width - 2 * padding).coerceAtLeast(1f), android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(label, padding, geometry.height - padding, textPaint)
    }

    private fun drawWorkspace(canvas: Canvas, geometry: CockpitStageFrame) {
        val density = resources.displayMetrics.density
        val width = geometry.monitorWidth / density *
            (CockpitStageFrame.CONTENT_RIGHT - CockpitStageFrame.CONTENT_LEFT)
        val height = geometry.monitorHeight / density *
            (CockpitStageFrame.CONTENT_BOTTOM - CockpitStageFrame.CONTENT_TOP)
        if (width <= 24f || height <= 24f) return
        val source = floatArrayOf(0f, 0f, width, 0f, width, height, 0f, height)
        if (!projection.setPolyToPoly(source, 0, geometry.quad(), 0, 4)) return
        val save = canvas.save()
        canvas.concat(projection)
        canvas.clipRect(0f, 0f, width, height)
        fillPaint.color = 0xff071222.toInt()
        canvas.drawRect(0f, 0f, width, height, fillPaint)
        fillPaint.color = 0xff32c7e8.toInt()
        canvas.drawRect(0f, 0f, width, 1.2f, fillPaint)
        val font = maxOf(1f, resources.configuration.fontScale)
        workspacePaint.textSize = 14f * font
        workspacePaint.color = 0xffdceffa.toInt()
        fun line(text: String, baseline: Float) {
            val visible = android.text.TextUtils.ellipsize(text, workspacePaint,
                width - 20f, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(visible, 10f, baseline, workspacePaint)
        }
        var y = 10f - workspacePaint.fontMetrics.ascent
        line("Arbeitsbereich", y)
        workspacePaint.textSize = 11.5f * font
        workspacePaint.color = 0xffa9c7dc.toInt()
        val step = workspacePaint.fontSpacing + 5f
        for (text in workspace) {
            y += step
            if (y + workspacePaint.fontMetrics.descent > height - 8f) break
            line(text, y)
        }
        canvas.restoreToCount(save)
    }

    companion object {
        internal fun showWorkspace(ready: Boolean, atRest: Boolean, sourceBound: Boolean) =
            ready && atRest && !sourceBound
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
