package at.heinz.deviceai.cognitive

/**
 * Presentation admission for the canonical local-reasoning scene.
 * It never grants tools or changes model/context budgets.
 */
internal object LocalReasoningWorkScenePolicy {
    const val SIMPLE_TURN_GRACE_MS = 1_000L

    data class Decision(
        val promptBudget: Int,
        val assessment: IntelligenceTaskAssessment,
        val delayMillis: Long
    ) {
        val immediate: Boolean get() = delayMillis == 0L
    }

    fun decide(
        task: String,
        hasMemories: Boolean,
        hasConversationContext: Boolean,
        hasGroundedEvidence: Boolean
    ): Decision {
        val assessment = ContinuumTaskClassifier.classify(task)
        val budget = LocalChatPromptBudget.limit(
            task = task,
            hasMemories = hasMemories,
            hasConversationContext = hasConversationContext,
            hasGroundedEvidence = hasGroundedEvidence,
            hasObservations = false,
            hasReflection = false
        )
        val immediate = budget == LocalChatPromptBudget.FULL_CHARACTERS ||
            assessment.expectedToolUse || assessment.freshnessNeeded ||
            assessment.verifyExistingAnswer || assessment.complexity > 0.35
        return Decision(
            promptBudget = budget,
            assessment = assessment,
            delayMillis = if (immediate) 0L else SIMPLE_TURN_GRACE_MS
        )
    }
}
