package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LocalFollowUpFrameAdmissionTest {
    private val current = "Welche Kiste hatte ursprünglich mehr Ringe?"
    private val owner = "Die blaue Kiste hat 7 Ringe. " + "Zusatz. ".repeat(200) +
        "Die rote Kiste hat nicht mehr als 4 Ringe."

    @Test fun wholeActualTaskIsRequiredBeforeOwnerHistoryCanBeDeduplicated() {
        val frame = LocalFollowUpTaskFrame.composeFrame(current, current, listOf(owner))
        assertTrue(frame.task.contains(owner))
        assertTrue(frame.task.endsWith(current))
        assertEquals(setOf(owner), frame.ownerTurnsIn("Prefix\n" + frame.task + "\nSuffix"))
        assertTrue(frame.ownerTurnsIn(frame.task.replace("nicht mehr", "mehr")).isEmpty())
        assertTrue(frame.ownerTurnsIn(frame.task.dropLast(1)).isEmpty())
        assertTrue(frame.ownerTurnsIn(owner).isEmpty())
    }

    @Test fun combinedPromptCarriesOwnerOnceCurrentRequestAndRequiredTail() {
        val context = LocalConversationContext().apply { record("a", owner, "Unbestätigt.") }
        val frame = LocalFollowUpTaskFrame.composeFrame(current, current, context.sessionEvidence("a"))
        val body = "Aktueller Auftrag:\n" + frame.task
        val tail = "\nVertrauenswürdiger Zeitanker: 2026-11-06T19:00:00Z"
        val max = 2_700
        val budget = LocalPromptFrame.historyBudget(body, tail, max)
        val history = context.render("a", maxBytes = budget, ownerTurnsInTask = frame.ownerTurnsIn(body))
        val prompt = LocalDecisionPromptFrame.assemble(body, history, "", null, tail, max, true)
        assertNotNull(prompt)
        assertEquals(1, Regex(Regex.escape(owner)).findAll(prompt!!).count())
        assertTrue(prompt.contains(current))
        assertTrue(prompt.endsWith(tail))
        assertTrue(LocalContextIntegrityProbe.inspect(true, context.sessionEvidence("a"), history, prompt)
            .requiredHistoryAdmitted)
        val changedBody = body.replace("nicht mehr", "mehr")
        val fullHistory = context.render("a", maxBytes = 5_000, ownerTurnsInTask = frame.ownerTurnsIn(changedBody))
        assertTrue(fullHistory.contains(owner))
    }

    @Test fun failedFrameDoesNotClaimAnyDeduplicatedOwnerEvidence() {
        val oversized = "x".repeat(LocalChatPromptBudget.MAX_TASK_CHARACTERS - 50)
        val frame = LocalFollowUpTaskFrame.composeFrame(current, current, listOf(oversized))
        assertEquals(current, frame.task)
        assertTrue(frame.ownerTurnsIn(current).isEmpty())
        val continuation = LocalFollowUpTaskFrame.composeFrame("Weiter", "Weiter", listOf(owner))
        assertEquals("Weiter", continuation.task)
        assertTrue(continuation.ownerTurnsIn(owner + "Weiter").isEmpty())
    }

    @Test fun unicodeRestBudgetAndRequiredHistoryNeverSilentlyDropEvidence() {
        val body = "東京".repeat(200)
        val tail = "\nZeitanker"
        val max = 1_500
        val budget = LocalPromptFrame.historyBudget(body, tail, max)
        assertEquals(max - (body + tail).toByteArray(Charsets.UTF_8).size, budget)
        val context = LocalConversationContext().apply { record("a", owner, "Unbestätigt.") }
        val history = context.render("a", maxBytes = budget)
        assertEquals("", history)
        assertNull(LocalPromptFrame.assemble(body, history, null, tail, max, requireHistory = true))
    }
}
