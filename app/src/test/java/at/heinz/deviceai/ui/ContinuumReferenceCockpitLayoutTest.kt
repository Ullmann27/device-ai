package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner's November reference: distinct avatar/brain stage, direct actions and
 * readable content at real window sizes. No coordinates from the superseded
 * dashboard are used as an oracle. */
class ContinuumReferenceCockpitLayoutTest {
    private data class Window(val width: Float, val height: Float)

    private val windows = listOf(
        Window(280f, 720f), Window(320f, 720f), Window(360f, 800f),
        Window(412f, 915f), Window(656f, 690f), Window(750f, 790f),
        Window(840f, 649f), Window(840f, 1050f), Window(1280f, 800f)
    )
    private val fonts = listOf(1f, 1.3f, 1.5f, 2f)
    private val actions = listOf(
        "LABEL_THINKING", "ACTION_MEMORY", "LABEL_ANALYSING", "LABEL_DEVELOPING",
        "ACTION_AGENTS", "ACTION_BOTS", "ACTION_TASK", "LABEL_LEARNING", "ACTION_DEVICE", "LABEL_CONNECTED"
    )
    private val targets = actions + listOf(
        "COMPOSER", "LABEL_EVOLVING", "SEARCH", "NOTIFICATIONS", "PROFILE",
        "VIEW_AVATAR", "VIEW_ORB", "VIEW_COMPACT"
    )

    private fun layout(window: Window, font: Float): ContinuumHomeLayout.Layout =
        if (ContinuumHomeLayout.isWide(window.width, window.height)) {
            ContinuumHomeLayout.wide(window.width, window.height, font)
        } else ContinuumHomeLayout.phone(window.width, font)

    private fun box(layout: ContinuumHomeLayout.Layout, name: String): ContinuumHomeLayout.Box {
        val result = layout.boxes.entries.singleOrNull { it.key.name == name }?.value
        assertNotNull("The reference cockpit must expose $name", result)
        return requireNotNull(result)
    }

    @Test fun everyDirectDestinationRemainsReachableAtPhoneFoldAndLargeType() {
        for (window in windows) for (font in fonts) {
            val layout = layout(window, font)
            val context = "${window.width}x${window.height} font=$font"
            assertEquals("$context must preserve physical dp instead of shrinking all text", 1f, layout.scale, .001f)
            for (name in targets) {
                val area = box(layout, name)
                assertTrue("$context $name width=${area.width}", area.width >= 48f)
                assertTrue("$context $name height=${area.height}", area.height >= 48f)
                assertTrue("$context $name stays inside the window", area.left >= 0f && area.right <= window.width + .01f)
                assertTrue("$context $name stays in scrollable content", area.top >= 0f && area.bottom <= layout.contentHeight + .01f)
            }
            assertTrue("$context has a real avatar stage", box(layout, "HERO").height > 0f)
            val elementSlots = ContinuumHomeScreen.elements(layout, font).keys.map { it.name }.toSet()
            assertTrue("$context every action has an actual rendered element", elementSlots.containsAll(targets))
        }
    }

    @Test fun interactiveTargetsDoNotOverlapEachOtherOrTheAvatarViewport() {
        for (window in windows) for (font in fonts) {
            val layout = layout(window, font)
            val areas = targets.map { it to box(layout, it) } + ("HERO" to box(layout, "HERO"))
            for (i in areas.indices) for (j in i + 1 until areas.size) {
                val (firstName, first) = areas[i]
                val (secondName, second) = areas[j]
                val overlaps = first.left < second.right && second.left < first.right &&
                    first.top < second.bottom && second.top < first.bottom
                assertFalse("${window.width}x${window.height} font=$font: $firstName overlaps $secondName", overlaps)
            }
        }
    }

    @Test fun bothFoldOrientationsRetainTheRailAndAllDirectActions() {
        for (window in windows.filter { ContinuumHomeLayout.isWide(it.width, it.height) }) {
            for (font in fonts) {
                val layout = layout(window, font)
                val rail = box(layout, "RAIL")
                assertTrue("A legible wide rail cannot become an icon sliver", rail.width >= 80f)
                assertTrue("The rail must leave room for the cockpit", rail.width <= window.width * .25f)
                for (name in targets + "HERO") {
                    assertTrue("$name must not be covered by the navigation rail", box(layout, name).left >= rail.right)
                }
            }
        }
    }

    @Test fun largerTextAddsSpaceInsteadOfDroppingDestinations() {
        for (window in windows) {
            val normal = layout(window, 1f)
            val large = layout(window, 2f)
            assertTrue("${window.width} wide content may scroll at 200%", large.contentHeight >= normal.contentHeight)
            for (name in actions + "COMPOSER") {
                assertTrue("$name cannot lose its reading height at 200%", box(large, name).height >= box(normal, name).height)
            }
        }
    }

    @Test fun compactPresentationKeepsAllDestinationsAndReducesStageSpace() {
        for (window in windows) for (font in fonts) {
            val normal = layout(window, font)
            val compact = ContinuumReferenceLayout.create(window.width, window.height, normal.wide, font, compact = true)
            assertTrue(box(compact, "HERO").height < box(normal, "HERO").height)
            assertTrue(box(compact, "COMPOSER").top < box(normal, "COMPOSER").top)
            for (name in targets) {
                val area = box(compact, name)
                assertTrue("Compact $name is a usable native target", area.width >= 48f && area.height >= 48f)
                assertTrue(area.left >= 0f && area.right <= window.width + .01f)
            }
        }
    }

    @Test fun anUnmeasuredCockpitDoesNotCopyTheReferenceArtworkNumbers() {
        val unknown = ContinuumDashboardState()
        val percentage = Regex("\\b\\d+(?:[.,]\\d+)?\\s*%")
        val invented = listOf("387.426", "128k", "Gemma 3B", "Continuum-7B", "All Systems Optimal", "User Recognized")
        for (window in listOf(Window(412f, 915f), Window(840f, 649f), Window(840f, 1050f))) {
            val elements = ContinuumHomeScreen.elements(layout(window, 1f), 1f)
            for ((slot, element) in elements) {
                val description = element.describe(unknown)
                assertTrue("$slot needs a meaningful accessibility label", description.isNotBlank())
                for (sample in invented) {
                    assertFalse("$slot copies unmeasured reference data: $sample", description.contains(sample, ignoreCase = true))
                }
                if (slot.name in setOf("MODEL", "STORAGE", "SYSTEM_STATUS", "SYSTEM_ACTIVITY", "WORKERS")) {
                    assertFalse("$slot must not invent a measured percentage: $description", percentage.containsMatchIn(description))
                }
            }
        }
    }
}
