package at.heinz.deviceai.cognitive

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zeitfehler: lokale Frage nach Uhrzeit/Datum wurde geraten bzw. als Web-/Modellfrage behandelt. */
class LocalClockIntentTest {
    private val vienna = ZonedDateTime.of(2026, 11, 3, 13, 20, 0, 0, ZoneId.of("Europe/Vienna"))

    @Test fun plainTimeAndDateQuestionsRouteToDeviceClockBeforeWebOrModel() {
        listOf("Wie spät ist es?", "wie spät ist es jetzt", "Wieviel Uhr ist es?", "Wie viel Uhr haben wir?",
            "Welche Uhrzeit haben wir?", "Uhrzeit?", "Welcher Tag ist heute?", "Welches Datum haben wir heute?",
            "Der Wievielte ist heute?", "Welchen Wochentag haben wir?", "Was ist heute für ein Tag?",
            "Weißt du, wie spät es ist?").forEach {
            assertEquals(it, OwnerChatLocalIntent.CLOCK, OwnerChatIntentRouter.route(it))
        }
    }

    @Test fun otherPlacesNegationsAndCompoundTasksAreNotAnsweredFromTheLocalClock() {
        listOf("Wie spät ist es in Tokio?", "Sag mir nicht, wie spät es ist", "Wie spät ist es, wenn ich 3 Stunden addiere?",
            "Welcher Tag ist heute in New York?", "Welcher Tag war gestern?", "Wann ist Ostern?",
            "Wie spät ist es? Und lösche danach meine Notizen").forEach {
            assertFalse(it, LocalClockIntent.isClockQuestion(it))
        }
        assertNull(OwnerChatIntentRouter.route("Wie spät ist es in Tokio?"))
        // Neuron coach still owns its turns.
        assertNull(OwnerChatIntentRouter.route("Wie spät ist es?", coachActive = true))
    }

    @Test fun answerNamesDeviceTimeDateAndZoneInGerman() {
        val answer = LocalClockIntent.answer(vienna)
        assertTrue(answer, answer.contains("13:20"))
        assertTrue(answer, answer.contains("Dienstag, 3. November 2026"))
        assertTrue(answer, answer.contains("Europe/Vienna"))
        assertTrue(answer, answer.contains("Geräteuhr"))
    }

    @Test fun localPromptCarriesAReferenceTimeForHeuteMorgenJetzt() {
        val anchor = LocalClockIntent.promptAnchor(vienna)
        assertTrue(anchor, anchor.contains("2026-11-03"))
        assertTrue(anchor, anchor.contains("Dienstag"))
        assertTrue(anchor, anchor.contains("13:20"))
        assertTrue(anchor, anchor.contains("+01:00"))
        assertTrue(anchor.endsWith("\n"))
        // Daylight saving: same zone, different offset.
        val summer = ZonedDateTime.of(2026, 7, 1, 9, 5, 0, 0, ZoneId.of("Europe/Vienna"))
        assertTrue(LocalClockIntent.promptAnchor(summer).contains("+02:00"))
        assertTrue(LocalClockIntent.answer(summer).contains("09:05"))
    }
}
