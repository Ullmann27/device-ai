package at.heinz.deviceai.briefing

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerLaneResult
import at.heinz.deviceai.briefing.ContinuumBriefingHarness.Companion.TODAY
import at.heinz.deviceai.briefing.ContinuumBriefingHarness.Companion.event
import at.heinz.deviceai.briefing.ContinuumBriefingHarness.Companion.moment
import at.heinz.deviceai.cognitive.InternetAccessMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The calendar over a week through the productive briefing director on a faked Activity (owner sentence 19.11.2026:
 * „Schau, welche Termine ich diese Woche habe, und sag mir, ob ich etwas vergessen könnte“): one read through the
 * same READ_CALENDAR path as the briefing, an answer built from the entries and the fixed rules, a typed outcome – and
 * neither a model nor the network. The phone's calendar provider is NOT part of this run.
 */
class ContinuumAgendaWeekFlowTest {
    private lateinit var h: ContinuumBriefingHarness

    @Before fun fresh() {
        // Thursday, 19.11.2026, a quarter past eight.
        h = ContinuumBriefingHarness(moment(TODAY, 8, 15))
        ContinuumBriefingBoard.clear()
    }

    private val week = listOf(
        event("Zahnarzt", 9, location = "Praxis Dr. Beispiel"), event("Besprechung", 9, 30, 90, location = "Büro"),
        event("Flug nach Berlin", 5, 30, 90, TODAY.plusDays(1), "Flughafen Linz"), event("Kundentermin", 14, date = TODAY.plusDays(1)),
        // Next week.
        event("Steuerberater", 10, date = TODAY.plusDays(5), location = "Kanzlei"))

    private val listed = "Diese Woche hast du vier Termine: heute um 9 Uhr Zahnarzt (Praxis Dr. Beispiel) und um 9 Uhr 30 Besprechung " +
        "(Büro); morgen um 5 Uhr 30 Flug nach Berlin (Flughafen Linz) und um 14 Uhr Kundentermin."

    @Test fun theWeekIsReadOnceFromTodayUntilSundayAndAnsweredFromTheEntries() {
        h.events = week
        for (question in listOf("Welche Termine habe ich diese Woche?", "Was steht diese Woche an?")) {
            assertTrue(question, h.say(question))
            assertEquals(question, listed, h.last)
        }
        // Thursday 00:00 to Monday 00:00 – one read per question, through the calendar job of the briefing.
        assertEquals(List(2) { moment(TODAY, 0).toEpochMilli() to moment(TODAY.plusDays(4), 0).toEpochMilli() }, h.calendarWindows)
        assertEquals(listOf("Kalender lesen", "Kalender lesen"), h.jobs)
        assertEquals("Termine diese Woche", ContinuumBriefingBoard.cards().single().title)
        assertEquals(OwnerLaneOutcome.Done(OwnerLaneResult.Agenda("diese Woche", events = 4, findings = 5, freeBlocks = 5)), h.outcomes.last())
        assertTrue(h.activities.last(), h.activities.last().startsWith("KALENDER · nur gelesen · 4 Einträge im Zeitraum · diese Woche"))
        assertTrue(h.activities.last(), h.activities.last().endsWith("ohne Modell"))
    }

    @Test fun nextWeekIsMondayToSunday() {
        h.events = week
        assertTrue(h.say("Welche Termine habe ich nächste Woche?"))
        assertEquals("Nächste Woche hast du einen Termin: am Dienstag um 10 Uhr Steuerberater (Kanzlei).", h.last)
        assertEquals(moment(TODAY.plusDays(4), 0).toEpochMilli() to moment(TODAY.plusDays(11), 0).toEpochMilli(), h.calendarWindows.single())
        h.events = emptyList()
        assertTrue(h.say("Was steht kommende Woche an?"))
        assertEquals("Nächste Woche steht nichts im Kalender.", h.last)
    }

    /** The owner's sentence: the week, and what the fixed rules notice – never a guess. */
    @Test fun theOwnersSentenceListsTheWeekAndSaysWhatStandsOut() {
        h.events = week
        assertTrue(h.say("Schau, welche Termine ich diese Woche habe, und sag mir, ob ich etwas vergessen könnte."))
        assertEquals(listed + " Geprüft habe ich die vier Einträge für diese Woche. Gleich zuerst: Zahnarzt beginnt in 45 Minuten (9 Uhr) " +
            "und Besprechung beginnt in gut einer Stunde (9 Uhr 30). Achtung: heute überschneiden sich Zahnarzt (9 Uhr bis 10 Uhr) und " +
            "Besprechung (9 Uhr 30 bis 11 Uhr). Ungewöhnliche Zeiten: morgen beginnt Flug nach Berlin schon um 5 Uhr 30. Kein Ort ist " +
            "eingetragen bei Kundentermin morgen um 14 Uhr. Nichts eingetragen ist am Samstag und am Sonntag. " + AgendaCheck.ONLY_CALENDAR, h.last)
        assertEquals(h.spoken.last(), h.shown.last())
        val card = ContinuumBriefingBoard.cards().single()
        assertEquals("Terminprüfung diese Woche", card.title)
        assertEquals(ContinuumBriefingBoard.State.ATTENTION, card.state)
        assertEquals(OwnerLaneOutcome.Done(OwnerLaneResult.Agenda("diese Woche", events = 4, findings = 5, freeBlocks = 5)), h.outcomes.single())
        // A week without entries is said as such – „vergessen“ is not answered with a guess.
        h.events = emptyList()
        assertTrue(h.say("Habe ich nächste Woche etwas vergessen?"))
        assertEquals("Nächste Woche steht nichts im Kalender. Für nächste Woche steht nichts im Kalender – da gibt es nichts, das ich prüfen " +
            "könnte. " + AgendaCheck.ONLY_CALENDAR, h.last)
    }

    @Test fun freeTimeComesFromTheSameEntries() {
        h.events = week
        assertTrue(h.say("Wo habe ich diese Woche noch Zeit?"))
        assertEquals("Frei – mindestens zwei Stunden am Stück zwischen 8 Uhr und 20 Uhr – bist du heute von 11 Uhr bis 20 Uhr; morgen von " +
            "8 Uhr bis 14 Uhr und von 15 Uhr bis 20 Uhr; am Samstag den ganzen Tag; am Sonntag den ganzen Tag. Das gilt für das, was im " +
            "Kalender steht.", h.last)
        assertTrue(h.say("Wann habe ich heute noch Zeit?"))
        assertEquals("Frei – mindestens zwei Stunden am Stück zwischen 8 Uhr und 20 Uhr – bist du heute von 11 Uhr bis 20 Uhr. Das gilt " +
            "für das, was im Kalender steht.", h.last)
        assertEquals(moment(TODAY, 0).toEpochMilli() to moment(TODAY.plusDays(1), 0).toEpochMilli(), h.calendarWindows.last())
    }

    @Test fun withoutTheCalendarPermissionItSaysSoAndNamesTheSettingsPath() {
        h.calendarGranted = false
        h.calendarAnswer = false
        assertTrue(h.say("Schau, welche Termine ich diese Woche habe, und sag mir, ob ich etwas vergessen könnte."))
        // Explained and asked through the briefing's own path, once; denied: said plainly, with the way to switch it on.
        assertEquals(listOf(ContinuumBriefingDirector.CALENDAR_EXPLANATION), h.calendarExplanations)
        assertEquals(ContinuumBriefingText.CALENDAR_DENIED, h.last)
        assertTrue(h.last.contains("Apps → Continuum → Berechtigungen"))
        assertTrue(h.calendarWindows.isEmpty())
        assertEquals(OwnerLaneOutcome.NeedsOwner(OwnerLaneOutcome.Need.GRANT, ContinuumBriefingText.CALENDAR_DENIED), h.outcomes.single())
        // Granted on the next question: read at once.
        h.calendarAnswer = true
        h.events = week
        assertTrue(h.say("Was steht diese Woche an?"))
        assertEquals(listed, h.last)
        // A calendar that cannot be read is a failure, not an empty week.
        h.calendarFailure = "Provider nicht erreichbar"
        assertTrue(h.say("Habe ich diese Woche etwas vergessen?"))
        assertEquals(ContinuumBriefingText.CALENDAR_FAILED, h.last)
        assertEquals(OwnerLaneOutcome.Failed(ContinuumBriefingText.CALENDAR_FAILED), h.outcomes.last())
    }

    /** Calendar entries are private: no web consent is asked, nothing is fetched, and the lane has no way to a model. */
    @Test fun nothingOfTheCalendarLeavesTheDevice() {
        h.mode = InternetAccessMode.ALWAYS_ON
        h.settings.place = "Linz"
        h.events = week
        for (question in listOf("Welche Termine habe ich diese Woche?", "Habe ich diese Woche etwas vergessen?",
            "Wo habe ich nächste Woche noch Zeit?"))
            assertTrue(question, h.say(question))
        assertTrue(h.webExplanations.isEmpty())
        assertTrue(h.weatherRequests.isEmpty())
        assertTrue(h.submitted.isEmpty())
        assertEquals(listOf("Kalender lesen", "Kalender lesen", "Kalender lesen"), h.jobs)
        // The host interface of this lane offers no generation at all: the answer can only be the director's own text.
        assertFalse(ContinuumBriefingHost::class.java.methods.any { it.name.contains("generate", ignoreCase = true) || it.name.contains("model", ignoreCase = true) })
    }

    @Test fun aSentenceThatIsNotThisOrderIsLeftAlone() {
        for (other in listOf("Schau, welche Termine ich diese Woche habe, und verschieb den Zahnarzt", "Trag für diese Woche einen Termin ein",
            "Habe ich etwas vergessen?", "Hat Anna diese Woche Zeit?", "Diese Woche war anstrengend")) {
            assertFalse(other, h.say(other))
        }
        assertTrue(h.chat.isEmpty() && h.outcomes.isEmpty() && h.calendarWindows.isEmpty())
    }
}
