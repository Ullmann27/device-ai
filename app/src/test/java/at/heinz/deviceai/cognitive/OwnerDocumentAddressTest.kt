package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerDocumentAddressTest {
    @Test fun configuredNamesUseTheExistingAddressRecognition() {
        for (name in listOf("Atlas", "KITT", "Astra 7", "A+B")) {
            assertEquals(name, OwnerDocumentIntent.Request(false),
                OwnerDocumentIntent.parse("$name, kannst du mir bitte die PDF öffnen?", name))
        }
        assertNull(OwnerDocumentIntent.parse("Atlas, kannst du die PDF öffnen?", "KITT"))
    }

    @Test fun politeSummaryKeepsTheDraftAndReuseFlags() {
        assertEquals(OwnerDocumentIntent.Request(true, true),
            OwnerDocumentIntent.parse("Kannst du bitte die ausgewählte PDF zusammenfassen?"))
        assertEquals(OwnerDocumentIntent.Request(true, false),
            OwnerDocumentIntent.parse("Ich möchte das Dokument zusammenfassen."))
    }
}
