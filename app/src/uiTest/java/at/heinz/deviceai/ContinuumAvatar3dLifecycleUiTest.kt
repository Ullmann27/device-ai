package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import at.heinz.deviceai.avatar.AvatarEvent
import at.heinz.deviceai.avatar.AvatarPoseReducer
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

    private fun field(view: ContinuumHologramView, name: String): Any? =
        ContinuumHologramView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(view)
    private fun draw(view: ContinuumHologramView): Bitmap {
        view.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(307, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 412, 307)
        return Bitmap.createBitmap(412, 307, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    @Test fun newAppearanceUses3dButSavedOrbAndLegacy2dStayChosen() {
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
            assertEquals(false, field(legacy, "head3dEnabled"))
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
            controller.get().setContentView(view)
            val orb = draw(view)
            try {
                assertEquals(0, view.childCount); assertNull(field(view, "native3d")); assertEquals(false, field(view, "assetLoading"))
                view.setNative3dEnabled(true) // Paused: even explicit selection must not start IO or native work.
                draw(view).recycle()
                assertTrue(view.isNativeHeadEnabled()); assertEquals(0, view.childCount)
                assertNull(field(view, "native3d")); assertNull(field(view, "assetBytes")); assertEquals(false, field(view, "assetLoading"))
                shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                assertEquals(0L, view.sampleMotion().callbackCount)
                view.setNativeHeadEnabled(false)
                val restored = draw(view)
                try { assertTrue("Opt-out restores original Canvas orb", orb.sameAs(restored)) } finally { restored.recycle() }
            } finally { orb.recycle() }
        } finally { view.setNativeHeadEnabled(false); controller.pause().stop().destroy() }
    }
    @Test fun actualFailurePathRetains2dAndOriginalSingleLifecycleClock() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = ContinuumHologramView(controller.get())
        try {
            controller.get().setContentView(view); view.setNativeHeadEnabled(true); view.setAnimationsEnabled(false)
            val baseline = draw(view)
            try {
                view.setNative3dEnabled(true)
                ContinuumHologramView::class.java.getDeclaredMethod("fail3d").apply { isAccessible = true }.invoke(view)
                view.setForegroundActive(true)
                val fallback = draw(view)
                try { assertTrue("Rejected 3D must show existing 2D pixels", baseline.sameAs(fallback)) } finally { fallback.recycle() }
                assertEquals(0, view.childCount); assertNull(field(view, "native3d"))
                shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                assertEquals(0L, view.sampleMotion().callbackCount)
                view.setAnimationsEnabled(true)
                shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
                assertTrue(view.sampleMotion().callbackCount > 0)
                view.setForegroundActive(false)
                val paused = view.sampleMotion().callbackCount
                shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                assertEquals(paused, view.sampleMotion().callbackCount)
                assertNull(field(view, "nativeSurface")); assertNull(field(view, "native3d"))
                controller.get().setContentView(View(controller.get()))
                assertNull(field(view, "assetBytes")); assertNull(field(view, "nativeTexture"))
            } finally { baseline.recycle() }
        } finally { view.setNativeHeadEnabled(false); controller.pause().stop().destroy() }
    }

    @Test fun pauseAndReducedMotionClearObservedGazeAndRejectLateCameraSamples() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = ContinuumHologramView(controller.get())
        try {
            view.setNativeHeadEnabled(true)
            controller.get().setContentView(view)
            view.setForegroundActive(true)
            val reducer = field(view, "avatarPose") as AvatarPoseReducer
            fun observe() {
                // The View's clear uses wall time; virtual Looper idle need not advance that clock.
                val cleared = AvatarPoseReducer::class.java.getDeclaredField("gazeClearedAtMillis")
                    .apply { isAccessible = true }.getLong(reducer)
                val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100)
                while (System.currentTimeMillis() <= cleared && System.nanoTime() < deadline) Thread.sleep(1)
                val now = System.currentTimeMillis()
                assertTrue("Real wall time did not advance beyond the actual clear watermark within 100ms", now > cleared)
                view.onAvatarEvent(AvatarEvent.GazeObserved(GazeTarget(.4f, -.2f), 80, now))
            }
            observe()
            assertTrue(reducer.pose(System.currentTimeMillis()).gazeObserved)
            view.setForegroundActive(false)
            assertFalse(reducer.pose(System.currentTimeMillis()).gazeObserved)
            observe()
            assertFalse(reducer.pose(System.currentTimeMillis()).gazeObserved)
            view.setForegroundActive(true)
            observe()
            assertTrue(reducer.pose(System.currentTimeMillis()).gazeObserved)
            view.setAnimationsEnabled(false)
            assertFalse(reducer.pose(System.currentTimeMillis()).gazeObserved)
            observe()
            assertFalse(reducer.pose(System.currentTimeMillis()).gazeObserved)
        } finally { view.setNativeHeadEnabled(false); controller.pause().stop().destroy() }
    }
}
