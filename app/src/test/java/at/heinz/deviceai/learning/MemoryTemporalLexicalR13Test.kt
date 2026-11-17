package at.heinz.deviceai.learning

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frozen before R13 production: time cues need token boundaries and flexible whitespace. */
class MemoryTemporalLexicalR13Test {
    private val zone = ZoneId.of("Europe/Vienna")
    private val cue = TemporalCue(zone)
    private val now = Instant.parse("2026-11-05T12:00:00Z").toEpochMilli()
    private val twoDaysAgo = java.time.LocalDate.of(2026, 11, 3)
    private val window = twoDaysAgo.atStartOfDay(zone).toInstant().toEpochMilli() until
        twoDaysAgo.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun multipleSpacesRetainRelativeDayWindow() {
        assertEquals(window, cue.window("vor   2   Tagen", now))
    }

    @Test fun mixedWhitespaceRetainsRelativeDayWindow() {
        assertEquals(window, cue.window("vor\t2\nTagen", now))
    }

    @Test fun relativeDayCueRequiresStartOfWord() {
        assertNull(cue.window("zuvor 2 tagen", now))
        assertNull(cue.window("bevor 2 tagen", now))
    }

    @Test fun relativeDayCueRequiresEndOfWord() {
        assertNull(cue.window("vor 2 tagenlos", now))
    }

    @Test fun writtenNumbersStillWork() {
        assertEquals(window, cue.window("vor zwei Tagen", now))
    }

    @Test fun numericFilterAndTemporalParserAgreeOnWhitespace() {
        assertEquals(setOf("4"), GermanText.numericEvidenceTerms("vor   2   Tagen 4 Kinder"))
        assertEquals(setOf("4"), GermanText.numericEvidenceTerms("vor\t2\nTagen 4 Kinder"))
    }

    @Test fun nonTemporalPrefixesDoNotEraseFactualDigits() {
        assertEquals(setOf("2", "4"), GermanText.numericEvidenceTerms("zuvor 2 Tagen 4 Kinder"))
        assertEquals(setOf("2", "4"), GermanText.numericEvidenceTerms("bevor 2 Tagen 4 Kinder"))
    }

    @Test fun plainRelativeDaysPreserveExistingSemantics() {
        assertEquals(window, cue.window("vor 2 Tagen", now))
        assertTrue(GermanText.numericEvidenceTerms("vor 2 Tagen").isEmpty())
    }
}
