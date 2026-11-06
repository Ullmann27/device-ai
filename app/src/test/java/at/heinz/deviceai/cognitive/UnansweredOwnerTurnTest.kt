package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class UnansweredOwnerTurnTest {
    @Test fun failedModelTurnKeepsOwnerLiteralButNoModelClaim() {
        val ctx = LocalConversationContext()
        ctx.record("session", "Ich wohne in Graz.", "Alles klar.")
        ctx.recordUnanswered("session", "Nein, das stimmt NICHT. Ich wohne nicht mehr dort.")
        val evidence = ctx.sessionEvidence("session")!!
        assertEquals(2, evidence.size)
        assertEquals("Nein, das stimmt NICHT. Ich wohne nicht mehr dort.", evidence.last())
        val preview = ctx.render("session")
        assertTrue(preview.contains("Keine bestätigte Modellantwort"))
        assertTrue(preview.contains("UNBESTÄTIGT"))
    }

    @Test fun longFailedOwnerInputForcesUncertainHistoryAndAbstention() {
        val limit = LocalDialogueContextLimits.MAX_OWNER_CHARACTERS
        for (request in listOf("x".repeat(limit) + " NICHT MEHR!", "ä".repeat(limit / 2 + 1) + " NICHT MEHR!")) {
            assertTrue(request.toByteArray(Charsets.UTF_8).size > limit)
            val ctx = LocalConversationContext()
            ctx.record("s", "Ich bin in Graz.", "Verstanden.")
            ctx.recordUnanswered("s", request)
            assertNull(ctx.sessionEvidence("s"))
            val rendered = ctx.render("s")
            assertTrue(rendered.contains("UNVOLLSTÄNDIG"))
            assertFalse("old assertion must not return", rendered.contains("Ich bin in Graz."))
        }
    }

    @Test fun completeFailedOwnerInputPreservesLateNegationWithinTheBudget() {
        val suffix = " NICHT MEHR!"
        for (request in listOf("Langer Text ".repeat(110) + suffix,
            "x".repeat(LocalDialogueContextLimits.MAX_OWNER_CHARACTERS - suffix.length) + suffix)) {
            val ctx = LocalConversationContext()
            ctx.record("s", "Ich bin in Graz.", "Verstanden.")
            ctx.recordUnanswered("s", request)
            assertEquals(listOf("Ich bin in Graz.", request), ctx.sessionEvidence("s"))
            assertTrue(ctx.render("s").contains(request))
            assertFalse(ctx.hasContinuation("s"))
        }
    }

    @Test fun repeatedFailedCallbackKeepsPreviousDistinctOwnerEvidence() {
        val ctx = LocalConversationContext()
        ctx.record("s", "Ich bin in Wien.", "Verstanden.")
        ctx.recordUnanswered("s", "Nein, ich bin jetzt in Linz.")
        repeat(8) { ctx.recordUnanswered("s", "Nein, ich bin jetzt in Linz.") }
        assertEquals(listOf("Ich bin in Wien.", "Nein, ich bin jetzt in Linz."),
            ctx.sessionEvidence("s"))
    }

    @Test fun newSessionInvalidatesPreviousFailedTurnAndCorrections() {
        val ctx = LocalConversationContext()
        ctx.recordUnanswered("s1", "Ich bin jetzt in Wien.")
        assertEquals(1, ctx.sessionEvidence("s1")!!.size)
        ctx.recordUnanswered("s2", "Eine neue Sitzung.")
        assertEquals(emptyList<String>(), ctx.sessionEvidence("s1"))
        assertEquals(listOf("Eine neue Sitzung."), ctx.sessionEvidence("s2"))
    }
}
