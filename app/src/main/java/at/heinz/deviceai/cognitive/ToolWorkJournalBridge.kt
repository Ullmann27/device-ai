package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolExecutionStatus

/** Bridges trusted ToolGate evidence into the bounded work journal. */
internal object ToolWorkJournalBridge {
    /** Retained for older tests/callers; no longer the only journalled tool. */
    const val FIRST_TOOL = "get_storage_info"

    fun classify(proof: AgentToolEvidence, current: Boolean): CockpitWorkJournal.ToolState {
        if (!current || proof.outcomeUncertain || proof.replayed)
            return CockpitWorkJournal.ToolState.UNKNOWN
        return when (proof.status) {
            ToolExecutionStatus.SUCCEEDED ->
                if (proof.freshOutput && proof.outputSha256?.matches(Regex("[a-f0-9]{64}")) == true)
                    CockpitWorkJournal.ToolState.SUCCEEDED else CockpitWorkJournal.ToolState.UNKNOWN
            ToolExecutionStatus.REJECTED -> CockpitWorkJournal.ToolState.REJECTED
            ToolExecutionStatus.FAILED -> CockpitWorkJournal.ToolState.FAILED
            ToolExecutionStatus.CANCELLED -> CockpitWorkJournal.ToolState.CANCELLED
            ToolExecutionStatus.TIMED_OUT -> CockpitWorkJournal.ToolState.UNKNOWN
        }
    }

    fun settle(journal: CockpitWorkJournal, proof: AgentToolEvidence, current: Boolean): CockpitWorkJournal.Episode? {
        val state = classify(proof, current)
        return journal.finishTool(
            proof.callId, proof.toolName, state,
            proof.outputSha256.takeIf { current && state == CockpitWorkJournal.ToolState.SUCCEEDED }
        )
    }
}
