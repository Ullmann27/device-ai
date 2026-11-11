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

    @Test fun theEnergyBandLeavesTheOrbOnTheSideOfTheWindow() {
        val orbAbove = HollywoodFuiCinematics.Box(500f, 120f, 650f, 250f)
        val below = HollywoodFuiCinematics.energyBridge(orbAbove, window)
        assertNotNull(below)
        assertEquals("leaves the pedestal", orbAbove.bottom, below!!.y0, 0f)
        assertEquals("enters the top rim", window.top, below.y1, 0f)
        assertEquals(orbAbove.centerX, below.x0, 0f)
        assertTrue("inside the rim, clear of the rounded corners", below.x1 in window.left + 12f..window.right - 12f)

        val beside = HollywoodFuiCinematics.energyBridge(HollywoodFuiCinematics.Box(720f, 300f, 860f, 430f), window)
        assertNotNull(beside)
        assertEquals(window.right, beside!!.x1, 0f)
        assertEquals(720f, beside.x0, 0f)

        assertNull("an orb the window overlaps has no honest path",
            HollywoodFuiCinematics.energyBridge(HollywoodFuiCinematics.Box(300f, 300f, 420f, 420f), window))
        assertNull("no room between orb and window",
            HollywoodFuiCinematics.energyBridge(HollywoodFuiCinematics.Box(500f, 120f, 650f, 278f), window))
        assertNull(HollywoodFuiCinematics.energyBridge(HollywoodFuiCinematics.Box(0f, 0f, 0f, 0f), window))
        assertNull(HollywoodFuiCinematics.energyBridge(HollywoodFuiCinematics.Box(Float.NaN, 0f, 10f, 10f), window))
    }

    @Test fun theBandFlowsOnlyWithEnergyBudgetAndStaysBetweenItsEnds() {
        val idle = Calls()
        JarvisFluidLight.drawBridge(painter(idle), 575f, 250f, 575f, 280f, 3.2f, 0f, -1f, high)
        assertEquals("no work, no band", 0, idle.strokes + idle.fills)
        val hot = Calls()
        JarvisFluidLight.drawBridge(painter(hot), 575f, 250f, 575f, 280f, 3.2f, 1f, -1f, suspended)
        assertEquals("a suspended or hot budget draws nothing", 0, hot.strokes + hot.fills)
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY)) {
            val refused = Calls()
            JarvisFluidLight.drawBridge(painter(refused), bad, 250f, 575f, 280f, 3.2f, 1f, -1f, high)
            JarvisFluidLight.drawBridge(painter(refused), 575f, 250f, 575f, 280f, bad, 1f, -1f, high)
            JarvisFluidLight.drawBridge(painter(refused), 575f, 250f, 575f, 280f, 3.2f, bad, -1f, high)
            assertEquals(0, refused.strokes + refused.fills)
        }
        val live = Calls()
        JarvisFluidLight.drawBridge(painter(live), 575f, 250f, 600f, 330f, 3.2f, 1f, -1f, high)
        assertTrue("ribbons are drawn", live.strokes >= 2)
        assertEquals("additive light is switched off again", live.additiveOn, live.additiveOff)
        assertTrue("the band stays between the orb and the rim: y ${live.minY}..${live.maxY}",
            live.minY >= 250f - .5f && live.maxY <= 330f + .5f)
        assertTrue("…and near its line: x ${live.minX}..${live.maxX}", live.minX >= 575f - 22f && live.maxX <= 600f + 22f)
        val pulse = Calls()
        JarvisFluidLight.drawBridge(painter(pulse), 575f, 250f, 600f, 330f, 3.2f, 1f, .5f, high)
        assertTrue("a verified step sends one packet along the band", pulse.fills > live.fills)
    }

    @Test fun theBandMovesContinuouslyWithoutAPhaseJump() {
        fun extent(seconds: Float): Float {
            val calls = Calls()
            JarvisFluidLight.drawBridge(painter(calls), 100f, 100f, 100f, 220f, seconds, 1f, -1f, high)
            return calls.maxX - calls.minX
        }
        var last = extent(0f)
        var t = 1f / 60f
        var moved = false
        while (t < 12f) {
            val now = extent(t)
            assertTrue("no jump at $t s: $last → $now", kotlin.math.abs(now - last) < 3.5f)
            if (kotlin.math.abs(now - last) > .01f) moved = true
            last = now; t += 1f / 60f
        }
        assertTrue("the band is alive", moved)
        assertFalse(extent(5f).isNaN())
    }
}
