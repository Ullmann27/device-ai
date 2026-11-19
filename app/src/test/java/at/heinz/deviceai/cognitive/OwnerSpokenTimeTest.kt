package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A stored moment as people say it – from two real timestamps, never more precise than the data, and nothing at all
 * when the two values are no honest pair. 19.11.2026 is a Thursday.
 */
class OwnerSpokenTimeTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private fun at(time: String): Long = LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli()
    private val now = at("2026-11-19T10:00:00")
    private fun said(time: String) = OwnerSpokenTime.at(at(time), now, zone)

    @Test fun todayIsSaidByHowLongAgoAndThenByThePartOfTheDay() {
        assertEquals("gerade eben", said("2026-11-19T09:57:00"))
        assertEquals("vorhin", said("2026-11-19T09:20:00"))
        assertEquals("heute früh", said("2026-11-19T08:30:00"))
        assertEquals("heute Nacht", said("2026-11-19T02:10:00"))
        val evening = at("2026-11-19T23:30:00")
        assertEquals("heute Vormittag", OwnerSpokenTime.at(at("2026-11-19T09:40:00"), evening, zone))
        assertEquals("heute Mittag", OwnerSpokenTime.at(at("2026-11-19T12:30:00"), evening, zone))
        assertEquals("heute Nachmittag", OwnerSpokenTime.at(at("2026-11-19T15:00:00"), evening, zone))
        assertEquals("heute Abend", OwnerSpokenTime.at(at("2026-11-19T19:00:00"), evening, zone))
        assertEquals("heute am späten Abend", OwnerSpokenTime.at(at("2026-11-19T22:15:00"), evening, zone))
    }

    @Test fun earlierDaysAreSaidAsPeopleSayThem() {
        assertEquals("gestern Abend", said("2026-11-18T21:28:00"))
        assertEquals("gestern früh", said("2026-11-18T06:00:00"))
        assertEquals("gestern Nachmittag", said("2026-11-18T17:10:00"))
        assertEquals("gestern in der Nacht", said("2026-11-18T03:00:00"))
        // Just before midnight is still yesterday – the calendar day counts, not 24 hours.
        assertEquals("gestern am späten Abend", OwnerSpokenTime.at(at("2026-11-18T23:50:00"), at("2026-11-19T00:10:00"), zone))
        assertEquals("vorgestern", said("2026-11-17T15:00:00"))
        assertEquals("am Montag", said("2026-11-16T12:00:00"))
        assertEquals("am Freitag", said("2026-11-13T09:00:00"))
        assertEquals("vor einer Woche", said("2026-11-12T09:00:00"))
        assertEquals("vor gut einer Woche", said("2026-11-10T09:00:00"))
        assertEquals("vor knapp zwei Wochen", said("2026-11-07T09:00:00"))
        assertEquals("vor zwei Wochen", said("2026-11-04T09:00:00"))
        assertEquals("vor drei Wochen", said("2026-10-28T09:00:00"))
        assertEquals("vor etwa einem Monat", said("2026-10-15T09:00:00"))
    }

    @Test fun withoutAnHonestPairOfTimestampsNothingIsSaid() {
        assertNull(OwnerSpokenTime.at(null, now, zone))
        assertNull(OwnerSpokenTime.at(0L, now, zone))
        assertNull("a clock that went backwards", said("2026-11-19T10:00:01"))
        assertNull("an absurd gap", said("2026-08-01T10:00:00"))
        assertNull(OwnerSpokenTime.atAbout(null, now, zone))
    }

    @Test fun theClockIsRoundedToTheHalfHourAndOnlyForTodayAndYesterday() {
        fun about(time: String) = OwnerSpokenTime.atAbout(at(time), now, zone)
        assertEquals("gestern Abend gegen halb zehn", about("2026-11-18T21:28:00"))
        assertEquals("gestern Abend gegen neun", about("2026-11-18T21:10:00"))
        assertEquals("gestern Mittag gegen eins", about("2026-11-18T13:05:00"))
        assertEquals("heute früh gegen halb neun", about("2026-11-19T08:30:00"))
        assertEquals("gestern am späten Abend", OwnerSpokenTime.atAbout(at("2026-11-18T23:50:00"), now, zone))
        // „vorhin“ and older days carry no clock: nobody says „am Montag gegen halb zehn“ from memory.
        assertEquals("vorhin", about("2026-11-19T09:20:00"))
        assertEquals("vorgestern", about("2026-11-17T15:00:00"))
        assertEquals("am Montag", about("2026-11-16T12:00:00"))
    }

    @Test fun weekdaysAreFoundInASentenceAndCountedBack() {
        assertEquals(DayOfWeek.TUESDAY, OwnerSpokenTime.weekdayIn("was haben wir am dienstag gemacht"))
        assertNull(OwnerSpokenTime.weekdayIn("was haben wir gestern gemacht"))
        // Thursday 19.11.: the last Tuesday was two days ago, the last Thursday a week ago.
        assertEquals(2, OwnerSpokenTime.daysSince(DayOfWeek.TUESDAY, now, zone))
        assertEquals(7, OwnerSpokenTime.daysSince(DayOfWeek.THURSDAY, now, zone))
        assertEquals(6, OwnerSpokenTime.daysSince(DayOfWeek.FRIDAY, now, zone))
        assertEquals("drei", OwnerSpokenTime.number(3))
        assertEquals("13", OwnerSpokenTime.number(13))
    }
}
