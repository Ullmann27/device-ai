package at.heinz.deviceai.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreGemmaRaceTest {
    private val ids = (1..30).map { "blind-$it" }
    private val tasks = ids.map { BenchmarkTask(it, "unseen task $it") }
    private val domains = listOf(RaceDomain.MEMORY, RaceDomain.REASONING, RaceDomain.CODE)

    private fun pairedRun(coreMs: Long = 12, gemmaMs: Long = 100): BenchmarkRun =
        BenchmarkRun(ids.flatMap { id ->
            listOf(
                TaskMeasurement(CoreGemmaPairedRunner.CORE, id, true, 1.0, null,
                    0, 0, 0, coreMs, 0, null),
                TaskMeasurement(CoreGemmaPairedRunner.GEMMA, id, true, 1.0, null,
                    0, 1, 0, gemmaMs, 0, null)
            )
        }, false, 0)

    private fun grades(): List<RaceReferenceGrade> = ids.mapIndexed { index, id ->
        RaceReferenceGrade(id, domains[index / 10], 0.98, 0.98, true, true, "heldout-$id")
    }

    @Test fun provisionalWinRequiresQualityAndBothLatencyPercentiles() {
        val result = CoreGemmaRaceEvaluator.evaluate(pairedRun(), grades())
        assertEquals(RaceAssessment.CORE_FASTER_AT_COMPARABLE_QUALITY, result.assessment)
        assertEquals(30, result.summary.completePairs)
        assertEquals(3, result.summary.domains)
        assertEquals(12L, result.summary.coreP95Ms)
        assertEquals(100L, result.summary.gemmaP95Ms)
        assertFalse("Synthetic inputs are never declared real device measurements",
            result.measurementEvidenceIsDeviceVerified)
    }

    @Test fun fastestCoreLosesIfBlindAnswerQualityDrops() {
        val poor = grades().map { it.copy(coreQuality = 0.20) }
        val result = CoreGemmaRaceEvaluator.evaluate(pairedRun(coreMs = 1), poor)
        assertEquals(RaceAssessment.CORE_SAFETY_OR_QUALITY_BELOW_PARITY, result.assessment)
    }

    @Test fun safetyVetoOverridesPerfectSpeedAndAnswers() {
        val unsafe = grades().toMutableList()
        unsafe[0] = unsafe[0].copy(coreSafetyPass = false)
        val result = CoreGemmaRaceEvaluator.evaluate(pairedRun(coreMs = 1), unsafe)
        assertEquals(RaceAssessment.CORE_SAFETY_OR_QUALITY_BELOW_PARITY, result.assessment)
    }

    @Test fun refusesUnpairedSkippedNegativeLatencyAndFailedMeasurements() {
        val run = pairedRun()
        val missing = run.copy(measurements = run.measurements.dropLast(1))
        assertEquals(RaceAssessment.INCOMPLETE,
            CoreGemmaRaceEvaluator.evaluate(missing, grades()).assessment)
        assertEquals(RaceAssessment.INCOMPLETE,
            CoreGemmaRaceEvaluator.evaluate(run.copy(skippedTasks = 1), grades()).assessment)
        // Invalid latency is rejected by TaskMeasurement before an evaluator can
        // receive it. Keep the constructor guard; never relax it to build a fixture.
        assertThrows(IllegalArgumentException::class.java) {
            run.measurements.first().copy(latencyMillis = -1)
        }
        val failed = run.copy(measurements = run.measurements.mapIndexed { index, m ->
            if (index == 0) m.copy(error = "timeout") else m
        })
        assertEquals(RaceAssessment.INCOMPLETE,
            CoreGemmaRaceEvaluator.evaluate(failed, grades()).assessment)
    }

    @Test fun successfulSubMillisecondCoreAnswersDoNotInvalidateTheRace() {
        // A millisecond-resolution monotonic clock can legitimately report 0 ms.
        val result = CoreGemmaRaceEvaluator.evaluate(pairedRun(coreMs = 0), grades())
        assertEquals(RaceAssessment.CORE_FASTER_AT_COMPARABLE_QUALITY, result.assessment)
        assertEquals(0L, result.summary.coreP50Ms)
        assertEquals(0L, result.summary.coreP95Ms)
        assertFalse(result.measurementEvidenceIsDeviceVerified)
    }

    @Test fun refusesSelfClaimedQualityWithoutIndependentGrading() {
        assertEquals(RaceAssessment.UNGRADED,
            CoreGemmaRaceEvaluator.evaluate(pairedRun(), emptyList()).assessment)
        val repeated = grades().toMutableList()
        repeated[1] = repeated[0]
        assertEquals(RaceAssessment.UNGRADED,
            CoreGemmaRaceEvaluator.evaluate(pairedRun(), repeated).assessment)
    }

    @Test fun requiresEnoughUnseenCasesAcrossDifferentTaskDomains() {
        val few = pairedRun().copy(measurements = pairedRun().measurements.take(20))
        assertEquals(RaceAssessment.INSUFFICIENT_HOLDOUT,
            CoreGemmaRaceEvaluator.evaluate(few, grades().take(10)).assessment)
        val skew = grades().map { it.copy(domain = RaceDomain.MEMORY) }
        assertEquals(RaceAssessment.INSUFFICIENT_HOLDOUT,
            CoreGemmaRaceEvaluator.evaluate(pairedRun(), skew).assessment)
    }

    @Test fun comparableAccuracyWithoutP95AdvantageDoesNotWin() {
        val run = pairedRun().copy(measurements = pairedRun().measurements.map { m ->
            if (m.system == CoreGemmaPairedRunner.CORE &&
                m.taskId in setOf("blind-29", "blind-30")) m.copy(latencyMillis = 200)
            else m
        })
        val result = CoreGemmaRaceEvaluator.evaluate(run, grades())
        assertEquals(RaceAssessment.CORE_NOT_FASTER_AT_COMPARABLE_QUALITY, result.assessment)
        assertEquals(200L, result.summary.coreP95Ms)
    }

    @Test fun pairedRunnerAlternatesRunOrderRatherThanHeatingOnlyOneModelFirst() {
        val order = mutableListOf<String>()
        val core = object : BenchmarkSystem {
            override val name = CoreGemmaPairedRunner.CORE
            override fun run(task: BenchmarkTask): TaskOutcome {
                order += name + ":" + task.id
                return TaskOutcome(true)
            }
        }
        val gemma = object : BenchmarkSystem {
            override val name = CoreGemmaPairedRunner.GEMMA
            override fun run(task: BenchmarkTask): TaskOutcome {
                order += name + ":" + task.id
                return TaskOutcome(true)
            }
        }
        var now = 0L
        val result = CoreGemmaPairedRunner.run(tasks.take(3), core, gemma,
            clock = BenchmarkClock { ++now })
        assertEquals(listOf(
            "continuum-core:blind-1", "gemma-4:blind-1",
            "gemma-4:blind-2", "continuum-core:blind-2",
            "continuum-core:blind-3", "gemma-4:blind-3"
        ), order)
        assertEquals(6, result.measurements.size)
        assertEquals(0, result.skippedTasks)
    }

    @Test fun cancelledRaceCannotPretendToWin() {
        val flag = CancellationFlag()
        val core = object : BenchmarkSystem {
            override val name = CoreGemmaPairedRunner.CORE
            override fun run(task: BenchmarkTask): TaskOutcome {
                flag.cancel()
                return TaskOutcome(true)
            }
        }
        val gemma = object : BenchmarkSystem {
            override val name = CoreGemmaPairedRunner.GEMMA
            override fun run(task: BenchmarkTask) = TaskOutcome(true)
        }
        var now = 0L
        val run = CoreGemmaPairedRunner.run(tasks.take(2), core, gemma, flag,
            BenchmarkClock { ++now })
        assertTrue(run.cancelled)
        assertEquals(RaceAssessment.INCOMPLETE,
            CoreGemmaRaceEvaluator.evaluate(run, grades().take(2)).assessment)
    }
}
