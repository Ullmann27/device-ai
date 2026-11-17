package at.heinz.deviceai.learning

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Frozen before R13 production: incidental word fragments are not day cues. */
class MemoryTemporalWordBoundariesR13Test {
    private val zone = ZoneId.of("Europe/Vienna")
    private val cue = TemporalCue(zone)
    private val now = Instant.parse("2026-11-05T12:00:00Z").toEpochMilli()

    private fun day(n: Long): LongRange {
        val d = LocalDate.of(2026, 11, 5).minusDays(n)
        return d.atStartOfDay(zone).toInstant().toEpochMilli() until
            d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    @Test fun heutzutageIsNotToday() {
        assertNull(cue.window("Heutzutage ist alles anders", now))
    }

    @Test fun longerWordsContainingGesternAreNotYesterday() {
        assertNull(cue.window("gesternlich", now))
        assertNull(cue.window("vorgesternlich", now))
    }

    @Test fun standaloneTimeWordsRetainTheirWindows() {
        assertEquals(day(0), cue.window("heute", now))
        assertEquals(day(1), cue.window("gestern", now))
        assertEquals(day(2), cue.window("vorgestern", now))
    }

    @Test fun timeWordsInsideNaturalPhrasesStillWork() {
        assertEquals(day(0), cue.window("Was ist heute neu?", now))
        assertEquals(day(1), cue.window("Was habe ich gestern gemacht?", now))
    }
}
