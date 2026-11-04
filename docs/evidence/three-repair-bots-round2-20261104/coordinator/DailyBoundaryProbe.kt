package at.heinz.deviceai.learning

import java.time.Instant
import java.time.ZoneId

/** Independent fixed-UTC oracle, compiled with the complete production retriever. */
object DailyBoundaryProbe {
    private data class Case(val zone: String, val now: String, val query: String, val start: String, val end: String)

    @JvmStatic fun main(args: Array<String>) {
        val santiago = "America/Santiago"
        val now = "2026-09-06T15:00:00Z"
        val cases = listOf(
            Case(santiago, now, "heute", "2026-09-06T04:00:00Z", "2026-09-07T03:00:00Z"),
            Case(santiago, now, "gestern", "2026-09-05T04:00:00Z", "2026-09-06T04:00:00Z"),
            Case(santiago, now, "vorgestern", "2026-09-04T04:00:00Z", "2026-09-05T04:00:00Z"),
            Case(santiago, now, "vor zwei Tagen", "2026-09-04T04:00:00Z", "2026-09-05T04:00:00Z"),
            Case(santiago, now, "vor 3 Tagen", "2026-09-03T04:00:00Z", "2026-09-04T04:00:00Z"),
            Case(santiago, now, "vor einem Monat", "2026-07-23T04:00:00Z", "2026-08-18T04:00:00Z"),
            Case("Europe/Vienna", "2026-03-29T12:00:00Z", "heute", "2026-03-28T23:00:00Z", "2026-03-29T22:00:00Z"),
            Case("Europe/Vienna", "2026-10-25T12:00:00Z", "heute", "2026-10-24T22:00:00Z", "2026-10-25T23:00:00Z"),
        )
        var failed = 0
        for (case in cases) {
            val actual = TemporalCue(ZoneId.of(case.zone)).window(case.query, Instant.parse(case.now).toEpochMilli())
            val start = Instant.parse(case.start).toEpochMilli()
            val end = Instant.parse(case.end).toEpochMilli() - 1
            val pass = actual?.first == start && actual.last == end
            if (!pass) failed++
            println("DAILY_BOUNDARY zone=${case.zone} now=${case.now} query=${case.query} " +
                "expected=$start..$end actual=$actual pass=$pass")
        }
        println("DAILY_BOUNDARY_TOTAL cases=${cases.size} pass=${cases.size - failed} fail=$failed")
        check(failed == 0) { "Daily calendar boundary mismatch in $failed cases" }
    }
}
