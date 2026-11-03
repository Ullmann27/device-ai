package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Adaptive owner design: equal usable controls below a free stage, without scaling the desktop. */
class ContinuumCoreMirrorTest {
    private val actions = listOf(Slot.LABEL_CONNECTED, Slot.LABEL_THINKING, Slot.LABEL_LEARNING,
        Slot.LABEL_ANALYSING, Slot.LABEL_DEVELOPING, Slot.LABEL_EVOLVING)

    @Test fun allSixControlsHaveEqualUsableBoundsInRowsBelowTheHero() {
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
            val controls = actions.map { layout.boxes.getValue(it) }
            val first = controls.first()
            controls.forEach { box ->
                assertEquals(first.width, box.width, .002f)
                assertEquals(first.height, box.height, .002f)
                assertTrue("touch target must not shrink", box.width >= 48f && box.height >= 48f)
                assertTrue("action stays below free stage", box.top >= hero.bottom + 8f)
                assertTrue(box.left >= 0f && box.right <= layout.width)
                assertTrue(box.bottom <= layout.contentHeight)
            }
            for (i in controls.indices) for (j in i + 1 until controls.size) {
                val a = controls[i]; val b = controls[j]
                assertFalse("action targets intersect", a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom)
            }
            controls.groupBy { it.top }.values.forEach { row ->
                row.sortedBy { it.left }.zipWithNext().forEach { (left, right) ->
                    assertTrue("grid controls have a clear gap", right.left - left.right >= 8f)
                }
            }
        }
    }

    @Test fun foldOrbStaysVisibleInsideFreeStageAndClearOfEveryAction() {
        // The unchanged FOLD renderer geometry is the native Home fallback, not a claim about the optional3D asset.
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
            val centerY = hero.top + hero.height * fold.centerY
            val radius = minOf(hero.width * fold.radiusOfWidth, hero.height * fold.radiusOfHeight) * fold.sphere
            assertTrue("orb has a visible radius", radius > 0f)
            assertTrue("orb left clipping", centerX - radius >= hero.left)
            assertTrue("orb right clipping", centerX + radius <= hero.right)
            assertTrue("orb top clipping", centerY - radius >= hero.top)
            assertTrue("orb bottom clipping", centerY + radius <= hero.bottom)
            actions.forEach { slot ->
                assertTrue("$slot sphere collision", centerY + radius + 8f <= layout.boxes.getValue(slot).top)
            }
        }
    }

    @Test fun portraitFoldCardsReflowFromTwoColumnsForLargerFonts() {
        val cardSlots = listOf(Slot.MODEL, Slot.LEARNING, Slot.STORAGE, Slot.DEVELOPMENT,
            Slot.WORKERS, Slot.SYSTEM_STATUS, Slot.SYSTEM_ACTIVITY)
        for ((width, height) in listOf(656f to 690f, 750f to 790f, 840f to 1010f)) {
            val normal = ContinuumHomeLayout.wide(width, height)
            val large = ContinuumHomeLayout.wide(width, height, 2f)
            assertEquals("portrait must not reserve a landscape rail", null, normal[Slot.RAIL])
            assertEquals(null, large[Slot.RAIL])
            assertEquals(2, cardSlots.map { normal.boxes.getValue(it).left }.distinct().size)
            assertEquals("large type reflows into one column", 1, cardSlots.map { large.boxes.getValue(it).left }.distinct().size)
            assertTrue(large.boxes.getValue(Slot.MODEL).height > normal.boxes.getValue(Slot.MODEL).height)
            for (layout in listOf(normal, large)) {
                val hero = layout.boxes.getValue(Slot.HERO)
                val actionBottom = actions.maxOf { layout.boxes.getValue(it).bottom }
                actions.forEach { assertTrue(layout.boxes.getValue(it).top > hero.bottom) }
                val cards = cardSlots.map { layout.boxes.getValue(it) }
                for (box in cards) {
                    assertEquals(cards.first().width, box.width, .002f)
                    assertEquals(cards.first().height, box.height, .002f)
                    assertTrue(box.left >= 0f && box.right <= width)
                    assertTrue(box.top > actionBottom && box.bottom <= layout.contentHeight)
                }
                for (i in cards.indices) for (j in i + 1 until cards.size) {
                    val a = cards[i]; val b = cards[j]
                    assertFalse("card overlap", a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom)
                }
            }
        }
    }

    @Test fun everyActionTileUsesItsOwnRealStatusAndKeepsReadableLabels() {
        val state = ContinuumDashboardState(core = ContinuumDashboardState.CoreStatus(
            connected = "Verbindung-Zustand", thinking = "Dialog-Zustand", learning = "Lernen-Zustand",
            analysing = "Recherche-Zustand", developing = "Entwicklung-Zustand", evolving = "Lernstatus-Zustand"))
        val expected = mapOf(Slot.LABEL_CONNECTED to "Verbindung: Verbindung-Zustand",
            Slot.LABEL_THINKING to "Chat: Dialog-Zustand", Slot.LABEL_LEARNING to "Lernen: Lernen-Zustand",
            Slot.LABEL_ANALYSING to "Forschung: Recherche-Zustand", Slot.LABEL_DEVELOPING to "Entwicklung: Entwicklung-Zustand",
            Slot.LABEL_EVOLVING to "Lernstatus: Lernstatus-Zustand")
        for (layout in listOf(ContinuumHomeLayout.phone(412f), ContinuumHomeLayout.wide(750f, 790f))) {
            val elements = ContinuumHomeScreen.elements(layout, 1f)
            for (slot in actions) {
                val element = elements.getValue(slot)
                assertTrue("action uses the shared adaptive tile", element is CockpitActionTile)
                assertEquals(expected.getValue(slot), element.describe(state))
                assertTrue(element.describe(state).isNotBlank())
            }
        }
    }
}
