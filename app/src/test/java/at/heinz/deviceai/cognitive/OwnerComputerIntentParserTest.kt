package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class OwnerComputerIntentParserTest {
    private fun ready(text: String, context: ComputerApp? = null) =
        (OwnerComputerIntentParser.parse(text, context) as OwnerComputerIntentParser.Result.Ready).intent

    @Test fun openWhatsAppIsOnlyOpen() {
        val intent = ready("Bitte öffne WhatsApp.")
        assertEquals(ComputerGoal.OPEN, intent.goal)
        assertEquals(listOf(ComputerPlanStep.OPEN_APP, ComputerPlanStep.OBSERVE_APP), intent.steps)
        assertNull(intent.draft)
    }

    @Test fun combinedSearchKeepsOwnerQuery() {
        val intent = ready("Öffne WhatsApp und suche Anna")
        assertEquals(ComputerGoal.SEARCH, intent.goal)
        assertEquals("Anna", intent.query)
        assertFalse(intent.steps.contains(ComputerPlanStep.SET_DRAFT))
        assertEquals("Elena Müller", ready("Suche in Facebook nach Elena Müller").query)
    }

    @Test fun draftPreservesExactContent() {
        val intent = ready("Bereite in WhatsApp eine Nachricht an Anna vor: Ich bin  in 20 Minuten da!")
        assertEquals(ComputerGoal.DRAFT_MESSAGE, intent.goal)
        assertEquals("Anna", intent.query)
        assertEquals("Ich bin  in 20 Minuten da!", intent.draft)
        assertFalse(intent.steps.contains(ComputerPlanStep.REQUIRE_SEND_APPROVAL))
    }

    @Test fun sendIntentStillRequiresSeparateApproval() {
        val intent = ready("Sende eine Nachricht an Anna auf WhatsApp: Bis bald.")
        assertEquals(ComputerGoal.SEND_MESSAGE, intent.goal)
        assertEquals(ComputerPlanStep.REQUIRE_SEND_APPROVAL, intent.steps.last())
        assertEquals("Bis bald.", intent.draft)
    }

    @Test fun missingAppAndTextAreAskedSeparately() {
        assertTrue(OwnerComputerIntentParser.parse("Bereite eine Nachricht an Anna vor: Hallo") is OwnerComputerIntentParser.Result.Ask)
        assertEquals("Anna", ready("Bereite eine Nachricht an Anna vor: Hallo", ComputerApp.WHATSAPP).query)
        assertTrue(OwnerComputerIntentParser.parse("Schreibe Anna auf WhatsApp") is OwnerComputerIntentParser.Result.Ask)
    }

    @Test fun unsupportedTailNeverFallsBackToOpening() {
        assertTrue(OwnerComputerIntentParser.parse("Öffne WhatsApp und lösche Anna") is OwnerComputerIntentParser.Result.Unsupported)
        assertTrue(OwnerComputerIntentParser.parse("Öffne WhatsApp und suche Anna und sende Hallo") is OwnerComputerIntentParser.Result.Unsupported)
        assertTrue(OwnerComputerIntentParser.parse("Öffne WhatsApp nicht") is OwnerComputerIntentParser.Result.Unsupported)
    }

    @Test fun ordinaryConversationIsNotAComputerOrder() {
        assertSame(OwnerComputerIntentParser.Result.NotComputerCommand,
            OwnerComputerIntentParser.parse("Wie öffne ich WhatsApp?"))
        assertSame(OwnerComputerIntentParser.Result.NotComputerCommand,
            OwnerComputerIntentParser.parse("Morgen wollen wir mit unserem Projekt starten."))
        assertSame(OwnerComputerIntentParser.Result.NotComputerCommand, OwnerComputerIntentParser.parse("WhatsApp"))
        for (request in listOf("Schreibe eine Geschichte", "Schreibe einen Brief", "Schreibe eine Zusammenfassung")) {
            assertSame(OwnerComputerIntentParser.Result.NotComputerCommand, OwnerComputerIntentParser.parse(request))
        }
    }

    @Test fun allRequestedAppsCanOpenWithoutPretendingEditors() {
        for (app in ComputerApp.entries) assertEquals(app, ready("Öffne ${app.discoveryQuery}").app)
        assertEquals(ComputerGoal.OPEN_DOWNLOADS, ready("Öffne den Download-Ordner").goal)
        assertTrue(OwnerComputerIntentParser.parse("Öffne Word und bearbeite den Vertrag") is OwnerComputerIntentParser.Result.Unsupported)
    }

    @Test fun ambiguityAndOversizedTextDoNotProduceExecutablePlans() {
        assertTrue(OwnerComputerIntentParser.parse("Schreibe Anna auf WhatsApp und auf Facebook: Hallo") is OwnerComputerIntentParser.Result.Ask)
        assertTrue(OwnerComputerIntentParser.parse("Schreibe Anna auf WhatsApp: " + "x".repeat(501)) is OwnerComputerIntentParser.Result.Unsupported)
    }
}
