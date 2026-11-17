package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class JarvisLightStreamReuseTest {
    private data class Stroke(val path: VectorPath, val ops: List<VectorPath.Op>,
                              val width: Float, val fill: Fill, val alpha: Float)

    private fun draw(width: Float, height: Float): List<Stroke> {
        val strokes = mutableListOf<Stroke>()
        val painter = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, args ->
            when (method.name) {
                "strokePath" -> {
                    val path = args[0] as VectorPath
                    strokes += Stroke(path, path.ops.toList(), args[1] as Float,
                        args[2] as Fill, args[3] as Float)
                    null
                }
                "measureText", "capHeight" -> 0f
                "drawRaster" -> false
                else -> null
            }
        } as ContinuumPainter
        JarvisHeadAura.drawLightStreams(painter, width, height)
        return strokes
    }

    @Test fun unchangedFramesReusePathsAndGradient() {
        val first = draw(320f, 410f)
        repeat(120) {
            val next = draw(320f, 410f)
            assertEquals(4, next.size)
            for (i in first.indices) {
                assertSame("static path must reuse native cache", first[i].path, next[i].path)
                assertSame("static fill must reuse shader", first[i].fill, next[i].fill)
                assertEquals(first[i].ops, next[i].ops)
                assertEquals(first[i].width, next[i].width, 0f)
                assertEquals(first[i].alpha, next[i].alpha, 0f)
            }
        }
    }

    @Test fun fractionalResizeKeepsExactAuthoredGeometry() {
        draw(320f, 410f)
        val w = 320.25f
        val h = 410.5f
        val next = draw(w, h)
        assertEquals(listOf(
            VectorPath.Op.Move(-w * .05f, h * .93f),
            VectorPath.Op.Cubic(w * .30f, h * .80f, w * .62f, h * .99f, w * 1.05f, h * .84f)
        ), next[0].ops)
        assertEquals(listOf(
            VectorPath.Op.Move(-w * .05f, h * .86f),
            VectorPath.Op.Cubic(w * .36f, h * .97f, w * .70f, h * .78f, w * 1.05f, h * .92f)
        ), next[2].ops)
        val fill = next[0].fill as Fill.Linear
        assertEquals(w, fill.x1, 0f)
        assertEquals(listOf(6f, 1.2f, 6f, 1.2f), next.map { it.width })
        assertEquals(listOf(.10f, .85f, .10f, .85f), next.map { it.alpha })
    }

    @Test fun foldedAndUnfoldedStagesNeverRetainStalePaths() {
        val before = draw(280f, 440f)
        draw(620f, 530f)
        val restored = draw(280f, 440f)
        assertEquals(before.map { it.ops }, restored.map { it.ops })
        assertArrayEquals((before[0].fill as Fill.Linear).colors,
            (restored[0].fill as Fill.Linear).colors)
        assertSame(restored[0].path, restored[1].path)
        assertSame(restored[2].path, restored[3].path)
        assertNotSame(restored[0].path, restored[2].path)
    }

    @Test fun nonDrawableSizesProduceNoCommands() {
        for ((w, h) in listOf(0f to 410f, 320f to 8f, 8f to 410f, -1f to 410f))
            assertTrue(draw(w, h).isEmpty())
    }
}
