package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class OwnerOpenDocumentSummaryTest {
    @Test fun definiteSummaryRequestsReferToAlreadyOpenedSource() {
        for (request in listOf("Fasse die PDF zusammen.", "Fasse diese PDF zusammen.",
                "Kannst du die PDF zusammenfassen?", "Lies die PDF und erstelle eine Zusammenfassung.",
                "Fasse die geöffnete PDF neu zusammen.", "Lies die letzte PDF und erstelle einen Entwurf.")) {
            assertTrue(request, OwnerDocumentDialogue.reuseOpenSummary(request))
        }
    }

    @Test fun resultArticleDoesNotRequestAnotherDocument() {
        val request = "Astra, lies die PDF und erstelle eine neue Zusammenfassung."
        assertTrue(OwnerDocumentDialogue.reuseOpenSummary(request, "Astra"))
        assertTrue(OwnerDocumentIntent.parse(request, "Astra")!!.wantsDraft)
    }

    @Test fun explicitNewIndefiniteOrNamedPdfRequiresSelection() {
        for (request in listOf("Fasse eine PDF zusammen.", "Fasse eine neue PDF zusammen.",
                "Fasse die andere PDF zusammen.", "Fasse eine weitere PDF zusammen.",
                "Fasse ein neues Dokument zusammen.", "Fasse die PDF Anderer_Bericht.pdf zusammen.")) {
            assertNotNull(request, OwnerDocumentIntent.parse(request))
            assertFalse(request, OwnerDocumentDialogue.reuseOpenSummary(request))
        }
    }

    @Test fun negatedAndAdditionalDeviceActionsCannotReuseSummaryRoute() {
        for (request in listOf("Fasse die PDF nicht zusammen.", "Fasse die PDF zusammen und sende sie an Anna.",
                "Fasse die PDF zusammen und öffne WhatsApp.", "Wie heißt die PDF?", "Öffne die PDF.")) {
            assertFalse(request, OwnerDocumentDialogue.reuseOpenSummary(request))
        }
    }
}
