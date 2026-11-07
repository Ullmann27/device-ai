package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic frames; no device acceptance. */
class JarvisSequenceProtectionTest {
    private val width = 412f
    private val height = 600f

    @Test fun dismissingTheOnlyCardKeepsTheHeadProtected() {
        val fx = JarvisSequenceEffects()
        fx.setProtectedAreas(floatArrayOf(40f, 260f, 380f, 335f), null)
        fx.step("step", "Document analysis", 1000L)
        frame(fx, 2000L)
        assertEquals(0, fx.drawnCardCount)
        fx.dismissAll(2001L)
        frame(fx, 2001L)
        assertEquals("Dismissed cards remain protected", 0, fx.drawnCardCount)
    }

    @Test fun leavingLastCardIsCheckedBeyondTheActiveStack() {
        val fx = JarvisSequenceEffects()
        fx.setProtectedAreas(null, floatArrayOf(40f, 340f, 380f, 390f))
        fx.step("first", "Read", 1000L)
        fx.step("last", "Review", 1000L)
        assertTrue(fx.dismiss("last", 2000L))
        frame(fx, 2001L)
        assertSafeCards(fx, floatArrayOf(40f, 340f, 380f, 390f))
    }

    @Test fun exitingCardCannotSlideIntoProtectedRegion() {
        val fx = JarvisSequenceEffects()
        val head = floatArrayOf(0f, 264f, 20f, 322f)
        fx.setProtectedAreas(head, null)
        fx.step("step", "Document analysis", 1000L)
        frame(fx, 2000L)
        assertEquals("Resting card is clear", 1, fx.drawnCardCount)
        fx.dismiss("step", 2000L)
        frame(fx, 2080L)
        assertSafeCards(fx, head)
    }

    @Test fun cardNeonRimCannotReachAProtectedHead() {
        val fx = JarvisSequenceEffects()
        val head = floatArrayOf(24f, 270f, 30f, 310f)
        fx.setProtectedAreas(head, null)
        fx.step("step", "Document analysis", 1000L)
        val p = frame(fx, 2000L)
        assertFalse("Outer neon rim avoids head", p.outlines.any { overlaps(it, head) })
    }

    @Test fun enteringCardTrailCannotCrossAProtectedRegion() {
        val fx = JarvisSequenceEffects()
        val head = floatArrayOf(400f, 258f, 410f, 265f)
        fx.setProtectedAreas(head, null)
        fx.step("step", "Document analysis", 1000L)
        val p = frame(fx, 1030L)
        assertEquals("Clear card remains drawn", 1, fx.drawnCardCount)
        assertFalse("Entrance trail avoids head", p.lines.any { overlaps(it, head) })
    }

    @Test fun liveCardConnectionCannotCrossAProtectedRegion() {
        val fx = JarvisSequenceEffects()
        val head = floatArrayOf(200f, 220f, 212f, 230f)
        fx.setProtectedAreas(head, null)
        fx.step("step", "Document analysis", 1000L)
        val p = frame(fx, 2000L)
        assertEquals("Clear card remains drawn", 1, fx.drawnCardCount)
        assertFalse("Live connection avoids head", p.lines.any { overlaps(it, head) })
    }

    @Test fun movingPointCannotReachAProtectedRegionBesideItsLine() {
        val fx = JarvisSequenceEffects()
        val head = floatArrayOf(207.8f, 188f, 209f, 190f)
        fx.setProtectedAreas(head, null)
        fx.step("step", "Document analysis", 1000L)
        val p = frame(fx, 2000L)
        assertTrue("Clear live connection remains", p.lines.isNotEmpty())
        assertFalse("Moving point avoids head", p.disks.any { overlaps(it, head) })
    }

    @Test fun flyingCommandChipCannotCoverTheHeadWhenHubIsClear() {
        val fx = JarvisSequenceEffects()
        fx.setProtectedAreas(floatArrayOf(220f, 246f, 235f, 274f), null)
        fx.command("Analyse document", 1000L)
        val p = frame(fx, 1200L)
        assertFalse("Overlapping chip text is suppressed", p.texts.contains("Analyse document"))
    }

    @Test fun commandTrailCannotCrossTheOrbWhenChipAndHubAreClear() {
        val fx = JarvisSequenceEffects()
        val orb = floatArrayOf(230f, 330f, 246f, 345f)
        fx.setProtectedAreas(null, orb)
        fx.command("Analyse document", 1000L)
        val p = frame(fx, 1200L)
        assertFalse("Light trail avoids protected orb", p.lines.any { overlaps(it, orb) })
    }

    @Test fun expandingCommandRingsCannotReachAProtectedHead() {
        val fx = JarvisSequenceEffects()
        val head = floatArrayOf(258f, 175f, 266f, 185f)
        fx.setProtectedAreas(head, null)
        fx.command("Analyse document", 1000L)
        val p = frame(fx, 1700L)
        assertFalse("Shock ring avoids protected head", p.rings.any { overlaps(it, head) })
    }

    @Test fun ringsRemainVisibleAroundAProtectedInterior() {
        val fx = JarvisSequenceEffects()
        fx.setProtectedAreas(floatArrayOf(220f, 178f, 225f, 182f), null)
        fx.command("Analyse document", 1000L)
        assertEquals("Neither ring touches this region", 2, frame(fx, 1700L).rings.size)
    }

    @Test fun diagonalSparksRemainVisibleBesideAProtectedRegion() {
        val fx = JarvisSequenceEffects()
        fx.setProtectedAreas(floatArrayOf(226f, 196f, 227f, 197f), null)
        fx.command("Analyse document", 1000L)
        assertEquals("Every spark passes beside the region", 12, frame(fx, 1700L).lines.size)
    }

    @Test fun unobstructedCommandsAndCardsStillAnimate() {
        val fx = JarvisSequenceEffects()
        fx.setProtectedAreas(floatArrayOf(0f, 0f, 20f, 20f), null)
        fx.command("Analyse document", 1000L)
        assertTrue(frame(fx, 1200L).texts.contains("Analyse document"))
        assertTrue(frame(fx, 1700L).rings.isNotEmpty())
        fx.step("step", "Document analysis", 2000L)
        frame(fx, 3000L)
        assertEquals(1, fx.drawnCardCount)
        fx.dismiss("step", 3000L)
        frame(fx, 3080L)
        assertEquals("Unobstructed exit remains visible", 1, fx.drawnCardCount)
    }

    private fun frame(fx: JarvisSequenceEffects, now: Long): Recording = Recording().also {
        fx.draw(it, width, height, now)
    }

    private fun assertSafeCards(fx: JarvisSequenceEffects, box: FloatArray) {
        for (i in 0 until fx.drawnCardCount) {
            assertFalse("Drawn card $i overlaps protected region", overlaps(fx.drawnCardBox(i), box))
        }
    }

    private fun overlaps(a: FloatArray, b: FloatArray) =
        a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1]

    private class Recording(
        private val delegate: JarvisPresentationRecordingPainter = JarvisPresentationRecordingPainter(),
    ) : ContinuumPainter by delegate {
        val texts get() = delegate.texts
        val lines = mutableListOf<FloatArray>()
        val rings = mutableListOf<FloatArray>()
        val outlines = mutableListOf<FloatArray>()
        val disks = mutableListOf<FloatArray>()

        override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float,
                                     width: Float, fill: Fill, alpha: Float) {
            val pad = width / 2f
            outlines += floatArrayOf(left - pad, top - pad, right + pad, bottom + pad)
        }

        override fun fillCircle(cx: Float, cy: Float, radius: Float, fill: Fill, alpha: Float) {
            disks += floatArrayOf(cx - radius, cy - radius, cx + radius, cy + radius)
        }

        override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, width: Float,
                              fill: Fill, alpha: Float, roundCap: Boolean) {
            val pad = width / 2f
            lines += floatArrayOf(minOf(x0, x1) - pad, minOf(y0, y1) - pad,
                maxOf(x0, x1) + pad, maxOf(y0, y1) + pad)
        }

        override fun strokeCircle(cx: Float, cy: Float, radius: Float, width: Float, fill: Fill, alpha: Float) {
            val r = radius + width / 2f
            rings += floatArrayOf(cx - r, cy - r, cx + r, cy + r)
        }
    }
}
