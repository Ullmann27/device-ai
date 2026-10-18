package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class InternetConsentPolicyTest {
    @Test fun approvalIsOneShotAndBoundToExactQuestion() {
        val policy = InternetConsentPolicy()
        assertTrue(policy.request("Welche Quellen gibt es?"))
        assertFalse(policy.approve("Andere Frage"))
        assertFalse(policy.approve("Welche Quellen gibt es?"))
        assertTrue(policy.request("Welche Quellen gibt es?"))
        assertTrue(policy.approve("Welche Quellen gibt es?"))
        assertFalse(policy.approve("Welche Quellen gibt es?"))
    }

    @Test fun disabledModeAndCancellationDenyNetwork() {
        val policy = InternetConsentPolicy()
        assertTrue(policy.request("Quellen"))
        policy.cancel()
        assertFalse(policy.approve("Quellen"))
        policy.setMode(InternetAccessMode.OFF)
        assertFalse(policy.request("Quellen"))
        assertFalse(policy.approve("Quellen"))
    }

    @Test fun rejectsBlankOrOversizedRequests() {
        val policy = InternetConsentPolicy()
        assertFalse(policy.request(" "))
        assertFalse(policy.request("x".repeat(2049)))
    }
    @Test fun researchGateRejectsStaleOrReplayedPermission() {
        val consent = InternetConsentPolicy()
        val gate = InternetResearchGate(consent)
        assertFalse(gate.authorize("question"))
        assertTrue(consent.request("question"))
        assertFalse(gate.authorize("different"))
        assertFalse(gate.authorize("question"))
        assertTrue(consent.request("question"))
        assertTrue(gate.authorize("question"))
        assertFalse(gate.authorize("question"))
    }

    @Test fun alwaysOnDoesNotTurnOldAskTicketsIntoPermanentGrants() {
        val policy=InternetConsentPolicy()
        val ticket=policy.requestTicket("public sources")!!
        policy.setMode(InternetAccessMode.ALWAYS_ON)
        assertFalse(policy.consume(ticket))
        assertFalse(policy.isCurrent(ticket))
        assertFalse(policy.request("another source"))
    }

    @Test fun alwaysOnIssuesSeparateRevocableCapabilities() {
        val policy=InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val first=policy.automaticTicket("public article")!!
        assertTrue(policy.isCurrent(first))
        val second=policy.automaticTicket("another article")!!
        assertFalse(policy.isCurrent(first))
        assertTrue(policy.isCurrent(second))
        policy.setMode(InternetAccessMode.OFF)
        assertFalse(policy.isCurrent(second))
        assertNull(policy.automaticTicket("new article"))
    }

    @Test fun switchingOffRevokesPendingResearch() {
        val consent = InternetConsentPolicy()
        val gate = InternetResearchGate(consent)
        assertTrue(consent.request("topic"))
        consent.setMode(InternetAccessMode.OFF)
        assertFalse(gate.authorize("topic"))
        consent.setMode(InternetAccessMode.ASK)
        assertFalse(gate.authorize("topic"))
    }

}
