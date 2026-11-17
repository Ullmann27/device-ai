package at.heinz.deviceai.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import at.heinz.deviceai.avatar3d.CockpitStageFrame
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CockpitMonitorPixelsUiTest {
    @Test fun appPixelsRemainOpaqueCorrectWayRoundAndUntinted() {
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val input = Canvas(source)
        val paint = android.graphics.Paint()
        paint.color = Color.RED; input.drawRect(0f, 0f, 200f, 100f, paint)
        paint.color = Color.GREEN; input.drawRect(200f, 0f, 400f, 100f, paint)
        paint.color = Color.BLUE; input.drawRect(0f, 100f, 200f, 200f, paint)
        paint.color = Color.YELLOW; input.drawRect(200f, 100f, 400f, 200f, paint)
        val frame = CockpitStageFrame(600f, 400f, 100f, 100f, 500f, 350f, 0f)
        val out = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        CockpitMonitorPainter().draw(Canvas(out), frame, source, "Testbild", false, true, 1f)
        assertEquals(Color.RED, out.getPixel(210, 180))
        assertEquals(Color.GREEN, out.getPixel(390, 180))
        assertEquals(Color.BLUE, out.getPixel(210, 270))
        assertEquals(Color.YELLOW, out.getPixel(390, 270))
        assertEquals(255, Color.alpha(out.getPixel(210, 180)))
        source.recycle(); out.recycle()
    }
}
