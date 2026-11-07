package at.heinz.deviceai

import android.content.Context
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Actual Activity scroll ownership. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumWorkWindowScrollUiTest {
    private var pump: CockpitHostFramePump? = null

    @Before fun pauseVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass
        .getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun shown(root: View, tag: String): View {
        val views = descendants(root).filter { it.tag == tag && it.isShown }
        assertEquals("One visible $tag", 1, views.size)
        return views.single()
    }

    private fun measure(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(915, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 412, 915)
    }

    private fun advance(ms: Long) {
        var remaining = ms
        while (remaining > 0L) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            pump?.drawRequestedFrame()
            remaining -= step
        }
    }

    private fun touch(root: View, target: View) {
        target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), true)
        advance(32L)
        measure(root)
        val bounds = Rect()
        assertTrue("Actual tile is visible", target.getGlobalVisibleRect(bounds))
        assertEquals(target.width, bounds.width())
        assertEquals(target.height, bounds.height())
        val now = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to now, MotionEvent.ACTION_UP to now + 16L)) {
            val event = MotionEvent.obtain(now, time, action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try { assertTrue(root.dispatchTouchEvent(event)) } finally { event.recycle() }
        }
    }

    private data class Fixture(val activity: AgentActivity, val root: View,
        val home: ContinuumHomeView, val avatar: ContinuumHologramView,
        val work: View, val background: ScrollView)

    private fun withOpenWindow(test: (Fixture) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            advance(32L)
            controller.windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            pump = CockpitHostFramePump { controller.get().window.decorView }
            val avatar = field(activity, "hologramView") as ContinuumHologramView
            val home = field(activity, "homeView") as ContinuumHomeView
            avatar.setAnimationsEnabled(true)
            avatar.setForegroundActive(true)
            home.setForegroundActive(true)
            measure(root); advance(320L); measure(root)
            assertTrue("Real decor attached", root.isAttachedToWindow)
            assertTrue(home.jarvisShellStage)
            assertEquals(ContinuumWorkArea.START, field(activity, "activeWorkArea"))
            val beforeDialog = ShadowAlertDialog.getLatestAlertDialog()
            touch(root, shown(root, "jarvis-tile:system"))
            advance(80L); measure(root)
            val work = shown(root, "jarvis-work-window")
            assertSame(beforeDialog, ShadowAlertDialog.getLatestAlertDialog())
            assertSame(root, work.rootView)
            assertTrue(activity.hasWindowFocus())
            val background = generateSequence(home.parent as? View) { it.parent as? View }
                .filterIsInstance<ScrollView>().first {
                    it.childCount > 0 && it.getChildAt(0).height > it.height
                }
            test(Fixture(activity, root, home, avatar, work, background))
        } finally {
            pump?.close()
            pump = null
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.close() }
        }
    }

    private fun gazeDirected(avatar: ContinuumHologramView) =
        (field(avatar, "avatarPose") as AvatarPoseReducer).pose().gazeDirected

    @Test fun backgroundScrollClosesTheRealWorkAreaAndGlance() = withOpenWindow { f ->
        assertTrue("Work area directs the existing gaze", gazeDirected(f.avatar))
        val before = f.background.scrollY
        f.background.scrollTo(f.background.scrollX, before + 32)
        assertTrue("Real background scroll advances", f.background.scrollY != before)
        advance(32L)
        println("SCROLL_WINDOW background=$before->${f.background.scrollY} workShown=${f.work.isShown}")
        assertFalse("Moved background closes stale protected bounds", f.work.isShown)
        assertTrue("Closed surface is removed", descendants(f.root).none { it.tag == "jarvis-work-window" })
        assertFalse("Background scroll clears window glance", gazeDirected(f.avatar))
        assertSame(f.avatar, field(f.activity, "hologramView"))
        assertEquals(ContinuumWorkArea.START, field(f.activity, "activeWorkArea"))
    }

    @Test fun innerStatusScrollKeepsTheWorkAreaAndBackgroundStill() = withOpenWindow { f ->
        advance(960L); measure(f.root)
        (field(f.activity, "learningIoExecutor") as ExecutorService)
            .submit(Runnable {}).get(5L, TimeUnit.SECONDS)
        advance(64L); measure(f.root)
        val inner = shown(f.work, "jarvis-work-window:scroll") as ScrollView
        assertTrue("Real status content overflows", inner.getChildAt(0).height > inner.height)
        val shellBefore = IntArray(2).also { f.home.jarvisShell.getLocationInWindow(it) }
        val backgroundBefore = f.background.scrollY
        val before = inner.scrollY
        inner.scrollTo(inner.scrollX, before + 48)
        assertTrue("Real inner scroll advances", inner.scrollY > before)
        advance(32L)
        println("SCROLL_WINDOW inner=$before->${inner.scrollY} workShown=${f.work.isShown}")
        assertTrue("Status scrolling keeps its work area", f.work.isShown)
        assertSame(f.work, shown(f.root, "jarvis-work-window"))
        assertEquals(backgroundBefore, f.background.scrollY)
        assertTrue("Inner scroll keeps protected coordinates",
            shellBefore.contentEquals(IntArray(2).also { f.home.jarvisShell.getLocationInWindow(it) }))
        assertSame(f.avatar, field(f.activity, "hologramView"))
        assertTrue(f.avatar.isShown)
        assertTrue(f.activity.hasWindowFocus())
    }
}
