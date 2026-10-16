package at.heinz.deviceai.benchmark

/** Immutable benchmark task. */
data class BenchmarkTask(val id: String, val prompt: String) {
    init {
        require(id.isNotBlank()) { "task id must not be blank" }
    }
}

/**
 * What a system under test reports for one task. Null means "not measured".
 * Counters must be non-negative.
 */
data class TaskOutcome(
    val success: Boolean,
    val factualSupport: Double? = null,
    val contradictionDetected: Boolean? = null,
    val invalidToolProposals: Long = 0,
    val modelCalls: Long = 0,
    val contextBytes: Long = 0,
    val humanCorrections: Long = 0,
    val recoveredAfterFailure: Boolean? = null
) {
    init {
        require(factualSupport == null || (factualSupport in 0.0..1.0)) { "factualSupport must be in 0..1 or null" }
        require(invalidToolProposals >= 0) { "invalidToolProposals must be >= 0" }
        require(modelCalls >= 0) { "modelCalls must be >= 0" }
        require(contextBytes >= 0) { "contextBytes must be >= 0" }
        require(humanCorrections >= 0) { "humanCorrections must be >= 0" }
    }
}

/** Injectable system under test (single-pass baseline or Continuum pipeline adapter). */
interface BenchmarkSystem {
    val name: String
    fun run(task: BenchmarkTask): TaskOutcome
}

/** Injectable monotonic clock in milliseconds. */
fun interface BenchmarkClock {
    fun nowMillis(): Long
}

/** Cooperative cancellation. */
class CancellationFlag {
    @Volatile
    private var cancelled = false
    fun cancel() { cancelled = true }
    fun isCancelled(): Boolean = cancelled
}

/** One measured task. Immutable. */
data class TaskMeasurement(
    val system: String,
    val taskId: String,
    val success: Boolean,
    val factualSupport: Double?,
    val contradictionDetected: Boolean?,
    val invalidToolProposals: Long,
    val modelCalls: Long,
    val contextBytes: Long,
    val latencyMillis: Long,
    val humanCorrections: Long,
    val recoveredAfterFailure: Boolean?,
    val error: String? = null
) {
    init {
        require(latencyMillis >= 0) { "latencyMillis must be >= 0" }
    }
}

/** Raw runner output. */
data class BenchmarkRun(
    val measurements: List<TaskMeasurement>,
    val cancelled: Boolean,
    val skippedTasks: Int
)
