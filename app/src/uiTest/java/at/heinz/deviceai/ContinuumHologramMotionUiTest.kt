package at.heinz.deviceai

import android.animation.ValueAnimator
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

/**
 * Runs the actual Android View under Robolectric, not a screenshot or stub renderer.
 * It never claims a Fold device was tested and does not run Termux or sign an APK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumHologramMotionUiTest {
    private fun pending(view: ContinuumHologramView): Boolean =
        ContinuumHologramView::class.java.getDeclaredField("framePending").let {
            it.isAccessible = true
            it.getBoolean(view)
        }

    private fun phase(view: ContinuumHologramView): Float =
        ContinuumHologramView::class.java.getDeclaredField("phase").let {
            it.isAccessible = true
            it.getFloat(view)
        }

    @Test fun foregroundAndReducedMotionHaveOneBoundedLifecycleClock() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val view = ContinuumHologramView(activity)
        try {
            view.setAnimationsEnabled(false)
            activity.setContentView(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(307, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 412, 307)
            view.setForegroundActive(true)
            assertFalse("Reduced motion must not queue rendering work", pending(view))
            view.setAnimationsEnabled(true)
            val platformAllowsMotion = ValueAnimator.areAnimatorsEnabled()
            if (platformAllowsMotion) {
                assertTrue("Visible foreground core must schedule its animation", pending(view))
                val before = phase(view)
                shadowOf(Looper.getMainLooper()).idleFor(120, TimeUnit.MILLISECONDS)
                assertTrue("Animated phase must advance from a frame callback", phase(view) > before)
            }
            view.setForegroundActive(false)
            assertFalse("Paused Activity must cancel any pending orbit callback", pending(view))
            assertTrue(phase(view).isFinite())
            view.setForegroundActive(true)
            if (platformAllowsMotion) assertTrue("Resume must schedule exactly one callback", pending(view))
            view.setAnimationsEnabled(false)
            assertFalse("Turning animation off cancels the callback", pending(view))
        } finally {controller.pause().stop().destroy()}
    }

    @Test fun renderingAtRestDoesNotByItselfAdvanceTheOrbitClock() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val view = ContinuumHologramView(activity)
        try {
            view.setAnimationsEnabled(false)
            activity.setContentView(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(307, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 412, 307)
            view.setForegroundActive(false)
            val before = phase(view)
            val bitmap = Bitmap.createBitmap(412, 307, Bitmap.Config.ARGB_8888)
            try { view.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
            assertEquals("Canvas draws must not own phase updates", before, phase(view), 0f)
            assertFalse(pending(view))
        } finally {controller.pause().stop().destroy()}
    }
}
