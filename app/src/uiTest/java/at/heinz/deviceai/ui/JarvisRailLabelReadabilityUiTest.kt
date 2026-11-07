package at.heinz.deviceai.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JarvisRailLabelReadabilityUiTest {
    @Test fun nativeRailKeepsEveryDestinationNameComplete() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val assets = controller.get().assets
        val typeface = Typeface.createFromAsset(assets, ContinuumFonts.assetPath(FontFamily.TEXT, 400))
        val metrics = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface }
        val ink = Rect()
        try {
            for (width in listOf(600f, 692f, 840f)) {
                for (font in listOf(1f, 1.5f, 2f)) {
                    val shell = JarvisShellLayout.create(width, 960f, font)
                    val rail = requireNotNull(shell[JarvisShellLayout.Slot.NAV_RAIL])
                    val height = JarvisTiles.railContentHeight(rail.width, font)
                    val bounds = JarvisShellLayout.Box(0f, 0f, rail.width, height)
                    val rows = JarvisTiles.railItemBoxes(bounds, fontScale = font)
                    val labels = List(rows.size) { mutableListOf<String>() }
                    val bitmap = Bitmap.createBitmap(ceil(rail.width).toInt(), ceil(height).toInt(),
                        Bitmap.Config.ARGB_8888)
                    try {
                        val native = AndroidContinuumPainter(assets).apply { begin(Canvas(bitmap)) }
                        val measured = object : ContinuumPainter by native {
                            override fun drawText(text: String, x: Float, y: Float, style: TextStyle,
                                                  fill: Fill, align: TextAlign, alpha: Float) {
                                val index = rows.indexOfFirst { y >= it.top && y < it.bottom }
                                assertTrue("$width/$font unbound label '$text'", index >= 0)
                                val row = rows[index]
                                assertFalse("$width/$font truncated '$text'", text.contains('…'))
                                val textWidth = native.measureText(text, style)
                                assertTrue("$width/$font '$text' exceeds row width",
                                    x >= row.left && x + textWidth <= row.right - 2f)
                                assertTrue("$width/$font text shrunk too far",
                                    style.size >= JarvisDesign.Type.nav.size * font * .88f - .01f)
                                metrics.textSize = style.size
                                metrics.letterSpacing = style.letterSpacing / style.size
                                metrics.getTextBounds(text, 0, text.length, ink)
                                assertTrue("$width/$font '$text' exceeds row height",
                                    y + ink.top >= row.top && y + ink.bottom <= row.bottom)
                                labels[index] += text
                                native.drawText(text, x, y, style, fill, align, alpha)
                            }
                        }
                        JarvisTiles.drawRail(measured, bounds, fontScale = font)
                        JarvisTiles.railNav.forEachIndexed { index, entry ->
                            val complete = labels[index].joinToString(" ").replace("- ", "")
                            assertEquals("$width/$font/${entry.target}", entry.label, complete)
                            assertTrue("$width/$font target remains touchable", rows[index].height >= 48f)
                        }
                    } finally { bitmap.recycle() }
                }
            }
        } finally { controller.pause().stop().destroy() }
    }
}
