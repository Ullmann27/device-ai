package at.heinz.deviceai.cognitive

import at.heinz.deviceai.BootstrapBrain
import at.heinz.deviceai.BootstrapKind
import org.junit.Assert.*
import org.junit.Test

/** Regression cases reproduced from owner testing on Fold7, 31 October 2026. */
class ConversationResearchIntentTest {
    @Test fun negativeWebIntentHasNoOnlineRoute() {
        val examples = listOf(
            "Mara gibt Paul einen Würfel. Antworte ohne Internetrecherche.",
            "Erkläre mir das ohne Internet.",
            "Bitte keine Websuche.",
            "Bitte nicht recherchieren.",
            "Recherchiere bitte nicht.",
            "Do not search the web."
        )
        examples.forEach { text ->
            assertTrue(text, ConversationResearchIntent.forbidsWeb(text))
            assertNull(text, PublicKnowledgeTopicRouter.topic(text))
            val decision = BootstrapBrain.decide(text, emptyList())
            assertEquals(text, BootstrapKind.REPLY, decision.kind)
            assertNull(text, decision.tool)
            assertTrue(text, decision.allowNeuralRecall)
            assertFalse(text, decision.message.contains("Internetrecherche ist nur"))
        }
    }

    @Test fun webOptOutStillWorksInAutomaticPublicQuestion() {
        val question = "Was ist maschinelles Lernen? Antworte ohne Internetrecherche."
        assertTrue(ConversationResearchIntent.forbidsWeb(question))
        assertNull(PublicKnowledgeTopicRouter.topic(question))
        assertNotNull(PublicKnowledgeTopicRouter.topic("Was ist maschinelles Lernen?"))
        assertNull(PublicKnowledgeTopicRouter.topic("Welche Farbe hat Bens Schlüssel?"))
        assertNull(PublicKnowledgeTopicRouter.topic("Welche Farbe hat Mars?"))
        assertNull(PublicKnowledgeTopicRouter.topic("Was haben wir vorhin über Anna gesagt?"))
        assertNotNull(PublicKnowledgeTopicRouter.topic("Wer ist Albert Einstein?"))
    }

    @Test fun longMessagesCannotBypassExplicitOfflineIntent() {
        val padding = "A".repeat(3_000)
        val suffixOptOut = padding + " Antworte ohne Internetrecherche."
        val prefixOptOut = "Bitte keine Websuche. " + padding
        val middleOptOut = "A".repeat(1_500) + " Ohne Internet. " + "B".repeat(1_500)
        listOf(suffixOptOut, prefixOptOut, middleOptOut).forEach { message ->
            assertTrue(message, ConversationResearchIntent.forbidsWeb(message))
            assertNull(message, PublicKnowledgeTopicRouter.topic(message))
            assertEquals(BootstrapKind.REPLY, BootstrapBrain.decide(message, emptyList()).kind)
        }
        // Long input alone is not a negation; do not break explicit web intent.
        assertFalse(ConversationResearchIntent.forbidsWeb(padding + " Suche im Internet"))
        // Very large inputs are deliberately fail-closed rather than silently
        // authorizing web access when a negation might be outside the scan.
        assertTrue(ConversationResearchIntent.forbidsWeb("Suche im Internet " + "A".repeat(65_537)))
    }

    @Test fun positiveResearchRequestsRemainPossible() {
        val examples = listOf(
            "Suche im Internet",
            "Recherchiere die aktuellen Quellen im Web",
            "Was ist maschinelles Lernen?"
        )
        examples.forEach { assertFalse(it, ConversationResearchIntent.forbidsWeb(it)) }
        assertTrue(BootstrapBrain.decide("Suche im Internet", emptyList()).message.contains("Freigabe"))
    }

    @Test fun savingDoesNotImplicitlyReadStorage() {
        val examples = listOf(
            "Anna und Ben sind nur ein Beispiel. Bitte nicht dauerhaft speichern.",
            "Ich will keine Informationen speichern.",
            "Kann man eine Unterhaltung speichern?"
        )
        examples.forEach { text ->
            val decision = BootstrapBrain.decide(text, emptyList())
            assertEquals(text, BootstrapKind.REPLY, decision.kind)
            assertNull(text, decision.tool)
        }
        assertEquals("get_storage_info", BootstrapBrain.decide("Speicher", emptyList()).tool)
        assertEquals("get_storage_info", BootstrapBrain.decide("Speicherplatz prüfen", emptyList()).tool)
        assertEquals("get_storage_info", BootstrapBrain.decide("Große Dateien", emptyList()).tool)
    }

    @Test fun explicitMemoryAndSafetyRulesRemainInForce() {
        assertEquals(BootstrapKind.REMEMBER,
            BootstrapBrain.decide("Merke dir: Ich arbeite ohne Internet", emptyList()).kind)
        val forbidden = BootstrapBrain.decide(
            "Lösche Downloads, aber ohne Internetrecherche.", emptyList()
        )
        assertEquals(BootstrapKind.REPLY, forbidden.kind)
        assertNull(forbidden.tool)
        assertFalse(forbidden.allowNeuralRecall)
    }
}
