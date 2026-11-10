package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.JarvisActionScenes
import at.heinz.deviceai.ui.JarvisWorkWindow
import at.heinz.deviceai.ui.JarvisWorkWindowHost
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.util.concurrent.TimeUnit

/** Real scroll and overlay coordinates. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class JarvisWorkWindowHostViewportUiTest {
    @Before fun pauseVsync() { ShadowChoreographer.setPaused(true) }

    private class Fixture : AutoCloseable {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        init {
            activity.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
                .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
            activity.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        }
        val live = ContinuumHologramView(activity).apply {
            setForegroundActive(false)
            setAnimationsEnabled(false)
        }
        val home = ContinuumHomeView(activity, live) { _, _, _ -> }
        val host = JarvisWorkWindowHost(activity, AvatarDirector(live))
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isSmoothScrollingEnabled = false
            clipToPadding = true
            setPadding(8, 12, 8, 16)
            addView(home, ViewGroup.LayoutParams(-1, -2))
        }
        val clipped = FrameLayout(activity).apply {
            clipChildren = true
            addView(scroll, FrameLayout.LayoutParams(692, 620).apply { leftMargin = 20; topMargin = 10 })
        }
        val page = FrameLayout(activity).apply {
            addView(clipped, FrameLayout.LayoutParams(740, 520).apply { leftMargin = 60; topMargin = 72 })
        }
        val container: ViewGroup
        var opened: JarvisWorkWindow? = null
        var launch: RectF? = null
        var closed = 0
        var routed = 0
        val viewport = RectF(88f, 94f, 764f, 592f)
        init {
            JarvisAvatarSlot.register(JarvisHoloAvatar(live))
            home.setJarvisShellStage(true)
            home.jarvisShell.ambientMotionEnabled = false
            // Below REFERENCE_MIN_HEIGHT so the fixture keeps feature tiles and a scrollable deck.
            home.configure(676f, true, windowHeightDp = 520f)
            home.onNavigateTarget = { target ->
                assertEquals("system", target)
                routed++
                opened = host.show(home, TextView(activity).apply { text = "Echte Statusansicht" },
                    "Systemstatus", requireNotNull(JarvisActionScenes.forCentre("Systemstatus", target))) { closed++ }
                launch = opened?.let(::launchBounds)
            }
            activity.setContentView(page)
            controller.visible()
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            container = activity.findViewById(android.R.id.content)
            container.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
            container.layout(0, 0, 884, 760)
            assertTrue(home.isShown)
            assertEquals(676, home.width)
        }

        fun card(): View = JarvisHomeTestRoutes.destination(home, "system")

        fun bounds(view: View): RectF = Rect(0, 0, view.width, view.height).let {
            container.offsetDescendantRectToMyCoords(view, it)
            RectF(it)
        }

        fun tapCard(): RectF {
            val shown = bounds(card()).apply { assertTrue(intersect(viewport)) }
            assertTrue(shown.width() >= 48f && shown.height() >= 48f)
            val now = SystemClock.uptimeMillis()
            for ((action, time) in listOf(MotionEvent.ACTION_DOWN to now, MotionEvent.ACTION_UP to now + 16)) {
                val event = MotionEvent.obtain(now, time, action, shown.centerX(), shown.centerY(), 0)
                try { assertTrue(container.dispatchTouchEvent(event)) } finally { event.recycle() }
            }
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            assertEquals(1, routed)
            return shown
        }

        fun launchBounds(window: JarvisWorkWindow): RectF {
            val panel = window.findViewWithTag<View>("jarvis-work-window:panel")
            val params = panel.layoutParams as FrameLayout.LayoutParams
            val left = params.leftMargin + panel.translationX
            val top = params.topMargin + panel.translationY
            return RectF(left, top, left + params.width * panel.scaleX, top + params.height * panel.scaleY)
        }

        override fun close() {
            host.close(immediate = true)
            controller.pause().stop().destroy()
            JarvisAvatarSlot.register(null)
            activity.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun sameBounds(expected: RectF, actual: RectF) {
        assertEquals(expected.left, actual.left, .01f)
        assertEquals(expected.top, actual.top, .01f)
        assertEquals(expected.right, actual.right, .01f)
        assertEquals(expected.bottom, actual.bottom, .01f)
    }

    @Test fun scrolledCardOpensInsideTheClippedViewportWithoutResettingItsOrigin() {
        Fixture().use { f ->
            f.scroll.scrollTo(0, 700)
            assertEquals(700, f.scroll.scrollY)
            val origin = f.tapCard()
            assertEquals("Opening preserves the tapped scroll position", 700, f.scroll.scrollY)
            val window = requireNotNull(f.opened) { "Scrolled card must open its work window" }
            sameBounds(origin, requireNotNull(f.launch))
            val destination = requireNotNull(window.shownBounds())
            assertTrue("Window stays within ancestor clipping", f.viewport.contains(destination))
            assertTrue("Scroll does not collapse usable height", destination.height() >= 460f)
            assertEquals(View.INVISIBLE, f.card().visibility)
            f.host.close(immediate = true)
            assertEquals(700, f.scroll.scrollY)
            assertFalse(f.host.isShowing)
            assertEquals(View.VISIBLE, f.card().visibility)
            assertEquals(1, f.closed)
        }
    }

    @Test fun partiallyVisibleCardStartsFromItsVisiblePartAndLaterScrollClosesTheWindow() {
        Fixture().use { f ->
            f.scroll.scrollTo(0, f.card().bottom - 60)
            val initial = f.scroll.scrollY
            val origin = f.tapCard()
            assertEquals(60f, origin.height(), .01f)
            assertEquals(initial, f.scroll.scrollY)
            val window = requireNotNull(f.opened) { "Visible card fragment must remain actionable" }
            sameBounds(origin, requireNotNull(f.launch))
            assertTrue(f.viewport.contains(requireNotNull(window.shownBounds())))
            f.scroll.scrollTo(0, initial + 1)
            assertFalse("Changed geometry closes the old overlay", f.host.isShowing)
            assertEquals(View.VISIBLE, f.card().visibility)
            assertEquals(1, f.closed)
        }
    }
}
