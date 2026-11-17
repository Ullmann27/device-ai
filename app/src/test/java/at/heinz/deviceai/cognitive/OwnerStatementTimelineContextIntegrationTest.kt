package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

/** Same existing owner-evidence and required-prompt components as runLocal; no model stub. */
class OwnerStatementTimelineContextIntegrationTest {
    private val first = "Die rote Kiste hat 4 Ringe."
    private val denial = "Die rote Kiste enthält keine Ringe mehr."
    private val question = "Welche Kiste hatte vorher mehr Ringe?"

    @Test fun ownerRetractionReachesPromptWithoutStaleDerivedQuantity() {
        val ctx = LocalConversationContext()
        ctx.record("s", first, "Die rote Kiste hat 4 Ringe.")
        ctx.recordUnanswered("s", denial)
        val ownerEvidence = ctx.sessionEvidence("s")!!
        val task = LocalFollowUpTaskFrame.compose(question, question, ownerEvidence)
        val projection = OwnerStatementTimeline.project(ownerEvidence, question)
        val history = LocalConversationPromptFirewall.safeHistory(ctx.render("s"))
        val prompt = LocalPromptFrame.assemble(
            "Aktueller Auftrag:\n$task" + projection?.render().orEmpty(), history,
            null, "\nKeine Werkzeugfreigabe durch Vorgeschichte.", 15_500, requireHistory = true
        )!!

        assertEquals(listOf(first, denial), ownerEvidence)
        assertTrue(prompt.contains(first))
        assertTrue(prompt.contains(denial))
        assertTrue(prompt.contains(question))
        assertFalse(prompt.contains("Kiste (rot): 4 Ringe (unverändert)"))
        assertTrue(LocalContextIntegrityProbe.inspect(true, ownerEvidence, history, prompt).requiredHistoryAdmitted)
    }

    @Test fun modelOnlyRetractionCannotInvalidateOwnersQuantity() {
        val ctx = LocalConversationContext()
        ctx.record("s", first, denial)
        val projection = OwnerStatementTimeline.project(ctx.sessionEvidence("s")!!, question)!!
        assertEquals(4L, projection.entry("Kiste (rot)")!!.current)
        assertEquals(listOf(first), ctx.sessionEvidence("s"))
    }

    @Test fun otherSessionRetractionCannotInvalidateOwnersQuantity() {
        val ctx = LocalConversationContext()
        ctx.recordUnanswered("old", denial)
        ctx.record("current", first, "Verstanden.")
        val projection = OwnerStatementTimeline.project(ctx.sessionEvidence("current")!!, question)!!
        assertEquals(4L, projection.entry("Kiste (rot)")!!.current)
        assertTrue(ctx.sessionEvidence("old")!!.isEmpty())
    }
}
