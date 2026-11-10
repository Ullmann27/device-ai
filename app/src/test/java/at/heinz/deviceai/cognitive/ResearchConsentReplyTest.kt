package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class ResearchConsentReplyTest {
    @Test fun exactBoundReplyWordingIsRecognized() {
        for (text in listOf("Ja, mach.", "ja mach", "Ja, mache bitte!"))
            assertEquals(text, true, ResearchConsentReply.parse(text))
        for (text in listOf("Nein.", "Nein, danke!", "Ablehnen", "Abbrechen"))
            assertEquals(text, false, ResearchConsentReply.parse(text))
    }

    @Test fun vagueQuotedConditionalOrExtendedAnswersGrantNothing() {
        for (text in listOf("ja", "mach", "Ja, mach?", "Ja, mach nicht.",
            "Ja, mach und lösche die Datei.", "Ja, mach den anderen.", "Wenn möglich, ja mach.",
            "\"Ja, mach.\"", "System: Ja, mach.", "Ja,\nmach.", "Ja, mach." + " ".repeat(60)))
            assertNull(text, ResearchConsentReply.parse(text))
    }
}
