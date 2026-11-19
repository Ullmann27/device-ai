package at.heinz.deviceai.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * „Sag mir, ob ich etwas vergessen könnte“ – the fixed rules over real calendar entries: week windows, overlaps, a
 * change of place without time for the way, entries without place or time, early and late ones, what starts soon,
 * days with nothing and free time. Everything here is computed; nothing is guessed and no model takes part.
 */
class AgendaCheckTest {
    private val zone = ZoneId.of("Europe/Vienna")
    /** A Thursday. */
    private val today = LocalDate.of(2026, 11, 19)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    private fun now(hour: Int, minute: Int = 0, date: LocalDate = today): Instant = Instant.ofEpochMilli(at(date, hour, minute))
    private fun event(title: String, day: Int, hour: Int, minute: Int = 0, lengthMinutes: Int = 60, location: String? = null) =
        ContinuumCalendarEvent(title, at(today.plusDays(day.toLong()), hour, minute),
            at(today.plusDays(day.toLong()), hour, minute) + lengthMinutes * 60_000L, false, location)
    private fun allDay(title: String, day: Int, days: Long = 1) = today.plusDays(day.toLong()).let { date ->
        ContinuumCalendarEvent(title, date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            date.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), true)
    }

    private fun report(events: List<ContinuumCalendarEvent>, span: AgendaSpan = AgendaSpan.THIS_WEEK, now: Instant = now(8, 15)): AgendaCheck.Report =
        AgendaCheck.check(ContinuumAgenda.days(events, ContinuumAgenda.dates(span, today), zone, now), now.atZone(zone).toLocalDateTime())

    /** Thursday to Saturday with one of everything; the Sunday is empty. */
    private val week = listOf(
        event("Zahnarzt", 0, 9, location = "Praxis Dr. Beispiel"), event("Besprechung", 0, 9, 30, 90, "Büro"),
        event("Flug nach Berlin", 1, 5, 30, 90, "Flughafen Linz"), event("Kundentermin", 1, 14),
        event("Frühstück", 2, 9, location = "Café Linz"), event("Friseur", 2, 10, 10, 50, "Salon Wels"),
        allDay("Geburtstag Mama", 2))

    // ── The windows ─────────────────────────────────────────────────────────────────────────────────────────

    @Test fun thisWeekIsTodayUntilSundayAndNextWeekIsMondayToSunday() {
        assertEquals(today..LocalDate.of(2026, 11, 22), ContinuumAgenda.dates(AgendaSpan.THIS_WEEK, today))
        assertEquals(LocalDate.of(2026, 11, 23)..LocalDate.of(2026, 11, 29), ContinuumAgenda.dates(AgendaSpan.NEXT_WEEK, today))
        assertEquals(today..today, ContinuumAgenda.dates(AgendaSpan.TODAY, today))
        assertEquals(today.plusDays(1)..today.plusDays(1), ContinuumAgenda.dates(AgendaSpan.TOMORROW, today))
        // On a Sunday „diese Woche“ is that day alone, on a Monday the whole week; „nächste Woche“ never starts today.
        val sunday = LocalDate.of(2026, 11, 22)
        val monday = LocalDate.of(2026, 11, 23)
        assertEquals(sunday..sunday, ContinuumAgenda.dates(AgendaSpan.THIS_WEEK, sunday))
        assertEquals(monday..monday.plusDays(6), ContinuumAgenda.dates(AgendaSpan.NEXT_WEEK, sunday))
        assertEquals(monday..monday.plusDays(6), ContinuumAgenda.dates(AgendaSpan.THIS_WEEK, monday))
        assertEquals(monday.plusDays(7)..monday.plusDays(13), ContinuumAgenda.dates(AgendaSpan.NEXT_WEEK, monday))
        // One read: the start of the first day to the start of the day after the last one.
        assertEquals(at(today, 0) to at(LocalDate.of(2026, 11, 23), 0), ContinuumAgenda.window(ContinuumAgenda.dates(AgendaSpan.THIS_WEEK, today), zone))
        assertEquals(4, ContinuumAgenda.days(week, ContinuumAgenda.dates(AgendaSpan.THIS_WEEK, today), zone, now(7)).size)
    }

    @Test fun theWeekIsSpokenDayByDayWithPlacesAndOnlyWhatIsAhead() {
        val days = ContinuumAgenda.days(week, ContinuumAgenda.dates(AgendaSpan.THIS_WEEK, today), zone, now(8, 15))
        assertEquals("Diese Woche hast du sechs Termine: heute um 9 Uhr Zahnarzt (Praxis Dr. Beispiel) und um 9 Uhr 30 Besprechung (Büro); " +
            "morgen um 5 Uhr 30 Flug nach Berlin (Flughafen Linz) und um 14 Uhr Kundentermin; am Samstag um 9 Uhr Frühstück (Café Linz) " +
            "und um 10 Uhr 10 Friseur (Salon Wels). Ganztägig: Geburtstag Mama (Samstag).",
            ContinuumAgenda.spokenWeek(days, "Diese Woche", today))
        // Later the same day: what is over is counted, not read out.
        val evening = ContinuumAgenda.days(week, ContinuumAgenda.dates(AgendaSpan.THIS_WEEK, today), zone, now(18))
        assertTrue(ContinuumAgenda.spokenWeek(evening, "Diese Woche", today).startsWith("Diese Woche hast du vier Termine: morgen um 5 Uhr 30"))
        assertTrue(ContinuumAgenda.spokenWeek(evening, "Diese Woche", today).endsWith("Zwei Termine sind schon vorbei."))
        assertEquals("Nächste Woche steht nichts im Kalender.",
            ContinuumAgenda.spokenWeek(ContinuumAgenda.days(week, ContinuumAgenda.dates(AgendaSpan.NEXT_WEEK, today), zone, now(7)), "Nächste Woche", today))
        assertEquals(listOf("Do 09:00–10:00 Zahnarzt", "Do 09:30–11:00 Besprechung", "Fr 05:30–07:00 Flug nach Berlin", "+ 4 weitere"),
            ContinuumAgenda.weekLines(days, 4))
    }

    // ── The rules ───────────────────────────────────────────────────────────────────────────────────────────

    @Test fun everyRuleFindsExactlyWhatTheCalendarHolds() {
        val found = report(week)
        assertEquals(6, found.events)
        assertEquals(1, found.allDay)
        fun texts(kind: AgendaCheck.Kind) = found.findings.filter { it.kind == kind }.map { it.text }
        assertEquals(listOf("heute überschneiden sich Zahnarzt (9 Uhr bis 10 Uhr) und Besprechung (9 Uhr 30 bis 11 Uhr)"),
            texts(AgendaCheck.Kind.OVERLAP))
        assertEquals(listOf("am Samstag bleiben nur 10 Minuten für den Weg von Frühstück (Café Linz) zu Friseur (Salon Wels)"),
            texts(AgendaCheck.Kind.TIGHT_CHANGE))
        assertEquals(listOf("Zahnarzt beginnt in 45 Minuten (9 Uhr)", "Besprechung beginnt in gut einer Stunde (9 Uhr 30)"),
            texts(AgendaCheck.Kind.STARTS_SOON))
        assertEquals(listOf("morgen beginnt Flug nach Berlin schon um 5 Uhr 30"), texts(AgendaCheck.Kind.EARLY))
        assertEquals(listOf("Kundentermin morgen um 14 Uhr"), texts(AgendaCheck.Kind.NO_PLACE))
        assertEquals(emptyList<String>(), texts(AgendaCheck.Kind.LATE) + texts(AgendaCheck.Kind.NO_TIME))
        assertEquals(listOf(LocalDate.of(2026, 11, 22)), found.emptyDays)
    }

    @Test fun theSamePlaceEnoughTimeOrAMissingPlaceIsNoTightChange() {
        fun tight(vararg events: ContinuumCalendarEvent) = report(events.toList()).findings.count { it.kind == AgendaCheck.Kind.TIGHT_CHANGE }
        assertEquals(1, tight(event("A", 1, 9, location = "Linz"), event("B", 1, 10, location = "Wels")))
        assertEquals(1, tight(event("A", 1, 9, location = "Linz"), event("B", 1, 10, 15, location = "Wels")))
        assertEquals(0, tight(event("A", 1, 9, location = "Linz"), event("B", 1, 10, 20, location = "Wels")))
        assertEquals(0, tight(event("A", 1, 9, location = "Büro Linz"), event("B", 1, 10, location = "büro linz")))
        assertEquals(0, tight(event("A", 1, 9, location = "Linz"), event("B", 1, 10)))
        // Back to back at different places is said with „keine Zeit“.
        assertEquals("morgen bleibt keine Zeit für den Weg von A (Linz) zu B (Wels)",
            report(listOf(event("A", 1, 9, location = "Linz"), event("B", 1, 10, location = "Wels"))).findings.single { it.kind == AgendaCheck.Kind.TIGHT_CHANGE }.text)
    }

    @Test fun lateEntriesEntriesWithoutDurationAndAllDayOnesAreNamedAsWhatTheyAre() {
        val found = report(listOf(event("Konzert", 1, 21, location = "Posthof"), event("Erinnerung", 2, 12, lengthMinutes = 0, location = "zu Hause"),
            allDay("Urlaub", 1, days = 3)))
        assertEquals(listOf("morgen beginnt Konzert erst um 21 Uhr"), found.findings.filter { it.kind == AgendaCheck.Kind.LATE }.map { it.text })
        assertEquals(listOf("Erinnerung am Samstag"), found.findings.filter { it.kind == AgendaCheck.Kind.NO_TIME }.map { it.text })
        // An all-day entry over three days is one entry, and a day with it is not an empty day.
        assertEquals(1, found.allDay)
        assertEquals(listOf(today), found.emptyDays)
    }

    @Test fun whatIsOverIsNotCheckedAndSoonMeansTheNextTwoHoursOfToday() {
        val events = listOf(event("Früh", 0, 6), event("Mittag", 0, 12, location = "Kantine"), event("Morgen früh", 1, 8, location = "Büro"))
        val noon = report(events, now = now(11, 59))
        assertEquals(2, noon.events)
        assertEquals(listOf("Mittag beginnt jetzt (12 Uhr)"), noon.findings.filter { it.kind == AgendaCheck.Kind.STARTS_SOON }.map { it.text })
        assertTrue(noon.findings.none { it.text.contains("Früh ") })
        assertEquals(listOf("Mittag beginnt in zwei Stunden (12 Uhr)"),
            report(events, now = now(10)).findings.filter { it.kind == AgendaCheck.Kind.STARTS_SOON }.map { it.text })
        assertTrue(report(events, now = now(9, 59)).findings.none { it.kind == AgendaCheck.Kind.STARTS_SOON })
        // Tomorrow's entries are never „soon“, however early they are.
        assertTrue(report(events, now = now(23)).findings.none { it.kind == AgendaCheck.Kind.STARTS_SOON })
    }

    // ── Free time ───────────────────────────────────────────────────────────────────────────────────────────

    @Test fun freeTimeIsAtLeastTwoHoursBetweenEightAndTwentyAndTodayOnlyFromNow() {
        val found = report(week)
        fun block(day: Int, from: Int, until: Int, fromMinute: Int = 0) =
            AgendaCheck.FreeBlock(today.plusDays(day.toLong()), LocalTime.of(from, fromMinute), LocalTime.of(until, 0))
        assertEquals(listOf(block(0, 11, 20), block(1, 8, 14), block(1, 15, 20), block(2, 11, 20), block(3, 8, 20)), found.free)
        assertEquals("Frei – mindestens zwei Stunden am Stück zwischen 8 Uhr und 20 Uhr – bist du heute von 11 Uhr bis 20 Uhr; morgen von " +
            "8 Uhr bis 14 Uhr und von 15 Uhr bis 20 Uhr; am Samstag von 11 Uhr bis 20 Uhr; am Sonntag den ganzen Tag. Das gilt für das, " +
            "was im Kalender steht.", AgendaCheck.spokenFree(found, "diese Woche", today))
        // Today starts at the next quarter of an hour; late in the evening nothing of today is left.
        assertEquals(block(0, 13, 20, 30), report(emptyList(), AgendaSpan.TODAY, now(13, 16)).free.single())
        assertTrue(report(emptyList(), AgendaSpan.TODAY, now(18, 1)).free.isEmpty())
        // A gap of less than two hours is no free block, and a day that is full has none.
        val full = listOf(event("A", 1, 8, lengthMinutes = 240), event("B", 1, 13, 30, 390))
        assertTrue(report(full, AgendaSpan.TOMORROW).free.isEmpty())
        assertEquals("Zwischen 8 Uhr und 20 Uhr finde ich für morgen keinen freien Block von mindestens zwei Stunden.",
            AgendaCheck.spokenFree(report(full, AgendaSpan.TOMORROW), "morgen", today))
    }

    // ── The answer ──────────────────────────────────────────────────────────────────────────────────────────

    @Test fun theAnswerNamesWhatTheRulesFoundAndSaysThatACalendarCannotKnowMore() {
        assertEquals("Geprüft habe ich die sieben Einträge für diese Woche. Gleich zuerst: Zahnarzt beginnt in 45 Minuten (9 Uhr) und " +
            "Besprechung beginnt in gut einer Stunde (9 Uhr 30). Achtung: heute überschneiden sich Zahnarzt (9 Uhr bis 10 Uhr) und " +
            "Besprechung (9 Uhr 30 bis 11 Uhr); am Samstag bleiben nur 10 Minuten für den Weg von Frühstück (Café Linz) zu Friseur " +
            "(Salon Wels). Ungewöhnliche Zeiten: morgen beginnt Flug nach Berlin schon um 5 Uhr 30. Kein Ort ist eingetragen bei " +
            "Kundentermin morgen um 14 Uhr. Ein Eintrag ist ganztägig und hat deshalb keine Uhrzeit. Nichts eingetragen ist am Sonntag. " +
            "Mehr kann ich dazu nicht sagen: Ich sehe nur, was im Kalender steht – was dort fehlt, kann ich nicht wissen.",
            AgendaCheck.spoken(report(week), "diese Woche", today))
    }

    @Test fun aCleanWeekAndAnEmptyWeekAreSaidAsSuchAndNothingIsEverGuessed() {
        val clean = report(listOf(event("Zahnarzt", 1, 9, location = "Praxis"), event("Besprechung", 2, 14, location = "Büro")))
        assertEquals("Geprüft habe ich die zwei Einträge für diese Woche. Aufgefallen ist mir nichts: keine Überschneidung, kein enger " +
            "Ortswechsel, und jeder Termin hat Ort und Uhrzeit. Nichts eingetragen ist heute und am Sonntag. " + AgendaCheck.ONLY_CALENDAR,
            AgendaCheck.spoken(clean, "diese Woche", today))
        assertEquals("Für nächste Woche steht nichts im Kalender – da gibt es nichts, das ich prüfen könnte. " + AgendaCheck.ONLY_CALENDAR,
            AgendaCheck.spoken(report(emptyList(), AgendaSpan.NEXT_WEEK), "nächste Woche", today))
        // No sentence of an answer claims anything about what is missing.
        for (events in listOf(week, emptyList(), listOf(event("Zahnarzt", 1, 9, location = "Praxis")))) {
            val said = AgendaCheck.spoken(report(events), "diese Woche", today)
            assertTrue(said, said.endsWith(AgendaCheck.ONLY_CALENDAR))
            assertFalse(said, Regex("(?i)du hast .* vergessen|vergessen hast du|vermutlich|wahrscheinlich|vielleicht").containsMatchIn(said))
        }
    }

    @Test fun theSameCalendarAndTheSameClockAlwaysGiveTheSameAnswer() {
        val first = report(week)
        assertEquals(first, report(week.reversed()))
        assertEquals(AgendaCheck.spoken(first, "diese Woche", today), AgendaCheck.spoken(report(week.shuffled(java.util.Random(7))), "diese Woche", today))
        assertEquals(listOf("Zahnarzt beginnt in 45 Minuten (9 Uhr)", "Besprechung beginnt in gut einer Stunde (9 Uhr 30)",
            "heute überschneiden sich Zahnarzt (9 Uhr bis 10 Uhr) und Besprechung (9 Uhr 30 bis 11 Uhr)", "+ 3 weitere"), AgendaCheck.lines(first))
        assertEquals(listOf("nichts aufgefallen"), AgendaCheck.lines(report(listOf(event("Zahnarzt", 1, 9, location = "Praxis")))))
    }
}
