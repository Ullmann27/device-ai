package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * The Activity reports the unchanged window height to the Home from its layout callback on every pass.
 * Real ContinuumHomeView inside the same fill-viewport scroll structure the Activity uses; no avatar rendering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumHomeViewportUpdateUiTest {
    private class Fixture(compact: Boolean) : AutoCloseable {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity: Activity = controller.get()
        val prefs = activity.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
        init { prefs.edit().clear().putBoolean("compact", compact).commit() }
        val home = ContinuumHomeView(activity, ContinuumHologramView(activity).apply {
            setForegroundActive(false)
            setAnimationsEnabled(false)
        }) { _, _, _ -> }
        private val dashboard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(home, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(dashboard, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        init {
            home.setJarvisShellStage(true)
            home.configure(412f, false, windowHeightDp = WINDOW_HEIGHT)
            activity.setContentView(scroll)
            pass()
        }

        fun pass() {
            // As in the Activity, the pinned composer leaves the scrolling stage less than the window height.
            scroll.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(STAGE_HEIGHT, View.MeasureSpec.EXACTLY))
            scroll.layout(0, 0, 412, STAGE_HEIGHT)
        }

        override fun close() {
            prefs.edit().clear().commit()
            controller.pause().stop().destroy()
        }
    }

    @Test fun unchangedWindowHeightRequestsNoLayoutAfterCompactContentWasMeasuredAgainstTheViewport() {
        Fixture(compact = true).use { f ->
            assertTrue("Compact content is shorter than the scroll viewport: ${f.home.height}",
                f.home.height < STAGE_HEIGHT)
            assertTrue("The measure pass clamped the shell viewport: ${f.home.jarvisShell.viewportHeightDp}",
                f.home.jarvisShell.viewportHeightDp < WINDOW_HEIGHT)
            assertFalse("A completed pass leaves no pending request", f.home.isLayoutRequested)
            val height = f.home.height
            // What AgentActivity's layout callback does on every pass while the window keeps its size.
            repeat(3) {
                f.home.updateAvailableViewport(WINDOW_HEIGHT)
                assertFalse("An unchanged window height must not request another layout", f.home.isLayoutRequested)
                f.pass()
                assertEquals("Geometry stays stable", height, f.home.height)
            }
        }
    }

    @Test fun aRealHeightChangeStillRequestsLayoutInBothPresentations() {
        for (compact in listOf(true, false)) Fixture(compact).use { f ->
            f.home.updateAvailableViewport(WINDOW_HEIGHT)
            assertFalse("compact=$compact unchanged", f.home.isLayoutRequested)
            f.home.updateAvailableViewport(WINDOW_HEIGHT - 160f)
            assertTrue("compact=$compact: a shorter window is laid out again", f.home.isLayoutRequested)
            f.pass()
            assertFalse(f.home.isLayoutRequested)
            f.home.updateAvailableViewport(WINDOW_HEIGHT - 160f)
            assertFalse("compact=$compact: the new height is now the unchanged one", f.home.isLayoutRequested)
        }
    }

    private companion object {
        const val WINDOW_HEIGHT = 800f
        const val STAGE_HEIGHT = 680
    }
}
