package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import at.heinz.deviceai.ui.JarvisShellView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class JarvisShellMotionUiTest {
    @Before fun pauseVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun advance(ms: Long = 240L) {
        var left = ms
        while (left > 0L) {
            val step = minOf(16L, left)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            left -= step
        }
    }

    private class Fixture(val controller: ActivityController<Activity>, val parent: FrameLayout,
                          val shell: JarvisShellView) {
        var frames = 0
    }

    private fun withShell(block: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        val resolver = activity.contentResolver
        val scale = Settings.Global.getString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val parent = FrameLayout(activity)
        val shell = JarvisShellView(activity)
        val fixture = Fixture(controller, parent, shell)
        shell.onFrameDrawn = { fixture.frames++ }
        parent.addView(shell, FrameLayout.LayoutParams(-1, -1))
        try {
            controller.setup()
            activity.setContentView(parent)
            controller.visible()
            advance(32L)
            controller.windowFocusChanged(true)
            assertTrue(shell.isAttachedToWindow)
            assertRunning(fixture)
            block(fixture)
        } finally {
            controller.pause().stop().destroy()
            Settings.Global.putString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        }
    }

    private fun assertRunning(f: Fixture) {
        advance()
        val before = f.frames
        advance()
        assertTrue("ambient drawing must continue", f.frames >= before + 2)
    }

    private fun assertStopped(f: Fixture) {
        advance()
        val before = f.frames
        advance()
        assertEquals("inactive drawing must settle", before, f.frames)
    }

    @Test fun focusReturnRestartsAmbientFrames() = withShell { f ->
        f.controller.windowFocusChanged(false)
        assertStopped(f)
        f.controller.windowFocusChanged(true)
        assertRunning(f)
    }

    @Test fun visibilityReturnRestartsAmbientFrames() = withShell { f ->
        f.shell.visibility = View.INVISIBLE
        assertStopped(f)
        f.shell.visibility = View.VISIBLE
        assertRunning(f)
    }

    @Test fun powerSaveExitRestartsAmbientFrames() = withShell { f ->
        val activity = f.controller.get()
        val power = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(power).setIsPowerSaveMode(true)
        activity.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        assertStopped(f)
        shadowOf(power).setIsPowerSaveMode(false)
        activity.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        assertRunning(f)
    }

    @Test fun restoredAnimatorScaleRestartsAmbientFrames() = withShell { f ->
        val resolver = f.controller.get().contentResolver
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        resolver.notifyChange(uri, null)
        assertStopped(f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        resolver.notifyChange(uri, null)
        assertRunning(f)
    }

    @Test fun reattachmentRestartsWithoutRetainedCallbacks() = withShell { f ->
        f.parent.removeView(f.shell)
        assertStopped(f)
        f.parent.addView(f.shell, FrameLayout.LayoutParams(-1, -1))
        assertRunning(f)
    }
}
