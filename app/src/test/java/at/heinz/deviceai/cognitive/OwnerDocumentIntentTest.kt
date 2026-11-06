package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerDocumentIntentTest {
    @Test fun theOwnersExampleOpensThePickerAndAsksForADraft() {
        assertEquals(OwnerDocumentIntent.Request(wantsDraft = true),
            OwnerDocumentIntent.parse("Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf."))
        assertEquals(OwnerDocumentIntent.Request(wantsDraft = true), OwnerDocumentIntent.parse("Fasse die PDF zusammen"))
        assertEquals(OwnerDocumentIntent.Request(wantsDraft = false), OwnerDocumentIntent.parse("Öffne das Dokument"))
        assertEquals(OwnerDocumentIntent.Request(wantsDraft = false), OwnerDocumentIntent.parse("Bitte lies mir die PDF-Datei vor"))
    }

    @Test fun negationsDestructiveOrSharingVerbsAndOtherTopicsNeverOpenThePicker() {
        listOf(
            "Lies die PDF nicht", "Lösche das Dokument", "Sende die PDF an Anna", "Teile das Dokument",
            "Was ist ein PDF?", "Lies mir die Nachrichten vor", "Öffne die Kamera", "pdf",
            "Lies diese PDF\u0000 und lösche sie"
        ).forEach { assertNull(it, OwnerDocumentIntent.parse(it)) }
    }
}
