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
    private var externalBottomBarHeightDp = 0f
    private var configuredWide = false
    private var configuredNavigation = false
    private val presentationUpdate = Runnable {
        if (isAttachedToWindow) refreshPresentationPreference()
    }
    private val presentationListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "compact") post(presentationUpdate)
    }

    private fun refreshPresentationPreference() {
        val compact = presentationPrefs.getBoolean("compact", false)
        if (compactPresentation == compact) return
        compactPresentation = compact
        if (configuredWidth > 0f) configure(configuredWidth, configuredWide, offset, configuredNavigation,
            configuredHeight, externalBottomBarHeightDp)
        else jarvisShell.compactPresentation = compact
        views.values.forEach { it.refresh() }
        requestLayout()
    }

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
        configure(configuredWidth, configuredWide, offset, configuredNavigation, configuredHeight, externalBottomBarHeightDp)
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
    @Volatile internal var presentationGeneration = 0L
        private set
    fun setForegroundActive(active: Boolean) {
        if (foregroundActive && !active) presentationGeneration++
        foregroundActive = active
        if (!active) hologram.clearMemoryScene()
        if (!active) liveTabs.collapseImmediately()
        jarvisShell.refreshMotionPolicy()
        updateWaveAnimation()
    }
    internal fun isPresentationActive() = foregroundActive && isAttachedToWindow &&
        isShown && windowVisibility == View.VISIBLE
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
        presentationBudgetRegistration?.close()
        presentationBudgetRegistration = hologram.observePresentationBudget { jarvisShell.refreshMotionPolicy() }
        presentationPrefs.registerOnSharedPreferenceChangeListener(presentationListener)
        refreshPresentationPreference()
        slotRegistration?.close()
        slotRegistration = at.heinz.deviceai.cockpit.JarvisAvatarSlot.addListener { post(avatarSlotUpdate) }
        post(avatarSlotUpdate)
        updateWaveAnimation()
    }

    override fun onDetachedFromWindow() {
        presentationGeneration++
        presentationBudgetRegistration?.close()
        presentationBudgetRegistration = null
        liveTabs.collapseImmediately()
        hologram.clearMemoryScene()
        presentationPrefs.unregisterOnSharedPreferenceChangeListener(presentationListener)
        removeCallbacks(presentationUpdate)
        slotRegistration?.close(); slotRegistration = null
        removeCallbacks(avatarSlotUpdate)
        dockedRenderer?.let { runCatching { it.detach() } }
        dockedRenderer = null
        removeCallbacks(waveFrame)
        waveFramePending = false
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != View.VISIBLE) presentationGeneration++
        if (visibility != View.VISIBLE) hologram.clearMemoryScene()
        if (visibility != View.VISIBLE) liveTabs.collapseImmediately()
        updateWaveAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!isShown) presentationGeneration++
        if (!isShown) hologram.clearMemoryScene()
        if (!isShown) liveTabs.collapseImmediately()
        updateWaveAnimation()
    }
    var state: ContinuumDashboardState = ContinuumDashboardState()
        set(value) {
            val until = now() + 900L
            if (value.learning.series != field.learning.series && value.learning.series.size > 1) learningMotionUntil = until
            if (value.model.qualityPercent != field.model.qualityPercent && value.model.qualityPercent != null) modelMotionUntil = until
            if (value.system.activity != field.system.activity && value.system.activity.size > 1) systemMotionUntil = until
            field = value
            liveTabs.updateState(value)
            views.values.forEach { it.refresh() }
            if (jarvisShellStage) jarvisShell.tiles = shellTiles(value)
            shellForeground.invalidate()
            refreshShellActionDescriptions()
            updateWaveAnimation()
        }

    /**
     * Welten-Optik's shell (#479) as the bottom layer. Opt-in stage ("jarvis-shell" preference or
     * [setJarvisShellStage]): the shell draws phone/Fold7/tablet composition with real content from
     * [JarvisShellContent]; the live avatar sits in its AVATAR slot. Off by default until the shell offers touch
     * targets and tile destinations (docs/ARCHITEKTUR-JARVIS.md, "Fehlt an der Schnittstelle").
     */
    val jarvisShell = JarvisShellView(context)
    private val liveTabs = JarvisLiveTabs(context, jarvisShell, ::openJarvisTarget).apply {
        translationZ = density * 2f
    }
    private var presentationBudgetRegistration: AutoCloseable? = null
    private val shellForeground = object : View(context) {
        init {
            tag = "jarvis-foreground"
            isClickable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            translationZ = density * .5f
        }
        override fun onDraw(canvas: Canvas) { jarvisShell.drawForeground(canvas) }
    }
    private val memoryFullStage = object : View(context) {
        private val visibleViewport = android.graphics.Rect()
        init {
            tag = "continuum-memory-fullstage"
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            translationZ = density * .25f
        }
        override fun onDraw(canvas: Canvas) {
            if (!hologram.memoryScene.isActive) return
            if (!getLocalVisibleRect(visibleViewport)) { hologram.clearMemoryScene(); return }
            val save = canvas.save()
            try {
            shellActions.values.forEach { view ->
                if (view.isShown) canvas.clipOutRect(view.left, view.top, view.right, view.bottom)
            }
            if (shellRailScroll.isShown) {
                canvas.clipOutRect(shellRailScroll.left, shellRailScroll.top, shellRailScroll.right, shellRailScroll.bottom)
            }
            val top = visibleViewport.top
            val h = minOf(visibleViewport.height(), (jarvisShell.viewportHeightDp * density).toInt())
            canvas.clipRect(0, top, width, top + h)
            canvas.translate(0f, top.toFloat())
            hologram.drawMemoryScene(canvas, width, h, top)
            } finally { canvas.restoreToCount(save) }
        }
    }
    var jarvisShellStage = presentationPrefs.getBoolean("jarvis-shell", false)
        private set

    /** Existing Activity navigator, including explicit microphone / camera / appearance actions. */
    var onNavigateTarget: ((String) -> Unit)? = null
        set(value) { field = value; refreshShellActionDescriptions() }
    private var selectedJarvisTarget = "home"
    private var pressedJarvisTarget: String? = null
    private val shellActions = linkedMapOf<String, ShellActionView>()
    private var shellActionBounds: Map<String, JarvisShellLayout.Box> = emptyMap()
    private val shellRailList = ShellRailList(context)
    private val shellRailScroll = android.widget.ScrollView(context).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        clipToPadding = true
        contentDescription = "Navigation"
        addView(shellRailList, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Selection follows the existing navigator; it never starts an agent or a model operation. */
    fun selectJarvisTarget(target: String) {
        selectedJarvisTarget = target
        shellActions.values.forEach { it.isSelected = it.target == target }
        shellRailList.refreshSelection()
        if (jarvisShellStage) jarvisShell.tiles = shellTiles(state)
    }

    private fun openJarvisTarget(target: String) {
        val native = onNavigateTarget
        val route = JarvisShellContent.routes[target]
        if (native == null && route == null) return
        if (route != null) selectJarvisTarget(target)
        if (native != null) native(target) else onSlot(requireNotNull(route), 0f, 0f)
    }

    fun setJarvisShellStage(enabled: Boolean) {
        jarvisShellStage = enabled
        presentationPrefs.edit().putBoolean("jarvis-shell", enabled).apply()
        applyShellStage()
        requestLayout()
    }

    private fun applyShellStage() {
        jarvisShell.visibility = if (jarvisShellStage) View.VISIBLE else View.GONE
        shellForeground.visibility = jarvisShell.visibility
        views.values.forEach { it.visibility = if (jarvisShellStage) View.GONE else View.VISIBLE }
        shellActions.values.forEach { it.visibility = if (jarvisShellStage) View.VISIBLE else View.GONE }
        if (jarvisShellStage) applyDeckToActions()
        shellRailScroll.visibility = View.GONE
        liveTabs.visibility = if (jarvisShellStage && !deckCleared) View.VISIBLE else View.GONE
        applyAvatarSlot()
    }

    // --- The ONE avatar slot (JarvisAvatarRenderer, docs/ARCHITEKTUR-JARVIS.md) ---
    /** Cockpit modes own the avatar slot: no head/orb of our own, only a docked renderer or the placeholder. */
    val ownsAvatarSlot: Boolean get() = jarvisShellStage || landscapeCockpit
    private val avatarPlaceholder = AvatarSlotPlaceholder(context)
    private var dockedRenderer: at.heinz.deviceai.cockpit.JarvisAvatarRenderer? = null
    private val avatarRect = android.graphics.Rect()
    private val avatarSlotUpdate = Runnable {
        if (isAttachedToWindow) applyAvatarSlot()
    }

    val avatarRenderer: at.heinz.deviceai.cockpit.JarvisAvatarRenderer? get() = dockedRenderer
    /** Pixel rect of the avatar slot (cockpit modes). */
    val avatarSlotRect: android.graphics.Rect get() = android.graphics.Rect(avatarRect)
    /** The slot is visibly held: docked renderer or the named placeholder. */
    val avatarSlotShown: Boolean get() = dockedRenderer != null || avatarPlaceholder.isShown

    private fun applyAvatarSlot() {
        val owns = ownsAvatarSlot
        val registered = at.heinz.deviceai.cockpit.JarvisAvatarSlot.renderer
        val compatible = registered !is at.heinz.deviceai.avatar.JarvisHoloAvatar || registered.canAttachTo(this)
        val wanted = if (owns && isAttachedToWindow && hologram.parent === this && compatible) registered else null
        if (wanted !== dockedRenderer) {
            dockedRenderer?.let { runCatching { it.detach() } }
            dockedRenderer = null
            if (wanted != null && !avatarRect.isEmpty) { wanted.attach(this, android.graphics.Rect(avatarRect)); dockedRenderer = wanted }
        }
        // Only while the home hosts it: chat and other panes reparent the same avatar and keep it visible.
        if (hologram.parent === this) {
            val sharesLiveSurface = (dockedRenderer as? at.heinz.deviceai.avatar.JarvisHoloAvatar)?.hologram === hologram
            hologram.visibility = if (owns && !sharesLiveSurface) View.GONE else View.VISIBLE
        }
        avatarPlaceholder.visibility = if (owns && hologram.parent === this && dockedRenderer == null) View.VISIBLE else View.GONE
    }

    private var slotRegistration: AutoCloseable? = null

    /** Children added by a docked renderer (attach): sized to the slot; the renderer positions them via x/y. */
    private fun isForeign(child: View) = child !== jarvisShell && child !== hologram && child !== avatarPlaceholder &&
        child !== shellForeground && child !== memoryFullStage && child !== transientCard && child !== shellRailScroll &&
        child !== liveTabs && child !in views.values && child !in shellActions.values

    private fun layoutForeign() {
        val w = avatarRect.width(); val h = avatarRect.height()
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (!isForeign(c)) continue
            c.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
            c.layout(0, 0, w, h)
        }
    }

    /**
     * Re-adopt the shared live avatar after another pane (chat) borrowed it. The avatar must return to its
     * original drawing layer directly above the opaque shell background; index 0 would hide head and orb
     * behind the shell (owner Fold7 report, code42: avatar "vanished" after leaving chat).
     */
    fun adoptHologram() {
        if (hologram.parent === this) return
        (hologram.parent as? ViewGroup)?.removeView(hologram)
        val shellIndex = indexOfChild(jarvisShell)
        addView(hologram, if (shellIndex >= 0) shellIndex + 1 else 0,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** The avatar draws above the shell background whenever this view hosts it. */
    val hologramAboveShell: Boolean get() = hologram.parent === this &&
        indexOfChild(hologram) > indexOfChild(jarvisShell)

    override fun onViewRemoved(child: View?) {
        super.onViewRemoved(child)
        if (child === hologram) {
            hologram.clearMemoryScene()
            child.visibility = View.VISIBLE
            hologram.setCockpitRegions(null, null)
            applyAvatarSlot()
        }
    }

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        if (child === hologram && views.isNotEmpty()) applyAvatarSlot()
    }

    private fun layoutAvatarSlot(left: Int, top: Int, right: Int, bottom: Int) {
        val changed = avatarRect.left != left || avatarRect.top != top || avatarRect.right != right || avatarRect.bottom != bottom
        avatarRect.set(left, top, right, bottom)
        avatarPlaceholder.measure(MeasureSpec.makeMeasureSpec(right - left, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(bottom - top, MeasureSpec.EXACTLY))
        avatarPlaceholder.layout(left, top, right, bottom)
        if (dockedRenderer == null) { if (at.heinz.deviceai.cockpit.JarvisAvatarSlot.renderer != null) post(avatarSlotUpdate) }
        else if (changed) dockedRenderer?.let { r ->
            r.onSlotChanged(android.graphics.Rect(avatarRect))
            // The live adapter handles resize in place. Preserve compatibility with other slot implementations.
            if (r !is at.heinz.deviceai.avatar.JarvisHoloAvatar) {
                post { if (isAttachedToWindow && dockedRenderer === r) r.attach(this, android.graphics.Rect(avatarRect)) }
            }
        }
        if (hologram.parent === this) hologram.layout(left, top, right, bottom)
        layoutForeign()
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
        jarvisShell.content = JarvisShellContent.forState({ state }, grid = { jarvisShell.shell?.grid },
            shell = { jarvisShell.shell }) + mapOf(
                JarvisShellLayout.Slot.NAV_RAIL to JarvisShellRenderer.SlotContent { _, _ -> },
                JarvisShellLayout.Slot.AVATAR to JarvisShellRenderer.SlotContent { _, _ -> })
        jarvisShell.onFrameDrawn = { shellForeground.invalidate() }
        jarvisShell.motionBudgetProvider = {
            if (foregroundActive) hologram.presentationRenderBudget()
            else JarvisRenderQuality.resolve(false, false, null, false, false)
        }
        jarvisShell.tiles = shellTiles(state)
        // Drawing and hit targets are separate. Real Views own DOWN/MOVE/CANCEL/UP, keyboard focus and TalkBack.
        jarvisShell.isClickable = false
        jarvisShell.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(jarvisShell, 0)
        addView(hologram)
        addView(avatarPlaceholder)
        addView(memoryFullStage)
        addView(shellForeground)
        addView(liveTabs)
        hologram.memorySceneHostReady = {
            jarvisShellStage && hologram.parent === this && isPresentationActive() &&
                memoryFullStage.isShown && width > 0 && height > 0
        }
        hologram.memorySceneInvalidation = { memoryFullStage.invalidate() }
        applyShellStage()
    }

    /**
     * Builds the element views for a window [windowWidthDp] wide. In the wide composition the navigation rail is a
     * separate view on the left; [railOffset] shifts every slot so the remaining slots keep their reference positions.
     */
    fun configure(windowWidthDp: Float, wide: Boolean, railOffset: Float = 0f, includeNavigation: Boolean = false,
                  windowHeightDp: Float = 0f, externalBottomBarHeightDp: Float = 0f) {
        require(externalBottomBarHeightDp.isFinite() && externalBottomBarHeightDp >= 0f)
        configuredWidth = windowWidthDp
        configuredHeight = windowHeightDp
        val bottomChanged = this.externalBottomBarHeightDp != externalBottomBarHeightDp
        this.externalBottomBarHeightDp = externalBottomBarHeightDp
        configuredWide = wide
        configuredNavigation = includeNavigation
        jarvisShell.compactPresentation = compactPresentation
        val spec = ContinuumReferenceLayout.create(windowWidthDp, if (wide) windowHeightDp else 0f,
            wide, textScale, compactPresentation)
        if (spec == layoutSpec && offset == railOffset && views.isNotEmpty() && !bottomChanged) return
        layoutSpec = spec
        offset = railOffset
        jarvisShell.contentRailInsetDp = offset
        // The portrait hero retains its exact layout/3D viewport. Its decorative
        // floor rings may paint behind the later foreground controls, without
        // moving or enlarging their touch targets or introducing another surface.
        clipChildren = spec.referenceCockpit || spec.adaptive || spec[Slot.WORKERS] == null
        views.values.forEach { removeView(it) }
        views.clear()
        if (hologram.parent === this) hologram.setGeometry(intendedGeometry)
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

    /**
     * Updates only the available window height after system insets, before the external bottom navigation.
     * The shell reserves that navigation exactly once. No child, renderer, speech session or legacy layout is
     * rebuilt; the next measurement applies the current profile to the existing live surface and controls.
     */
    fun updateAvailableViewport(heightDp: Float) {
        require(heightDp.isFinite() && heightDp > 0f)
        // Extremely short transient windows still expose scrollable content with usable controls.
        val available = heightDp.coerceAtLeast(160f)
        val viewport = (available - externalBottomBarHeightDp).coerceAtLeast(160f)
        if (configuredHeight == available && jarvisShell.viewportHeightDp == viewport) return
        configuredHeight = available
        jarvisShell.viewportHeightDp = viewport
        requestLayout()
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
        view.translationZ = density * .75f
        transientCard = view
        addView(view)
        if (animate) {
            view.translationX = 60f * density; view.alpha = 0f
            view.animate().translationX(0f).alpha(1f).setDuration(300L).start()
        }
        postDelayed({ if (transientCard === view) { removeView(view); transientCard = null } }, durationMs)
    }

    private fun transientBox(widthPx: Int, heightPx: Int): ContinuumHomeLayout.Box? {
        val hero = if (jarvisShellStage) shellAvatarBox(widthPx, heightPx)?.let {
            ContinuumHomeLayout.Box(it.left, it.top, it.right, it.bottom)
        } else layoutSpec?.get(Slot.HERO)
        hero ?: return null
        val railOffset = if (jarvisShellStage) 0f else offset
        val h = 84f
        val l = hero.left + 12f - railOffset
        val r = hero.right - 12f - railOffset
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
        // Responsive phone / medium / large-type content scrolls through the existing Activity ScrollView.
        // Keep the visible height separate, otherwise measuring an expanded page would grow it on every pass.
        val measuredLimitDp = MeasureSpec.getSize(heightMeasureSpec) / density
        val viewport = when {
            MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED && measuredLimitDp >= 160f ->
                if (configuredHeight >= 160f) minOf(configuredHeight, measuredLimitDp) else measuredLimitDp
            configuredHeight >= 160f -> configuredHeight
            else -> maxOf(160f, contentHeight / density)
        } - externalBottomBarHeightDp
        jarvisShell.viewportHeightDp = viewport.coerceAtLeast(160f)
        val h = if (jarvisShellStage && width / density >= 240f)
            (JarvisShellLayout.forContent(width / density, jarvisShell.viewportHeightDp,
                resources.configuration.fontScale, jarvisShell.foldRail, offset,
                compactPresentation = compactPresentation).height * density).toInt()
            else maxOf(contentHeight, suggestedMinimumHeight)
        jarvisShell.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        shellForeground.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        memoryFullStage.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        if (jarvisShellStage) shellGeometry(width, h)?.let {
            measureShellActions(it)
            liveTabs.setGeometry(it)
        }
        liveTabs.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        updateCockpitRegions(width, h)
        if (jarvisShellStage && hologram.parent === this) shellAvatarBox(width, h)?.let { box ->
            hologram.measure(MeasureSpec.makeMeasureSpec((box.right * density).toInt() - (box.left * density).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((box.bottom * density).toInt() - (box.top * density).toInt(), MeasureSpec.EXACTLY))
        }
        transientCard?.let { card -> transientBox(width, h)?.let { box ->
            card.measure(MeasureSpec.makeMeasureSpec((box.width * density).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((box.height * density).toInt(), MeasureSpec.EXACTLY))
        } }
        setMeasuredDimension(width, h)
    }

    /** The same ten destinations and subtitles in every responsive composition. */
    private fun shellTiles(s: ContinuumDashboardState): List<JarvisTile> =
        JarvisShellContent.tiles(s, System.currentTimeMillis(), subtitles = true).map { tile ->
            tile.copy(controlState = when {
                tile.target !in JarvisShellContent.routes -> JarvisControlState.DISABLED
                tile.target == pressedJarvisTarget -> JarvisControlState.PRESSED
                tile.target == selectedJarvisTarget -> JarvisControlState.SELECTED
                else -> JarvisControlState.NORMAL
            })
        }

    /**
     * Avatar slot of the shell for the given pixel size (dp geometry from JarvisShellLayout). In the ref5 grid
     * (Fold7 open) the existing live surface holds both HEAD and ORB, with separate local draw bounds.
     */
    private fun shellAvatarBox(widthPx: Int, heightPx: Int): JarvisShellLayout.Box? = shellGeometry(widthPx, heightPx)?.let { shell ->
        shell[JarvisShellLayout.Slot.AVATAR]
    }

    private fun updateCockpitRegions(widthPx: Int, heightPx: Int) {
        if (hologram.parent !== this) return
        val shell = if (jarvisShellStage) shellGeometry(widthPx, heightPx) else null
        val stage = shell?.get(JarvisShellLayout.Slot.AVATAR)
        val head = shell?.headStage ?: shell?.headBounds
        val orb = shell?.orbBounds
        if (stage == null || head == null || orb == null) {
            hologram.setCockpitRegions(null, null)
            hologram.setHeadFraming(.5f, 1f)
            return
        }
        val originX = (stage.left * density).toInt(); val originY = (stage.top * density).toInt()
        fun local(box: JarvisShellLayout.Box): android.graphics.Rect {
            return android.graphics.Rect((box.left * density).toInt() - originX, (box.top * density).toInt() - originY,
                (box.right * density).toInt() - originX, (box.bottom * density).toInt() - originY)
        }
        // The latest reference has no owner card reserving half the face viewport.
        hologram.setCockpitRegions(local(head), local(orb))
        hologram.setHeadFraming(shell.headAnchorX, shell.headCloseUp, dialogKeepOut(shell, head))
    }

    /**
     * The dialogue card inside the transparent head stage, normalized to that stage, plus a small gap. The live 3D
     * head is drawn above the shell; when a narrow stage (open Fold7) re-solves the head window, the resting face
     * (chin to crown) stays out of the card while the dissolving neck may pass behind it, as in the reference.
     * Null outside the reference cockpit.
     */
    private fun dialogKeepOut(shell: JarvisShellLayout.Shell, stage: JarvisShellLayout.Box): at.heinz.deviceai.avatar3d.AvatarFraming.KeepOut? {
        val dialog = shell[JarvisShellLayout.Slot.DIALOG] ?: return null
        if (shell.headStage == null || stage.width <= 0f || stage.height <= 0f) return null
        val gap = 6f
        return at.heinz.deviceai.avatar3d.AvatarFraming.KeepOut(
            ((dialog.right + gap - stage.left) / stage.width).toDouble(),
            ((dialog.top - gap - stage.top) / stage.height).toDouble())
    }

    private fun shellGeometry(widthPx: Int, heightPx: Int): JarvisShellLayout.Shell? {
        val w = widthPx / density
        val viewport = jarvisShell.viewportHeightDp.takeIf { it >= 160f } ?: (heightPx / density)
        if (w < 240f || viewport < 160f) return null
        return JarvisShellLayout.forContent(w, viewport, resources.configuration.fontScale, jarvisShell.foldRail,
            externalRailWidthDp = offset, compactPresentation = compactPresentation)
    }

    private data class ShellActionSpec(val key: String, val target: String, val label: String,
                                       val box: JarvisShellLayout.Box)
    private var shellRailBounds: JarvisShellLayout.Box? = null

    /** One real focusable View per visible action; the painter is never the input dispatcher. */
    private fun measureShellActions(shell: JarvisShellLayout.Shell) {
        val actions = ArrayList<ShellActionSpec>()
        fun add(key: String, target: String, label: String, box: JarvisShellLayout.Box?) {
            if (box != null && box.width > 0f && box.height > 0f) actions += ShellActionSpec(key, target, label, box)
        }
        val tiles = shellTiles(state)
        shell.tileBoxes().forEachIndexed { i, box ->
            tiles.getOrNull(i)?.let { add("tile:${it.target}", it.target, it.label, box) }
        }
        shell[JarvisShellLayout.Slot.DIALOG]?.let { b ->
            add("dialog:conversation", "chat", "JARVIS: Konversation öffnen",
                b)
            val controls = listOf(Triple("voice-input", "voice-input", "Mikrofon starten"),
                Triple("send", "chat", "Gespräch öffnen"))
            shell.dialogControlBoxes().forEachIndexed { i, box ->
                add("dialog:${controls[i].first}", controls[i].second, controls[i].third, box)
            }
        }
        add("input:conversation", "chat", "Konversation öffnen", shell.inputConversationBox())
        add("camera:settings", "camera-settings", "Kamera und Wahrnehmung: Einstellungen", shell[JarvisShellLayout.Slot.CAMERA_STATUS])
        val panelTargets = listOf(JarvisShellLayout.Slot.ROW_AGENTS to "agents", JarvisShellLayout.Slot.ROW_TASKS to "tasks",
            JarvisShellLayout.Slot.ROW_MEMORY to "memory", JarvisShellLayout.Slot.ROW_MODEL to "model-management",
            JarvisShellLayout.Slot.COCKPIT_SECONDARY to "system", JarvisShellLayout.Slot.STATUS to "system")
        panelTargets.forEach { (slot, target) ->
            val label = if (target == "model-management") "Lokales Modell und Gerät"
                else JarvisTiles.railNav.first { it.target == target }.label
            val touchBox = shell[slot]?.let { box ->
                val minimumHeight = kotlin.math.ceil(48f * density) / density
                // Status uses unused bottom gutter.
                if (slot == JarvisShellLayout.Slot.STATUS && box.height < minimumHeight)
                    box.copy(bottom = Math.nextUp(box.top + minimumHeight)) else box
            }
            add("panel:$slot", target, label, touchBox)
        }
        shell.grid?.areas?.get(JarvisCockpitGrid.Area.BRAND)?.let { box ->
            val h = maxOf(48f, box.height)
            val top = maxOf(0f, box.centerY - h / 2f)
            add("header:home", "home", "Device AI Continuum: Start", box.copy(top = top, bottom = top + h))
        }
        shell.grid?.areas?.get(JarvisCockpitGrid.Area.MODE_PILL)?.let { box ->
            val h = maxOf(48f, box.height)
            val top = maxOf(0f, box.centerY - h / 2f)
            add("header:mode", "avatar-appearance", "JARVIS-Modus: Darstellung", box.copy(top = top, bottom = top + h))
        }
        shell.headerActionBoxes().forEach { (target, box) ->
            val label = when (target) { "notifications" -> "Benachrichtigungen"; "settings" -> "Einstellungen"; else -> "Mehr" }
            add("header:$target", target, label, box)
        }
        shell[JarvisShellLayout.Slot.BOTTOM_NAV]?.let { b ->
            val items = JarvisTiles.bottomNav
            val cell = b.width / items.size
            items.forEachIndexed { i, item ->
                add("bottom:${item.target}", item.target, item.label,
                    JarvisShellLayout.Box(b.left + i * cell, b.top, b.left + (i + 1) * cell, b.bottom))
            }
        }
        val desired = actions.map { it.key }.toSet()
        shellActions.keys.filter { it !in desired }.forEach { key -> shellActions.remove(key)?.let(::removeView) }
        shellActionBounds = actions.associate { it.key to it.box }
        actions.forEach { spec ->
            val view = shellActions.getOrPut(spec.key) {
                ShellActionView(context, spec.key, spec.target, spec.label).also { addView(it) }
            }
            view.visibility = if (deckCleared && (spec.key.startsWith("tile:") || spec.key.startsWith("panel:ROW_")))
                View.INVISIBLE else View.VISIBLE
            view.refresh()
            val px = spec.box.width * density
            val py = spec.box.height * density
            view.measure(MeasureSpec.makeMeasureSpec(px.toInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(py.toInt().coerceAtLeast(1), MeasureSpec.EXACTLY))
        }
        shellRailBounds = shell[JarvisShellLayout.Slot.NAV_RAIL]
        shellRailScroll.visibility = if (shellRailBounds != null) View.VISIBLE else View.GONE
        shellRailBounds?.let { box ->
            shellRailList.labelled = shell.rail != JarvisShellLayout.Rail.ICONS
            shellRailScroll.translationZ = density
            shellRailScroll.measure(MeasureSpec.makeMeasureSpec((box.width * density).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((box.height * density).toInt(), MeasureSpec.EXACTLY))
        }
    }

    private fun layoutShellActions() {
        shellActionBounds.forEach { (key, box) ->
            shellActions[key]?.layout((box.left * density).toInt(), (box.top * density).toInt(),
                (box.right * density).toInt(), (box.bottom * density).toInt())
        }
        shellRailBounds?.let { box ->
            shellRailScroll.layout((box.left * density).toInt(), (box.top * density).toInt(),
                (box.right * density).toInt(), (box.bottom * density).toInt())
        }
    }

    /**
     * Airbus deck (owner brief 06.11.2026): while a work window is open the tiles and lower rows make room. Their hit
     * targets leave with them, so nothing invisible can be touched or focused; they return when the window has closed.
     */
    fun setDeckCleared(cleared: Boolean) {
        deckCleared = cleared
        if (cleared) liveTabs.collapseImmediately()
        liveTabs.visibility = if (jarvisShellStage && !cleared) View.VISIBLE else View.GONE
        jarvisShell.setDeckCleared(cleared)
        applyDeckToActions()
    }

    private var deckCleared = false

    private fun applyDeckToActions() {
        shellActions.forEach { (key, view) ->
            if (key.startsWith("tile:") || key.startsWith("panel:ROW_"))
                view.visibility = if (deckCleared) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun refreshShellActionDescriptions() {
        shellActions.values.forEach { it.refresh() }
        shellRailList.refreshSelection()
    }

    private inner class ShellActionView(context: Context, val key: String, val target: String,
                                        private val label: String) : View(context) {
        init {
            id = View.generateViewId()
            tag = "jarvis-$key"
            isClickable = true
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            translationZ = density
            ContinuumControls.feedback(this, if (key.startsWith("dialog:")) 24f else 10f)
            setOnClickListener { openJarvisTarget(target) }
            refresh()
        }

        fun refresh() {
            val available = target in JarvisShellContent.routes || onNavigateTarget != null
            isEnabled = available
            isSelected = target == selectedJarvisTarget
            val value = if (key.startsWith("tile:")) JarvisShellContent.tileValue(target, state, System.currentTimeMillis()) else null
            contentDescription = label + (value?.let { ": $it" } ?: "") + if (available) "" else ": nicht verfügbar"
        }

        override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = android.widget.Button::class.java.name
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val handled = super.onTouchEvent(event)
            val down = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL && isPressed
            if (key.startsWith("tile:")) {
                val wanted = target.takeIf { down }
                if (pressedJarvisTarget != wanted) {
                    pressedJarvisTarget = wanted
                    jarvisShell.tiles = shellTiles(state)
                }
            }
            if (key.startsWith("rail:")) shellRailList.invalidate()
            return handled
        }
    }

    /** Rail rows scroll on their own when eleven 48dp targets cannot fit the available hero height. */
    private inner class ShellRailList(context: Context) : ViewGroup(context) {
        private val painter = AndroidContinuumPainter(context.assets)
        private val buttons = JarvisTiles.railNav.map { item ->
            ShellActionView(context, "rail:${item.target}", item.target, item.label)
        }
        var labelled = true

        init {
            setWillNotDraw(false)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            clipChildren = false
            buttons.forEach(::addView)
        }

        fun refreshSelection() { buttons.forEach { it.refresh() }; invalidate() }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val font = resources.configuration.fontScale
            val required = (JarvisTiles.railContentHeight(w / density, font) * density).toInt()
            val h = maxOf(required, MeasureSpec.getSize(heightMeasureSpec))
            val rows = JarvisTiles.railItemBoxes(JarvisShellLayout.Box(0f, 0f, w / density, h / density), fontScale = font)
            buttons.forEachIndexed { i, view ->
                view.measure(MeasureSpec.makeMeasureSpec((rows[i].width * density).toInt().coerceAtLeast(0), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((rows[i].height * density).toInt(), MeasureSpec.EXACTLY))
            }
            setMeasuredDimension(w, h)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val rows = JarvisTiles.railItemBoxes(JarvisShellLayout.Box(0f, 0f, width / density, height / density),
                fontScale = resources.configuration.fontScale)
            buttons.forEachIndexed { i, view ->
                val box = rows[i]
                view.layout((box.left * density).toInt(), (box.top * density).toInt(),
                    (box.right * density).toInt(), (box.bottom * density).toInt())
            }
        }

        override fun onDraw(canvas: Canvas) {
            painter.begin(canvas)
            val save = canvas.save()
            canvas.scale(density, density)
            JarvisTiles.drawRail(painter, JarvisShellLayout.Box(0f, 0f, width / density, height / density),
                selected = JarvisTiles.railNav.indexOfFirst { it.target == selectedJarvisTarget }, labelled = labelled,
                fontScale = resources.configuration.fontScale, pressed = buttons.indexOfFirst { it.isPressed })
            canvas.restoreToCount(save)
        }
    }

    /** Publishes the ref5 user card bounds (window pixels) for Lückenfüller's view; null outside the grid. */
    private fun publishUserCardBounds(widthPx: Int, heightPx: Int) {
        val box = shellGeometry(widthPx, heightPx)?.get(JarvisShellLayout.Slot.USER_CARD)
        at.heinz.deviceai.cockpit.JarvisUserCardSlot.bounds = box?.let {
            android.graphics.Rect((it.left * density).toInt(), (it.top * density).toInt(), (it.right * density).toInt(), (it.bottom * density).toInt())
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        jarvisShell.layout(0, 0, r - l, b - t)
        shellForeground.layout(0, 0, r - l, b - t)
        memoryFullStage.layout(0, 0, r - l, b - t)
        liveTabs.layout(0, 0, r - l, b - t)
        updateCockpitRegions(r - l, b - t)
        transientCard?.let { card -> transientBox(r - l, b - t)?.let { box ->
            card.layout((box.left * density).toInt(), (box.top * density).toInt(),
                (box.right * density).toInt(), (box.bottom * density).toInt())
        } }
        if (jarvisShellStage) {
            publishUserCardBounds(r - l, b - t)
            if (changed) jarvisShell.tiles = shellTiles(state)
            shellAvatarBox(r - l, b - t)?.let { box ->
                layoutAvatarSlot((box.left * density).toInt(), (box.top * density).toInt(),
                    (box.right * density).toInt(), (box.bottom * density).toInt())
            }
            layoutShellActions()
            return
        }
        for (child in listOf<View>(hologram) + views.values) {
            if (child.parent !== this) continue
            val box = boxOf(child) ?: continue
            child.layout((box.left * density).toInt(), (box.top * density).toInt(),
                (box.right * density).toInt(), (box.bottom * density).toInt())
        }
        if (ownsAvatarSlot) boxOf(hologram)?.let { box ->
            layoutAvatarSlot((box.left * density).toInt(), (box.top * density).toInt(),
                (box.right * density).toInt(), (box.bottom * density).toInt())
        }
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
