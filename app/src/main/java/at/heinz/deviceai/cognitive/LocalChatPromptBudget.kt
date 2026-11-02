package at.heinz.deviceai.cognitive

/**
 * A small first-turn conversation needs a smaller CPU prefill than a coding
 * job with evidence, owner memories or tool observations. The normal full
 * context path remains available whenever any such constraint applies.
 *
 * Budget selection does not bypass tool authorization, memory provenance,
 * the original request or the native model identity checks.
 */
internal object LocalChatPromptBudget {
    const val MAX_TASK_CHARACTERS = 7_900
    fun acceptsCompleteTask(task: String): Boolean = task.isNotBlank() && task.length <= MAX_TASK_CHARACTERS

    const val FULL_CHARACTERS = 15_500
    const val BRIEF_FIRST_TURN_CHARACTERS = 6_000

    fun limit(
        task: String,
        hasMemories: Boolean,
        hasConversationContext: Boolean,
        hasGroundedEvidence: Boolean,
        hasObservations: Boolean,
        hasReflection: Boolean
    ): Int {
        if (task.isBlank() || task.length !in 1..240 || hasMemories || hasConversationContext ||
            hasGroundedEvidence || hasObservations || hasReflection) return FULL_CHARACTERS
        val assessment = ContinuumTaskClassifier.classify(task)
        if (assessment.expectedToolUse || assessment.freshnessNeeded ||
            assessment.verifyExistingAnswer || assessment.complexity > 0.35)
            return FULL_CHARACTERS
        return BRIEF_FIRST_TURN_CHARACTERS
    }
}
