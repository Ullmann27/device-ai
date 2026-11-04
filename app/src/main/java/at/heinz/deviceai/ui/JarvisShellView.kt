package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.View

/**
 * Drop-in View that lays out [JarvisShellLayout] for its own size and draws it with [JarvisShellRenderer]
 * plus native glass ([JarvisGlassRenderer]: backdrop blur API 31+, AGSL background/rim API 33+).
 * Jarvis Kern: put content into [content] / [tiles], call [playEntrance] on a command event.
 */
class JarvisShellView(context: Context) : View(context) {
    private val painter = AndroidContinuumPainter(context.assets)
    private val textPainter = JarvisTextScalePainter(painter)
    private val glass = JarvisGlassRenderer(painter)
    /** The separate foreground layer follows this layer's invalidation without owning an animation clock. */
    var onFrameDrawn: (() -> Unit)? = null
    var content: Map<JarvisShellLayout.Slot, JarvisShellRenderer.SlotContent> = emptyMap()
        set(value) { field = value; invalidate() }
    /** Presentation model of the action tiles (label/icon/target, optional display text from Kern). */
    var tiles: List<JarvisTile> = JarvisTiles.reference
        set(value) { field = value; invalidate() }
    var foldRail: JarvisShellLayout.Rail = JarvisShellLayout.Rail.LABELLED
        set(value) { field = value; invalidate() }
    private var startMs = -1L
    private var layoutFont = 0f
    private var layoutRail: JarvisShellLayout.Rail? = null
    private var layoutInset = Float.NaN
    private var layoutViewport = Float.NaN
    private var layoutCompact = false
    var compactPresentation = false
        set(value) {
            if (field == value) return
            field = value
            shell = null
            invalidate()
        }
    /** Stable visible height, independent of this View's larger scrolling content height. */
    var viewportHeightDp = 0f
        set(value) {
            require(value.isFinite() && value >= 0f)
            if (field == value) return
            field = value
            invalidate()
        }
    /** Width already occupied by the Activity's functional rail, in window dp. */
    var contentRailInsetDp = 0f
        set(value) {
            require(value.isFinite() && value >= 0f)
            if (field == value) return
            field = value
            invalidate()
        }

    /** Optional decorative refresh. Native head/orb own their frame clock; static HUD needs no continuous redraw. */
    var ambientMotionEnabled = false
        set(value) { field = value; invalidate() }

    private fun ambientMotion(scale: Float) = ambientMotionEnabled && scale > 0f && isShown &&
        windowVisibility == VISIBLE && hasWindowFocus()

    /** Latest shell geometry (dp) – lets Kern position its own child views on the same slots. */
    var shell: JarvisShellLayout.Shell? = null
        private set

    fun playEntrance() { startMs = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }

    private fun animatorScale(): Float =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val w = width / d; val h = height / d
        if (w < 240f || h < 160f) return
        val font = resources.configuration.fontScale
        val viewport = viewportHeightDp.takeIf { it >= 160f } ?: h
        val s = shell?.takeIf { it.width == w && layoutViewport == viewport && layoutFont == font && layoutRail == foldRail && layoutInset == contentRailInsetDp && layoutCompact == compactPresentation }
            ?: JarvisShellLayout.forContent(w, viewport, font, foldRail, contentRailInsetDp,
                compactPresentation = compactPresentation).also {
                shell = it; layoutFont = font; layoutRail = foldRail; layoutInset = contentRailInsetDp; layoutViewport = viewport; layoutCompact = compactPresentation
            }
        val scale = animatorScale()
        val now = SystemClock.uptimeMillis()
        val elapsed = if (startMs < 0) Long.MAX_VALUE / 4 else now - startMs
        painter.begin(canvas); glass.begin(canvas, d, width, height)
        textPainter.fontScale = font
        canvas.save(); canvas.scale(d, d)
        JarvisShellRenderer.draw(textPainter, s, content, elapsed, scale, tiles, glass, now, deferForeground = true)
        canvas.restore()
        onFrameDrawn?.invoke()
        val total = JarvisMotion.groupDurationMs(JarvisMotion.Presets.panelLeft, s.boxes.size + s.tileBoxes().size, animatorScale = scale)
        // Invalidate only for an explicit entrance/decorative mode. Status updates invalidate through state setters.
        if ((startMs >= 0 && elapsed < total) || ambientMotion(scale)) postInvalidateOnAnimation()
        if (frameLogging) frames.onDrawn(SystemClock.elapsedRealtimeNanos() - drawStart(now))
    }

    /** Same geometry and text metrics, rendered above the transparent native avatar but below its hit targets. */
    fun drawForeground(canvas: Canvas) {
        val s = shell ?: return
        val d = resources.displayMetrics.density
        painter.begin(canvas)
        textPainter.fontScale = resources.configuration.fontScale
        val save = canvas.save()
        canvas.scale(d, d)
        JarvisShellRenderer.drawForeground(textPainter, s, content)
        canvas.restoreToCount(save)
    }

    // --- Debug: frame-time measurement (Fold7 measurement still pending on device) ---
    /** Logs frame interval stats every 120 frames under tag "JarvisFrames" while true. Debug only. */
    var frameLogging = false
        set(value) {
            field = value
            if (value) Choreographer.getInstance().postFrameCallback(frames) else frames.reset()
        }
    private fun drawStart(nowUptime: Long) = SystemClock.elapsedRealtimeNanos() - (SystemClock.uptimeMillis() - nowUptime) * 1_000_000L

    private val frames = object : Choreographer.FrameCallback {
        private var last = 0L; private var n = 0; private var sum = 0L; private var max = 0L; private var drawSum = 0L
        fun reset() { last = 0L; n = 0; sum = 0L; max = 0L; drawSum = 0L }
        fun onDrawn(ns: Long) { drawSum += ns }
        override fun doFrame(frameTimeNanos: Long) {
            if (last != 0L) { val dt = frameTimeNanos - last; sum += dt; if (dt > max) max = dt; n++ }
            last = frameTimeNanos
            if (n >= 120) {
                Log.d("JarvisFrames", "avg=%.2fms max=%.2fms onDraw=%.2fms (n=%d)".format(sum / n / 1e6, max / 1e6, drawSum / n / 1e6, n))
                n = 0; sum = 0L; max = 0L; drawSum = 0L
            }
            invalidate()
            if (frameLogging) Choreographer.getInstance().postFrameCallback(this)
        }
    }
}
