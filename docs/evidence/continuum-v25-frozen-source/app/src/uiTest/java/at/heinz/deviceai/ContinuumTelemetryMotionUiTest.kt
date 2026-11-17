package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import at.heinz.deviceai.ui.ContinuumDashboardState.SystemStatus
import at.heinz.deviceai.ui.ContinuumSystemGaugesView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumTelemetryMotionUiTest {
    private fun frame(view: View) = Bitmap.createBitmap(412, 240, Bitmap.Config.ARGB_8888).also {
        view.draw(Canvas(it))
    }

    @Test fun dashboardHighlightsStopWithoutNewSamplesAndPauseCancelsWork() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val home = at.heinz.deviceai.ui.ContinuumHomeView(controller.get(), ContinuumHologramView(controller.get())) { _, _, _ -> }
        val pending = home.javaClass.getDeclaredField("waveFramePending").apply { isAccessible = true }
        try {
            home.configure(412f, false)
            controller.get().setContentView(home)
            home.setForegroundActive(true)
            assertFalse(pending.getBoolean(home))
            val real = at.heinz.deviceai.ui.ContinuumDashboardState(
                system = SystemStatus(activity = listOf(8f, 22f, 12f)))
            home.state = real
            assertTrue(pending.getBoolean(home))
            shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
            assertFalse("A historic curve must stop highlighting", pending.getBoolean(home))
            home.state = real.copy()
            assertFalse("An unchanged refresh must not restart decoration", pending.getBoolean(home))
            home.state = real.copy(system = real.system.copy(activity = listOf(22f, 12f, 18f)))
            assertTrue(pending.getBoolean(home))
            home.setForegroundActive(false)
            assertFalse(pending.getBoolean(home))
            assertEquals(listOf(22f, 12f, 18f), home.state.system.activity)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun realSampleTransitionIsFiniteAndMissingDataNeverInterpolatesFromZero() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = ContinuumSystemGaugesView(controller.get(), SystemStatus(appCpuPercent = 10, memoryPercent = 40))
        val frames = mutableListOf<Bitmap>()
        try {
            controller.get().setContentView(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 412, 240)
            view.setForegroundActive(true)
            shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
            view.updateTelemetry(SystemStatus(appCpuPercent = 90, memoryPercent = 70))
            frames += frame(view)
            shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
            frames += frame(view)
            assertFalse("A measured change must glide rather than jump", frames[0].sameAs(frames[1]))
            assertTrue(view.contentDescription.contains("App-CPU: 90 Prozent"))
            assertTrue(view.contentDescription.contains("GPU: nicht messbar"))
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            frames += frame(view)
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            frames += frame(view)
            assertTrue("Settled data must not produce endless motion", frames[2].sameAs(frames[3]))
            view.updateTelemetry(SystemStatus())
            frames += frame(view)
            shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
            frames += frame(view)
            assertTrue("Unknown means absent, never a synthetic zero/ramp", frames[4].sameAs(frames[5]))
            assertTrue(view.contentDescription.contains("App-CPU: nicht messbar"))
            view.updateTelemetry(SystemStatus(appCpuPercent = 20))
            view.updateTelemetry(SystemStatus(appCpuPercent = 80))
            shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
            // Mid-transition reversal starts at the current pose, then pause settles to the true sample.
            view.updateTelemetry(SystemStatus(appCpuPercent = 30))
            view.setForegroundActive(false)
            frames += frame(view)
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            frames += frame(view)
            assertTrue(frames[6].sameAs(frames[7]))
            assertTrue(view.contentDescription.contains("App-CPU: 30 Prozent"))
        } finally {
            frames.forEach { it.recycle() }
            controller.pause().stop().destroy()
        }
    }
}
