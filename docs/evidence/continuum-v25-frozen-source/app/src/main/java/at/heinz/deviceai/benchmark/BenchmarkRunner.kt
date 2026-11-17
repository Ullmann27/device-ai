package at.heinz.deviceai.benchmark

/**
 * Runs every system over a bounded task list. Failures of a system (exceptions,
 * invalid outcomes, negative latency) are recorded as unsuccessful measurements
 * with latency 0 and an error message rather than aborting the run.
 */
class BenchmarkRunner(
    private val clock: BenchmarkClock = BenchmarkClock { System.nanoTime() / 1_000_000L },
    private val maxTasks: Int = DEFAULT_MAX_TASKS
) {
    init {
        require(maxTasks > 0) { "maxTasks must be > 0" }
    }

    fun run(
        tasks: List<BenchmarkTask>,
        systems: List<BenchmarkSystem>,
        cancellation: CancellationFlag = CancellationFlag()
    ): BenchmarkRun {
        require(tasks.size <= maxTasks) { "task count ${tasks.size} exceeds bound $maxTasks" }
        require(systems.map { it.name }.toSet().size == systems.size) { "system names must be unique" }
        val out = ArrayList<TaskMeasurement>(tasks.size * systems.size)
        var done = 0
        var cancelled = false
        require(systems.size <= MAX_SYSTEMS) { "too many benchmark systems" }
        for (task in tasks) {
            if (cancellation.isCancelled()) { cancelled = true; break }
            var taskComplete = true
            for ((index, system) in systems.withIndex()) {
                if (cancellation.isCancelled()) {
                    cancelled = true
                    taskComplete = false
                    break
                }
                out += measure(system, task)
                if (cancellation.isCancelled()) {
                    cancelled = true
                    // A fully executed last system still completes this task.
                    taskComplete = index == systems.lastIndex
                    break
                }
            }
            if (taskComplete) done++
            if (cancelled) break
        }
        return BenchmarkRun(out.toList(), cancelled, tasks.size - done)
    }

    private fun measure(system: BenchmarkSystem, task: BenchmarkTask): TaskMeasurement {
        val start = clock.nowMillis()
        val outcome = try {
            system.run(task)
        } catch (e: Exception) {
            return failed(system, task, "${e.javaClass.simpleName}: ${e.message}")
        }
        val latency = clock.nowMillis() - start
        if (latency < 0) return failed(system, task, "negative latency $latency")
        return TaskMeasurement(
            system.name, task.id, outcome.success, outcome.factualSupport,
            outcome.contradictionDetected, outcome.invalidToolProposals, outcome.modelCalls,
            outcome.contextBytes, latency, outcome.humanCorrections, outcome.recoveredAfterFailure
        )
    }

    private fun failed(system: BenchmarkSystem, task: BenchmarkTask, error: String) = TaskMeasurement(
        system.name, task.id, false, null, null, 0, 0, 0, 0, 0, null, error
    )

    companion object {
        const val DEFAULT_MAX_TASKS = 10_000
        const val MAX_SYSTEMS = 16
    }
}
