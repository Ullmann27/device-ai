package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolExecutionRecord
import at.heinz.deviceai.ToolExecutionResult
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OwnerAppVerbFinalIntentTest {
    @Test fun pluralStatementDoesNotBecomeAppLookup() {
        assertNull(OwnerAppLookupIntent.inspect("Wir starten"))
    }

    @Test fun sharedSuggestionDoesNotBecomeAppLookup() {
        assertNull(OwnerAppLookupIntent.inspect("Lass uns jetzt starten"))
    }

    @Test fun futurePlanDoesNotBecomeAppLookup() {
        assertNull(OwnerAppLookupIntent.inspect("Morgen wollen wir das Projekt starten"))
    }

    @Test fun pluralAndIndirectFormsStayConversation() {
        listOf(
            "Ihr wollt Chrome starten", "Lasst uns Chrome starten",
            "Morgen müssen wir das Projekt starten", "Morgen werden wir die Sitzung starten",
            "Heute sollen die Kollegen WhatsApp öffnen", "Heute dürfen die Kollegen Chrome starten",
            "Können wir WhatsApp öffnen?", "Könnten die Kollegen Chrome starten?",
            "Wir möchten Signal öffnen", "Meine Frau will WhatsApp öffnen",
            "Ihr könnt morgen starten", "Heute werden wir das Projekt starten", "Lass uns die Planung starten"
        ).forEach { assertNull(it, OwnerAppLookupIntent.inspect(it)) }
    }

    @Test fun bareNamesKeepTheirTargetButRequireInstalledEvidence() {
        mapOf(
            "WhatsApp öffnen" to "WhatsApp", "WhatsApp öffnen bitte" to "WhatsApp",
            "Bitte Chrome starten" to "Chrome", "Die Einstellungen öffnen" to "Einstellungen",
            "Google Maps öffnen" to "Google Maps", "Jarvis, Signal öffnen" to "Signal",
            "com.whatsapp öffnen" to "com.whatsapp", "Google Maps öffnen bitte" to "Google Maps",
            "Samsung Internet starten" to "Samsung Internet"
        ).forEach { (request, name) ->
            val intake = OwnerAppLookupIntent.inspect(request) as OwnerAppLookupIntent.Intake.Lookup
            assertEquals(request, name, intake.query)
            assertTrue(request, intake.opening)
            assertTrue(request, intake.requireInstalledCandidate)
        }
    }

    @Test fun unmatchedBareTargetReturnsToConversation() {
        val intake = lookup("Projekt starten")
        val flow = OwnerAppActionFlow(intake.opening, intake.requireInstalledCandidate)
        val call = flow.start(intake.query).call
        assertTrue(flow.returned(discovery(call)) is OwnerAppActionFlow.Step.Done)
        assertTrue(flow.lookupFinishedWithoutInteraction)
        assertTrue(OwnerAppLookupIntent.shouldFallbackToConversation(
            intake, flow.lookupVerified, flow.candidates.size, flow.exactMatchVerified))
        assertFalse(flow.canRetainAppLookupContext)
        assertNull(flow.confirm(true))
    }

    @Test fun fuzzyBareTargetCannotOfferAnAppSelection() {
        val intake = lookup("Sig starten")
        val flow = OwnerAppActionFlow(intake.opening, intake.requireInstalledCandidate)
        val call = flow.start(intake.query).call
        assertTrue(flow.returned(discovery(call, NativeAppCandidate("org.example.signal", "Signal")))
            is OwnerAppActionFlow.Step.Done)
        assertTrue(OwnerAppLookupIntent.shouldFallbackToConversation(
            intake, flow.lookupVerified, flow.candidates.size, flow.exactMatchVerified))
        assertFalse(flow.canRetainAppLookupContext)
        assertNull(flow.confirm(true))
    }

    @Test fun exactInstalledNameStillRequiresTheExistingConfirmation() {
        val intake = lookup("Signal öffnen")
        val flow = OwnerAppActionFlow(intake.opening, intake.requireInstalledCandidate)
        val call = flow.start(intake.query).call
        assertEquals("find_app", call.name)
        val step = flow.returned(discovery(call, NativeAppCandidate("org.example.signal", "Signal")))
        assertTrue(step is OwnerAppActionFlow.Step.Confirm)
        assertFalse(OwnerAppLookupIntent.shouldFallbackToConversation(
            intake, flow.lookupVerified, flow.candidates.size, flow.exactMatchVerified))
        assertEquals("launch_app", checkNotNull(flow.confirm(true)).call.name)
    }

    @Test fun unverifiedDiscoveryCannotProveAnInstalledMiss() {
        val intake = lookup("Projekt starten")
        val flow = OwnerAppActionFlow(intake.opening, intake.requireInstalledCandidate)
        val call = flow.start(intake.query).call
        assertTrue(flow.returned(ToolExecutionResult(null, ToolExecutionRecord(
            call.callId, call.name, ToolRegistry.risk(call.name), call.arguments,
            "unavailable", ToolExecutionStatus.REJECTED, 1, 0))) is OwnerAppActionFlow.Step.Done)
        assertFalse(flow.lookupVerified)
        assertFalse(OwnerAppLookupIntent.shouldFallbackToConversation(
            intake, flow.lookupVerified, flow.candidates.size, flow.exactMatchVerified))
        assertFalse(flow.canRetainAppLookupContext)
        assertNull(flow.confirm(true))
    }

    @Test fun ordinaryTurnAlsoInvalidatesAnExistingAppNameQuestion() {
        for (request in listOf("Wir starten", "Lass uns jetzt starten",
                "Morgen wollen wir das Projekt starten", "Heute müssen wir starten")) {
            val pending = OwnerAppClarification()
            pending.requestName("session", 7, opening = true)
            assertNull(request, pending.consume("session", 7, request))
            assertNull(request, pending.consume("session", 7, "Signal"))
        }
    }

    private fun lookup(request: String) = OwnerAppLookupIntent.inspect(request) as OwnerAppLookupIntent.Intake.Lookup

    private fun discovery(call: ToolCallRequest, vararg candidates: NativeAppCandidate): ToolExecutionResult {
        val output = JSONObject().put("schema", "CONTINUUM_APP_DISCOVERY_V1").put("readOnly", true)
            .put("totalMatches", candidates.size).put("candidates", JSONArray().apply {
                candidates.forEach { put(JSONObject().put("label", it.label).put("packageName", it.packageName)) }
            }).toString()
        return ToolExecutionResult(output, ToolExecutionRecord(call.callId, call.name,
            ToolRegistry.risk(call.name), call.arguments, "ok", ToolExecutionStatus.SUCCEEDED, 1, output.length))
    }
}
