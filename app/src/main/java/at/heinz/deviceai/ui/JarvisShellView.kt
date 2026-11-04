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
    private val glass = JarvisGlassRenderer(painter)
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

    /** Continuous camera drift / dolly / dust. Off: static composition (also off for reduced motion). */
    var ambientMotionEnabled = true
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
        if (w < 240f || h < 320f) return
        val font = resources.configuration.fontScale
        val s = shell?.takeIf { it.width == w && it.height == h && layoutFont == font && layoutRail == foldRail }
            ?: JarvisShellLayout.create(w, h, font, foldRail).also { shell = it; layoutFont = font; layoutRail = foldRail }
        val scale = animatorScale()
        val now = SystemClock.uptimeMillis()
        val elapsed = if (startMs < 0) Long.MAX_VALUE / 4 else now - startMs
        painter.begin(canvas); glass.begin(canvas, d, width, height)
        canvas.save(); canvas.scale(d, d)
        JarvisShellRenderer.draw(painter, s, content, elapsed, scale, tiles, glass, now)
        canvas.restore()
        val total = JarvisMotion.groupDurationMs(JarvisMotion.Presets.panelLeft, s.boxes.size + s.tileBoxes().size, animatorScale = scale)
        // Camera never static (drift + dolly): keep ticking while visible and animators are enabled.
        if ((startMs >= 0 && elapsed < total) || ambientMotion(scale)) postInvalidateOnAnimation()
        if (frameLogging) frames.onDrawn(SystemClock.elapsedRealtimeNanos() - drawStart(now))
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
