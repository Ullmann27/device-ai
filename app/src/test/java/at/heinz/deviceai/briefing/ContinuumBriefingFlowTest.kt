package at.heinz.deviceai.briefing

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerLaneResult
import at.heinz.deviceai.OwnerSavedFile
import at.heinz.deviceai.briefing.ContinuumBriefingHarness.Companion.LINZ
import at.heinz.deviceai.briefing.ContinuumBriefingHarness.Companion.TODAY
import at.heinz.deviceai.briefing.ContinuumBriefingHarness.Companion.moment
import at.heinz.deviceai.briefing.ContinuumBriefingHarness.Companion.event
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerNewsCommand
import at.heinz.deviceai.cognitive.WorkRecall
import at.heinz.deviceai.sending.OwnerContactPhone
import at.heinz.deviceai.systemintelligence.SystemIntelligenceCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Whole owner turns through the productive director, parsers and wording on a faked Activity: the daily briefing in
 * its combinations, the once-per-day offer, the next-step offers, the weather and place orders, calling and the
 * system check. The phone itself (calendar provider, dialer, Open-Meteo) is NOT part of this run.
 */
class ContinuumBriefingFlowTest {
    private lateinit var h: ContinuumBriefingHarness

    @Before fun fresh() {
        h = ContinuumBriefingHarness()
        ContinuumBriefingBoard.clear()
    }

    private val dayOfWork = listOf(event("Besprechung mit Anna", 14, 30), event("Zahnarzt", 9),
        event("Flug nach Wien", 8, date = TODAY.plusDays(1)), event("Abendessen", 19, date = TODAY.plusDays(1)))

    // ── The briefing ────────────────────────────────────────────────────────────────────────────────────────

    @Test fun theFullBriefingIsOneShortSpokenSummaryWithCardsAndAChoice() {
        h.mode = InternetAccessMode.ALWAYS_ON
        h.settings.place = "Linz"
        h.events = dayOfWork
        h.work = listOf("Vertrag.pdf: Entwurf prüfen und speichern")
        assertTrue(h.say("Was steht heute an?"))
        assertEquals("> Was steht heute an?", h.chat.first())
        val spoken = h.spoken.single()
        assertEquals("Heinz, heute ist Donnerstag, der 19. November. " +
            "Heute hast du zwei Termine: um 9 Uhr Zahnarzt und um 14 Uhr 30 Besprechung mit Anna. Morgen früh: um 8 Uhr Flug nach Wien. " +
            "In Linz sind es gerade 9 Grad, überwiegend klar. Heute 6 bis 12 Grad, leichte Regenschauer, Regenwahrscheinlichkeit 78 Prozent. " +
            "Offen ist noch eine Arbeit: Vertrag.pdf: Entwurf prüfen und speichern. " +
            "Soll ich die offene Arbeit zeigen oder die Nachrichten von heute zusammenfassen?", spoken)
        // Suited to listening: roughly 20 to 30 seconds at a calm speaking rate.
        assertTrue(spoken.split(' ').size.toString(), spoken.split(' ').size in 45..85)
        val cards = ContinuumBriefingBoard.cards()
        assertEquals(listOf("calendar", "weather", "tasks"), cards.map { it.kind.code })
        assertEquals(listOf("09:00–10:00 Zahnarzt", "14:30–15:30 Besprechung mit Anna"), cards[0].lines)
        assertEquals("Wetter · Linz, Oberösterreich, Österreich", cards[1].title)
        assertEquals("Quelle: Open-Meteo", cards[1].lines.last())
        assertEquals(ContinuumBriefingBoard.State.ATTENTION, cards[2].state)
        assertTrue(cards.all { it.lines.size in 1..4 && it.id.isNotBlank() && it.title.isNotBlank() })
        // The chat shows the same cards as plain text below the spoken part.
        assertEquals(spoken + "\n\n" + ContinuumBriefingBoard.chat(cards), h.shown.single())
        assertTrue(h.last, h.last.contains("Termine heute\n· 09:00–10:00 Zahnarzt\n· 14:30–15:30 Besprechung mit Anna"))
        assertTrue(h.last, h.last.contains("! Offene Arbeit\n· Vertrag.pdf: Entwurf prüfen und speichern"))
        // One calendar read from midnight to tomorrow noon, one weather read for the stored place.
        assertEquals(listOf(moment(TODAY, 0).toEpochMilli() to moment(TODAY.plusDays(1), 12).toEpochMilli()), h.calendarWindows)
        assertEquals(listOf(ContinuumWeatherRequest("Linz", null)), h.weatherRequests)
        assertEquals(listOf("work", "news"), ContinuumBriefingBoard.offers().map { it.id })
        assertEquals(TODAY, h.settings.lastBriefingDay)
        // The coordinates found are kept: the next time the place name is not sent again.
        assertEquals(LINZ, h.settings.placeFound)
        assertTrue(h.say("Gib mir mein Briefing"))
        assertEquals(ContinuumWeatherRequest("Linz", LINZ), h.weatherRequests.last())
    }

    @Test fun anEmptyDayWithoutPlaceAndInternetSaysExactlyThat() {
        assertTrue(h.say("Guten Morgen, gib mir mein Briefing"))
        assertEquals("Heinz, heute ist Donnerstag, der 19. November. Heute steht nichts im Kalender. " +
            "Für das Wetter fehlt mir dein Ort – sag zum Beispiel: Mein Ort ist Linz. Offen ist nichts.", h.spoken.single())
        assertTrue(h.weatherRequests.isEmpty() && h.webExplanations.isEmpty())
        assertTrue("nothing to choose: no question", ContinuumBriefingBoard.offers().isEmpty())
        val cards = ContinuumBriefingBoard.cards()
        assertEquals(listOf("nichts im Kalender"), cards.single { it.kind == ContinuumBriefingBoard.Kind.CALENDAR }.lines)
        assertEquals(listOf("kein Ort festgelegt"), cards.single { it.kind == ContinuumBriefingBoard.Kind.WEATHER }.lines)
        assertEquals(listOf("nichts offen"), cards.single { it.kind == ContinuumBriefingBoard.Kind.TASKS }.lines)
        assertTrue(cards.all { it.state == ContinuumBriefingBoard.State.INFO })
        // Without a name the briefing is not addressed to anyone.
        h.owner = null
        assertTrue(h.say("Briefing"))
        assertTrue(h.spoken.last(), h.spoken.last().startsWith("Heute ist Donnerstag, der 19. November."))
    }

    @Test fun withoutInternetOrConsentThereIsSimplyNoWeather() {
        h.settings.place = "Linz"
        // OFF: nothing is asked and nothing is sent.
        assertTrue(h.say("Was steht heute an?"))
        assertTrue(h.spoken.last(), h.spoken.last().contains("Wetter gibt es keines: Der Internetzugang ist ausgeschaltet."))
        assertTrue(h.weatherRequests.isEmpty() && h.webExplanations.isEmpty())
        assertTrue(h.activities.toString(), h.activities.contains("WETTER · Internetzugang AUS · keine Anfrage gesendet"))
        assertEquals(listOf("nicht abgerufen · Internetzugang aus"), ContinuumBriefingBoard.cards().single { it.kind.code == "weather" }.lines)
        // ASK, declined: the gate's notice, then the briefing without a weather sentence.
        h.mode = InternetAccessMode.ASK
        h.webAnswer = false
        assertTrue(h.say("Was steht heute an?"))
        assertEquals(ContinuumBriefingText.WEATHER_NOT_GRANTED, h.chat[h.chat.size - 2])
        assertFalse(h.spoken.last(), h.spoken.last().contains("Wetter") || h.spoken.last().contains("Grad"))
        assertTrue(h.weatherRequests.isEmpty())
        assertEquals(listOf("nicht abgerufen · nicht freigegeben"), ContinuumBriefingBoard.cards().single { it.kind.code == "weather" }.lines)
        // The owner is told what the grant is used for and what is transmitted.
        assertTrue(h.webExplanations.last(), h.webExplanations.last().contains("Open-Meteo") && h.webExplanations.last().contains("„Linz“") &&
            h.webExplanations.last().contains("kein Konto"))
        // ASK, granted.
        h.webAnswer = true
        assertTrue(h.say("Was steht heute an?"))
        assertTrue(h.spoken.last(), h.spoken.last().contains("In Linz sind es gerade 9 Grad"))
        assertEquals(1, h.weatherRequests.size)
    }

    @Test fun aFailingWeatherServiceOrAnUnknownPlaceIsSaidPlainly() {
        h.mode = InternetAccessMode.ALWAYS_ON
        h.settings.place = "Xyz"
        h.weather = { ContinuumWeatherRead.PlaceUnknown(it.placeName) }
        assertTrue(h.say("Was steht heute an?"))
        assertTrue(h.spoken.last(), h.spoken.last().contains("Den Ort „Xyz“ kennt der Wetterdienst nicht."))
        assertNull(h.settings.placeFound)
        h.weather = { ContinuumWeatherRead.Failed("keine Verbindung zum Wetterdienst") }
        assertTrue(h.say("Was steht heute an?"))
        assertTrue(h.spoken.last(), h.spoken.last().contains("Das Wetter konnte ich nicht abrufen: keine Verbindung zum Wetterdienst."))
        h.weather = { error("kaputt") }
        assertTrue(h.say("Was steht heute an?"))
        assertTrue(h.spoken.last(), h.spoken.last().contains("Das Wetter konnte ich nicht abrufen: der Wetterdienst war nicht erreichbar."))
        assertFalse(h.spoken.last(), h.spoken.last().contains("Grad"))
        // A stopped job says nothing more – no half briefing.
        val before = h.chat.size
        h.weatherStopped = true
        assertTrue(h.say("Was steht heute an?"))
        assertEquals(listOf("> Was steht heute an?"), h.chat.drop(before))
    }

    @Test fun theCalendarPermissionIsExplainedOnceAndNotGrantedIsSaidPlainly() {
        h.calendarGranted = false
        h.calendarAnswer = false
        assertTrue(h.say("Was steht heute an?"))
        assertEquals(listOf(ContinuumBriefingDirector.CALENDAR_EXPLANATION), h.calendarExplanations)
        assertTrue(h.calendarExplanations.single().contains("nur lesen") && h.calendarExplanations.single().contains("bleiben auf dem Gerät"))
        assertTrue(h.spoken.last(), h.spoken.last().contains("Termine kann ich dir nicht nennen, der Kalenderzugriff ist nicht erlaubt."))
        assertEquals(ContinuumBriefingBoard.State.ATTENTION, ContinuumBriefingBoard.cards().single { it.kind.code == "calendar" }.state)
        assertTrue(h.calendarWindows.isEmpty())
        // The next briefing does not ask again …
        h.calendarAnswer = null
        assertTrue(h.say("Gib mir mein Briefing"))
        assertEquals(1, h.calendarExplanations.size)
        // … the explicit question for appointments does, and a grant is used at once.
        h.calendarAnswer = true
        h.events = listOf(event("Zahnarzt", 9, location = "Praxis Dr. Beispiel"))
        assertTrue(h.say("Welche Termine habe ich heute?"))
        assertEquals(2, h.calendarExplanations.size)
        assertEquals("Heute hast du einen Termin: um 9 Uhr Zahnarzt (Praxis Dr. Beispiel).", h.last)
        // Denied again on the explicit question: how to switch it on.
        h.calendarGranted = false
        h.calendarAnswer = false
        assertTrue(h.say("Habe ich morgen Termine?"))
        assertEquals(ContinuumBriefingText.CALENDAR_DENIED, h.last)
    }

    @Test fun appointmentsOfTodayAndTomorrowAreAnsweredFromTheirOwnDay() {
        h.events = dayOfWork
        assertTrue(h.say("Welche Termine habe ich morgen?"))
        assertEquals("Morgen hast du zwei Termine: um 8 Uhr Flug nach Wien und um 19 Uhr Abendessen.", h.last)
        assertEquals(moment(TODAY.plusDays(1), 0).toEpochMilli() to moment(TODAY.plusDays(2), 0).toEpochMilli(), h.calendarWindows.last())
        assertEquals("Termine morgen", ContinuumBriefingBoard.cards().single().title)
        h.events = emptyList()
        assertTrue(h.say("Habe ich heute Termine?"))
        assertEquals("Heute steht nichts im Kalender.", h.last)
        h.calendarFailure = "Provider nicht erreichbar"
        assertTrue(h.say("Meine Termine"))
        assertEquals(ContinuumBriefingText.CALENDAR_FAILED, h.last)
        assertTrue(h.activities.last(), h.activities.last().startsWith("KALENDER · nur gelesen"))
    }

    @Test fun openMessagesAndSavedFilesBelongToTheOpenItems() {
        h.messages = ContinuumOpenMessages { listOf(ContinuumOpenMessage("Anna", "WhatsApp", "Lies die Nachricht von Anna vor"),
            ContinuumOpenMessage("Thomas", "Gmail")) }
        val saved = OwnerSavedFile("Nachrichten-2026-11-19.pdf", "Download", "content://x/1", "application/pdf")
        val result = OwnerDeliverable(OwnerDeliverable.Kind.NEWS_BRIEFING, "Nachrichten", TODAY, listOf("Text"))
        h.director.laneOutcome(OwnerLaneOutcome.Done(OwnerLaneResult.FileSaved(saved, result, "Download", true)))
        assertTrue(h.say("Was steht heute an?"))
        assertTrue(h.spoken.last(), h.spoken.last().contains("Zwei Nachrichten sind offen, von Anna und Thomas. " +
            "Gespeichert, aber von mir noch nicht verschickt: Nachrichten-2026-11-19.pdf."))
        val cards = ContinuumBriefingBoard.cards()
        assertEquals(listOf("Anna · WhatsApp", "Thomas · Gmail"), cards.single { it.kind.code == "messages" }.lines)
        assertEquals(listOf("gespeichert, nicht verschickt: Nachrichten-2026-11-19.pdf"), cards.single { it.kind.code == "tasks" }.lines)
        // Only a message the app can open is offered.
        assertEquals(listOf("die Nachricht von Anna öffnen"), ContinuumBriefingBoard.offers().map { it.label })
        assertTrue(h.spoken.last(), h.spoken.last().endsWith("Soll ich die Nachricht von Anna öffnen?"))
        // Handed on: no longer open. A list that is wired but empty says nothing about messages.
        h.director.laneOutcome(OwnerLaneOutcome.Done(OwnerLaneResult.Delivered("Gmail", "anna@example.org", saved,
            OwnerLaneResult.Delivered.State.HANDED_OVER)))
        h.messages = ContinuumOpenMessages { emptyList() }
        assertTrue(h.say("Was steht heute an?"))
        assertTrue(h.spoken.last(), h.spoken.last().endsWith("Offen ist nichts."))
        assertEquals(listOf("keine"), ContinuumBriefingBoard.cards().single { it.kind.code == "messages" }.lines)
        // A file saved more than a day ago is no longer mentioned.
        h.director.laneOutcome(OwnerLaneOutcome.Done(OwnerLaneResult.FileSaved(saved, result, "Download", true)))
        h.clock = moment(TODAY.plusDays(2), 7)
        assertTrue(h.say("Was steht heute an?"))
        assertFalse(h.spoken.last(), h.spoken.last().contains("Gespeichert"))
    }

    @Test fun aSeriousDeviceProblemIsPartOfTheBriefingAndASmallOneOnlyACard() {
        h.facts = h.facts.copy(batteryPercent = 4)
        assertTrue(h.say("Was steht heute an?"))
        assertTrue(h.spoken.last(), h.spoken.last().endsWith("Noch etwas: der Akku ist fast leer, nur noch 4 Prozent."))
        assertEquals(listOf("Akku: 4 % · lädt nicht"), ContinuumBriefingBoard.cards().single { it.kind.code == "system" }.lines)
        h.facts = h.facts.copy(batteryPercent = 82, shizukuReady = false)
        assertTrue(h.say("Was steht heute an?"))
        assertFalse(h.spoken.last(), h.spoken.last().contains("Shizuku"))
        assertEquals(listOf("Shizuku: nicht erreichbar"), ContinuumBriefingBoard.cards().single { it.kind.code == "system" }.lines)
        h.facts = h.facts.copy(shizukuReady = true)
        assertTrue(h.say("Was steht heute an?"))
        assertTrue("no system card when nothing is wrong", ContinuumBriefingBoard.cards().none { it.kind.code == "system" })
    }

    // ── News part ───────────────────────────────────────────────────────────────────────────────────────────

    @Test fun theNewsPartReusesTheNewsLaneAndIsOffUnlessAskedOrSet() {
        h.mode = InternetAccessMode.ALWAYS_ON
        assertTrue(h.say("Was steht heute an?"))
        assertTrue("off by default", h.submitted.isEmpty())
        assertTrue(h.say("Gib mir mein Briefing mit Nachrichten"))
        assertTrue(h.spoken.last(), h.spoken.last().endsWith("Die Nachrichten folgen gleich."))
        assertEquals(listOf(ContinuumBriefingDirector.ORDER_NEWS), h.submitted)
        assertNotNull("the dispatched order is one the news lane takes", OwnerNewsCommand.parse(h.submitted.single(), "Jarvis"))
        assertTrue("no choice is offered while the news follow", ContinuumBriefingBoard.offers().isEmpty())
        assertEquals(listOf("Überblick folgt als eigene Antwort"), ContinuumBriefingBoard.cards().single { it.kind.code == "news" }.lines)
        // Standing setting.
        assertTrue(h.say("Nimm die Nachrichten ins Briefing auf"))
        assertTrue(h.last, h.last.startsWith("Gut, die Nachrichten gehören ab jetzt zu deinem Briefing."))
        assertTrue(h.say("Briefing"))
        assertEquals(2, h.submitted.size)
        // Without internet there are no news, and that is said.
        h.mode = InternetAccessMode.OFF
        assertTrue(h.say("Briefing"))
        assertTrue(h.spoken.last(), h.spoken.last().endsWith("Nachrichten gibt es keine: Der Internetzugang ist ausgeschaltet."))
        assertEquals(2, h.submitted.size)
        assertTrue(h.say("Lass die Nachrichten im Briefing weg"))
        h.mode = InternetAccessMode.ALWAYS_ON
        assertTrue(h.say("Briefing"))
        assertEquals(2, h.submitted.size)
    }

    @Test fun theNewsAreNotStartedWhenTheOwnerWentOnInTheMeantime() {
        h.mode = InternetAccessMode.ALWAYS_ON
        assertTrue(h.director.handle("Gib mir mein Briefing mit Nachrichten"))
        // Before the briefing was spoken to its end, the owner says something else.
        h.acceptOwnerTurn("Stopp")
        h.drain()
        assertTrue(h.submitted.isEmpty())
        assertTrue(h.director.handle("Gib mir mein Briefing mit Nachrichten"))
        h.busy = true
        h.drain()
        assertTrue(h.submitted.isEmpty())
    }

    // ── The once-per-day offer ──────────────────────────────────────────────────────────────────────────────

    @Test fun theOfferIsMadeOncePerDayAndAYesStartsTheBriefing() {
        assertEquals(ContinuumBriefingDirector.OFFER_TEXT, h.director.dueOffer(h.clock))
        assertEquals("dueOffer only reads", ContinuumBriefingDirector.OFFER_TEXT, h.director.dueOffer(h.clock))
        assertEquals(ContinuumBriefingDirector.OFFER_TEXT, h.director.briefingOffer())
        assertNull("consumed for today", h.director.briefingOffer())
        assertNull(h.director.dueOffer(moment(TODAY, 21)))
        assertTrue("the hook itself says nothing", h.chat.isEmpty() && h.spoken.isEmpty())
        assertTrue(h.say("ja"))
        assertEquals("> ja", h.chat.first())
        assertTrue(h.spoken.single(), h.spoken.single().startsWith("Heinz, heute ist Donnerstag, der 19. November."))
        // The next day it is offered again; a „nein“ is taken as the answer.
        h.clock = moment(TODAY.plusDays(1), 6, 30)
        assertEquals(ContinuumBriefingDirector.OFFER_TEXT, h.director.briefingOffer())
        assertTrue(h.say("nein danke"))
        assertEquals("In Ordnung. Sag einfach „Gib mir mein Briefing“, wenn du es doch möchtest.", h.last)
        assertNull(h.director.dueOffer(moment(TODAY.plusDays(1), 9)))
    }

    @Test fun noOfferBeforeTheHourAfterABriefingOrWhenSwitchedOff() {
        assertNull("too early in the night", h.director.dueOffer(moment(TODAY, 3)))
        assertNotNull(h.director.dueOffer(moment(TODAY, 5)))
        assertTrue(h.say("Jeden Morgen um 7 das Briefing anbieten"))
        assertTrue(h.last, h.last.startsWith("Gut, ich biete dir das Briefing einmal am Tag an – ab 7 Uhr, sobald du dich das erste Mal meldest."))
        assertTrue("the Android limit is said, not hidden", h.last.contains("Android lässt eine App im Hintergrund nicht ungefragt sprechen"))
        assertNull(h.director.dueOffer(moment(TODAY, 6, 59)))
        assertNotNull(h.director.dueOffer(moment(TODAY, 7)))
        // A briefing he asked for himself counts: no offer afterwards on that day.
        assertTrue(h.say("Was steht heute an?"))
        assertNull(h.director.dueOffer(moment(TODAY, 8)))
        assertNotNull(h.director.dueOffer(moment(TODAY.plusDays(1), 8)))
        assertTrue(h.say("Biete mir kein Briefing mehr an"))
        assertNull(h.director.dueOffer(moment(TODAY.plusDays(1), 8)))
        assertNull(h.director.briefingOffer())
        assertTrue(h.say("Biete mir das Briefing wieder an"))
        assertNotNull(h.director.dueOffer(moment(TODAY.plusDays(1), 8)))
        assertEquals("the hour he chose is kept", 7, h.settings.offerFromHour)
    }

    @Test fun aYesWithoutAnOfferOrAfterAnotherTurnIsNotABriefingOrder() {
        assertFalse(h.say("ja"))
        assertEquals(ContinuumBriefingDirector.OFFER_TEXT, h.director.briefingOffer())
        // Another lane took a turn in between: the offer is no longer what „ja“ answers.
        h.acceptOwnerTurn("Wie spät ist es?")
        assertFalse(h.say("ja"))
        // Too long ago.
        h.clock = moment(TODAY.plusDays(1), 7)
        assertNotNull(h.director.briefingOffer())
        h.clock = moment(TODAY.plusDays(1), 7, 6)
        assertFalse(h.say("ja"))
        // runBriefing starts it without any owner turn.
        h.director.runBriefing()
        assertTrue(h.spoken.last(), h.spoken.last().startsWith("Heinz, heute ist "))
        assertTrue(h.chat.none { it.startsWith("> ja") })
    }

    // ── „Entscheide du die Aktion“ ──────────────────────────────────────────────────────────────────────────

    private fun briefedWithChoices() {
        h.mode = InternetAccessMode.ALWAYS_ON
        h.events = listOf(event("Zahnarzt", 14, location = "Praxis Dr. Beispiel"))
        h.work = listOf("Vertrag.pdf: Entwurf prüfen und speichern")
        h.messages = ContinuumOpenMessages { listOf(ContinuumOpenMessage("Anna", "WhatsApp", "Lies die Nachricht von Anna vor")) }
        assertTrue(h.say("Was steht heute an?"))
    }

    @Test fun atMostThreeStepsAreOfferedAndEachIsAnOrderALaneReallyTakes() {
        briefedWithChoices()
        val offers = ContinuumBriefingBoard.offers()
        assertEquals(listOf("appointments", "message", "work"), offers.map { it.id })
        assertTrue(h.spoken.last(), h.spoken.last().endsWith("Soll ich den Termin um 14 Uhr mit Ort vorlesen, die Nachricht von Anna öffnen " +
            "oder die offene Arbeit zeigen?"))
        assertTrue(ContinuumBriefingCommands.parse(ContinuumBriefingDirector.ORDER_APPOINTMENTS, "Jarvis") is ContinuumBriefingCommand.Appointments)
        assertNotNull(WorkRecall.parse(ContinuumBriefingDirector.ORDER_OPEN_WORK))
        assertTrue(WorkRecall.parse(ContinuumBriefingDirector.ORDER_OPEN_WORK)!!.openOnly)
        assertNotNull(OwnerNewsCommand.parse(ContinuumBriefingDirector.ORDER_NEWS, "Jarvis"))
        assertTrue(SystemIntelligenceCommands.isDiagnosisRequest(ContinuumBriefingDirector.ORDER_DIAGNOSIS))
        assertEquals(listOf(ContinuumBriefingDirector.ORDER_APPOINTMENTS, "Lies die Nachricht von Anna vor",
            ContinuumBriefingDirector.ORDER_OPEN_WORK), offers.map { it.order })
    }

    @Test fun aChosenStepIsDispatchedAsIfTheOwnerHadSaidItAndNothingHappensWithoutAChoice() {
        briefedWithChoices()
        assertTrue("nothing is dispatched by itself", h.submitted.isEmpty() && h.dialed.isEmpty())
        val turns = h.ownerTurn
        assertTrue(h.say("die erste"))
        assertEquals(listOf(ContinuumBriefingDirector.ORDER_APPOINTMENTS), h.submitted)
        // The order itself is the owner's turn that is shown and answered – here by this lane.
        assertEquals(turns + 1, h.ownerTurn)
        assertEquals("> " + ContinuumBriefingDirector.ORDER_APPOINTMENTS, h.chat[h.chat.size - 2])
        assertEquals("Heute hast du einen Termin: um 14 Uhr Zahnarzt (Praxis Dr. Beispiel).", h.last)
        assertEquals(1, h.inputCleared)
        assertTrue(ContinuumBriefingBoard.offers().isEmpty())
        assertFalse("the offer is over", h.say("die zweite"))
    }

    @Test fun byKeywordByTapDeclinedOrIgnored() {
        briefedWithChoices()
        assertTrue(h.say("die offene Arbeit"))
        assertEquals(listOf(ContinuumBriefingDirector.ORDER_OPEN_WORK), h.submitted)
        briefedWithChoices()
        assertTrue(h.director.choose("message"))
        h.drain()
        assertEquals("Lies die Nachricht von Anna vor", h.submitted.last())
        assertFalse("the same tap twice does nothing", h.director.choose("message"))
        briefedWithChoices()
        assertTrue(h.say("nein"))
        assertEquals("In Ordnung.", h.last)
        assertEquals(2, h.submitted.size)
        briefedWithChoices()
        assertTrue(h.say("ja"))
        assertEquals("Welchen Schritt meinst du – den ersten, den zweiten oder den dritten?", h.last)
        assertTrue(h.say("den dritten"))
        assertEquals(ContinuumBriefingDirector.ORDER_OPEN_WORK, h.submitted.last())
        // Any other sentence is whatever it is without the offer – and ends the offer.
        briefedWithChoices()
        assertFalse(h.say("Wie spät ist es?"))
        assertTrue(ContinuumBriefingBoard.offers().isEmpty())
        assertFalse(h.say("die erste"))
        assertEquals(3, h.submitted.size)
        // A tap while a job runs is refused.
        briefedWithChoices()
        h.busy = true
        assertFalse(h.director.choose("work"))
    }

    // ── Weather and place orders ────────────────────────────────────────────────────────────────────────────

    @Test fun thePlaceIsAnOwnerSettingAndNothingIsSentWhenItIsSet() {
        h.mode = InternetAccessMode.ALWAYS_ON
        assertTrue(h.say("Mein Ort ist Linz"))
        assertEquals("Gut, Linz ist jetzt dein Ort für das Wetter. Nachgeschlagen wird er erst, wenn du nach dem Wetter fragst.", h.last)
        assertEquals("Linz", h.settings.place)
        assertTrue(h.weatherRequests.isEmpty() && h.webExplanations.isEmpty())
        assertTrue(h.say("Wie wird das Wetter morgen?"))
        assertEquals("Morgen in Linz: 8 bis 12 Grad, leichte Regenschauer, Regenwahrscheinlichkeit 65 Prozent.", h.spoken.last())
        assertEquals(h.spoken.last() + "\n\nQuelle: Open-Meteo · Linz, Oberösterreich, Österreich", h.last)
        // A new place forgets the coordinates of the old one.
        assertEquals(LINZ, h.settings.placeFound)
        assertTrue(h.say("Mein Ort ist Graz"))
        assertNull(h.settings.placeFound)
    }

    @Test fun aPlaceNamedInTheQuestionIsUsedOnceAndNotStored() {
        h.mode = InternetAccessMode.ALWAYS_ON
        h.settings.place = "Linz"
        val wien = ContinuumWeatherPlace("Wien", "Wien, Österreich", 48.2085, 16.3721)
        h.weather = { ContinuumWeatherRead.Report(ContinuumBriefingHarness.LINZ_REPORT.copy(place = wien)) }
        assertTrue(h.say("Wie wird das Wetter in Wien?"))
        assertEquals(listOf(ContinuumWeatherRequest("Wien", null)), h.weatherRequests)
        assertTrue(h.spoken.last(), h.spoken.last().startsWith("In Wien sind es gerade 9 Grad"))
        assertEquals("Linz", h.settings.place)
        assertNull("the looked-up place of a one-off question is not kept for his own place", h.settings.placeFound)
    }

    @Test fun withoutAPlaceTheQuestionIsAnsweredOnceHeNamesIt() {
        h.mode = InternetAccessMode.ALWAYS_ON
        assertTrue(h.say("Wie wird das Wetter morgen?"))
        assertEquals(ContinuumBriefingText.NO_PLACE, h.last)
        assertTrue(h.weatherRequests.isEmpty())
        assertTrue(h.say("Mein Ort ist Linz"))
        assertEquals("Linz", h.settings.place)
        assertEquals("Morgen in Linz: 8 bis 12 Grad, leichte Regenschauer, Regenwahrscheinlichkeit 65 Prozent.", h.spoken.last())
        // With the internet switched off the answer says how to get the weather; nothing is sent.
        h.mode = InternetAccessMode.OFF
        assertTrue(h.say("Wie ist das Wetter?"))
        assertEquals(ContinuumBriefingDirector.WEATHER_OFF_REPLY, h.last)
        assertEquals(1, h.weatherRequests.size)
        h.mode = InternetAccessMode.ALWAYS_ON
        h.weather = { ContinuumWeatherRead.PlaceUnknown(it.placeName) }
        assertTrue(h.say("Wie ist das Wetter in Entenhausen?"))
        assertEquals("Den Ort „Entenhausen“ kennt der Wetterdienst nicht. Ich habe kein Wetter abgerufen.", h.last)
    }

    // ── Calling ─────────────────────────────────────────────────────────────────────────────────────────────

    private val book = listOf(
        OwnerContactPhone(1, "Anna Beispiel", "+43 000 555 0101", "+430005550101", true),
        OwnerContactPhone(2, "Anna Muster", "+43 000 555 0102", "+430005550102", true),
        OwnerContactPhone(3, "Thomas Muster", "+43 000 555 0103", "+430005550103", true),
        OwnerContactPhone(3, "Thomas Muster", "+43 000 555 0199", "+430005550199", false),
        OwnerContactPhone(4, "Mama", "0000 555 0104", "+430005550104", true),
        OwnerContactPhone(6, "Karl Ohnevorwahl", "0000 555 0106", null, true),
        OwnerContactPhone(7, "Doppel Nummer", "+43 000 555 0107", "+430005550107", true),
        OwnerContactPhone(7, "Doppel Nummer", "+43 000 555 0108", "+430005550108", true))

    @Test fun aFoundContactOpensTheDialerAndTheReplySaysExactlyThat() {
        h.phones = book
        assertTrue(h.say("Ruf Thomas an"))
        assertEquals(listOf("+430005550103"), h.dialed)
        assertEquals("Die Wähltastatur ist offen, mit der Nummer von Thomas Muster. Auf Anrufen tippst du selbst – ich wähle nicht.", h.last)
        assertFalse("never a claim that a call was made", h.last.contains("angerufen") || h.last.contains("rufe ich"))
        assertTrue(h.activities.toString(), h.activities.contains("ANRUF · Wähltastatur geöffnet · kein Anruf gestartet"))
        assertEquals(1, h.contactReads)
        assertTrue(h.say("Ich möchte meine Mama anrufen"))
        assertEquals("+430005550104", h.dialed.last())
        // A number stored without country code is dialled as stored.
        assertTrue(h.say("Kannst du Karl anrufen?"))
        assertEquals("00005550106", h.dialed.last())
    }

    @Test fun anAmbiguousNameIsAQuestionAndTheAnswerDecides() {
        h.phones = book
        assertTrue(h.say("Ruf Anna an"))
        assertTrue(h.dialed.isEmpty())
        assertEquals("Zu „Anna“ gibt es mehrere passende Nummern: Anna Beispiel, Anna Muster. Welche meinst du?", h.last)
        assertTrue(h.say("die zweite"))
        assertEquals(listOf("+430005550102"), h.dialed)
        assertTrue(h.last, h.last.startsWith("Die Wähltastatur ist offen, mit der Nummer von Anna Muster."))
        // Two numbers of one contact are a question as well; „nein“ ends it without dialling.
        assertTrue(h.say("Ruf Doppel Nummer an"))
        assertEquals("Zu „Doppel Nummer“ gibt es mehrere passende Nummern: Doppel Nummer (Nummer endet auf 07), " +
            "Doppel Nummer (Nummer endet auf 08). Welche meinst du?", h.last)
        assertTrue(h.say("nein"))
        assertEquals("In Ordnung, ich habe niemanden angerufen.", h.last)
        assertEquals(1, h.dialed.size)
        // An answer that names nobody of the list is not taken as an answer.
        assertTrue(h.say("Ruf Anna an"))
        assertFalse(h.say("Wie spät ist es?"))
        assertFalse("the question is over", h.say("die erste"))
        assertEquals(1, h.dialed.size)
    }

    @Test fun anUnknownNameANumberAndAMissingPermission() {
        h.phones = book
        assertTrue(h.say("Ruf Berta an"))
        assertEquals("Im Adressbuch finde ich „Berta“ nicht. Angerufen habe ich niemanden.", h.last)
        assertTrue(h.dialed.isEmpty())
        // A number needs no address book.
        val reads = h.contactReads
        assertTrue(h.say("Ruf 0664 1234567 an"))
        assertEquals(listOf("0664 1234567"), h.dialed)
        assertEquals("Die Wähltastatur ist offen, mit der Nummer 0664 1234567. Auf Anrufen tippst du selbst – ich wähle nicht.", h.last)
        assertEquals(reads, h.contactReads)
        // An emergency service is never searched in the address book: the dialer opens with the Euro emergency number.
        assertTrue(h.say("Ruf die Rettung an"))
        assertEquals("112", h.dialed.last())
        assertEquals("Die Wähltastatur ist offen, mit dem Euro-Notruf 112. Auf Anrufen tippst du selbst – ich wähle nicht.", h.last)
        assertTrue(h.say("Ruf den Notruf an"))
        assertEquals(listOf("0664 1234567", "112", "112"), h.dialed)
        assertEquals(reads, h.contactReads)
        h.dialed.clear()
        h.dialed += "0664 1234567"
        // Contacts not readable: explained first, and a no is said plainly.
        h.contactsGranted = false
        h.contactsAnswer = false
        assertTrue(h.say("Ruf Thomas an"))
        assertEquals(listOf(ContinuumBriefingDirector.CONTACTS_EXPLANATION), h.contactExplanations)
        assertEquals(ContinuumBriefingDirector.CONTACTS_DENIED, h.last)
        assertEquals(1, h.dialed.size)
        h.contactsAnswer = true
        assertTrue(h.say("Ruf Thomas an"))
        assertEquals("+430005550103", h.dialed.last())
        // The dialer could not be opened: said, and nobody was called.
        h.dialProblem = "auf diesem Gerät gibt es keine Telefon-App"
        assertTrue(h.say("Ruf Thomas an"))
        assertEquals("Die Wähltastatur ließ sich nicht öffnen: auf diesem Gerät gibt es keine Telefon-App. Angerufen habe ich niemanden.", h.last)
        // The address book cannot be read right now.
        h.dialProblem = null
        h.jobRefused = true
        assertTrue(h.say("Ruf Thomas an"))
        assertEquals("Das Adressbuch konnte ich gerade nicht lesen. Angerufen habe ich niemanden.", h.last)
    }

    // ── System check as an owner turn ───────────────────────────────────────────────────────────────────────

    @Test fun theSystemCheckAnswersFromTheFactsOfThatMomentAndOffersWhatFits() {
        h.mode = InternetAccessMode.ASK
        assertTrue(h.say("Systemcheck"))
        assertEquals("Alles läuft, Heinz: Gemma ist geladen, die Spracheingabe steht und der Akku ist bei 82 Prozent.", h.spoken.single())
        assertTrue(h.last, h.last.startsWith(h.spoken.single() + "\n\n✓ Gehirn: gemma-3n-e4b-it · geladen\n"))
        assertEquals(ContinuumSystemCheck.snapshot().size, h.last.lines().size - 2)
        assertTrue(ContinuumBriefingBoard.offers().isEmpty())
        assertEquals(listOf("Alle Prüfungen in Ordnung"), ContinuumBriefingBoard.cards().single { it.kind.code == "system" }.lines)
        h.facts = h.facts.copy(shizukuReady = false, thermalStatus = 2)
        assertTrue(h.say("Wie ist dein Status?"))
        assertTrue(h.spoken.last(), h.spoken.last().startsWith("Zwei Dinge, Heinz: Shizuku läuft nicht, und das Gerät ist schon warm."))
        assertEquals(ContinuumBriefingBoard.State.ATTENTION, ContinuumBriefingBoard.cards().single { it.kind.code == "system" }.state)
        // A full storage is something the app can look into: the diagnosis is offered, and only started on a yes.
        h.facts = ContinuumBriefingHarness.fine().copy(storageFreeBytes = 5 * ContinuumBriefingHarness.GB)
        assertTrue(h.say("Statusbericht"))
        assertTrue(h.spoken.last(), h.spoken.last().endsWith("Soll ich eine Systemdiagnose machen?"))
        assertTrue(h.submitted.isEmpty())
        assertTrue(h.say("ja bitte"))
        assertEquals(listOf(ContinuumBriefingDirector.ORDER_DIAGNOSIS), h.submitted)
    }

    @Test fun theStartReportHookReturnsTheReportForTheGreetingAndSaysNothingItself() {
        h.facts = h.facts.copy(shizukuReady = false)
        val before = ContinuumBriefingBoard.revision()
        val report = h.director.systemReport()
        assertTrue(report, report.startsWith("Ein Punkt, Heinz: Shizuku läuft nicht."))
        assertTrue(report, report.endsWith("Ausgeschaltet ist der Internetzugang."))
        assertTrue(h.chat.isEmpty() && h.spoken.isEmpty() && h.activities.isEmpty())
        assertEquals(ContinuumSystemCheck.State.WARN, ContinuumSystemCheck.snapshot().single { it.key == "bridge" }.state)
        assertEquals(listOf("Shizuku: nicht erreichbar"), ContinuumBriefingBoard.cards().single().lines)
        assertTrue("the stage can see that something changed", ContinuumBriefingBoard.revision() > before)
        val unchanged = ContinuumBriefingBoard.revision()
        ContinuumBriefingBoard.publishOffers(emptyList())
        assertEquals(unchanged, ContinuumBriefingBoard.revision())
    }

    @Test fun whatIsNotAnOrderOfThisLaneIsLeftAlone() {
        listOf("Was steht in der PDF?", "Wie wird das Wetter in dem Film dargestellt?", "Fass mir die Nachrichten von heute zusammen",
            "Wie spät ist es?", "Guten Morgen", "Ruf Anna an und sag ihr, dass ich später komme", "Trag einen Termin für morgen um 9 ein").forEach {
            assertFalse(it, h.say(it))
        }
        assertTrue(h.chat.isEmpty() && h.activities.isEmpty() && h.dialed.isEmpty() && h.calendarWindows.isEmpty())
    }
}
