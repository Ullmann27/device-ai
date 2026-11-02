package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumBenchmarkTest {
    @Test
    fun benchmarkAggregatesOutcomeAndEvidenceMetrics() {
        val cases = listOf(
            ContinuumBenchmarkCase("research", BenchmarkCategory.RESEARCH_CITATION, "research this"),
            ContinuumBenchmarkCase("recovery", BenchmarkCategory.RECOVERY, "recover")
        )
        val (_, summary) = ContinuumBenchmarkHarness.run(cases) { case ->
            when (case.id) {
                "research" -> BenchmarkExecution(
                    success = true,
                    supportClaims = 4,
                    citedClaims = 3,
                    contradictionsDetected = 1,
                    expectedContradictions = 1,
                    invalidToolProposals = 0,
                    modelCalls = 4,
                    latencyMillis = 100,
                    contextBytes = 2_000
                )
                else -> BenchmarkExecution(
                    success = true,
                    modelCalls = 1,
                    latencyMillis = 20,
                    contextBytes = 500,
                    recoverySuccess = true
                )
            }
        }

        assertEquals(1.0, summary.successRate, 0.0001)
        assertEquals(0.75, summary.citationCoverage, 0.0001)
        assertEquals(1.0, summary.contradictionRecall, 0.0001)
        assertEquals(1.0, summary.recoverySuccessRate, 0.0001)
    }

    @Test
    fun modelMortalitySeparatesModelOutputFromCoreContinuity() {
        val fingerprint = ContinuityFingerprint(
            ContinuumInstanceId("same-instance"),
            GovernanceVersion(1),
            ContinuumObjectId.of(ContinuumObjectType.STATE_MANIFEST, "same-root".toByteArray()),
            setOf("memory-1"),
            setOf("skill-1")
        )
        val before = ModelContinuityRun("model-a", fingerprint, "answer A")
        val after = ModelContinuityRun("model-b", fingerprint, "answer B")

        val report = ModelMortalityExperiment.compare(before, after)

        assertTrue(report.coreContinuity)
        assertTrue(report.modelChanged)
        assertTrue(report.outputChanged)
    }
}
