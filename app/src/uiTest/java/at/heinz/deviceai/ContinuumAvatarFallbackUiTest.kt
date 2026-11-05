package at.heinz.deviceai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import at.heinz.deviceai.ui.ContinuumHologramRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A 3D loading/error screen must preserve the existing animated 2D head. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumAvatarFallbackUiTest {
    private fun portrait(threeD: Boolean, failed: Boolean = false): Bitmap {
        val view = ContinuumHologramView(RuntimeEnvironment.getApplication())
        view.setAnimationsEnabled(false)
        view.setNativeHeadEnabled(true)
        if (threeD) view.setNative3dEnabled(true)
        if (failed) ContinuumHologramView::class.java.getDeclaredField("native3dFailed")
            .apply { isAccessible = true }.setBoolean(view, true)
        view.setGeometry(ContinuumHologramRenderer.Geometry.PHONE)
        val w = 412; val h = 420
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun assertHeadRetained(reference: Bitmap, candidate: Bitmap) {
        var headPixels = 0
        var retained = 0
        for (y in 0 until reference.height * 3 / 4) for (x in 0 until reference.width) {
            val pixel = reference.getPixel(x, y)
            if ((pixel ushr 24) <= 10) continue
            headPixels++
            if (candidate.getPixel(x, y) == pixel) retained++
        }
        assertTrue("Head fixture must contain real vector pixels", headPixels > 1_000)
        assertTrue("3D loading/error state must keep 2D head, $retained/$headPixels",
            retained * 100 >= headPixels * 95)
    }

    @Test fun first3dFrameNotReadyKeepsTheExistingPortrait() {
        val control = portrait(threeD = false)
        val loading = portrait(threeD = true)
        try {
            assertHeadRetained(control, loading)
        } finally {
            control.recycle(); loading.recycle()
        }
    }

    @Test fun failed3dRendererStillKeepsThePortrait() {
        val control = portrait(threeD = false)
        val failed = portrait(threeD = true, failed = true)
        try { assertHeadRetained(control, failed) }
        finally { control.recycle(); failed.recycle() }
    }
}
