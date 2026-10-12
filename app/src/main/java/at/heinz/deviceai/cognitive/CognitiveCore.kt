package at.heinz.deviceai.cognitive

data class Observation(val source: String, val content: String)
data class MemoryItem(val content: String, val provenance: String)
data class ModelRequest(val prompt: String, val context: List<MemoryItem> = emptyList())
data class Candidate(val modelId: String, val text: String, val score: Double = 0.0)
data class ToolCall(val tool: String, val args: Map<String, String> = emptyMap())
data class ToolResult(val ok: Boolean, val output: String)
data class Evaluation(val success: Boolean, val score: Double, val notes: String = "")
data class Experience(val summary: String, val evaluation: Evaluation)
data class BehaviorProposal(val trait: String, val delta: Double, val reason: String)
data class Checkpoint(val identity: IdentityState, val note: String)

/** Provider-agnostic cognitive engine; no vendor is hard-wired. */
interface ModelEngine { val id: String; fun ask(request: ModelRequest): Candidate }
interface Memory { fun add(item: MemoryItem); fun retrieve(query: String, limit: Int = 5): List<MemoryItem> }
interface ExperienceStore { fun store(e: Experience); fun all(): List<Experience> }
interface ModelRouter { fun select(request: ModelRequest, engines: List<ModelEngine>): List<ModelEngine> }
interface SkillGraph { fun register(name: String, dependsOn: List<String> = emptyList()); fun skills(): Set<String> }
interface SelfModel { fun update(evaluation: Evaluation); fun confidence(): Double }

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
    fun proposeBehaviorUpdate(p: BehaviorProposal): BehaviorProposal
    fun registerSkill(name: String, dependsOn: List<String> = emptyList())
    fun createCheckpoint(note: String): Checkpoint
}
