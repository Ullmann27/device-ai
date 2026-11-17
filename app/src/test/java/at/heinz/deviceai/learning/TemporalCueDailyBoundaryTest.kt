package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Fixed UTC oracles for local days whose midnight is skipped; exercises the real retrievers. */
class TemporalCueDailyBoundaryTest {
    private val santiago = ZoneId.of("America/Santiago")
    private val transitionNow = "2026-09-06T12:00:00Z"
    private fun millis(instant: String) = Instant.parse(instant).toEpochMilli()

    private fun assertWindow(
        query: String,
        start: String,
        endExclusive: String,
        now: String = transitionNow,
        zone: ZoneId = santiago,
    ) {
        val cue = TemporalCue(zone)
        val window = cue.window(query, millis(now))
        assertNotNull("Expected a time window for: $query at $now in $zone", window)
        val actual = requireNotNull(window)
        val first = millis(start)
        val afterLast = millis(endExclusive)
        assertEquals("First included millisecond: $query at $now in $zone", first, actual.first)
        assertEquals("Last included millisecond: $query at $now in $zone", afterLast - 1, actual.last)
        assertEquals(0.35, cue.weight(actual, first - 1), 0.0)
        assertEquals(1.0, cue.weight(actual, first), 0.0)
        assertEquals(1.0, cue.weight(actual, afterLast - 1), 0.0)
        assertEquals(0.35, cue.weight(actual, afterLast), 0.0)
    }

    @Test fun todayWithMissingMidnightEndsAtTheNextDaysActualStart() {
        // Santiago jumps from September 5 at 23:59:59 to September 6 at 01:00.
        for (now in listOf("2026-09-06T04:00:00Z", transitionNow, "2026-09-07T02:59:59Z")) {
            assertWindow("heute", "2026-09-06T04:00:00Z", "2026-09-07T03:00:00Z", now)
        }
    }

    @Test fun yesterdayKeepsItsFirstHourWhenTodayHasMissingMidnight() {
        assertWindow("gestern", "2026-09-05T04:00:00Z", "2026-09-06T04:00:00Z")
    }

    @Test fun dayBeforeYesterdayKeepsBothMidnightsWhenTodayHasMissingMidnight() {
        assertWindow("vorgestern", "2026-09-04T04:00:00Z", "2026-09-05T04:00:00Z")
    }

    @Test fun spelledDayCountsKeepEachTargetsActualMidnights() {
        for ((count, start, end) in listOf(
            Triple("zwei", "2026-09-04T04:00:00Z", "2026-09-05T04:00:00Z"),
            Triple("drei", "2026-09-03T04:00:00Z", "2026-09-04T04:00:00Z"),
            Triple("vier", "2026-09-02T04:00:00Z", "2026-09-03T04:00:00Z"),
            Triple("fünf", "2026-09-01T04:00:00Z", "2026-09-02T04:00:00Z"),
        )) {
            assertWindow("vor $count Tagen", start, end)
        }
    }

    @Test fun numericDayCountsIncludingZeroKeepEachTargetsActualMidnights() {
        for ((count, start, end) in listOf(
            Triple(0, "2026-09-06T04:00:00Z", "2026-09-07T03:00:00Z"),
            Triple(1, "2026-09-05T04:00:00Z", "2026-09-06T04:00:00Z"),
            Triple(2, "2026-09-04T04:00:00Z", "2026-09-05T04:00:00Z"),
            Triple(3, "2026-09-03T04:00:00Z", "2026-09-04T04:00:00Z"),
            Triple(4, "2026-09-02T04:00:00Z", "2026-09-03T04:00:00Z"),
            Triple(5, "2026-09-01T04:00:00Z", "2026-09-02T04:00:00Z"),
        )) {
            assertWindow("vor $count Tagen", start, end)
        }
    }

    @Test fun approximateMonthAgoKeepsItsExistingDayOffsetsAndActualMidnights() {
        // Existing approximation: from 45 days ago through 20 days ago, inclusive.
        assertWindow("vor einem Monat", "2026-07-23T04:00:00Z", "2026-08-18T04:00:00Z")
    }

    @Test fun aTargetDayWithMissingMidnightStillStartsAtItsFirstValidTime() {
        assertWindow("gestern", "2026-09-06T04:00:00Z", "2026-09-07T03:00:00Z", "2026-09-07T12:00:00Z")
    }

    @Test fun aTwentyThreeHourDayKeepsBothLocalMidnights() {
        assertWindow("heute", "2026-03-28T23:00:00Z", "2026-03-29T22:00:00Z", "2026-03-29T12:00:00Z", ZoneId.of("Europe/Vienna"))
    }

    @Test fun aTwentyFiveHourDayKeepsBothLocalMidnights() {
        assertWindow("heute", "2026-10-24T22:00:00Z", "2026-10-25T23:00:00Z", "2026-10-25T12:00:00Z", ZoneId.of("Europe/Vienna"))
    }

    @Test fun todaysDateUsesTheConfiguredZoneAtTheSameInstant() {
        val now = "2026-09-06T03:30:00Z"
        assertWindow("heute", "2026-09-05T04:00:00Z", "2026-09-06T04:00:00Z", now)
        assertWindow("heute", "2026-09-06T00:00:00Z", "2026-09-07T00:00:00Z", now, ZoneId.of("UTC"))
    }

    @Test fun yesterdayRetrievalIncludesItsFirstHourWithoutPromotingTrust() {
        assertBoundaryRetrieval(
            "Welche Rechnung wurde gestern bezahlt?",
            "2026-09-05T04:30:00Z",
            "2026-09-06T04:30:00Z",
        )
    }

    @Test fun todayRetrievalExcludesTheNextDaysFirstHourWithoutPromotingTrust() {
        assertBoundaryRetrieval(
            "Welche Rechnung wurde heute bezahlt?",
            "2026-09-07T02:30:00Z",
            "2026-09-07T03:30:00Z",
        )
    }

    private fun assertBoundaryRetrieval(query: String, inside: String, outside: String) {
        // Identical text isolates calendar weighting; put the outside document first to catch ties.
        val docs = listOf(
            MemoryDoc("outside-confirmed", "Die Rechnung wurde bezahlt.", millis(outside), true),
            MemoryDoc("inside-unconfirmed", "Die Rechnung wurde bezahlt.", millis(inside), false),
        )
        val retrievers = listOf(
            Bm25GermanRetriever(TemporalCue(santiago)),
            CharNgramHashRetriever(TemporalCue(santiago)),
            ContinuumMemoryRetriever.default(santiago),
        )
        for (retriever in retrievers) {
            retriever.index(docs)
            val hits = retriever.search(query, millis(transitionNow), 2)
            assertEquals(2, hits.size)
            assertEquals("inside-unconfirmed", hits.first().doc.id)
            assertFalse("Retrieval must not promote an unconfirmed memory", hits.first().confirmed)
            assertEquals(hits.first().score * 0.35, hits.last().score, 1e-12)
            for (hit in hits) assertEquals(docs.first { it.id == hit.doc.id }.confirmed, hit.confirmed)
        }
    }
}
