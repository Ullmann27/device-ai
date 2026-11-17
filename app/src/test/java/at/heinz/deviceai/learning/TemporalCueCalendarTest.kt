package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Calendar regressions for the existing M2.9 retrieval component; no model or Android runtime. */
class TemporalCueCalendarTest {
    private val vienna = ZoneId.of("Europe/Vienna")
    private fun millis(instant: String) = Instant.parse(instant).toEpochMilli()

    private fun assertWindow(
        query: String,
        now: String,
        start: String,
        endExclusive: String,
        zone: ZoneId = vienna,
    ) {
        val cue = TemporalCue(zone)
        val window = cue.window(query, millis(now))
        assertNotNull("Expected a time window for: $query at $now", window)
        val actual = requireNotNull(window)
        assertEquals("First included millisecond: $query at $now", millis(start), actual.first)
        assertEquals("Last included millisecond: $query at $now", millis(endExclusive) - 1, actual.last)
        assertEquals(0.35, cue.weight(actual, actual.first - 1), 0.0)
        assertEquals(1.0, cue.weight(actual, actual.first), 0.0)
        assertEquals(1.0, cue.weight(actual, actual.last), 0.0)
        assertEquals(0.35, cue.weight(actual, actual.last + 1), 0.0)
    }

    @Test fun previousWeekHasTheSameMondayToSundayBoundsThroughoutTheCurrentWeek() {
        for (day in 2..8) {
            for (query in listOf("letzte Woche", "letzten Woche", "vorige Woche", "voriger Woche")) {
                assertWindow(
                    query,
                    "2026-11-0${day}T09:00:00Z",
                    "2026-10-25T23:00:00Z",
                    "2026-11-01T23:00:00Z",
                )
            }
        }
    }

    @Test fun previousWeekCanCrossTheYearBoundary() {
        assertWindow("letzte Woche", "2026-01-06T12:00:00Z", "2025-12-28T23:00:00Z", "2026-01-04T23:00:00Z")
    }

    @Test fun previousWeekKeepsLocalMidnightAcrossBothDaylightSavingChanges() {
        assertWindow("letzte Woche", "2026-03-30T12:00:00Z", "2026-03-22T23:00:00Z", "2026-03-29T22:00:00Z")
        assertWindow("letzte Woche", "2026-10-26T12:00:00Z", "2026-10-18T22:00:00Z", "2026-10-25T23:00:00Z")
    }

    @Test fun previousMonthIncludesTheWholeCalendarMonth() {
        for (now in listOf("2026-11-01T00:00:00Z", "2026-11-04T09:00:00Z", "2026-11-30T22:59:59Z")) {
            for (query in listOf("letzten Monat", "letzter Monat", "voriger Monat")) {
                assertWindow(query, now, "2026-09-30T22:00:00Z", "2026-10-31T23:00:00Z")
            }
        }
    }

    @Test fun previousMonthCanCrossTheYearBoundary() {
        assertWindow("letzten Monat", "2027-01-15T12:00:00Z", "2026-11-30T23:00:00Z", "2026-12-31T23:00:00Z")
    }

    @Test fun previousMonthUsesFebruarysActualLengthInLeapAndOrdinaryYears() {
        assertWindow("letzten Monat", "2028-03-15T12:00:00Z", "2028-01-31T23:00:00Z", "2028-02-29T23:00:00Z")
        assertWindow("letzten Monat", "2027-03-15T12:00:00Z", "2027-01-31T23:00:00Z", "2027-02-28T23:00:00Z")
    }

    @Test fun previousMonthKeepsLocalMidnightAcrossSpringDaylightSavingChange() {
        assertWindow("letzten Monat", "2026-04-02T12:00:00Z", "2026-02-28T23:00:00Z", "2026-03-31T22:00:00Z")
    }

    @Test fun calendarBoundariesUseTheConfiguredZone() {
        // The same instant is already November in Vienna, but still October in UTC.
        val now = "2026-10-31T23:30:00Z"
        assertWindow("letzten Monat", now, "2026-09-30T22:00:00Z", "2026-10-31T23:00:00Z")
        assertWindow("letzten Monat", now, "2026-09-01T00:00:00Z", "2026-10-01T00:00:00Z", ZoneId.of("UTC"))
    }

    @Test fun aMidnightGapTodayDoesNotShiftPreviousMonthBoundaries() {
        assertWindow(
            "letzten Monat",
            "2026-09-06T12:00:00Z",
            "2026-08-01T04:00:00Z",
            "2026-09-01T04:00:00Z",
            ZoneId.of("America/Santiago"),
        )
    }

    @Test fun aMidnightGapTodayDoesNotShiftPreviousWeekBoundaries() {
        assertWindow(
            "letzte Woche",
            "2026-09-06T12:00:00Z",
            "2026-08-24T04:00:00Z",
            "2026-08-31T04:00:00Z",
            ZoneId.of("America/Santiago"),
        )
    }

    @Test fun lastMonthRetrievalRanksItsFinalDayAboveAnOlderIdenticalMemoryWithoutPromotingTrust() {
        val now = millis("2026-11-04T09:00:00Z")
        val docs = listOf(
            MemoryDoc("older-confirmed", "Die Rechnung wurde bezahlt.", millis("2026-09-21T12:00:00Z"), true),
            MemoryDoc("last-month-unconfirmed", "Die Rechnung wurde bezahlt.", millis("2026-10-31T12:00:00Z"), false),
        )
        val retrievers = listOf(
            Bm25GermanRetriever(TemporalCue(vienna)),
            CharNgramHashRetriever(TemporalCue(vienna)),
            ContinuumMemoryRetriever.default(vienna),
        )
        for (retriever in retrievers) {
            retriever.index(docs)
            val hits = retriever.search("Welche Rechnung wurde letzten Monat bezahlt?", now, 2)
            assertEquals(2, hits.size)
            assertEquals("last-month-unconfirmed", hits.first().doc.id)
            assertFalse("Retrieval must not promote an unconfirmed memory", hits.first().confirmed)
            for (hit in hits) assertEquals(docs.first { it.id == hit.doc.id }.confirmed, hit.confirmed)
        }
    }

    @Test fun existingDailyCuesAndApproximateOneMonthAgoWindowRemainUnchanged() {
        val now = "2026-11-04T09:00:00Z"
        assertWindow("heute", now, "2026-11-03T23:00:00Z", "2026-11-04T23:00:00Z")
        assertWindow("gestern", now, "2026-11-02T23:00:00Z", "2026-11-03T23:00:00Z")
        assertWindow("vorgestern", now, "2026-11-01T23:00:00Z", "2026-11-02T23:00:00Z")
        assertWindow("vor drei Tagen", now, "2026-10-31T23:00:00Z", "2026-11-01T23:00:00Z")
        assertWindow("vor 3 Tagen", now, "2026-10-31T23:00:00Z", "2026-11-01T23:00:00Z")
        assertWindow("vor einem Monat", now, "2026-09-19T22:00:00Z", "2026-10-15T22:00:00Z")
        assertNull(TemporalCue(vienna).window("Wo liegt die Rechnung?", millis(now)))
        assertEquals(1.0, TemporalCue(vienna).weight(null, millis(now)), 0.0)
    }

    @Test fun aDayCountBeyondLongRangeDoesNotSilentlyMeanYesterday() {
        val cue = TemporalCue(vienna)
        assertNull(cue.window("vor 9223372036854775808 Tagen", millis("2026-11-04T09:00:00Z")))
    }

    @Test fun aDayCountBeyondTheCalendarRangeDoesNotCrashRetrieval() {
        assertRejectedRelativeDate("vor 9223372036854775807 Tagen")
    }

    @Test fun aDateBeyondEpochMillisecondRangeDoesNotCrashRetrieval() {
        assertRejectedRelativeDate("vor 200000000000 Tagen")
    }

    private fun assertRejectedRelativeDate(query: String) {
        val now = millis("2026-11-04T09:00:00Z")
        val cue = TemporalCue(vienna)
        assertNull("Unrepresentable dates must not become a time filter", cue.window(query, now))
        val retriever = CharNgramHashRetriever(cue)
        retriever.index(listOf(MemoryDoc("receipt", "Rechnung bezahlt", now, false)))
        val hit = retriever.search("Rechnung bezahlt $query", now).single()
        assertEquals("receipt", hit.doc.id)
        assertFalse(hit.confirmed)
    }
}
