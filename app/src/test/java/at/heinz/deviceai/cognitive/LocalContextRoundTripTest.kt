package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class LocalContextRoundTripTest {
    @get:Rule val tmp = TemporaryFolder()
    private val session = UUID.randomUUID().toString()

    @Test fun recorderRestoreKeepsCompleteCorrectionAndDoesNotWriteTwice() {
        val directory = tmp.newFolder()
        val store = LocalChatTranscriptStore(directory)
        var writes = 0
        val before = LocalConversationContext().apply {
            onTurnRecorded = { id, turn -> writes++; store.appendContextTurn(id, turn, writes.toLong()) }
        }
        val original = "Emma ist in Berg."
        val correction = "Zusatz. ".repeat(600) + "Korrektur: Emma ist nicht mehr in Berg."
        before.record(session, original, "Unbestätigt.")
        before.recordUnanswered(session, correction)
        before.recordUnanswered(session, correction)
        assertEquals(2, writes)
        val restored = LocalConversationContext().apply { onTurnRecorded = { _, _ -> fail("Restore writes") } }
        LocalChatTranscriptStore(directory).loadContextTurns(session).forEach { restored.restore(session, it) }
        assertEquals(listOf(original, correction), restored.sessionEvidence(session))
        val history = restored.render(session, maxBytes = 7_000)
        assertTrue(history.contains(correction))
        assertTrue(history.indexOf(original) < history.indexOf(correction))
        assertFalse(restored.hasContinuation(session))
    }

    @Test fun partialEndSurvivesDiskAndReadOnlyMemoryRecall() {
        val directory = tmp.newFolder()
        val store = LocalChatTranscriptStore(directory)
        val context = LocalConversationContext().apply {
            onTurnRecorded = { id, turn -> store.appendContextTurn(id, turn, 1) }
        }
        val partial = "Anfang. " + "Erklärung. ".repeat(500) + "Letzter erhaltener Satz beginnt hier"
        context.record(session, "Erkläre den Ablauf.", partial, responseComplete = false)
        assertTrue(LocalContextRetentionPolicy.apply(context, session, "Was hast du dir gemerkt?",
            NonModelReplyKind.MEMORY_RECALL))
        val restored = LocalConversationContext()
        LocalChatTranscriptStore(directory).loadContextTurns(session).forEach { restored.restore(session, it) }
        assertTrue(restored.hasContinuation(session))
        val rendered = restored.render(session, maxBytes = 1_500, continuation = true)
        assertTrue(rendered.contains("Letzter erhaltener Satz beginnt hier"))
        assertTrue(rendered.contains("unvollständig"))
        assertFalse(rendered.contains("Anfang."))
        assertTrue(rendered.contains("keine Gedächtnisänderung"))
    }

    @Test fun oversizedLaterOwnerTextCannotResurrectAnEarlierClaimAfterRestore() {
        val directory = tmp.newFolder()
        val store = LocalChatTranscriptStore(directory)
        val context = LocalConversationContext().apply {
            onTurnRecorded = { id, turn -> store.appendContextTurn(id, turn, 1) }
        }
        context.record(session, "Emma ist in Berg.", "Unbestätigt.")
        context.recordUnanswered(session, "x".repeat(LocalDialogueContextLimits.MAX_OWNER_CHARACTERS) + " nicht Berg.")
        val restored = LocalConversationContext()
        store.loadContextTurns(session).forEach { restored.restore(session, it) }
        assertNull(restored.sessionEvidence(session))
        assertFalse(restored.render(session).contains("Emma ist in Berg"))
        assertFalse(restored.hasContinuation(session))
    }

    @Test fun realMemoryChangeAndExplicitResetStillInvalidatePersistedContext() {
        val directory = tmp.newFolder()
        val store = LocalChatTranscriptStore(directory)
        val context = LocalConversationContext().apply {
            onTurnRecorded = { id, turn -> store.appendContextTurn(id, turn, 1) }
            onTurnsInvalidated = { id -> store.appendContextReset(id, 2) }
        }
        context.record(session, "Emma ist in Berg.", "Unbestätigt.")
        LocalContextRetentionPolicy.apply(context, session, "Widerrufe: Emma ist in Berg.",
            NonModelReplyKind.MEMORY_CHANGE)
        assertEquals(emptyList<String>(), context.sessionEvidence(session))
        assertTrue(LocalChatTranscriptStore(directory).loadContextTurns(session).isEmpty())
    }

    @Test fun requiredHistoryBudgetSurvivesRestartAndRetainsTheActualAnswerEnd() {
        val store = LocalChatTranscriptStore(tmp.newFolder())
        val context = LocalConversationContext().apply {
            onTurnRecorded = { id, turn -> store.appendContextTurn(id, turn, 1L) }
        }
        context.record(session, "Erkläre die Kiste ausführlich.", "Abschnitt. ".repeat(900) + "Ende: Nun folgt Teil zwei.")
        context.recordToolResult(session, "Prüfe mein Handy.", "diagnose_device", "Messdaten. ".repeat(300))
        val required = requireNotNull(context.requiredPromptBytes(session, continuation = true))
        val restored = LocalConversationContext()
        store.loadContextTurns(session).forEach { restored.restore(session, it) }
        assertEquals(required, restored.requiredPromptBytes(session, continuation = true))
        val rendered = restored.render(session, maxBytes = required, continuation = true)
        assertTrue(rendered.contains("Ende: Nun folgt Teil zwei."))
        assertTrue(rendered.contains("Erkläre die Kiste ausführlich."))
        assertTrue(rendered.contains("Prüfe mein Handy."))
        assertTrue(rendered.toByteArray(Charsets.UTF_8).size <= required)
        assertNull(restored.requiredPromptBytes("different-session", continuation = true))
    }
}
