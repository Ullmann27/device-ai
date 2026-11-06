package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerDocumentPoliteIntentTest {
    @Test fun politeObjectFirstRequestsReachDocumentIntake() {
        listOf(
            "Kannst du mir bitte die PDF öffnen?",
            "Jarvis, könntest du bitte dieses Dokument anzeigen?",
            "Continuum würdest du die PDF für mich öffnen?",
            "Ich möchte bitte eine PDF öffnen.",
            "Ich würde gerne das Dokument lesen.",
            "Bitte kannst du mir die PDF vorlesen?"
        ).forEach { text ->
            assertEquals(text, OwnerDocumentIntent.Request(false), OwnerDocumentIntent.parse(text))
        }
    }

    @Test fun politeReuseKeepsTheExistingGrantCheck() {
        assertEquals(OwnerDocumentIntent.Request(false, true),
            OwnerDocumentIntent.parse("Jarvis kannst du bitte die letzte PDF öffnen?"))
    }

    @Test fun explanationQuestionsAndQuotesStayOutOfThePicker() {
        listOf(
            "Wie kann ich eine PDF öffnen?",
            "Warum kannst du die PDF öffnen?",
            "Erkläre mir, wie ich die PDF öffnen kann.",
            "Erkläre den Satz: Kannst du mir die PDF öffnen?",
            "\"Kannst du mir die PDF öffnen?\"",
            "Was bedeutet PDF öffnen?"
        ).forEach { assertNull(it, OwnerDocumentIntent.parse(it)) }
    }

    @Test fun negationAndAdditionalActionsCannotBecomeAnOpenOnlyTask() {
        listOf(
            "Kannst du die PDF nicht öffnen?",
            "Ich möchte keine PDF öffnen.",
            "Kannst du die PDF öffnen und löschen?",
            "Kannst du die PDF öffnen und senden?",
            "Kannst du die PDF öffnen und WhatsApp starten?",
            "Kannst du die PDF öffnen? Starte danach WhatsApp.",
            "Kannst du die PDF öffnen\u0000?"
        ).forEach { assertNull(it, OwnerDocumentIntent.parse(it)) }
    }

    @Test fun extraAppStepsKeepTheCompleteRequestInPlanning() {
        listOf(
            "Öffne die PDF und starte WhatsApp.",
            "Lies die PDF. Danach öffne die Kamera.",
            "Zeige das Dokument und schreibe meinem Vater.",
            "Öffne die PDF und zeige mir den Kalender.",
            "Starte WhatsApp und öffne die PDF.",
            "Zeige mir den Kalender und öffne die PDF.",
            "Öffne die PDF, starte WhatsApp.",
            "Öffne die PDF; starte WhatsApp.",
            "Öffne den Kalender und die PDF."
        ).forEach { assertNull(it, OwnerDocumentIntent.parse(it)) }
        assertEquals(OwnerDocumentIntent.Request(true),
            OwnerDocumentIntent.parse("Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf."))
    }
}
