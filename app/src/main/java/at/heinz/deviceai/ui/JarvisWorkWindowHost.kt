package at.heinz.deviceai.ui

import android.app.Activity
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.GazeTarget

/** Owns presentation, never tool access. */
class JarvisWorkWindowHost(
    private val activity: Activity,
    private val director: AvatarDirector
) {
    private var current: JarvisWorkWindow? = null
    private var currentFui: HollywoodFuiStageOverlay? = null
    private var cameraHome: ContinuumHomeView? = null
    private var serial = 0L
    private var back: android.window.OnBackInvokedCallback? = null
    private var currentId: String? = null
    private var currentGaze: GazeTarget? = null
    private var currentClientClosed: (() -> Unit)? = null
    private var currentFootprint = JarvisActionScenes.WorkspaceFootprint.CONVERSATION_IDLE
    private var currentCameraSide = 0f
    /** Window whose content the local model is really working on (JARVIS reads it until the work ends). */
    private var workingId: String? = null
    private var emphasizedWork = false
    val isShowing: Boolean get() = current?.isShowing == true

    /**
     * The local model starts real work on the content of the window on screen (a document draft): JARVIS keeps his
     * eyes on it and reads – the director holds the gaze until [workEnded], STOP, speech or the window closes.
     */
    fun workStarted(): Boolean = workStarted(emphasize = false)

    fun workStarted(emphasize: Boolean): Boolean {
        val id = currentId ?: return false
        if (current?.isShowing != true) return false
        val target = currentGaze ?: GazeTarget.WORK_WINDOW
        val admitted = director.windowWorkStarted(id,
            if (emphasize) ToolWorkSceneLease.workingGaze(target) else target)
        emphasizedWork = admitted && emphasize
        workingId = if (admitted) id else null
        currentFui?.setWorkActive(admitted)
        return admitted
    }

    /**
     * Steps of the turn that really runs (CockpitAnimationTriggers.activeTimeline) as the window's status strip – the
     * cards that would cover the avatar's face and the orb dock here. Empty steps hide it; never „fertig“ while running.
     */
    fun showStatus(steps: List<at.heinz.deviceai.cockpit.ProcessStep>) {
        current?.setStatus(statusLine(steps))
        currentFui?.setWorkActive(steps.any { it.state == at.heinz.deviceai.cockpit.StepState.ACTIVE })
    }

    fun setMissionTelemetry(text: String?) {
        currentFui?.setTelemetryText(text)
    }

    fun setFuiLiveData(data: HollywoodFuiLiveData?) {
        currentFui?.setLiveData(data)
    }

    /**
     * Promotes only the visual grammar/footprint of the already-open physical work window.
     * No content, tool state, window id or AvatarDirector lease is replaced.
     */
    fun morphPresentation(home: ContinuumHomeView, scene: JarvisActionScenes.Scene): Boolean {
        val window = current?.takeIf { it.isShowing } ?: return false
        if (cameraHome !== home || currentId == null) return false
        val bounds = window.shownBounds() ?: return false
        currentFootprint = strongerFootprint(currentFootprint, scene.footprint)
        home.setWorkspaceFootprint(currentFootprint)
        home.hologram.setWorkWindowCamera(
            true,
            currentCameraSide,
            profile = cameraProfile(currentFootprint)
        )
        currentFui?.morphInPlace(bounds, scene)
        return true
    }

    /** The real work ended (result, failure, STOP): the reading gaze ends with it. */
    fun workEnded() {
        workingId?.let(director::windowWorkEnded)
        workingId = null
        emphasizedWork = false
        currentFui?.setWorkActive(false)
    }

    fun close(immediate: Boolean = false) {
        if (immediate) cameraHome?.hologram?.setWorkWindowCamera(false, immediate = true)
        currentFui?.close(immediate)
        current?.close(immediate)
    }

    /**
     * Morphs the current work window in place. Geometry, camera framing, deck clearance,
     * window id and AvatarDirector ownership stay continuous.
     */
    fun replaceCurrent(
        home: ContinuumHomeView,
        body: View,
        title: String,
        scene: JarvisActionScenes.Scene,
        emblem: android.graphics.drawable.Drawable? = null,
        crystal: Boolean = false,
        depthLayers: Int = 0,
        onClosed: () -> Unit
    ): JarvisWorkWindow? {
        val window = current?.takeIf { it.isShowing } ?: return null
        if (cameraHome !== home || currentId == null) return null
        if (!window.replaceContent(
                body = body,
                title = title,
                accent = scene.accent,
                emblem = emblem,
                crystal = crystal,
                depthLayers = depthLayers
            )) return null
        currentClientClosed = onClosed
        morphPresentation(home, scene)
        return window
    }

    /**
     * Unfolds [body] as the cockpit work window of [scene]: from the tile the owner touched, or – without that
     * tile – from the point beside the face Jarvis glances at first. Null when the START cockpit cannot host it.
     */
    fun show(home: ContinuumHomeView, body: View, title: String,
             scene: JarvisActionScenes.Scene = STATUS_SCENE, emblem: android.graphics.drawable.Drawable? = null,
             crystal: Boolean = false, fromCurrent: Boolean = false, depthLayers: Int = 0,
             handover: android.widget.ImageView? = null, onClosed: () -> Unit): JarvisWorkWindow? {
        if (!home.jarvisShellStage || !home.isShown) return null
        val container = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        val shell = home.jarvisShell
        var parent = home.parent
        var background: ScrollView? = null
        while (parent is View) {
            if (parent is ScrollView) {
                background = parent
                break
            }
            parent = parent.parent
        }
        // A result that comes out of the window on screen (document → draft) unfolds from where that window is.
        val previous = if (fromCurrent) current?.shownBounds()?.let { shown ->
            // The result grows out of the middle of that window (its document), not from a tile across the cockpit.
            val w = shown.width() * .3f; val h = shown.height() * .3f
            RectF(shown.centerX() - w / 2f, shown.centerY() - h / 2f, shown.centerX() + w / 2f, shown.centerY() + h / 2f)
        } else null
        // Two layers (owner brief 06.11.): the window on screen steps back as the document plane behind the result, its
        // real page flies into the result's own page view, its further pages keep standing behind the plane.
        val recession = if (fromCurrent) current?.let { shown -> shown.shownBounds()?.let { bounds ->
            val pageView = shown.findViewWithTag<View>(DOCUMENT_PAGE_TAG)?.takeIf { it.isShown && it.width > 0 && it.height > 0 }
            val page = pageView?.let { localBounds(it, container) }?.takeIf(JarvisWorkWindowGeometry::valid)
            val image = handover?.drawable?.constantState?.newDrawable(activity.resources)?.mutate()
            JarvisWorkWindow.Recession(bounds, page, image, handover)
        } } else null
        val inheritedLayers = if (recession != null) maxOf(depthLayers, current?.depthLayers ?: 0) else depthLayers
        val planned = plan(home, container, scene) ?: return null
        val first = previous?.let { Planned(JarvisWorkWindow.Plan(it, planned.plan.destination, planned.plan.protectedBounds),
            planned.face, planned.viewport) } ?: planned
        close(immediate = true)
        val id = "work-window-${++serial}"
        val window = JarvisWorkWindow(activity, shell::observeMotionFrames) { shell.invalidate() }
        val fui = HollywoodFuiStageOverlay(activity)
        fui.requestFrame = { shell.invalidate() }
        window.onCinematicFrame = fui::present
        val presentation = JarvisCinematicSettings.preferences(activity)
        fun applyCinematics() {
            val enabled = JarvisCinematicSettings.enabled(activity)
            fui.cinematicEffectsEnabled = enabled
            window.cinematicEffectsEnabled = enabled
        }
        applyCinematics()
        val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == JarvisCinematicSettings.KEY) applyCinematics()
        }
        presentation.registerOnSharedPreferenceChangeListener(preferenceListener)
        var releaseGeometry: () -> Unit = {}
        // One cockpit budget drives the decorative layer too (thermal/power/inference): no second quality model.
        fui.setRenderBudget(home.hologram.presentationRenderBudget())
        val budgetSubscription = runCatching {
            home.hologram.observePresentationBudget { fui.setRenderBudget(home.hologram.presentationRenderBudget()) }
        }.getOrNull()
        container.addView(fui, ViewGroup.LayoutParams(-1, -1))
        container.addView(window, ViewGroup.LayoutParams(-1, -1))
        // A first layout at another size (rotation/fold/keyboard still pending) plans again on the real size.
        val refit = {
            if (current === window) plan(home, container, scene)?.also {
                // The window moved to the real size: JARVIS looks where it is now (#565 review) and the camera leans
                // towards its new side (progress is still ~0).
                currentGaze = gaze(it)
                director.windowMoved(id, if (workingId == id && emphasizedWork)
                    ToolWorkSceneLease.workingGaze(gaze(it)) else gaze(it))
                currentCameraSide = cameraSide(it)
                home.hologram.setWorkWindowCamera(true, currentCameraSide,
                    profile = cameraProfile(currentFootprint))
                fui.retarget(it.plan.destination, it.plan.origin, it.plan.protectedBounds)
            }?.plan else null
        }
        val accepted = window.open(body, first.plan.origin, first.plan.destination, title, first.plan.protectedBounds,
            accent = scene.accent, emblem = emblem, refit = refit, crystal = crystal, depthLayers = inheritedLayers,
            recession = recession, onClosed = {
            releaseGeometry()
            presentation.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            runCatching { budgetSubscription?.close() }
            (window.parent as? ViewGroup)?.removeView(window)
            (fui.parent as? ViewGroup)?.removeView(fui)
            director.windowClosed(id)
            if (workingId == id) { workingId = null; emphasizedWork = false }
            if (currentFui === fui) currentFui = null
            if (current === window) {
                val clientClosed = currentClientClosed
                current = null
                currentId = null
                currentGaze = null
                currentClientClosed = null
                currentFootprint = JarvisActionScenes.WorkspaceFootprint.CONVERSATION_IDLE
                currentCameraSide = 0f
                home.hologram.setWorkWindowCamera(false)
                cameraHome = null
                home.setWorkspaceFootprint(JarvisActionScenes.WorkspaceFootprint.CONVERSATION_IDLE)
                unregisterBack()
                clientClosed?.invoke()
            }
        })
        if (!accepted) {
            presentation.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            runCatching { budgetSubscription?.close() }
            window.dispose()
            container.removeView(window)
            container.removeView(fui)
            return null
        }
        currentClientClosed = onClosed
        current = window
        currentFui = fui
        currentId = id
        currentGaze = gaze(first)
        // The light anchor stands where the window really comes from (avatar core or the tapped control, #643 C).
        fui.open(first.plan.destination, scene, first.plan.origin, first.plan.protectedBounds)
        // Airbus deck: footprint controls how much of the cockpit physically clears for this real window.
        currentFootprint = scene.footprint
        home.setWorkspaceFootprint(currentFootprint)
        val initialX = background?.scrollX ?: 0
        val initialY = background?.scrollY ?: 0
        background?.setOnScrollChangeListener { _, x, y, _, _ ->
            if (current === window && (x != initialX || y != initialY)) window.close(immediate = true)
        }
        releaseGeometry = { if (current === window) background?.setOnScrollChangeListener(null) }
        // #571: the bounded camera move follows the accepted window (towards its side, at most 2.5 % stage travel).
        cameraHome = home
        currentCameraSide = cameraSide(first)
        home.hologram.setWorkWindowCamera(true, currentCameraSide,
            profile = cameraProfile(currentFootprint))
        director.windowOpened(id, gaze(first))
        if (Build.VERSION.SDK_INT >= 33) {
            val callback = android.window.OnBackInvokedCallback { close() }
            back = callback
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
        }
        return window
    }

    private class Planned(val plan: JarvisWorkWindow.Plan, val face: RectF?, val viewport: RectF)

    /** Where JARVIS looks for the window of [planned]: its centre relative to the face, in the gaze range. */
    private fun gaze(planned: Planned): GazeTarget {
        val viewport = planned.viewport
        val destination = planned.plan.destination
        val headX = planned.face?.centerX() ?: viewport.centerX()
        val headY = planned.face?.centerY() ?: viewport.centerY()
        return GazeTarget(((destination.centerX() - headX) / viewport.width() * 2f).coerceIn(-.7f, .7f),
            ((headY - destination.centerY()) / viewport.height()).coerceIn(-.4f, .4f))
    }

    /** Camera lean of #571: the window's horizontal offset from the face, in viewport half-widths. */
    private fun cameraSide(planned: Planned): Float {
        val headX = planned.face?.centerX() ?: planned.viewport.centerX()
        return ((planned.plan.destination.centerX() - headX) / planned.viewport.width() * 2f).coerceIn(-1f, 1f)
    }

    /** Current geometry, including undrawn layouts. */
    private fun plan(home: ContinuumHomeView, container: ViewGroup, scene: JarvisActionScenes.Scene): Planned? {
        val shell = home.jarvisShell
        val geometry = shell.currentShell() ?: return null
        val density = activity.resources.displayMetrics.density
        val offset = localBounds(shell, container)
        val viewport = visibleBounds(shell, container) ?: return null
        fun local(box: JarvisShellLayout.Box) = RectF(offset.left + box.left * density,
            offset.top + box.top * density, offset.left + box.right * density,
            offset.top + box.bottom * density)
        val face = (geometry.headStage ?: geometry.headBounds)?.let(::local)
        val protected = listOfNotNull(face, geometry[JarvisShellLayout.Slot.DIALOG]?.let(::local))
        val withOrb = protected + listOfNotNull(geometry.orbBounds?.let(::local))
        val destination = JarvisWorkWindowGeometry.destination(viewport, withOrb, density) ?: return null
        val navigation = (scene.origin as? JarvisActionScenes.Origin.Tile)?.let {
            val prefix = if (geometry.hasFeatureTiles) "jarvis-tile:" else "jarvis-rail:"
            home.findViewWithTag<View>("$prefix${it.target}")
        }
        val origin = navigation?.let { visibleBounds(it, container) }
            ?: besideFace(face, viewport, density)
        return Planned(JarvisWorkWindow.Plan(origin, destination, withOrb), face, viewport)
    }

    /**
     * A small seed beside the face, on the side towards the viewport centre, where the energy pulse forms. It stays
     * clear of the face plus the window's 8 dp route clearance, so the light trail can start there.
     */
    private fun besideFace(face: RectF?, viewport: RectF, density: Float): RectF {
        val size = 40f * density
        val gap = 12f * density
        val x = face?.let { if (it.centerX() > viewport.centerX()) it.left - gap - size else it.right + gap }
            ?: viewport.centerX() - size / 2f
        val y = (face?.centerY() ?: viewport.centerY()) - size / 2f
        val left = x.coerceIn(viewport.left, viewport.right - size)
        val top = y.coerceIn(viewport.top, viewport.bottom - size)
        return RectF(left, top, left + size, top + size)
    }

    private fun strongerFootprint(
        a: JarvisActionScenes.WorkspaceFootprint,
        b: JarvisActionScenes.WorkspaceFootprint
    ): JarvisActionScenes.WorkspaceFootprint = if (a.ordinal >= b.ordinal) a else b

    private fun cameraProfile(
        footprint: JarvisActionScenes.WorkspaceFootprint
    ): at.heinz.deviceai.avatar3d.AvatarCameraFraming.Profile = when (footprint) {
        JarvisActionScenes.WorkspaceFootprint.CONVERSATION_IDLE ->
            at.heinz.deviceai.avatar3d.AvatarCameraFraming.Profile.LEGACY
        JarvisActionScenes.WorkspaceFootprint.MEDIUM_TERMINAL ->
            at.heinz.deviceai.avatar3d.AvatarCameraFraming.Profile.MEDIUM_TERMINAL
        JarvisActionScenes.WorkspaceFootprint.FULL_MISSION_BAY ->
            at.heinz.deviceai.avatar3d.AvatarCameraFraming.Profile.FULL_MISSION_BAY
    }

    private fun unregisterBack() {
        if (Build.VERSION.SDK_INT >= 33) back?.let(activity.onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
        back = null
    }

    internal companion object {
        private val STATUS_SCENE = requireNotNull(JarvisActionScenes.forCentre("Systemstatus"))
        /** The real page view of the document window (AgentActivity.presentDocumentScene): where the page recedes from. */
        const val DOCUMENT_PAGE_TAG = "jarvis-document-page"
        /** „Auftrag angenommen · Bearbeitung läuft“ – the last steps, each label once, with its real state. */
        fun statusLine(steps: List<at.heinz.deviceai.cockpit.ProcessStep>): String? {
            if (steps.isEmpty()) return null
            return steps.distinctBy { it.key }.takeLast(3).joinToString(" · ") { step ->
                when (step.state) {
                    at.heinz.deviceai.cockpit.StepState.ACTIVE -> "${step.label} läuft"
                    at.heinz.deviceai.cockpit.StepState.WAITING -> "${step.label} wartet auf Freigabe"
                    at.heinz.deviceai.cockpit.StepState.DONE -> step.label
                    at.heinz.deviceai.cockpit.StepState.FAILED -> "${step.label} fehlgeschlagen"
                    at.heinz.deviceai.cockpit.StepState.BLOCKED -> "${step.label} blockiert"
                }
            }
        }

    }

    private fun localBounds(view: View, root: View): RectF {
        val point = IntArray(2); val rootPoint = IntArray(2)
        view.getLocationInWindow(point); root.getLocationInWindow(rootPoint)
        val x = (point[0] - rootPoint[0]).toFloat(); val y = (point[1] - rootPoint[1]).toFloat()
        return RectF(x, y, x + view.width, y + view.height)
    }

    /** Ancestor clipping in overlay coordinates. */
    private fun visibleBounds(view: View, root: View): RectF? {
        if (!view.isShown || !root.isShown) return null
        val visible = Rect()
        val rootVisible = Rect()
        val rootOffset = Point()
        if (!view.getGlobalVisibleRect(visible) || !root.getGlobalVisibleRect(rootVisible, rootOffset) ||
            !visible.intersect(rootVisible)) return null
        return RectF(visible).apply { offset(-rootOffset.x.toFloat(), -rootOffset.y.toFloat()) }
            .takeIf(JarvisWorkWindowGeometry::valid)
    }
}
