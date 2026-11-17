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
    @Test fun phoneToolDockKeepsSevenCompleteLabelsAtTheOriginalTextSize() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val assets = controller.get().assets
        try {
            for (width in listOf(392f, 412f)) {
                val shell = CinematicWorkstationLayout.create(width, 852f)
                val dock = requireNotNull(shell[JarvisShellLayout.Slot.BOTTOM_NAV])
                val cells = JarvisTiles.bottomNavItemBoxes(dock)
                val labels = mutableListOf<String>()
                val bitmap = Bitmap.createBitmap(ceil(width).toInt(), ceil(dock.bottom).toInt(), Bitmap.Config.ARGB_8888)
                try {
                    val native = AndroidContinuumPainter(assets).apply { begin(Canvas(bitmap)) }
                    val measured = object : ContinuumPainter by native {
                        override fun drawText(text: String, x: Float, y: Float, style: TextStyle,
                                              fill: Fill, align: TextAlign, alpha: Float) {
                            val index = cells.indexOfFirst { x >= it.left && x < it.right && y >= it.top && y < it.bottom }
                            assertTrue("$width has an unbound dock label '$text'", index >= 0)
                            val cell = cells[index]
                            assertEquals("The German destination is drawn in full", JarvisTiles.bottomNav[index].label, text)
                            assertEquals("Use the original label size", CockpitGlassButtons.LABEL_DP, style.size, .001f)
                            val textWidth = native.measureText(text, style)
                            assertTrue("$width '$text' remains inside the glass body's content area",
                                x - textWidth * .5f >= cell.left + 7f && x + textWidth * .5f <= cell.right - 7f)
                            labels += text
                            native.drawText(text, x, y, style, fill, align, alpha)
                        }
                    }
                    JarvisTiles.drawBottomNav(measured, dock)
                    assertEquals(JarvisTiles.bottomNav.map { it.label }, labels)
                    assertEquals("Two rows retain their original height", 2, cells.map { it.top }.distinct().size)
                    assertEquals(JarvisShellLayout.TOOL_DOCK_HEIGHT_DP + JarvisTiles.DOCK_EXTRA_ROW_DP, dock.height, .001f)
                    cells.forEachIndexed { index, cell ->
                        assertTrue("Every destination keeps a 48dp target", cell.width >= 48f && cell.height >= 48f)
                        assertEquals(JarvisTiles.bottomNav[index].target,
                            JarvisShellContent.hitTarget(shell, JarvisTiles.reference, cell.centerX, cell.centerY))
                    }
                } finally { bitmap.recycle() }
            }
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun actualConversationRailFitsItsRowsAndKeepsFullAccessibleNames() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val assets = controller.get().assets
        val typeface = Typeface.createFromAsset(assets, ContinuumFonts.assetPath(FontFamily.TEXT, 400))
        val metrics = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface }
        val ink = Rect()
        try {
            for (width in listOf(600f, 692f, 840f)) {
                for (font in listOf(1f, 1.5f, 2f)) {
                    val railWidth = ContinuumHomeLayout.railWidthDp(width)
                    val height = JarvisMenuRail.minHeightDp()
                    val rows = JarvisMenuRail.rows(height)
                    val element = JarvisMenuRailElement(textScale = font.coerceIn(.85f, 1.3f))
                    val state = ContinuumDashboardState()
                    val labels = List(rows.size) { mutableListOf<String>() }
                    val bitmap = Bitmap.createBitmap(ceil(railWidth).toInt(), ceil(height).toInt(),
                        Bitmap.Config.ARGB_8888)
                    try {
                        val native = AndroidContinuumPainter(assets).apply { begin(Canvas(bitmap)) }
                        val measured = object : ContinuumPainter by native {
                            override fun drawText(text: String, x: Float, y: Float, style: TextStyle,
                                                  fill: Fill, align: TextAlign, alpha: Float) {
                                if (y < JarvisMenuRail.FIRST_ROW) {
                                    native.drawText(text, x, y, style, fill, align, alpha)
                                    return
                                }
                                val index = rows.indexOfFirst { y >= it.first && y < it.second }
                                assertTrue("$width/$font unbound label '$text'", index >= 0)
                                val row = rows[index]
                                assertFalse("$width/$font empty label", text.isBlank())
                                val textWidth = native.measureText(text, style)
                                assertTrue("$width/$font '$text' exceeds row width",
                                    x >= JarvisMenuRail.MARGIN && x + textWidth <= railWidth - 2f)
                                assertTrue("$width/$font text shrunk too far",
                                    style.size >= JarvisDesign.Type.nav.size - .01f)
                                metrics.textSize = style.size
                                metrics.letterSpacing = style.letterSpacing / style.size
                                metrics.getTextBounds(text, 0, text.length, ink)
                                assertTrue("$width/$font '$text' exceeds row height",
                                    y + ink.top >= row.first && y + ink.bottom <= row.second)
                                labels[index] += text
                                native.drawText(text, x, y, style, fill, align, alpha)
                            }
                        }
                        element.draw(measured, railWidth, height, state)
                        JarvisMenu.railEntries.forEachIndexed { index, entry ->
                            assertEquals("$width/$font/${entry.target} has one visible label", 1, labels[index].size)
                            assertTrue("The complete destination remains accessible",
                                element.describe(state).contains(entry.label))
                            assertTrue("$width/$font target remains touchable", rows[index].second - rows[index].first >= 48f)
                        }
                    } finally { bitmap.recycle() }
                }
            }
        } finally { controller.pause().stop().destroy() }
    }
}
