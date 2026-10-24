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
import org.robolectric.annotation.LooperMode
import java.time.Duration

/** Real attached Android view/frame callbacks; no phone or GPU performance claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumHologramMotionUiTest {
    private fun phase(view: ContinuumHologramView) =
        ContinuumHologramView::class.java.getDeclaredField("phase").apply { isAccessible = true }.getFloat(view)
    private fun pending(view: ContinuumHologramView) =
        ContinuumHologramView::class.java.getDeclaredField("framePending").apply { isAccessible = true }.getBoolean(view)
    private fun host(view: ContinuumHologramView, resumed: Boolean) {
        ContinuumHologramView::class.java.getMethod("setHostResumed", Boolean::class.javaPrimitiveType).invoke(view, resumed)
    }
    private fun withView(test: (ContinuumHologramView) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val view = ContinuumHologramView(controller.get())
        controller.get().setContentView(view)
        view.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(307, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 412, 307)
        view.onVisibilityAggregated(true)
        try { test(view) } finally { controller.pause().stop().destroy() }
    }
    private fun tick(ms: Long = 1000) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun visibleFrameCallbacksAdvanceTheCoreEvenWhenRenderingIsCoalesced() = withView { view ->
        val bitmap = Bitmap.createBitmap(412, 307, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val start = phase(view)
        tick(2000)
        assertTrue("Animation time must progress from its frame callback, without another caller drawing it", phase(view) - start > .5f)
        assertTrue(pending(view))
        bitmap.recycle()
    }

    @Test fun hostPauseResumeAndRepeatedResumeOwnExactlyOneLoop() = withView { view ->
        tick(200)
        host(view, false)
        val paused = phase(view)
        assertFalse(pending(view))
        tick()
        assertEquals(paused, phase(view), 0f)
        repeat(4) { host(view, true) }
        tick()
        assertTrue(phase(view) > paused)
        assertTrue("Repeated resumes must not accelerate the clock", phase(view) - paused < .6f)
        host(view, false)
        assertFalse(pending(view))
    }

    @Test fun hiddenAndReducedMotionViewsStopAndResumeWithoutRestartJumps() = withView { view ->
        tick(200)
        view.visibility = View.GONE
        view.onVisibilityAggregated(false)
        val hidden = phase(view)
        tick()
        assertFalse(pending(view))
        assertEquals(hidden, phase(view), 0f)
        view.visibility = View.VISIBLE
        view.onVisibilityAggregated(true)
        view.setAnimationsEnabled(false)
        tick()
        assertFalse(pending(view))
        assertEquals(hidden, phase(view), 0f)
        view.setAnimationsEnabled(true)
        tick(500)
        assertTrue(phase(view) > hidden)
        assertTrue(phase(view) - hidden < .4f)
    }
}
