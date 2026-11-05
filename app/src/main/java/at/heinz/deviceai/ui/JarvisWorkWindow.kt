package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import at.heinz.deviceai.ui.JarvisDesign.Colors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/** In-cockpit content, shell frames only. */
class JarvisWorkWindow(
    context: Context,
    private val frameSource: (((Long, Float) -> Boolean) -> AutoCloseable),
    private val requestFrame: () -> Unit
) : FrameLayout(context) {
    private enum class Phase { CLOSED, OPENING, OPEN, CLOSING }
    private val density = resources.displayMetrics.density
    private val origin = RectF()
    private val destination = RectF()
    private var protectedBounds = emptyList<RectF>()
    private var phase = Phase.CLOSED
    private var disposed = false
    private var subscription: AutoCloseable? = null
    private var generation = 0L
    private var deliveringFrame = false
    private var pendingCleanup: Runnable? = null
    private var startedAt: Long? = null
    private var lastFrameTime: Long? = null
    private var openness = 0f
    private var opacity = 0f
    private var closeFrom = 0f
    private var closeAlpha = 0f
    private var trailProgress = 0f
    private var trailAlpha = 0f
    private var route = emptyList<PointF>()
    private var routeLength = 0f
    private var edge = Fill.Linear(0f, 0f, 1f, 1f,
        intArrayOf(Colors.cyan, Colors.cardRimEnd, Colors.violet, Colors.magenta))
    private var onOpened: () -> Unit = {}
    private var onClosed: () -> Unit = {}
    private val painter = AndroidContinuumPainter(context.assets)
    private val opening = JarvisMotion.Presets.widget(JarvisMotion.Direction.DEPTH)
        .copy(distanceDp = 0f, fromScale = 0f)
    private val closing = JarvisMotion.FlyIn(JarvisMotion.Direction.DEPTH, 0f, 0f,
        durationMs = JarvisMotion.Durations.LASER, edgeFlashMs = 0L,
        easing = JarvisMotion.Easing.easeInOutCubic)

    private val titleView = TextView(context).apply {
        setTextColor(Colors.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }
    private val closeView = TextView(context).apply {
        tag = "jarvis-work-window:close"
        text = "SCHLIESSEN"
        contentDescription = "Arbeitsfenster schließen"
        setTextColor(Colors.cyanSoft)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        gravity = Gravity.CENTER
        minHeight = dp(48f)
        minWidth = dp(104f)
        setPadding(dp(8f), 0, dp(8f), 0)
        isFocusable = true
        setOnClickListener { close() }
    }
    private val scroll = ScrollView(context).apply {
        tag = "jarvis-work-window:scroll"
        isFillViewport = false
        clipToPadding = true
    }
    private val panel = GlassPanel(context).apply {
        tag = "jarvis-work-window:panel"
        orientation = LinearLayout.VERTICAL
        isClickable = true
        setPadding(dp(8f), dp(4f), dp(8f), dp(8f))
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48f)
            addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
            addView(closeView, LinearLayout.LayoutParams(-2, -2))
        }
        addView(header, LinearLayout.LayoutParams(-1, -2))
        addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        pivotX = 0f
        pivotY = 0f
    }

    val isShowing: Boolean get() = phase != Phase.CLOSED

    init {
        tag = "jarvis-work-window"
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        visibility = GONE
        addView(panel)
    }

    /** Rectangles use local overlay pixels. */
    fun open(body: View, origin: RectF, destination: RectF, title: String,
             protectedBounds: List<RectF> = emptyList(),
             onOpened: () -> Unit = {}, onClosed: () -> Unit = {}, accent: Int = Colors.cyan): Boolean {
        if (disposed || deliveringFrame || isInLayout || !JarvisWorkWindowGeometry.valid(origin) ||
            !JarvisWorkWindowGeometry.valid(destination)) return false
        val pixels = RectF(ceil(destination.left), ceil(destination.top), floor(destination.right), floor(destination.bottom))
        if (pixels.width() < 200f * density || pixels.height() < 160f * density ||
            protectedBounds.any { !JarvisWorkWindowGeometry.finite(it) ||
                (JarvisWorkWindowGeometry.valid(it) && RectF.intersects(it, pixels)) } ||
            (body.parent != null && body.parent !== scroll) ||
            (width > 0 && height > 0 && !RectF(0f, 0f, width.toFloat(), height.toFloat()).contains(pixels))) return false
        generation++
        pendingCleanup?.let { removeCallbacks(it) }
        pendingCleanup = null
        this.onClosed = {}
        close(immediate = true)
        scroll.removeAllViews()
        this.origin.set(origin)
        this.destination.set(pixels)
        this.protectedBounds = protectedBounds.filter(JarvisWorkWindowGeometry::valid).map(::RectF)
        this.onOpened = onOpened
        this.onClosed = onClosed
        titleView.text = title
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        scroll.scrollTo(0, 0)
        panel.layoutParams = LayoutParams(pixels.width().toInt(), pixels.height().toInt()).apply {
            leftMargin = pixels.left.toInt()
            topMargin = pixels.top.toInt()
        }
        edge = Fill.Linear(origin.centerX() / density, origin.centerY() / density,
            pixels.centerX() / density, pixels.centerY() / density,
            intArrayOf(accent, Colors.cardRimEnd, Colors.violet, Colors.magenta))
        phase = Phase.OPENING
        startedAt = null
        lastFrameTime = null
        openness = 0f
        opacity = 0f
        trailProgress = 0f
        trailAlpha = 0f
        visibility = VISIBLE
        applyFrame()
        updateRoute()
        val handle = frameSource(::onFrame)
        if (isShowing && !disposed) subscription = handle else handle.close()
        requestFrame()
        return true
    }

    fun close(immediate: Boolean = false) {
        if (!isShowing) {
            if (immediate && !deliveringFrame && !isInLayout) pendingCleanup?.run()
            return
        }
        if (immediate || openness <= 0f) { finishClose(); return }
        if (phase == Phase.CLOSING) return
        phase = Phase.CLOSING
        closeFrom = openness
        closeAlpha = opacity
        startedAt = lastFrameTime
        onOpened = {}
        requestFrame()
    }

    fun dispose() {
        disposed = true
        close(immediate = true)
    }

    private fun onFrame(nowMs: Long, animatorScale: Float): Boolean {
        val previous = deliveringFrame
        deliveringFrame = true
        return try { updateFrame(nowMs, animatorScale) } finally { deliveringFrame = previous }
    }

    private fun updateFrame(nowMs: Long, animatorScale: Float): Boolean {
        if (!isShowing) return false
        lastFrameTime = nowMs
        if (phase == Phase.OPEN) return false
        val start = startedAt ?: nowMs.also { startedAt = it }
        val elapsed = (nowMs - start).coerceAtLeast(0L)
        val state = JarvisMotion.frame(if (phase == Phase.CLOSING) closing else opening, elapsed, animatorScale)
        if (phase == Phase.CLOSING) {
            openness = closeFrom * (1f - state.scale)
            opacity = closeAlpha * (1f - state.alpha)
            if (state.done) { finishClose(); return false }
        } else {
            openness = state.scale
            opacity = state.alpha
        }
        trailProgress = JarvisMotion.laser(elapsed, animatorScale)
        trailAlpha = if (phase == Phase.CLOSING) openness else 1f - openness
        panel.edgeFlash = state.glow
        applyFrame()
        if (state.done && phase == Phase.OPENING) {
            phase = Phase.OPEN
            val callback = onOpened
            onOpened = {}
            callback()
        }
        return phase == Phase.OPENING || phase == Phase.CLOSING
    }

    private fun applyFrame() {
        val params = panel.layoutParams as LayoutParams
        if (openness >= 1f) {
            panel.translationX = 0f
            panel.translationY = 0f
            panel.scaleX = 1f
            panel.scaleY = 1f
        } else {
            panel.translationX = origin.left + (destination.left - origin.left) * openness - params.leftMargin
            panel.translationY = origin.top + (destination.top - origin.top) * openness - params.topMargin
            panel.scaleX = (origin.width() + (destination.width() - origin.width()) * openness) / params.width
            panel.scaleY = (origin.height() + (destination.height() - origin.height()) * openness) / params.height
        }
        panel.alpha = opacity
        panel.invalidate()
        invalidate()
    }

    private fun finishClose() {
        phase = Phase.CLOSED
        val handle = subscription
        subscription = null
        handle?.close()
        visibility = GONE
        route = emptyList()
        openness = 0f
        opacity = 0f
        trailAlpha = 0f
        startedAt = null
        lastFrameTime = null
        onOpened = {}
        val callback = onClosed
        onClosed = {}
        val body = scroll.getChildAt(0)
        val expectedGeneration = generation
        lateinit var release: Runnable
        release = Runnable {
            if (pendingCleanup !== release || generation != expectedGeneration) return@Runnable
            pendingCleanup = null
            removeCallbacks(release)
            if (body?.parent === scroll) scroll.removeView(body)
            callback()
        }
        pendingCleanup = release
        if (deliveringFrame || isInLayout) post(release) else release.run()
    }

    private fun updateRoute() {
        route = JarvisWorkWindowGeometry.route(origin, destination,
            RectF(0f, 0f, width.toFloat(), height.toFloat()), protectedBounds, 8f * density)
        routeLength = route.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isShowing) return
        if ((oldw > 0 && oldh > 0 && (w != oldw || h != oldh)) ||
            !RectF(0f, 0f, w.toFloat(), h.toFloat()).contains(destination)) close(immediate = true)
        else updateRoute()
    }

    override fun onDetachedFromWindow() {
        close(immediate = true)
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!isShowing || (event.actionMasked == MotionEvent.ACTION_DOWN &&
                protectedBounds.any { it.contains(event.x, event.y) })) return false
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!isShowing) return
        val save = canvas.save()
        protectedBounds.forEach { canvas.clipOutRect(it) }
        drawTrail(canvas)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    private fun drawTrail(canvas: Canvas) {
        if (trailAlpha <= 0f || trailProgress <= 0f || routeLength <= 0f) return
        val save = canvas.save()
        canvas.scale(density, density)
        painter.begin(canvas)
        val points = if (phase == Phase.CLOSING) route.asReversed() else route
        var remaining = routeLength * trailProgress
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val length = hypot(b.x - a.x, b.y - a.y)
            if (length <= 0f) continue
            val progress = (remaining / length).coerceIn(0f, 1f)
            val x = (a.x + (b.x - a.x) * progress) / density
            val y = (a.y + (b.y - a.y) * progress) / density
            painter.drawLine(a.x / density, a.y / density, x, y, 6f, edge, .08f * trailAlpha)
            painter.drawLine(a.x / density, a.y / density, x, y, 1f, edge, .85f * trailAlpha)
            remaining -= length
            if (remaining <= 0f) {
                painter.fillCircle(x, y, 1.5f, Fill.Solid(Colors.glowCore), trailAlpha)
                break
            }
        }
        canvas.restoreToCount(save)
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    private inner class GlassPanel(context: Context) : LinearLayout(context) {
        private val glass = AndroidContinuumPainter(context.assets)
        var edgeFlash = 0f
        init {
            setWillNotDraw(false)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 14f * density)
                }
            }
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val save = canvas.save()
            canvas.scale(density, density)
            glass.begin(canvas)
            val right = width / density - 1f
            val bottom = height / density - 1f
            JarvisReferenceHud.surface(glass, JarvisShellLayout.Box(1f, 1f, right, bottom), 14f,
                Colors.panelRim, Colors.violetSoft)
            if (edgeFlash > 0f) glass.strokeRoundRect(1f, 1f, right, bottom, 14f, 1f,
                Fill.Solid(Colors.cyanSoft), .35f * edgeFlash)
            canvas.restoreToCount(save)
        }
    }
}
