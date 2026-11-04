package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Test

/** Jarvis Sicherheit recheck of PR #483 @97784c1b (local, not pushed to integration). */
class RecheckNegationProbeTest {
    private fun run(task: String, tool: String, args: Map<String, Any?>, yes: Boolean): Pair<Int, Int> {
        var exec = 0; var asks = 0
        val model = AgentModel { _, _, _, obs ->
            when (obs.size) {
                0 -> AgentModelOutput("HOST_SIMULATED", "", ToolProposal(tool, args))
                else -> AgentModelOutput("HOST_SIMULATED", "Fertig.")
            }
        }
        val runner = ToolRunner { r -> if (r.name == tool) { exec++; "done" } else "ok" }
        BoundedAgentOrchestrator(model, { emptyList() }, ToolGate(ToolPolicy(readOnlyEnabled = true), runner),
            AgentEvaluator { _, _ -> Evaluation(true, 1.0) }, InMemoryExperienceStore(), callIdPrefix = "rc",
            limits = AgentLimits(maxRetries = 0),
            approvalProvider = { _, _ -> asks++; AgentToolApproval(yes) }).run(task)
        return exec to asks
    }
    private val del = mapOf<String, Any?>("path" to "/sdcard/familie.jpg")
    private val mv = mapOf<String, Any?>("source" to "/sdcard/a.txt", "destination" to "/sdcard/b/a.txt")

    @Test fun negatedDeleteNamedFile() { for (y in listOf(true, false)) assertEquals(0 to 0, run("lösche familie.jpg nicht", "delete_file", del, y)) }
    @Test fun negatedMove() { for (y in listOf(true, false)) assertEquals(0 to 0, run("verschieb das nicht", "move_file", mv, y)) }

    @Test fun probeTable() {
        val cases = listOf(
            "nicht löschen" to "delete_file", "keinesfalls löschen" to "delete_file",
            "lösch bitte auf keinen Fall" to "delete_file", "don't delete" to "delete_file",
            "do not delete" to "delete_file",
            "nicht die alten großen Urlaubsfotos löschen" to "delete_file",
            "lösche die Datei, nicht die andere" to "delete_file", "nicht vergessen: lösche x.tmp" to "delete_file",
            "lösche familie.jpg" to "delete_file")
        for ((t, tool) in cases) {
            val neg = ExternalContentActionGuard.ownerNegated(t, tool)
            val (e, a) = run(t, tool, del, true)
            println("PROBE|$t|negated=$neg|exec(yes)=$e|asks=$a")
        }
    }
}
