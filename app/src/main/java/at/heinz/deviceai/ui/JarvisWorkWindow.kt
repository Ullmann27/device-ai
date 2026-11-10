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
import kotlin.math.abs
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
/** Rear sheets and the receding document plane (JarvisWorkWindowDepth): dark glass, a Fresnel rim, a top-edge glint. */
private val DEPTH_GLASS_FILL = Fill.Solid(Colors.background)
private val DEPTH_GLINT_FILL = Fill.Solid(Colors.cyanSoft)
/** EXP03 plate: haze glass, not the generic window backing. Allocated once. */
private val PROTECTED_DEPTH_FILL = Fill.Solid(Colors.backgroundHaze)
private const val DEPTH_RADIUS_DP = 14f
private const val HANDOVER_SLOT_LEFT_DP = 16f
private const val HANDOVER_SLOT_TOP_DP = 60f
private const val HANDOVER_SLOT_W_DP = 44f
private const val HANDOVER_SLOT_H_DP = 58f
private const val CONTENT_MORPH_MS = 250L

/** In-cockpit content, shell frames only. */
class JarvisWorkWindow(
    context: Context,
    private val frameSource: (((Long, Float) -> Boolean) -> AutoCloseable),
    private val requestFrame: () -> Unit
) : FrameLayout(context) {
    private enum class Phase { CLOSED, OPENING, OPEN, CLOSING }

    /** Where the window unfolds from and to, and what it must leave visible; local overlay pixels. */
    class Plan(val origin: RectF, val destination: RectF, val protectedBounds: List<RectF>)
    /**
     * The document that steps back behind this window while it unfolds (draft out of document, owner brief 06.11.):
     * [document] is the window it had (overlay pixels), [page] where its real page stood, [pageImage] that page for the
     * flight into the body's own page view [handover], which stays invisible until the flight has arrived.
     */
    class Recession(val document: RectF, val page: RectF?, val pageImage: android.graphics.drawable.Drawable?,
                    val handover: View?)
    private val density = resources.displayMetrics.density
    private var lastAnimatorScale = 1f
    private var phaseTiltDegrees = HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES
    internal var cinematicArcEnabled = false
    /** EXP03: extra protected depth plates beyond [DEPTH_GLASS_FILL]. */
    internal var protectedDepthSurfacesEnabled = false
        set(value) {
            if (field == value) return
            field = value
            requestFrame()
        }
    internal var onCinematicFrame: ((Long, Long, Boolean, Float) -> Boolean)? = null
    internal var onPresentedCenter: ((Float, Float) -> Unit)? = null
    private val projectedCenter = FloatArray(2)
    internal var cinematicEffectsEnabled = true
        set(value) {
            if (field == value) return
            field = value
            // A live fallback change settles the same content; it must not replay work or tear down its lease.
            if (phase == Phase.OPENING) {
                openness = 1f
                opacity = 1f
                trailAlpha = 0f
                panel.edgeFlash = 0f
                phase = Phase.OPEN
                handOver()
                val callback = onOpened
                onOpened = {}
                callback()
            }
            if (phase == Phase.CLOSING) finishClose()
            else if (isShowing) applyFrame()
            requestFrame()
        }
    private var constructionElapsedMs = 0L
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
    /** Rear sheets behind the front glass: the document's real further pages (JarvisWorkWindowDepth.layers). */
    var depthLayers = 0
        private set
    private var recession: Recession? = null
    private var handedOver = false
    // Scratch rectangles of the depth drawing – owned by the window, never allocated in a frame.
    private val frontRect = RectF()
    private val depthRect = RectF()
    private val planeRect = RectF()
    private val slotRect = RectF()
    private val pageRect = RectF()
    private val protectedDepthRect = RectF()
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

    internal fun projectedMotionPhase(): at.heinz.deviceai.cockpit.WindowProjectedMotion.Phase = when (phase) {
        Phase.OPENING -> at.heinz.deviceai.cockpit.WindowProjectedMotion.Phase.FLYING
        Phase.OPEN -> at.heinz.deviceai.cockpit.WindowProjectedMotion.Phase.SETTLED
        Phase.CLOSING -> at.heinz.deviceai.cockpit.WindowProjectedMotion.Phase.CLOSING
        Phase.CLOSED -> at.heinz.deviceai.cockpit.WindowProjectedMotion.Phase.CLOSED
    }

    internal fun motionSuppressed(): Boolean = lastAnimatorScale <= 0f

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

    /**
     * Replaces the semantic work surface without closing/reopening this window.
     * Geometry, openness, frame subscription and camera ownership remain unchanged.
     */
    fun replaceContent(
        body: View,
        title: String,
        accent: Int = Colors.cyan,
        emblem: android.graphics.drawable.Drawable? = null,
        crystal: Boolean = false,
        depthLayers: Int = 0
    ): Boolean {
        if (disposed || !isShowing || deliveringFrame || isInLayout) return false
        if (body.parent != null && body.parent !== scroll) return false
        generation++
        pendingCleanup?.let { removeCallbacks(it) }
        pendingCleanup = null
        this.onOpened = {}
        this.emblem = emblem?.mutate()
        this.crystal = crystal && emblem != null
        this.accent = accent
        this.depthLayers = depthLayers.coerceIn(0, JarvisWorkWindowDepth.MAX_LAYERS)
        recession = null
        handedOver = true
        titleView.text = title
        statusText = null
        statusView.text = ""
        statusView.visibility = GONE
        val oldBody = scroll.getChildAt(0)
        if (oldBody !== body) {
            oldBody?.animate()?.cancel()
            scroll.removeAllViews()
            body.animate().cancel()
            body.alpha = 0f
            scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
            body.animate().alpha(1f).setDuration(CONTENT_MORPH_MS).start()
        } else body.alpha = 1f
        scroll.scrollTo(0, 0)
        val params = panel.layoutParams as? LayoutParams
        if (params != null && params.width > 0 && params.height > 0) fitTitle(params.width, params.height)
        edge = Fill.Linear(origin.centerX() / density, origin.centerY() / density,
            destination.centerX() / density, destination.centerY() / density,
            intArrayOf(accent, Colors.cardRimEnd, Colors.violet, Colors.magenta))
        invalidate()
        requestFrame()
        return true
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
             crystal: Boolean = false, depthLayers: Int = 0, recession: Recession? = null): Boolean {
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
        this.depthLayers = depthLayers.coerceIn(0, JarvisWorkWindowDepth.MAX_LAYERS)
        this.recession = recession?.takeIf { JarvisWorkWindowGeometry.valid(it.document) }
        // The body's own page view shows itself only once the real page has flown into its slot.
        handedOver = this.recession?.let { it.page == null || it.pageImage == null || it.handover == null } ?: true
        if (!handedOver) this.recession?.handover?.alpha = 0f
        titleView.text = title
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        scroll.scrollTo(0, 0)
        place(origin, pixels, protectedBounds)
        phase = Phase.OPENING
        startedAt = null
        lastFrameTime = null
        constructionElapsedMs = 0L
        phaseTiltDegrees = HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES
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
        startedAt = null
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
        val start = startedAt ?: nowMs.also { startedAt = it }
        val elapsed = (nowMs - start).coerceAtLeast(0L)
        val reduced = !animatorScale.isFinite() || animatorScale <= 0f
        val normalized = if (reduced) HollywoodFuiConstruct.DATA_END_MS else (elapsed / animatorScale).toLong()
        if (phase == Phase.OPEN) {
            constructionElapsedMs = maxOf(constructionElapsedMs, normalized, HollywoodFuiConstruct.DATA_END_MS)
            return onCinematicFrame?.invoke(constructionElapsedMs, -1L, reduced, 1f) == true
        }
        if (cinematicEffectsEnabled) {
            lastAnimatorScale = animatorScale
            val isClosing = phase == Phase.CLOSING
            if (!isClosing) constructionElapsedMs = normalized
            val closingTime = if (isClosing) {
                if (reduced) HollywoodFuiConstruct.CLOSE_END_MS else normalized
            } else -1L
            val frame = HollywoodFuiConstruct.frame(constructionElapsedMs, closingTime, reduced)
            phaseTiltDegrees = frame.tiltDegrees
            openness = if (isClosing) closeFrom * frame.closeFrame
                else if (reduced) 1f else HollywoodFuiConstruct.flight(normalized)
            opacity = if (isClosing) closeAlpha * frame.closeData else frame.data
            trailProgress = frame.anchor
            trailAlpha = (1f - frame.brackets) * frame.closeFrame
            panel.edgeFlash = frame.pulse * .35f
            applyFrame()
            val live = onCinematicFrame?.invoke(constructionElapsedMs, closingTime, reduced, openness) == true
            if (isClosing && frame.closeFactor <= 0f) { finishClose(); return false }
            if (!handedOver && JarvisWorkWindowDepth.handedOver(openness)) handOver()
            if (!isClosing && frame.settled) {
                phase = Phase.OPEN
                handOver()
                val callback = onOpened
                onOpened = {}
                callback()
            }
            return phase == Phase.OPENING || phase == Phase.CLOSING || live
        }
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
        lastAnimatorScale = animatorScale
        applyFrame()
        if (!handedOver && JarvisWorkWindowDepth.handedOver(openness)) handOver()
        if (state.done && phase == Phase.OPENING) {
            phase = Phase.OPEN
            handOver()
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
            panel.rotationY = 0f
        } else {
            panel.translationX = origin.left + (destination.left - origin.left) * openness - params.leftMargin
            panel.translationY = origin.top + (destination.top - origin.top) * openness - params.topMargin +
                HollywoodFuiConstruct.arcLiftDp(openness, cinematicArcEnabled && lastAnimatorScale > 0f) * density
            panel.scaleX = (origin.width() + (destination.width() - origin.width()) * openness) / params.width
            panel.scaleY = (origin.height() + (destination.height() - origin.height()) * openness) / params.height
            // #643 C: the glass swings in around its vertical axis from the side it comes from and settles flat.
            panel.cameraDistance = density * JarvisMotion.Depth.CAMERA_DISTANCE_DP
            panel.rotationY = if (cinematicEffectsEnabled)
                HollywoodFuiConstruct.orientedTilt(phaseTiltDegrees, entranceSide(), lastAnimatorScale <= 0f) else 0f
        }
        panel.alpha = opacity
        reportPresentedCenter()
        panel.invalidate()
        invalidate()
    }

    internal fun reportPresentedCenter() {
        if (!isShowing) return
        val report = onPresentedCenter ?: return
        val params = panel.layoutParams as LayoutParams
        projectedCenter[0] = params.width * .5f
        projectedCenter[1] = params.height * .5f
        // View.matrix includes scale, translation and the actual Android camera perspective around the panel pivot.
        panel.matrix.mapPoints(projectedCenter)
        report(params.leftMargin + projectedCenter[0], params.topMargin + projectedCenter[1])
    }

    /** −1 when the window comes from the left of its destination, +1 from the right, 0 when it grows in place. */
    private fun entranceSide(): Float {
        val delta = origin.centerX() - destination.centerX()
        return if (abs(delta) < density * 8f) 0f else if (delta < 0f) -1f else 1f
    }

    /** The flight of the real page has arrived: the body's page view takes over, the drawn page ends. */
    private fun handOver() {
        if (handedOver) return
        handedOver = true
        recession?.handover?.alpha = 1f
    }

    private fun finishClose() {
        phase = Phase.CLOSED
        val handle = subscription
        subscription = null
        handle?.close()
        visibility = GONE
        route = emptyList()
        handOver()
        recession = null
        depthLayers = 0
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
        drawDepth(canvas)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    /**
     * What stands behind the front glass, deepest first: the document's further pages as narrow sheets, the document
     * plane stepping back while a draft unfolds in front of it, and the real page on its flight into its slot in the
     * body. Everything follows the same frame as the panel (openness) – no clock, no allocation of its own.
     */
    private fun drawDepth(canvas: Canvas) {
        val recession = recession
        if (depthLayers == 0 && recession == null) return
        if (!JarvisWorkWindowGeometry.valid(destination)) return
        val progress = openness.coerceIn(0f, 1f)
        // The front glass as the panel shows it in this frame.
        if (progress >= 1f) frontRect.set(destination)
        else frontRect.set(origin.left + (destination.left - origin.left) * progress,
            origin.top + (destination.top - origin.top) * progress,
            origin.right + (destination.right - origin.right) * progress,
            origin.bottom + (destination.bottom - origin.bottom) * progress)
        val closing = phase == Phase.CLOSING
        val glassAlpha = if (closing) opacity else 1f
        val save = canvas.save()
        canvas.scale(density, density)
        painter.begin(canvas)
        if (recession == null) {
            for (index in depthLayers downTo 1) {
                if (JarvisWorkWindowDepth.layer(frontRect, index, progress, density, depthRect))
                    drawSheet(depthRect, JarvisWorkWindowDepth.layerAlpha(index, glassAlpha))
            }
        } else if (JarvisWorkWindowDepth.plane(recession.document, destination, progress, density, planeRect)) {
            // The further pages of the document keep standing behind its plane while it steps back.
            for (index in depthLayers downTo 2) {
                if (JarvisWorkWindowDepth.layer(planeRect, index - 1, 1f, density, depthRect))
                    drawSheet(depthRect, JarvisWorkWindowDepth.layerAlpha(index, glassAlpha))
            }
            drawSheet(planeRect, JarvisWorkWindowDepth.planeAlpha(progress) * glassAlpha)
        }
        canvas.restoreToCount(save)
        if (recession != null && !handedOver && !closing) drawRecedingPage(canvas, recession, progress)
    }

    /** One glass sheet in dp: dark glass, Fresnel rim along the window's own light gradient, a glint on its top edge. */
    private fun drawSheet(rect: RectF, alpha: Float) {
        if (alpha <= .01f) return
        val l = rect.left / density; val t = rect.top / density; val r = rect.right / density; val b = rect.bottom / density
        painter.fillRoundRect(l, t, r, b, DEPTH_RADIUS_DP, DEPTH_GLASS_FILL, WINDOW_BACKING_ALPHA * alpha)
        painter.strokeRoundRect(l, t, r, b, DEPTH_RADIUS_DP, 1f, edge, .9f * alpha)
        painter.drawLine(l + DEPTH_RADIUS_DP, t, r - DEPTH_RADIUS_DP, t, 1.5f, DEPTH_GLINT_FILL, .35f * alpha)
        if (protectedDepthSurfacesEnabled &&
            JarvisProtectedDepthSurfaces.recess(rect, density, protectedDepthRect)) {
            val pl = protectedDepthRect.left / density
            val pt = protectedDepthRect.top / density
            val pr = protectedDepthRect.right / density
            val pb = protectedDepthRect.bottom / density
            val plate = JarvisProtectedDepthSurfaces.plateAlpha(alpha)
            painter.fillRoundRect(pl, pt, pr, pb, DEPTH_RADIUS_DP, PROTECTED_DEPTH_FILL, plate)
            painter.strokeRoundRect(pl, pt, pr, pb, DEPTH_RADIUS_DP, 1f, DEPTH_GLINT_FILL,
                JarvisProtectedDepthSurfaces.FRESNEL_ALPHA * alpha)
        }
    }

    /** The real page (pixel canvas) from its place in the document window into the body's page slot. */
    private fun drawRecedingPage(canvas: Canvas, recession: Recession, progress: Float) {
        val from = recession.page ?: return
        val image = recession.pageImage ?: return
        if (!handoverSlot(recession.handover, slotRect)) return
        if (!JarvisWorkWindowDepth.recede(from, slotRect, progress, pageRect)) return
        val alpha = JarvisWorkWindowDepth.recedeAlpha(progress)
        if (alpha <= .01f) return
        image.setBounds(pageRect.left.roundToInt(), pageRect.top.roundToInt(),
            pageRect.right.roundToInt(), pageRect.bottom.roundToInt())
        image.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        image.draw(canvas)
        painter.begin(canvas)
        val save = canvas.save()
        canvas.scale(density, density)
        painter.strokeRoundRect(pageRect.left / density, pageRect.top / density, pageRect.right / density,
            pageRect.bottom / density, 2f, 1f, edge, .8f * alpha)
        canvas.restoreToCount(save)
    }

    /**
     * Where the body's page view stands once the panel is open (destination pixels), walking its offsets up to the
     * panel; before its first layout a slot in the panel's top-left corner, where the source card is laid out.
     */
    private fun handoverSlot(view: View?, out: RectF): Boolean {
        if (view != null && view.width > 0 && view.height > 0 && view.isAttachedToWindow) {
            var x = 0f; var y = 0f
            var current: View = view
            while (true) {
                x += current.left; y += current.top
                val parent = current.parent as? View ?: break
                if (parent === panel) {
                    out.set(destination.left + x, destination.top + y,
                        destination.left + x + view.width, destination.top + y + view.height)
                    return true
                }
                x -= parent.scrollX; y -= parent.scrollY
                current = parent
            }
        }
        val left = destination.left + HANDOVER_SLOT_LEFT_DP * density
        val top = destination.top + HANDOVER_SLOT_TOP_DP * density
        out.set(left, top, left + HANDOVER_SLOT_W_DP * density, top + HANDOVER_SLOT_H_DP * density)
        return true
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
