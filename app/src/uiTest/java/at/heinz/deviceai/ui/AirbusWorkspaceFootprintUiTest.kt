package at.heinz.deviceai.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.ContinuumHologramView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-mdpi")
class AirbusWorkspaceFootprintUiTest {
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(descendants(view.getChildAt(i)))
    }

    private fun layout(home: ContinuumHomeView) {
        home.measure(
            View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY)
        )
        home.layout(0, 0, 884, 760)
    }

    @Test fun footprintSurvivesRelayoutWithoutInvisibleClickableControls() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val hologram = ContinuumHologramView(activity)
            val home = ContinuumHomeView(activity, hologram) { _, _, _ -> }
            home.configure(884f, wide = true, windowHeightDp = 760f)
            home.setJarvisShellStage(true)
            activity.setContentView(home)
            controller.visible()
            layout(home)

            fun tagged(tag: String): View {
                val found = descendants(home).firstOrNull { it.tag == tag }
                assertNotNull(tag, found)
                return requireNotNull(found)
            }
            val tile = descendants(home).firstOrNull { it.tag?.toString()?.startsWith("jarvis-tile:") == true }
            assertNotNull("at least one tile target", tile)
            val row = tagged("jarvis-panel:ROW_AGENTS")
            val secondary = tagged("jarvis-panel:COCKPIT_SECONDARY")

            home.setWorkspaceFootprint(JarvisActionScenes.WorkspaceFootprint.MEDIUM_TERMINAL)
            layout(home) // fold/keyboard/refit-style remeasure
            assertEquals(View.INVISIBLE, requireNotNull(tile).visibility)
            assertEquals(View.INVISIBLE, secondary.visibility)
            assertEquals("medium keeps mission rows usable", View.VISIBLE, row.visibility)

            home.setWorkspaceFootprint(JarvisActionScenes.WorkspaceFootprint.FULL_MISSION_BAY)
            layout(home)
            assertEquals(View.INVISIBLE, row.visibility)
            assertEquals(View.INVISIBLE, secondary.visibility)

            home.setWorkspaceFootprint(JarvisActionScenes.WorkspaceFootprint.CONVERSATION_IDLE)
            layout(home)
            assertEquals(View.VISIBLE, requireNotNull(tile).visibility)
            assertEquals(View.VISIBLE, row.visibility)
            assertEquals(View.VISIBLE, secondary.visibility)
        } finally {
            controller.close()
        }
    }
}
