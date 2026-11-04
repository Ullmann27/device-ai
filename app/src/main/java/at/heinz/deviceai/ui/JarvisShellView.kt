package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.provider.Settings
import android.view.View

/**
 * Drop-in View that lays out [JarvisShellLayout] for its own size and draws it with [JarvisShellRenderer].
 * Jarvis Kern: add it as background/stage, put content into [content], call [playEntrance] when a command arrives.
 * Also serves as the design preview (placeholders when no content is set).
 */
class JarvisShellView(context: Context) : View(context) {
    private val painter = AndroidContinuumPainter(context.assets)
    var content: Map<JarvisShellLayout.Slot, JarvisShellRenderer.SlotContent> = emptyMap()
        set(value) { field = value; invalidate() }
    var foldRail: JarvisShellLayout.Rail = JarvisShellLayout.Rail.LABELLED
        set(value) { field = value; invalidate() }
    private var startMs = -1L

    /** Latest shell geometry (dp) – lets Kern position its own child views on the same slots. */
    var shell: JarvisShellLayout.Shell? = null
        private set

    fun playEntrance() { startMs = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }

    private fun animatorScale(): Float =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val w = width / d; val h = height / d
        if (w < 240f || h < 320f) return
        val s = JarvisShellLayout.create(w, h, resources.configuration.fontScale, foldRail).also { shell = it }
        val scale = animatorScale()
        val elapsed = if (startMs < 0) Long.MAX_VALUE / 4 else SystemClock.uptimeMillis() - startMs
        painter.begin(canvas)
        canvas.save(); canvas.scale(d, d)
        JarvisShellRenderer.draw(painter, s, content, elapsed, scale)
        canvas.restore()
        val total = JarvisMotion.groupDurationMs(JarvisMotion.Presets.panelLeft, s.boxes.size + s.tileBoxes().size, animatorScale = scale)
        if (startMs >= 0 && elapsed < total) postInvalidateOnAnimation()
    }
}
