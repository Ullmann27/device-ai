package at.heinz.deviceai.cognitive

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.management.ManagementFactory
import org.json.JSONArray
import org.json.JSONObject

private fun get(value: Any?, name: String): Any? {
    if (value == null) return null
    val suffix = name.replaceFirstChar { it.uppercase() }
    return value.javaClass.methods.firstOrNull {
        it.parameterCount == 0 && (it.name == "get$suffix" || it.name == "is$suffix")
    }?.invoke(value)
}
private fun call(value: Any, name: String, vararg args: Any?): Any? {
    val method = value.javaClass.methods.firstOrNull {
        (it.name == name || it.name.startsWith("$name$")) && it.parameterCount == args.size
    } ?: return null
    return method.invoke(value, *args)
}
private fun make(name: String, vararg args: Any?): Any =
    Class.forName("at.heinz.deviceai.cognitive.$name").constructors.first {
        it.parameterCount == args.size
    }.newInstance(*args)
private fun strings(array: JSONArray): List<String> = (0 until array.length()).map(array::getString)
private fun objects(array: JSONArray): List<JSONObject> = (0 until array.length()).map(array::getJSONObject)
private fun JSONObject.optionalString(key: String): String? = if (isNull(key)) null else getString(key)

private data class Sample(val wallNs: Long, val cpuNs: Long?, val allocatedBytes: Long?)
private val threadBean = ManagementFactory.getThreadMXBean()
private val allocationBean = threadBean as? com.sun.management.ThreadMXBean
private fun cpuNow(): Long? = if (threadBean.isCurrentThreadCpuTimeSupported) threadBean.currentThreadCpuTime.takeIf { it >= 0 } else null
private fun allocatedNow(): Long? = allocationBean?.takeIf { it.isThreadAllocatedMemorySupported }?.getThreadAllocatedBytes(Thread.currentThread().threadId())?.takeIf { it >= 0 }
private fun memory(): JSONObject {
    val status = runCatching { File("/proc/self/status").readLines() }.getOrDefault(emptyList())
    fun kb(key: String) = status.firstOrNull { it.startsWith(key) }?.substringAfter(":")?.trim()?.substringBefore(" ")?.toLongOrNull()
    val runtime = Runtime.getRuntime()
    return JSONObject().put("heap_used_bytes",runtime.totalMemory()-runtime.freeMemory())
        .put("heap_committed_bytes",runtime.totalMemory()).put("rss_kb",kb("VmRSS") ?: JSONObject.NULL)
        .put("process_high_water_rss_kb",kb("VmHWM") ?: JSONObject.NULL)
}
private class Run(private val candidate: Boolean, private val samples: MutableList<Sample>? = null) {
    private val context: LocalConversationContext = if (candidate) {
        LocalConversationContext::class.java.getDeclaredConstructor(Boolean::class.javaPrimitiveType)
            .newInstance(true)
    } else LocalConversationContext()
    private var session = "s1"
    private var seed: JSONObject? = null
    private var revision = 1L
    private var decision: Any? = null
    private var directive: ContextDirective? = null
    private val pendingPlans = mutableMapOf<String, Any>()
    private var stalePlanAdmitted: Boolean? = null
    private val unsupported = mutableListOf<String>()

    private fun reference(value: JSONObject): Any = make("WorkingTaskReference",
        value.getString("id"), value.getString("label"), value.getString("source"),
        emptySet<String>(), value.optBoolean("revoked", false))

    private fun bind(value: JSONObject) {
        seed = JSONObject(value.toString())
        if (candidate) {
            val constraints = strings(value.getJSONArray("constraints")).mapIndexed { i, v ->
                make("WorkingTaskConstraint", "constraint-$i", v, "owner-source-$i")
            }
            val references = objects(value.getJSONArray("candidates")).map(::reference)
            val task = make("WorkingTaskSeed", value.optString("task_id", "animation"), revision,
                value.getString("goal"), "owner-goal", constraints, references,
                value.optionalString("selected"))
            call(context, "bindWorkingTask", session, task)
        } else {
            val current = objects(value.getJSONArray("candidates"))
                .firstOrNull { it.getString("id") == value.optionalString("selected") }
            val serialized = buildString {
                append("Arbeitsziel: ").append(value.getString("goal")).append(". ")
                append("Bestätigte Vorgaben: ").append(strings(value.getJSONArray("constraints")).joinToString("; ")).append(". ")
                append("Referenzen: ").append(objects(value.getJSONArray("candidates")).joinToString("; ") {
                    "${it.getString("label")} (${it.getString("id")}, ${it.getString("source")})"
                }).append(". ")
                append("Aktuell ausgewählt: ").append(current?.getString("label") ?: "nicht festgelegt").append(".")
            }
            context.recordNonModel(session, serialized, "Bestätigter Testkontext")
        }
    }

    fun execute(case: JSONObject): JSONObject {
        for (event in objects(case.getJSONArray("events"))) {
            when (event.getString("kind")) {
                "session" -> {
                    session = event.getString("id")
                    context.inspectRequest(session, "Sitzungswechsel")
                    decision = null
                    directive = null
                }
                "confirmed_work" -> bind(event)
                "owner" -> {
                    val text = event.getString("text")
                    val cpuStart = if (samples != null) cpuNow() else null
                    val allocatedStart = if (samples != null) allocatedNow() else null
                    val wallStart = System.nanoTime()
                    directive = context.inspectRequest(session, text)
                    decision = if (candidate) call(context, "resolveWorkingTask", session, text) else null
                    val elapsed = System.nanoTime() - wallStart
                    if (samples != null) samples += Sample(elapsed,
                        cpuStart?.let { start -> cpuNow()?.minus(start) },
                        allocatedStart?.let { start -> allocatedNow()?.minus(start) })
                    context.recordUnanswered(session, text)
                }
                "candidate" -> {
                    val updated = JSONObject(seed!!.toString())
                    updated.getJSONArray("candidates").put(event)
                    revision++
                    bind(updated)
                }
                "untrusted_memory", "model_text" -> {
                    unsupported += event.getString("kind") + ": no candidate output/owner-memory ingestion in context slice"
                }
                "confirmed_constraint" -> {
                    val updated = JSONObject(seed!!.toString())
                    val constraints = updated.getJSONArray("constraints")
                    val index = strings(constraints).indexOf(event.getString("conflicts_with"))
                    constraints.put(index, event.getString("value"))
                    revision = event.getLong("revision")
                    bind(updated)
                }
                "dispatch" -> {
                    val plan = get(decision, "plan")
                    if (plan != null) pendingPlans[event.getString("call_id")] = plan
                    unsupported += "dispatch: no ToolGate dispatch from this context-only harness"
                }
                "result" -> {
                    val old = pendingPlans[event.getString("call_id")]
                    if (candidate && old != null) {
                        stalePlanAdmitted = call(context, "isWorkingTaskPlanCurrent", session, old) as? Boolean
                    }
                    unsupported += "result: no external outcome evaluator or memory admission in context slice"
                }
                else -> unsupported += event.getString("kind") + ": actual authorization module not invoked"
            }
        }
        val expected = case.getJSONObject("expected")
        val snapshot = if (candidate) call(context, "workingTaskSnapshot", session) else null
        val currentSeed = get(snapshot, "seed")
        val plan = get(decision, "plan")
        val decisionKind = decision?.javaClass?.simpleName ?: directive?.kind?.name ?: "NoStructuredDecision"
        val clarifies = decisionKind == "Clarify" || directive?.kind == ContextDirectiveKind.CLARIFY_SCOPE
        val reference = get(plan, "reference")
        val selected = if (clarifies) null else (get(reference, "id") ?: get(snapshot, "selectedReferenceId")) as? String
        val rawEvidence = context.sessionEvidence(session).orEmpty().joinToString("\n")
        val expectedGoal = expected.optionalString("goal")
        val goal = if (candidate) get(currentSeed, "goal") as? String else expectedGoal?.takeIf { rawEvidence.contains(it) }
        val constraints = if (candidate) (get(currentSeed, "constraints") as? List<*>)
            .orEmpty().mapNotNull { get(it, "value") as? String } else strings(expected.getJSONArray("constraints"))
            .filter { rawEvidence.contains(it) }
        val checks = JSONObject()
            .put("target", if (candidate) selected == expected.optionalString("target") else JSONObject.NULL)
            .put("clarification", if (candidate || directive != null) clarifies == expected.getBoolean("clarify") else JSONObject.NULL)
            .put("goal_available", goal == expectedGoal)
            .put("constraints_available", constraints.toSet() == strings(expected.getJSONArray("constraints")).toSet())
        if (expected.has("excluded")) checks.put("excluded_target", selected !in strings(expected.getJSONArray("excluded")))
        if (expected.has("revoked_constraints")) checks.put("revoked_constraint_absent", constraints.none { it in strings(expected.getJSONArray("revoked_constraints")) })
        val checkNames = checks.keys().asSequence().toList()
        val failed = checkNames.any { !checks.isNull(it) && !checks.getBoolean(it) }
        val missing = checkNames.any { checks.isNull(it) }
        return JSONObject().put("id",case.getString("id")).put("category",case.getString("category"))
            .put("context_status", if(failed) "FAIL" else if(missing) "PARTIAL" else "PASS")
            .put("decision",decisionKind).put("target", selected ?: JSONObject.NULL)
            .put("goal",goal ?: JSONObject.NULL).put("constraints", constraints)
            .put("checks",checks).put("unsupported_events", unsupported.distinct())
            .put("stale_plan_admitted", stalePlanAdmitted ?: JSONObject.NULL)
            .put("tool_authority", "NOT_RUN").put("receipt_memory_avatar_e2e", "NOT_RUN")
    }
}

fun main(args: Array<String>) {
    require(args.size in 3..4)
    val candidate = args[0] == "candidate"
    val input = JSONObject(File(args[1]).readText())
    val cases = objects(input.getJSONArray("cases"))
    if (args.getOrNull(3) == "performance") {
        repeat(10) { cases.forEach { Run(candidate).execute(it) } }
        val before = memory()
        val samples = mutableListOf<Sample>()
        repeat(40) { cases.forEach { Run(candidate, samples).execute(it) } }
        val after = memory()
        fun percentile(values: List<Long>, ratio: Double): Long? = values.sorted().let {
            if (it.isEmpty()) null else it[(kotlin.math.ceil(it.size * ratio).toInt() - 1).coerceIn(0,it.lastIndex)]
        }
        fun stats(values: List<Long>) = JSONObject().put("count",values.size).put("sum",values.sum())
            .put("p50",percentile(values,.50) ?: JSONObject.NULL).put("p95",percentile(values,.95) ?: JSONObject.NULL)
            .put("max",values.maxOrNull() ?: JSONObject.NULL)
        val report = JSONObject().put("schema","omega-independent-admission-performance-v1")
            .put("arm",args[0]).put("warmup_case_runs",10*cases.size).put("measured_case_runs",40*cases.size)
            .put("owner_admissions",samples.size).put("wall_ns",stats(samples.map { it.wallNs }))
            .put("thread_cpu_ns",stats(samples.mapNotNull { it.cpuNs })).put("thread_allocated_bytes",stats(samples.mapNotNull { it.allocatedBytes }))
            .put("process_memory_before",before).put("process_memory_after",after)
            .put("scope","inspectRequest plus resolveWorkingTask if present; excludes seed setup, record and model; candidate reflection overhead retained")
            .put("model_calls",0).put("Fold7","NOT_RUN")
        File(args[2]).writeText(report.toString(2)+"\n")
        println("${args[0]} admission samples=${samples.size} wallP95ns=${percentile(samples.map { it.wallNs },.95)}; model/device NOT_RUN")
        return
    }
    val rows = cases.map { case ->
        try { Run(candidate).execute(case) } catch (error: Throwable) {
            val cause = if(error is InvocationTargetException) error.targetException else error
            JSONObject().put("id", case.getString("id")).put("context_status", "ERROR")
                .put("error", cause.toString()).put("tool_authority", "NOT_RUN")
        }
    }
    val report = JSONObject().put("schema", "omega-independent-context-result-v1")
        .put("arm",args[0]).put("cases",rows.size)
        .put("context_pass", rows.count { it.getString("context_status") == "PASS" })
        .put("context_fail", rows.count { it.getString("context_status") == "FAIL" })
        .put("context_partial", rows.count { it.getString("context_status") == "PARTIAL" })
        .put("errors", rows.count { it.getString("context_status") == "ERROR" })
        .put("model_calls",0).put("Fold7","NOT_RUN").put("rows",rows)
    File(args[2]).writeText(report.toString(2)+"\n")
    println("${args[0]}: ${report.getInt("context_pass")}/${rows.size} context PASS; errors=${report.getInt("errors")}; model/device/E2E NOT_RUN")
}
