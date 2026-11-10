package at.heinz.deviceai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import at.heinz.deviceai.systemintelligence.SystemDiagnosis
import at.heinz.deviceai.ui.SystemDiagnosisHologramView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SystemSurfaceCaptureTest {
    @Test fun actualAndroidViewRendersUnknownAndMeasuredFixture() {
        val fixture = SystemDiagnosis(1000, 37, 256L shl 30, 41L shl 30,
            12L shl 30, 3L shl 30, false, 460L shl 20, 73, 37.2, false, 2, 84.2, 250)
        for ((name, sample) in listOf("unknown" to null, "measured-fixture" to fixture)) {
            val view = SystemDiagnosisHologramView(RuntimeEnvironment.getApplication(), sample)
            view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 720, 560)
            val bitmap = Bitmap.createBitmap(720, 600, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.rgb(4, 12, 23))
                view.draw(canvas)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=20f }
                canvas.drawText("NATIVE TESTDARSTELLUNG · keine Fold7-Messung", 18f, 584f, paint)
                val out = File(System.getProperty("continuum.surface.output"), "system-$name.png")
                out.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
                assertTrue(out.length()>5000)
                assertTrue(view.contentDescription.contains(if(sample==null) "Noch keine Messung" else "37.2"))
            } finally { bitmap.recycle() }
        }
    }
}
