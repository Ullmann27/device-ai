package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerDocumentPresentationIntentTest {
    @Test fun fold7AsrOpensPdfWithoutDraftInference() {
        assertEquals(OwnerDocumentIntent.Request(false), OwnerDocumentIntent.parse(
            "continuum öffne meine PDF arg und 4 forschungsexperiment und zeige mir jeden Arbeitsschritt"))
    }

    @Test fun boundedPresentationRequestsKeepDocumentMeaning() {
        listOf(
            "Öffne meine PDF Argon 4 Forschungsexperiment und zeige mir jeden Arbeitsschritt.",
            "Öffne die PDF und zeige mir alle Arbeitsschritte.",
            "Jarvis, könntest du die letzte PDF öffnen und zeige mir die einzelnen Arbeitsschritte?",
            "Lies die PDF; zeige mir jeden einzelnen Arbeitsschritt."
        ).forEach { text ->
            val reuse = text.contains("letzte")
            assertEquals(text, OwnerDocumentIntent.Request(false, reuse), OwnerDocumentIntent.parse(text))
        }
        assertEquals(OwnerDocumentIntent.Request(true), OwnerDocumentIntent.parse(
            "Lies diese PDF und erstelle eine Zusammenfassung als Entwurf und zeige mir jeden Arbeitsschritt."))
    }

    @Test fun presentationCannotHideAnotherAction() {
        listOf(
            "Öffne die PDF und zeige mir jeden Arbeitsschritt und starte WhatsApp.",
            "Öffne die PDF und starte WhatsApp und zeige mir jeden Arbeitsschritt.",
            "Öffne die PDF und zeige mir jeden Arbeitsschritt; öffne die Kamera.",
            "Öffne die PDF und zeige mir den Kalender.",
            "Öffne die PDF und zeige mir jeden Arbeitsschritt im Kalender.",
            "Öffne die PDF und zeige mir jeden Arbeitsschritt sowie die Kamera.",
            "Öffne die PDF und sende sie; zeige mir jeden Arbeitsschritt.",
            "Öffne die PDF und zeige mir jeden Arbeitsschritt und installiere die App."
        ).forEach { assertNull(it, OwnerDocumentIntent.parse(it)) }
    }

    @Test fun presentationCannotRemoveNegationOrInvalidInput() {
        listOf(
            "Öffne die PDF nicht und zeige mir jeden Arbeitsschritt.",
            "Öffne keine PDF und zeige mir alle Arbeitsschritte.",
            "Öffne die PDF und zeige mir keinen Arbeitsschritt.",
            "Öffne die PDF\u0000 und zeige mir jeden Arbeitsschritt.",
            "Was bedeutet: PDF öffnen und zeige mir jeden Arbeitsschritt?"
        ).forEach { assertNull(it, OwnerDocumentIntent.parse(it)) }
    }
}
