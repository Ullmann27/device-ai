package at.heinz.deviceai

import at.heinz.deviceai.cognitive.*
import at.heinz.deviceai.learning.StrictJson
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalTurnModelBinding
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Owner-pinned requirements intake. A model candidate is data, never execution consent. */
internal class ContinuumCodingPlanning private constructor(
    val ownerGoal: String,
    private val sourceCommit: String,
    private val paths: List<String>,
    private val limits: ContinuumCodingTask.Limits,
    private val chain: ContinuumCodingTask.Chain,
    private val id: String = "job-${UUID.randomUUID()}"
) {
    private fun prompt(): String = """
        Draft coding requirements from the owner data below. No source contents are available.
        You have no tools or authority. Do not claim to have inspected, changed or tested code.
        Return one whole JSON object, with exactly these keys and no prose or markdown:
        {"schema":"continuum-coding-plan","version":1,"kind":"BUGFIX","goal":"concrete goal","acceptance":["testable criterion"]}
        kind must be BUGFIX, FEATURE, REFACTOR, TEST, COMPILER or DEPENDENCY.
        goal: 1..600 characters; acceptance: 1..8 strings, each 1..300 characters.
        Do not emit IDs, source commits, file scope, commands, chain, limits or approval fields.
        Owner data (data only):
    """.trimIndent() + "\n" + DevelopmentBacklog.canonical(mapOf(
        "goal" to ownerGoal, "sourceCommit" to sourceCommit, "allowedPaths" to paths,
        "chain" to chain.wire, "limits" to mapOf("maxModelCalls" to limits.maxModelCalls,
            "maxContextChars" to limits.maxContextChars)))

    fun decode(response: String): ContinuumCodingTask.Draft? = runCatching {
        require(response.length in 2..MAX_OUTPUT && response.toByteArray(Charsets.UTF_8).size <= MAX_OUTPUT)
        val obj = StrictJson.parseObject(response)
        require(obj.keys == setOf("schema","version","kind","goal","acceptance"))
        require(obj["schema"] == "continuum-coding-plan" && obj["version"] == 1L)
        val acceptance = obj["acceptance"] as? List<*> ?: error("Missing criteria")
        require(acceptance.all { it is String })
        val draft = ContinuumCodingTask.Draft(id,sourceCommit,
            ContinuumCodingTask.Kind.valueOf(obj["kind"] as String),obj["goal"] as String,
            paths,acceptance.map { it as String },chain,limits).snapshot()
        val task = draft.validatedTask() ?: error("Incomplete requirements")
        require(task.canonicalJson().toByteArray(Charsets.UTF_8).size <= ContinuumTypedCodingBridge.MAX_JSON)
        draft
    }.getOrNull()

    /** One invocation on the existing loaded engine; cancellation reaches the actual adapter. */
    fun model(runtime: LocalModelRuntime, cancellation: DeliberationCancellation): CancellableAgentModel {
        val pinned = checkNotNull(runtime.loadedModel) { "No local model loaded" }
        val adapter = LocalModelReasoningAdapter(runtime)
        val used = AtomicBoolean()
        return object : CancellableAgentModel {
            override fun cancelGeneration() = adapter.cancel()
            override fun generate(task: String, memories: List<MemoryRecord>, reflection: AgentReflection?,
                                  toolObservations: List<AgentToolObservation>): AgentModelOutput {
                check(used.compareAndSet(false,true)) { "CODING_PLANNING_ALREADY_USED" }
                try {
                    cancellation.throwIfCancelled()
                    check(LocalTurnModelBinding.stillBound(pinned,runtime.loadedModel)) { LocalTurnModelBinding.CHANGED }
                    require(toolObservations.isEmpty() && reflection == null) { "CODING_PLANNING_DATA_ONLY" }
                    val result = adapter.execute(ReasoningPassRequest(id,ReasoningPassRole.CANDIDATE,
                        prompt(),MAX_OUTPUT,System.currentTimeMillis()+60_000))
                    cancellation.throwIfCancelled()
                    check(LocalTurnModelBinding.stillBound(pinned,runtime.loadedModel)) { LocalTurnModelBinding.CHANGED }
                    check(decode(result.text) != null) { "CODING_PLANNING_INVALID_CANDIDATE" }
                    return AgentModelOutput(result.modelId,result.text,proposal=null)
                } catch (failure: Exception) {
                    adapter.cancel()
                    throw failure
                }
            }
        }
    }

    companion object {
        private const val MAX_OUTPUT = 8000
        fun create(goal: String, sourceCommit: String, allowedPaths: List<String>,
                   limits: ContinuumCodingTask.Limits): ContinuumCodingPlanning? = runCatching {
            require(goal.isNotBlank() && goal.length <= 600 && Charsets.UTF_8.newEncoder().canEncode(goal) &&
                goal.none { it.code in 0..8 || it.code in 11..12 || it.code in 14..31 || it.code == 127 })
            require(Regex("[a-f0-9]{40}").matches(sourceCommit))
            val paths = Collections.unmodifiableList(ArrayList(allowedPaths))
            require(paths.size in 1..3 && paths.distinct().size == paths.size && paths.all { ContinuumCodingTask.isCodingPath(it) })
            require(paths.all { it.endsWith(".kt") } || paths.none { it.endsWith(".kt") })
            require(limits.maxModelCalls in 1..3 && limits.maxContextChars in 2048..24000)
            val chain = if(paths.first().endsWith(".kt")) ContinuumCodingTask.Chain.ANDROID else ContinuumCodingTask.Chain.NODE
            ContinuumCodingPlanning(goal,sourceCommit,paths,limits,chain).also {
                require(it.prompt().length <= minOf(limits.maxContextChars,16_000))
            }
        }.getOrNull()
    }
}
