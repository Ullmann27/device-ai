package at.heinz.deviceai.ui

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import at.heinz.deviceai.AndroidLiveFrame
import at.heinz.deviceai.cognitive.LiveFrameLease
import at.heinz.deviceai.cognitive.LiveMonitorFeed
import at.heinz.deviceai.cognitive.LiveMonitorState
import at.heinz.deviceai.cognitive.LiveMonitorStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class CockpitMonitorLifecycleUiTest {
    private class CountingFeed : LiveMonitorFeed<AndroidLiveFrame> {
        var reads = 0
        override fun status(nowNanos: Long): LiveMonitorStatus {
            reads++
            return LiveMonitorStatus(LiveMonitorState.NO_SOURCE, null, null, null, 0, null)
        }
        override fun acquire(): LiveFrameLease<AndroidLiveFrame>? = null
        override fun version() = 0L
    }

    private fun advance() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
    }

    private fun withMonitor(check: (VirtualComputerMonitorView, FrameLayout, CountingFeed) -> Unit) {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val host = FrameLayout(controller.get())
        val monitor = VirtualComputerMonitorView(controller.get())
        host.addView(monitor, FrameLayout.LayoutParams(600, 400))
        controller.get().setContentView(host)
        val feed = CountingFeed()
        try {
            advance()
            monitor.bind(feed)
            monitor.setForegroundActive(true)
            advance()
            val reads = feed.reads
            advance()
            assertTrue("Visible feed advances", feed.reads > reads)
            check(monitor, host, feed)
        } finally {
            monitor.setForegroundActive(false)
            controller.pause().stop().destroy()
        }
    }

    @Test fun hiddenMonitorRestartsWhenVisible() = withMonitor { monitor, _, feed ->
        monitor.visibility = View.INVISIBLE
        advance()
        val stopped = feed.reads
        advance()
        assertEquals(stopped, feed.reads)
        monitor.visibility = View.VISIBLE
        advance()
        assertTrue("Visibility resumes existing tick", feed.reads > stopped)
    }

    @Test fun parentVisibilityRestartsWithoutForegroundToggle() = withMonitor { _, host, feed ->
        host.visibility = View.GONE
        advance()
        val stopped = feed.reads
        advance()
        assertEquals(stopped, feed.reads)
        host.visibility = View.VISIBLE
        advance()
        assertTrue("Parent visibility resumes tick", feed.reads > stopped)
    }

    @Test fun backgroundAndDetachStayStopped() = withMonitor { monitor, host, feed ->
        monitor.setForegroundActive(false)
        monitor.visibility = View.INVISIBLE
        monitor.visibility = View.VISIBLE
        advance()
        val background = feed.reads
        advance()
        assertEquals(background, feed.reads)
        monitor.setForegroundActive(true)
        advance()
        assertTrue(feed.reads > background)
        host.removeView(monitor)
        advance()
        val detached = feed.reads
        advance()
        assertEquals(detached, feed.reads)
    }
}
