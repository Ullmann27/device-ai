package at.heinz.deviceai.ui

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
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
    private val motionFrames = linkedSetOf<(Long, Float) -> Boolean>()

    /** Shares the existing draw clock. */
    fun observeMotionFrames(listener: (Long, Float) -> Boolean): AutoCloseable {
        motionFrames.add(listener)
        invalidate()
        return AutoCloseable { motionFrames.remove(listener) }
    }
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
    var ambientMotionEnabled = true
        set(value) { field = value; refreshMotion() }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private fun motionAllowed(scale: Float) = isAttachedToWindow && scale > 0f && isShown &&
        windowVisibility == VISIBLE && hasWindowFocus() && !powerManager.isPowerSaveMode
    private fun ambientMotion(scale: Float) = ambientMotionEnabled && motionAllowed(scale)
    private val ambientFrame = Runnable {
        if (ambientMotion(animatorScale())) invalidate()
    }
    private val motionObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshMotion()
    }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshMotion()
    }
    private var motionObserverRegistered = false
    private var powerReceiverRegistered = false

    private fun refreshMotion() {
        removeCallbacks(ambientFrame)
        lastFrameStartNanos = 0L
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!motionObserverRegistered) runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionObserver)
            motionObserverRegistered = true
        }.onFailure { Log.w("JarvisMotion", "Motion observer unavailable", it) }
        if (!powerReceiverRegistered) runCatching {
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(powerReceiver, filter)
            powerReceiverRegistered = true
        }.onFailure { Log.w("JarvisMotion", "Power observer unavailable", it) }
        refreshMotion()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ambientFrame)
        if (motionObserverRegistered) {
            runCatching { context.contentResolver.unregisterContentObserver(motionObserver) }
            motionObserverRegistered = false
        }
        if (powerReceiverRegistered) {
            runCatching { context.unregisterReceiver(powerReceiver) }
            powerReceiverRegistered = false
        }
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isAttachedToWindow) refreshMotion()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) refreshMotion()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) refreshMotion()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (isAttachedToWindow) refreshMotion()
    }

    /** Latest shell geometry (dp) – lets Kern position its own child views on the same slots. */
    var shell: JarvisShellLayout.Shell? = null
        private set

    /** Airbus deck: 0 = tiles and rows in place, 1 = cleared for an open work window (see JarvisDeckClearance). */
    var deckClearance = 0f
        private set
    private var deckCleared = false
    private var deckFrameMs = -1L

    fun setDeckCleared(cleared: Boolean) {
        if (deckCleared == cleared) return
        deckCleared = cleared
        deckFrameMs = -1L
        invalidate()
    }

    /**
     * Geometry for the size the view has now (dp), from the same cache onDraw uses – so a work window can be planned
     * after a layout even before the next draw. Null while the view is too small for the shell.
     */
    fun currentShell(): JarvisShellLayout.Shell? {
        val d = resources.displayMetrics.density
        val w = width / d; val h = height / d
        if (w < 240f || h < 160f) return null
        val font = resources.configuration.fontScale
        val viewport = viewportHeightDp.takeIf { it >= 160f } ?: h
        return shell?.takeIf { it.width == w && layoutViewport == viewport && layoutFont == font && layoutRail == foldRail && layoutInset == contentRailInsetDp && layoutCompact == compactPresentation }
            ?: JarvisShellLayout.forContent(w, viewport, font, foldRail, contentRailInsetDp,
                compactPresentation = compactPresentation).also {
                shell = it; layoutFont = font; layoutRail = foldRail; layoutInset = contentRailInsetDp; layoutViewport = viewport; layoutCompact = compactPresentation
            }
    }

    fun playEntrance() { startMs = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }

    private fun animatorScale(): Float =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    override fun onDraw(canvas: Canvas) {
        val drawStartedNanos = if (frameLogging) System.nanoTime() else 0L
        val d = resources.displayMetrics.density
        val w = width / d; val h = height / d
        if (w < 240f || h < 160f) return
        val font = resources.configuration.fontScale
        val s = currentShell() ?: return
        val scale = animatorScale()
        val now = SystemClock.uptimeMillis()
        val elapsed = if (startMs < 0) Long.MAX_VALUE / 4 else now - startMs
        painter.begin(canvas); glass.begin(canvas, d, width, height)
        textPainter.fontScale = font
        canvas.save(); canvas.scale(d, d)
        val sceneTime = if (ambientMotion(scale)) now else 0L
        val deckMoving = (deckCleared && deckClearance < 1f) || (!deckCleared && deckClearance > 0f)
        if (deckMoving) {
            val frame = if (deckFrameMs < 0L) 0L else now - deckFrameMs
            deckClearance = JarvisDeckClearance.step(deckClearance, deckCleared, (frame / scale.coerceAtLeast(.05f)).toLong(),
                motionAllowed(scale))
        }
        deckFrameMs = now
        JarvisShellRenderer.draw(textPainter, s, content, elapsed, scale, tiles, glass, sceneTime, deferForeground = true,
            deckClearance = deckClearance)
        canvas.restore()
        var workWindowMoving = false
        val effectiveScale = if (motionAllowed(scale)) scale else 0f
        motionFrames.toList().forEach { listener ->
            if (listener(now, effectiveScale)) workWindowMoving = true
        }
        onFrameDrawn?.invoke()
        val total = JarvisMotion.groupDurationMs(JarvisMotion.Presets.panelLeft, s.boxes.size + s.tileBoxes().size, animatorScale = scale)
        // Invalidate only for an explicit entrance/decorative mode. Status updates invalidate through state setters.
        removeCallbacks(ambientFrame)
        val deckStillMoving = (deckCleared && deckClearance < 1f) || (!deckCleared && deckClearance > 0f)
        if (motionAllowed(scale) && (workWindowMoving || deckStillMoving || startMs >= 0 && elapsed < total)) postInvalidateOnAnimation()
        else if (ambientMotion(scale)) postDelayed(ambientFrame, 66L)
        if (frameLogging) recordFrame(drawStartedNanos, System.nanoTime())
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

    /** Observes draws without scheduling frames. */
    var frameLogging = false
        set(value) {
            if (field == value) return
            field = value
            lastFrameStartNanos = 0L
            if (value) frameSample = FrameSample()
        }

    data class FrameSample(
        val draws: Long = 0L,
        val intervals: Long = 0L,
        val intervalNanos: Long = 0L,
        val maxIntervalNanos: Long = 0L,
        val drawNanos: Long = 0L
    ) {
        val averageIntervalMs: Double? get() = if (intervals > 0) intervalNanos / intervals / 1e6 else null
        val averageDrawMs: Double? get() = if (draws > 0) drawNanos / draws / 1e6 else null
    }

    private var lastFrameStartNanos = 0L
    private var frameSample = FrameSample()
    fun sampleFrames(): FrameSample = frameSample

    private fun recordFrame(start: Long, end: Long) {
        val interval = if (lastFrameStartNanos > 0L) (start - lastFrameStartNanos).coerceAtLeast(0L) else 0L
        val before = frameSample
        frameSample = FrameSample(before.draws + 1, before.intervals + if (lastFrameStartNanos > 0L) 1 else 0,
            before.intervalNanos + interval, maxOf(before.maxIntervalNanos, interval),
            before.drawNanos + (end - start).coerceAtLeast(0L))
        lastFrameStartNanos = start
        if (frameSample.draws % 120L == 0L) {
            Log.d("JarvisFrames", "draws=%d avg=%.2fms max=%.2fms onDraw=%.2fms".format(
                java.util.Locale.ROOT, frameSample.draws, frameSample.averageIntervalMs ?: 0.0,
                frameSample.maxIntervalNanos / 1e6, frameSample.averageDrawMs ?: 0.0))
        }
    }
}
