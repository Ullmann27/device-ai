package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumChatOutcome
import at.heinz.deviceai.learning.WorkingTurn

/**
 * Chat-turn chain (GROK2-HANDOFF §5 H1): today's outcome → EWM2 brain → temporal memory
 * specialist. Each stage gets the previous stage's outcome as its baseline; a failing stage
 * keeps its input. Both stages are owner-switched (default OFF = unchanged behaviour).
 * The working memory is this session's own earlier owner turns, RAM only, never stored.
 */
internal object ChatSpecialistChain {
    private const val MAX_TURNS = 64
    private const val MAX_TURN_CHARS = 4096

    fun workingTurns(priorOwnerTurns: List<String>, nowMillis: Long): List<WorkingTurn> {
        val recent = priorOwnerTurns.takeLast(MAX_TURNS)
        // Chronological, strictly before "now": the current request is never its own history.
        return recent.mapIndexed { i, text -> WorkingTurn(text.take(MAX_TURN_CHARS), nowMillis - (recent.size - i)) }
    }

    fun run(baseline: ContinuumChatOutcome,
            brain: (ContinuumChatOutcome) -> ContinuumChatOutcome,
            temporal: (ContinuumChatOutcome) -> ContinuumChatOutcome): ContinuumChatOutcome {
        val brainShown = runCatching { brain(baseline) }.getOrDefault(baseline)
        return runCatching { temporal(brainShown) }.getOrDefault(brainShown)
    }
}
