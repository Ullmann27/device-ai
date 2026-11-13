package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputerFailureExplanationTest {
    @Test fun fieldFailureNamesMissingLiveFrame() {
        val text = ComputerFailureExplanation.describe("NO_FRESH_MONITOR_FRAME")
        assertTrue(text.contains("kein bestätigtes Live-Bild"))
        assertTrue(text.endsWith("(Diagnose: NO_FRESH_MONITOR_FRAME)"))
    }

    @Test fun unknownOrFreeTextNeverInventsACause() {
        val neutral = "Der Grund wurde nicht eindeutig gemeldet."
        assertEquals(neutral, ComputerFailureExplanation.describe(null))
        assertEquals(neutral, ComputerFailureExplanation.describe("SOMETHING_NEW_HAPPENED"))
        assertEquals(neutral, ComputerFailureExplanation.describe("Passwort 1234 im Feld"))
        assertFalse(ComputerFailureExplanation.describe("Passwort 1234 im Feld").contains("1234"))
    }

    @Test fun everyKnownCodeHasOneSentenceWithItsCode() {
        for (code in ComputerFailureExplanation.knownCodes()) {
            val text = ComputerFailureExplanation.describe(code)
            assertTrue(code, text.endsWith("(Diagnose: $code)"))
            assertFalse(code, text.startsWith("Der Grund wurde nicht"))
        }
    }
}
