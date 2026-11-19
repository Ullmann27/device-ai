package at.heinz.deviceai.cognitive

import at.heinz.deviceai.BootstrapBrain
import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import at.heinz.deviceai.localmodel.RuntimeChainPlanSource
import at.heinz.deviceai.ui.Command03ActivityProjection
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * `regression-chains.jsonl`: owner task chains executed on the host through the productive orchestrator, ToolGate,
 * approval path and guard. Only the device backend is a fake that returns the outputs written in each case (in the
 * real tools' formats) or fails; nothing of the chain logic is stubbed.
 */
class OwnerTaskChainRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun strings(json: JSONObject, key: String): List<String> =
        json.optJSONArray(key)?.let { array -> (0 until array.length()).map(array::getString) } ?: emptyList()

    /** The device backend of one case: per tool a queue of outputs or failures; every call is recorded. */
    private class Backend(spec: JSONObject?) : ToolRunner {
        val calls = CopyOnWriteArrayList<ToolCallRequest>()
        var onCall: ((ToolCallRequest) -> Unit)? = null
        private val queues = HashMap<String, ArrayDeque<JSONObject>>()
        init {
            spec?.keys()?.forEach { tool ->
                val array = spec.getJSONArray(tool)
                queues[tool] = ArrayDeque((0 until array.length()).map(array::getJSONObject))
            }
        }
        override fun execute(request: ToolCallRequest): String {
            calls += request
            onCall?.invoke(request)
            val next = synchronized(queues) { queues[request.name]?.removeFirstOrNull() }
                ?: throw IllegalStateException("Kein Gerät hinter ${request.name}")
            // „fail“: the backend reports a clean failure (nothing ran); „crash“: it breaks without saying what happened.
            if (next.has("fail")) throw at.heinz.deviceai.ToolBackendFailure(at.heinz.deviceai.ToolExecutionStatus.FAILED, next.getString("fail"))
            if (next.has("crash")) throw IllegalStateException(next.getString("crash"))
            return next.getString("output")
        }
    }

    private class Stage : OwnerTaskChainObserver {
        val events = CopyOnWriteArrayList<String>()
        var afterSettled: ((Int) -> Unit)? = null
        override fun waitingForApproval(index: Int, step: ChainStep, total: Int) { events += "WAITING:$index" }
        override fun started(index: Int, step: ChainStep, total: Int) { events += "STARTED:$index" }
        override fun settled(index: Int, step: ChainStep, state: StepState) { events += "${state.name}:$index"; afterSettled?.invoke(index) }
        override fun ended(completed: Boolean) { events += "ENDED:$completed" }
    }

    private class Harness(val chain: OwnerTaskChain, val backend: Backend, approvals: List<Boolean>) {
        private val answers = ArrayDeque(approvals)
        val approvalRequests = AtomicInteger()
        val stage = Stage()
        private var runs = 0

        fun run(stopAfterStep: Int? = null, stopInTool: String? = null): OwnerTaskChain.Outcome {
            chain.observer = stage
            lateinit var orchestrator: BoundedAgentOrchestrator
            stage.afterSettled = { index -> if (stopAfterStep != null && index == stopAfterStep - 1) orchestrator.cancel() }
            backend.onCall = { request -> if (request.name == stopInTool) orchestrator.cancel() }
            orchestrator = BoundedAgentOrchestrator(
                model = chain, memories = { emptyList() },
                toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), backend),
                evaluator = chain, experienceStore = InMemoryExperienceStore(), evaluationConfirmsOutcome = false,
                approvalProvider = chain.approvals { _, _ ->
                    approvalRequests.incrementAndGet()
                    AgentToolApproval(answers.removeFirstOrNull() ?: false)
                },
                limits = OwnerTaskChain.limits(chain.plan), callIdPrefix = "chain-test-${runs++}",
                onToolStarted = { _, _ -> chain.toolStarted() })
            return chain.runWith(orchestrator).outcome
        }
    }

    private fun label(outcome: OwnerTaskChain.Outcome): String = when (outcome) {
        is OwnerTaskChain.Outcome.Completed -> "COMPLETED"
        is OwnerTaskChain.Outcome.Failed -> "FAILED"
        is OwnerTaskChain.Outcome.Paused -> "PAUSED:${outcome.pause.name}"
    }

    @Test fun everyChainCaseHoldsOnTheRealOrchestratorGateAndGuard() {
        val all = cases("regression-chains.jsonl")
        assertTrue("chain set unexpectedly small: ${all.size}", all.size >= 24)
        assertEquals("duplicate ids", all.size, all.map { it.getString("id") }.toSet().size)
        val failures = mutableListOf<String>()
        val report = StringBuilder()
        var sameToolsBefore = 0
        var chainCases = 0
        all.forEach { case ->
            val id = case.getString("id")
            val input = case.getString("input")
            val expect = case.getJSONObject("expect")
            fun check(ok: Boolean, message: String) { if (!ok) failures += "$id „$input“: $message" }
            assertEquals("$id: runner", "chain", case.getString("runner"))
            val wantedOutcomes = strings(expect, "outcomes")
            val wantedCalls = strings(expect, "calls")
            val backend = Backend(case.optJSONObject("backend"))
            val plan: ChainPlan? = if (case.has("model_plan")) {
                when (val parsed = OwnerChainPlanCodec.parse(input, case.getString("model_plan"))) {
                    is OwnerChainPlanValidator.Result.Valid -> parsed.plan
                    is OwnerChainPlanValidator.Result.Rejected -> {
                        check(wantedOutcomes == listOf("REJECTED"), "plan rejected (${parsed.reason}), expected $wantedOutcomes")
                        strings(expect, "contains").forEach { check(parsed.reason.contains(it), "missing „$it“ in: ${parsed.reason}") }
                        report.append("$id\tREJECTED\t${parsed.reason}\n")
                        null
                    }
                }
            } else OwnerTaskChainRecipes.recognise(input).also {
                if (it == null) { check(wantedOutcomes == listOf("NO_CHAIN"), "no recipe, expected $wantedOutcomes"); report.append("$id\tNO_CHAIN\n") }
            }
            if (plan == null) return@forEach
            chainCases++
            check(wantedOutcomes.none { it == "NO_CHAIN" || it == "REJECTED" }, "a plan was built (${plan.recipe ?: "model"}), expected $wantedOutcomes")
            if (expect.has("recipe")) check(plan.recipe == expect.getString("recipe"), "recipe ${plan.recipe}")
            // What the unchanged deterministic routing would have run for this utterance: at most one tool.
            val before = BootstrapBrain.decide(input, emptyList()).tool?.let(::listOf).orEmpty()
            if (before.isNotEmpty() && before == plan.steps.map { it.tool }) sameToolsBefore++
            val approvals = case.optJSONArray("approve")?.let { a -> (0 until a.length()).map(a::getBoolean) }.orEmpty()
            val harness = Harness(OwnerTaskChain(plan, input), backend, approvals)
            val seen = mutableListOf<String>()
            var outcome: OwnerTaskChain.Outcome? = null
            var dropped = false
            val actions = case.getJSONArray("actions")
            (0 until actions.length()).map(actions::getJSONObject).forEachIndexed { index, action ->
                if (action.has("say")) {
                    val paused = outcome as? OwnerTaskChain.Outcome.Paused
                    when (val command = OwnerTaskChainContinuation.parse(action.getString("say"),
                        waitsForChoice = paused?.pause == OwnerTaskChain.Pause.NEEDS_CHOICE)) {
                        is OwnerTaskChainContinuation.Command.Continue ->
                            if (harness.chain.resume(command.choice)) outcome = harness.run() else outcome = harness.chain.outcome()
                        OwnerTaskChainContinuation.Command.Drop -> dropped = true
                        null -> Unit
                    }
                } else outcome = harness.run(action.optInt("stop_after_step").takeIf { it > 0 }, action.optString("stop_in_tool").takeIf { it.isNotEmpty() })
                seen += if (dropped) "DROPPED" else label(outcome!!)
                if (index == 0 && expect.has("calls_after_first"))
                    check(backend.calls.map { it.name } == strings(expect, "calls_after_first"), "calls after first action ${backend.calls.map { it.name }}")
            }
            val final = outcome!!
            check(seen == wantedOutcomes, "outcomes $seen, expected $wantedOutcomes: ${final.report}")
            check(backend.calls.map { it.name } == wantedCalls, "calls ${backend.calls.map { it.name }}, expected $wantedCalls")
            expect.optJSONArray("args")?.let { args ->
                (0 until args.length()).forEach { i ->
                    val wanted = args.getJSONObject(i)
                    val actual = backend.calls.getOrNull(i)?.arguments.orEmpty()
                    wanted.keys().forEach { key -> check(actual[key]?.toString() == wanted.get(key).toString(), "call ${i + 1} argument $key=${actual[key]}") }
                    check(actual.keys == wanted.keys().asSequence().toSet(), "call ${i + 1} arguments ${actual.keys}")
                }
            }
            if (expect.has("failed_step")) check((final as? OwnerTaskChain.Outcome.Failed)?.step == expect.getInt("failed_step") - 1,
                "failed step ${(final as? OwnerTaskChain.Outcome.Failed)?.step?.plus(1)}")
            strings(expect, "contains").forEach { check(final.report.contains(it), "missing „$it“ in: ${final.report}") }
            strings(expect, "not_contains").forEach { check(!final.report.contains(it), "forbidden „$it“ in: ${final.report}") }
            // Nothing is claimed beyond the receipts: a completed chain ran every planned tool exactly once, in order.
            if (final is OwnerTaskChain.Outcome.Completed && !dropped) {
                check(backend.calls.map { it.name } == plan.steps.map { it.tool }, "completed, but calls ${backend.calls.map { it.name }}")
                check(final.report.startsWith("Erledigt"), "completed report: ${final.report}")
            } else check(!final.report.startsWith("Erledigt."), "unfinished chain reports done: ${final.report}")
            // The stage saw only steps that really waited or started, each at most as often as it ran.
            val started = harness.stage.events.count { it.startsWith("STARTED:") }
            check(started == backend.calls.size, "stage showed $started started steps for ${backend.calls.size} real calls")
            check(harness.stage.events.none { it.startsWith("DONE:") && it.substringAfter(':').toInt() >= harness.chain.completedSteps() },
                "stage shows a step as done that is not verified: ${harness.stage.events}")
            report.append("$id\t${seen.joinToString(">")}\t${backend.calls.joinToString(",") { it.name }}\t${final.report.replace("\n", " | ").take(260)}\n")
        }
        report.append("TOTAL ${all.size} chainCases=$chainCases sameToolSequenceBefore=$sameToolsBefore failures=${failures.size}\n")
        runCatching { File(System.getProperty("java.io.tmpdir"), "continuum-chain-report.txt").writeText(report.toString()) }
        println(report)
        if (failures.isNotEmpty()) fail("${failures.size} checks failed:\n" + failures.joinToString("\n"))
        // Pinned evidence: before this change the deterministic routing ran the same tool sequence for exactly one
        // chain case (the single-step folder listing – as a raw listing, without the requested answer).
        assertEquals("cases whose tool sequence the unchanged routing already ran", BEFORE_SAME_TOOLS, sameToolsBefore)
    }

    /** No utterance of the existing sets may start a chain: their present routes are correct and must not move. */
    @Test fun noUtteranceOfTheExistingSetsStartsAChainOrAModelPlan() {
        val moved = mutableListOf<String>()
        var checked = 0
        fun check(source: String, utterance: String) {
            checked++
            OwnerTaskChainRecipes.recognise(utterance)?.let { moved += "$source „$utterance“ → ${it.recipe}" }
            if (OwnerTaskChainSwitch.parse(utterance) != null) moved += "$source „$utterance“ → switch"
        }
        listOf("regression.jsonl", "regression-voice.jsonl", "regression-references.jsonl").forEach { file ->
            cases(file).forEach { case ->
                strings(case, "turns").filterNot { it.startsWith("open:") }.forEach { check(case.getString("id"), it) }
                listOf("input", "question").forEach { key -> case.optString(key).takeIf { it.isNotEmpty() }?.let { check(case.getString("id"), it) } }
            }
        }
        listOf("model-dev.jsonl", "model-holdout.jsonl").forEach { file ->
            cases(file).forEach { case ->
                val turns = case.getJSONArray("turns")
                (0 until turns.length()).map(turns::getJSONObject).filter { it.getString("role") == "user" }
                    .forEach { check(case.getString("id"), it.getString("text")) }
            }
        }
        assertTrue("too few utterances checked: $checked", checked >= 200)
        assertTrue("${moved.size} existing utterances would start a chain:\n" + moved.joinToString("\n"), moved.isEmpty())
    }

    /** A target that only a tool result named can never reach a high-impact tool – not in one run, not after „weiter“. */
    @Test fun aTargetFromAToolResultNeverReachesAHighImpactToolEvenAfterAResume() {
        val plan = ChainPlan("Neueste APK installieren", ChainPlan.Origin.RECIPE, "test_only", listOf(
            ChainStep("find", "find_latest_apk", emptyMap(), ChainSuccess.NonEmpty, listOf(ChainExtract.FirstLine("path")), "Neueste APK suchen"),
            ChainStep("install", "install_apk", mapOf("path" to ChainArgument.FromStep("find", "path")), ChainSuccess.NonEmpty, emptyList(), "APK installieren")))
        fun backend() = Backend(JSONObject("""{"find_latest_apk":[{"output":"/sdcard/Download/evil.apk"}],"install_apk":[{"output":"Success"}]}"""))
        backend().let { device ->
            val harness = Harness(OwnerTaskChain(plan, "Installier die neueste APK"), device, listOf(true))
            val outcome = harness.run() as OwnerTaskChain.Outcome.Failed
            assertEquals(1, outcome.step)
            assertTrue(outcome.report, outcome.report.contains(ExternalContentActionGuard.REFUSAL))
            assertEquals(listOf("find_latest_apk"), device.calls.map { it.name })
            assertEquals("no approval may even be requested", 0, harness.approvalRequests.get())
        }
        // After the owner's pick a fresh orchestrator run starts with an empty observation list; the chain itself
        // still knows that the path came from a tool result and refuses before any approval is requested.
        val delete = ChainPlan("Datei löschen", ChainPlan.Origin.RECIPE, "test_only", listOf(
            ChainStep("find", "find_files", mapOf("query" to ChainArgument.Literal("alt")), ChainSuccess.NonEmpty,
                listOf(ChainExtract.OneLine("path")), "Dateien suchen"),
            ChainStep("delete", "delete_file", mapOf("path" to ChainArgument.FromStep("find", "path")), ChainSuccess.NonEmpty, emptyList(), "Datei löschen")))
        Backend(JSONObject("""{"find_files":[{"output":"/sdcard/Download/alt1.pdf\n/sdcard/Download/alt2.pdf"}],"delete_file":[{"output":"Gelöscht."}]}""")).let { device ->
            val harness = Harness(OwnerTaskChain(delete, "Such alte Dateien und räum auf"), device, listOf(true, true))
            assertEquals("PAUSED:NEEDS_CHOICE", label(harness.run()))
            assertTrue(harness.chain.resume(1))
            val refused = harness.run() as OwnerTaskChain.Outcome.Failed
            assertTrue(refused.report, refused.report.contains(ExternalContentActionGuard.REFUSAL))
            assertEquals(listOf("find_files"), device.calls.map { it.name })
            assertEquals(0, harness.approvalRequests.get())
            assertFalse("a failed chain cannot be resumed", harness.chain.resume())
        }
        // The owner named the file himself: the guard lets the step through to the normal approval.
        backend().let { device ->
            val harness = Harness(OwnerTaskChain(plan, "Installiere evil.apk aus den Downloads"), device, listOf(true))
            assertEquals("COMPLETED", label(harness.run()))
            assertEquals(listOf("find_latest_apk", "install_apk"), device.calls.map { it.name })
            assertEquals(1, harness.approvalRequests.get())
        }
    }

    @Test fun aChangingStepStoppedInFlightIsNeverRepeatedAndAReadOnlyOneCanBe() {
        val shot = checkNotNull(OwnerTaskChainRecipes.recognise("Mach einen Screenshot"))
        val device = Backend(JSONObject("""{"take_screenshot":[{"output":"Gespeichert: /sdcard/Download/a.png"},{"output":"Gespeichert: /sdcard/Download/b.png"}]}"""))
        val harness = Harness(OwnerTaskChain(shot, "Mach einen Screenshot"), device, listOf(true, true))
        val outcome = harness.run(stopInTool = "take_screenshot")
        assertTrue(outcome.report, outcome is OwnerTaskChain.Outcome.Failed)
        assertFalse(outcome.report.startsWith("Erledigt"))
        assertFalse(harness.chain.resume())
        assertEquals(listOf("take_screenshot"), device.calls.map { it.name })
    }

    @Test fun theTimeLimitPausesBetweenStepsAndTheChainContinues() {
        var now = 0L
        val plan = checkNotNull(OwnerTaskChainRecipes.recognise("Mach einen Gerätecheck"))
        val device = Backend(JSONObject("""{"get_battery_info":[{"output":"Akkustand: 50 %"}],"get_storage_info":[{"output":"Gesamt: 10 Byte\nFür die App nutzbar: 5 Byte"}],"get_network_summary":[{"output":"WLAN"}]}"""))
        val chain = OwnerTaskChain(plan, "Mach einen Gerätecheck", clock = { now }, maxMillis = 1_000)
        val harness = Harness(chain, device, emptyList())
        chain.observer = object : OwnerTaskChainObserver { override fun settled(index: Int, step: ChainStep, state: StepState) { now += 600 } }
        val orchestrator = BoundedAgentOrchestrator(model = chain, memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), device), evaluator = chain, experienceStore = InMemoryExperienceStore(),
            limits = OwnerTaskChain.limits(plan), callIdPrefix = "chain-time", onToolStarted = { _, _ -> chain.toolStarted() })
        val paused = chain.runWith(orchestrator).outcome as OwnerTaskChain.Outcome.Paused
        assertEquals(OwnerTaskChain.Pause.INTERRUPTED, paused.pause)
        assertEquals(2, paused.step)
        assertTrue(paused.report, paused.report.contains("Zeitgrenze"))
        assertTrue(chain.resume())
        assertEquals("COMPLETED", label(harness.run()))
        assertEquals(listOf("get_battery_info", "get_storage_info", "get_network_summary"), device.calls.map { it.name })
    }

    @Test fun theStageProjectionShowsTheRealSequenceAndNothingBeforeItStarts() {
        val projection = Command03ActivityProjection()
        assertNull(projection.snapshot())
        val plan = checkNotNull(OwnerTaskChainRecipes.recognise("Finde die neueste APK und zeig mir die Infos"))
        val snapshots = mutableListOf<String>()
        val chain = OwnerTaskChain(plan, "Finde die neueste APK und zeig mir die Infos")
        chain.observer = OwnerTaskChainStage("chain-stage-1", { event: CockpitAnimationEvent ->
            projection.render(event)
            snapshots += projection.snapshot().toString()
        })
        val device = Backend(JSONObject("""{"find_latest_apk":[{"output":"/sdcard/Download/a.apk"}],"list_files":[{"output":"-rw-rw---- 1 u m 5M 2026-11-17 14:02 a.apk"}]}"""))
        val orchestrator = BoundedAgentOrchestrator(model = chain, memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), device), evaluator = chain, experienceStore = InMemoryExperienceStore(),
            limits = OwnerTaskChain.limits(plan), callIdPrefix = "chain-stage", onToolStarted = { _, _ -> chain.toolStarted() })
        assertTrue(chain.runWith(orchestrator).outcome is OwnerTaskChain.Outcome.Completed)
        val first = JSONObject(snapshots.first())
        assertEquals(1, first.getJSONArray("steps").length())
        assertEquals("ACTIVE", first.getJSONArray("steps").getJSONObject(0).getString("state"))
        assertTrue(first.getJSONArray("steps").getJSONObject(0).getString("label").startsWith("1/2 Neueste APK"))
        assertTrue(snapshots.any { JSONObject(it).getString("detail").contains("Schritt 2 von 2") })
        val last = checkNotNull(projection.snapshot())
        assertFalse(last.getBoolean("active"))
        val steps = last.getJSONArray("steps")
        assertEquals(2, steps.length())
        assertEquals(listOf("DONE", "DONE"), (0 until 2).map { steps.getJSONObject(it).getString("state") })
    }

    @Test fun listingParserAndValueParts() {
        val entries = OwnerChainValues.listing("total 8\ndrwxrwx--- 2 u g 3.4K 2026-11-18 09:12 Ordner\n" +
            "-rw-rw---- 1 u g 2.3M 2026-11-16 10:30 Rechnung November.pdf\nnicht eine zeile\n")
        assertEquals(listOf("Ordner", "Rechnung November.pdf"), entries.map { it.name })
        assertTrue(entries[0].directory)
        assertEquals("2.3M", entries[1].size)
        assertEquals("/sdcard/Download", OwnerChainValues.part("/sdcard/Download/a b.pdf", ChainArgument.Part.PARENT))
        assertEquals("a b.pdf", OwnerChainValues.part("/sdcard/Download/a b.pdf", ChainArgument.Part.LEAF))
        assertEquals("16.11.2026", OwnerChainValues.germanDate("2026-11-16"))
    }

    @Test fun continuationAndSwitchCommands() {
        assertEquals(OwnerTaskChainContinuation.Command.Continue(null), OwnerTaskChainContinuation.parse("Weiter", false))
        assertEquals(OwnerTaskChainContinuation.Command.Continue(null), OwnerTaskChainContinuation.parse("Jarvis, mach weiter bitte", false))
        assertEquals(OwnerTaskChainContinuation.Command.Continue(2), OwnerTaskChainContinuation.parse("die zweite", true))
        assertEquals(OwnerTaskChainContinuation.Command.Continue(3), OwnerTaskChainContinuation.parse("weiter mit 3", true))
        assertEquals(OwnerTaskChainContinuation.Command.Drop, OwnerTaskChainContinuation.parse("abbrechen", true))
        assertNull(OwnerTaskChainContinuation.parse("weiter", true))
        assertNull(OwnerTaskChainContinuation.parse("die zweite", false))
        assertNull(OwnerTaskChainContinuation.parse("weiter so, das gefällt mir", false))
        assertEquals(true, OwnerTaskChainSwitch.parse("Aufgabenketten mit Modellplan an"))
        assertEquals(false, OwnerTaskChainSwitch.parse("Schalte die Aufgabenkette mit Modellplan aus"))
        assertNull(OwnerTaskChainSwitch.parse("Was ist eine Aufgabenkette?"))
        assertTrue(OwnerTaskChainSwitch.wantsModelPlan("Such die neueste APK und sag mir dann wie groß sie ist"))
        assertFalse(OwnerTaskChainSwitch.wantsModelPlan("Was ist ein Akku und wie funktioniert er danach?"))
        assertFalse(OwnerTaskChainSwitch.wantsModelPlan("Erzähl mir einen Witz und danach noch einen"))
    }

    @Test fun planPromptListsOnlyChainableRegisteredToolsAndStaysSmall() {
        val prompt = OwnerChainPlanCodec.prompt("Such die neueste APK und sag mir dann wie groß sie ist")
        assertTrue(prompt.length < 4_000)
        assertTrue(prompt.contains("find_latest_apk(filter?)"))
        assertFalse(prompt.contains("delete_file") || prompt.contains("install_apk") || prompt.contains("web_search"))
        OwnerChainStepCatalog.all().forEach { assertNotNull(at.heinz.deviceai.ToolRegistry.get(it.tool)) }
    }

    /** The plan source runs through the one runtime interface: the same code serves the local model and Claude. */
    @Test fun theRuntimePlanSourceCollectsOneToolFreeGenerationAndGivesUpCleanly() {
        class Fake(val behavior: (LocalInferenceCallback) -> Unit, override val state: LocalModelState = LocalModelState.READY) : LocalModelRuntime {
            var cancelled = false
            var prompts = 0
            override val loadedModel = ImportedLocalModel("model", "v1", "a".repeat(64), 1,
                LocalModelCapabilities(true, false, false, false, false, 8192, null, setOf(LocalModelBackend.CPU)))
            override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) = error("unused")
            override fun unload(callback: (Result<Unit>) -> Unit) = callback(Result.success(Unit))
            override fun stream(prompt: String, callback: LocalInferenceCallback) { prompts++; behavior(callback) }
            override fun cancel() { cancelled = true }
        }
        val plan = """{"schema":"continuum-owner-chain-v1","goal":"Akku","steps":[{"tool":"get_battery_info","args":{}}]}"""
        val answering = Fake({ callback ->
            plan.chunked(17).forEach { callback.onEvent(LocalInferenceEvent.TextDelta(it)) }
            callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(1, plan.length, 4, null, null, null, 1)))
        })
        val text = RuntimeChainPlanSource(answering).propose(OwnerChainPlanCodec.prompt("Prüf den Akku und sag mir dann den Stand"))
        assertEquals(plan, text)
        assertEquals(1, answering.prompts)
        assertTrue(OwnerChainPlanCodec.parse("Prüf den Akku und sag mir dann den Stand", text!!) is OwnerChainPlanValidator.Result.Valid)
        assertNull(RuntimeChainPlanSource(Fake({ it.onEvent(LocalInferenceEvent.Failed("x")) })).propose("p"))
        assertNull(RuntimeChainPlanSource(Fake({}, LocalModelState.EMPTY)).propose("p"))
        val silent = Fake({})
        assertNull(RuntimeChainPlanSource(silent, timeoutMillis = 60).propose("p"))
        assertTrue(silent.cancelled)
    }

    private companion object {
        const val BEFORE_SAME_TOOLS = 1
    }
}
