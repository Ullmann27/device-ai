package at.heinz.deviceai.continuum

import at.heinz.deviceai.cognitive.MemoryRecord
import java.security.MessageDigest

enum class BenchmarkTaskFamily {
    DEVICE_REASONING, CODE_REASONING, TOOL_PLANNING, RESEARCH_CITATION,
    CONTRADICTION_HANDLING, MEMORY_RETRIEVAL, CONSTRAINT_PLANNING,
    RECOVERY, MODEL_CONTINUITY, RESOURCE_CONSTRAINED_REASONING
}

data class BenchmarkCase(
    val id: String,
    val family: BenchmarkTaskFamily,
    val prompt: String
) {
    init {
        require(id.matches(Regex("[a-z0-9._-]{1,64}")))
        require(prompt.isNotBlank() && prompt.length <= 8_000)
    }
}

object FixedBenchmarkCorpus {
    val cases = listOf(
        BenchmarkCase("device-001", BenchmarkTaskFamily.DEVICE_REASONING, "Diagnose a device issue from supplied observations."),
        BenchmarkCase("code-001", BenchmarkTaskFamily.CODE_REASONING, "Explain a bounded code change and its regression risk."),
        BenchmarkCase("tools-001", BenchmarkTaskFamily.TOOL_PLANNING, "Propose a registered tool plan without executing it."),
        BenchmarkCase("research-001", BenchmarkTaskFamily.RESEARCH_CITATION, "Assess supplied claims and cite supporting evidence."),
        BenchmarkCase("contradiction-001", BenchmarkTaskFamily.CONTRADICTION_HANDLING, "Identify and explain conflicting evidence."),
        BenchmarkCase("memory-001", BenchmarkTaskFamily.MEMORY_RETRIEVAL, "Answer using only relevant permitted memory."),
        BenchmarkCase("constraints-001", BenchmarkTaskFamily.CONSTRAINT_PLANNING, "Plan a solution under explicit constraints."),
        BenchmarkCase("recovery-001", BenchmarkTaskFamily.RECOVERY, "Recover from a failed bounded task without repeating side effects."),
        BenchmarkCase("continuity-001", BenchmarkTaskFamily.MODEL_CONTINUITY, "Preserve identity and governance across model replacement."),
        BenchmarkCase("resource-001", BenchmarkTaskFamily.RESOURCE_CONSTRAINED_REASONING, "Solve a task within fixed context and call budgets.")
    )
}

data class BenchmarkMetrics(
    val success: Boolean,
    val supportCoverage: Double,
    val citationCoverage: Double,
    val contradictionDetection: Double,
    val invalidToolProposalCount: Int,
    val modelCallCount: Int,
    val latencyMillis: Long,
    val contextBytes: Int,
    val humanCorrectionRequired: Boolean,
    val recoverySuccess: Boolean,
    val optionalReportedMetrics: Map<String, Double> = emptyMap()
) {
    init {
        require(supportCoverage in 0.0..1.0 && citationCoverage in 0.0..1.0)
        require(contradictionDetection in 0.0..1.0)
        require(invalidToolProposalCount >= 0 && modelCallCount >= 0)
        require(latencyMillis >= 0 && contextBytes >= 0)
        require(optionalReportedMetrics.size <= 32)
        require(optionalReportedMetrics.all { (key, value) ->
            key.matches(Regex("[A-Za-z0-9._-]{1,64}")) && value.isFinite()
        })
    }
}

data class BenchmarkObservation(val caseId: String, val metrics: BenchmarkMetrics)

fun interface BenchmarkEvaluator {
    fun evaluate(case: BenchmarkCase, latencyMillis: Long): BenchmarkMetrics
}

object BenchmarkHarness {
    fun run(
        cases: List<BenchmarkCase>,
        latencyMillisByCase: Map<String, Long>,
        evaluator: BenchmarkEvaluator
    ): List<BenchmarkObservation> {
        require(cases.isNotEmpty() && cases.size <= 1_000)
        require(cases.map { it.id }.distinct().size == cases.size)
        return cases.map { case ->
            val latency = latencyMillisByCase[case.id] ?: error("Harness latency missing for ${case.id}.")
            require(latency >= 0)
            val metrics = evaluator.evaluate(case, latency)
            require(metrics.latencyMillis == latency) { "Evaluator must use harness-supplied latency." }
            BenchmarkObservation(case.id, metrics)
        }
    }
}

data class ContinuityStateSnapshot(
    val seed: ContinuumSeed,
    val stateRoot: ContinuumObjectId,
    val memoryIds: List<String>,
    val skillIds: List<String>
) {
    init {
        require(memoryIds.size <= 4_096 && skillIds.size <= 512)
        require(memoryIds.all { it.matches(Regex("[A-Za-z0-9._-]{1,96}")) })
        require(skillIds.all { it.matches(Regex("[A-Za-z0-9._-]{1,96}")) })
        require(memoryIds.distinct().size == memoryIds.size && skillIds.distinct().size == skillIds.size)
    }
}

data class ContinuityModelOutput(val modelId: String, val text: String) {
    init { require(modelId.isNotBlank() && modelId.length <= 128 && text.length <= 8_000) }
}

fun interface ContinuityModel {
    fun respond(context: String): ContinuityModelOutput
}

data class ContinuityExperimentResult(
    val identityContinuous: Boolean,
    val governanceContinuous: Boolean,
    val stateContinuous: Boolean,
    val contextContinuous: Boolean,
    val modelSpecificOutputsDiffer: Boolean,
    val modelAOutputHash: String,
    val modelBOutputHash: String
)

object ModelContinuityHarness {
    fun run(
        snapshot: ContinuityStateSnapshot,
        request: String,
        memory: List<MemoryRecord>,
        compiler: AiContextCompiler,
        modelA: ContinuityModel,
        modelB: ContinuityModel
    ): ContinuityExperimentResult {
        require(snapshot.seed.currentStateRoot == snapshot.stateRoot)
        require(memory.map { it.id }.toSet() == snapshot.memoryIds.toSet())
        val seedBefore = ContinuumSeedCodec.encode(snapshot.seed)
        val governanceBefore = snapshot.seed.governanceVersion
        val rootBefore = snapshot.stateRoot
        val memoryIdsBefore = snapshot.memoryIds.toSet()
        val skillIdsBefore = snapshot.skillIds.toSet()
        val invariants = listOf(
            "Continuum instance ${snapshot.seed.instanceId.value}",
            "Governance version ${snapshot.seed.governanceVersion.value}",
            "State root ${snapshot.stateRoot.value}",
            "Authorized nodes and owner authority remain seed-controlled."
        )
        val common = AiContextInput(
            userRequest = request,
            identityInvariants = invariants,
            memories = memory.map { ContextMemory(it, 1.0) },
            availableSkillsAndTools = snapshot.skillIds.sorted(),
            localModelCapabilitySummary = "Model adapter ABI ${snapshot.seed.modelAdapterAbiVersion.value}",
            unresolvedContradictions = emptyList()
        )
        val contextA = compiler.compile(common)
        val contextB = compiler.compile(common.copy())
        val outputA = modelA.respond(contextA.text)
        val outputB = modelB.respond(contextB.text)
        val hashA = sha256Hex(outputA.text.toByteArray(Charsets.UTF_8))
        val hashB = sha256Hex(outputB.text.toByteArray(Charsets.UTF_8))
        return ContinuityExperimentResult(
            identityContinuous = seedBefore.contentEquals(ContinuumSeedCodec.encode(snapshot.seed)),
            governanceContinuous = governanceBefore == snapshot.seed.governanceVersion,
            stateContinuous = rootBefore == snapshot.stateRoot &&
                memoryIdsBefore == snapshot.memoryIds.toSet() && skillIdsBefore == snapshot.skillIds.toSet(),
            contextContinuous = contextA == contextB,
            modelSpecificOutputsDiffer = hashA != hashB,
            modelAOutputHash = hashA,
            modelBOutputHash = hashB
        )
    }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
