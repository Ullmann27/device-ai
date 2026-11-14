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
    const val MAX_TASK_CHARACTERS = LocalDialogueContextLimits.MAX_OWNER_CHARACTERS
    // Reject a complete request early if it exceeds either conservative model
    // budget. A multilingual suffix must never be clipped later by LocalPromptFrame.
    fun acceptsCompleteTask(task: String): Boolean = task.isNotBlank() &&
        task.length <= MAX_TASK_CHARACTERS &&
        task.toByteArray(Charsets.UTF_8).size <= MAX_TASK_CHARACTERS

    const val FULL_CHARACTERS = 15_500
    const val BRIEF_FIRST_TURN_CHARACTERS = 6_000

    // Brief-first-turn is a speed optimization, never an instruction to
    // contradict an owner's explicit long-form answer request. This changes
    // prompt framing only; the loaded native context/output limits still apply.
    private val longReplyCue = Regex(
        """(?iu)(?<!\p{L})(?:ausführlich\p{L}*|detailliert\p{L}*|umfangreich\p{L}*|""" +
            """(?:in|mindestens)\s+(?:[4-9]|\d{2,}|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf|dreizehn|vierzehn|fünfzehn|zwanzig)\s+(?:sätzen?|absätzen?|punkten?))(?!\p{L})"""
    )

    /** The prompt frame never exceeds the loaded model's native window budget (Code56); unknown keeps [cap]. */
    fun withNativeBudget(cap: Int, nativeBytes: Int?): Int =
        if (nativeBytes == null) cap else minOf(cap, nativeBytes).coerceAtLeast(1)

    fun limit(
        task: String,
        hasMemories: Boolean,
        hasConversationContext: Boolean,
        hasGroundedEvidence: Boolean,
        hasObservations: Boolean,
        hasReflection: Boolean
    ): Int {
        if (task.isBlank() || task.length > 240 || hasMemories || hasConversationContext ||
            hasGroundedEvidence || hasObservations || hasReflection || longReplyCue.containsMatchIn(task))
            return FULL_CHARACTERS
        val assessment = ContinuumTaskClassifier.classify(task)
        if (assessment.expectedToolUse || assessment.freshnessNeeded ||
            assessment.verifyExistingAnswer || assessment.complexity > 0.35)
            return FULL_CHARACTERS
        return BRIEF_FIRST_TURN_CHARACTERS
    }
}
