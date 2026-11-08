package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReasoningWorkScenePolicyTest {
    @Test fun shortSimpleFirstTurnUsesOneSecondGrace() {
        val d = LocalReasoningWorkScenePolicy.decide(
            "Hallo, wie geht es dir?",
            hasMemories = false,
            hasConversationContext = false,
            hasGroundedEvidence = false
        )
        assertEquals(LocalChatPromptBudget.BRIEF_FIRST_TURN_CHARACTERS, d.promptBudget)
        assertEquals(1_000L, d.delayMillis)
        assertFalse(d.immediate)
    }

    @Test fun complexToolAndGroundedTurnsOpenImmediately() {
        for (task in listOf(
            "Öffne die App Einstellungen",
            "Recherchiere die aktuellen Quellen und prüfe die Aussage",
            "Vergleiche mehrere Bedingungen und entwickle eine ausführliche Strategie"
        )) {
            assertTrue(task, LocalReasoningWorkScenePolicy.decide(
                task, false, false, false
            ).immediate)
        }
        assertTrue(LocalReasoningWorkScenePolicy.decide(
            "Was war die Entscheidung davor?",
            hasMemories = true,
            hasConversationContext = false,
            hasGroundedEvidence = true
        ).immediate)
    }

    @Test fun priorConversationContextUsesFullBudgetAndImmediateScene() {
        val d = LocalReasoningWorkScenePolicy.decide(
            "Und die zweite Variante?",
            hasMemories = false,
            hasConversationContext = true,
            hasGroundedEvidence = false
        )
        assertEquals(LocalChatPromptBudget.FULL_CHARACTERS, d.promptBudget)
        assertTrue(d.immediate)
    }
}
