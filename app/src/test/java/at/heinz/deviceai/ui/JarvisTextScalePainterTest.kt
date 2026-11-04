package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

/** Accessibility font scaling must affect measurement and glyphs together, never the live scene geometry. */
class JarvisTextScalePainterTest {
    @Test fun measuredAndDrawnTextUseTheSameScaledStyleWhileShapesKeepTheirBounds() {
        val drawn = mutableListOf<TextStyle>()
        val measured = mutableListOf<TextStyle>()
        val shapes = mutableListOf<List<Any?>>()
        val base = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, args ->
            when (method.name) {
                "drawText" -> { drawn += args[3] as TextStyle; null }
                "measureText" -> (args[1] as TextStyle).let { measured += it; (args[0] as String).length * it.size }
                "capHeight" -> (args[0] as TextStyle).size * .7f
                "fillRect" -> { shapes += args.toList(); null }
                else -> null
            }
        } as ContinuumPainter
        val painter = JarvisTextScalePainter(base)
        val style = TextStyle(FontFamily.TEXT, 500, 14f, .2f)
        val fill = Fill.Solid(Argb.rgb(234, 251, 255))
        painter.fontScale = 1.5f
        assertEquals(84f, painter.measureText("Test", style), .001f)
        assertEquals(14.7f, painter.capHeight(style), .001f)
        painter.drawText("Test", 10f, 20f, style, fill)
        painter.fillRect(1f, 2f, 31f, 42f, fill)
        assertEquals(measured.single(), drawn.single())
        assertEquals(21f, drawn.single().size, 0f)
        assertEquals(.3f, drawn.single().letterSpacing, .00001f)
        assertEquals(listOf(1f, 2f, 31f, 42f), shapes.single().take(4))
        assertEquals(14f, style.size, 0f)
        painter.fontScale = 2f
        painter.drawText("Test", 10f, 20f, style, fill)
        assertEquals(28f, drawn.last().size, 0f)
    }

    @Test fun invalidFontScaleCannotMakeTextNonFinite() {
        val base = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, args ->
            if (method.name == "capHeight") (args[0] as TextStyle).size else null
        } as ContinuumPainter
        val painter = JarvisTextScalePainter(base)
        val style = TextStyle(FontFamily.TEXT, 400, 12f)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            painter.fontScale = invalid
            assertEquals(12f, painter.capHeight(style), 0f)
        }
    }
}
