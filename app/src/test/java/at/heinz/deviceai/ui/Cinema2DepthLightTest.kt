package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Cinema 2.0, milestone 1 (owner brief 11.11.2026) – pure rules of the three depth/light improvements, HOST/JVM:
 * focus depth behind a constructed window, glass that is dense only where real content stands, and the energy band
 * from the original orb to the window while real work runs. No clock is read; budgets and reduced motion win.
 */
class Cinema2DepthLightTest {
    private val high = JarvisRenderQuality.resolve(true, false, 0, false, false)
    private val suspended = JarvisRenderQuality.resolve(false, false, 0, false, false)
    private val window = HollywoodFuiCinematics.Box(40f, 280f, 680f, 750f)

    private class Calls {
        var strokes = 0; var fills = 0; var additiveOn = 0; var additiveOff = 0
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    }

    /** Counts the draw calls a renderer really makes and the extent of its segment batches. */
    private fun painter(calls: Calls): ContinuumPainter = Proxy.newProxyInstance(
        ContinuumPainter::class.java.classLoader, arrayOf(ContinuumPainter::class.java)) { _, method, args ->
        when (method.name) {
            "strokeSegments" -> {
                calls.strokes++
                val points = args[0] as FloatArray
                val count = args[1] as Int
                for (i in 0 until count step 2) {
                    calls.minX = minOf(calls.minX, points[i]); calls.maxX = maxOf(calls.maxX, points[i])
                    calls.minY = minOf(calls.minY, points[i + 1]); calls.maxY = maxOf(calls.maxY, points[i + 1])
                }
            }
            "fillRect", "fillRoundRect", "fillOval", "strokeRoundRect", "drawLine", "strokeOval" -> calls.fills++
            "setAdditive" -> if (args[0] as Boolean) calls.additiveOn++ else calls.additiveOff++
            "measureText", "capHeight" -> return@newProxyInstance 0f
        }
        null
    } as ContinuumPainter

    @Test fun focusDepthFollowsTheGlassAndLeavesWithIt() {
        val forming = HollywoodFuiConstruct.frame(HollywoodFuiConstruct.BRACKETS_END_MS)
        val settled = HollywoodFuiConstruct.frame(HollywoodFuiConstruct.DATA_END_MS)
        val closed = HollywoodFuiConstruct.frame(HollywoodFuiConstruct.DATA_END_MS, HollywoodFuiConstruct.CLOSE_END_MS)
        assertEquals("no dim before the glass exists", 0f, HollywoodFuiCinematics.focusDepth(forming, 0f), 0f)
        assertEquals("no dim before the window has arrived", 0f, HollywoodFuiCinematics.focusDepth(settled, 0f), 0f)
        assertEquals(1f, HollywoodFuiCinematics.focusDepth(settled, 1f), 1e-4f)
        assertEquals("the deck is fully back after the close", 0f, HollywoodFuiCinematics.focusDepth(closed, 1f), 1e-4f)
        var last = -1f
        for (t in 0L..HollywoodFuiConstruct.DATA_END_MS step 16) {
            val depth = HollywoodFuiCinematics.focusDepth(HollywoodFuiConstruct.frame(t), 1f)
            assertTrue("continuous and monotone while the window forms ($t ms)", depth in 0f..1f && depth >= last - 1e-4f)
            assertTrue("no jump ($t ms)", last < 0f || depth - last < .2f)
            last = depth
        }
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f))
            assertEquals(0f, HollywoodFuiCinematics.focusDepth(settled, bad), 0f)
        assertTrue("the deck stays readable", HollywoodFuiCinematics.FOCUS_DIM_FAR <= .6f)
        assertTrue(HollywoodFuiCinematics.FOCUS_DIM_NEAR < HollywoodFuiCinematics.FOCUS_DIM_FAR)
    }

    @Test fun glassIsDenseOnlyWhereContentStands() {
        val dense = JarvisWorkWindowDepth.glassVacancy(panelHeightDp = 470f, contentBottomDp = 262f)
        assertNotNull("content ends 208 dp above the bottom: the glass below clears", dense)
        assertEquals(262f + JarvisWorkWindowDepth.GLASS_CONTENT_MARGIN_DP, dense!!.denseBottomDp, 1e-4f)
        assertEquals(1f, dense.density(dense.denseBottomDp - 1f), 0f)
        assertEquals("text never stands on clear glass", 1f, dense.density(262f), 0f)
        assertEquals(JarvisWorkWindowDepth.GLASS_VACANT_DENSITY, dense.density(469f), 1e-4f)
        var last = 2f
        var y = 0f
        while (y <= 470f) {
            val d = dense.density(y)
            assertTrue("density falls monotonically, never below the smoke floor", d <= last + 1e-5f &&
                d >= JarvisWorkWindowDepth.GLASS_VACANT_DENSITY - 1e-5f)
            last = d; y += 2f
        }
        assertNull("full content: one dense sheet as before", JarvisWorkWindowDepth.glassVacancy(470f, 455f))
        assertNull("scrolling content", JarvisWorkWindowDepth.glassVacancy(470f, 900f))
        for (bad in listOf(Float.NaN, -1f, Float.POSITIVE_INFINITY)) {
            assertNull(JarvisWorkWindowDepth.glassVacancy(bad, 100f))
            assertNull(JarvisWorkWindowDepth.glassVacancy(470f, bad))
        }
    }
}
