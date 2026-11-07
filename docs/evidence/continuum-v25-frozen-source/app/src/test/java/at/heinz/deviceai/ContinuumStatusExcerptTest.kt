package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumStatusExcerptTest {
    @Test fun keepsShortGermanStatusVerbatim() {
        assertEquals("Lernbereit: neues Beispiel erwartet.",
            ContinuumStatusExcerpt.readable("  Lernbereit: neues Beispiel erwartet.  ", 110))
    }

    @Test fun truncationEndsAtWordBoundaryWithVisibleEllipsis() {
        val raw = "Lernbereit: Ich warte auf ein neues bestätigtes Sprachbeispiel. " +
            "Die bisherigen Wiederholungen haben keinen weiteren messbaren Fortschritt gezeigt."
        val result = ContinuumStatusExcerpt.readable(raw, 110)
        assertTrue(result.length <= 110)
        assertTrue(result.endsWith("…"))
        assertFalse(result.endsWith("wei…"))
        assertFalse(result.contains("\n"))
    }

    @Test fun compactMultilineTelemetryRemainsReadable() {
        assertEquals("Ein Test mit Daten",
            ContinuumStatusExcerpt.readable("  Ein \nTest\t mit   Daten ", 100))
    }

    @Test fun invalidLengthIsRejectedInsteadOfClipping() {
        try {
            ContinuumStatusExcerpt.readable("Test", 3)
            throw AssertionError("Should reject unreadable excerpt length")
        } catch (_: IllegalArgumentException) {
            // The caller must provide enough space for a readable preview.
        }
    }
}
