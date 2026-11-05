package at.heinz.deviceai.ui

import android.app.Activity
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.GazeTarget

/** Owns presentation, never tool access. */
class JarvisWorkWindowHost(
    private val activity: Activity,
    private val director: AvatarDirector
) {
    private var current: JarvisWorkWindow? = null
    private var serial = 0L
    private var back: android.window.OnBackInvokedCallback? = null
    val isShowing: Boolean get() = current?.isShowing == true

    fun close(immediate: Boolean = false) { current?.close(immediate) }

    fun show(home: ContinuumHomeView, body: View, title: String,
             onClosed: () -> Unit): JarvisWorkWindow? {
        if (!home.jarvisShellStage || !home.isShown) return null
        val container = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        val shell = home.jarvisShell
        val geometry = shell.shell ?: return null
        val density = activity.resources.displayMetrics.density
        val source = home.findViewWithTag<View>("jarvis-tile:system") ?: return null
        val origin = localBounds(source, container)
        var parent = home.parent
        while (parent is View) {
            if (parent is ScrollView) { parent.scrollTo(0, 0); break }
            parent = parent.parent
        }
        val offset = localBounds(shell, container)
        val viewport = RectF(0f, 0f, container.width.toFloat(),
            minOf(container.height.toFloat(), offset.top + geometry.viewportHeight * density))
        fun local(box: JarvisShellLayout.Box) = RectF(offset.left + box.left * density,
            offset.top + box.top * density, offset.left + box.right * density,
            offset.top + box.bottom * density)
        val face = (geometry.headStage ?: geometry.headBounds)?.let(::local)
        val protected = listOfNotNull(face, geometry[JarvisShellLayout.Slot.DIALOG]?.let(::local))
        val withOrb = protected + listOfNotNull(geometry.orbBounds?.let(::local))
        val destination = JarvisWorkWindowGeometry.destination(viewport, withOrb, density)
            ?: return null
        close(immediate = true)
        val id = "work-window-${++serial}"
        val window = JarvisWorkWindow(activity, shell::observeMotionFrames) { shell.invalidate() }
        var releaseGeometry: () -> Unit = {}
        container.addView(window, ViewGroup.LayoutParams(-1, -1))
        val accepted = window.open(body, origin, destination, title, withOrb, onClosed = {
            releaseGeometry()
            (window.parent as? ViewGroup)?.removeView(window)
            director.windowClosed(id)
            if (current === window) {
                current = null
                unregisterBack()
                onClosed()
            }
        })
        if (!accepted) { window.dispose(); container.removeView(window); return null }
        current = window
        val observer = home.viewTreeObserver
        val scroll = ViewTreeObserver.OnScrollChangedListener {
            if (current === window && localBounds(shell, container) != offset) window.close(immediate = true)
        }
        observer.addOnScrollChangedListener(scroll)
        releaseGeometry = { if (observer.isAlive) observer.removeOnScrollChangedListener(scroll) }
        val headX = face?.centerX() ?: viewport.centerX()
        val headY = face?.centerY() ?: viewport.centerY()
        director.windowOpened(id, GazeTarget(
            ((destination.centerX() - headX) / viewport.width() * 2f).coerceIn(-.6f, .6f),
            ((headY - destination.centerY()) / viewport.height()).coerceIn(-.4f, .4f)))
        if (Build.VERSION.SDK_INT >= 33) {
            val callback = android.window.OnBackInvokedCallback { close() }
            back = callback
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
        }
        return window
    }

    private fun unregisterBack() {
        if (Build.VERSION.SDK_INT >= 33) back?.let(activity.onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
        back = null
    }

    private fun localBounds(view: View, root: View): RectF {
        val point = IntArray(2); val rootPoint = IntArray(2)
        view.getLocationInWindow(point); root.getLocationInWindow(rootPoint)
        val x = (point[0] - rootPoint[0]).toFloat(); val y = (point[1] - rootPoint[1]).toFloat()
        return RectF(x, y, x + view.width, y + view.height)
    }
}
