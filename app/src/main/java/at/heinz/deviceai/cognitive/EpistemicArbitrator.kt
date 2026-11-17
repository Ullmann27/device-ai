package at.heinz.deviceai.cognitive

/** Weakest-link control policy over independently measured evidence dimensions. */
internal object EpistemicArbitrator {
    enum class Decision {
        ANSWER_DIRECTLY,
        ANSWER_WITH_QUALIFIER,
        VERIFY,
        RESEARCH,
        ASK_CLARIFICATION,
        EXECUTE_AUTHORIZED_TOOL,
        ABSTAIN
    }

    data class Vector(
        val referenceResolution: Float,
        val memory: Float,
        val retrieval: Float,
        val source: Float,
        val reasoning: Float,
        val execution: Float,
        val answerAlignment: Float
    ) {
        init {
            listOf(referenceResolution, memory, retrieval, source, reasoning, execution, answerAlignment)
                .forEach { require(it.isFinite() && it in 0f..1f) }
        }
    }

    fun decide(
        v: Vector,
        requiresToolExecution: Boolean,
        toolAuthorized: Boolean,
        canResearch: Boolean
    ): Decision {
        // Strong memory + answer disagreement is a verifier problem, not a reason to average confidence upward.
        if (v.memory >= .80f && v.answerAlignment < .50f) return Decision.VERIFY

        // Ambiguous referent/goal must be resolved before factual or action logic.
        if (v.referenceResolution < .60f) return Decision.ASK_CLARIFICATION

        val factualFloor = minOf(v.memory, v.retrieval, v.source)
        if (factualFloor < .45f) return if (canResearch) Decision.RESEARCH else Decision.ABSTAIN

        if (requiresToolExecution) {
            if (!toolAuthorized) return Decision.ASK_CLARIFICATION
            return if (factualFloor >= .80f && v.execution >= .85f)
                Decision.EXECUTE_AUTHORIZED_TOOL
            else Decision.VERIFY
        }

        if (factualFloor >= .75f && v.reasoning < .60f)
            return Decision.ANSWER_WITH_QUALIFIER

        if (v.answerAlignment < .70f) return Decision.VERIFY

        return Decision.ANSWER_DIRECTLY
    }
}
