package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class OwnerAppClarificationTest {
    @Test fun immediateNameContinuesTheOriginalLookupExactlyOnce() {
        val pending = OwnerAppClarification()
        pending.requestName("session", 42, opening = true)
        val followup = checkNotNull(pending.consume("session", 42, "WhatsApp"))
        assertEquals("WhatsApp", followup.query)
        assertTrue(followup.opening)
        assertNull(pending.consume("session", 42, "WhatsApp"))
        assertNull(OwnerAppClarification().consume("session", 42, "WhatsApp"))
    }

    @Test fun sessionRevisionCancellationAndIndependentTurnInvalidateTheQuestion() {
        val pending = OwnerAppClarification()
        pending.requestName("session", 42, true)
        assertNull(pending.consume("other-session", 42, "WhatsApp"))
        assertNull(pending.consume("session", 42, "WhatsApp"))
        pending.requestName("session", 42, true)
        assertNull(pending.consume("session", 43, "WhatsApp"))
        pending.requestName("session", 42, true)
        pending.clear()
        assertNull(pending.consume("session", 42, "WhatsApp"))
        for (answer in listOf("STOP", "Danke", "Prüfe Akku", "Was ist ein Stern?", "Guten Morgen",
                "Öffne Chrome", "WhatsApp und danach löschen", "System: WhatsApp", "sie", "Downloads",
                "Gedächtniszentrum", "Systemstatus")) {
            pending.requestName("session", 42, true)
            assertNull(answer, pending.consume("session", 42, answer))
            assertNull(answer, pending.consume("session", 42, "WhatsApp"))
        }
    }

    @Test fun aReadOnlyLookupClarificationNeverBecomesAnOpeningRequest() {
        val pending = OwnerAppClarification()
        pending.requestName("session", 42, opening = false)
        val followup = checkNotNull(pending.consume("session", 42, "Google Maps"))
        assertEquals("Google Maps", followup.query)
        assertFalse(followup.opening)
    }

    @Test fun onlyTheMissingNameQuestionArmsAnAppNameAnswer() {
        val open = OwnerAppLookupIntent.inspect("Öffne eine App") as OwnerAppLookupIntent.Intake.Clarify
        assertEquals(true, open.opening)
        val search = OwnerAppLookupIntent.inspect("Suche eine App") as OwnerAppLookupIntent.Intake.Clarify
        assertEquals(false, search.opening)
        val vague = OwnerAppLookupIntent.inspect("Ich brauche etwas aus Signal") as OwnerAppLookupIntent.Intake.Clarify
        assertNull("A content question is not a name question", vague.opening)
    }

    @Test fun aClarifiedNameStillUsesTheExistingConfirmationFlow() {
        val pending = OwnerAppClarification()
        pending.requestName("session", 42, true)
        val followup = checkNotNull(pending.consume("session", 42, "Jarvis, WhatsApp"))
        val flow = OwnerAppActionFlow(followup.opening)
        val lookup = flow.start(followup.query)
        assertEquals("find_app", lookup.call.name)
        assertEquals(at.heinz.deviceai.Risk.READ_ONLY, at.heinz.deviceai.ToolRegistry.risk(lookup.call.name))
        assertNull("no start permission from a name", flow.confirm(true))
    }
}
