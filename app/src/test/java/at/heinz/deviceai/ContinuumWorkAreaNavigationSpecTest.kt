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

    @Test fun bottomBarIsMobileOnlyAndHiddenWhileKeyboardIsVisible() {
        assertTrue(ContinuumWorkAreaNavigationSpec.showBottomBar(false, false))
        assertFalse(ContinuumWorkAreaNavigationSpec.showBottomBar(false, true))
        assertFalse(ContinuumWorkAreaNavigationSpec.showBottomBar(true, false))
        assertFalse(ContinuumWorkAreaNavigationSpec.showBottomBar(true, true))
    }
}
