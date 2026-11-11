package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.avatar.AvatarEvent
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.AvatarSink
import at.heinz.deviceai.avatar.GazeTarget
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
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Real View/Canvas lifecycle checks; never initializes Android Filament JNI on the host. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumAvatar3dLifecycleUiTest {
    @Before fun configureVirtualVsyncClock() {
        // Automatic virtual-vsync advances time while idleFor drains recurring frames.
        // Explicit frame advancement keeps the real lifecycle assertions bounded.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun advanceFrames(totalMillis: Long) {
        // Paused VSync is driven by clock changes, not by a queued Looper deadline.
        var remaining = totalMillis
        while (remaining > 0) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            remaining -= step
        }
    }

    private fun attachVisible(activity: Activity, view: View, width: Int = 412, height: Int = 307) {
        activity.setContentView(view, ViewGroup.LayoutParams(width, height))
        advanceFrames(16) // Complete the actual window traversal before foreground activation.
        assertTrue("Fixture must be attached to the real Activity window", view.isAttachedToWindow)
        assertTrue("Fixture must be shown before testing foreground behavior", view.isShown)
        assertEquals(View.VISIBLE, view.windowVisibility)
        assertEquals("Window traversal must preserve the requested viewport width", width, view.width)
        assertEquals("Window traversal must preserve the requested viewport height", height, view.height)
    }

    private fun field(view: ContinuumHologramView, name: String): Any? =
        ContinuumHologramView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(view)
    private fun draw(view: ContinuumHologramView): Bitmap {
        assertEquals(412, view.width)
        assertEquals(307, view.height)
        return Bitmap.createBitmap(412, 307, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    @Test fun legacyEnabledHeadUpgradesButExplicitOrbAnd2dStayChosen() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val context = controller.get()
        val prefs = context.getSharedPreferences("continuum-avatar-appearance", android.content.Context.MODE_PRIVATE)
        try {
            prefs.edit().clear().commit()
            val fresh = ContinuumHologramView(context)
            assertTrue(fresh.isNativeHeadEnabled())
            assertEquals(true, field(fresh, "head3dEnabled"))
            assertEquals(false, field(fresh, "assetLoading"))
            assertNull(field(fresh, "native3d")) // Selection alone grants no native work while hidden.
            fresh.setNativeHeadEnabled(false)
            val orb = ContinuumHologramView(context)
            assertFalse(orb.isNativeHeadEnabled())
            assertEquals(false, field(orb, "head3dEnabled"))
            prefs.edit().clear().putBoolean("native-head", true).commit()
            val legacy = ContinuumHologramView(context)
            assertTrue(legacy.isNativeHeadEnabled())
            assertEquals(true, field(legacy, "head3dEnabled"))
            prefs.edit().putBoolean("native-head-3d", false).commit()
            val explicit2d = ContinuumHologramView(context)
            assertTrue(explicit2d.isNativeHeadEnabled())
            assertFalse(explicit2d.isNative3dEnabled())
            prefs.edit().clear().putBoolean("native-head-3d", true).commit()
            assertTrue(ContinuumHologramView(context).isNativeHeadEnabled())
        } finally {
            prefs.edit().clear().commit()
            controller.pause().stop().destroy()
        }
    }
    @Test fun defaultOrbAndPausedOptional3dNeverStartAssetOrNativeWork() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = ContinuumHologramView(controller.get())
        try {
            view.setNativeHeadEnabled(false); view.setAnimationsEnabled(false)
            attachVisible(controller.get(), view)
            val orb = draw(view)
            try {
                assertEquals(0, view.childCount); assertNull(field(view, "native3d")); assertEquals(false, field(view, "assetLoading"))
                view.setNative3dEnabled(true) // Paused: even explicit selection must not start IO or native work.
                draw(view).recycle()
                assertTrue(view.isNativeHeadEnabled()); assertEquals(0, view.childCount)
                assertNull(field(view, "native3d")); assertNull(field(view, "assetBytes")); assertEquals(false, field(view, "assetLoading"))
                advanceFrames(1_000)
                assertEquals(0L, view.sampleMotion().callbackCount)
                view.setNativeHeadEnabled(false)
                val restored = draw(view)
                try { assertTrue("Opt-out restores original Canvas orb", orb.sameAs(restored)) } finally { restored.recycle() }
            } finally { orb.recycle() }
        } finally { view.setNativeHeadEnabled(false); controller.pause().stop().destroy() }
    }
    @Test fun actualFailureReportsUnavailabilityAndRetainsSingleLifecycleClock() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = ContinuumHologramView(controller.get())
        try {
            view.setNativeHeadEnabled(true); view.setAnimationsEnabled(false)
            attachVisible(controller.get(), view)
            val baseline = draw(view)
            try {
                view.setNative3dEnabled(true)
                ContinuumHologramView::class.java.getDeclaredMethod("fail3d").apply { isAccessible = true }.invoke(view)
                view.setForegroundActive(true)
                val fallback = draw(view)
                try {
                    assertFalse("Unavailable 3D must not substitute a different face", baseline.sameAs(fallback))
                    assertEquals(true, field(view, "native3dFailed"))
                    assertEquals(true, field(view, "head3dEnabled"))
                } finally { fallback.recycle() }
                assertEquals(0, view.childCount); assertNull(field(view, "native3d"))
                advanceFrames(1_000)
                assertEquals(0L, view.sampleMotion().callbackCount)
                view.setAnimationsEnabled(true)
                advanceFrames(200)
                assertTrue(view.sampleMotion().callbackCount > 0)
                view.setForegroundActive(false)
                val paused = view.sampleMotion().callbackCount
                advanceFrames(1_000)
                assertEquals(paused, view.sampleMotion().callbackCount)
                assertNull(field(view, "nativeSurface")); assertNull(field(view, "native3d"))
                controller.get().setContentView(View(controller.get()))
                assertNull(field(view, "assetBytes")); assertNull(field(view, "nativeTexture"))
            } finally { baseline.recycle() }
        } finally { view.setNativeHeadEnabled(false); controller.pause().stop().destroy() }
    }

    @Test fun pauseMotionAndReattachmentResumeOnlyFreshMonotonicGaze() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = ContinuumHologramView(controller.get())
        try {
            view.setNativeHeadEnabled(true)
            attachVisible(controller.get(), view)
            view.setForegroundActive(true)
            assertTrue("Observed gaze requires a live visible head", view.motionDiagnostics().contains("LIVE"))
            val reducer = field(view, "avatarPose") as AvatarPoseReducer
            val director = AvatarDirector(AvatarSink(view::onAvatarEvent), SystemClock::elapsedRealtime)
            fun observe() {
                advanceFrames(16)
                assertTrue(director.onObservedGaze(GazeTarget(.4f, -.2f), 80, 0))
            }
            fun observed() = reducer.pose(SystemClock.elapsedRealtime()).gazeObserved
            observe()
            assertTrue("Initial camera gaze must reach the visible head", observed())
            view.setForegroundActive(false)
            assertFalse(observed())
            observe()
            assertFalse(observed())
            view.setForegroundActive(true)
            observe()
            assertTrue("Fresh camera gaze must resume after foreground return", observed())
            view.setAnimationsEnabled(false)
            assertFalse(observed())
            observe()
            assertFalse(observed())
            view.setAnimationsEnabled(true)
            observe()
            assertTrue("Fresh gaze must resume after motion is re-enabled", observed())
            val oldSampleAt = SystemClock.elapsedRealtime()
            controller.get().setContentView(View(controller.get()))
            advanceFrames(16)
            assertFalse(view.isAttachedToWindow)
            assertFalse(observed())
            attachVisible(controller.get(), view)
            view.onAvatarEvent(AvatarEvent.GazeObserved(GazeTarget(-.4f, .2f), 80, oldSampleAt))
            assertFalse("Reattachment must still reject pre-detach samples", observed())
            observe()
            assertTrue("Fresh gaze must resume after view reattachment", observed())
        } finally { view.setNativeHeadEnabled(false); controller.pause().stop().destroy() }
    }
}
