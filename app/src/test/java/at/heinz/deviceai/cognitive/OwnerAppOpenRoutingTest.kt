package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ContinuumWorkerChatRouter
import at.heinz.deviceai.GroqInvocationRouter
import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolExecutionRecord
import at.heinz.deviceai.ToolExecutionResult
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.learning.ContinuumBrainCommand
import at.heinz.deviceai.systemintelligence.SystemIntelligenceCommands
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #765 (Code53 Fold7): a spoken „Öffne WhatsApp“ once ended in the local-model context message instead of
 * the app lookup. The owner later confirmed the voice launch via the accessibility path (PASS) – this
 * test pins the routing so it cannot regress: explicit app-open commands reach `OwnerAppLookupIntent`
 * before any model route, every router the send path consults earlier leaves them alone, and an UNKNOWN
 * launch outcome is reported, never retried.
 */
class OwnerAppOpenRoutingTest {
    /** The routers AgentActivity.sendCurrentInput consults before OwnerAppLookupIntent, in that order. */
    private fun earlierRouterClaims(request: String): String? = when {
        OwnerChatIntentRouter.route(request) != null -> "OwnerChatIntentRouter"
        OwnerCorrectionIntent.parse(request) != null -> "OwnerCorrectionIntent"
        SystemIntelligenceCommands.isDiagnosisRequest(request) -> "diagnosis"
        OwnerPreviousBatteryIntent.isPreviousReadingRequest(request) -> "previousBattery"
        WorkDecisionRecall.parse(request) != null -> "WorkDecisionRecall"
        WorkRecall.parse(request) != null -> "WorkRecall"
        OwnerDocumentIntent.parse(request, null) != null -> "OwnerDocumentIntent"
        ContinuumBrainCommand.parse(request) != null -> "ContinuumBrainCommand"
        GroqInvocationRouter.extractQuestion(request) != null -> "Groq"
        ContinuumWorkerChatRouter.parse(request) != null -> "ContinuumWorkerChatRouter"
        OwnerInternalNavigationIntent.resolve(request, null) != null -> "OwnerInternalNavigationIntent"
        OwnerResearchCommand.parse(request, null) != null -> "OwnerResearchCommand"
        else -> null
    }

    @Test fun explicitAppOpenCommandsReachTheAppLookupBeforeAnyModelRoute() {
        mapOf(
            "Öffne WhatsApp" to "WhatsApp",
            "öffne WhatsApp." to "WhatsApp",
            "Öffne bitte WhatsApp" to "WhatsApp",
            "Continuum, öffne WhatsApp" to "WhatsApp",
            "Hey Continuum, öffne WhatsApp bitte" to "WhatsApp",
            "Kannst du WhatsApp öffnen?" to "WhatsApp",
            "öffne Facebook" to "Facebook",
            "öffne Chrome" to "Chrome",
            "Öffne die Einstellungen" to "Einstellungen",
            "Starte Chrome" to "Chrome",
            // Verb-final command form, as ASR often delivers it (#765 test list).
            "WhatsApp öffnen" to "WhatsApp",
            "WhatsApp öffnen bitte" to "WhatsApp",
            "Bitte Chrome starten" to "Chrome",
            "Die Einstellungen öffnen" to "Einstellungen"
        ).forEach { (request, app) ->
            assertNull("claimed earlier: $request", earlierRouterClaims(request))
            val intake = OwnerAppLookupIntent.inspect(request)
            assertTrue("no app lookup: $request -> $intake", intake is OwnerAppLookupIntent.Intake.Lookup)
            intake as OwnerAppLookupIntent.Intake.Lookup
            assertEquals(request, app, intake.query)
            assertTrue(request, intake.opening)
            assertFalse(request, intake.requireInstalledCandidate)
        }
    }

    @Test fun verbFinalFormDoesNotTurnQuestionsOrCompoundsIntoLaunches() {
        listOf(
            "Wie kann ich WhatsApp öffnen?",
            "Warum lässt sich WhatsApp nicht öffnen?",
            "WhatsApp nicht öffnen",
            "WhatsApp öffnen und Nachricht senden",
            "Downloads öffnen",
            "Ich weiß nicht, wie man WhatsApp öffnen soll"
        ).forEach { request ->
            val intake = OwnerAppLookupIntent.inspect(request)
            assertFalse("launch from: $request -> $intake",
                intake is OwnerAppLookupIntent.Intake.Lookup && intake.opening)
        }
    }

    @Test fun aDiagnosisCommandIsNotMistakenForAnAppNamedLikeIt() {
        listOf("Starte eine Systemdiagnose.", "Starte die Analyse meines Handys").forEach { request ->
            assertFalse(request, OwnerAppLookupIntent.inspect(request) is OwnerAppLookupIntent.Intake.Lookup)
        }
    }

    @Test fun unknownLaunchOutcomeIsReportedAndNeverRetried() {
        val flow = OwnerAppActionFlow(opening = true)
        val lookup = flow.start("WhatsApp")
        val discovery = JSONObject().put("schema", "CONTINUUM_APP_DISCOVERY_V1").put("readOnly", true)
            .put("totalMatches", 1).put("candidates", JSONArray().put(
                JSONObject().put("label", "WhatsApp").put("packageName", "com.whatsapp")))
        val confirm = flow.returned(result(lookup.call, discovery.toString(), ToolExecutionStatus.SUCCEEDED))
            as OwnerAppActionFlow.Step.Confirm
        assertEquals(Risk.CONFIRM, at.heinz.deviceai.ToolRegistry.risk(confirm.call.name))
        val launch = checkNotNull(flow.confirm(true))
        val unknown = result(launch.call, null, ToolExecutionStatus.REJECTED, uncertain = true)
        val done = flow.returned(unknown) as OwnerAppActionFlow.Step.Done
        assertTrue(done.message, done.message.contains("nicht bestätigt"))
        assertFalse(done.message, done.message.contains("wurde in einem neuen aktiven Fenster beobachtet"))
        // Nothing can re-dispatch the possibly executed launch: no second confirmation, no replayed result.
        assertNull(flow.confirm(true))
        assertNull(flow.returned(unknown))
        assertNull(flow.choose(confirm.candidate))
    }

    private fun result(call: ToolCallRequest, output: String?, status: ToolExecutionStatus,
                       uncertain: Boolean = false) = ToolExecutionResult(output,
        ToolExecutionRecord(call.callId, call.name, at.heinz.deviceai.ToolRegistry.risk(call.name), call.arguments,
            if (uncertain) "outcome UNKNOWN after possible dispatch" else "ok", status, 1, output?.length ?: 0, uncertain))
}
