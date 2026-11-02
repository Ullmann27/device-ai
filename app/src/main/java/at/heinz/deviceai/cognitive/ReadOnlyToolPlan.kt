package at.heinz.deviceai.cognitive

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolExecutionRecord
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRegistry

/** Same bounded orchestrator and ToolGate, deterministic (never neural inference).
 * A failed, replayed or unknown observation stops the sequence without retry.
 * ToolGate receipts remain authoritative; this plan grants no permissions.
 */
class ReadOnlyToolPlan(proposals: List<ToolProposal>) : AgentModel, AgentEvaluator {
    private val steps=proposals.map { it.copy(arguments=it.arguments.toMap()) }
    constructor(proposal:ToolProposal):this(listOf(proposal))
    val stepCount:Int get()=steps.size
    val plannedToolNames:List<String> get()=steps.map { it.toolName }
    private var observed:List<AgentToolObservation> = emptyList()

    init {
        require(steps.size in 1..3) { "Only one to three read-only steps are permitted." }
        require(steps.map { it.toolName }.distinct().size==steps.size) { "Duplicate tools are not permitted." }
        steps.forEach {
            require(it.validate()==null) { "Invalid registered-tool plan." }
            require(ToolRegistry.get(it.toolName)?.risk==Risk.READ_ONLY) { "Only read-only tools are permitted." }
        }
    }

    override fun generate(task:String,memories:List<MemoryRecord>,reflection:AgentReflection?,
        toolObservations:List<AgentToolObservation>):AgentModelOutput {
        observed=emptyList()
        val valid=toolObservations.size<=steps.size && toolObservations.withIndex().all { (i,item) ->
            item.toolName==steps[i].toolName && item.status==ToolExecutionStatus.SUCCEEDED && !item.output.isNullOrBlank()
        }
        if(!valid)return AgentModelOutput(MODEL_ID,"Kein frisches, erfolgreiches Geräteergebnis vorhanden; weitere Schritte abgebrochen.")
        if(toolObservations.size<steps.size)return AgentModelOutput(MODEL_ID,"",steps[toolObservations.size])
        observed=toolObservations.toList()
        return AgentModelOutput(MODEL_ID,render(observed))
    }

    override fun evaluate(response:String,toolRecord:ToolExecutionRecord?):Evaluation {
        val last=observed.lastOrNull()
        val verified=observed.size==steps.size && last!=null && toolRecord?.toolName==steps.last().toolName &&
            toolRecord.risk==Risk.READ_ONLY && toolRecord.status==ToolExecutionStatus.SUCCEEDED &&
            toolRecord.outputLength==last.output?.length && response==render(observed)
        return Evaluation(verified,if(verified)1.0 else 0.0,
            if(verified)"Antwort entspricht allen frischen Tool-Beobachtungen."
            else "Kein vollständiges frisches Tool-Ergebnis; Aktion nicht bestätigt.")
    }

    private fun render(items:List<AgentToolObservation>):String=items.joinToString("\n\n") {
        "${ToolRegistry.label(it.toolName)}:\n${it.output}"
    }

    companion object {
        const val MODEL_ID="deterministic-read-only-plan-v1"
        /** Narrow explicit owner request only. Questions, quotes, negations and role
         * headers cannot expand an existing single-tool decision into extra actions. */
        fun forRequest(request:String,fallback:ToolProposal):ReadOnlyToolPlan {
            val combined=Regex("(?iu)^(?:continuum[,!]?\\s+)?(?:prüfe|überprüfe)\\s+(?:bitte\\s+)?(?:den\\s+)?(?:freien\\s+)?speicher\\s+und\\s+(?:die\\s+)?downloads[.!]?$")
            return if(fallback.toolName=="list_files" && fallback.arguments==mapOf("path" to "/sdcard/Download") && combined.matches(request.trim()))
                ReadOnlyToolPlan(listOf(ToolProposal("get_storage_info",emptyMap()),fallback))
            else ReadOnlyToolPlan(fallback)
        }
    }
}
