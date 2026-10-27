package at.heinz.deviceai.cognitive

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolExecutionRecord
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRegistry

/** Adapts an already-typed bootstrap decision to the SAME orchestrator used by
 * local generation. This is a deterministic one-tool plan, never neural inference.
 * Memories cannot add actions; the response must reproduce the fresh observation.
 */
class ReadOnlyToolPlan(private val proposal: ToolProposal) : AgentModel, AgentEvaluator {
    private var observed: AgentToolObservation? = null

    init {
        require(proposal.validate() == null) { "Invalid registered-tool plan." }
        require(ToolRegistry.get(proposal.toolName)?.risk == Risk.READ_ONLY) { "Only read-only tools are permitted." }
    }

    override fun generate(
        task: String, memories: List<MemoryRecord>, reflection: AgentReflection?,
        toolObservations: List<AgentToolObservation>
    ): AgentModelOutput {
        observed = toolObservations.singleOrNull()?.takeIf {
            it.toolName == proposal.toolName && it.status == ToolExecutionStatus.SUCCEEDED && !it.output.isNullOrBlank()
        }
        return when {
            toolObservations.isEmpty() -> AgentModelOutput(MODEL_ID, "", proposal)
            observed != null -> AgentModelOutput(MODEL_ID, render(checkNotNull(observed).output!!))
            else -> AgentModelOutput(MODEL_ID, "Kein frisches, erfolgreiches Geräteergebnis vorhanden.")
        }
    }

    override fun evaluate(response: String, toolRecord: ToolExecutionRecord?): Evaluation {
        val output = observed?.output
        val verified = output != null && toolRecord?.toolName == proposal.toolName &&
            toolRecord.risk == Risk.READ_ONLY && toolRecord.status == ToolExecutionStatus.SUCCEEDED &&
            toolRecord.outputLength == output.length && response == render(output)
        return Evaluation(verified, if (verified) 1.0 else 0.0,
            if (verified) "Antwort entspricht der frischen Tool-Beobachtung."
            else "Kein passendes frisches Tool-Ergebnis; Aktion nicht bestätigt.")
    }

    private fun render(output: String): String = "${ToolRegistry.label(proposal.toolName)}:\n$output"

    companion object { const val MODEL_ID = "deterministic-read-only-plan-v1" }
}
