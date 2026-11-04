package at.heinz.deviceai.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot

/**
 * The Continuum start screen as real Android views: one [ElementView] per layout slot plus the living core
 * ([ContinuumHologramView]) in the hero slot. Positions come from [ContinuumHomeLayout] (measured from the reference
 * images); every element is a focusable, clickable view with a TalkBack description of its real values.
 * The phone navigation is not part of this view: the activity pins it to the bottom edge.
 */
@SuppressLint("ViewConstructor")
class ContinuumHomeView(context: Context, val hologram: ContinuumHologramView, private val onSlot: (Slot, Float, Float) -> Unit) :
    ViewGroup(context) {
    private val density = resources.displayMetrics.density
    private val textScale = resources.configuration.fontScale.coerceIn(.85f, 2f)
    private var layoutSpec: ContinuumHomeLayout.Layout? = null
    private var offset = 0f
    private val presentationPrefs = context.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
    private var compactPresentation = presentationPrefs.getBoolean("compact", false)
    private var configuredWidth = 0f
    private var configuredHeight = 0f
    private var configuredWide = false
    private var configuredNavigation = false

    /** Presentation reuses the same live view and its existing original orb/3D lifecycle. */
    private fun selectPresentation(slot: Slot) {
        val compact = slot == Slot.VIEW_COMPACT
        if (slot == Slot.VIEW_AVATAR) {
            val use3d = presentationPrefs.getBoolean("avatar-3d", true)
            if (use3d) hologram.setNative3dEnabled(true) else hologram.setNativeHeadEnabled(true)
        } else {
            if (hologram.isNativeHeadEnabled()) {
                presentationPrefs.edit().putBoolean("avatar-3d", hologram.isNative3dEnabled()).apply()
            }
            hologram.setNativeHeadEnabled(false)
        }
        compactPresentation = compact
        presentationPrefs.edit().putBoolean("compact", compact).apply()
        configure(configuredWidth, configuredWide, offset, configuredNavigation, configuredHeight)
        views.values.forEach { it.refresh() }
        views[slot]?.requestFocus()
    }
    private val views = linkedMapOf<Slot, ElementView>()
    private val backgroundPainter = AndroidContinuumPainter(context.assets)
    private val sciFiEffects = ContinuumSciFiEffects(density)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var learningMotionUntil = 0L
    private var modelMotionUntil = 0L
    private var systemMotionUntil = 0L
    private fun now() = android.os.SystemClock.uptimeMillis()
    private var foregroundActive = false
    fun setForegroundActive(active: Boolean) {
        foregroundActive = active
        updateWaveAnimation()
    }
    private var waveFramePending = false
    private val waveFrame = object : Runnable {
        override fun run() {
            waveFramePending = false
            if (!waveAnimationEnabled()) {
                views[Slot.LEARNING]?.invalidate()
                views[Slot.MODEL]?.invalidate()
                views[Slot.SYSTEM_ACTIVITY]?.invalidate()
                return
            }
            // Moving light follows authentic measurements; data points never change here.
            // One brief highlight per new measurement; no endless activity on unchanged history.
            // No running light on an idle quality score or historical learning curve.
            if (now() <= learningMotionUntil) views[Slot.LEARNING]?.invalidate()
            if (now() <= modelMotionUntil) views[Slot.MODEL]?.invalidate()
            if (now() <= systemMotionUntil) views[Slot.SYSTEM_ACTIVITY]?.invalidate()
            scheduleWaveFrame()
        }
    }

    private fun waveAnimationEnabled(): Boolean = foregroundActive && isAttachedToWindow && windowVisibility == View.VISIBLE &&
        visibility == View.VISIBLE && isShown && android.animation.ValueAnimator.areAnimatorsEnabled() &&
        !powerManager.isPowerSaveMode && now() < maxOf(learningMotionUntil, modelMotionUntil, systemMotionUntil)

    private fun scheduleWaveFrame() {
        if (!waveFramePending && waveAnimationEnabled()) {
            waveFramePending = true
            postOnAnimationDelayed(waveFrame, 66L)
        }
    }

    private fun updateWaveAnimation() {
        if (waveAnimationEnabled()) scheduleWaveFrame()
        else {
            removeCallbacks(waveFrame)
            waveFramePending = false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateWaveAnimation()
    }

    override fun onDetachedFromWindow() {
        dockedRenderer?.let { runCatching { it.detach() } }
        dockedRenderer = null
        removeCallbacks(waveFrame)
        waveFramePending = false
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateWaveAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateWaveAnimation()
    }
    var state: ContinuumDashboardState = ContinuumDashboardState()
        set(value) {
            val until = now() + 900L
            if (value.learning.series != field.learning.series && value.learning.series.size > 1) learningMotionUntil = until
            if (value.model.qualityPercent != field.model.qualityPercent && value.model.qualityPercent != null) modelMotionUntil = until
            if (value.system.activity != field.system.activity && value.system.activity.size > 1) systemMotionUntil = until
            field = value
            views.values.forEach { it.refresh() }
            if (jarvisShellStage) jarvisShell.tiles = JarvisShellContent.tiles(value, System.currentTimeMillis())
            updateWaveAnimation()
        }

    /**
     * Welten-Optik's shell (#479) as the bottom layer. Opt-in stage ("jarvis-shell" preference or
     * [setJarvisShellStage]): the shell draws phone/Fold7/tablet composition with real content from
     * [JarvisShellContent]; the live avatar sits in its AVATAR slot. Off by default until the shell offers touch
     * targets and tile destinations (docs/ARCHITEKTUR-JARVIS.md, "Fehlt an der Schnittstelle").
     */
    val jarvisShell = JarvisShellView(context)
    var jarvisShellStage = presentationPrefs.getBoolean("jarvis-shell", false)
        private set

    fun setJarvisShellStage(enabled: Boolean) {
        jarvisShellStage = enabled
        presentationPrefs.edit().putBoolean("jarvis-shell", enabled).apply()
        applyShellStage()
        requestLayout()
    }

    private fun applyShellStage() {
        jarvisShell.visibility = if (jarvisShellStage) View.VISIBLE else View.GONE
        views.values.forEach { it.visibility = if (jarvisShellStage) View.GONE else View.VISIBLE }
        applyAvatarSlot()
    }

    // --- The ONE avatar slot (JarvisAvatarRenderer, docs/ARCHITEKTUR-JARVIS.md) ---
    /** Cockpit modes own the avatar slot: no head/orb of our own, only a docked renderer or the placeholder. */
    val ownsAvatarSlot: Boolean get() = jarvisShellStage || landscapeCockpit
    private val avatarPlaceholder = AvatarSlotPlaceholder(context)
    private var dockedRenderer: at.heinz.deviceai.cockpit.JarvisAvatarRenderer? = null
    private val avatarRect = android.graphics.Rect()

    val avatarRenderer: at.heinz.deviceai.cockpit.JarvisAvatarRenderer? get() = dockedRenderer
    /** Pixel rect of the avatar slot (cockpit modes). */
    val avatarSlotRect: android.graphics.Rect get() = android.graphics.Rect(avatarRect)
    /** The slot is visibly held: docked renderer or the named placeholder. */
    val avatarSlotShown: Boolean get() = dockedRenderer != null || avatarPlaceholder.isShown

    private fun applyAvatarSlot() {
        val owns = ownsAvatarSlot
        val wanted = if (owns) at.heinz.deviceai.cockpit.JarvisAvatarSlot.renderer else null
        if (wanted !== dockedRenderer) {
            dockedRenderer?.let { runCatching { it.detach() } }
            dockedRenderer = null
            if (wanted != null && !avatarRect.isEmpty) { wanted.attach(this, android.graphics.Rect(avatarRect)); dockedRenderer = wanted }
        }
        hologram.visibility = if (owns) View.GONE else View.VISIBLE
        avatarPlaceholder.visibility = if (owns && dockedRenderer == null) View.VISIBLE else View.GONE
    }

    private fun layoutAvatarSlot(left: Int, top: Int, right: Int, bottom: Int) {
        val changed = avatarRect.left != left || avatarRect.top != top || avatarRect.right != right || avatarRect.bottom != bottom
        avatarRect.set(left, top, right, bottom)
        avatarPlaceholder.measure(MeasureSpec.makeMeasureSpec(right - left, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(bottom - top, MeasureSpec.EXACTLY))
        avatarPlaceholder.layout(left, top, right, bottom)
        if (dockedRenderer == null) applyAvatarSlot()
        else if (changed) dockedRenderer?.onSlotChanged(android.graphics.Rect(avatarRect))
    }

    /** Empty, clearly named slot until Jarvis Avatar docks a renderer. Draws only a thin rim and the name. */
    private class AvatarSlotPlaceholder(context: Context) : View(context) {
        private val painter = AndroidContinuumPainter(context.assets)
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES; contentDescription = NAME }
        override fun onDraw(canvas: android.graphics.Canvas) {
            val d = resources.displayMetrics.density
            val w = width / d; val h = height / d
            painter.begin(canvas); canvas.save(); canvas.scale(d, d)
            val style = JarvisDesign.Type.caption
            painter.strokeRoundRect(4f, 4f, w - 4f, h - 4f, 18f, 1f, Fill.Solid(JarvisDesign.Colors.rim))
            painter.drawText(NAME, w / 2f, h / 2f, style, Fill.Solid(JarvisDesign.Colors.textSecondary), TextAlign.CENTER)
            canvas.restore()
        }
        companion object { const val NAME = "Jarvis Avatar – Renderer nicht angedockt" }
    }

    init {
        setWillNotDraw(false)
        jarvisShell.content = JarvisShellContent.forState({ state })
        jarvisShell.tiles = JarvisShellContent.tiles(state, System.currentTimeMillis())
        jarvisShell.setOnTouchListener { v, e ->
            if (e.actionMasked == android.view.MotionEvent.ACTION_UP) {
                val shell = jarvisShell.shell
                val target = shell?.let { JarvisShellContent.hitTarget(it, jarvisShell.tiles, e.x / density, e.y / density) }
                val route = target?.let { JarvisShellContent.routes[it] }
                if (route != null) { v.performClick(); onSlot(route, 0f, 0f) }
            }
            true
        }
        addView(jarvisShell, 0)
        addView(hologram)
        addView(avatarPlaceholder)
        applyShellStage()
    }

    /**
     * Builds the element views for a window [windowWidthDp] wide. In the wide composition the navigation rail is a
     * separate view on the left; [railOffset] shifts every slot so the remaining slots keep their reference positions.
     */
    fun configure(windowWidthDp: Float, wide: Boolean, railOffset: Float = 0f, includeNavigation: Boolean = false,
                  windowHeightDp: Float = 0f) {
        configuredWidth = windowWidthDp
        configuredHeight = windowHeightDp
        configuredWide = wide
        configuredNavigation = includeNavigation
        val spec = ContinuumReferenceLayout.create(windowWidthDp, if (wide) windowHeightDp else 0f,
            wide, textScale, compactPresentation)
        if (spec == layoutSpec && offset == railOffset && views.isNotEmpty()) return
        layoutSpec = spec
        offset = railOffset
        // The portrait hero retains its exact layout/3D viewport. Its decorative
        // floor rings may paint behind the later foreground controls, without
        // moving or enlarging their touch targets or introducing another surface.
        clipChildren = spec.referenceCockpit || spec.adaptive || spec[Slot.WORKERS] == null
        views.values.forEach { removeView(it) }
        views.clear()
        hologram.setGeometry(intendedGeometry)
        for ((slot, element) in ContinuumHomeScreen.elements(spec, textScale)) {
            if (slot == Slot.NAV && !includeNavigation) continue
            if (slot == Slot.RAIL) continue
            val view = ElementView(context, element, slot)
            views[slot] = view
            addView(view)
        }
        applyShellStage()
        requestLayout()
        updateWaveAnimation()
    }

    val wide: Boolean get() = layoutSpec?.wide == true
    val referenceCockpit: Boolean get() = layoutSpec?.referenceCockpit == true
    val landscapeCockpit: Boolean get() = layoutSpec?.landscapeCockpit == true
    /** The drawn view of [slot] (animation layer / tests); null if the composition has no such slot. */
    fun viewFor(slot: Slot): View? = if (slot == ContinuumLandscapeCockpitLayout.AVATAR_SLOT) hologram else views[slot]
    /** Geometry belongs to the composition, not to the presence of an adaptive worker card. */
    val intendedGeometry: ContinuumHologramRenderer.Geometry
        get() {
            val spec = layoutSpec ?: return ContinuumHologramRenderer.Geometry.PHONE
            return when {
                spec.referenceCockpit -> ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT
                !spec.adaptive && spec[Slot.WORKERS] != null -> ContinuumHologramRenderer.Geometry.FOLD_PORTRAIT
                spec.wide -> ContinuumHologramRenderer.Geometry.FOLD
                else -> ContinuumHologramRenderer.Geometry.PHONE
            }
        }
    /** Window dp per reference dp of the current composition (null before the first configure). */
    val layoutScale: Float? get() = layoutSpec?.scale

    /** Height of a slot in dp (for tap zones inside an element). */
    fun slotHeightDp(slot: Slot): Float = layoutSpec?.get(slot)?.height ?: 0f

    private var transientCard: ContinuumElementView? = null

    /** Shows a transient notice card over the lower avatar stage (account/document scenes), then removes it. */
    fun showTransientCard(title: String, lines: List<String>, animate: Boolean, durationMs: Long = 6_000L) {
        transientCard?.let { removeView(it) }
        val view = ContinuumElementView(context, CockpitNoticeCard(title, lines, textScale)) { _, _ ->
            transientCard?.let { removeView(it) }; transientCard = null
        }
        view.state = state
        transientCard = view
        addView(view)
        if (animate) {
            view.translationX = 60f * density; view.alpha = 0f
            view.animate().translationX(0f).alpha(1f).setDuration(300L).start()
        }
        postDelayed({ if (transientCard === view) { removeView(view); transientCard = null } }, durationMs)
    }

    private fun transientBox(): ContinuumHomeLayout.Box? {
        val hero = layoutSpec?.get(Slot.HERO) ?: return null
        val h = 84f
        val l = hero.left + 12f - offset
        val r = hero.right - 12f - offset
        return ContinuumHomeLayout.Box(l, hero.bottom - h - 8f, r, hero.bottom - 8f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val spec = layoutSpec
        val contentHeight = ((spec?.contentHeight ?: 0f) * density).toInt()
        for (child in listOf<View>(hologram) + views.values) {
            if (child.parent !== this) continue
            val box = boxOf(child) ?: continue
            child.measure(MeasureSpec.makeMeasureSpec((box.width * density).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((box.height * density).toInt(), MeasureSpec.EXACTLY))
        }
        // The shell stage is one window tall (no scrolling page underneath).
        val h = if (jarvisShellStage && configuredHeight > 0f) (configuredHeight * density).toInt()
            else maxOf(contentHeight, suggestedMinimumHeight)
        jarvisShell.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        if (jarvisShellStage) shellAvatarBox(width, h)?.let { box ->
            hologram.measure(MeasureSpec.makeMeasureSpec((box.width * density).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((box.height * density).toInt(), MeasureSpec.EXACTLY))
        }
        transientCard?.let { card -> transientBox()?.let { box ->
            card.measure(MeasureSpec.makeMeasureSpec((box.width * density).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((box.height * density).toInt(), MeasureSpec.EXACTLY))
        } }
        setMeasuredDimension(width, h)
    }

    /** AVATAR slot of the shell for the given pixel size (dp geometry from JarvisShellLayout). */
    private fun shellAvatarBox(widthPx: Int, heightPx: Int): JarvisShellLayout.Box? {
        val w = widthPx / density; val h = heightPx / density
        if (w < 240f || h < 320f) return null
        return JarvisShellLayout.create(w, h, resources.configuration.fontScale, jarvisShell.foldRail)[JarvisShellLayout.Slot.AVATAR]
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        jarvisShell.layout(0, 0, r - l, b - t)
        if (jarvisShellStage) {
            shellAvatarBox(r - l, b - t)?.let { box ->
                layoutAvatarSlot((box.left * density).toInt(), (box.top * density).toInt(),
                    (box.right * density).toInt(), (box.bottom * density).toInt())
            }
            return
        }
        for (child in listOf<View>(hologram) + views.values) {
            if (child.parent !== this) continue
            val box = boxOf(child) ?: continue
            child.layout((box.left * density).toInt(), (box.top * density).toInt(),
                (box.right * density).toInt(), (box.bottom * density).toInt())
        }
        transientCard?.let { card -> transientBox()?.let { box ->
            card.layout((box.left * density).toInt(), (box.top * density).toInt(),
                (box.right * density).toInt(), (box.bottom * density).toInt())
        } }
    }

    private fun boxOf(child: View): ContinuumHomeLayout.Box? {
        val spec = layoutSpec ?: return null
        val box = if (child === hologram) spec[Slot.HERO] else (child as? ElementView)?.slot?.let { spec[it] }
        return box?.let { ContinuumHomeLayout.Box(it.left - offset, it.top, it.right - offset, it.bottom) }
    }

    override fun onDraw(canvas: Canvas) {
        val spec = layoutSpec ?: return
        backgroundPainter.begin(canvas)
        val saved = canvas.save()
        canvas.scale(density, density)
        canvas.translate(-offset, 0f)
        ContinuumHomeScreen.drawBackground(backgroundPainter, width / density + offset, height / density, spec)
        canvas.restoreToCount(saved)
        // Ambient radiance sits behind child cards and their real measurements.
        sciFiEffects.drawAmbient(canvas, width.toFloat(), height.toFloat())
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // No intercepting overlay and no full-screen blur over foreground text.
        sciFiEffects.drawScanlines(canvas, width.toFloat(), height.toFloat())
    }

    /** Selected navigation index (phone bar / rail); redraws the navigation element. */
    fun selectNavigation(index: Int) {
        (views[Slot.NAV]?.element as? BottomNavElement)?.selected = index
        views[Slot.NAV]?.refresh()
    }

    /** A single drawn element; reports taps with the touch position in element dp coordinates. */
    inner class ElementView(context: Context, val element: ContinuumElement, val slot: Slot) : View(context) {
        private val painter = AndroidContinuumPainter(context.assets)
        private var downX = 0f
        private var downY = 0f

        init {
            isClickable = true
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            ContinuumControls.feedback(this)
            refresh()
            setOnClickListener {
                if (slot in listOf(Slot.VIEW_AVATAR, Slot.VIEW_ORB, Slot.VIEW_COMPACT)) selectPresentation(slot)
                else onSlot(slot, downX / density, downY / density)
            }
        }

        override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            if (element is ReferenceViewModeElement) {
                info.className = android.widget.RadioButton::class.java.name
                info.isCheckable = true
                info.isChecked = isSelected
            } else {
                info.className = android.widget.Button::class.java.name
            }
        }

        fun refresh() {
            (element as? ReferenceViewModeElement)?.let {
                it.selected = when (slot) {
                    Slot.VIEW_COMPACT -> compactPresentation
                    Slot.VIEW_AVATAR -> !compactPresentation && hologram.isNativeHeadEnabled()
                    else -> !compactPresentation && !hologram.isNativeHeadEnabled()
                }
                isSelected = it.selected
            }
            contentDescription = element.describe(state)
            invalidate()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) {
                downX = event.x; downY = event.y
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            painter.begin(canvas)
            val saved = canvas.save()
            // Elements are drawn in reference dp; the slot box is the reference box times the layout scale, so the
            // content must be scaled by the same factor or it overflows narrower windows (360 dp phones, Fold7).
            val k = density * (layoutSpec?.scale ?: 1f)
            canvas.scale(k, k)
            // Decorative phase follows the uptime clock; historical sample coordinates remain unchanged.
            val active = when (slot) {
                Slot.LEARNING -> now() < learningMotionUntil
                Slot.MODEL -> now() < modelMotionUntil
                Slot.SYSTEM_ACTIVITY -> now() < systemMotionUntil
                else -> false
            }
            element.motionPhase = if (active && waveAnimationEnabled()) {
                val until = when (slot) {
                    Slot.LEARNING -> learningMotionUntil
                    Slot.MODEL -> modelMotionUntil
                    else -> systemMotionUntil
                }
                (1f - (until - now()) / 900f).coerceIn(0f, 1f)
            } else null
            element.draw(painter, width / k, height / k, state)
            canvas.restoreToCount(saved)
        }
    }
}

/** Standalone drawn element (phone navigation bar, Fold rail) used outside the scrolling start screen. */
@SuppressLint("ViewConstructor")
class ContinuumElementView(context: Context, val element: ContinuumElement, private val designScale: Float = 1f,
                           private val onTap: (Float, Float) -> Unit) : View(context) {
    private val density = resources.displayMetrics.density
    private val painter = AndroidContinuumPainter(context.assets)
    private var tapX = 0f
    private var tapY = 0f
    var state: ContinuumDashboardState = ContinuumDashboardState()
        set(value) { field = value; contentDescription = element.describe(value); invalidate() }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = element.describe(state)
        setOnClickListener { onTap(tapX / density, tapY / density) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) { tapX = event.x; tapY = event.y }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        painter.begin(canvas)
        val saved = canvas.save()
        // [designScale] = window width / reference width, like the start-screen slots
        val k = density * designScale
        canvas.scale(k, k)
        element.motionPhase = null // Navigation selection is stable; real workload animates in its own card.
        element.draw(painter, width / k, height / k, state)
        canvas.restoreToCount(saved)
    }
}
