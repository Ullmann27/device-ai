package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geometric symmetry must survive a Fold reflow and the owner's larger display zoom. */
class ContinuumCoreMirrorTest {
    private val pairs = listOf(
        Slot.LABEL_CONNECTED to Slot.LABEL_THINKING,
        Slot.LABEL_LEARNING to Slot.LABEL_ANALYSING,
        Slot.LABEL_DEVELOPING to Slot.LABEL_EVOLVING
    )

    /** Original symmetry contract stays MANDATORY for phone and the old landscape geometry. */
    @Test fun allSixControlsHaveEqualAndReflectedBounds() {
        val layouts = listOf(
            ContinuumHomeLayout.phone(360f),
            ContinuumHomeLayout.phone(411f),
            ContinuumHomeLayout.phone(412f),
            ContinuumHomeLayout.wide(840f, 649f),
            ContinuumHomeLayout.wide(832f, 708f),
            ContinuumHomeLayout.wide(840f, 840f)
        )
        layouts.forEach { layout ->
            val hero = layout.boxes.getValue(Slot.HERO)
            val centerX = (hero.left + hero.right) / 2f
            pairs.forEach { (leftSlot, rightSlot) ->
                val left = layout.boxes.getValue(leftSlot)
                val right = layout.boxes.getValue(rightSlot)
                assertEquals("$leftSlot / $rightSlot width", left.width, right.width, .002f)
                assertEquals("$leftSlot / $rightSlot top", left.top, right.top, .002f)
                assertEquals("$leftSlot / $rightSlot bottom", left.bottom, right.bottom, .002f)
                assertEquals("$leftSlot / $rightSlot outer reflection",
                    2f * centerX, left.left + right.right, .002f)
                assertEquals("$leftSlot / $rightSlot inner reflection",
                    2f * centerX, left.right + right.left, .002f)
                assertTrue(left.left >= 0f && right.right <= layout.width)
                assertTrue(left.right < centerX && right.left > centerX)
            }
        }
    }

    @Test fun foldOrbDoesNotObscureMirroredLabelsWhenHeroGrows() {
        val fold = ContinuumHologramRenderer.Geometry.FOLD
        val layouts = listOf(
            ContinuumHomeLayout.wide(840f, 649f),
            ContinuumHomeLayout.wide(832f, 708f),
            ContinuumHomeLayout.wide(840f, 840f),
            ContinuumHomeLayout.wide(920f, 730f)
        )
        layouts.forEach { layout ->
            val hero = layout.boxes.getValue(Slot.HERO)
            val centerX = (hero.left + hero.right) / 2f
            val sphereRadius = minOf(
                hero.width * fold.radiusOfWidth,
                hero.height * fold.radiusOfHeight
            ) * fold.sphere
            val clearance = 8f * layout.scale
            pairs.forEach { (leftSlot, rightSlot) ->
                val left = layout.boxes.getValue(leftSlot)
                val right = layout.boxes.getValue(rightSlot)
                assertTrue("$leftSlot sphere collision", left.right + clearance <= centerX - sphereRadius + .01f)
                assertTrue("$rightSlot sphere collision", centerX + sphereRadius + clearance <= right.left + .01f)
            }
        }
    }

    /**
     * The owner corrected the Fold portrait reference after device inspection:
     * both action columns must now have exactly equal width and height, with
     * the large animated orb visible between the columns. Keep actual geometry
     * and chart alignment under test rather than relying on stale screenshot values.
     */
    @Test fun portraitFoldButtonsFollowMeasuredReferenceInsteadOfLegacyMirror() {
        for ((w,h) in listOf(656f to 690f, 750f to 790f, 840f to 1010f)) {
            val layout=ContinuumHomeLayout.wide(w,h)
            val u=w/840f
            val hero=layout.boxes.getValue(Slot.HERO)
            val left=layout.boxes.getValue(Slot.LABEL_CONNECTED)
            val right=layout.boxes.getValue(Slot.LABEL_THINKING)
            assertEquals(216f*u,left.width,.002f)
            assertEquals(216f*u,right.width,.002f)
            assertEquals(82f*u,left.height,.002f)
            assertEquals(left.width,right.width,.002f)
            assertEquals(w-left.left,right.right,.002f)
            assertEquals(left.top,right.top,.002f)
            assertEquals(left.bottom,right.bottom,.002f)
            assertTrue("both buttons outside the orb center",left.right<hero.left+hero.width/2f &&
                right.left>hero.left+hero.width/2f)
            assertTrue("new owner layout keeps an unobstructed orb gap",right.left-left.right>=370f*u)
            assertTrue("left control does not overlap hero stage",left.right<hero.left)
            assertTrue("right control does not overlap hero stage",right.left>hero.right)
            assertTrue("full-width card grid must not use landscape rail",layout.boxes[Slot.RAIL]==null)
            val upper=listOf(Slot.MODEL,Slot.LEARNING,Slot.STORAGE).map{layout.boxes.getValue(it)}
            val lower=listOf(Slot.DEVELOPMENT,Slot.SYSTEM_ACTIVITY,Slot.WORKERS).map{layout.boxes.getValue(it)}
            for(i in 0..2){
                assertEquals(upper[i].left,lower[i].left,.002f)
                assertEquals(upper[i].width,lower[i].width,.002f)
                assertEquals(upper[i].height,lower[i].height,.002f)
            }
        }
    }

    @Test fun onlyRightHandCardsReverseElementOrderNotLetters() {
        for (layout in listOf(ContinuumHomeLayout.phone(412f), ContinuumHomeLayout.wide(750f, 790f))) {
            val elements = ContinuumHomeScreen.elements(layout, 1f)
            pairs.forEach { (left, right) ->
                val l = elements.getValue(left) as CoreLabelElement
                val r = elements.getValue(right) as CoreLabelElement
                assertFalse(l.mirrored)
                assertTrue(r.mirrored)
                assertTrue(l.title.isNotBlank())
                assertTrue(r.title.isNotBlank())
            }
        }
    }
}
