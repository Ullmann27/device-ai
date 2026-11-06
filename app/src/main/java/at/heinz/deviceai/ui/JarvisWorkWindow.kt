package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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

private const val EMBLEM_DP = 18f
// Per-frame fills of the trail and emblem: allocated once, never while an animation runs (Android custom-view guidance).
private val TRAIL_HEAD_FILL = Fill.Solid(Colors.glowCore)
private val EMBLEM_DISC_FILL = Fill.Solid(Colors.background)
private val EMBLEM_HALO_FILL = Fill.Solid(Colors.cyan)
private val EDGE_FLASH_FILL = Fill.Solid(Colors.cyanSoft)
/*
 * App crystal (owner brief 06.11.: the real installed app symbol as a spatial crystal/prism on the light trail): a tall
 * hexagonal prism in dp around its centre – a violet/magenta and a cyan facet, a dark glass core under the real icon, a
 * central ridge and a glint that sweeps across while the window unfolds. Paths and fills are built once (no per-frame
 * allocation); the painter caches their Android paths and shaders.
 */
private const val CRYSTAL_H = EMBLEM_DP * 1.5f
private const val CRYSTAL_W = CRYSTAL_H * .64f
private const val CRYSTAL_S = CRYSTAL_H * .46f
private val CRYSTAL_OUTLINE = VectorPath().moveTo(0f, -CRYSTAL_H).lineTo(CRYSTAL_W, -CRYSTAL_S).lineTo(CRYSTAL_W, CRYSTAL_S)
    .lineTo(0f, CRYSTAL_H).lineTo(-CRYSTAL_W, CRYSTAL_S).lineTo(-CRYSTAL_W, -CRYSTAL_S).close()
private val CRYSTAL_LEFT = VectorPath().moveTo(0f, -CRYSTAL_H).lineTo(-CRYSTAL_W, -CRYSTAL_S).lineTo(-CRYSTAL_W, CRYSTAL_S)
    .lineTo(0f, CRYSTAL_H).close()
private val CRYSTAL_RIGHT = VectorPath().moveTo(0f, -CRYSTAL_H).lineTo(CRYSTAL_W, -CRYSTAL_S).lineTo(CRYSTAL_W, CRYSTAL_S)
    .lineTo(0f, CRYSTAL_H).close()
private val CRYSTAL_LEFT_FILL = Fill.Linear(-CRYSTAL_W, -CRYSTAL_H, 0f, CRYSTAL_H, intArrayOf(Colors.violet, Colors.magenta))
private val CRYSTAL_RIGHT_FILL = Fill.Linear(0f, -CRYSTAL_H, CRYSTAL_W, CRYSTAL_H, intArrayOf(Colors.cyan, Colors.violet))
private val CRYSTAL_RIM_FILL = Fill.Linear(-CRYSTAL_W, -CRYSTAL_H, CRYSTAL_W, CRYSTAL_H,
    intArrayOf(Colors.cyan, Colors.violet, Colors.magenta))

/**
 * The work window is a layer ABOVE the cockpit (the cockpit cards sit on the background): a dark backing under its glass
 * keeps rail labels and tiles from showing through text (host capture 06.11.: ~6–9 % leakage of bright labels).
 */
private val WINDOW_BACKING_FILL = Fill.Solid(Colors.background)
private const val WINDOW_BACKING_ALPHA = .7f

/** In-cockpit content, shell frames only. */
class JarvisWorkWindow(
    context: Context,
    private val frameSource: (((Long, Float) -> Boolean) -> AutoCloseable),
    private val requestFrame: () -> Unit
) : FrameLayout(context) {
    private enum class Phase { CLOSED, OPENING, OPEN, CLOSING }

    /** Where the window unfolds from and to, and what it must leave visible; local overlay pixels. */
    class Plan(val origin: RectF, val destination: RectF, val protectedBounds: List<RectF>)
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
    /** Real app icon of the scene (owner V1): rides the head of the light trail while the window unfolds/folds. */
    private var emblem: android.graphics.drawable.Drawable? = null
    /** The emblem is an installed app's real icon: it rides the trail inside the app crystal (a document page does not). */
    private var crystal = false
    private var onOpened: () -> Unit = {}
    private var onClosed: () -> Unit = {}
    private var accent = Colors.cyan
    /** Host's fresh plan for the size the overlay really has (see onSizeChanged). */
    private var refit: (() -> Plan?)? = null
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
    /**
     * Holographic status strip of the running work (owner addendum 06.11.): the steps of the turn that really runs –
     * docked here instead of over the avatar's face and the orb. One line; hidden when nothing runs or when the window is
     * too small to show it without hiding a control. A polite live region for TalkBack.
     */
    private val statusView = TextView(context).apply {
        tag = "jarvis-work-window:status"
        setTextColor(Colors.cyanSoft)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(8f), dp(2f), dp(8f), dp(4f))
        visibility = GONE
        if (Build.VERSION.SDK_INT >= 19) accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private var statusText: String? = null
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
        addView(statusView, LinearLayout.LayoutParams(-1, -2))
        addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        pivotX = 0f
        pivotY = 0f
    }

    val isShowing: Boolean get() = phase != Phase.CLOSED

    /** The running work's steps for the status strip; null or blank hides it. Only real, current steps. */
    fun setStatus(text: String?) {
        val value = text?.takeIf { it.isNotBlank() }
        if (value == statusText) return
        statusText = value
        statusView.text = value.orEmpty()
        val params = panel.layoutParams as? LayoutParams
        if (params != null && params.width > 0 && params.height > 0) fitTitle(params.width, params.height)
        else statusView.visibility = if (value == null) GONE else VISIBLE
    }

    /** Where the window stands (overlay pixels) while it is shown; null when closed. */
    fun shownBounds(): RectF? = if (isShowing && JarvisWorkWindowGeometry.valid(destination)) RectF(destination) else null

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
             onOpened: () -> Unit = {}, onClosed: () -> Unit = {}, accent: Int = Colors.cyan,
             emblem: android.graphics.drawable.Drawable? = null, refit: (() -> Plan?)? = null,
             crystal: Boolean = false): Boolean {
        if (disposed || deliveringFrame || isInLayout) return false
        val pixels = usable(origin, destination, protectedBounds) ?: return false
        if (body.parent != null && body.parent !== scroll) return false
        generation++
        pendingCleanup?.let { removeCallbacks(it) }
        pendingCleanup = null
        this.onClosed = {}
        close(immediate = true)
        scroll.removeAllViews()
        statusText = null
        statusView.text = ""
        statusView.visibility = GONE
        this.onOpened = onOpened
        this.onClosed = onClosed
        this.emblem = emblem?.mutate()
        this.crystal = crystal && emblem != null
        this.accent = accent
        this.refit = refit
        titleView.text = title
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        scroll.scrollTo(0, 0)
        place(origin, pixels, protectedBounds)
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

    /** Pixel destination if it is a usable window that leaves the protected areas free and fits the overlay. */
    private fun usable(origin: RectF, destination: RectF, protectedBounds: List<RectF>): RectF? {
        if (!JarvisWorkWindowGeometry.valid(origin) || !JarvisWorkWindowGeometry.valid(destination)) return null
        val pixels = RectF(ceil(destination.left), ceil(destination.top), floor(destination.right), floor(destination.bottom))
        if (pixels.width() < 200f * density || pixels.height() < 160f * density ||
            protectedBounds.any { !JarvisWorkWindowGeometry.finite(it) ||
                (JarvisWorkWindowGeometry.valid(it) && RectF.intersects(it, pixels)) } ||
            (width > 0 && height > 0 && !RectF(0f, 0f, width.toFloat(), height.toFloat()).contains(pixels))) return null
        return pixels
    }

    private fun place(origin: RectF, pixels: RectF, protectedBounds: List<RectF>) {
        this.origin.set(origin)
        this.destination.set(pixels)
        this.protectedBounds = protectedBounds.filter(JarvisWorkWindowGeometry::valid).map(::RectF)
        panel.layoutParams = LayoutParams(pixels.width().toInt(), pixels.height().toInt()).apply {
            leftMargin = pixels.left.toInt()
            topMargin = pixels.top.toInt()
        }
        fitTitle(pixels.width().toInt(), pixels.height().toInt())
        edge = Fill.Linear(origin.centerX() / density, origin.centerY() / density,
            pixels.centerX() / density, pixels.centerY() / density,
            intArrayOf(accent, Colors.cardRimEnd, Colors.violet, Colors.magenta))
    }

    /**
     * At a large font scale a long title wraps. It may never take so much of the panel that the scroll viewport can no
     * longer show the body's tallest control whole (PR #572: 200 % font in an 840 × 320 window – a four-line title left
     * 101 px for a 128 px action). Then the title keeps one line; the full title stays its text and accessibility heading.
     */
    private fun fitTitle(widthPx: Int, heightPx: Int) {
        titleView.maxLines = Int.MAX_VALUE
        titleView.ellipsize = null
        statusView.visibility = if (statusText == null) GONE else VISIBLE
        val body = scroll.getChildAt(0) ?: return
        fun fits(): Boolean {
            panel.measure(MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY))
            return tallestControl(body) <= scroll.measuredHeight - scroll.paddingTop - scroll.paddingBottom
        }
        if (fits()) return
        titleView.maxLines = 1
        titleView.ellipsize = TextUtils.TruncateAt.END
        // Too small even then (200 % font, outer display): the controls win, the status strip steps aside.
        if (!fits() && statusView.visibility == VISIBLE) statusView.visibility = GONE
    }

    /** Measured height of the tallest clickable leaf (a clickable group without clickable children counts as one). */
    private fun tallestControl(view: View): Int {
        if (view.visibility == GONE) return 0
        val inner = (view as? ViewGroup)?.let { group ->
            (0 until group.childCount).maxOfOrNull { tallestControl(group.getChildAt(it)) }
        } ?: 0
        return if (inner == 0 && view.isClickable) view.measuredHeight else inner
    }

    /**
     * The first layout came at another size than the one the window was planned for (a rotation, fold or keyboard
     * resize was still pending when the owner touched). Instead of vanishing without a trace, the window asks its host
     * for a plan on the real size once the layout has settled; only without a usable plan does it close.
     */
    private fun refitAfterLayout() {
        val token = generation
        post {
            if (token != generation || !isShowing) return@post
            // The host plans on its fresh geometry; without a host plan the window refits within its own protected areas.
            val plan = refit.let { host -> if (host != null) host() else selfPlan() }
            val pixels = plan?.let { usable(it.origin, it.destination, it.protectedBounds) }
            if (plan == null || pixels == null) { close(immediate = true); return@post }
            place(plan.origin, pixels, plan.protectedBounds)
            applyFrame()
            updateRoute()
            requestFrame()
        }
    }

    private fun selfPlan(): Plan? = JarvisWorkWindowGeometry.destination(
        RectF(0f, 0f, width.toFloat(), height.toFloat()), protectedBounds, density)?.let { Plan(RectF(origin), it, protectedBounds) }

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
        when {
            // A live resize of an open window (rotation, fold): its geometry is gone, it folds away.
            oldw > 0 && oldh > 0 && (w != oldw || h != oldh) -> close(immediate = true)
            RectF(0f, 0f, w.toFloat(), h.toFloat()).contains(destination) -> updateRoute()
            else -> refitAfterLayout()
        }
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
        // Closing runs the same route backwards; indices instead of a reversed view keep the frame allocation-free.
        val reversed = phase == Phase.CLOSING
        val last = route.lastIndex
        var remaining = routeLength * trailProgress
        var headX = Float.NaN
        var headY = Float.NaN
        for (i in 0 until last) {
            val a = route[if (reversed) last - i else i]
            val b = route[if (reversed) last - i - 1 else i + 1]
            val length = hypot(b.x - a.x, b.y - a.y)
            if (length <= 0f) continue
            val progress = (remaining / length).coerceIn(0f, 1f)
            val x = (a.x + (b.x - a.x) * progress) / density
            val y = (a.y + (b.y - a.y) * progress) / density
            painter.drawLine(a.x / density, a.y / density, x, y, 6f, edge, .08f * trailAlpha)
            painter.drawLine(a.x / density, a.y / density, x, y, 1f, edge, .85f * trailAlpha)
            headX = x; headY = y
            remaining -= length
            if (remaining <= 0f) {
                painter.fillCircle(x, y, 1.5f, TRAIL_HEAD_FILL, trailAlpha)
                break
            }
        }
        drawEmblem(canvas, headX, headY)
        canvas.restoreToCount(save)
    }

    /**
     * The real app icon at the head of the trail (dp canvas): a small glass disc with a fine rim. It is strongest
     * while the window is still small and fades as the window unfolds; on closing it returns along the same route.
     */
    private fun drawEmblem(canvas: Canvas, x: Float, y: Float) {
        val icon = emblem ?: return
        if (!x.isFinite() || !y.isFinite()) return
        val a = (trailAlpha * (1f - .85f * openness)).coerceIn(0f, 1f)
        if (a <= .02f) return
        if (crystal) { drawCrystal(canvas, icon, x, y, a); return }
        painter.fillCircle(x, y, EMBLEM_DP * .95f, EMBLEM_DISC_FILL, .72f * a)
        painter.fillCircle(x, y, EMBLEM_DP * 1.35f, EMBLEM_HALO_FILL, .07f * a)
        painter.strokeCircle(x, y, EMBLEM_DP * .95f, 1f, edge, .9f * a)
        val half = (EMBLEM_DP * .62f).roundToInt()
        icon.setBounds(x.roundToInt() - half, y.roundToInt() - half, x.roundToInt() + half, y.roundToInt() + half)
        icon.alpha = (a * 255f).roundToInt().coerceIn(0, 255)
        icon.draw(canvas)
    }

    /** The app crystal around the real [icon] at the trail head (dp canvas), see CRYSTAL_*. */
    private fun drawCrystal(canvas: Canvas, icon: android.graphics.drawable.Drawable, x: Float, y: Float, a: Float) {
        val save = canvas.save()
        canvas.translate(x, y)
        painter.fillCircle(0f, 0f, CRYSTAL_H * 1.2f, EMBLEM_HALO_FILL, .06f * a)
        painter.fillPath(CRYSTAL_OUTLINE, EMBLEM_DISC_FILL, .78f * a)
        painter.fillPath(CRYSTAL_LEFT, CRYSTAL_LEFT_FILL, .46f * a)
        painter.fillPath(CRYSTAL_RIGHT, CRYSTAL_RIGHT_FILL, .34f * a)
        // Girdle facets above and below the icon: the prism reads as a cut crystal, not a flat badge. They and the ridge
        // stay outside the icon's bounds – nothing is drawn over the real app symbol (#565 review).
        val half = (CRYSTAL_W * .52f).roundToInt()
        val girdle = maxOf(CRYSTAL_S * .55f, half + 1f)
        painter.drawLine(-CRYSTAL_W, -CRYSTAL_S, 0f, -girdle, .8f, CRYSTAL_RIM_FILL, .55f * a)
        painter.drawLine(0f, -girdle, CRYSTAL_W, -CRYSTAL_S, .8f, CRYSTAL_RIM_FILL, .55f * a)
        painter.drawLine(-CRYSTAL_W, CRYSTAL_S, 0f, girdle, .8f, CRYSTAL_RIM_FILL, .4f * a)
        painter.drawLine(0f, girdle, CRYSTAL_W, CRYSTAL_S, .8f, CRYSTAL_RIM_FILL, .4f * a)
        icon.setBounds(-half, -half, half, half)
        icon.alpha = (a * 255f).roundToInt().coerceIn(0, 255)
        icon.draw(canvas)
        painter.drawLine(0f, -CRYSTAL_H, 0f, -girdle, .7f, CRYSTAL_RIM_FILL, .6f * a)
        painter.drawLine(0f, girdle, 0f, CRYSTAL_H, .7f, CRYSTAL_RIM_FILL, .6f * a)
        // A glint sweeps once across the prism while the window unfolds (its height follows the silhouette).
        val sweep = (trailProgress * 1.4f).coerceIn(0f, 1f)
        if (sweep in .02f..0.98f) {
            val gx = -CRYSTAL_W + 2f * CRYSTAL_W * sweep
            val top = -CRYSTAL_H + (CRYSTAL_H - CRYSTAL_S) * kotlin.math.abs(gx) / CRYSTAL_W
            painter.drawLine(gx, top, gx, -top, 1.2f, TRAIL_HEAD_FILL,
                .55f * a * kotlin.math.sin(Math.PI.toFloat() * sweep))
        }
        painter.strokePath(CRYSTAL_OUTLINE, 1.2f, CRYSTAL_RIM_FILL, a)
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
            glass.fillRoundRect(1f, 1f, right, bottom, 14f, WINDOW_BACKING_FILL, WINDOW_BACKING_ALPHA)
            JarvisReferenceHud.surface(glass, JarvisShellLayout.Box(1f, 1f, right, bottom), 14f,
                Colors.panelRim, Colors.violetSoft)
            if (edgeFlash > 0f) glass.strokeRoundRect(1f, 1f, right, bottom, 14f, 1f,
                EDGE_FLASH_FILL, .35f * edgeFlash)
            canvas.restoreToCount(save)
        }
    }
}
