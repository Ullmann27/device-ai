package at.heinz.deviceai.ui

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import at.heinz.deviceai.AndroidLiveFrame
import at.heinz.deviceai.cognitive.LatestLiveFrameHolder
import at.heinz.deviceai.cognitive.LiveFrameBinding
import at.heinz.deviceai.cognitive.LiveFrameOrigin
import at.heinz.deviceai.cognitive.LiveMonitorFeed
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CockpitMonitorLifecycleUiTest {
    private fun advance() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
    }

    private fun withMonitor(check: (VirtualComputerMonitorView, FrameLayout,
        LatestLiveFrameHolder<AndroidLiveFrame>, MutableList<LiveMonitorFeed<AndroidLiveFrame>?>) -> Unit) {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val host = FrameLayout(controller.get())
        val monitor = VirtualComputerMonitorView(controller.get())
        val delivered = mutableListOf<LiveMonitorFeed<AndroidLiveFrame>?>()
        monitor.onFeedChanged = { delivered += it }
        host.addView(monitor, FrameLayout.LayoutParams(600, 400))
        controller.get().setContentView(host)
        val feed = LatestLiveFrameHolder<AndroidLiveFrame>({}).apply {
            bind(LiveFrameBinding("ui-lifecycle", 1L, "com.example.target", "at.heinz.deviceai",
                LiveFrameOrigin.PROJECTION_SINGLE_APP))
        }
        try {
            advance()
            assertTrue("Monitor attaches to real window", monitor.isAttachedToWindow)
            assertTrue("Monitor starts shown", monitor.isShown)
            assertEquals("Host window starts visible", View.VISIBLE, monitor.windowVisibility)
            monitor.bind(feed)
            assertNull("Background never forwards a source", delivered.last())
            monitor.setForegroundActive(true)
            advance()
            assertSame("Visible renderer receives bound object", feed, delivered.last())
            check(monitor, host, feed, delivered)
        } finally {
            monitor.setForegroundActive(false)
            controller.pause().stop().destroy()
        }
    }

    private fun delivery(expected: LiveMonitorFeed<AndroidLiveFrame>?,
                         delivered: MutableList<LiveMonitorFeed<AndroidLiveFrame>?>, action: () -> Unit) {
        val before = delivered.size
        action()
        advance()
        assertTrue("Lifecycle publishes renderer binding", delivered.size > before)
        assertSame(expected, delivered.last())
        val settled = delivered.size
        advance()
        assertSame(expected, delivered.last())
        assertTrue("Stable lifecycle cannot leak another source", delivered.drop(settled).all { it === expected })
    }

    @Test fun hiddenMonitorReleasesAndRestoresBoundSource() = withMonitor { monitor, _, feed, delivered ->
        delivery(null, delivered) { monitor.visibility = View.INVISIBLE }
        assertFalse(monitor.isShown)
        delivery(feed, delivered) { monitor.visibility = View.VISIBLE }
        assertTrue(monitor.isShown)
    }

    @Test fun parentAndWindowVisibilityRestoreTheSameSource() = withMonitor { monitor, host, feed, delivered ->
        delivery(null, delivered) { host.visibility = View.GONE }
        delivery(feed, delivered) { host.visibility = View.VISIBLE }
        val decor = host.rootView
        delivery(null, delivered) { decor.visibility = View.INVISIBLE }
        assertEquals("Actual window became hidden", View.INVISIBLE, monitor.windowVisibility)
        delivery(feed, delivered) { decor.visibility = View.VISIBLE }
        assertEquals("Actual window became visible", View.VISIBLE, monitor.windowVisibility)
    }

    @Test fun backgroundAndDetachNeverForwardSource() = withMonitor { monitor, host, feed, delivered ->
        delivery(null, delivered) { monitor.setForegroundActive(false) }
        delivery(null, delivered) { monitor.visibility = View.INVISIBLE }
        delivery(null, delivered) { monitor.visibility = View.VISIBLE }
        delivery(feed, delivered) { monitor.setForegroundActive(true) }
        delivery(null, delivered) { host.removeView(monitor) }
        assertFalse(monitor.isAttachedToWindow)
        delivery(null, delivered) { monitor.bind(feed) }
        delivery(null, delivered) { monitor.setForegroundActive(true) }
        delivery(feed, delivered) { host.addView(monitor, FrameLayout.LayoutParams(600, 400)) }
        assertTrue(monitor.isAttachedToWindow)
    }
}
