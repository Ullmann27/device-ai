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

    @Test fun realFrameClockChangesPhoneAndFoldPixelsWithoutInjectingPhase() {
        for ((width, height) in listOf(412 to 307, 410 to 291)) {
            val controller = Robolectric.buildActivity(Activity::class.java).setup()
            val view = ContinuumHologramView(controller.get())
            val frames = mutableListOf<Bitmap>()
            try {
                assertTrue("Visual QA requires enabled Android animations", ValueAnimator.areAnimatorsEnabled())
                controller.get().setContentView(view)
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
                if (height == 291) view.setGeometry(at.heinz.deviceai.ui.ContinuumHologramRenderer.Geometry.FOLD)
                view.setForegroundActive(true)
                val phases = mutableListOf<Float>()
                repeat(3) { index ->
                    if (index > 0) shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                    phases.add(phase(view))
                    val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    frames.add(frame)
                    view.draw(Canvas(frame))
                    System.getProperty("continuum.ui.evidenceDir")?.let { path ->
                        val out = java.io.File(path).apply { mkdirs() }
                        java.io.File(out, "orb-${width}x$height-$index.png").outputStream().use {
                            assertTrue(frame.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    }
                }
                assertTrue(phases[1] > phases[0] && phases[2] > phases[1])
                assertFalse("First real second must visibly change Canvas pixels", frames[0].sameAs(frames[1]))
                assertFalse("Continuous motion must change the next second too", frames[1].sameAs(frames[2]))
                view.setForegroundActive(false)
                val paused = phase(view)
                shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                assertEquals("Paused view must keep its last pose", paused, phase(view), 0f)
                assertFalse(pending(view))
                repeat(3) { view.setForegroundActive(true) }
                shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
                assertTrue("Resume must not duplicate the clock", phase(view) - paused in 0.1f..0.6f)
            } finally {
                frames.forEach { it.recycle() }
                controller.pause().stop().destroy()
            }
        }
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

    @Test fun differentOrbitalPhasesActuallyChangeRenderedPixels() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val view = ContinuumHologramView(activity)
        try {
            view.setAnimationsEnabled(false)
            activity.setContentView(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(307, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 412, 307)
            val phaseField = ContinuumHologramView::class.java.getDeclaredField("phase").apply {
                isAccessible = true
            }
            val before = Bitmap.createBitmap(412, 307, Bitmap.Config.ARGB_8888)
            val after = Bitmap.createBitmap(412, 307, Bitmap.Config.ARGB_8888)
            try {
                phaseField.setFloat(view, 0.4f)
                view.draw(Canvas(before))
                phaseField.setFloat(view, 2.6f)
                view.draw(Canvas(after))
                assertFalse(
                    "Real Android Canvas pixels must change with the orb/ring phase",
                    before.sameAs(after)
                )
            } finally {
                before.recycle()
                after.recycle()
            }
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun deviceMotionDiagnosticNeverReportsLiveBeforeForegroundActivation() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = ContinuumHologramView(controller.get())
        try {
            controller.get().setContentView(view)
            assertFalse(view.motionDiagnostics().contains("LIVE"))
            view.setForegroundActive(false)
            assertFalse(view.motionDiagnostics().contains("LIVE"))
        } finally { controller.pause().stop().destroy() }
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
