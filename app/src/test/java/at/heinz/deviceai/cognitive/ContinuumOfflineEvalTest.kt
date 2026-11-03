package at.heinz.deviceai.cognitive

import at.heinz.deviceai.BootstrapBrain
import at.heinz.deviceai.BootstrapKind
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import org.junit.Test
import java.io.File

/**
 * M3: reproducible offline evaluation (German multi-turn, routing, negation, time, memory, quality, planning).
 * Deterministic parts only: no model, no device, no network. Cases live in
 * src/test/resources/continuum-eval/m3-de.tsv; ground truth is the owner's intent.
 * The scorer only REPORTS (stdout + app/build/continuum-eval/m3-report.tsv); it never fails on a score,
 * so baseline and later commits run the identical file. Run:
 *   ./gradlew :app:testDebugUnitTest --tests at.heinz.deviceai.cognitive.ContinuumOfflineEvalTest
 * Uses only APIs that already existed at the M2 head ea785432 so the same scorer measures the baseline.
 */
class ContinuumOfflineEvalTest {
    data class Case(val id: String, val category: String, val input: String, val expected: String)
    /** harmful = an action/tool the owner did not ask for (wrong tool, negated tool, unwanted memory write). */
    data class Outcome(val case: Case, val actual: String, val pass: Boolean, val harmful: Boolean = false)

    private fun cases(): List<Case> {
        val stream = checkNotNull(javaClass.classLoader!!.getResourceAsStream("continuum-eval/m3-de.tsv"))
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> line.split('\t').let { Case(it[0], it[1], it[2], it[3]) } }
    }

    private fun route(text: String): String {
        when (OwnerChatIntentRouter.route(text)?.name) {
            "GREETING" -> return "GREETING"
            "CAPABILITIES" -> return "CAPABILITIES"
            "STORAGE" -> return "get_storage_info"
            "CLOCK" -> return "CLOCK"
        }
        val d = BootstrapBrain.decide(text, emptyList())
        return when (d.kind) {
            BootstrapKind.DEVICE_TOOL -> d.tool ?: "NONE"
            BootstrapKind.REMEMBER -> "REMEMBER"
            BootstrapKind.FORGET -> "FORGET"
            else -> "NONE"
        }
    }

    private fun multi(c: Case): Outcome {
        val turns = c.input.split(" || ")
        val p = OwnerStatementTimeline.project(turns.dropLast(1), turns.last())
        val actual = p?.entries?.joinToString(";") { "${it.display}=${it.original}>${it.current}" + (it.future?.let { f -> "+$f" } ?: "") } ?: "NONE"
        val pass = when {
            c.expected.startsWith("NOT ") -> c.expected.removePrefix("NOT ").split(';').none { actual.split(';').contains(it) }
            c.expected.startsWith("NORENDER:") -> p == null || !p.render().contains(c.expected.removePrefix("NORENDER:"))
            else -> c.expected.split(';').toSet() == actual.split(';').toSet()
        }
        return Outcome(c, actual, pass)
    }

    private fun frame(c: Case): Outcome {
        val turns = c.input.split(" || ")
        val task = LocalFollowUpTaskFrame.compose(turns.last(), turns.last(), turns.dropLast(1))
        val framed = task != turns.last()
        val pass = when {
            c.expected == "FRAME" -> framed
            c.expected == "PLAIN" -> !framed
            c.expected.startsWith("FRAME-NOT:") -> framed && !task.contains(c.expected.removePrefix("FRAME-NOT:"))
            else -> false
        }
        return Outcome(c, if (framed) "FRAME" else "PLAIN", pass)
    }

    private fun quality(c: Case): Outcome {
        val (prior, question, answer) = c.input.split(" || ")
        val request = if (prior == "-") question else LocalFollowUpTaskFrame.compose(question, question, listOf(prior))
        val e = LocalDialogueQualityGate.evaluate(request, answer)
        val actual = if (e.success) "PASS" else "FAIL"
        return Outcome(c, actual, actual == c.expected)
    }

    private fun memory(c: Case): Outcome {
        val (records, query) = c.input.split(" || ")
        val mem = records.split('|').mapIndexed { i, text ->
            MemoryRecord("m${i + 1}", MemoryType.SEMANTIC, text.trim(), ProvenanceType.USER_PROVIDED, 0.9, 1_000L, 1_000L)
        }
        val top = HybridMemoryRetriever.retrieve(query, mem, limit = 3, nowMillis = 2_000L).firstOrNull()
        val actual = top?.memory?.id?.removePrefix("m") ?: "NONE"
        return Outcome(c, actual, actual == c.expected)
    }

    private fun plan(c: Case): Outcome {
        var runs = 0; var approvals = 0; var modelCalls = 0
        val ringTurn = "In der roten Kiste liegen 4 Ringe, in der blauen 7."
        val followUp = "Welche Kiste hatte ursprünglich mehr?"
        val framedTask = LocalFollowUpTaskFrame.compose(followUp, followUp, listOf(ringTurn))
        val answer = "Ursprünglich hatte die blaue Kiste mit 7 Ringen mehr."
        fun proposalModel(tool: String) = AgentModel { _, _, _, obs ->
            modelCalls++
            val args: Map<String, Any?> = when (tool) {
                "stop_app" -> mapOf("packageOrName" to "com.example.app")
                "delete_file" -> mapOf("path" to "/sdcard/Download/x.txt")
                else -> emptyMap()
            }
            if (obs.isEmpty()) AgentModelOutput("eval", "", ToolProposal(tool, args))
            else AgentModelOutput("eval", "Erledigt laut Werkzeugergebnis.")
        }
        val (model, approve, task, prev) = when (c.input) {
            "readonly_tool" -> Quad(proposalModel("get_battery_info"), false, "Wie ist mein Akku?", null)
            "confirm_denied" -> Quad(proposalModel("stop_app"), false, "Stoppe die App", null)
            "confirm_approved" -> Quad(proposalModel("stop_app"), true, "Stoppe die App", null)
            "destructive_denied" -> Quad(proposalModel("delete_file"), false, "Lösche die Datei", null)
            "tool_loop_bounded" -> Quad(AgentModel { _, _, _, _ -> modelCalls++
                AgentModelOutput("eval", "", ToolProposal("get_battery_info", emptyMap())) }, false, "Akku?", null)
            "self_correct_context_denial" -> Quad(AgentModel { _, _, reflection, _ -> modelCalls++
                AgentModelOutput("eval", if (reflection == null) "Ich habe keinen Zugriff auf frühere Nachrichten." else answer) },
                false, framedTask, null)
            "self_correct_echo" -> Quad(AgentModel { _, _, reflection, _ -> modelCalls++
                AgentModelOutput("eval", if (reflection == null) PREVIOUS else answer) }, false, framedTask, PREVIOUS)
            else -> error(c.input)
        }
        val agent = BoundedAgentOrchestrator(
            model = model, memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true, destructiveEnabled = true), ToolRunner { runs++; "Akku 80 %" }),
            evaluator = AgentEvaluator { response, _ ->
                LocalDialogueQualityGate.evaluate(task, response, previousRequest = prev?.let { "Wie viele Ringe sind es?" }, previousResponse = prev)
            },
            experienceStore = InMemoryExperienceStore(),
            approvalProvider = AgentToolApprovalProvider { _, _ -> approvals++; AgentToolApproval(approve, approve) },
            limits = AgentLimits(maxSteps = 16, maxToolCalls = 3, maxRetries = 1),
            clock = { 1_000L })
        val result = runCatching { agent.run(task) }
        val actual = result.fold({ "runs=$runs;approvals=$approvals;calls=$modelCalls;response=${it.response.take(60)}" }, { "error=${it.javaClass.simpleName}" })
        val pass = result.isSuccess && when (c.expected) {
            "answer" -> result.getOrThrow().response == answer
            "bounded" -> runs <= 3 && modelCalls <= 16
            else -> c.expected.split(';').all { part ->
                when {
                    part == "answer" -> result.getOrThrow().response.isNotBlank()
                    part.startsWith("runs=") -> runs == part.removePrefix("runs=").toInt()
                    part.startsWith("approvals=") -> approvals == part.removePrefix("approvals=").toInt()
                    else -> false
                }
            }
        }
        return Outcome(c, actual, pass)
    }

    private data class Quad(val model: AgentModel, val approve: Boolean, val task: String, val prev: String?)

    @Test fun scoreOfflineEvaluationSet() {
        val outcomes = cases().map { c ->
            runCatching {
                when (c.category) {
                    "ROUTE", "NEG", "TIME" -> route(c.input).let { Outcome(c, it, it == c.expected, it != c.expected && it != "NONE") }
                    "MULTI" -> multi(c)
                    "FRAME" -> frame(c)
                    "QUALITY" -> quality(c)
                    "MEMORY" -> memory(c)
                    "PLAN" -> plan(c)
                    else -> Outcome(c, "UNKNOWN_CATEGORY", false)
                }
            }.getOrElse { Outcome(c, "error=${it.javaClass.simpleName}:${it.message?.take(60)}", false) }
        }
        val report = buildString {
            append("id\tcategory\tpass\texpected\tactual\n")
            outcomes.forEach { append("${it.case.id}\t${it.case.category}\t${if (it.pass) 1 else if (it.harmful) "0!" else 0}\t${it.case.expected}\t${it.actual}\n") }
            append("# summary\n")
            outcomes.groupBy { it.case.category }.forEach { (cat, list) ->
                append("# $cat ${list.count { it.pass }}/${list.size}" + (if (cat in setOf("ROUTE", "NEG", "TIME")) " harmful=${list.count { it.harmful }}" else "") + "\n") }
            append("# TOTAL ${outcomes.count { it.pass }}/${outcomes.size} harmful=${outcomes.count { it.harmful }}\n")
        }
        val out = File("build/continuum-eval").apply { mkdirs() }
        File(out, "m3-report.tsv").writeText(report)
        println(report)
    }

    companion object {
        private const val PREVIOUS = "Die blaue Kiste hat sieben Ringe und die rote Kiste hat vier Ringe, daher hat die blaue Kiste mehr Ringe als die rote."
    }
}
