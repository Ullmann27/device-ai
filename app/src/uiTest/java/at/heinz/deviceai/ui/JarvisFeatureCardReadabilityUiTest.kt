package at.heinz.deviceai.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class JarvisFeatureCardReadabilityUiTest {
    @Test fun realFontsKeepEveryCardLabelAndDescriptionComplete() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val assets = controller.get().assets
        val faces = mutableMapOf<String, Typeface>()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val ink = Rect()
        try {
            for ((width, height) in listOf(692f to 768f, 840f to 960f, 412f to 915f)) {
                for (font in listOf(1f, 1.5f, 2f)) {
                    val shell = JarvisShellLayout.create(width, height, font)
                    val cards = shell.tileBoxes()
                    assertNull("Home has no fixed side rail", shell[JarvisShellLayout.Slot.NAV_RAIL])
                    if (!shell.hasFeatureTiles) {
                        assertNotNull(shell[JarvisShellLayout.Slot.BOTTOM_NAV])
                        continue
                    }
                    assertEquals(10, cards.size)
                    JarvisTiles.reference.forEachIndexed { index, tile ->
                        val card = cards[index]
                        val b = JarvisShellLayout.Box(0f, 0f, card.width, card.height)
                        val bitmap = Bitmap.createBitmap(ceil(b.width).toInt(), ceil(b.height).toInt(),
                            Bitmap.Config.ARGB_8888)
                        val title = mutableListOf<String>()
                        val subtitle = mutableListOf<String>()
                        val description = JarvisCockpitGridContent.tileSubtitles.getValue(tile.target)
                        val key = "$width/$font/${tile.target}"
                        try {
                            val native = AndroidContinuumPainter(assets).apply { begin(Canvas(bitmap)) }
                            val measured = object : ContinuumPainter by native {
                                override fun drawText(text: String, x: Float, y: Float, style: TextStyle,
                                                      fill: Fill, align: TextAlign, alpha: Float) {
                                    assertFalse("$key truncated '$text', size=${style.size}, card=${b.width}", text.contains('…'))
                                    assertEquals(TextAlign.CENTER, align)
                                    val textWidth = native.measureText(text, style)
                                    assertTrue("$key '$text' outside width",
                                        x - textWidth / 2f >= b.left + 7f &&
                                            x + textWidth / 2f <= b.right - 7f)
                                    val label = style.weight == JarvisDesign.Type.tileLabel.weight
                                    val minimum = (if (label) 15f else 12f) * font * .88f
                                    assertTrue("$key '$text' too small: ${style.size}", style.size >= minimum - .01f)
                                    val asset = ContinuumFonts.assetPath(style.family, style.weight)
                                    paint.typeface = faces.getOrPut(asset) { Typeface.createFromAsset(assets, asset) }
                                    paint.textSize = style.size
                                    paint.letterSpacing = style.letterSpacing / style.size
                                    paint.getTextBounds(text, 0, text.length, ink)
                                    assertTrue("$key '$text' outside height: $y/$ink",
                                        y + ink.top >= b.top + 4f && y + ink.bottom <= b.bottom - 4f)
                                    if (label) title += text else subtitle += text
                                    native.drawText(text, x, y, style, fill, align, alpha)
                                }
                            }
                            val painter = JarvisTextScalePainter(measured).apply { fontScale = font }
                            JarvisCockpitGridContent.drawTile(painter, b, tile, description)
                            assertEquals("$key full label", tile.label,
                                title.joinToString(" ").replace("- ", ""))
                            assertEquals("$key full description", description.joinToString(" "),
                                subtitle.joinToString(" ").replace("- ", ""))
                        } finally { bitmap.recycle() }
                    }
                }
            }
        } finally { controller.pause().stop().destroy() }
    }
}
