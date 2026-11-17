package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test

class WhatsAppDraftPolicyTest {
    @Test fun ownerDraftPreservesSafeVisibleText() {
        assertEquals("Hallo!\nAlles gut?", WhatsAppDraftPolicy.validated("  Hallo!\nAlles gut?  "))
        assertEquals("com.whatsapp", WhatsAppDraftPolicy.TARGET_PACKAGE)
        assertEquals(2_000, WhatsAppDraftPolicy.MAX_CHARACTERS)
    }

    @Test fun emptyOversizedOrControlBearingDraftsAreRejected() {
        for (text in listOf("", " \n ", "x".repeat(2_001), "Hallo\u0000Welt",
                "Hallo\rWelt", "a\n".repeat(31), "\u202eKontrolle", "\u2066unsichtbar")) {
            assertNull(text, WhatsAppDraftPolicy.validated(text))
        }
    }

    @Test fun ordinaryDraftIsNotSentOrAddressedByValidation() {
        assertEquals("Hallo", WhatsAppDraftPolicy.validated("Hallo"))
    }
}
