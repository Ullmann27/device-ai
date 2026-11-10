package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.math.abs

class LuxuryHoloButtonRendererTest {
    private val box = JarvisShellLayout.Box(100f, 40f, 250f, 140f)

    @Test fun lightIsDirectionalBoundedAndDeterministic() {
        val closer = LuxuryHoloButtonRenderer.highlightProximity(box, 175f, 90f)
        val far = LuxuryHoloButtonRenderer.highlightProximity(box, 4_000f, 2_000f)
        assertTrue(closer > far)
        assertTrue(closer in 0.12f..0.72f)
        assertTrue(far in 0.12f..0.72f)
        assertEquals(closer, LuxuryHoloButtonRenderer.highlightProximity(box, 175f, 90f), 0f)
    }

    @Test fun invalidAndEmptyLightInputsCannotChangeTheCockpit() {
        assertEquals(0f, LuxuryHoloButtonRenderer.highlightProximity(box, Float.NaN, 0f), 0f)
        assertEquals(0f, LuxuryHoloButtonRenderer.highlightProximity(box, Float.POSITIVE_INFINITY, 0f), 0f)
        assertEquals(0f, LuxuryHoloButtonRenderer.highlightProximity(
            JarvisShellLayout.Box(0f, 0f, 0f, 50f), 1f, 1f), 0f)
    }
    @Test fun pressureUsesOnlyTheSuppliedClockAndNativeState() {
        for (time in listOf(-1L, 0L, 300L, 900L, Long.MAX_VALUE)) {
            assertEquals(0f, LuxuryHoloButtonRenderer.pressure(false, false, time), 0f)
            assertEquals(0f, LuxuryHoloButtonRenderer.pressure(true, true, time), 0f)
            assertTrue(LuxuryHoloButtonRenderer.pressure(true, false, time) in .7f..1f)
        }
        for (time in 1L..2400L step 16) {
            val a = LuxuryHoloButtonRenderer.pressure(true, false, time)
            assertEquals(a, LuxuryHoloButtonRenderer.pressure(true, false, time + 1200L), .000001f)
            assertTrue(abs(a - LuxuryHoloButtonRenderer.pressure(true, false, time + 16L)) < .014f)
        }
    }

    private data class Edge(val box: JarvisShellLayout.Box, val width: Float,
                            val fill: Fill.Radial, val alpha: Float)
    private class Probe {
        val contacts = mutableListOf<Edge>()
        var additive = false
        val painter = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, args ->
            when (method.name) {
                "setAdditive" -> { additive = args!![0] as Boolean; null }
                "strokeRoundRect" -> {
                    if (args!![6] is Fill.Radial) contacts += Edge(JarvisShellLayout.Box(
                        args[0] as Float, args[1] as Float, args[2] as Float, args[3] as Float),
                        args[5] as Float, args[6] as Fill.Radial, args[7] as Float)
                    null
                }
                "measureText", "capHeight" -> 0f
                "drawRaster" -> false
                else -> null
            }
        } as ContinuumPainter
    }

    @Test fun contactLightFollowsPointerAndStaysOnTwoBoundedRimStrokes() {
        val first = Probe()
        LuxuryHoloButtonRenderer.draw(first.painter, box, 0f, 0f, false, false, true,
            timeMs = 300, pressX = .1f, pressY = .2f)
        val second = Probe()
        LuxuryHoloButtonRenderer.draw(second.painter, box, 0f, 0f, false, false, true,
            timeMs = 900, pressX = .9f, pressY = .8f)
        assertEquals(2, first.contacts.size)
        assertEquals(2, second.contacts.size)
        assertTrue(first.contacts.all { it.width <= 6f && it.alpha in 0f..1f })
        assertTrue(first.contacts.first().fill.cx < second.contacts.first().fill.cx)
        assertTrue(first.contacts.first().fill.cy < second.contacts.first().fill.cy)
        assertTrue(first.contacts.first().alpha > second.contacts.first().alpha)
        assertFalse(first.additive)
        assertFalse(second.additive)
        for ((pressed, disabled) in listOf(false to false, true to true)) {
            val off = Probe()
            LuxuryHoloButtonRenderer.draw(off.painter, box, 0f, 0f, false, disabled, pressed, 300)
            assertTrue(off.contacts.isEmpty())
        }
    }

    @Test fun shellRoutesPressureToOnlyTheActualDockControl() {
        val shell = JarvisShellLayout.create(884f, 760f)
        val pressed = JarvisShellRenderer.ControlPress("bottom:system", "system", .2f, .8f)
        val probe = Probe()
        JarvisShellRenderer.draw(probe.painter, shell, timeMs = 300L, controlPress = pressed)
        assertEquals(2, probe.contacts.size)
        val dock = requireNotNull(shell[JarvisShellLayout.Slot.BOTTOM_NAV])
        val index = JarvisTiles.bottomNav.indexOfFirst { it.target == "system" }
        val cell = JarvisTiles.bottomNavItemBoxes(dock)[index]
        assertTrue(probe.contacts.all { it.box.left > cell.left && it.box.right < cell.right })
        val released = Probe()
        JarvisShellRenderer.draw(released.painter, shell, timeMs = 300L)
        assertTrue(released.contacts.isEmpty())
        val other = Probe()
        JarvisShellRenderer.draw(other.painter, shell, timeMs = 300L,
            controlPress = pressed.copy(key = "header:system"))
        assertTrue(other.contacts.isEmpty())
    }

    @Test fun reducedMotionAndThermalFloorKeepPressureStatic() {
        val low = JarvisRenderQuality.resolve(true, false, 3, false, false)
        val bounds = JarvisShellLayout.Box(0f, 0f, 600f, 120f)
        val before = Probe()
        val after = Probe()
        JarvisTiles.drawBottomNav(before.painter, bounds, timeMs = 300, pressed = 1, cinemaBudget = low)
        JarvisTiles.drawBottomNav(after.painter, bounds, timeMs = 900, pressed = 1, cinemaBudget = low)
        assertEquals(2, before.contacts.size)
        assertEquals(before.contacts.map { it.alpha }, after.contacts.map { it.alpha })
        val reduced = Probe()
        JarvisTiles.drawBottomNav(reduced.painter, bounds, timeMs = 900, pressed = 1, animatorScale = 0f)
        assertEquals(before.contacts.map { it.alpha }, reduced.contacts.map { it.alpha })
    }

    @Test fun heldMaterialClockDoesNotAdvanceFrozenBackground() {
        val shell = JarvisShellLayout.create(884f, 760f)
        val probe = Probe()
        var backgroundTime = -1L
        val glass = object : JarvisShellRenderer.GlassBackend {
            override fun drawBackground(width: Float, height: Float, timeMs: Long): Boolean {
                backgroundTime = timeMs
                return true
            }
            override fun drawPanelGlass(box: JarvisShellLayout.Box, radius: Float, glow: Float) = false
        }
        JarvisShellRenderer.draw(probe.painter, shell, glass = glass, timeMs = 0L,
            materialTimeMs = 300L, controlPress = JarvisShellRenderer.ControlPress("bottom:system", "system"))
        assertEquals(0L, backgroundTime)
        assertEquals(2, probe.contacts.size)
        assertEquals(.35f, probe.contacts.first().alpha, .0001f)
    }

}
