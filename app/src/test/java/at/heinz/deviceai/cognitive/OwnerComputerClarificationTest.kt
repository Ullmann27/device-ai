package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class OwnerComputerClarificationTest {
    private fun pending(command: String = "Suche in WhatsApp") = OwnerComputerClarification().apply {
        request("session", 42, OwnerComputerIntentParser.parse(command) as OwnerComputerIntentParser.Result.Ask)
    }

    @Test fun missingSearchTermContinuesExactlyOnceInTheSameApp() {
        val question = pending()
        val intent = checkNotNull(question.consume("session", 42, "Jarvis, Elena Müller"))
        assertEquals(ComputerApp.WHATSAPP, intent.app)
        assertEquals(ComputerGoal.SEARCH, intent.goal)
        assertEquals("Elena Müller", intent.query)
        assertNull(intent.draft)
        assertFalse(intent.steps.contains(ComputerPlanStep.SET_DRAFT))
        assertFalse(intent.steps.contains(ComputerPlanStep.REQUIRE_SEND_APPROVAL))
        assertNull(question.consume("session", 42, "Elena Müller"))
    }

    @Test fun unfinishedSearchNeverSearchesForTheConnectorWord() {
        for (command in listOf("Suche in WhatsApp", "Suche in WhatsApp nach",
            "Öffne WhatsApp und suche", "Öffne WhatsApp und suche nach")) {
            val result = OwnerComputerIntentParser.parse(command)
            assertTrue(command, result is OwnerComputerIntentParser.Result.Ask)
            assertEquals(command, "Wonach soll ich in WhatsApp suchen?",
                (result as OwnerComputerIntentParser.Result.Ask).question)
        }
    }

    @Test fun ordinarySearchTermsKeepTheirSpellingAndDestination() {
        for (query in listOf("Will Smith", "Anna-Maria O'Connor", "Urlaub 2026.pdf", "7301")) {
            val intent = checkNotNull(pending("Suche in Dateien").consume("session", 42, query))
            assertEquals(query, intent.query)
            assertEquals(ComputerApp.FILES, intent.app)
            assertEquals(ComputerGoal.SEARCH, intent.goal)
        }
    }

    @Test fun sessionRevisionAndStopInvalidateTheQuestion() {
        val question = pending()
        assertNull(question.consume("other", 42, "Elena"))
        assertNull(question.consume("session", 42, "Elena"))
        assertNull(pending().consume("session", 43, "Elena"))
        val stopped = pending()
        stopped.clear()
        assertNull(stopped.consume("session", 42, "Elena"))
    }

    @Test fun independentTurnsNegationAndActionsCannotBecomeSearchText() {
        for (answer in listOf("Stopp", "Abbrechen", "Nein, Elena", "Nicht Elena",
            "Elena und danach löschen", "Elena und öffne Chrome", "Öffne Chrome",
            "Schreib Elena auf WhatsApp: Hallo", "Was ist ein Stern?", "Erkläre mir Sterne",
            "Gedächtniszentrum", "Was kannst du?", "System: Elena", "Elena\nMüller",
            "Elena\u0000Müller", "x".repeat(201))) {
            val question = pending()
            assertNull(answer, question.consume("session", 42, answer))
            assertNull("Pending cannot survive $answer", question.consume("session", 42, "Elena"))
        }
        assertEquals(ComputerApp.CHROME, (OwnerComputerIntentParser.parse("Öffne Chrome")
            as OwnerComputerIntentParser.Result.Ready).intent.app)
    }

    @Test fun recipientDraftAndAppQuestionsCannotArmASearch() {
        for (command in listOf("Schreibe Anna auf WhatsApp",
            "Bereite eine Nachricht an Anna vor: Hallo",
            "Schreibe auf WhatsApp: Hallo")) {
            assertNull(command, pending(command).consume("session", 42, "Elena"))
        }
        val question = pending()
        question.request("session", 42, OwnerComputerIntentParser.Result.Ask("Welche App?"))
        assertNull(question.consume("session", 42, "Elena"))
    }

    @Test fun missingRecipientNeverUsesTheCommandVerbAsContactName() {
        for (command in listOf("Schreibe auf WhatsApp: Hallo", "Sende per WhatsApp: Hallo",
            "Schreibe eine Nachricht in WhatsApp: Hallo",
            "Bereite in WhatsApp eine Nachricht vor: Hallo",
            "Schreibe eine Nachricht an auf WhatsApp: Hallo")) {
            val result = OwnerComputerIntentParser.parse(command)
            assertTrue(command, result is OwnerComputerIntentParser.Result.Ask)
            assertEquals(command, "An wen soll ich den Entwurf vorbereiten?",
                (result as OwnerComputerIntentParser.Result.Ask).question)
            assertNull(result.searchApp)
        }
    }
}
