package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import at.heinz.deviceai.ui.ContinuumHomeLayout
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Real Android Canvas of the existing home widget. No device, model or GPU claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumAdaptiveCockpitUiTest {
    @Test fun renderPhoneFoldAndTabletAtNormalAndLargeFonts() {
        for ((width, height) in listOf(320 to 720, 412 to 915, 600 to 840, 840 to 960, 1280 to 800)) {
            for (font in listOf(1f, 1.3f, 1.6f)) {
                val controller = Robolectric.buildActivity(Activity::class.java).setup()
                val activity = controller.get()
                val configuration = android.content.res.Configuration(activity.resources.configuration).apply { fontScale = font }
                val context = activity.createConfigurationContext(configuration)
                val orb = ContinuumHologramView(context).apply {
                    setNativeHeadEnabled(false)
                    setAnimationsEnabled(false)
                }
                val clicked = mutableListOf<ContinuumHomeLayout.Slot>()
                val home = ContinuumHomeView(context, orb) { slot, _, _ -> clicked += slot }
                try {
                    home.configure(width.toFloat(), ContinuumHomeLayout.isWide(width.toFloat(), height.toFloat()),
                        windowHeightDp = height.toFloat())
                    activity.setContentView(home)
                    home.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
                    home.layout(0, 0, width, home.measuredHeight)
                    assertTrue(home.height > 0)
                    val bitmap = Bitmap.createBitmap(width, home.height, Bitmap.Config.ARGB_8888)
                    try {
                        home.draw(Canvas(bitmap))
                        System.getProperty("continuum.ui.evidenceDir")?.let { dir ->
                            File(dir).mkdirs()
                            File(dir, "cockpit-${width}x$height-font$font.png").outputStream().use {
                                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                            }
                        }
                    } finally { bitmap.recycle() }
                    for (index in 0 until home.childCount) {
                        val child = home.getChildAt(index)
                        if (child is ContinuumHomeView.ElementView) {
                            assertTrue("${child.slot} has spoken real state", child.contentDescription.isNotBlank())
                            child.performClick()
                            assertEquals(child.slot, clicked.last())
                        }
                    }
                } finally { controller.pause().stop().destroy() }
            }
        }
    }
}
