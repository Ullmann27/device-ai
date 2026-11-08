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
            "Vergleiche mehrere Bedingungen und entwickle eine ausführliche Strategie".padEnd(200, ' ')
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

    @Test fun complexityThresholdIsStrictAndKeepsShortPlanningInGrace() {
        val shortPlan = LocalReasoningWorkScenePolicy.decide(
            "Vergleiche mehrere Bedingungen und entwickle eine ausführliche Strategie", false, false, false)
        assertEquals(.30, shortPlan.assessment.complexity, 1e-9)
        assertEquals(1_000L, shortPlan.delayMillis)
        val boundary = LocalReasoningWorkScenePolicy.decide(
            "Wie funktioniert das?".padEnd(200, ' '), false, false, false)
        assertEquals(.35, boundary.assessment.complexity, 1e-9)
        assertEquals(LocalChatPromptBudget.BRIEF_FIRST_TURN_CHARACTERS, boundary.promptBudget)
        assertEquals(1_000L, boundary.delayMillis)
        val above = LocalReasoningWorkScenePolicy.decide(
            "Vergleiche mehrere Bedingungen.".padEnd(200, ' '), false, false, false)
        assertTrue(above.assessment.complexity > .35)
        assertEquals(0L, above.delayMillis)
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
