package at.heinz.deviceai.ui

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner brief 06.11.2026, open item „Dokument tritt hinter den Entwurf zurück (zwei Ebenen)“: behind a document window
 * narrow glass layers stand for the document's REAL further pages (at most three); when the draft unfolds out of the
 * document window, the document plane steps back into the first rear layer and the real page recedes from its place in
 * the document window into the source-card slot of the draft. Pure geometry on the shell's frame clock, no allocation.
 */
class JarvisWorkWindowDepthTest {
    private val density = 2.5f
    private val front = RectF(100f, 300f, 900f, 1100f)

    @Test fun rearLayersFollowTheRealPageCountUpToThree() {
        assertEquals("a single page has nothing behind it", 0, JarvisWorkWindowDepth.layers(1))
        assertEquals(1, JarvisWorkWindowDepth.layers(2))
        assertEquals(2, JarvisWorkWindowDepth.layers(3))
        assertEquals(3, JarvisWorkWindowDepth.layers(4))
        assertEquals("never more than three sheets, whatever the count", 3, JarvisWorkWindowDepth.layers(240))
        assertEquals("unknown or empty documents claim nothing", 0, JarvisWorkWindowDepth.layers(0))
        assertEquals(0, JarvisWorkWindowDepth.layers(-3))
    }

    @Test fun rearLayersStepBackAndUpBehindTheFrontWhileItIsOpen() {
        val first = RectF(); val second = RectF(); val third = RectF()
        assertTrue(JarvisWorkWindowDepth.layer(front, 1, 1f, density, first))
        assertTrue(JarvisWorkWindowDepth.layer(front, 2, 1f, density, second))
        assertTrue(JarvisWorkWindowDepth.layer(front, 3, 1f, density, third))
        // Each sheet is lifted a little above the one in front of it (the visible strip) and narrower (further away).
        assertTrue(first.top < front.top && second.top < first.top && third.top < second.top)
        assertTrue(first.left > front.left && first.right < front.right)
        assertTrue(second.left > first.left && second.right < first.right)
        assertEquals("the lift per sheet is the same", first.top - second.top, second.top - third.top, 1e-3f)
        assertEquals(JarvisWorkWindowDepth.STEP_DP * density, front.top - first.top, 1e-3f)
        assertEquals("the sheets keep the bottom of the front window", front.bottom, first.bottom, 1e-3f)
        // While the window is still folded shut the sheets lie exactly behind it (nothing sticks out of a seed).
        val closed = RectF()
        assertTrue(JarvisWorkWindowDepth.layer(front, 2, 0f, density, closed))
        assertEquals(front, closed)
        // Rear sheets are translucent and fade with distance; never brighter than the front glass.
        val a1 = JarvisWorkWindowDepth.layerAlpha(1, 1f)
        val a2 = JarvisWorkWindowDepth.layerAlpha(2, 1f)
        val a3 = JarvisWorkWindowDepth.layerAlpha(3, 1f)
        assertTrue(a1 > a2 && a2 > a3 && a3 > 0f)
        assertTrue(a1 <= JarvisWorkWindowDepth.LAYER_ALPHA)
        assertEquals("the sheets fade with the window", 0f, JarvisWorkWindowDepth.layerAlpha(1, 0f), 0f)
    }

    @Test fun thePageRecedesFromItsPlaceInTheDocumentToTheSourceSlotOfTheDraft() {
        val from = RectF(140f, 420f, 440f, 820f)
        val to = RectF(130f, 380f, 240f, 525f)
        val out = RectF()
        assertTrue(JarvisWorkWindowDepth.recede(from, to, 0f, out))
        assertEquals(from, out)
        assertTrue(JarvisWorkWindowDepth.recede(from, to, 1f, out))
        assertEquals(to, out)
        assertTrue(JarvisWorkWindowDepth.recede(from, to, .5f, out))
        assertTrue("half-way the page is smaller than in the document", out.width() < from.width() && out.height() < from.height())
        assertTrue("…but still larger than its slot", out.width() > to.width() && out.height() > to.height())
        assertTrue("it moves with an ease-out: more than half the way is done at half the time",
            from.width() - out.width() > (from.width() - to.width()) * .5f)
        var previous = from.width()
        for (step in 1..20) {
            JarvisWorkWindowDepth.recede(from, to, step / 20f, out)
            assertTrue("shrinks monotonically", out.width() <= previous + 1e-3f)
            previous = out.width()
        }
        // The drawn page stays fully visible until near the end and then hands over to the real page view in the body.
        assertEquals(1f, JarvisWorkWindowDepth.recedeAlpha(0f), 0f)
        assertEquals(1f, JarvisWorkWindowDepth.recedeAlpha(.6f), 0f)
        assertTrue(JarvisWorkWindowDepth.recedeAlpha(.9f) in .01f..0.99f)
        assertEquals(0f, JarvisWorkWindowDepth.recedeAlpha(1f), 0f)
        assertFalse(JarvisWorkWindowDepth.handedOver(.95f))
        assertTrue(JarvisWorkWindowDepth.handedOver(1f))
    }

    @Test fun theDocumentPlaneSettlesAsTheFirstRearLayerOfTheDraft() {
        val document = RectF(100f, 300f, 900f, 1100f)
        val destination = RectF(100f, 300f, 900f, 1100f)
        val out = RectF(); val rear = RectF()
        assertTrue(JarvisWorkWindowDepth.plane(document, destination, 0f, density, out))
        assertEquals("at the start the plane is the document window itself", document, out)
        assertTrue(JarvisWorkWindowDepth.plane(document, destination, 1f, density, out))
        JarvisWorkWindowDepth.layer(destination, 1, 1f, density, rear)
        assertEquals("at the end it stands where the first rear sheet of the draft stands", rear, out)
        assertTrue(JarvisWorkWindowDepth.plane(document, destination, .5f, density, out))
        assertTrue(out.top < document.top && out.top > rear.top)
        assertTrue(out.left > document.left && out.left < rear.left)
        // The plane dims as the draft comes forward but never vanishes: the document stays visible behind the result.
        assertEquals(1f, JarvisWorkWindowDepth.planeAlpha(0f), 1e-3f)
        val settled = JarvisWorkWindowDepth.planeAlpha(1f)
        assertEquals(JarvisWorkWindowDepth.layerAlpha(1, 1f), settled, 1e-3f)
        assertTrue(JarvisWorkWindowDepth.planeAlpha(.5f) in settled..1f)
    }

    @Test fun nonFiniteOrEmptyGeometryDrawsNothing() {
        val out = RectF(1f, 2f, 3f, 4f)
        assertFalse(JarvisWorkWindowDepth.layer(RectF(0f, 0f, 0f, 0f), 1, 1f, density, out))
        assertFalse(JarvisWorkWindowDepth.layer(front, 1, Float.NaN, density, out))
        assertFalse(JarvisWorkWindowDepth.layer(front, 1, 1f, 0f, out))
        assertFalse(JarvisWorkWindowDepth.layer(front, 0, 1f, density, out))
        assertFalse(JarvisWorkWindowDepth.recede(RectF(0f, 0f, Float.NaN, 10f), front, .5f, out))
        assertFalse(JarvisWorkWindowDepth.plane(front, RectF(), .5f, density, out))
        assertEquals("a refused frame leaves the target untouched", RectF(1f, 2f, 3f, 4f), out)
        assertEquals(0f, JarvisWorkWindowDepth.layerAlpha(0, 1f), 0f)
        assertEquals(0f, JarvisWorkWindowDepth.layerAlpha(1, Float.NaN), 0f)
    }

    @Test fun aNarrowWindowKeepsItsSheetsInsideTheirOwnWidth() {
        // Outer display, 200 % font: a 200 dp wide window must not end up with crossed or inverted rear sheets.
        val narrow = RectF(0f, 0f, 200f * density, 160f * density)
        val out = RectF()
        assertTrue(JarvisWorkWindowDepth.layer(narrow, 3, 1f, density, out))
        assertTrue(out.width() > narrow.width() * .5f)
        assertTrue(out.left < out.right && out.top < out.bottom)
    }
}
