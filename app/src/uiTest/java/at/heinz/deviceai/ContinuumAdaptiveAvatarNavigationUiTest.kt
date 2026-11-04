package at.heinz.deviceai

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.ContinuumHologramRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual pane construction and Activity navigation, hosted without unrelated app startup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumAdaptiveAvatarNavigationUiTest {
    private fun field(activity: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun set(activity: AgentActivity, name: String, value: Any?) {
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()

    private fun measure(view: View, width: Int, height: Int) {
        fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        view.measure(exact(width), exact(height))
        view.layout(0, 0, width, height)
    }

    private fun checkNavigation(width: Int, height: Int, wide: Boolean, font: Float,
                                expectedHomeGeometry: ContinuumHologramRenderer.Geometry) {
        val originalFont = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(font)
        try {
            val activity = Robolectric.buildActivity(AgentActivity::class.java).get()
            set(activity, "bootstrapMemory", BootstrapMemoryStore(activity))
            set(activity, "foregroundLearningActive", true)
            set(activity, "renderedWindowWidthDp", width.toFloat())
            set(activity, "renderedWindowHeightDp", height.toFloat())
            val pane = AgentActivity::class.java
                .getDeclaredMethod("buildConversationPane", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(activity, wide) as View
            val home = field(activity, "homeView") as ContinuumHomeView
            val head = field(activity, "hologramView") as ContinuumHologramView
            val chat = field(activity, "chatColumn") as LinearLayout
            val director = field(activity, "avatarDirector") as AvatarDirector
            val renderer = ContinuumHologramView::class.java.getDeclaredField("renderer")
                .apply { isAccessible = true }.get(head) as ContinuumHologramRenderer
            val refresh = AgentActivity::class.java.getDeclaredMethod("refreshConversationLayout")
                .apply { isAccessible = true }
            val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
            try {
                head.setNativeHeadEnabled(false)
                head.setAnimationsEnabled(false)
                host.get().setContentView(pane)
                shadowOf(Looper.getMainLooper()).idle()
                measure(pane, width, height)

                assertEquals("Initial Activity refresh must respect the selected layout",
                    expectedHomeGeometry, renderer.geometry)
                val homeBounds = listOf(head.left, head.top, head.right, head.bottom)
                repeat(3) {
                    set(activity, "conversationActive", true)
                    refresh.invoke(activity)
                    measure(pane, width, height)
                    assertSame(chat, head.parent)
                    assertEquals(ContinuumHologramRenderer.Geometry.PHONE, renderer.geometry)

                    set(activity, "conversationActive", false)
                    refresh.invoke(activity)
                    measure(pane, width, height)
                    assertSame(home, head.parent)
                    assertEquals("Home geometry must survive the real Chat → Home refresh",
                        expectedHomeGeometry, renderer.geometry)
                    assertEquals(homeBounds, listOf(head.left, head.top, head.right, head.bottom))
                    assertSame(head, field(activity, "hologramView"))
                    assertSame(director, field(activity, "avatarDirector"))
                    assertEquals("Navigation must keep one live avatar surface", 1,
                        descendants(pane).count { it is ContinuumHologramView })
                    assertSame(head, AvatarDirector::class.java.getDeclaredField("sink")
                        .apply { isAccessible = true }.get(director))
                }
            } finally {
                head.setForegroundActive(false)
                home.setForegroundActive(false)
                host.pause().stop().destroy()
            }
        } finally {
            RuntimeEnvironment.setFontScale(originalFont)
        }
    }

    @Test fun largeTypePhoneRestoresCockpitGeometry() = checkNavigation(
        412, 915, false, 1.6f, ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT)

    @Test fun largeTypeLandscapeRestoresCockpitGeometry() = checkNavigation(
        840, 649, true, 1.6f, ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT)

    @Test fun largeTypePortraitRestoresCockpitGeometry() = checkNavigation(
        840, 1050, true, 1.6f, ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT)

    @Test fun narrowPhoneRestoresCockpitGeometryAtNormalFont() = checkNavigation(
        280, 720, false, 1f, ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT)

    @Test fun referencePortraitRetainsItsReviewedGeometry() = checkNavigation(
        840, 1050, true, 1f, ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT)
}
