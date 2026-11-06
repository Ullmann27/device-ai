package at.heinz.deviceai.ui

import android.app.Activity
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
    private var cameraHome: ContinuumHomeView? = null
    private var serial = 0L
    private var back: android.window.OnBackInvokedCallback? = null
    private var currentId: String? = null
    private var currentGaze: GazeTarget? = null
    /** Window whose content the local model is really working on (JARVIS reads it until the work ends). */
    private var workingId: String? = null
    val isShowing: Boolean get() = current?.isShowing == true

    /**
     * The local model starts real work on the content of the window on screen (a document draft): JARVIS keeps his
     * eyes on it and reads – the director holds the gaze until [workEnded], STOP, speech or the window closes.
     */
    fun workStarted(): Boolean {
        val id = currentId ?: return false
        if (current?.isShowing != true) return false
        val admitted = director.windowWorkStarted(id, currentGaze ?: GazeTarget.WORK_WINDOW)
        workingId = if (admitted) id else null
        return admitted
    }

    /** The real work ended (result, failure, STOP): the reading gaze ends with it. */
    fun workEnded() {
        workingId?.let(director::windowWorkEnded)
        workingId = null
    }

    fun close(immediate: Boolean = false) {
        if (immediate) cameraHome?.hologram?.setWorkWindowCamera(false, immediate = true)
        current?.close(immediate)
    }

    /**
     * Unfolds [body] as the cockpit work window of [scene]: from the tile the owner touched, or – without that
     * tile – from the point beside the face Jarvis glances at first. Null when the START cockpit cannot host it.
     */
    fun show(home: ContinuumHomeView, body: View, title: String,
             scene: JarvisActionScenes.Scene = STATUS_SCENE, emblem: android.graphics.drawable.Drawable? = null,
             crystal: Boolean = false, fromCurrent: Boolean = false, onClosed: () -> Unit): JarvisWorkWindow? {
        if (!home.jarvisShellStage || !home.isShown) return null
        val container = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        val shell = home.jarvisShell
        var parent = home.parent
        var background: ScrollView? = null
        while (parent is View) {
            if (parent is ScrollView) {
                background = parent
                parent.scrollTo(0, 0)
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
        val planned = plan(home, container, scene, fresh = false) ?: return null
        val first = previous?.let { Planned(JarvisWorkWindow.Plan(it, planned.plan.destination, planned.plan.protectedBounds),
            planned.face, planned.viewport) } ?: planned
        close(immediate = true)
        val id = "work-window-${++serial}"
        val window = JarvisWorkWindow(activity, shell::observeMotionFrames) { shell.invalidate() }
        var releaseGeometry: () -> Unit = {}
        container.addView(window, ViewGroup.LayoutParams(-1, -1))
        // A first layout at another size (rotation/fold/keyboard still pending) plans again on the real size.
        val refit = {
            if (current === window) plan(home, container, scene, fresh = true)?.also {
                // The window moved to the real size: JARVIS looks where it is now (#565 review) and the camera leans
                // towards its new side (progress is still ~0).
                currentGaze = gaze(it)
                director.windowMoved(id, gaze(it))
                home.hologram.setWorkWindowCamera(true, cameraSide(it))
            }?.plan else null
        }
        val accepted = window.open(body, first.plan.origin, first.plan.destination, title, first.plan.protectedBounds,
            accent = scene.accent, emblem = emblem, refit = refit, crystal = crystal, onClosed = {
            releaseGeometry()
            (window.parent as? ViewGroup)?.removeView(window)
            director.windowClosed(id)
            if (workingId == id) workingId = null
            if (current === window) {
                current = null
                currentId = null
                currentGaze = null
                home.hologram.setWorkWindowCamera(false)
                cameraHome = null
                home.setDeckCleared(false)
                unregisterBack()
                onClosed()
            }
        })
        if (!accepted) { window.dispose(); container.removeView(window); return null }
        current = window
        currentId = id
        currentGaze = gaze(first)
        // Airbus deck: the cockpit makes room for the window and gets it back after the window has folded away.
        home.setDeckCleared(true)
        val initialX = background?.scrollX ?: 0
        val initialY = background?.scrollY ?: 0
        background?.setOnScrollChangeListener { _, x, y, _, _ ->
            if (current === window && (x != initialX || y != initialY)) window.close(immediate = true)
        }
        releaseGeometry = { if (current === window) background?.setOnScrollChangeListener(null) }
        // #571: the bounded camera move follows the accepted window (towards its side, at most 2.5 % stage travel).
        cameraHome = home
        home.hologram.setWorkWindowCamera(true, cameraSide(first))
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
        return GazeTarget(((destination.centerX() - headX) / viewport.width() * 2f).coerceIn(-.6f, .6f),
            ((headY - destination.centerY()) / viewport.height()).coerceIn(-.4f, .4f))
    }

    /** Camera lean of #571: the window's horizontal offset from the face, in viewport half-widths. */
    private fun cameraSide(planned: Planned): Float {
        val headX = planned.face?.centerX() ?: planned.viewport.centerX()
        return ((planned.plan.destination.centerX() - headX) / planned.viewport.width() * 2f).coerceIn(-1f, 1f)
    }

    /**
     * Origin, destination and protected areas (face, dialog, orb) of [scene] in [container] pixels. A first plan uses
     * the geometry the owner sees (last drawn shell, none before the first draw); a [fresh] replan uses the shell's
     * geometry for its current size. Null when the START cockpit has no room for a window.
     */
    private fun plan(home: ContinuumHomeView, container: ViewGroup, scene: JarvisActionScenes.Scene,
                     fresh: Boolean): Planned? {
        val shell = home.jarvisShell
        val geometry = (if (fresh) shell.currentShell() else shell.shell) ?: return null
        val density = activity.resources.displayMetrics.density
        val offset = localBounds(shell, container)
        val viewport = RectF(0f, 0f, container.width.toFloat(),
            minOf(container.height.toFloat(), offset.top + geometry.viewportHeight * density))
        fun local(box: JarvisShellLayout.Box) = RectF(offset.left + box.left * density,
            offset.top + box.top * density, offset.left + box.right * density,
            offset.top + box.bottom * density)
        val face = (geometry.headStage ?: geometry.headBounds)?.let(::local)
        val tile = (scene.origin as? JarvisActionScenes.Origin.Tile)?.let { home.findViewWithTag<View>("jarvis-tile:${it.target}") }
        val origin = tile?.let { localBounds(it, container) }?.takeIf(JarvisWorkWindowGeometry::valid)
            ?: besideFace(face, viewport, density)
        val protected = listOfNotNull(face, geometry[JarvisShellLayout.Slot.DIALOG]?.let(::local))
        val withOrb = protected + listOfNotNull(geometry.orbBounds?.let(::local))
        val destination = JarvisWorkWindowGeometry.destination(viewport, withOrb, density) ?: return null
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

    private fun unregisterBack() {
        if (Build.VERSION.SDK_INT >= 33) back?.let(activity.onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
        back = null
    }

    private companion object {
        val STATUS_SCENE = requireNotNull(JarvisActionScenes.forCentre("Systemstatus"))
    }

    private fun localBounds(view: View, root: View): RectF {
        val point = IntArray(2); val rootPoint = IntArray(2)
        view.getLocationInWindow(point); root.getLocationInWindow(rootPoint)
        val x = (point[0] - rootPoint[0]).toFloat(); val y = (point[1] - rootPoint[1]).toFloat()
        return RectF(x, y, x + view.width, y + view.height)
    }
}
