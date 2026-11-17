package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import org.junit.Assert.*
import org.junit.Test

/**
 * The November owner images supersede the old six mirrored controls around the head.
 * Their required balance now belongs to a complete action grid below an unobstructed stage.
 */
class ContinuumCoreMirrorTest {
    private val layouts = listOf(
        ContinuumHomeLayout.phone(360f), ContinuumHomeLayout.phone(412f),
        ContinuumHomeLayout.wide(840f, 649f), ContinuumHomeLayout.wide(832f, 708f),
        ContinuumHomeLayout.wide(656f, 690f), ContinuumHomeLayout.wide(840f, 1050f)
    // 2026-11-04: landscape windows now use the ref1/ref3 cockpit (ContinuumLandscapeCockpitLayoutTest);
    // this mirror composition remains the phone/portrait/large-type layout.
    ).filterNot { it.landscapeCockpit }

    @Test fun allEightControlsKeepEqualBoundsAndBalancedRows() {
        for (layout in layouts) {
            val actions = ContinuumReferenceLayout.actions.map { layout.boxes.getValue(it) }
            val composer = layout.boxes.getValue(Slot.COMPOSER)
            for (box in actions) {
                assertEquals(actions.first().width, box.width, .002f)
                assertEquals(actions.first().height, box.height, .002f)
                assertTrue(box.width >= 48f && box.height >= 48f)
            }
            for ((_, row) in actions.groupBy { it.top }) {
                assertEquals(composer.left, row.first().left, .002f)
                assertEquals(composer.right, row.last().right, .002f)
                for ((a, b) in row.zipWithNext()) assertTrue(a.right < b.left)
            }
        }
    }

    @Test fun avatarAndCoreDoNotUnderlayAnInteractiveActionOrComposer() {
        for (layout in layouts) {
            val hero = layout.boxes.getValue(Slot.HERO)
            for (slot in ContinuumReferenceLayout.actions + Slot.COMPOSER + Slot.LABEL_EVOLVING) {
                val control = layout.boxes.getValue(slot)
                assertTrue("$slot below the complete avatar/core stage", control.top > hero.bottom)
            }
        }
    }

    @Test fun portraitAndLandscapeBothKeepOneHeroAndTheSameDestinations() {
        val destinations = ContinuumReferenceLayout.actions.toSet() + setOf(
            Slot.COMPOSER, Slot.LABEL_EVOLVING, Slot.MODEL, Slot.LEARNING,
            Slot.STORAGE, Slot.DEVELOPMENT, Slot.WORKERS, Slot.SYSTEM_STATUS, Slot.SYSTEM_ACTIVITY)
        for (layout in layouts) {
            assertNotNull(layout[Slot.HERO])
            val elements = ContinuumHomeScreen.elements(layout, 1f)
            assertTrue(elements.keys.containsAll(destinations))
            assertFalse("The native avatar owns the stage", elements.containsKey(Slot.HERO))
        }
    }

    @Test fun actionTextUsesReadableDestinationNamesOnBothSides() {
        val labels = mapOf(Slot.LABEL_THINKING to "Gespräch", Slot.ACTION_MEMORY to "Gedächtnis",
            Slot.LABEL_ANALYSING to "Forschung", Slot.LABEL_DEVELOPING to "Entwicklung",
            Slot.ACTION_AGENTS to "Agenten", Slot.LABEL_LEARNING to "Lernen",
            Slot.ACTION_DEVICE to "Gerät", Slot.LABEL_CONNECTED to "Internet")
        for (layout in layouts) {
            val elements = ContinuumHomeScreen.elements(layout, 1f)
            for ((slot, title) in labels) {
                assertTrue(elements.getValue(slot) is ReferenceActionElement)
                assertTrue(elements.getValue(slot).describe(ContinuumDashboardState()).startsWith("$title."))
            }
        }
    }

    // Security review #481 (4): the landscape cockpit gets the same strength of checks, adapted to its composition.
    private val cockpits = listOf(ContinuumHomeLayout.wide(840f, 649f), ContinuumHomeLayout.wide(832f, 708f),
        ContinuumHomeLayout.wide(1280f, 800f)).onEach { assertTrue(it.landscapeCockpit) }

    @Test fun landscapeCockpitActionBarKeepsEqualBoundsInOneRowUnderTheStage() {
        for (layout in cockpits) {
            val bar = ContinuumLandscapeCockpitLayout.actionBar.map { layout.boxes.getValue(it) }
            val hero = layout.boxes.getValue(Slot.HERO)
            val composer = layout.boxes.getValue(Slot.COMPOSER)
            for (box in bar) {
                assertEquals(bar.first().width, box.width, .002f)
                assertEquals(bar.first().height, box.height, .002f)
                assertEquals(bar.first().top, box.top, .002f)
                assertTrue(box.width >= 48f && box.height >= 48f)
                assertTrue("$box below the avatar stage", box.top > hero.bottom)
                assertTrue("$box below the composer", box.top > composer.bottom)
            }
            for ((a, b) in bar.zipWithNext()) assertTrue(a.right < b.left)
            assertTrue("composer below the avatar stage", composer.top > hero.bottom)
        }
    }

    @Test fun landscapeCockpitKeepsOneHeroAndRealDestinations() {
        for (layout in cockpits) {
            assertNotNull(layout[Slot.HERO])
            val elements = ContinuumHomeScreen.elements(layout, 1f)
            val needed = ContinuumLandscapeCockpitLayout.actionBar + ContinuumLandscapeCockpitLayout.topTabs +
                ContinuumLandscapeCockpitLayout.cards + Slot.COMPOSER
            assertTrue(elements.keys.containsAll(needed))
            assertFalse("The native avatar owns the stage", elements.containsKey(Slot.HERO))
            val hero = layout.boxes.getValue(Slot.HERO)
            for (card in ContinuumLandscapeCockpitLayout.cards) {
                val c = layout.boxes.getValue(card)
                assertTrue("$card does not underlay the avatar", c.right <= hero.left || c.left >= hero.right)
            }
        }
    }
}
