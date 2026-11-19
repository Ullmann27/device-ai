package at.heinz.deviceai.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edges of the order recognition and of the answer to a next-step offer that the regression sets do not spell out. */
class ContinuumBriefingCommandTest {
    private fun route(text: String, name: String? = "Jarvis") = ContinuumBriefingCommands.parse(text, name)?.code

    @Test fun aTimeSpanOrADescriptionIsNotAPlace() {
        assertEquals("WEATHER:TODAY@Wien", route("Wetterbericht für Wien"))
        assertEquals("WEATHER:TOMORROW@Bad Ischl", route("Wie wird morgen das Wetter in Bad Ischl?"))
        listOf("Wie ist das Wetter in zwei Wochen?", "Wie wird das Wetter in den nächsten Tagen?", "Wetter für das Wochenende",
            "Wie wird das Wetter in Wien morgen und übermorgen?", "Wie ist das Wetter in der Zukunft?",
            "Wie wird das Wetter in dem Buch beschrieben?", "Mein Ort ist nicht Linz", "Mein Ort ist der schönste",
            "Mein Ort ist " + "x".repeat(70)).forEach { assertNull(it, route(it)) }
    }

    @Test fun aCallNeedsAPersonOrANumberAndNothingElseInTheSentence() {
        assertEquals("CALL:Anna", route("Ruf doch mal Anna an"))
        assertEquals("CALL:+43 664 1234567", route("Ruf +43 664 1234567 an"))
        assertEquals("CALL:Dr. Beispiel", route("Ruf Dr. Beispiel an"))
        listOf("Ruf mal an", "Ruf an", "Nicht anrufen", "Soll ich Anna anrufen?", "Wann soll ich Anna anrufen?",
            "Ruf Anna später an", "Ruf Anna oder Thomas an", "Ruf Anna zurück", "Ich muss noch anrufen", "Ruf 12 an",
            "Anna hat mich angerufen", "Ruf Anna an, wenn du Zeit hast").forEach { assertNull(it, route(it)) }
        assertTrue(ContinuumBriefingCommands.isNumber("0664 1234567"))
        assertTrue(ContinuumBriefingCommands.isNumber("+43 664/123-4567"))
        assertFalse(ContinuumBriefingCommands.isNumber("Anna"))
        assertFalse(ContinuumBriefingCommands.isNumber("12"))
    }

    @Test fun anEmergencyServiceIsRecognisedAsSuch() {
        listOf("den Notruf", "die Rettung", "Polizei", "die Feuerwehr", "einen Krankenwagen").forEach {
            assertTrue(it, ContinuumBriefingCommands.isEmergency(it))
        }
        listOf("Anna", "Polizei Linz", "meinen Rettungsschwimmer", "").forEach { assertFalse(it, ContinuumBriefingCommands.isEmergency(it)) }
        assertEquals("CALL:die Rettung", route("Ruf die Rettung an"))
    }

    @Test fun theOfferHourIsAnHourOfTheDay() {
        assertEquals("OFFER:0", route("Biete mir das Briefing täglich ab 0 Uhr an"))
        assertEquals("OFFER:23", route("Jeden Tag um 23 Uhr das Briefing anbieten"))
        assertEquals("OFFER:ON", route("Biete mir das Briefing täglich an"))
        assertNull(route("Jeden Morgen um 25 das Briefing anbieten"))
        assertNull(route("Jeden Morgen um sieben das Briefing vorlesen"))
        assertEquals("OFFER:OFF", ContinuumBriefingCommand.Offer(false, 7).code)
    }

    @Test fun addressGreetingAndCourtesyAreNotPartOfTheOrder() {
        assertEquals("SYSTEM_CHECK", route("Nova, Systemcheck bitte", "Nova"))
        assertEquals("SYSTEM_CHECK", route("Hey Jarvis, mach bitte kurz einen Systemcheck!"))
        assertEquals("BRIEFING", route("Servus Continuum, was steht heute an?"))
        assertEquals("BRIEFING", route("Okay, gib mir mein Briefing, danke"))
        assertEquals("APPOINTMENTS:TODAY", route("Bitte lies mir meine Termine vor."))
        // The name of another assistant is not an address.
        assertNull(route("Alexa, Systemcheck"))
        // Control characters, quotations and over-long input are never an order.
        assertNull(route("System\ncheck"))
        assertNull(route("Sag „Systemcheck“"))
        assertNull(route("Systemcheck " + "bitte ".repeat(30)))
        assertNull(route(""))
        assertNull(route("   "))
    }

    @Test fun theQuestionNamesWhatIsOfferedAndTheAnswerIsReadStrictly() {
        fun offer(kind: ContinuumBriefingBoard.OfferKind, label: String) = ContinuumBriefingBoard.Offer(kind.name, kind, label, "x")
        val work = offer(ContinuumBriefingBoard.OfferKind.WORK, "die offene Arbeit zeigen")
        val news = offer(ContinuumBriefingBoard.OfferKind.NEWS, "die Nachrichten von heute zusammenfassen")
        val weather = offer(ContinuumBriefingBoard.OfferKind.WEATHER, "das Wetter für morgen holen")
        assertNull(ContinuumNextSteps.question(emptyList()))
        assertEquals("Soll ich die offene Arbeit zeigen?", ContinuumNextSteps.question(listOf(work)))
        assertEquals("Soll ich die offene Arbeit zeigen oder die Nachrichten von heute zusammenfassen?", ContinuumNextSteps.question(listOf(work, news)))
        assertEquals("Soll ich die offene Arbeit zeigen, die Nachrichten von heute zusammenfassen oder das Wetter für morgen holen?",
            ContinuumNextSteps.question(listOf(work, news, weather)))
        assertEquals("Welchen Schritt meinst du – den ersten oder den zweiten?", ContinuumNextSteps.askWhich(listOf(work, news)))
        val kinds = listOf(ContinuumBriefingBoard.OfferKind.WORK, ContinuumBriefingBoard.OfferKind.MESSAGE)
        // „Die Nachrichten“ means the open messages when no news overview is offered.
        assertEquals(ContinuumNextSteps.Choice.Pick(1), ContinuumNextSteps.choose("die Nachrichten", kinds))
        assertEquals(ContinuumNextSteps.Choice.Pick(0), ContinuumNextSteps.choose("Jarvis, zeig mir die offene Arbeit", kinds, "Jarvis"))
        // Two different things named, something that is not offered, or an unknown word: nothing is chosen.
        assertNull(ContinuumNextSteps.choose("die Arbeit und die Nachricht", kinds))
        assertNull(ContinuumNextSteps.choose("das Wetter", kinds))
        assertNull(ContinuumNextSteps.choose("die Arbeit von gestern löschen", kinds))
        assertNull(ContinuumNextSteps.choose("ja", emptyList()))
        assertNull(ContinuumNextSteps.choose("x".repeat(90), kinds))
        assertTrue(ContinuumNextSteps.isYes("Ja bitte!") && ContinuumNextSteps.isNo("Nein, danke.") && !ContinuumNextSteps.isYes("ja und nein"))
    }

    @Test fun theCardsOfTheBoardAreBoundedPlainData() {
        ContinuumBriefingBoard.clear()
        assertTrue(ContinuumBriefingBoard.cards().isEmpty() && ContinuumBriefingBoard.offers().isEmpty())
        val card = ContinuumBriefingBoard.Card("weather", ContinuumBriefingBoard.Kind.WEATHER, "Wetter", listOf("a", "b"), ContinuumBriefingBoard.State.INFO)
        ContinuumBriefingBoard.put(card)
        ContinuumBriefingBoard.put(card.copy(lines = listOf("c")))
        assertEquals("one card per kind", listOf(listOf("c")), ContinuumBriefingBoard.cards().map { it.lines })
        assertEquals("Wetter\n· c", ContinuumBriefingBoard.chat(ContinuumBriefingBoard.cards()))
        assertEquals(listOf("calendar", "weather", "messages", "tasks", "news", "system"), ContinuumBriefingBoard.Kind.entries.map { it.code })
        assertEquals(listOf("info", "attention"), ContinuumBriefingBoard.State.entries.map { it.code })
        val tooMany = runCatching { card.copy(lines = List(5) { "x" }) }
        assertTrue("more than four lines are refused", tooMany.isFailure)
        val offers = List(5) { ContinuumBriefingBoard.Offer("o$it", ContinuumBriefingBoard.OfferKind.NEWS, "x", "y") }
        ContinuumBriefingBoard.publishOffers(offers)
        assertEquals(ContinuumBriefingBoard.MAX_OFFERS, ContinuumBriefingBoard.offers().size)
        ContinuumBriefingBoard.clear()
    }
}
