package at.heinz.deviceai.benchmark

/** Aggregate per system. Null fields were not measured by any task. Counter sums saturate at Long.MAX_VALUE. */
data class SystemSummary(
    val system: String,
    val tasks: Int,
    val successes: Int,
    val errors: Int,
    val meanFactualSupport: Double?,
    val contradictionsDetected: Int?,
    val invalidToolProposals: Long,
    val modelCalls: Long,
    val contextBytes: Long,
    val totalLatencyMillis: Long,
    val humanCorrections: Long,
    val recoveries: Int?,
    val recoveryOpportunities: Int?
)

data class BenchmarkReport(
    val cancelled: Boolean,
    val skippedTasks: Int,
    val summaries: List<SystemSummary>,
    val measurements: List<TaskMeasurement>
) {
    fun toJson(): String = buildString {
        append("{\"cancelled\":").append(cancelled)
        append(",\"skippedTasks\":").append(skippedTasks)
        append(",\"summaries\":[")
        summaries.forEachIndexed { i, s ->
            if (i > 0) append(',')
            append("{\"system\":").append(str(s.system))
            append(",\"tasks\":").append(s.tasks)
            append(",\"successes\":").append(s.successes)
            append(",\"errors\":").append(s.errors)
            append(",\"meanFactualSupport\":").append(num(s.meanFactualSupport))
            append(",\"contradictionsDetected\":").append(num(s.contradictionsDetected))
            append(",\"invalidToolProposals\":").append(s.invalidToolProposals)
            append(",\"modelCalls\":").append(s.modelCalls)
            append(",\"contextBytes\":").append(s.contextBytes)
            append(",\"totalLatencyMillis\":").append(s.totalLatencyMillis)
            append(",\"humanCorrections\":").append(s.humanCorrections)
            append(",\"recoveries\":").append(num(s.recoveries))
            append(",\"recoveryOpportunities\":").append(num(s.recoveryOpportunities))
            append('}')
        }
        append("],\"measurements\":[")
        measurements.forEachIndexed { i, m ->
            if (i > 0) append(',')
            append("{\"system\":").append(str(m.system))
            append(",\"taskId\":").append(str(m.taskId))
            append(",\"success\":").append(m.success)
            append(",\"factualSupport\":").append(num(m.factualSupport))
            append(",\"contradictionDetected\":").append(num(m.contradictionDetected))
            append(",\"invalidToolProposals\":").append(m.invalidToolProposals)
            append(",\"modelCalls\":").append(m.modelCalls)
            append(",\"contextBytes\":").append(m.contextBytes)
            append(",\"latencyMillis\":").append(m.latencyMillis)
            append(",\"humanCorrections\":").append(m.humanCorrections)
            append(",\"recoveredAfterFailure\":").append(num(m.recoveredAfterFailure))
            append(",\"error\":").append(if (m.error == null) "null" else str(m.error))
            append('}')
        }
        append("]}")
    }

    /** One row per measurement; unmeasured fields are the literal `null`. */
    fun toCsv(): String = buildString {
        append(CSV_HEADER).append('\n')
        for (m in measurements) {
            append(
                listOf(
                    csv(m.system), csv(m.taskId), m.success, num(m.factualSupport),
                    num(m.contradictionDetected), m.invalidToolProposals, m.modelCalls,
                    m.contextBytes, m.latencyMillis, m.humanCorrections,
                    num(m.recoveredAfterFailure), if (m.error == null) "null" else csv(m.error)
                ).joinToString(",")
            ).append('\n')
        }
    }

    private fun num(v: Any?): String = v?.toString() ?: "null"

    private fun str(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }

    private fun csv(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    companion object {
        const val CSV_HEADER = "system,taskId,success,factualSupport,contradictionDetected,invalidToolProposals," +
            "modelCalls,contextBytes,latencyMillis,humanCorrections,recoveredAfterFailure,error"
    }
}

object BenchmarkReportAggregator {
    fun aggregate(run: BenchmarkRun): BenchmarkReport {
        val summaries = run.measurements.groupBy { it.system }.toSortedMap().map { (name, ms) ->
            val support = ms.mapNotNull { it.factualSupport }
            val contradictions = ms.mapNotNull { it.contradictionDetected }
            val recovery = ms.mapNotNull { it.recoveredAfterFailure }
            SystemSummary(
                system = name,
                tasks = ms.size,
                successes = ms.count { it.success },
                errors = ms.count { it.error != null },
                meanFactualSupport = if (support.isEmpty()) null else support.sum() / support.size,
                contradictionsDetected = if (contradictions.isEmpty()) null else contradictions.count { it },
                invalidToolProposals = ms.sumSat { it.invalidToolProposals },
                modelCalls = ms.sumSat { it.modelCalls },
                contextBytes = ms.sumSat { it.contextBytes },
                totalLatencyMillis = ms.sumSat { it.latencyMillis },
                humanCorrections = ms.sumSat { it.humanCorrections },
                recoveries = if (recovery.isEmpty()) null else recovery.count { it },
                recoveryOpportunities = if (recovery.isEmpty()) null else recovery.size
            )
        }
        return BenchmarkReport(run.cancelled, run.skippedTasks, summaries, run.measurements)
    }

    private inline fun List<TaskMeasurement>.sumSat(f: (TaskMeasurement) -> Long): Long {
        var total = 0L
        for (m in this) {
            val v = f(m)
            total = if (total > Long.MAX_VALUE - v) Long.MAX_VALUE else total + v
        }
        return total
    }
}
