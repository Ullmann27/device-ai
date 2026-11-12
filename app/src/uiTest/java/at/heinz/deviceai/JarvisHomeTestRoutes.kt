package at.heinz.deviceai

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.JarvisActionScenes
import at.heinz.deviceai.ui.JarvisActionScenes.WorkspaceFootprint
import at.heinz.deviceai.ui.JarvisShellLayout
import org.junit.Assert.*
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.Shadows.shadowOf
import android.os.Looper

internal object JarvisHomeTestRoutes {
    val features = listOf("chat", "memory", "logic", "research", "agents", "tasks", "web", "learning", "device", "system")
    val dockTargets = listOf("home", "chat", "memory", "research", "tasks", "web", "learning", "device")
    val moreTargets = listOf("logic", "agents", "system")
    private val rows = listOf("ROW_AGENTS", "ROW_TASKS", "ROW_MEMORY", "ROW_MODEL")

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun home(root: View): ContinuumHomeView {
        val matches = descendants(root).filterIsInstance<ContinuumHomeView>()
        assertEquals("One current Home owns navigation", 1, matches.size)
        return matches.single()
    }

    private fun visibleWithinHome(view: View, home: ContinuumHomeView): Boolean =
        generateSequence(view) { it.parent as? View }.takeWhile { it !== home }
            .all { it.visibility == View.VISIBLE }

    fun controls(root: View): List<View> {
        val home = home(root)
        val shell = requireNotNull(home.jarvisShell.currentShell())
        val all = descendants(home)
        val cards = all.filter { it.tag?.toString()?.startsWith("jarvis-tile:") == true }
        val dock = all.filter { it.tag?.toString()?.startsWith("jarvis-bottom:") == true }
        val rails = all.filter { it.tag?.toString()?.startsWith("jarvis-rail:") == true }
        assertNull("Home has no fixed navigation rail", shell[JarvisShellLayout.Slot.NAV_RAIL])
        assertTrue("Home removes native rail duplicates", rails.isEmpty())
        val nav = if (shell.hasFeatureTiles) {
            assertNotNull(shell[JarvisShellLayout.Slot.TILES])
            assertEquals("Every Home retains ten cards", 10, cards.size)
            assertEquals("One native navigation control per destination",
                features.map { "jarvis-tile:$it" }, cards.map { it.tag })
            cards
        } else {
            assertNotNull(shell[JarvisShellLayout.Slot.BOTTOM_NAV])
            assertEquals("Eight owner destinations retain their native dock order",
                dockTargets.map { "jarvis-bottom:$it" }, dock.map { it.tag })
            assertEquals("More preserves former destinations", features.toSet(),
                (dockTargets.filterNot { it == "home" } + moreTargets).toSet())
            assertTrue(ContinuumWorkAreaNavigationSpec.moreEntries.map { it.target }.containsAll(moreTargets))
            val headerMore = all.singleOrNull { it.tag == "jarvis-header:more" }
            if (shell.workstation) assertNotNull("New cockpit exposes More in its header", headerMore)
            val more = headerMore ?: all.single { it.tag == "jarvis-panel:STATUS" }
            assertTrue("More is reachable directly or through System", visibleWithinHome(more, home) && more.isClickable && more.isFocusable)
            dock
        }
        nav.forEach { view ->
            assertTrue("${view.tag} remains present in Home", visibleWithinHome(view, home))
            assertTrue("${view.tag} is actionable", view.isClickable && view.isFocusable && view.isEnabled)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, view.importantForAccessibility)
            assertFalse("${view.tag} has an accessible label", view.contentDescription.isNullOrBlank())
        }
        return nav
    }

    fun destination(root: View, target: String): View {
        controls(root).singleOrNull { it.tag.toString().substringAfter(':') == target }?.let { return it }
        if (target == "system") return descendants(home(root)).single { it.tag == "jarvis-panel:STATUS" }
        error("$target is in More; use navigate() for its two native actions")
    }

    fun expectedDirectTargets(root: View) = if (requireNotNull(home(root).jarvisShell.currentShell()).hasFeatureTiles)
        features else dockTargets

    fun navigate(root: View, target: String) {
        if (target !in moreTargets) { touch(root, destination(root, target)); return }
        val more = descendants(home(root)).single { it.tag == "jarvis-header:more" }
        touch(root, more)
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
        assertTrue("The actual More dialog opens", dialog.isShowing)
        val entries = ContinuumWorkAreaNavigationSpec.moreEntries
        val list = dialog.listView
        assertEquals(entries.map { it.title }, (0 until list.adapter.count).map { list.adapter.getItem(it).toString() })
        val index = entries.indexOfFirst { it.target == target }
        assertTrue(index >= 0)
        assertTrue("The actual menu dispatches $target", list.performItemClick(null, index, list.adapter.getItemId(index)))
    }

    /** Eight dock destinations, legacy cards. */
    fun expectedDeckSize(root: View): Int {
        val shell = requireNotNull(home(root).jarvisShell.currentShell())
        return if (shell.hasFeatureTiles) 14 else dockTargets.size
    }

    fun deck(root: View): List<View> {
        val home = home(root)
        val all = descendants(home)
        val panels = rows.mapNotNull { row ->
            all.singleOrNull { it.tag == "jarvis-panel:$row" }
        }
        if (panels.isNotEmpty()) assertEquals("The deck owns the four diagnostic rows", 4, panels.size)
        val cards = all.filter { it.tag?.toString()?.startsWith("jarvis-tile:") == true }
        if (cards.isNotEmpty()) {
            assertEquals("Every deck retains ten cards", 10, cards.size)
            return panels + cards
        }
        val dock = all.filter { it.tag?.toString()?.startsWith("jarvis-bottom:") == true }
        assertEquals("Packed deck retains the complete native dock", dockTargets.map { "jarvis-bottom:$it" }, dock.map { it.tag })
        return dock
    }

    /** Footprint of the work window that the centre called [title] opens. */
    fun footprintOfCentre(title: String): WorkspaceFootprint =
        requireNotNull(JarvisActionScenes.forCentre(title)) { "$title opens a cockpit work window" }.footprint

    /**
     * Airbus deck: a MEDIUM_TERMINAL window makes room by clearing the ten cards; only a FULL_MISSION_BAY window clears
     * the four mission rows as well. The rows of a medium window stay visible and usable.
     */
    fun clearedBy(root: View, footprint: WorkspaceFootprint): List<View> = deck(root).filter { clears(it, footprint) }

    /** The deck controls that stay on the deck while a window of [footprint] is open. */
    fun keptBy(root: View, footprint: WorkspaceFootprint): List<View> = deck(root).filterNot { clears(it, footprint) }

    private fun clears(view: View, footprint: WorkspaceFootprint): Boolean {
        val tag = view.tag.toString()
        return when (footprint) {
            WorkspaceFootprint.CONVERSATION_IDLE -> false
            WorkspaceFootprint.MEDIUM_TERMINAL -> tag.startsWith("jarvis-tile:")
            WorkspaceFootprint.FULL_MISSION_BAY -> tag.startsWith("jarvis-tile:") || tag.startsWith("jarvis-panel:ROW_")
        }
    }

    fun scrollIntoView(target: View) {
        target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), true)
        var parent = target.parent
        while (parent is View) {
            if (parent is ScrollView) {
                val relative = Rect(0, 0, target.width, target.height)
                parent.offsetDescendantRectToMyCoords(target, relative)
                parent.scrollTo(parent.scrollX, (relative.centerY() - parent.height / 2).coerceAtLeast(0))
            }
            parent = parent.parent
        }
    }

    fun visibleBounds(target: View): Rect {
        val visible = Rect()
        assertTrue("Scroll reaches ${target.tag}", target.getGlobalVisibleRect(visible))
        var width = target.width
        var height = target.height
        var ancestor: View? = target.parent as? View
        while (ancestor != null) {
            if (ancestor is ScrollView || ancestor === target.rootView) {
                val viewport = Rect()
                assertTrue("The actual viewport is visible", ancestor.getGlobalVisibleRect(viewport))
                width = minOf(width, viewport.width() - ancestor.paddingLeft - ancestor.paddingRight)
                height = minOf(height, viewport.height() - ancestor.paddingTop - ancestor.paddingBottom)
            }
            ancestor = ancestor.parent as? View
        }
        assertTrue("${target.tag} reaches its available width: $visible/$width", visible.width() >= width - 1)
        assertTrue("${target.tag} reaches its available height: $visible/$height", visible.height() >= height - 1)
        val minimum = 48f * target.resources.displayMetrics.density - 1f
        assertTrue("${target.tag} has a usable visible touch area", visible.width() >= minimum && visible.height() >= minimum)
        return visible
    }

    fun touch(root: View, target: View) {
        scrollIntoView(target)
        val bounds = visibleBounds(target)
        val origin = IntArray(2).also(root::getLocationOnScreen)
        val now = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to now, MotionEvent.ACTION_UP to now + 16L)) {
            val event = MotionEvent.obtain(now, time, action,
                bounds.exactCenterX() - origin[0], bounds.exactCenterY() - origin[1], 0)
            try { assertTrue("The root receives ${target.tag}", root.dispatchTouchEvent(event)) }
            finally { event.recycle() }
        }
    }
}
