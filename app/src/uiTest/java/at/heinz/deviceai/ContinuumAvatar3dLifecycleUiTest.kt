package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

/** Real View/Canvas lifecycle checks; never initializes Android Filament JNI on the host. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumAvatar3dLifecycleUiTest {
    private fun field(view: ContinuumHologramView, name: String): Any? =
        ContinuumHologramView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(view)
    private fun draw(view: ContinuumHologramView): Bitmap {
        view.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(307, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 412, 307)
        return Bitmap.createBitmap(412, 307, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
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
}
