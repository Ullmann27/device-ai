package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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
    private var activeFixture: Fixture? = null
    @Before fun pauseVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun advance(ms: Long = 240L) {
        var left = ms
        while (left > 0L) {
            val step = minOf(16L, left)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            activeFixture?.drawRequestedFrame()
            left -= step
        }
    }

    private class Fixture(val controller: ActivityController<Activity>, val parent: FrameLayout,
                          val shell: JarvisShellView) {
        var frames = 0
        private var surface: Bitmap? = null

        fun drawRequestedFrame() {
            if (!shell.isAttachedToWindow || !shell.isShown || shell.width <= 0 || shell.height <= 0) return
            val shadow = shadowOf(shell)
            if (!shadow.wasInvalidated()) return
            // Consume actual invalidations on host.
            shadow.clearWasInvalidated()
            val bitmap = surface?.takeIf { it.width == shell.width && it.height == shell.height }
                ?: Bitmap.createBitmap(shell.width, shell.height, Bitmap.Config.ARGB_8888).also {
                    surface?.recycle()
                    surface = it
                }
            shell.draw(Canvas(bitmap))
        }

        fun close() { surface?.recycle(); surface = null }
    }

    private fun withShell(block: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.setup().get()
        val resolver = activity.contentResolver
        val scale = Settings.Global.getString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val parent = FrameLayout(activity)
        val shell = JarvisShellView(activity)
        val fixture = Fixture(controller, parent, shell)
        activeFixture = fixture
        shell.onFrameDrawn = { fixture.frames++ }
        parent.addView(shell, FrameLayout.LayoutParams(-1, -1))
        try {
            activity.setContentView(parent)
            controller.visible()
            advance(32L)
            controller.windowFocusChanged(true)
            assertTrue(shell.isAttachedToWindow)
            assertRunning(fixture)
            block(fixture)
        } finally {
            controller.pause().stop().destroy()
            activeFixture = null
            fixture.close()
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

    @Test fun frameLoggingDoesNotKeepUnfocusedShellDrawing() = withShell { f ->
        f.shell.frameLogging = true
        try {
            f.controller.windowFocusChanged(false)
            assertStopped(f)
            f.controller.windowFocusChanged(true)
            assertRunning(f)
        } finally { f.shell.frameLogging = false }
    }

    @Test fun frameLoggingHonoursReducedMotion() = withShell { f ->
        f.shell.frameLogging = true
        try {
            val resolver = f.controller.get().contentResolver
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
            assertStopped(f)
        } finally { f.shell.frameLogging = false }
    }

    @Test fun workWindowSharesShellFramesThenSettles() = withShell { f ->
        f.shell.ambientMotionEnabled = false
        assertStopped(f)
        var callbacks = 0
        val until = android.os.SystemClock.uptimeMillis() + 96L
        val subscription = f.shell.observeMotionFrames { now, scale ->
            assertTrue(scale > 0f)
            callbacks++
            now < until
        }
        try {
            advance()
            assertTrue("Window uses supplied frames", callbacks > 1)
            val settled = callbacks
            advance()
            assertEquals("Completed window schedules nothing", settled, callbacks)
        } finally { subscription.close() }
    }

    @Test fun workWindowReceivesReducedMotionWithoutLoop() = withShell { f ->
        f.shell.ambientMotionEnabled = false
        var latestScale = -1f
        val subscription = f.shell.observeMotionFrames { _, scale -> latestScale = scale; true }
        try {
            advance(32L)
            val resolver = f.controller.get().contentResolver
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
            assertStopped(f)
            assertEquals("Reduced motion settles windows", 0f, latestScale, 0f)
        } finally { subscription.close() }
    }
}
