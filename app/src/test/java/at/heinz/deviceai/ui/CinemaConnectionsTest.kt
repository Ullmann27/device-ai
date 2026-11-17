package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.math.hypot

/**
 * Cinema 2.0 connections (be33c2c97), pure rules, HOST/JVM only – no device, no GPU:
 * the verified receipt packet that travels back along the existing orb→window band (EXP_12) and the avatar's
 * reading thread between face and window rim while real work runs. No clock is read here; callers pass the window's
 * own elapsed time.
 */
class CinemaConnectionsTest {
    private val high = JarvisRenderQuality.resolve(true, false, 0, false, false)
    private val window = HollywoodFuiCinematics.Box(420f, 120f, 840f, 660f)

    private class Recorded {
        var additiveOn = 0
        var additiveOff = 0
        /** Every strokeSegments batch, copied at call time (the renderer reuses its arrays). */
        val batches = mutableListOf<FloatArray>()
        /** Centres and radii of every fillOval (fillCircle forwards to it). */
        val ovals = mutableListOf<Triple<Float, Float, Float>>()
        val lines = mutableListOf<FloatArray>()
    }

    private fun painter(r: Recorded): ContinuumPainter = Proxy.newProxyInstance(
        ContinuumPainter::class.java.classLoader, arrayOf(ContinuumPainter::class.java)) { proxy, method, args ->
        when (method.name) {
            "strokeSegments" -> r.batches += (args[0] as FloatArray).copyOf(args[1] as Int)
            "fillOval" -> {
                val l = args[0] as Float; val t = args[1] as Float; val rr = args[2] as Float; val b = args[3] as Float
                r.ovals += Triple((l + rr) / 2f, (t + b) / 2f, (rr - l) / 2f)
            }
            "fillCircle" -> {
                val cx = args[0] as Float; val cy = args[1] as Float; val radius = args[2] as Float
                r.ovals += Triple(cx, cy, radius)
            }
            "drawLine" -> r.lines += floatArrayOf(args[0] as Float, args[1] as Float, args[2] as Float, args[3] as Float)
            "setAdditive" -> if (args[0] as Boolean) r.additiveOn++ else r.additiveOff++
            "measureText", "capHeight" -> return@newProxyInstance 0f
        }
        null
    } as ContinuumPainter

    // ---- receipt travel ------------------------------------------------------------------------------------------

    @Test fun receiptTravelIsBoundedToOneRimPulse() {
        assertEquals(-1f, HollywoodFuiCinematics.receiptTravel(-1L), 0f)
        assertEquals(0f, HollywoodFuiCinematics.receiptTravel(0L), 0f)
        assertEquals(.5f, HollywoodFuiCinematics.receiptTravel(190L), 1e-6f)
        assertEquals(1f, HollywoodFuiCinematics.receiptTravel(HollywoodFuiCinematics.RECEIPT_TRAVEL_MS), 0f)
        assertEquals("gone after one travel", -1f,
            HollywoodFuiCinematics.receiptTravel(HollywoodFuiCinematics.RECEIPT_TRAVEL_MS + 1), 0f)
        assertEquals(-1f, HollywoodFuiCinematics.receiptTravel(Long.MAX_VALUE), 0f)
        assertEquals(-1f, HollywoodFuiCinematics.receiptTravel(Long.MIN_VALUE), 0f)
        var last = -1f
        for (age in 0L..HollywoodFuiCinematics.RECEIPT_TRAVEL_MS) {
            val t = HollywoodFuiCinematics.receiptTravel(age)
            assertTrue("monotone 0..1 ($age ms)", t in 0f..1f && t >= last)
            last = t
        }
        assertEquals("the packet takes exactly as long as the existing rim pulse", 380L,
            HollywoodFuiCinematics.RECEIPT_TRAVEL_MS)
    }

    // ---- receipt packet on the band ------------------------------------------------------------------------------

    private val orb = JarvisShellLayout.Box(40f, 460f, 100f, 500f)
    private val sinkX = 420f
    private val sinkY = 480f
    private val working = JarvisFluidLight.workingSample(5_190L, true, false, high)

    private fun transfer(receipt: Float, sample: JarvisFluidLight.Sample = working,
                         budget: JarvisRenderQuality.Budget? = high): Recorded =
        Recorded().also { JarvisFluidLight.drawTransfer(painter(it), orb, sinkX, sinkY, sample, budget, receipt) }

    /** Centre lane points of all strands – where the band really runs. */
    private fun bandPoints(r: Recorded): List<Pair<Float, Float>> {
        val batch = r.batches.first()
        val points = ArrayList<Pair<Float, Float>>()
        for (i in 0 until batch.size step 4) {
            points += batch[i] to batch[i + 1]
            points += batch[i + 2] to batch[i + 3]
        }
        return points
    }

    private fun distanceToBand(x: Float, y: Float, band: List<Pair<Float, Float>>): Float =
        band.minOf { (bx, by) -> hypot(bx - x, by - y) }

    @Test fun noReceiptDrawsTheUnchangedBandWithoutPacket() {
        val without = transfer(-1f)
        assertTrue("the band itself is drawn", without.batches.isNotEmpty())
        assertTrue("no packet without a verified receipt", without.ovals.isEmpty())
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.01f, 1.01f, 7f)) {
            val r = transfer(bad)
            assertTrue("bad travel $bad draws no packet", r.ovals.isEmpty())
            assertEquals("the band itself is unchanged for $bad", without.batches.size, r.batches.size)
            for (i in without.batches.indices) assertTrue(without.batches[i].contentEquals(r.batches[i]))
        }
        assertEquals(1, without.additiveOn)
        assertEquals(1, without.additiveOff)
    }

    @Test fun receiptPacketRidesOnTheBandFromWindowToOrb() {
        val without = transfer(-1f)
        var lastToSink = -1f
        for (step in 1..19) {
            val travel = step / 20f
            val r = transfer(travel)
            assertEquals("the band is not altered by the packet ($travel)", without.batches.size, r.batches.size)
            for (i in without.batches.indices) assertTrue(without.batches[i].contentEquals(r.batches[i]))
            assertEquals("one packet: halo, core and glint ($travel)", 3, r.ovals.size)
            val (cx, cy, _) = r.ovals.first()
            for ((x, y, _) in r.ovals) {
                assertEquals("concentric", cx, x, 1e-3f)
                assertEquals("concentric", cy, y, 1e-3f)
            }
            assertEquals("the packet centre lies on the band itself ($travel)", 0f,
                distanceToBand(cx, cy, bandPoints(r)), 1e-3f)
            val toSink = hypot(cx - sinkX, cy - sinkY)
            assertTrue("it travels back from the window rim towards the orb ($travel)", toSink >= lastToSink - 1e-3f)
            lastToSink = toSink
            assertEquals(1, r.additiveOn)
            assertEquals(1, r.additiveOff)
        }
        val atRim = transfer(0f).ovals.first()
        assertEquals("travel 0 starts at the window end of the band", sinkX, atRim.first, 12f)
        val atOrb = transfer(1f).ovals.first()
        assertEquals("travel 1 arrives at the orb", orb.bottom, atOrb.second, 1e-3f)
        assertTrue(atOrb.first in orb.left..orb.right)
    }

    @Test fun packetNeedsTheBandAndHonoursTheBudget() {
        assertTrue("no band, no packet",
            transfer(.5f, sample = JarvisFluidLight.OFF).let { it.ovals.isEmpty() && it.batches.isEmpty() })
        for (budget in listOf(JarvisRenderQuality.resolve(false, false, 0, false, false),
            JarvisRenderQuality.resolve(true, false, 3, false, false),
            JarvisRenderQuality.resolve(true, true, 0, false, false))) {
            val r = transfer(.5f, budget = budget)
            assertTrue("suspended/hot/reduced budget draws neither band nor packet", r.ovals.isEmpty() && r.batches.isEmpty())
        }
        val medium = JarvisRenderQuality.resolve(true, false, 1, false, true)
        val r = transfer(.5f, budget = medium)
        assertEquals("fewer strands, still exactly one packet", 3, r.ovals.size)
        assertEquals(0f, distanceToBand(r.ovals[0].first, r.ovals[0].second, bandPoints(r)), 1e-3f)
    }

    // ---- reading thread ------------------------------------------------------------------------------------------

    private fun strictlyInside(x: Float, y: Float, b: HollywoodFuiCinematics.Box) =
        x > b.left && x < b.right && y > b.top && y < b.bottom

    private fun assertClearOfFace(beam: HollywoodFuiCinematics.Beam, face: HollywoodFuiCinematics.Box) {
        for (i in 0..100) {
            val t = i / 100f
            val x = beam.x0 + (beam.x1 - beam.x0) * t
            val y = beam.y0 + (beam.y1 - beam.y0) * t
            assertTrue("thread point $t stays out of the face $beam", !strictlyInside(x, y, face))
        }
    }

    @Test fun threadLeavesTheFaceAtEyeHeightAndMeetsTheRimClearOfCorners() {
        val leftFace = HollywoodFuiCinematics.Box(60f, 100f, 260f, 400f)
        val link = requireNotNull(HollywoodFuiCinematics.attentionLink(leftFace, window))
        assertEquals(leftFace.right, link.x0, 0f)
        assertEquals("eye height", leftFace.top + leftFace.height * .42f, link.y0, 1e-4f)
        assertEquals(window.left, link.x1, 0f)
        assertTrue(link.y1 in window.top + 18f..window.bottom - 18f)
        assertClearOfFace(link, leftFace)

        val rightFace = HollywoodFuiCinematics.Box(860f, 300f, 1000f, 500f)
        val fromRight = requireNotNull(HollywoodFuiCinematics.attentionLink(rightFace, window))
        assertEquals(rightFace.left, fromRight.x0, 0f)
        assertEquals(window.right, fromRight.x1, 0f)
        assertClearOfFace(fromRight, rightFace)

        val aboveFace = HollywoodFuiCinematics.Box(600f, 0f, 700f, 100f)
        val fromAbove = requireNotNull(HollywoodFuiCinematics.attentionLink(aboveFace, window))
        assertEquals(aboveFace.bottom, fromAbove.y0, 0f)
        assertEquals(window.top, fromAbove.y1, 0f)
        assertTrue(fromAbove.x1 in window.left + 18f..window.right - 18f)
        assertClearOfFace(fromAbove, aboveFace)

        val belowFace = HollywoodFuiCinematics.Box(820f, 680f, 900f, 760f)
        val fromBelow = requireNotNull(HollywoodFuiCinematics.attentionLink(belowFace, window))
        assertEquals(belowFace.top, fromBelow.y0, 0f)
        assertEquals(window.bottom, fromBelow.y1, 0f)
        assertEquals("clear of the rounded corner", window.right - 18f, fromBelow.x1, 0f)
        assertClearOfFace(fromBelow, belowFace)

        val highFace = HollywoodFuiCinematics.Box(60f, -400f, 260f, -100f)
        val diagonal = requireNotNull(HollywoodFuiCinematics.attentionLink(highFace, window))
        assertEquals("eye far above the window: the thread meets the rim below the corner", window.top + 18f,
            diagonal.y1, 0f)
    }

    @Test fun noHonestGapNoThread() {
        val overlapping = HollywoodFuiCinematics.Box(400f, 100f, 600f, 400f)
        assertNull(HollywoodFuiCinematics.attentionLink(overlapping, window))
        val touching = HollywoodFuiCinematics.Box(214f, 100f, 415f, 400f)
        assertNull("5 dp gap is not enough", HollywoodFuiCinematics.attentionLink(touching, window))
        assertNotNull("6 dp gap is", HollywoodFuiCinematics.attentionLink(
            HollywoodFuiCinematics.Box(214f, 100f, 414f, 400f), window))
        val good = HollywoodFuiCinematics.Box(60f, 100f, 260f, 400f)
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(HollywoodFuiCinematics.attentionLink(good.copy(left = bad), window))
            assertNull(HollywoodFuiCinematics.attentionLink(good, window.copy(bottom = bad)))
        }
        assertNull("empty face", HollywoodFuiCinematics.attentionLink(good.copy(right = good.left), window))
        assertNull("window too small for its corners", HollywoodFuiCinematics.attentionLink(good,
            HollywoodFuiCinematics.Box(420f, 120f, 455f, 660f)))
    }

    @Test fun drawnThreadStaysOnItsLineAndMovesContinuouslyOnTheGivenClock() {
        val face = HollywoodFuiCinematics.Box(60f, 100f, 260f, 400f)
        val link = requireNotNull(HollywoodFuiCinematics.attentionLink(face, window))
        val cinematics = HollywoodFuiCinematics.Painterly(JarvisDesign.Colors.cyan)
        for (bad in listOf(0f, .005f, -1f, Float.NaN)) {
            val r = Recorded()
            cinematics.drawAttentionLink(painter(r), link, 1f, bad, bloom = true)
            assertTrue("strength $bad draws nothing", r.ovals.isEmpty() && r.lines.isEmpty() && r.additiveOn == 0)
        }
        Recorded().also {
            cinematics.drawAttentionLink(painter(it), link, Float.NaN, 1f, bloom = true)
            assertTrue("no clock, no thread", it.ovals.isEmpty() && it.lines.isEmpty())
        }
        Recorded().also {
            cinematics.drawAttentionLink(painter(it), HollywoodFuiCinematics.Beam(10f, 10f, 12f, 12f), 1f, 1f, true)
            assertTrue("degenerate thread is skipped", it.ovals.isEmpty() && it.lines.isEmpty())
        }
        var previous: List<Triple<Float, Float, Float>>? = null
        var seconds = 0f
        while (seconds <= 4f) {
            val r = Recorded()
            cinematics.drawAttentionLink(painter(r), link, seconds, 1f, bloom = true)
            assertEquals(1, r.additiveOn)
            assertEquals(1, r.additiveOff)
            assertTrue("thread line follows the beam", r.lines.all {
                it[0] == link.x0 && it[1] == link.y0 && it[2] == link.x1 && it[3] == link.y1 })
            for ((x, y, _) in r.ovals) {
                assertEquals("sparks ride on the thread", link.y0, y, 1e-3f)
                assertTrue(x in link.x0 - 1e-3f..link.x1 + 1e-3f)
                assertTrue("never inside the face", !strictlyInside(x, y, face))
            }
            previous?.let { before ->
                // 16 ms at .55 thread lengths per second: a spark moves < 2 % of the thread per frame.
                for (k in 0 until 2) {
                    val a = before[2 * k + 1]; val b = r.ovals[2 * k + 1]
                    val wrapped = b.first < a.first
                    if (!wrapped) assertTrue("continuous spark at $seconds s",
                        b.first - a.first <= .02f * (link.x1 - link.x0) + 1e-3f)
                }
            }
            previous = r.ovals.toList()
            seconds += .016f
        }
        Recorded().also {
            cinematics.drawAttentionLink(painter(it), link, 1f, 1f, bloom = false)
            assertEquals("no bloom: no additive layer", 0, it.additiveOn)
            assertEquals(1, it.lines.size)
        }
    }
}
