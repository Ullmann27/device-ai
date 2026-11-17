package at.heinz.deviceai.cognitive

enum class BenchmarkCategory {
    DEVICE_REASONING,
    CODE_REASONING,
    TOOL_PLANNING,
    RESEARCH_CITATION,
    CONTRADICTION_HANDLING,
    MEMORY_RETRIEVAL,
    CONSTRAINT_PLANNING,
    RECOVERY,
    MODEL_CONTINUITY,
    RESOURCE_CONSTRAINED_REASONING
}

data class ContinuumBenchmarkCase(
    val id: String,
    val category: BenchmarkCategory,
    val prompt: String
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(prompt.isNotBlank() && prompt.length <= 8_192)
    }
}

data class BenchmarkExecution(
    val success: Boolean,
    val supportClaims: Int = 0,
    val citedClaims: Int = 0,
    val contradictionsDetected: Int = 0,
    val expectedContradictions: Int = 0,
    val invalidToolProposals: Int = 0,
    val modelCalls: Int = 1,
    val latencyMillis: Long,
    val contextBytes: Int,
    val humanCorrectionNeeded: Boolean = false,
    val recoverySuccess: Boolean = false
) {
    init {
        require(supportClaims >= 0 && citedClaims in 0..supportClaims)
        require(contradictionsDetected >= 0 && expectedContradictions >= 0)
        require(invalidToolProposals >= 0 && modelCalls >= 0)
        require(latencyMillis >= 0 && contextBytes >= 0)
    }
}

fun interface ContinuumBenchmarkExecutor {
    fun execute(case: ContinuumBenchmarkCase): BenchmarkExecution
}

data class ContinuumBenchmarkResult(
    val case: ContinuumBenchmarkCase,
    val execution: BenchmarkExecution
)

data class ContinuumBenchmarkSummary(
    val totalCases: Int,
    val successRate: Double,
    val citationCoverage: Double,
    val contradictionRecall: Double,
    val invalidToolProposalCount: Int,
    val totalModelCalls: Int,
    val totalLatencyMillis: Long,
    val totalContextBytes: Long,
    val humanCorrectionCount: Int,
    val recoverySuccessRate: Double
) {
    init {
        require(totalCases >= 0)
        require(successRate in 0.0..1.0 && citationCoverage in 0.0..1.0 && contradictionRecall in 0.0..1.0)
        require(recoverySuccessRate in 0.0..1.0)
    }
}

object ContinuumBenchmarkHarness {
    fun run(
        cases: List<ContinuumBenchmarkCase>,
        executor: ContinuumBenchmarkExecutor
    ): Pair<List<ContinuumBenchmarkResult>, ContinuumBenchmarkSummary> {
        require(cases.isNotEmpty() && cases.size <= 10_000)
        require(cases.map { it.id }.distinct().size == cases.size)
        val results = cases.map { ContinuumBenchmarkResult(it, executor.execute(it)) }
        val executions = results.map { it.execution }
        val supports = executions.sumOf { it.supportClaims }
        val cited = executions.sumOf { it.citedClaims }
        val expectedContradictions = executions.sumOf { it.expectedContradictions }
        val detected = executions.sumOf { minOf(it.contradictionsDetected, it.expectedContradictions) }
        val recoveryCases = results.filter { it.case.category == BenchmarkCategory.RECOVERY }
        val summary = ContinuumBenchmarkSummary(
            totalCases = results.size,
            successRate = executions.count { it.success }.toDouble() / results.size,
            citationCoverage = if (supports == 0) 1.0 else cited.toDouble() / supports,
            contradictionRecall = if (expectedContradictions == 0) 1.0 else detected.toDouble() / expectedContradictions,
            invalidToolProposalCount = executions.sumOf { it.invalidToolProposals },
            totalModelCalls = executions.sumOf { it.modelCalls },
            totalLatencyMillis = executions.sumOf { it.latencyMillis },
            totalContextBytes = executions.sumOf { it.contextBytes.toLong() },
            humanCorrectionCount = executions.count { it.humanCorrectionNeeded },
            recoverySuccessRate = if (recoveryCases.isEmpty()) 1.0 else
                recoveryCases.count { it.execution.recoverySuccess }.toDouble() / recoveryCases.size
        )
        return results to summary
    }
}

data class ContinuityFingerprint(
    val instanceId: ContinuumInstanceId,
    val governanceVersion: GovernanceVersion,
    val stateRoot: ContinuumObjectId,
    val memoryIds: Set<String>,
    val skillIds: Set<String>
) {
    init {
        require(memoryIds.size <= 100_000 && skillIds.size <= 10_000)
        require(memoryIds.none(String::isBlank) && skillIds.none(String::isBlank))
    }
}

data class ModelContinuityRun(
    val modelId: String,
    val fingerprint: ContinuityFingerprint,
    val modelOutput: String
) {
    init { require(modelId.isNotBlank() && modelOutput.length <= 64_000) }
}

data class ModelMortalityReport(
    val identityContinuous: Boolean,
    val governanceContinuous: Boolean,
    val stateContinuous: Boolean,
    val memoryContinuous: Boolean,
    val skillsContinuous: Boolean,
    val modelChanged: Boolean,
    val outputChanged: Boolean
) {
    val coreContinuity: Boolean
        get() = identityContinuous && governanceContinuous && stateContinuous && memoryContinuous && skillsContinuous
}

object ModelMortalityExperiment {
    fun compare(before: ModelContinuityRun, after: ModelContinuityRun): ModelMortalityReport =
        ModelMortalityReport(
            identityContinuous = before.fingerprint.instanceId == after.fingerprint.instanceId,
            governanceContinuous = before.fingerprint.governanceVersion == after.fingerprint.governanceVersion,
            stateContinuous = before.fingerprint.stateRoot == after.fingerprint.stateRoot,
            memoryContinuous = before.fingerprint.memoryIds == after.fingerprint.memoryIds,
            skillsContinuous = before.fingerprint.skillIds == after.fingerprint.skillIds,
            modelChanged = before.modelId != after.modelId,
            outputChanged = before.modelOutput != after.modelOutput
        )
}
