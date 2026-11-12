package at.heinz.deviceai.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.View
import at.heinz.deviceai.AndroidLiveFrame
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.cognitive.LatestLiveFrameHolder
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
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CockpitMonitorMotionUiTest {
    private fun withHome(check: (ContinuumHomeView, VirtualComputerMonitorView) -> Unit) {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        val home = ContinuumHomeView(activity, ContinuumHologramView(activity)) { _, _, _ -> }
        home.configure(884f, true, windowHeightDp = 760f)
        home.setJarvisShellStage(true)
        activity.setContentView(home)
        try {
            controller.visible()
            repeat(2) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }
            controller.windowFocusChanged(true)
            home.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
            home.layout(0, 0, 884, 760)
            home.setForegroundActive(true)
            assertTrue("Cockpit attaches to real window", home.isAttachedToWindow)
            assertTrue("Cockpit starts shown", home.isShown)
            assertEquals("Cockpit window starts visible", View.VISIBLE, home.windowVisibility)
            assertTrue(home.isPresentationActive())
            val monitor = home.findViewWithTag<VirtualComputerMonitorView>("continuum-live-computer-monitor")
            val labels = home.findViewWithTag<View>("jarvis-foreground")
            assertFalse("One shared 3D scene", (0 until home.childCount).map(home::getChildAt)
                .any { it.javaClass.simpleName == "CockpitStageView" })
            assertTrue("Labels stay above scene", labels.translationZ > home.hologram.translationZ)
            assertTrue("Labels stay above monitor", labels.translationZ > monitor.translationZ)
            check(home, monitor)
        } finally {
            home.setForegroundActive(false)
            controller.pause().stop().destroy()
        }
    }

    private fun animator(home: ContinuumHomeView): ValueAnimator? =
        ContinuumHomeView::class.java.getDeclaredField("monitorFocusAnimator").let {
            it.isAccessible = true
            it.get(home) as? ValueAnimator
        }

    private fun velocity(home: ContinuumHomeView): Float =
        ContinuumHomeView::class.java.getDeclaredField("monitorFocusVelocity").let {
            it.isAccessible = true
            it.getFloat(home)
        }

    @Test fun duplicateFeedDoesNotRestartAndReversalHasNoJump() = withHome { home, monitor ->
        val rest = monitor.frame
        val feed = LatestLiveFrameHolder<AndroidLiveFrame>({})
        home.setLiveComputerFeed(feed)
        val opening = requireNotNull(animator(home))
        opening.setCurrentFraction(.45f)
        val middle = requireNotNull(monitor.frame)
        assertTrue(middle.pitchDegrees < -1f && middle.depthOffset > 1f)
        home.setLiveComputerFeed(feed)
        assertSame(opening, animator(home))
        assertEquals(middle, monitor.frame)
        val incomingSpeed = velocity(home)
        home.clearLiveComputerFeed()
        val closing = requireNotNull(animator(home))
        assertNotSame(opening, closing)
        assertEquals(middle, monitor.frame)
        assertEquals(incomingSpeed, velocity(home), .000001f)
        closing.setCurrentFraction(.01f)
        assertTrue(requireNotNull(monitor.frame).depthOffset > middle.depthOffset)
        closing.end()
        assertNull(animator(home))
        assertEquals(0f, velocity(home), 0f)
        assertEquals(rest, monitor.frame)
    }

    @Test fun backgroundSettlesWithoutReplayingOnResume() = withHome { home, monitor ->
        home.setLiveComputerFeed(LatestLiveFrameHolder<AndroidLiveFrame>({}))
        requireNotNull(animator(home)).setCurrentFraction(.5f)
        home.setForegroundActive(false)
        val settled = requireNotNull(monitor.frame)
        assertNull(animator(home))
        assertEquals(0f, settled.pitchDegrees, 0f)
        assertTrue("Foreground work pose", settled.depthOffset > 0f)
        home.setForegroundActive(true)
        assertNull(animator(home))
        assertEquals(settled, monitor.frame)
    }

    @Test fun hiddenCockpitReleasesItsFlight() = withHome { home, monitor ->
        home.setLiveComputerFeed(LatestLiveFrameHolder<AndroidLiveFrame>({}))
        requireNotNull(animator(home)).setCurrentFraction(.3f)
        home.visibility = View.INVISIBLE
        assertNull(animator(home))
        assertEquals(0f, requireNotNull(monitor.frame).pitchDegrees, 0f)
        home.visibility = View.VISIBLE
        assertNull(animator(home))
    }

    @Test fun disabledSystemMotionGoesDirectlyToWorkPose() = withHome { home, monitor ->
        val method = ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val previous = ValueAnimator::class.java.getDeclaredMethod("getDurationScale").invoke(null) as Float
        try {
            method.invoke(null, 0f)
            assertFalse(ValueAnimator.areAnimatorsEnabled())
            home.setLiveComputerFeed(LatestLiveFrameHolder<AndroidLiveFrame>({}))
            assertNull(animator(home))
            assertEquals(0f, requireNotNull(monitor.frame).pitchDegrees, 0f)
            assertEquals(0f, requireNotNull(monitor.frame).yawDegrees, 0f)
        } finally {
            method.invoke(null, previous)
        }
    }
}
