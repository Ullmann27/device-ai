package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Code55 field P0: model and capability follow-up questions went to web or memory. */
class OwnerChatModelIdentityTest {
    @Test fun modelQuestionsAreAnsweredFromTheRuntime() {
        listOf("Welches lokale KI-Modell verwendest du?", "Welches Modell nutzt du gerade?",
            "Mit welchem Modell arbeitest du?", "Welches Sprachmodell verwendest du", "Welches KI-Modell läuft gerade?",
            "Was für ein Modell hast du?"
        ).forEach {
            assertEquals(it, OwnerChatLocalIntent.MODEL_IDENTITY, OwnerChatIntentRouter.route(it))
            assertNull(it, PublicKnowledgeTopicRouter.topic(it))
        }
    }

    @Test fun capabilityFollowUpsStayLocal() {
        listOf("Was kannst du sonst noch?", "Was kannst du noch alles?", "Was kannst du denn noch").forEach {
            assertEquals(it, OwnerChatLocalIntent.CAPABILITIES, OwnerChatIntentRouter.route(it))
        }
    }

    @Test fun publicModelTopicsAndCombinedOrdersAreNotCaptured() {
        listOf("Welches Modell hat das Galaxy Fold7?", "Welches Modell verwendest du und lösche den Verlauf",
            "Welches KI-Modell ist das beste?", "Was kannst du sonst noch löschen?"
        ).forEach { assertNull(it, OwnerChatIntentRouter.route(it)) }
    }

    @Test fun modelLineNamesOnlyTheLoadedModel() {
        val ready = OwnerCapabilitySnapshot(OwnerCapabilitySnapshot.ModelState.READY, "gemma-3n-e4b 1")
        assertTrue(OwnerCapabilityReply.describeModel(ready).contains("gemma-3n-e4b 1 ist geladen"))
        val none = OwnerCapabilitySnapshot(OwnerCapabilitySnapshot.ModelState.NOT_LOADED)
        assertTrue(OwnerCapabilityReply.describeModel(none).contains("keines geladen"))
    }
}
