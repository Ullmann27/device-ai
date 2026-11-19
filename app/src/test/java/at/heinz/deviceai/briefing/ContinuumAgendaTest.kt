package at.heinz.deviceai.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The appointments of a day as they are spoken and shown: empty, full, all-day, overlapping, tomorrow. */
class ContinuumAgendaTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private val today = LocalDate.of(2026, 11, 19)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    private fun now(hour: Int, minute: Int = 0): Instant = Instant.ofEpochMilli(at(today, hour, minute))
    private fun event(title: String, hour: Int, minute: Int = 0, lengthMinutes: Int = 60, date: LocalDate = today,
                      location: String? = null) =
        ContinuumCalendarEvent(title, at(date, hour, minute), at(date, hour, minute) + lengthMinutes * 60_000L, false, location)
    private fun allDay(title: String, date: LocalDate = today, days: Long = 1) = ContinuumCalendarEvent(title,
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        date.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), true)

    private fun day(events: List<ContinuumCalendarEvent>, now: Instant = now(7), date: LocalDate = today) =
        ContinuumAgenda.day(events, date, zone, now)

    @Test fun anEmptyDayIsSaidAsSuch() {
        val empty = day(emptyList())
        assertTrue(empty.empty)
        assertEquals("Heute steht nichts im Kalender.", ContinuumAgenda.spoken(empty, "Heute"))
        assertEquals("Morgen steht nichts im Kalender.", ContinuumAgenda.spoken(day(emptyList(), date = today.plusDays(1)), "Morgen"))
        assertEquals(emptyList<String>(), ContinuumAgenda.lines(empty))
        // An event of another day does not belong to today.
        assertTrue(day(listOf(event("Flug", 8, date = today.plusDays(1)), event("Gestern", 9, date = today.minusDays(1)))).empty)
    }

    @Test fun timesAreSpokenNaturallyAndInOrder() {
        val full = day(listOf(event("Besprechung mit Anna", 14, 30), event("Zahnarzt", 9)))
        assertEquals("Heute hast du zwei Termine: um 9 Uhr Zahnarzt und um 14 Uhr 30 Besprechung mit Anna.",
            ContinuumAgenda.spoken(full, "Heute"))
        assertEquals(listOf("09:00–10:00 Zahnarzt", "14:30–15:30 Besprechung mit Anna"), ContinuumAgenda.lines(full))
        assertEquals("Heute hast du einen Termin: um 9 Uhr Zahnarzt.", ContinuumAgenda.spoken(day(listOf(event("Zahnarzt", 9))), "Heute"))
        assertEquals("9 Uhr 5", ContinuumAgenda.spokenTime(java.time.LocalTime.of(9, 5)))
        assertEquals("Donnerstag, der 19. November", ContinuumAgenda.spokenDate(today))
    }

    @Test fun moreThanFourAreCountedAndTheDetailedAnswerNamesAllWithPlace() {
        val six = (8..13).map { event("Termin $it", it, location = if (it == 9) "Praxis Dr. Beispiel" else null) }
        val view = day(six)
        assertEquals("Heute hast du sechs Termine: um 8 Uhr Termin 8, um 9 Uhr Termin 9, um 10 Uhr Termin 10 und um 11 Uhr Termin 11 " +
            "– und zwei weitere.", ContinuumAgenda.spoken(view, "Heute"))
        val detailed = ContinuumAgenda.spoken(view, "Heute", detailed = true)
        assertTrue(detailed, detailed.contains("um 9 Uhr Termin 9 (Praxis Dr. Beispiel)"))
        assertTrue(detailed, detailed.contains("um 13 Uhr Termin 13."))
        assertEquals(listOf("08:00–09:00 Termin 8", "09:00–10:00 Termin 9", "10:00–11:00 Termin 10", "+ 3 weitere"), ContinuumAgenda.lines(view))
    }

    @Test fun pastAndRunningEventsAreToldApart() {
        val events = listOf(event("Frühstück", 8), event("Besprechung", 10, lengthMinutes = 120), event("Zahnarzt", 15))
        val midday = day(events, now(11))
        assertEquals(1, midday.ended)
        assertEquals("Heute hast du zwei Termine: bis 12 Uhr läuft noch Besprechung und um 15 Uhr Zahnarzt. Ein Termin ist schon vorbei.",
            ContinuumAgenda.spoken(midday, "Heute"))
        val evening = day(events, now(20))
        assertEquals("Für heute steht nichts mehr an – drei Termine sind schon vorbei.", ContinuumAgenda.spoken(evening, "Heute"))
        assertTrue(ContinuumAgenda.lines(evening).all { it.endsWith("(vorbei)") })
        assertEquals("Für heute steht nichts mehr an – ein Termin ist schon vorbei.",
            ContinuumAgenda.spoken(day(listOf(event("Frühstück", 8)), now(20)), "Heute"))
    }

    @Test fun allDayEventsUseTheirUtcDayAndAreSaidSeparately() {
        val birthday = allDay("Geburtstag Anna")
        assertEquals("Ganztägig: Geburtstag Anna.", ContinuumAgenda.spoken(day(listOf(birthday)), "Heute"))
        assertEquals(listOf("ganztägig Geburtstag Anna"), ContinuumAgenda.lines(day(listOf(birthday))))
        // The UTC bounds of an all-day entry must not leak into the neighbouring local days.
        assertTrue(day(listOf(birthday), date = today.plusDays(1)).empty)
        assertTrue(day(listOf(birthday), date = today.minusDays(1)).empty)
        val holiday = allDay("Urlaub", today.minusDays(1), days = 3)
        assertEquals(1, day(listOf(holiday), date = today.plusDays(1)).allDay.size)
        assertTrue(day(listOf(holiday), date = today.plusDays(2)).empty)
        assertEquals("Heute hast du einen Termin: um 9 Uhr Zahnarzt. Ganztägig: Geburtstag Anna und Urlaub.",
            ContinuumAgenda.spoken(day(listOf(event("Zahnarzt", 9), holiday, birthday)), "Heute"))
    }

    @Test fun overlappingAppointmentsAreMentionedOnce() {
        val view = day(listOf(event("Besprechung", 14, lengthMinutes = 90), event("Zahnarzt", 15), event("Sport", 18)))
        assertNotNull(ContinuumAgenda.overlap(view))
        assertEquals("Heute hast du drei Termine: um 14 Uhr Besprechung, um 15 Uhr Zahnarzt und um 18 Uhr Sport. " +
            "Achtung: Besprechung und Zahnarzt überschneiden sich.", ContinuumAgenda.spoken(view, "Heute"))
        // Back to back is not an overlap; an overlap that is over is not mentioned.
        assertNull(ContinuumAgenda.overlap(day(listOf(event("A", 14), event("B", 15)))))
        assertNull(ContinuumAgenda.overlap(day(listOf(event("Besprechung", 14, lengthMinutes = 90), event("Zahnarzt", 15)), now(17))))
    }

    @Test fun tomorrowMorningIsOneSentenceAndOnlyBeforeNoon() {
        val tomorrow = today.plusDays(1)
        val events = listOf(event("Flug nach Wien", 8, date = tomorrow), event("Abendessen", 19, date = tomorrow), event("Heute", 9))
        val view = day(events, date = tomorrow)
        assertEquals("Morgen früh: um 8 Uhr Flug nach Wien.", ContinuumAgenda.spokenMorning(view))
        assertNull(ContinuumAgenda.spokenMorning(day(listOf(event("Abendessen", 19, date = tomorrow)), date = tomorrow)))
        assertEquals("Morgen hast du zwei Termine: um 8 Uhr Flug nach Wien und um 19 Uhr Abendessen.", ContinuumAgenda.spoken(view, "Morgen"))
        val (from, until) = ContinuumAgenda.briefingWindow(today, zone)
        assertEquals(at(today, 0), from)
        assertEquals(at(tomorrow, 12), until)
        assertEquals(at(tomorrow, 0) to at(tomorrow.plusDays(1), 0), ContinuumAgenda.dayWindow(tomorrow, zone))
    }

    @Test fun titlesAreDataAndStayShortAndClean() {
        assertEquals("Termin ohne Titel", ContinuumAgenda.title(ContinuumCalendarEvent("  ", 0, 1, false)))
        assertEquals("Besprechung", ContinuumAgenda.title(ContinuumCalendarEvent("Besprechung https://example.org/join?x=1 ", 0, 1, false)))
        assertEquals(ContinuumAgenda.MAX_TITLE, ContinuumAgenda.title(ContinuumCalendarEvent("x".repeat(300), 0, 1, false)).length)
        assertEquals("Zeile eins Zeile zwei", ContinuumAgenda.title(ContinuumCalendarEvent("Zeile eins\nZeile zwei.", 0, 1, false)))
        // The same entry delivered twice (two calendars) is one appointment.
        assertEquals(1, day(listOf(event("Zahnarzt", 9), event("Zahnarzt", 9))).timed.size)
        // An event from yesterday evening into today is said with its end.
        val night = ContinuumCalendarEvent("Nachtdienst", at(today.minusDays(1), 22), at(today, 6), false)
        assertEquals("Heute hast du einen Termin: bis 6 Uhr läuft noch Nachtdienst.", ContinuumAgenda.spoken(day(listOf(night), now(5)), "Heute"))
    }
}
