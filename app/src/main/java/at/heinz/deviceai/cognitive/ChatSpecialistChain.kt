package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumChatOutcome
import at.heinz.deviceai.learning.WorkingTurn

/** RED stub: the temporal memory specialist (#371) is not part of the chat turn yet. */
internal object ChatSpecialistChain {
    fun workingTurns(priorOwnerTurns: List<String>, nowMillis: Long): List<WorkingTurn> = emptyList()
    fun run(baseline: ContinuumChatOutcome,
            brain: (ContinuumChatOutcome) -> ContinuumChatOutcome,
            temporal: (ContinuumChatOutcome) -> ContinuumChatOutcome): ContinuumChatOutcome =
        runCatching { brain(baseline) }.getOrDefault(baseline)
}
