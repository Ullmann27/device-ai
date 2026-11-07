package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumWorkAreaNavigationSpecTest {
    @Test fun mobileNavigationIncludesEveryWorkAreaExactlyOnce() {
        assertEquals(listOf(
            ContinuumWorkArea.START,
            ContinuumWorkArea.CHAT,
            ContinuumWorkArea.LERNEN,
            ContinuumWorkArea.ENTWICKELN,
            ContinuumWorkArea.MEHR
        ), ContinuumWorkAreaNavigationSpec.destinations)
        assertEquals(5, ContinuumWorkAreaNavigationSpec.destinations.distinct().size)
    }

    @Test fun everyWorkAreaHasReadableTextAndAccessibleDescription() {
        for (destination in ContinuumWorkAreaNavigationSpec.destinations) {
            assertTrue(destination.title.isNotBlank())
            assertTrue(destination.symbol.isNotBlank())
            assertTrue(destination.explanation.isNotBlank())
            assertTrue(ContinuumWorkAreaNavigationSpec.accessibilityLabel(destination)
                .contains(destination.title))
        }
    }

    @Test fun quickActionsRemainReadableOnSmallWindowsAndLargeFonts() {
        assertEquals(2, ContinuumWorkAreaNavigationSpec.quickActionColumns(320, 1.0f))
        assertEquals(2, ContinuumWorkAreaNavigationSpec.quickActionColumns(400, 1.25f))
        assertEquals(1, ContinuumWorkAreaNavigationSpec.quickActionColumns(319, 1.0f))
        assertEquals(1, ContinuumWorkAreaNavigationSpec.quickActionColumns(400, 1.5f))
        assertEquals(1, ContinuumWorkAreaNavigationSpec.quickActionColumns(400, Float.NaN))
        assertEquals(1, ContinuumWorkAreaNavigationSpec.quickActionColumns(400, Float.POSITIVE_INFINITY))
    }

    @Test fun moreIsTransientAndDoesNotEraseActiveTabSelection() {
        for (current in ContinuumWorkAreaNavigationSpec.destinations) {
            assertEquals(current, ContinuumWorkAreaNavigationSpec.selectionAfterTap(
                current, ContinuumWorkArea.MEHR
            ))
        }
        assertEquals(ContinuumWorkArea.CHAT, ContinuumWorkAreaNavigationSpec.selectionAfterTap(
            ContinuumWorkArea.START, ContinuumWorkArea.CHAT
        ))
        assertEquals(ContinuumWorkArea.START, ContinuumWorkAreaNavigationSpec.selectionAfterTap(
            ContinuumWorkArea.CHAT, ContinuumWorkArea.START
        ))
    }

    @Test fun bottomBarIsMobileOnlyAndHiddenWhileKeyboardIsVisible() {
        assertTrue(ContinuumWorkAreaNavigationSpec.showBottomBar(false, false))
        assertFalse(ContinuumWorkAreaNavigationSpec.showBottomBar(false, true))
        assertFalse(ContinuumWorkAreaNavigationSpec.showBottomBar(true, false))
        assertFalse(ContinuumWorkAreaNavigationSpec.showBottomBar(true, true))
    }
}
