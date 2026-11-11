package at.heinz.deviceai.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import kotlin.math.abs

/**
 * Cinema 2.0 connections (be33c2c97) on the real overlay/window pair, driven only by the window's own frames.
 * HOST evidence: Robolectric native canvas on the JVM – no device, no GPU, no Fold7.
 *
 * Geometry (mdpi, 1 px = 1 dp): avatar face left, orb below it, work window right; face and orb are protected
 * exactly as [JarvisWorkWindowHost.plan] protects them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HollywoodFuiConnectionsUiTest {
    private class Fixture(workPulse: Boolean = true) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val listeners = linkedSetOf<(Long, Float) -> Boolean>()
        val overlay = HollywoodFuiStageOverlay(activity)
        val window = JarvisWorkWindow(activity, { listener ->
            listeners.add(listener)
            AutoCloseable { listeners.remove(listener) }
        }, {})
        val face = RectF(60f, 100f, 260f, 400f)
        val orb = RectF(40f, 460f, 100f, 500f)
        val origin = RectF(300f, 600f, 360f, 640f)
        val target = RectF(420f, 120f, 840f, 660f)
        val protected = listOf(face, orb)
        val body = TextView(activity).apply { text = "Echte Daten bleiben nativ"; textSize = 20f }
        var closed = 0
        init {
            root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
            root.addView(window, FrameLayout.LayoutParams(-1, -1))
            activity.setContentView(root)
            window.onCinematicFrame = overlay::present
            assertTrue(window.open(body, origin, target, "Dokument", protected, onClosed = {
                closed++
                root.removeView(overlay)
            }))
            overlay.open(target, JarvisActionScenes.forDocument(), origin, protected)
            overlay.workPulseEnabled = workPulse
            layout()
        }
        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, W, H)
        }
        fun frame(now: Long, scale: Float = 1f): Boolean {
            val live = listeners.toList().map { it(now, scale) }.any { it }
            shadowOf(Looper.getMainLooper()).idle()
            layout()
            return live
        }
        fun pixels(): IntArray {
            val image = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            return try {
                overlay.draw(Canvas(image))
                IntArray(W * H).also { image.getPixels(it, 0, W, 0, 0, W, H) }
            } finally { image.recycle() }
        }
        fun dispose() { window.dispose(); controller.pause().stop().destroy() }
    }

    private fun fixture(workPulse: Boolean = true, block: (Fixture) -> Unit) {
        val f = Fixture(workPulse)
        try { block(f) } finally { f.dispose() }
    }

    private fun count(pixels: IntArray, xs: IntRange, ys: IntRange): Int {
        var visible = 0
        for (y in ys) for (x in xs) if (Color.alpha(pixels[y * W + x]) > 10) visible++
        return visible
    }

    /** The thread runs at the face's eye height (100 + .42·300 = 226) from x 260 to the window rim at 420. */
    private fun threadPixels(pixels: IntArray) = count(pixels, 270..400, 218..234)

    private fun assertFaceClear(pixels: IntArray, f: Fixture) {
        for (y in f.face.top.toInt() until f.face.bottom.toInt() step 3)
            for (x in f.face.left.toInt() until f.face.right.toInt() step 3)
                assertEquals("protected face pixel ($x,$y) stays clear", Color.TRANSPARENT, pixels[y * W + x])
    }

    @Test fun readingThreadOnlyWhileRealWorkRunsAndNeverInsideTheFace() = fixture { f ->
        f.overlay.setAttentionSource(f.face)
        f.frame(1_000L)
        f.frame(6_000L)
        assertEquals("no work, no thread", 0, threadPixels(f.pixels()))
        f.overlay.setWorkActive(true)
        assertTrue(f.frame(6_000L))
        val working = f.pixels()
        assertTrue("work runs: the avatar's reading thread is drawn (${threadPixels(working)})",
            threadPixels(working) > 60)
        assertFaceClear(working, f)
        for (now in 6_016L..6_400L step 48) {
            f.frame(now)
            val p = f.pixels()
            assertTrue("thread stays while work runs ($now)", threadPixels(p) > 60)
            assertFaceClear(p, f)
        }
        f.overlay.setAttentionSource(null)
        f.frame(6_416L)
        assertEquals("no face geometry, no thread", 0, threadPixels(f.pixels()))
        f.overlay.setAttentionSource(f.face)
        f.frame(6_432L)
        assertTrue(threadPixels(f.pixels()) > 60)
        // STOP / result: the host ends the work (JarvisWorkWindowHost.workEnded → setWorkActive(false)).
        f.overlay.setWorkActive(false)
        f.frame(6_448L)
        assertEquals("STOP ends the thread", 0, threadPixels(f.pixels()))
    }

    @Test fun readingThreadHonoursReducedMotionAndBudget() = fixture { f ->
        f.overlay.setAttentionSource(f.face)
        f.overlay.setWorkActive(true)
        f.frame(1_000L)
        f.frame(6_000L, 0f)
        assertEquals("reduced motion: no thread", 0, threadPixels(f.pixels()))
        f.frame(6_016L)
        assertTrue(threadPixels(f.pixels()) > 60)
        f.overlay.setRenderBudget(JarvisRenderQuality.resolve(false, false, 0, false, false))
        f.frame(6_032L)
        assertEquals("suspended budget: no thread", 0, threadPixels(f.pixels()))
    }

    @Test fun readingThreadSwitchRemovesOnlyTheThreadAndRestoresItLive() = fixture { f ->
        f.overlay.setAttentionSource(f.face)
        f.overlay.setEnergySource(f.orb)
        f.overlay.setWorkActive(true)
        f.frame(1_000L)
        f.frame(6_000L)
        assertTrue(threadPixels(f.pixels()) > 60)
        val band = count(f.pixels(), 105..350, 450..560)
        assertTrue("orb band runs", band > 100)
        f.overlay.attentionLinkEnabled = false
        val off = f.pixels()
        assertEquals("EXP_06 OFF: no reading thread", 0, threadPixels(off))
        assertEquals("the orb band is untouched", band, count(off, 105..350, 450..560))
        assertFaceClear(off, f)
        f.overlay.attentionLinkEnabled = true
        assertTrue("live ON restores the thread", threadPixels(f.pixels()) > 60)
    }

    private fun windowPixels(f: Fixture): IntArray {
        val image = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        return try {
            f.window.draw(Canvas(image))
            IntArray(W * H).also { image.getPixels(it, 0, W, 0, 0, W, H) }
        } finally { image.recycle() }
    }

    /** Accent deck light of the flight contact: bright cyan pixels in a row band left of the destination. */
    private fun deckLight(pixels: IntArray, xs: IntRange, ys: IntRange): Int {
        var lit = 0
        for (y in ys) for (x in xs) {
            val c = pixels[y * W + x]
            if (Color.alpha(c) > 60 && Color.blue(c) > 150 && Color.red(c) < 120) lit++
        }
        return lit
    }

    /**
     * Flight at 400 ms: openness = 4·((400−140)/620)³ ≈ .295, footprint (335,458)–(502,646), so the deck light lies
     * at y ≈ 651 from x ≈ 353 to 484 and the umbra covers the footprint 5 px lower.
     */
    @Test fun flyingConstructMarksItsFootprintOnTheDeckAndLandsClean() = fixture { f ->
        f.frame(1_000L)
        assertEquals("nothing before lift-off", 0, deckLight(windowPixels(f), 356..415, 646..656))
        f.frame(1_400L)
        val flying = windowPixels(f)
        assertTrue("deck light under the flying glass (${deckLight(flying, 356..415, 646..656)})",
            deckLight(flying, 356..415, 646..656) > 40)
        for ((x, y) in listOf(380 to 520, 420 to 560, 460 to 600))
            assertTrue("umbra on the footprint at ($x,$y): ${Color.alpha(flying[y * W + x])}",
                Color.alpha(flying[y * W + x]) > 40)
        for (pixel in listOf(f.face, f.orb).flatMap { r -> listOf(
                (r.top.toInt() + 2) * W + r.left.toInt() + 2, (r.bottom.toInt() - 2) * W + r.right.toInt() - 2) })
            assertEquals("protected bounds stay clear", Color.TRANSPARENT, flying[pixel])
        f.frame(1_760L)
        f.frame(3_000L)
        assertEquals("landed: no flight light is left on the deck", 0,
            deckLight(windowPixels(f), 356..415, 646..656))
    }

    @Test fun reducedMotionHasNoFlightContact() = fixture { f ->
        f.frame(1_000L, 0f)
        f.frame(1_400L, 0f)
        assertEquals(0, deckLight(windowPixels(f), 356..415, 646..656))
    }

    @Test fun closingTheWindowRemovesTheThread() = fixture { f ->
        f.overlay.setAttentionSource(f.face)
        f.overlay.setWorkActive(true)
        f.frame(1_000L)
        f.frame(6_000L)
        assertTrue(threadPixels(f.pixels()) > 60)
        f.window.close()
        f.frame(6_001L)
        assertEquals("closing cannot keep the reading thread", 0, threadPixels(f.pixels()))
        f.frame(6_200L)
        assertEquals(0, threadPixels(f.pixels()))
        f.frame(6_500L)
        assertEquals(1, f.closed)
        assertTrue(f.listeners.isEmpty())
    }

    /** Pixel differs from the baseline by more than rounding. */
    private fun differs(a: Int, b: Int) = abs(Color.alpha(a) - Color.alpha(b)) > 8 ||
        abs(Color.red(a) - Color.red(b)) > 8 || abs(Color.green(a) - Color.green(b)) > 8 ||
        abs(Color.blue(a) - Color.blue(b)) > 8

    private fun nearRim(x: Int, y: Int, box: RectF, d: Float): Boolean {
        val inOuter = x >= box.left - d && x <= box.right + d && y >= box.top - d && y <= box.bottom + d
        val inInner = x > box.left + d && x < box.right - d && y > box.top + d && y < box.bottom - d
        return inOuter && !inInner
    }

    private fun nearBand(x: Int, y: Int, baseline: IntArray, radius: Int): Boolean {
        for (yy in (y - radius).coerceAtLeast(0)..(y + radius).coerceAtMost(H - 1))
            for (xx in (x - radius).coerceAtLeast(0)..(x + radius).coerceAtMost(417))
                if (Color.alpha(baseline[yy * W + xx]) > 10) return true
        return false
    }

    @Test fun disablingOrbLinkRemovesBandAndPacketWithoutDisablingReceiptRim() {
        val withReceipt = Fixture()
        val baseline = Fixture()
        try {
            for (f in listOf(withReceipt, baseline)) {
                f.overlay.setEnergySource(f.orb)
                f.overlay.setWorkActive(true)
                f.frame(1_000L)
                f.frame(6_000L)
                assertTrue(count(f.pixels(), 105..350, 450..560) > 100)
                f.overlay.orbLinkEnabled = false
                assertEquals("Live OFF removes the band", 0,
                    count(f.pixels(), 105..350, 450..560))
            }
            assertTrue("EXP12 remains independent", withReceipt.overlay.verifiedStep("call-no-band"))
            for (f in listOf(withReceipt, baseline)) {
                f.frame(6_016L)
                f.frame(6_206L)
            }
            val actual = withReceipt.pixels()
            val without = baseline.pixels()
            assertEquals("No packet without EXP11", 0, count(actual, 105..350, 450..560))
            var rimChanges = 0
            for (i in actual.indices) {
                if (!differs(actual[i], without[i])) continue
                assertTrue("Only the receipt rim changes",
                    nearRim(i % W, i / W, withReceipt.target, 14f))
                rimChanges++
            }
            assertTrue("Verified rim remains visible", rimChanges > 20)
            withReceipt.overlay.orbLinkEnabled = true
            assertTrue("Live ON restores the band", count(withReceipt.pixels(), 105..350, 450..560) > 100)
        } finally {
            withReceipt.dispose()
            baseline.dispose()
        }
    }

    @Test fun orbLinkSwitchDoesNotHideIndependentReadingAttention() = fixture { f ->
        f.overlay.setAttentionSource(f.face)
        f.overlay.setEnergySource(f.orb)
        f.overlay.setWorkActive(true)
        f.overlay.orbLinkEnabled = false
        f.frame(1_000L)
        f.frame(6_000L)
        assertEquals(0, count(f.pixels(), 105..350, 450..560))
        assertTrue("Reading attention remains", threadPixels(f.pixels()) > 60)
        assertFaceClear(f.pixels(), f)
    }

    @Test fun receiptPacketOnlyAfterAVerifiedStepAndOnlyOnTheBand() {
        val withReceipt = Fixture()
        val baseline = Fixture()
        try {
            for (f in listOf(withReceipt, baseline)) {
                f.overlay.setEnergySource(f.orb)
                f.overlay.setWorkActive(true)
                f.frame(1_000L)
                f.frame(6_000L)
            }
            assertFalse("blank key is not a receipt", withReceipt.overlay.verifiedStep(" "))
            assertTrue("a verified, unique receipt is accepted", withReceipt.overlay.verifiedStep("call-1"))
            assertFalse("the same receipt is never replayed", withReceipt.overlay.verifiedStep("call-1"))
            val notYetPresented = withReceipt.pixels()
            val base0 = baseline.pixels()
            assertEquals("nothing before the window clock presents the receipt", 0,
                notYetPresented.indices.count { differs(notYetPresented[it], base0[it]) })
            for (f in listOf(withReceipt, baseline)) f.frame(6_016L)
            for (f in listOf(withReceipt, baseline)) f.frame(6_206L)
            val packet = withReceipt.pixels()
            val band = baseline.pixels()
            assertTrue("the band is there to carry it", count(band, 105..350, 450..560) > 100)
            var onBand = 0
            for (i in packet.indices) {
                if (!differs(packet[i], band[i])) continue
                val x = i % W
                val y = i / W
                if (nearRim(x, y, withReceipt.target, 14f)) continue // the existing EXP_12 rim pulse
                assertTrue("receipt pixel ($x,$y) leaves the band", x < 418 && nearBand(x, y, band, 12))
                onBand++
            }
            assertTrue("the packet is visible on the band ($onBand px)", onBand > 20)
            for (f in listOf(withReceipt, baseline)) f.frame(6_600L)
            val after = withReceipt.pixels()
            val baseAfter = baseline.pixels()
            assertEquals("one travel only: the packet is gone after 380 ms", 0,
                after.indices.count { differs(after[it], baseAfter[it]) })
            assertFalse("still no replay", withReceipt.overlay.verifiedStep("call-1"))
        } finally {
            withReceipt.dispose()
            baseline.dispose()
        }
    }

    @Test fun noBandNoReducedMotionNoExperimentNoPacket() {
        fun corridor(f: Fixture) = count(f.pixels(), 105..350, 450..560)
        fixture { f ->
            f.overlay.setEnergySource(f.orb)
            f.frame(1_000L)
            f.frame(6_000L)
            assertTrue(f.overlay.verifiedStep("call-idle"))
            f.frame(6_016L)
            f.frame(6_206L)
            assertEquals("without real work there is no band and no packet", 0, corridor(f))
        }
        fixture { f ->
            f.overlay.setEnergySource(f.orb)
            f.overlay.setWorkActive(true)
            f.frame(1_000L)
            f.frame(6_000L)
            assertTrue(f.overlay.verifiedStep("call-reduced"))
            f.frame(6_016L, 0f)
            f.frame(6_206L, 0f)
            assertEquals("reduced motion: neither band nor packet", 0, corridor(f))
        }
        fixture(workPulse = false) { f ->
            f.overlay.setEnergySource(f.orb)
            f.overlay.setWorkActive(true)
            f.frame(1_000L)
            f.frame(6_000L)
            assertFalse("EXP_12 off: no receipt accepted", f.overlay.verifiedStep("call-off"))
        }
    }

    private companion object {
        const val W = 884
        const val H = 760
    }
}
