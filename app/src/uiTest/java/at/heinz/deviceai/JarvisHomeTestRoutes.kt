package at.heinz.deviceai

import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.JarvisShellLayout
import org.junit.Assert.*

internal object JarvisHomeTestRoutes {
    val features = listOf("chat", "memory", "logic", "research", "agents", "tasks", "web", "learning", "device", "system")
    val rail = listOf("home") + features
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
        val rails = all.filter { it.tag?.toString()?.startsWith("jarvis-rail:") == true }
        val prefix: String
        val expected: List<String>
        val actual: List<View>
        if (shell.hasFeatureTiles) {
            assertNull("Compact cards replace the side rail", shell[JarvisShellLayout.Slot.NAV_RAIL])
            assertEquals("Compact Home retains ten cards", 10, cards.size)
            assertTrue("Hidden rail cannot duplicate compact accessibility", rails.none { visibleWithinHome(it, home) })
            prefix = "jarvis-tile:"
            expected = features
            actual = cards
        } else {
            assertNull("Wide Home has no duplicate card slot", shell[JarvisShellLayout.Slot.TILES])
            assertTrue("Wide Home removes native card duplicates", cards.isEmpty())
            assertNotNull("Wide Home exposes its actual side rail", shell[JarvisShellLayout.Slot.NAV_RAIL])
            prefix = "jarvis-rail:"
            expected = rail
            actual = rails
        }
        assertEquals("One native navigation control per destination", expected.map { prefix + it }, actual.map { it.tag })
        actual.forEach { view ->
            assertTrue("${view.tag} remains present in Home", visibleWithinHome(view, home))
            assertTrue("${view.tag} is actionable", view.isClickable && view.isFocusable && view.isEnabled)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, view.importantForAccessibility)
            assertFalse("${view.tag} has an accessible label", view.contentDescription.isNullOrBlank())
        }
        return actual
    }

    fun destination(root: View, target: String): View = controls(root).single {
        it.tag.toString().substringAfter(':') == target
    }

    fun deck(root: View): List<View> {
        val home = home(root)
        val shell = requireNotNull(home.jarvisShell.currentShell())
        val all = descendants(home)
        val panels = rows.map { row ->
            val matches = all.filter { it.tag == "jarvis-panel:$row" }
            assertEquals("The deck owns a real $row action", 1, matches.size)
            matches.single()
        }
        val cards = all.filter { it.tag?.toString()?.startsWith("jarvis-tile:") == true }
        assertEquals("Only compact Home has feature cards", if (shell.hasFeatureTiles) 10 else 0, cards.size)
        return panels + cards
    }
}
