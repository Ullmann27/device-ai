package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M2: Kurzzeitkontext überlebt Werkzeug-/Uhr-/Startkern-Antworten.
 * Simuliert exakt die AgentActivity-Regel: keepsSessionContext ? recordNonModel : clear().
 */
class LocalContextRetentionTest {
    private val s = "session-a"
    private val t1 = "In der roten Kiste liegen 4 Ringe, in der blauen 7. Welche Kiste hat mehr?"
    private val t3 = "Welche Kiste hatte ursprünglich mehr Ringe?"

    private fun afterNonModel(ctx: LocalConversationContext, kind: NonModelReplyKind, request: String) {
        if (LocalContextRetentionPolicy.keepsSessionContext(kind))
            ctx.recordNonModel(s, request, LocalContextRetentionPolicy.turnLabel(kind))
        else ctx.clear()
    }

    @Test fun ringTaskSurvivesAnInterleavedStorageToolAnswer() {
        val ctx = LocalConversationContext()
        ctx.record(s, t1, "Die blaue Kiste hat mehr: 7 Ringe.")
        afterNonModel(ctx, NonModelReplyKind.READ_ONLY_TOOL, "Wie viel Speicher habe ich?")
        val task = LocalFollowUpTaskFrame.compose(t3, t3, ctx.sessionEvidence(s))
        assertTrue(task, task.contains("in der blauen 7"))
        val history = ctx.render(s)
        assertTrue(history, history.contains("Wie viel Speicher habe ich?"))
        assertTrue(history, history.contains("lokale Geräteprüfung"))
    }

    @Test fun clockAndStartkernInfoAlsoKeepContext() {
        listOf(NonModelReplyKind.DEVICE_CLOCK, NonModelReplyKind.STARTKERN_INFO, NonModelReplyKind.SAFETY_REFUSAL).forEach { kind ->
            val ctx = LocalConversationContext()
            ctx.record(s, t1, "Blau, 7.")
            afterNonModel(ctx, kind, "Wie spät ist es?")
            assertEquals(kind.name, listOf(t1, "Wie spät ist es?"), ctx.sessionEvidence(s))
        }
    }

    @Test fun memoryChangesStillInvalidateTheShortTermContext() {
        val ctx = LocalConversationContext()
        ctx.record(s, t1, "Blau, 7.")
        afterNonModel(ctx, NonModelReplyKind.MEMORY_CHANGE, "Merke dir: Die Kiste ist rot")
        assertEquals("", ctx.render(s))
    }

    @Test fun nonModelTurnNeverCarriesToolOutputOrModelTextIntoLaterPrompts() {
        val ctx = LocalConversationContext()
        ctx.record(s, t1, "Blau, 7.")
        ctx.recordNonModel(s, "Wie viel Speicher habe ich?", LocalContextRetentionPolicy.turnLabel(NonModelReplyKind.READ_ONLY_TOOL))
        val history = ctx.render(s)
        assertFalse(history.contains("GB"))
        // Bounded like every other turn: at most four turns survive.
        repeat(5) { ctx.recordNonModel(s, "Uhrzeit $it?", "x") }
        assertEquals(4, ctx.sessionEvidence(s)!!.size)
        // A different session never inherits it.
        assertEquals(emptyList<String>(), ctx.sessionEvidence("other"))
    }
}
