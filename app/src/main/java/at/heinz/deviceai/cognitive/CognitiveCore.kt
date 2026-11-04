package at.heinz.deviceai.cognitive

data class Observation(val source: String, val content: String)
data class MemoryItem(val content: String, val provenance: String)
data class ModelRequest(val prompt: String, val context: List<MemoryItem> = emptyList())
data class Candidate(val modelId: String, val text: String, val score: Double = 0.0)
data class ToolCall(val tool: String, val args: Map<String, String> = emptyMap())
data class ToolResult(val ok: Boolean, val output: String)
data class Evaluation(val success: Boolean, val score: Double, val notes: String = "")
enum class ExperienceOutcome { SUCCESS, PARTIAL, FAILURE, CANCELLED }

data class Experience(
    val id: String,
    val taskType: String,
    val requestSummary: String,
    val memoryIds: List<String> = emptyList(),
    val modelIds: List<String> = emptyList(),
    val sourceIds: List<String> = emptyList(),
    val toolIds: List<String> = emptyList(),
    val planSummary: String = "",
    val resultSummary: String = "",
    val failures: List<String> = emptyList(),
    val retries: Int = 0,
    val evaluatorScores: Map<String, Double> = emptyMap(),
    val feedback: String? = null,
    val confidenceBefore: Double = 0.5,
    val confidenceAfter: Double = 0.5,
    val outcome: ExperienceOutcome = ExperienceOutcome.PARTIAL,
    val startedAt: Long,
    val completedAt: Long,
    val evaluation: Evaluation
) {
    init {
        require(id.isNotBlank() && taskType.isNotBlank() && requestSummary.isNotBlank())
        require(retries >= 0 && startedAt >= 0 && completedAt >= startedAt)
        require(evaluatorScores.keys.none { it.isBlank() })
        require((evaluatorScores.values + confidenceBefore + confidenceAfter + evaluation.score).all { it.isFinite() && it in 0.0..1.0 })
    }
}
data class Checkpoint(val identity: IdentityState, val note: String)

/** Provider-agnostic cognitive engine; no vendor is hard-wired. */
interface ModelEngine { val id: String; fun ask(request: ModelRequest): Candidate }
interface Memory { fun add(item: MemoryItem); fun retrieve(query: String, limit: Int = 5): List<MemoryItem> }
interface ExperienceStore { fun store(e: Experience); fun all(): List<Experience> }
interface SkillGraph { fun register(name: String, dependsOn: List<String> = emptyList()); fun skills(): Set<String> }

/** Primitive operations of the cognitive core. */
interface CognitiveCore {
    fun observe(o: Observation)
    fun retrieveMemory(query: String): List<MemoryItem>
    fun askModel(request: ModelRequest): List<Candidate>
    fun compareCandidates(candidates: List<Candidate>): Candidate?
    fun callTool(call: ToolCall): ToolResult
    fun evaluateResult(result: ToolResult): Evaluation
    fun storeExperience(e: Experience)
    fun updateSelfModel(e: Evaluation)
    /** Proposal only; applied solely with operator approval. */
    fun proposeBehaviorUpdate(p: BehaviorProposal): UpdateDecision
    fun registerSkill(name: String, dependsOn: List<String> = emptyList())
    fun createCheckpoint(note: String): Checkpoint
}
