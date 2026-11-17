package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class InternetConsentPolicyTest {
    @Test fun persistedModeReloadNeverRestoresAskApprovalAndOffRevokesEverySession() {
        // JVM disk roundtrip of the SAME preference value; actual Android SharedPreferences
        // commit/read wiring is checked by the structural contract and needs device E2E.
        val dir = Files.createTempDirectory("continuum-consent-restart")
        try {
            val file = dir.resolve("mode")
            val call = WebToolRequest.Search("restart regression")
            for (mode in InternetAccessMode.entries) {
                Files.write(file, mode.name.toByteArray(Charsets.UTF_8))
                val consent = InternetConsentPolicy(InternetConsentPolicy.restoredMode(String(Files.readAllBytes(file), Charsets.UTF_8)))
                val gate = ApprovedWebToolGate(consent)
                assertEquals(mode, consent.mode)
                val pending = gate.prepare(call)
                val restarted = InternetConsentPolicy(InternetConsentPolicy.restoredMode(String(Files.readAllBytes(file), Charsets.UTF_8)))
                val newGate = ApprovedWebToolGate(restarted)
                assertEquals(mode, restarted.mode)
                if (pending != null) assertNull(newGate.approve(pending, ResearchMode.QUICK))
                val session = when (mode) {
                    InternetAccessMode.OFF -> {
                        assertNull(newGate.prepare(call))
                        assertNull(newGate.authorizeAlwaysOn(call, ResearchMode.QUICK))
                        null
                    }
                    InternetAccessMode.ASK -> {
                        assertNull(newGate.authorizeAlwaysOn(call, ResearchMode.QUICK))
                        newGate.approve(newGate.prepare(call)!!, ResearchMode.QUICK)!!
                    }
                    InternetAccessMode.ALWAYS_ON -> newGate.authorizeAlwaysOn(call, ResearchMode.QUICK)!!
                }
                if (session != null) {
                    session.execute(call) { "verified call happened once" }
                    assertTrue(session.isAuthorized())
                    restarted.setMode(InternetAccessMode.OFF)
                    Files.write(file, restarted.mode.name.toByteArray(Charsets.UTF_8))
                    assertFalse(session.isAuthorized())
                    assertNull(newGate.authorizeAlwaysOn(call, ResearchMode.QUICK))
                    assertEquals(InternetAccessMode.OFF, InternetConsentPolicy.restoredMode(String(Files.readAllBytes(file), Charsets.UTF_8)))
                }
            }
        } finally { dir.toFile().deleteRecursively() }
    }
    @Test fun explicitAskSurvivesPreferenceRestoreAndReopeningSettings() {
        for (mode in InternetAccessMode.entries) {
            assertEquals(mode, InternetConsentPolicy.restoredMode(mode.name))
            val reopened = InternetConsentPolicy(InternetConsentPolicy.restoredMode(mode.name))
            assertEquals(mode, reopened.mode)
            assertEquals(mode, InternetConsentPolicy.restoredMode(reopened.mode.name))
        }
        assertEquals(InternetAccessMode.ALWAYS_ON, InternetConsentPolicy.restoredMode(null))
        assertEquals(InternetAccessMode.OFF, InternetConsentPolicy.restoredMode("corrupt-mode"))
    }
    @Test fun supportsExplicitAlwaysOnMode() {
        assertTrue("Owner can enable persistent Internet consent",
            InternetAccessMode.entries.any { it.name == "ALWAYS_ON" })
    }

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
