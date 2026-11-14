package at.heinz.deviceai.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import at.heinz.deviceai.AndroidLiveFrame
import at.heinz.deviceai.avatar3d.CockpitStageFrame
import at.heinz.deviceai.cognitive.LatestLiveFrameHolder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumBunkerWorkspaceUiTest {
    @Test fun nativeWorkspaceYieldsToBoundPixelsAndCameraFlight() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val monitor = VirtualComputerMonitorView(controller.get())
        monitor.layout(0, 0, 600, 400)
        monitor.frame = CockpitStageFrame(600f, 400f, 100f, 100f, 500f, 350f, 0f)
        monitor.updateWorkspace(listOf("Kein Dokument geöffnet", "CPU —  ·  RAM —"))
        fun alpha(): Int {
            val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
            monitor.draw(Canvas(bitmap))
            val alpha = Color.alpha(bitmap.getPixel(300, 250))
            bitmap.recycle()
            return alpha
        }
        try {
            monitor.updateStatus("Keine Bildquelle verbunden", true, true)
            assertEquals(255, alpha())
            assertTrue(monitor.contentDescription.contains("CPU —"))
            monitor.updateStatus("Kamera bewegt sich", true, false)
            assertEquals(0, alpha())
            monitor.bind(LatestLiveFrameHolder<AndroidLiveFrame>({}))
            monitor.updateStatus("Quelle wartet", true, true)
            assertEquals(0, alpha())
            assertFalse(monitor.contentDescription.contains("Arbeitsbereich"))
            monitor.bind(null)
            monitor.updateStatus("Keine Bildquelle verbunden", false, true)
            assertEquals(0, alpha())
            monitor.updateStatus("Keine Bildquelle verbunden", true, true)
            assertEquals(255, alpha())
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
