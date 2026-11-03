package at.heinz.deviceai.benchmark

import kotlin.math.ceil

/**
 * A paired, offline, model-neutral comparison. This does NOT run Gemma, evaluate text, or
 * claim device verification. Trusted evaluation must be supplied separately.
 */
enum class RaceDomain {
    MEMORY, ROUTINE, DEVICE, MULTI_TURN, REASONING, CODE, TOOL_SAFETY, RESEARCH
}

enum class RaceAssessment {
    INCOMPLETE,
    UNGRADED,
    INSUFFICIENT_HOLDOUT,
    CORE_SAFETY_OR_QUALITY_BELOW_PARITY,
    CORE_FASTER_AT_COMPARABLE_QUALITY,
    CORE_NOT_FASTER_AT_COMPARABLE_QUALITY
}

/**
 * Independent reference or blind grading; never populated from a model's self-reported
 * factualSupport, success, or a memory learned from the benchmark questions.
 *
 * The creator of the report must still verify that the grader and test suite are truly
 * independent. A Kotlin Boolean or a hash cannot establish external trust.
 */
data class RaceReferenceGrade(
    val taskId: String,
    val domain: RaceDomain,
    val coreQuality: Double,
    val gemmaQuality: Double,
    val coreSafetyPass: Boolean,
    val gemmaSafetyPass: Boolean,
    val graderReceipt: String
) {
    init {
        require(taskId.isNotBlank())
        require(coreQuality.isFinite() && coreQuality in 0.0..1.0)
        require(gemmaQuality.isFinite() && gemmaQuality in 0.0..1.0)
        require(graderReceipt.isNotBlank() && graderReceipt.length <= 256)
    }
}

data class RacePairStatistics(
    val completePairs: Int,
    val domains: Int,
    val coreP50Ms: Long?,
    val gemmaP50Ms: Long?,
    val coreP95Ms: Long?,
    val gemmaP95Ms: Long?,
    val coreMeanQuality: Double?,
    val gemmaMeanQuality: Double?,
    val parityFraction: Double?
)

/** Source-only verdict: must never be described as a Fold7 hardware result. */
data class RaceComparison(
    val assessment: RaceAssessment,
    val summary: RacePairStatistics,
    val reason: String,
    val measurementEvidenceIsDeviceVerified: Boolean = false
)

/**
 * Alternates execution order AB, BA, AB, BA to reduce one-sided warmup bias.
 * Both runs still need the SAME real device, fixed conditions and a frozen unseen
 * task suite. Do not run a live model from the UI thread.
 */
object CoreGemmaPairedRunner {
    const val CORE = "continuum-core"
    const val GEMMA = "gemma-4"
    const val MAX_TASKS = 200

    fun run(
        tasks: List<BenchmarkTask>,
        core: BenchmarkSystem,
        gemma: BenchmarkSystem,
        cancellation: CancellationFlag = CancellationFlag(),
        clock: BenchmarkClock = BenchmarkClock { System.nanoTime() / 1_000_000L }
    ): BenchmarkRun {
        require(tasks.size in 1..MAX_TASKS)
        require(tasks.map { it.id }.distinct().size == tasks.size)
        require(core.name == CORE && gemma.name == GEMMA)
        val measured = ArrayList<TaskMeasurement>(tasks.size * 2)
        var finished = 0
        for ((i, task) in tasks.withIndex()) {
            if (cancellation.isCancelled()) break
            val systems = if (i % 2 == 0) listOf(core, gemma) else listOf(gemma, core)
            val current = BenchmarkRunner(clock, maxTasks = 1).run(listOf(task), systems, cancellation)
            measured += current.measurements
            if (current.measurements.size != 2 || current.cancelled || current.skippedTasks != 0) break
            finished++
        }
        return BenchmarkRun(measured, cancellation.isCancelled(), tasks.size - finished)
    }
}

/**
 * Conservative scoring gate, not a device proof. 30+ independent frozen holdout
 * tasks from >=3 domains, with >=3 examples per domain, are mandatory to declare
 * even a provisional lead. A failed, missing or 0-ms measurement is never discarded.
 */
object CoreGemmaRaceEvaluator {
    private const val MIN_PAIRS = 30
    private const val MIN_DOMAINS = 3
    private const val MIN_PER_DOMAIN = 3
    private const val MAX_MEAN_QUALITY_GAP = 0.02
    private const val MAX_TASK_QUALITY_GAP = 0.05
    private const val MIN_PARITY_RATE = 0.95
    private const val SPEED_FACTOR = 0.80

    fun evaluate(run: BenchmarkRun, grades: List<RaceReferenceGrade>): RaceComparison {
        val empty = RacePairStatistics(0, 0, null, null, null, null, null, null, null)
        fun no(assessment: RaceAssessment, why: String, stats: RacePairStatistics = empty) =
            RaceComparison(assessment, stats, why)
        if (run.cancelled || run.skippedTasks != 0)
            return no(RaceAssessment.INCOMPLETE, "Cancelled or incomplete paired run.")
        if (run.measurements.isEmpty() || run.measurements.size % 2 != 0)
            return no(RaceAssessment.INCOMPLETE, "Missing paired measurements.")

        val rows = run.measurements.groupBy { it.taskId }
        val expectedSystems = setOf(CoreGemmaPairedRunner.CORE, CoreGemmaPairedRunner.GEMMA)
        if (rows.any { (_, ms) ->
            ms.size != 2 || ms.map { it.system }.toSet() != expectedSystems ||
                ms.any { it.error != null || !it.success || it.latencyMillis <= 0 ||
                    it.invalidToolProposals != 0L }
        }) return no(RaceAssessment.INCOMPLETE, "Missing, failed, invalid or zero-latency measurement.")

        val byTask = grades.groupBy { it.taskId }
        if (grades.size != rows.size || byTask.keys != rows.keys || byTask.values.any { it.size != 1 })
            return no(RaceAssessment.UNGRADED, "Missing, duplicated or mismatched independent grade.")
        if (grades.any { it.graderReceipt.isBlank() })
            return no(RaceAssessment.UNGRADED, "Independent grader receipt is missing.")

        val counts = grades.groupingBy { it.domain }.eachCount()
        if (rows.size < MIN_PAIRS || counts.size < MIN_DOMAINS || counts.values.any { it < MIN_PER_DOMAIN })
            return no(RaceAssessment.INSUFFICIENT_HOLDOUT, "Need >=30 complete pairs and >=3 domains of >=3 tasks each.")

        val coreLatency = rows.values.map { ms ->
            ms.single { it.system == CoreGemmaPairedRunner.CORE }.latencyMillis
        }.sorted()
        val gemmaLatency = rows.values.map { ms ->
            ms.single { it.system == CoreGemmaPairedRunner.GEMMA }.latencyMillis
        }.sorted()
        val meanCore = grades.map { it.coreQuality }.average()
        val meanGemma = grades.map { it.gemmaQuality }.average()
        val parityRate = grades.count { it.coreQuality + MAX_TASK_QUALITY_GAP >= it.gemmaQuality }
            .toDouble() / grades.size
        val stats = RacePairStatistics(
            rows.size, counts.size,
            percentile(coreLatency, 0.50), percentile(gemmaLatency, 0.50),
            percentile(coreLatency, 0.95), percentile(gemmaLatency, 0.95),
            meanCore, meanGemma, parityRate
        )
        if (grades.any { !it.coreSafetyPass || !it.gemmaSafetyPass } ||
            meanCore + MAX_MEAN_QUALITY_GAP < meanGemma || parityRate < MIN_PARITY_RATE)
            return no(RaceAssessment.CORE_SAFETY_OR_QUALITY_BELOW_PARITY,
                "Safety or independently graded answer quality did not meet parity.", stats)
        val faster = stats.coreP50Ms!!.toDouble() <= stats.gemmaP50Ms!! * SPEED_FACTOR &&
            stats.coreP95Ms!!.toDouble() <= stats.gemmaP95Ms!! * SPEED_FACTOR
        return if (faster)
            no(RaceAssessment.CORE_FASTER_AT_COMPARABLE_QUALITY,
                "Provisional lead on submitted measurements, NOT an independently verified Fold7 result.", stats)
        else
            no(RaceAssessment.CORE_NOT_FASTER_AT_COMPARABLE_QUALITY,
                "Quality comparable, but the core is not >=20% faster at p50 AND p95.", stats)
    }

    private fun percentile(sorted: List<Long>, p: Double): Long =
        sorted[(ceil(sorted.size * p).toInt() - 1).coerceIn(0, sorted.lastIndex)]
}
