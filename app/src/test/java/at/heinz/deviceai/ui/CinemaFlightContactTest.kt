package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Flight contact of the work-window construct (Code54 cinema): pure rule and painter geometry, HOST/JVM.
 * The footprint exists only while the construct is airborne and is derived from the construct frame – no clock.
 */
class CinemaFlightContactTest {
    private data class Call(val name: String, val left: Float, val top: Float, val right: Float, val bottom: Float,
                            val alpha: Float)

    private fun recorder(calls: MutableList<Call>): ContinuumPainter = Proxy.newProxyInstance(
        ContinuumPainter::class.java.classLoader, arrayOf(ContinuumPainter::class.java)) { _, method, args ->
        when (method.name) {
            "fillRoundRect", "strokeRoundRect", "drawLine", "fillRect" -> {
                val floats = args.filterIsInstance<Float>()
                calls += Call(method.name, floats[0], floats[1], floats[2], floats[3], when (method.name) {
                    "fillRect" -> floats[4]
                    "strokeRoundRect" -> floats[6]
                    else -> floats[5]
                })
            }
            "measureText", "capHeight" -> return@newProxyInstance 0f
            "drawRaster" -> return@newProxyInstance false
        }
        null
    } as ContinuumPainter

    private fun contact(t: Long) = HollywoodFuiCinematics.flightContact(HollywoodFuiConstruct.frame(t),
        HollywoodFuiConstruct.flight(t))

    @Test fun contactExistsOnlyWhileTheConstructIsAirborne() {
        assertEquals("nothing before lift-off", 0f, contact(0L), 0f)
        assertEquals("nothing while only the anchor stands", 0f, contact(HollywoodFuiConstruct.ANCHOR_END_MS), 0f)
        assertTrue("carried in mid flight", contact(400L) > .6f)
        assertEquals("gone at touch-down", 0f, contact(HollywoodFuiConstruct.GLASS_END_MS), 1e-4f)
        assertEquals(0f, contact(HollywoodFuiConstruct.DATA_END_MS), 0f)
        var last = 0f
        var peak = 0f
        for (t in 0L..HollywoodFuiConstruct.DATA_END_MS step 16) {
            val c = contact(t)
            assertTrue("bounded ($t ms)", c in 0f..1f)
            assertTrue("no jump ($t ms): ${c - last}", kotlin.math.abs(c - last) < .25f)
            last = c
            peak = maxOf(peak, c)
        }
        assertTrue("clearly present during the flight ($peak)", peak > .7f)
    }

    @Test fun reducedMotionNeverFliesAndBadInputIsSilent() {
        val reduced = HollywoodFuiConstruct.frame(300L, reducedMotion = true)
        assertEquals("reduced motion stands at once", 0f, HollywoodFuiCinematics.flightContact(reduced, 1f), 0f)
        val flying = HollywoodFuiConstruct.frame(400L)
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY))
            assertEquals(0f, HollywoodFuiCinematics.flightContact(flying, bad), 0f)
        assertEquals(0f, HollywoodFuiCinematics.flightContact(flying, 1f), 0f)
    }

    @Test fun contactReturnsWhileTheWindowFoldsAwayAndEndsWithTheFrame() {
        val settled = HollywoodFuiConstruct.DATA_END_MS
        var peak = 0f
        for (c in 0L..HollywoodFuiConstruct.CLOSE_END_MS step 16) {
            val frame = HollywoodFuiConstruct.frame(settled, c)
            peak = maxOf(peak, HollywoodFuiCinematics.flightContact(frame, frame.closeFrame))
        }
        assertTrue("the folding glass is carried back ($peak)", peak > .1f)
        val closed = HollywoodFuiConstruct.frame(settled, HollywoodFuiConstruct.CLOSE_END_MS)
        assertEquals(0f, HollywoodFuiCinematics.flightContact(closed, closed.closeFrame), 0f)
    }

    @Test fun painterMarksTheFootprintAndItsLightAndNothingWhenLanded() {
        val painterly = HollywoodFuiCinematics.Painterly(JarvisDesign.Colors.cyan)
        val ground = HollywoodFuiCinematics.Box(100f, 200f, 300f, 360f)
        val none = mutableListOf<Call>()
        painterly.drawFlightContact(recorder(none), ground, 0f, 14f)
        painterly.drawFlightContact(recorder(none), ground, Float.NaN, 14f)
        painterly.drawFlightContact(recorder(none), HollywoodFuiCinematics.Box(10f, 10f, 11f, 11f), 1f, 14f)
        assertTrue("landed, invalid or degenerate: no draw call", none.isEmpty())

        val calls = mutableListOf<Call>()
        painterly.drawFlightContact(recorder(calls), ground, .8f, 14f)
        val umbra = calls.single { it.name == "fillRoundRect" }
        assertEquals(100f, umbra.left, 0f)
        assertEquals(300f, umbra.right, 0f)
        assertEquals("the umbra lies just below the footprint", 205f, umbra.top, 0f)
        assertTrue("the deck stays readable under it", umbra.alpha > .1f && umbra.alpha < .4f)
        assertEquals("two penumbra rings", 2, calls.count { it.name == "strokeRoundRect" })
        val lines = calls.filter { it.name == "drawLine" }
        assertEquals("soft and fine deck light", 2, lines.size)
        for (line in lines) {
            assertEquals("light lies on the deck under the lower edge", 365f, line.top, 0f)
            assertEquals(365f, line.bottom, 0f)
            assertTrue("inside the footprint", line.left >= 100f && line.right <= 300f && line.left < line.right)
        }
        val half = mutableListOf<Call>()
        painterly.drawFlightContact(recorder(half), ground, .4f, 14f)
        assertEquals("strength scales every element", umbra.alpha * .5f,
            half.single { it.name == "fillRoundRect" }.alpha, 1e-4f)
    }

    @Test fun settledContactShadowIsUnchangedAndFollowsTheGlassWhileItStillFlies() {
        val painterly = HollywoodFuiCinematics.Painterly(JarvisDesign.Colors.cyan)
        val viewport = HollywoodFuiCinematics.Box(0f, 0f, 884f, 760f)
        val window = HollywoodFuiCinematics.Box(40f, 280f, 680f, 750f)
        val legacy = mutableListOf<Call>()
        painterly.drawFocusDepth(recorder(legacy), viewport, window, 1f, 14f)
        val same = mutableListOf<Call>()
        painterly.drawFocusDepth(recorder(same), viewport, window, 1f, 14f, window)
        assertEquals("a landed window draws exactly the previous focus depth", legacy, same)
        val rings0 = legacy.filter { it.name == "strokeRoundRect" }
        assertEquals(3, rings0.size)
        for (ring in rings0) {
            assertEquals(40f, ring.left, 0f)
            assertEquals(680f, ring.right, 0f)
        }
        val ground = HollywoodFuiCinematics.Box(60f, 320f, 600f, 740f)
        val flying = mutableListOf<Call>()
        painterly.drawFocusDepth(recorder(flying), viewport, window, .6f, 14f, ground)
        val rings = flying.filter { it.name == "strokeRoundRect" }
        assertEquals(3, rings.size)
        for (ring in rings) {
            assertEquals("the contact shadow stands under the glass where it is", 60f, ring.left, 0f)
            assertEquals(600f, ring.right, 0f)
        }
        val dim = flying.single { it.name == "fillRect" }
        assertEquals("the deck dim stays keyed to the destination", 280f, dim.top, 0f)
    }
}
