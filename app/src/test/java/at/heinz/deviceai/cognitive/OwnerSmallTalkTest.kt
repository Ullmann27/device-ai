package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.OwnerSmallTalk.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The few turns of small talk the app answers itself: recognised only where they are what they look like, answered
 * briefly and warmly – and never with a feeling, a mood or a life of its own.
 */
class OwnerSmallTalkTest {
    private fun parse(text: String, afterGreeting: Boolean = false, offerOpen: Boolean = false) =
        OwnerSmallTalk.parse(text, "Jarvis", afterGreeting, offerOpen)

    @Test fun hisQuestionBackAThankYouAndAGoodbyeAreAlwaysRecognised() {
        for (text in listOf("Wie geht's dir?", "Wie geht es dir?", "Na, wie geht's?", "wie gehts", "Alles klar bei dir?",
            "Hallo, wie geht es dir?", "Und, wie läuft's bei dir?")) assertEquals(text, Kind.ASKS_BACK, parse(text))
        for (text in listOf("Danke", "Danke dir", "Vielen Dank", "Dankeschön", "Danke, Jarvis", "Super, danke", "Danke, das war's"))
            assertEquals(text, Kind.THANKS, parse(text))
        for (text in listOf("Gute Nacht", "Bis später", "Tschüss", "Bis morgen, Jarvis", "Ich geh jetzt schlafen"))
            assertEquals(text, Kind.FAREWELL, parse(text))
    }

    @Test fun aBareMoodWordIsOnlyHisAnswerRightAfterTheGreeting() {
        val moods = mapOf("gut" to Kind.GOOD, "alles gut" to Kind.GOOD, "passt" to Kind.GOOD, "müde" to Kind.TIRED, "bin total kaputt" to Kind.TIRED,
            "geht so" to Kind.SO_SO, "naja" to Kind.SO_SO, "schlecht" to Kind.BAD, "nicht so gut" to Kind.BAD,
            "gut, und dir?" to Kind.GOOD_AND_ASKS_BACK, "super, und selbst?" to Kind.GOOD_AND_ASKS_BACK, "und dir?" to Kind.ASKS_BACK)
        moods.forEach { (text, kind) ->
            assertEquals(text, kind, parse(text, afterGreeting = true))
            assertNull("„$text“ without the greeting keeps its route", parse(text))
        }
    }

    @Test fun warteAndVergissEsAreOnlyAnswersWhileSomethingWaitsForOne() {
        assertEquals(Kind.HOLD, parse("warte kurz", offerOpen = true))
        assertEquals(Kind.HOLD, parse("Moment", afterGreeting = true))
        assertEquals(Kind.DISMISS, parse("vergiss es", offerOpen = true))
        assertEquals(Kind.DISMISS, parse("lass gut sein", offerOpen = true))
        for (text in listOf("warte kurz", "Moment", "vergiss es", "lass gut sein", "egal")) assertNull(text, parse(text))
        assertNull("no offer open: nothing to dismiss", parse("vergiss es", afterGreeting = true))
    }

    @Test fun anythingWithAnOrderOrAQuestionInItIsNotSmallTalk() {
        for (other in listOf("Wie geht es Anna?", "Wie geht das?", "Danke, und jetzt öffne das PDF", "Gute Nacht Geschichte vorlesen",
            "Wie geht's weiter mit dem Projekt?", "gut, dann öffne WhatsApp", "müde bin ich, geh zur Ruh", "Warte, öffne lieber das andere",
            "Alles klar, schick es ab", "Wie spät ist es?", "", "x"))
            assertNull(other, parse(other, afterGreeting = true, offerOpen = true))
    }

    @Test fun noSentenceOfTheAppClaimsAFeelingOrALife() {
        val sentences = OwnerSmallTalk.allSentences() + OwnerAcknowledgement.allSentences() +
            Kind.entries.flatMap { kind -> (0..6).map { OwnerSmallTalk.reply(kind, "Heinz", it, said = "Gute Nacht").text } }
        sentences.forEach { assertFalse(it, OwnerSmallTalk.claimsFeeling(it)) }
        // The check itself knows what it is looking for.
        for (claim in listOf("Ich freue mich riesig, dich zu sehen.", "Mir geht's gut, danke!", "Ich bin auch müde.", "Ich habe dich vermisst.",
            "Ich fühle mich heute großartig.", "Ich hatte einen langen Tag.", "Mir geht es bestens."))
            assertTrue(claim, OwnerSmallTalk.claimsFeeling(claim))
        for (fine in listOf("Bei mir läuft alles rund.", "Schön, dass du wieder da bist.", "Das hört man gern.", "Die Systeme laufen."))
            assertFalse(fine, OwnerSmallTalk.claimsFeeling(fine))
    }

    @Test fun theRepliesAreShortVaryAndAskAtMostOneThing() {
        Kind.entries.forEach { kind ->
            var previous = -1
            val said = (0..7).map { count -> OwnerSmallTalk.reply(kind, "Heinz", count, previous).also { previous = it.index }.text }
            said.zipWithNext().forEach { (first, second) -> assertNotEquals("$kind twice in a row", first, second) }
            said.forEach { text ->
                assertTrue(text, text.length <= 110)
                assertTrue(text, text.count { it == '?' } <= 1)
            }
        }
        // A thank-you and a goodbye are answered without a question back.
        for (kind in listOf(Kind.THANKS, Kind.FAREWELL, Kind.HOLD, Kind.DISMISS))
            (0..5).forEach { assertFalse(OwnerSmallTalk.reply(kind, "Heinz", it).text.contains("?")) }
        // After „und dir?“ the app does not ask „und bei dir?“ again.
        (0..4).forEach { assertFalse(OwnerSmallTalk.reply(Kind.GOOD_AND_ASKS_BACK, null, it).text.contains("bei dir?")) }
    }

    @Test fun aGoodbyeAnswersWhatHeSaid() {
        assertEquals("Gute Nacht, Heinz.", OwnerSmallTalk.reply(Kind.FAREWELL, "Heinz", 0, said = "Gute Nacht").text)
        assertEquals("Gute Nacht.", OwnerSmallTalk.reply(Kind.FAREWELL, null, 3, said = "Ich geh jetzt schlafen").text)
        assertEquals("Bis morgen, Heinz.", OwnerSmallTalk.reply(Kind.FAREWELL, "Heinz", 1, said = "Bis morgen").text)
        assertEquals("Bis später.", OwnerSmallTalk.reply(Kind.FAREWELL, "Heinz", 0, said = "Tschüss").text)
    }

    @Test fun theAcknowledgementVariesAndStartsWithTheOwnersOwnExample() {
        assertEquals("Verstanden. Ich kümmere mich darum.", OwnerAcknowledgement.line(0).text)
        var previous = -1
        val lines = (0..9).map { count -> OwnerAcknowledgement.line(count, previous).also { previous = it.index }.text }
        lines.zipWithNext().forEach { (first, second) -> assertNotEquals(first, second) }
        assertEquals(6, lines.toSet().size)
        lines.forEach { assertFalse(it, it.contains("?") || it.contains("einzeln")) }
    }
}
