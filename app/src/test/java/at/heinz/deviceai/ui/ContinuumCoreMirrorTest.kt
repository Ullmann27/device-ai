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

    @Test fun allSixControlsHaveEqualAndReflectedBounds() {
        val layouts = listOf(
            ContinuumHomeLayout.phone(360f),
            ContinuumHomeLayout.phone(411f),
            ContinuumHomeLayout.phone(412f),
            ContinuumHomeLayout.wide(656f, 690f),
            ContinuumHomeLayout.wide(750f, 790f),
            ContinuumHomeLayout.wide(840f, 649f)
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
