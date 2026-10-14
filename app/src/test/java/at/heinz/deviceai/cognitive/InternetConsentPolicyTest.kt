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
}
