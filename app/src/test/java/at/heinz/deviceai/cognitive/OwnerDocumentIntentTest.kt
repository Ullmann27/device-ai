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
        assertEquals("a new choice, not the earlier PDF", false, OwnerDocumentIntent.parse("Lies diese PDF")?.reuse)
        assertEquals(true, OwnerDocumentIntent.parse("Nimm die letzte PDF und fasse sie zusammen")?.reuse)
    }

    @Test fun negationsDestructiveOrSharingVerbsAndOtherTopicsNeverOpenThePicker() {
        listOf(
            "Lies die PDF nicht", "Lösche das Dokument", "Sende die PDF an Anna", "Teile das Dokument",
            "Was ist ein PDF?", "Lies mir die Nachrichten vor", "Öffne die Kamera", "pdf",
            "Lies diese PDF\u0000 und lösche sie"
        ).forEach { assertNull(it, OwnerDocumentIntent.parse(it)) }
    }

    @Test fun fold7FirstPageSelectionOpensAndroidPickerInsteadOfModelScopeToken() {
        assertEquals(OwnerDocumentIntent.Request(wantsDraft = true, reuse = false),
            OwnerDocumentIntent.parse("Ich möchte eine PDF auswählen und die erste Seite zusammenfassen."))
        assertEquals(OwnerDocumentIntent.Request(wantsDraft = true, reuse = false),
            OwnerDocumentIntent.parse("Ich will eine PDF auswählen und die erste Seite zusammenfassen"))
        for (notAllowed in listOf(
            "Ich möchte eine PDF auswählen und die erste Seite zusammenfassen und danach löschen",
            "Ich möchte eine PDF auswählen und die erste Seite zusammenfassen und hochladen",
            "Ich möchte keine PDF auswählen und die erste Seite zusammenfassen",
            "Ich möchte eine PDF auswählen und die erste Seite zusammenfassen im Internet"
        )) assertNull(notAllowed, OwnerDocumentIntent.parse(notAllowed))
    }

}
