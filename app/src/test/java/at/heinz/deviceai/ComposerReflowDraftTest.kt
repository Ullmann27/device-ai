package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerReflowDraftTest {
    @Test fun preservesCursorSelectionAndKeyboardIntent() {
        val snapshot = ComposerReflowDraft("Hallo 👨‍💻 Continuum", 6, 8, true, true)
        assertEquals(6 to 8, snapshot.safeSelection())
        assertTrue(snapshot.text.contains("👨‍💻") && snapshot.hadFocus && snapshot.imeWasVisible)
        assertEquals(0 to 0, ComposerReflowDraft("", -1, -1, false, false).safeSelection())
        assertEquals(5 to 5, ComposerReflowDraft("Hallo", 9, 99, true, false).safeSelection())
        assertEquals(2 to 5, ComposerReflowDraft("Hallo", 5, 2, true, true).safeSelection())
        assertEquals(3 to 6, ComposerReflowDraft("abcdef", -1, 3, true, false).safeSelection())
    }
}
