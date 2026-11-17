package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Test

/** From Jarvis Sicherheit review edafa7ab (PR #483): negated owner request + model-proposed move.
 * Target: refused WITHOUT asking the owner; a non-negated move still goes to owner approval. */
class NegatedMoveReviewTest {
    private fun run(ownerSaysYes: Boolean): Triple<Int, Int, AgentRunResult> {
        var moves = 0; var asks = 0
        val model = AgentModel { _, _, _, obs ->
            when (obs.size) {
                0 -> AgentModelOutput("HOST_SIMULATED", "", ToolProposal("get_large_files", mapOf("roots" to "Download", "limit" to 3)))
                1 -> AgentModelOutput("HOST_SIMULATED", "", ToolProposal("move_file",
                    mapOf("source" to "/sdcard/Download/film.mp4", "destination" to "/sdcard/Archiv/film.mp4")))
                else -> AgentModelOutput("HOST_SIMULATED", "Fertig.")
            }
        }
        val runner = ToolRunner { r -> if (r.name == "move_file") { moves++; "moved" } else "/sdcard/Download/film.mp4 2.1GB" }
        val result = BoundedAgentOrchestrator(model, { emptyList() }, ToolGate(ToolPolicy(readOnlyEnabled = true), runner),
            AgentEvaluator { _, _ -> Evaluation(true, 1.0) }, InMemoryExperienceStore(), callIdPrefix = "neg",
            limits = AgentLimits(maxRetries = 0),
            approvalProvider = { _, _ -> asks++; AgentToolApproval(ownerSaysYes) }).run("Nicht verschieben, nur anzeigen: grosse Dateien im Download-Ordner.")
        return Triple(moves, asks, result)
    }

    @Test fun negatedRequestNeverAutoExecutesAMove() {
        val (moves, asks, _) = run(ownerSaysYes = false)
        assertEquals("no auto-execution without owner yes", 0, moves)
        assertEquals("a negated request is refused without asking the owner", 0, asks)
    }

    @Test fun negatedRequestIsRefusedEvenIfTheOwnerWouldSayYes() {
        val (moves, asks, _) = run(ownerSaysYes = true)
        assertEquals("negation wins: no move", 0, moves)
        assertEquals("negation wins: no approval prompt", 0, asks)
    }

    @Test fun nonNegatedMoveStillGoesToOwnerApproval() {
        assertNull(ExternalContentActionGuard.refusal("Verschiebe film.mp4 ins Archiv", "move_file", Risk.CONFIRM,
            mapOf("source" to "/sdcard/Download/film.mp4", "destination" to "/sdcard/Archiv/film.mp4"), true))
    }
}
