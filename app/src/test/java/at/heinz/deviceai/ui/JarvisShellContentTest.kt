package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisShellContentTest {
    @Test fun everyContentSlotOfEveryFormFactorHasRealRowsOrNotAvailable() {
        val content = JarvisShellContent.forState({ ContinuumDashboardState() })
        for ((w, h) in listOf(412f to 915f, 884f to 760f, 1280f to 800f)) {
            val shell = JarvisShellLayout.create(w, h)
            for (slot in shell.boxes.keys.filter { it in JarvisShellContent.filled })
                assertTrue("$w x $h $slot has Kern content", slot in content)
            assertFalse("Avatar slot belongs to the live avatar", JarvisShellLayout.Slot.AVATAR in content)
        }
        val empty = ContinuumDashboardState()
        val system = JarvisShellContent.rows(JarvisShellLayout.Slot.STATUS, empty, 0L)
        assertTrue(system.all { it.value == ContinuumCockpitPresentation.NOT_AVAILABLE })
        assertEquals(ContinuumCockpitPresentation.NOT_AVAILABLE, JarvisShellContent.rows(JarvisShellLayout.Slot.ROW_MEMORY, empty, 0L).single().value)
        assertEquals("Noch kein Auftrag", JarvisShellContent.rows(JarvisShellLayout.Slot.ROW_MODEL, empty, 0L).first().value)
    }
}
