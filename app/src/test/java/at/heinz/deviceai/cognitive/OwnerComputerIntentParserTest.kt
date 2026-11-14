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

    @Test fun messageDestinationPrepositionsDoNotBecomeRecipientText() {
        for (preposition in listOf("in", "auf", "per", "via", "über", "ueber")) {
            for (alias in listOf("WhatsApp", "Whats App")) {
                for (request in listOf("Schreib Anna $preposition $alias: Hallo",
                    "Bereite $preposition $alias eine Nachricht an Anna vor: Hallo")) {
                    val intent = ready(request)
                    assertEquals(request, ComputerApp.WHATSAPP, intent.app)
                    assertEquals(request, ComputerGoal.DRAFT_MESSAGE, intent.goal)
                    assertEquals(request, "Anna", intent.query)
                    assertEquals(request, "Hallo", intent.draft)
                    assertFalse(intent.steps.contains(ComputerPlanStep.REQUIRE_SEND_APPROVAL))
                }
            }
        }
    }

    @Test fun perAndViaPreserveDraftBodyAndSeparateSendApproval() {
        for (preposition in listOf("per", "via")) {
            val body = "Nicht per WhatsApp;  bitte via Telefon: morgen!"
            val intent = ready("Sende Anna $preposition WhatsApp: $body")
            assertEquals(ComputerGoal.SEND_MESSAGE, intent.goal)
            assertEquals("Anna", intent.query)
            assertEquals(body, intent.draft)
            assertEquals(ComputerPlanStep.REQUIRE_SEND_APPROVAL, intent.steps.last())
        }
    }

    @Test fun destinationPrepositionsCannotBypassNegationOrAmbiguity() {
        for (preposition in listOf("per", "via")) {
            assertTrue(OwnerComputerIntentParser.parse("Schreib Anna nicht $preposition WhatsApp: Hallo")
                is OwnerComputerIntentParser.Result.Unsupported)
            assertTrue(OwnerComputerIntentParser.parse("Schreib Anna $preposition WhatsApp und per Facebook: Hallo")
                is OwnerComputerIntentParser.Result.Ask)
            assertTrue(OwnerComputerIntentParser.parse("Schreib Anna $preposition WhatsApp")
                is OwnerComputerIntentParser.Result.Ask)
            assertTrue(OwnerComputerIntentParser.parse("Schreib Anna $preposition WhatsApp: " + "x".repeat(501))
                is OwnerComputerIntentParser.Result.Unsupported)
        }
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

    @Test fun systemAndEverydayAppsUseTheExistingOwnerApprovalPath() {
        for ((phrase, expected) in listOf(
            "Öffne Google Chrome" to ComputerApp.CHROME,
            "Öffne Einstellungen" to ComputerApp.SETTINGS,
            "Öffne Kamera" to ComputerApp.CAMERA,
            "Öffne Samsung Galerie" to ComputerApp.GALLERY,
            "Öffne Kalender" to ComputerApp.CALENDAR
        )) {
            val intent = ready(phrase)
            assertEquals(expected, intent.app)
            assertEquals(ComputerGoal.OPEN, intent.goal)
            assertEquals(listOf(ComputerPlanStep.OPEN_APP, ComputerPlanStep.OBSERVE_APP), intent.steps)
        }
    }

    @Test fun newOpenOnlyAppsCannotAcquireUntestedEditOrSearchActions() {
        for (app in listOf(ComputerApp.CHROME, ComputerApp.SETTINGS,
            ComputerApp.CAMERA, ComputerApp.GALLERY, ComputerApp.CALENDAR)) {
            assertTrue(OwnerComputerIntentParser.parse(
                "Öffne ${app.discoveryQuery} und suche Geheimnisse"
            ) is OwnerComputerIntentParser.Result.Unsupported)
            assertTrue(OwnerComputerIntentParser.parse(
                "Suche in ${app.discoveryQuery} nach Kontakten"
            ) is OwnerComputerIntentParser.Result.Unsupported)
        }
        assertTrue(OwnerComputerIntentParser.parse("Öffne Einstellungen nicht")
            is OwnerComputerIntentParser.Result.Unsupported)
    }

    @Test fun explicitAppSwitchUsesTheSameNewOwnerApprovedOpenPlan() {
        for ((request, app) in listOf(
            "Wechsle zu Chrome" to ComputerApp.CHROME,
            "Wechsel zu WhatsApp" to ComputerApp.WHATSAPP,
            "Wechsle zu Einstellungen" to ComputerApp.SETTINGS
        )) {
            val intent = ready(request)
            assertEquals(app, intent.app)
            assertEquals(ComputerGoal.OPEN, intent.goal)
            assertEquals(listOf(ComputerPlanStep.OPEN_APP, ComputerPlanStep.OBSERVE_APP), intent.steps)
        }
        assertSame(OwnerComputerIntentParser.Result.NotComputerCommand,
            OwnerComputerIntentParser.parse("Wechsle zu irgendeiner anderen App"))
        assertTrue(OwnerComputerIntentParser.parse("Wechsle zu Chrome und lösche Daten")
            is OwnerComputerIntentParser.Result.Unsupported)
    }

    @Test fun ambiguityAndOversizedTextDoNotProduceExecutablePlans() {
        assertTrue(OwnerComputerIntentParser.parse("Schreibe Anna auf WhatsApp und auf Facebook: Hallo") is OwnerComputerIntentParser.Result.Ask)
        assertTrue(OwnerComputerIntentParser.parse("Schreibe Anna auf WhatsApp: " + "x".repeat(501)) is OwnerComputerIntentParser.Result.Unsupported)
    }

    @Test fun negatedEverydayQuestionsRemainConversation() {
        for (request in listOf(
            "Warum funktioniert meine Kamera nicht?",
            "Ich finde die Einstellungen nicht",
            "Warum synchronisiert mein Kalender nicht?",
            "Meine Galerie zeigt das Bild nicht",
            "Chrome funktioniert nicht",
            "Ich benutze WordPress niemals"
        )) assertSame(request, OwnerComputerIntentParser.Result.NotComputerCommand,
            OwnerComputerIntentParser.parse(request))
    }

    @Test fun openOnlyAppTopicsDoNotBecomeMessages() {
        for (app in listOf("Kamera", "Einstellungen", "Kalender", "Galerie", "Chrome")) {
            for (context in listOf(null, ComputerApp.WHATSAPP)) {
                for (request in listOf(
                    "Schreibe mir einen Text über den $app",
                    "Verfasse eine Erklärung über $app",
                    "Schreibe mir keinen Text über $app",
                    "Schreibe mir einen Text über $app nicht auf Englisch"
                )) assertSame(request, OwnerComputerIntentParser.Result.NotComputerCommand,
                    OwnerComputerIntentParser.parse(request, context))
            }
        }
        assertSame(OwnerComputerIntentParser.Result.NotComputerCommand,
            OwnerComputerIntentParser.parse("Schreibe einen Vergleich über Kalender und Galerie"))
    }

    @Test fun explicitNegatedCommandsStillCannotProducePlans() {
        for (app in ComputerApp.entries) {
            for (request in listOf(
                "Öffne ${app.discoveryQuery} nicht",
                "Öffne nicht ${app.discoveryQuery}",
                "Starte niemals ${app.discoveryQuery}",
                "Wechsle keinesfalls zu ${app.discoveryQuery}",
                "Nicht ${app.discoveryQuery} öffnen",
                "Suche in ${app.discoveryQuery} nicht nach Anna"
            )) assertTrue(request, OwnerComputerIntentParser.parse(request)
                is OwnerComputerIntentParser.Result.Unsupported)
        }
        assertTrue(OwnerComputerIntentParser.parse("Schreibe keine Nachricht an Anna in WhatsApp: Hallo")
            !is OwnerComputerIntentParser.Result.Ready)
        assertTrue(OwnerComputerIntentParser.parse("Sende Anna in WhatsApp nicht: Hallo")
            is OwnerComputerIntentParser.Result.Unsupported)
    }

    @Test fun explicitMessagesInOpenOnlyAppsRemainUnsupported() {
        for (app in listOf("Kamera", "Einstellungen", "Kalender", "Galerie", "Chrome")) {
            for (request in listOf("Schreibe eine Nachricht an Anna in $app: Hallo",
                "Schicke Anna auf $app: Hallo")) {
                assertTrue(request, OwnerComputerIntentParser.parse(request)
                    is OwnerComputerIntentParser.Result.Unsupported)
            }
        }
    }

    @Test fun draftBodyNegationDoesNotCancelTheOwnerCommand() {
        val intent = ready("Schreibe Anna auf WhatsApp: Die Kamera funktioniert nicht.")
        assertEquals(ComputerGoal.DRAFT_MESSAGE, intent.goal)
        assertEquals("Die Kamera funktioniert nicht.", intent.draft)
        assertFalse(intent.steps.contains(ComputerPlanStep.REQUIRE_SEND_APPROVAL))
    }
}
