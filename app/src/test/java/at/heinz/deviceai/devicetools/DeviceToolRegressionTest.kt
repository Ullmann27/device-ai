package at.heinz.deviceai.devicetools

import at.heinz.deviceai.ToolBackendFailure
import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentToolApproval
import at.heinz.deviceai.cognitive.BoundedAgentOrchestrator
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.cognitive.OwnerChainPlanCodec
import at.heinz.deviceai.cognitive.OwnerChainPlanValidator
import at.heinz.deviceai.cognitive.OwnerTaskChain
import at.heinz.deviceai.cognitive.OwnerTaskChainContinuation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList

/**
 * `regression-devicetools.jsonl` (development set) and `regression-devicetools-check.jsonl` (held-out set, written
 * and committed before the implementation, never used for tuning) against the productive device tool layer: search
 * ranking, switch commands, the decision before a tool starts, pre-approval, model plans through the real validator
 * and chain runs through the real orchestrator and ToolGate. Only the device backend of a chain run is a stand-in
 * that returns the outputs written in the case, in the real tools' formats.
 */
class DeviceToolRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun strings(json: JSONObject, key: String): List<String> =
        json.optJSONArray(key)?.let { array -> (0 until array.length()).map(array::getString) } ?: emptyList()

    private val fixture = JSONObject(File(root, "regression-devicetools.fixture.json").readText(Charsets.UTF_8))
    private val apps = fixture.getJSONArray("apps").let { a -> (0 until a.length()).map(a::getJSONObject) }
        .map { DeviceApp(it.getString("label"), it.getString("package")) }
    private val files: List<DeviceFile> = fixture.getJSONArray("files").let { a -> (0 until a.length()).map(a::getJSONObject) }.let { rows ->
        val entries = rows.map { row ->
            val path = row.getString("path")
            DeviceFile(path.substringAfterLast('/'), path, false, row.getLong("size"),
                LocalDateTime.parse(row.getString("modified")).toInstant(ZoneOffset.UTC).toEpochMilli())
        }
        // Every folder on the way to a file exists as an entry of its own, as a storage walk reports it.
        val folders = entries.flatMap { file ->
            val parts = file.relative.split('/').dropLast(1)
            parts.indices.map { parts.take(it + 1).joinToString("/") }
        }.distinct().map { DeviceFile(it.substringAfterLast('/'), it, true, 0L, 0L) }
        folders + entries
    }

    private class Backend(spec: JSONObject?) : ToolRunner {
        val calls = CopyOnWriteArrayList<ToolCallRequest>()
        private val queues = HashMap<String, ArrayDeque<JSONObject>>()
        init {
            spec?.keys()?.forEach { tool ->
                val array = spec.getJSONArray(tool)
                queues[tool] = ArrayDeque((0 until array.length()).map(array::getJSONObject))
            }
        }
        override fun execute(request: ToolCallRequest): String {
            calls += request
            val next = synchronized(queues) { queues[request.name]?.removeFirstOrNull() } ?: throw IllegalStateException("Kein Gerät hinter ${request.name}")
            if (next.has("fail")) throw ToolBackendFailure(ToolExecutionStatus.FAILED, next.getString("fail"))
            return next.getString("output")
        }
    }

    private fun label(outcome: OwnerTaskChain.Outcome): String = when (outcome) {
        is OwnerTaskChain.Outcome.Completed -> "COMPLETED"
        is OwnerTaskChain.Outcome.Failed -> "FAILED"
        is OwnerTaskChain.Outcome.Paused -> "PAUSED:${outcome.pause.name}"
    }

    /** Null = the case holds; otherwise what differs. */
    private fun run(case: JSONObject): String? {
        val expect = case.getJSONObject("expect")
        return when (val runner = case.getString("runner")) {
            "device_search" -> {
                val result = DeviceSearch.search(case.getString("query"), apps, files, DeviceSearch.scope(case.optString("scope").ifEmpty { null }))
                val names = result.hits.map { it.name }
                val problems = ArrayList<String>()
                if (expect.has("first") && names.firstOrNull() != expect.getString("first")) problems += "first=${names.firstOrNull()}"
                if (expect.has("total") && result.total != expect.getInt("total")) problems += "total=${result.total}"
                if (expect.optBoolean("near") != result.near) problems += "near=${result.near}"
                strings(expect, "contains").filterNot { it in names }.forEach { problems += "missing $it" }
                strings(expect, "absent").filter { it in names }.forEach { problems += "unexpected $it" }
                problems.takeIf { it.isNotEmpty() }?.let { "$it in $names" }
            }
            "consent_command" -> (DeviceToolCommand.parse(case.getString("input"))?.name ?: "NONE").let { got ->
                if (got == expect.getString("command")) null else "command=$got"
            }
            "gate" -> {
                val state = case.getJSONObject("state")
                val arguments = case.getJSONObject("args").let { a -> a.keys().asSequence().associateWith { a.get(it) as Any? } }
                val decided = DeviceToolPolicy.decide(case.getString("tool"), arguments, DeviceToolPolicy.State(
                    cloudBrain = state.optString("brain", "local") == "cloud", modelTurn = state.optBoolean("model_turn", true),
                    namesConsent = state.optBoolean("names"), contentConsent = state.optBoolean("content"),
                    bridgeReady = state.optBoolean("bridge"), filesAccess = state.optBoolean("files", true),
                    ownerTurn = state.optString("owner"), documentsCreated = state.optInt("created"),
                    readable = { state.optBoolean("own_file") }))
                val got = when (decided) {
                    is DeviceToolPolicy.Decision.Run -> "RUN:${decided.route.name}"
                    is DeviceToolPolicy.Decision.Refused -> "REFUSED:${decided.refusal.code}"
                }
                if (got == expect.getString("decision")) null else "decision=$got"
            }
            "preapproval" -> {
                val access = DeviceToolAccess(MemoryDeviceToolSettings(documentsWithoutAsking = case.getBoolean("switch")), { false }, { false }, { null })
                val got = if (access.preApproved("create_document", at.heinz.deviceai.Risk.CONFIRM, case.getString("owner"))) "PREAPPROVED" else "ASK"
                if (got == expect.getString("decision")) null else "decision=$got"
            }
            "chain_plan" -> when (val parsed = OwnerChainPlanCodec.parse(case.getString("owner_task"), case.getString("model_plan"))) {
                is OwnerChainPlanValidator.Result.Valid -> if (expect.getString("result") == "VALID") null else "plan accepted"
                is OwnerChainPlanValidator.Result.Rejected -> when {
                    expect.getString("result") != "REJECTED" -> "plan rejected: ${parsed.reason}"
                    else -> strings(expect, "contains").firstOrNull { it !in parsed.reason }?.let { "missing „$it“ in: ${parsed.reason}" }
                }
            }
            "chain_run" -> chainRun(case, expect)
            "document_blocks" -> DeviceDocumentMarkup.parse(case.getString("content")).map { block ->
                when (block) {
                    is DeviceDocumentMarkup.Block.Heading -> "H:"
                    is DeviceDocumentMarkup.Block.Paragraph -> "P:"
                    is DeviceDocumentMarkup.Block.Item -> "L:"
                } + block.text
            }.let { got -> if (got == strings(expect, "blocks")) null else "blocks=$got" }
            "page_range" -> (DeviceFileText.pageRange(case.getString("pages"), case.getInt("page_count"))?.toString() ?: "INVALID").let { got ->
                if (got == expect.getString("range")) null else "range=$got"
            }
            else -> "unknown runner $runner"
        }
    }

    private fun chainRun(case: JSONObject, expect: JSONObject): String? {
        val input = case.getString("owner_task")
        val plan = (OwnerChainPlanCodec.parse(input, case.getString("model_plan")) as? OwnerChainPlanValidator.Result.Valid)?.plan
            ?: return "the plan of this chain run is not valid"
        val backend = Backend(case.optJSONObject("backend"))
        val answers = ArrayDeque(case.optJSONArray("approve")?.let { a -> (0 until a.length()).map(a::getBoolean) }.orEmpty())
        val chain = OwnerTaskChain(plan, input)
        var runs = 0
        fun once(): OwnerTaskChain.Outcome {
            val orchestrator = BoundedAgentOrchestrator(
                model = chain, memories = { emptyList() }, toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), backend),
                evaluator = chain, experienceStore = InMemoryExperienceStore(), evaluationConfirmsOutcome = false,
                approvalProvider = chain.approvals { _, _ -> AgentToolApproval(answers.removeFirstOrNull() ?: false) },
                limits = OwnerTaskChain.limits(chain.plan), callIdPrefix = "devicetools-${case.getString("id")}-${runs++}",
                onToolStarted = { _, _ -> chain.toolStarted() })
            return chain.runWith(orchestrator).outcome
        }
        val seen = ArrayList<String>()
        var outcome: OwnerTaskChain.Outcome? = null
        val actions = case.getJSONArray("actions")
        (0 until actions.length()).map(actions::getJSONObject).forEach { action ->
            if (action.has("say")) {
                val paused = outcome as? OwnerTaskChain.Outcome.Paused
                val command = OwnerTaskChainContinuation.parse(action.getString("say"), paused?.pause == OwnerTaskChain.Pause.NEEDS_CHOICE)
                if (command is OwnerTaskChainContinuation.Command.Continue)
                    outcome = if (chain.resume(command.choice)) once() else chain.outcome()
            } else outcome = once()
            seen += label(outcome!!)
        }
        val final = outcome!!
        val problems = ArrayList<String>()
        if (seen != strings(expect, "outcomes")) problems += "outcomes=$seen"
        if (backend.calls.map { it.name } != strings(expect, "calls")) problems += "calls=${backend.calls.map { it.name }}"
        expect.optJSONArray("args")?.let { args ->
            (0 until args.length()).forEach { i ->
                val wanted = args.getJSONObject(i)
                val actual = backend.calls.getOrNull(i)?.arguments.orEmpty()
                wanted.keys().forEach { key -> if (actual[key]?.toString() != wanted.get(key).toString()) problems += "call ${i + 1} $key=${actual[key]}" }
                if (actual.keys != wanted.keys().asSequence().toSet()) problems += "call ${i + 1} arguments ${actual.keys}"
            }
        }
        strings(expect, "contains").filterNot { final.report.contains(it) }.forEach { problems += "missing „$it“" }
        strings(expect, "not_contains").filter { final.report.contains(it) }.forEach { problems += "forbidden „$it“" }
        if (final is OwnerTaskChain.Outcome.Completed != final.report.startsWith("Erledigt")) problems += "done wording does not match the outcome"
        return problems.takeIf { it.isNotEmpty() }?.let { "$it · report: ${final.report.replace("\n", " | ").take(300)}" }
    }

    private fun score(name: String): Pair<Int, List<String>> {
        val all = cases(name)
        assertEquals("duplicate ids in $name", all.size, all.map { it.getString("id") }.toSet().size)
        val failures = all.mapNotNull { case ->
            val problem = runCatching { run(case) }.getOrElse { "threw ${it.javaClass.simpleName}: ${it.message}" }
            problem?.let { "${case.getString("id")} [${case.getString("runner")}] ${case.optString("note")}: $it" }
        }
        println("DEVICE-TOOL-EVAL $name: ${all.size - failures.size}/${all.size}")
        failures.forEach(::println)
        return all.size to failures
    }

    @Test fun everyCaseOfTheDevelopmentSetHolds() {
        val (total, failures) = score("regression-devicetools.jsonl")
        assertTrue("development set unexpectedly small: $total", total >= 120)
        if (failures.isNotEmpty()) fail("${failures.size} of $total development cases failed:\n" + failures.joinToString("\n"))
    }

    @Test fun everyCaseOfTheHeldOutSetHolds() {
        val (total, failures) = score("regression-devicetools-check.jsonl")
        assertTrue("held-out set unexpectedly small: $total", total >= 90)
        if (failures.isNotEmpty()) fail("${failures.size} of $total held-out cases failed:\n" + failures.joinToString("\n"))
    }

    @Test fun theTwoSetsShareNoCase() {
        fun keys(name: String) = cases(name).map { case ->
            listOf("runner", "query", "scope", "input", "owner_task", "tool", "args", "state", "owner", "switch", "model_plan", "content", "pages", "page_count")
                .joinToString("|") { case.opt(it)?.toString().orEmpty() }
        }.toSet()
        assertEquals(emptySet<String>(), keys("regression-devicetools.jsonl") intersect keys("regression-devicetools-check.jsonl"))
    }
}
