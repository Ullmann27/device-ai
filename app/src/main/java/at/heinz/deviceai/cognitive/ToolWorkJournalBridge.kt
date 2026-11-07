package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolExecutionStatus

/** Only the first read-only tool is journalled in Code47 M2. */
internal object ToolWorkJournalBridge {
    const val FIRST_TOOL = "get_storage_info"

    fun classify(proof: AgentToolEvidence, current: Boolean): CockpitWorkJournal.ToolState {
        if (!current || proof.toolName != FIRST_TOOL || proof.outcomeUncertain || proof.replayed)
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

    fun settle(journal: CockpitWorkJournal, proof: AgentToolEvidence, current: Boolean): CockpitWorkJournal.Episode? =
        if (proof.toolName != FIRST_TOOL) null else journal.finishTool(
            proof.callId, proof.toolName, classify(proof, current),
            proof.outputSha256.takeIf { current && classify(proof, current) == CockpitWorkJournal.ToolState.SUCCEEDED }
        )
}
