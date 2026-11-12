package at.heinz.deviceai.ui

import at.heinz.deviceai.ContinuumWorkAreaNavigationSpec
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import org.junit.Assert.*
import org.junit.Test

class CinematicWorkstationLayoutTest {
    @Test fun originalSphereSharesForeheadAxisBehindAvatar() {
        for ((w, h) in listOf(412f to 915f, 600f to 800f, 884f to 760f, 1280f to 800f)) {
            val shell = CinematicWorkstationLayout.create(w, h)
            val head = requireNotNull(shell.headBounds)
            val orb = requireNotNull(shell.orbBounds)
            assertTrue(shell.workstation)
            assertEquals(head.centerX, orb.centerX, .01f)
            val infinityY = orb.top + orb.height * ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT.centerY
            assertTrue(infinityY > head.top + head.height * .20f)
            assertTrue(infinityY < head.top + head.height * .40f)
            assertTrue(orb.right > head.left && orb.left < head.right)
        }
    }

    @Test fun eightNativeDestinationsAreReadableOnPhoneAndFold() {
        for ((w, expectedRows) in listOf(412f to 2, 884f to 1)) {
            val shell = CinematicWorkstationLayout.create(w, 915f)
            val dock = requireNotNull(shell[Slot.BOTTOM_NAV])
            val cells = JarvisTiles.bottomNavItemBoxes(dock)
            assertEquals(8, cells.size)
            assertEquals(expectedRows, cells.map { it.top }.distinct().size)
            assertTrue(cells.all { it.width >= 72f && it.height >= 48f })
            assertEquals(8, JarvisTiles.bottomNav.map { it.target }.distinct().size)
            assertTrue(shell[Slot.INPUT]!!.bottom <= dock.top)
        }
    }

    @Test fun formerDockFunctionsRemainReachable() {
        val routes = ContinuumWorkAreaNavigationSpec.moreEntries.map { it.target }
        assertTrue(routes.containsAll(CockpitGlassButtons.secondaryTargets))
        assertTrue(CinematicWorkstationLayout.create(412f, 915f).headerActionBoxes().containsKey("more"))
    }

    @Test fun resizingAndLargeTextKeepPositiveBoundsAndAllowScrolling() {
        for (width in listOf(240f, 320f, 412f, 600f, 884f, 1280f)) {
            for (font in listOf(1f, 1.5f, 2f)) {
                val shell = CinematicWorkstationLayout.create(width, 500f, font)
                assertTrue(shell.boxes.values.all { it.left >= 0 && it.top >= 0 &&
                    it.right <= width && it.bottom <= shell.height && it.width > 0 && it.height > 0 })
                assertTrue(shell[Slot.WORK_PROCESS]!!.top > shell.headBounds!!.top)
                assertTrue(CockpitGlassButtons.baseLabelStyle(font).size >= 12.5f)
            }
        }
    }
}
