package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OwnerAppActionFlowTest {
    private val available = listOf(NativeAppCandidate("org.example.messenger", "Signal"),
        NativeAppCandidate("org.example.calendar", "Kalender"))

    private fun lookup(call: ToolCallRequest, apps: List<NativeAppCandidate> = available): String {
        val found = NativeAppLookup.search(call.arguments.getValue("query") as String, apps)
        return JSONObject().put("schema", "CONTINUUM_APP_DISCOVERY_V1").put("readOnly", true)
            .put("totalMatches", found.total).put("candidates", JSONArray(found.candidates.map {
                JSONObject().put("label", it.label).put("packageName", it.packageName)
            })).toString()
    }

    private fun invoke(gate: ToolGate, step: OwnerAppActionFlow.Step.Run): ToolExecutionResult =
        gate.execute(ToolInvocation(step.call.callId, step.call), step.confirmed)

    @Test fun ownerInputReachesRealLookupThenExactConfirmationThenGate() {
        for (input in listOf("Jarvis öffne mir Signal", "Kannst du mir bitte Signal öffnen?",
                "Mach bitte die App Signal auf.", "Ich brauche Signal")) {
            val intake = OwnerAppLookupIntent.inspect(input) as OwnerAppLookupIntent.Intake.Lookup
            val calls = mutableListOf<ToolCallRequest>()
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { call ->
                calls += call
                if (call.name == "find_app") lookup(call)
                else JSONObject().put("schema", "CONTINUUM_DEVICE_ACTION_V1").put("action", "LAUNCH_APP")
                    .put("package", "org.example.messenger").put("status", "VERIFIED_FOREGROUND")
                    .put("windowRevision", 7).toString()
            })
            val flow = OwnerAppActionFlow(intake.opening, intake.requireInstalledCandidate)
            val confirm = flow.returned(invoke(gate, flow.start(intake.query))) as OwnerAppActionFlow.Step.Confirm
            assertEquals(listOf("find_app"), calls.map { it.name })
            assertTrue(flow.canRetainAppLookupContext)
            assertEquals("org.example.messenger", confirm.call.arguments["packageOrName"])
            val approved = checkNotNull(flow.confirm(true))
            val result = flow.returned(invoke(gate, approved)) as OwnerAppActionFlow.Step.Done
            assertEquals(listOf("find_app", "launch_app"), calls.map { it.name })
            assertTrue(result.message.contains("neuen aktiven Fenster"))
            assertNull(flow.confirm(true))
        }
    }

    @Test fun singleCharacterAppLabelStillRequiresConcreteApproval() {
        val intake = OwnerAppLookupIntent.inspect("Öffne X") as OwnerAppLookupIntent.Intake.Lookup
        val app = NativeAppCandidate("org.example.single", "X")
        var launches = 0
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
            if (it.name == "launch_app") launches++
            lookup(it, listOf(app))
        })
        val flow = OwnerAppActionFlow(intake.opening)
        val confirmation = flow.returned(invoke(gate, flow.start(intake.query))) as OwnerAppActionFlow.Step.Confirm
        assertEquals(app.packageName, confirmation.call.arguments["packageOrName"])
        assertEquals(0, launches)
        assertEquals(ToolExecutionStatus.REJECTED, invoke(gate, checkNotNull(flow.confirm(false))).record.status)
        for (blank in listOf("", " ", "\n")) {
            assertNotNull(ToolRegistry.validate(ToolCallRequest("invalid", "find_app", mapOf("query" to blank))))
            assertThrows(IllegalArgumentException::class.java) { NativeAppLookup.search(blank, listOf(app)) }
        }
    }

    @Test fun declinedConfirmationAndStoppedApprovalNeverCallLaunchBackend() {
        for (stopped in listOf(false, true)) {
            var launches = 0
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { call ->
                if (call.name == "launch_app") launches++
                lookup(call)
            })
            val flow = OwnerAppActionFlow(true)
            assertTrue(flow.returned(invoke(gate, flow.start("Signal"))) is OwnerAppActionFlow.Step.Confirm)
            if (stopped) {
                flow.cancel()
                assertNull(flow.confirm(true))
            } else {
                val result = invoke(gate, checkNotNull(flow.confirm(false)))
                assertEquals(ToolExecutionStatus.REJECTED, result.record.status)
                assertTrue((flow.returned(result) as OwnerAppActionFlow.Step.Done).message.contains("abgelehnt"))
            }
            assertEquals(0, launches)
        }
    }

    @Test fun missingAppAndLookupOnlyDoNotOfferExecution() {
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner(::lookup))
        val missing = OwnerAppActionFlow(true)
        assertTrue((missing.returned(invoke(gate, missing.start("Fehlende App"))) as
            OwnerAppActionFlow.Step.Done).message.contains("keine passende"))
        val search = OwnerAppActionFlow(false)
        assertTrue(search.returned(invoke(gate, search.start("Signal"))) is OwnerAppActionFlow.Step.Done)
        assertNull(search.confirm(true))
    }

    @Test fun ambiguousAppsRequireSelectionBeforeConcreteConfirmation() {
        val apps = available + NativeAppCandidate("org.example.calendar.work", "Kalender")
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { lookup(it, apps) })
        val flow = OwnerAppActionFlow(true)
        val choose = flow.returned(invoke(gate, flow.start("Kalender"))) as OwnerAppActionFlow.Step.Choose
        assertNull(flow.confirm(true))
        assertNull(flow.choose(NativeAppCandidate("org.injected.app", "Kalender")))
        val confirmed = flow.choose(choose.candidates[1]) as OwnerAppActionFlow.Step.Confirm
        assertEquals(choose.candidates[1].packageName, confirmed.call.arguments["packageOrName"])
    }

    @Test fun permissionFailureCannotBecomeVerifiedOpening() {
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
            if (it.name == "find_app") lookup(it) else error("ACCESSIBILITY_OBSERVATION_NOT_GRANTED")
        })
        val flow = OwnerAppActionFlow(true)
        flow.returned(invoke(gate, flow.start("Signal")))
        val raw = invoke(gate, checkNotNull(flow.confirm(true)))
        val result = flow.returned(raw) as OwnerAppActionFlow.Step.Done
        assertEquals(ToolExecutionStatus.FAILED, raw.record.status)
        assertFalse(result.message.contains("wurde in einem neuen aktiven Fenster beobachtet"))
    }

    @Test fun staleCallAndReplayCannotSelectOrReopenAnApp() {
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner(::lookup))
        val flow = OwnerAppActionFlow(true)
        val run = flow.start("Signal")
        val raw = invoke(gate, run)
        assertNull(flow.returned(raw.copy(record = raw.record.copy(callId = "old-call"))))
        assertTrue(flow.returned(raw.copy(replayed = true)) is OwnerAppActionFlow.Step.Done)
        assertNull(flow.returned(raw))
        assertNull(flow.confirm(true))
    }

    @Test fun compoundRequestsStayWholeAndReferencesNeedActualCandidates() {
        val original = "Astra kannst du mir bitte Signal öffnen? Und meinem Vater schreiben?"
        assertEquals(original, (OwnerAppLookupIntent.inspect(original, "Astra") as
            OwnerAppLookupIntent.Intake.Planning).original)
        assertEquals("Signal", (OwnerAppLookupIntent.inspect("Astra, öffne Signal", "Astra") as
            OwnerAppLookupIntent.Intake.Lookup).query)
        assertEquals("Kalender", (OwnerAppLookupIntent.inspect(
            "Nova, könntest du bitte die Kalender App öffnen?", "Nova") as OwnerAppLookupIntent.Intake.Lookup).query)
        assertEquals("Signal", OwnerAppLookupIntent.parse("Mach mir Signal auf"))
        val needed = OwnerAppLookupIntent.inspect("Ich brauche WhatsApp") as OwnerAppLookupIntent.Intake.Lookup
        assertEquals("WhatsApp", needed.query)
        assertTrue(needed.opening)
        assertTrue(needed.requireInstalledCandidate)
        assertNull(OwnerAppLookupIntent.inspect("Ich brauche die PDF"))
        val maps = OwnerAppLookupIntent.inspect("Ich brauche die App Google Maps") as OwnerAppLookupIntent.Intake.Lookup
        assertEquals("Google Maps", maps.query)
        assertTrue(maps.opening)
        for (ordinary in listOf("Ich brauche Hilfe", "Ich brauche eine Pause", "Ich brauche einen Rat",
                "Ich brauche dich", "Ich brauche Hilfe bei der Steuer", "Ich brauche Ruhe"))
            assertNull(ordinary, OwnerAppLookupIntent.inspect(ordinary))
        assertTrue(OwnerAppLookupIntent.inspect("Ich brauche Signal und schreibe Anna") is
            OwnerAppLookupIntent.Intake.Planning)
        val message = OwnerAppLookupIntent.inspect("Schreibe WhatsApp") as OwnerAppLookupIntent.Intake.Lookup
        assertTrue(message.requireInstalledCandidate)
        assertTrue(message.messagePlanningIfMatched)
        assertTrue(OwnerAppLookupIntent.shouldClarifyMessage(message, true, 1, true))
        assertNull(OwnerAppLookupIntent.inspect("Schreibe einen langen Bericht über Wien"))
        assertTrue(OwnerAppLookupIntent.inspect("Ich will was von WhatsApp") is OwnerAppLookupIntent.Intake.Clarify)
        for (request in listOf("Öffne die PDF", "Öffne den Download-Ordner", "Öffne /sdcard/Download"))
            assertNull(request, OwnerAppLookupIntent.inspect(request))
        assertTrue((OwnerAppLookupIntent.inspect(original, "Astra") as
            OwnerAppLookupIntent.Intake.Planning).clarification!!.contains("Entwurf"))
        assertTrue(OwnerAppLookupIntent.inspect("Jarvis ich bräuchte etwas von Kalender") is
            OwnerAppLookupIntent.Intake.Clarify)
        assertEquals("die App", (OwnerAppLookupIntent.inspect("Öffne die App") as
            OwnerAppLookupIntent.Intake.Lookup).query)
        assertNull(OwnerAppLookupIntent.contextualTarget("sie", available))
        assertNull(OwnerAppLookupIntent.contextualTarget("9", available))
        assertEquals(available[1].packageName, OwnerAppLookupIntent.contextualTarget("zweite", available))
    }
    @Test fun lookupVerifiedOnlyAfterFreshValidatedDiscoveryPayload() {
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner(::lookup))
        val flow = OwnerAppActionFlow(true)
        val run = flow.start("Signal")
        assertFalse(flow.lookupVerified)
        val result = invoke(gate, run)
        assertTrue(flow.returned(result) is OwnerAppActionFlow.Step.Confirm)
        assertTrue(flow.lookupVerified)
        assertTrue(flow.exactMatchVerified)

        val replayed = OwnerAppActionFlow(true)
        val replayRun = replayed.start("Signal")
        val replayResult = invoke(gate, replayRun).copy(replayed = true)
        assertTrue(replayed.returned(replayResult) is OwnerAppActionFlow.Step.Done)
        assertFalse(replayed.lookupVerified)
        assertFalse(replayed.exactMatchVerified)
    }

    @Test fun fuzzyImplicitAppNeedsFallBackBeforeAnySelection() {
        val examples = listOf(
            "Ich brauche Whats" to NativeAppCandidate("org.example.messenger", "WhatsApp"),
            "Ich brauche Urlaub" to NativeAppCandidate("org.example.trip", "Urlaub Planer")
        )
        examples.forEach { (request, app) ->
            val intake = OwnerAppLookupIntent.inspect(request) as OwnerAppLookupIntent.Intake.Lookup
            assertTrue(intake.requireInstalledCandidate)
            val dispatched = mutableListOf<String>()
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { call ->
                dispatched += call.name
                if (call.name == "find_app") lookup(call, listOf(app))
                else error("Unexpected launch for fuzzy request")
            })
            val flow = OwnerAppActionFlow(intake.opening, intake.requireInstalledCandidate)
            val step = flow.returned(invoke(gate, flow.start(intake.query)))
            assertTrue(request, step is OwnerAppActionFlow.Step.Done)
            assertEquals(listOf("find_app"), dispatched)
            assertTrue(flow.lookupVerified)
            assertFalse(flow.exactMatchVerified)
            assertTrue(flow.lookupFinishedWithoutInteraction)
            assertFalse(flow.canRetainAppLookupContext)
            assertTrue(OwnerAppLookupIntent.shouldFallbackToConversation(
                intake, flow.lookupVerified, flow.candidates.size, flow.exactMatchVerified
            ))
            assertNull(flow.choose(app))
            assertNull(flow.confirm(true))
        }
    }

    @Test fun explicitSingleFuzzyAppSkipsChoiceButRetainsApprovalAndLaunchReceipts() {
        val app = NativeAppCandidate("org.example.messenger", "WhatsApp")
        var launches = 0
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { call ->
            if (call.name == "find_app") lookup(call, listOf(app))
            else { launches++; JSONObject().put("schema", "CONTINUUM_DEVICE_ACTION_V1")
                .put("action", "LAUNCH_APP").put("package", app.packageName)
                .put("status", "VERIFIED_FOREGROUND").put("windowRevision", 7).toString() }
        })
        val intake = OwnerAppLookupIntent.inspect("Öffne Whats") as OwnerAppLookupIntent.Intake.Lookup
        assertFalse(intake.requireInstalledCandidate)

        val cancelled = OwnerAppActionFlow(intake.opening, intake.requireInstalledCandidate)
        val offered = cancelled.returned(invoke(gate, cancelled.start(intake.query)))
        assertTrue(offered is OwnerAppActionFlow.Step.Confirm)
        assertFalse(cancelled.lookupFinishedWithoutInteraction)
        assertTrue(cancelled.canRetainAppLookupContext)
        val cancelReceipt = cancelled.returned(invoke(gate, checkNotNull(cancelled.confirm(false)))) as OwnerAppActionFlow.Step.Done
        assertTrue(cancelReceipt.message.contains("abgelehnt"))
        assertEquals(0, launches)
        assertFalse(cancelled.lookupFinishedWithoutInteraction)

        val launched = OwnerAppActionFlow(intake.opening, intake.requireInstalledCandidate)
        val confirmation = launched.returned(invoke(gate, launched.start(intake.query))) as
            OwnerAppActionFlow.Step.Confirm
        assertEquals(app.packageName, confirmation.candidate.packageName)
        val receipt = launched.returned(invoke(gate, checkNotNull(launched.confirm(true)))) as
            OwnerAppActionFlow.Step.Done
        assertTrue(receipt.message.contains("neuen aktiven Fenster"))
        assertEquals(1, launches)
        assertEquals(app, launched.launchedCandidate)
        assertTrue(launched.canRetainAppLookupContext)
        assertFalse(launched.lookupFinishedWithoutInteraction)
    }

    @Test fun lookupFallbackGuardRejectsPostSelectionCompletionEvenWithFuzzyNeed() {
        val intake = OwnerAppLookupIntent.inspect("Ich brauche Whats") as OwnerAppLookupIntent.Intake.Lookup
        val app = NativeAppCandidate("org.example.messenger", "WhatsApp")
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { lookup(it, listOf(app)) })
        val legacySelection = OwnerAppActionFlow(intake.opening)
        assertTrue(legacySelection.returned(invoke(gate, legacySelection.start(intake.query))) is
            OwnerAppActionFlow.Step.Confirm)
        val cancelReceipt = legacySelection.returned(invoke(gate, checkNotNull(legacySelection.confirm(false)))) as OwnerAppActionFlow.Step.Done
        assertTrue(cancelReceipt.message.contains("abgelehnt"))
        assertFalse(legacySelection.lookupFinishedWithoutInteraction)
        assertTrue(OwnerAppLookupIntent.shouldFallbackToConversation(
            intake, legacySelection.lookupVerified, legacySelection.candidates.size,
            legacySelection.exactMatchVerified
        ))
        assertFalse(legacySelection.lookupFinishedWithoutInteraction &&
            OwnerAppLookupIntent.shouldFallbackToConversation(
                intake, legacySelection.lookupVerified, legacySelection.candidates.size,
                legacySelection.exactMatchVerified
            ))
    }

    @Test fun fuzzyImplicitIntentsFinishBeforeSelectionOrLaunch() {
        val cases = listOf(
            "Ich brauche Sig" to NativeAppCandidate("org.example.signal", "Signal"),
            "Ich brauche Urlaub" to NativeAppCandidate("org.example.travel", "Urlaubsplaner"),
            "Schreibe Notizen" to NativeAppCandidate("org.example.notes", "Meine Notizen")
        )
        for ((request, app) in cases) {
            val intake = OwnerAppLookupIntent.inspect(request) as OwnerAppLookupIntent.Intake.Lookup
            val calls = mutableListOf<String>()
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                calls += it.name
                lookup(it, listOf(app))
            })
            val flow = OwnerAppActionFlow(intake.opening, requireExactInstalledCandidate = intake.requireInstalledCandidate)
            val result = flow.returned(invoke(gate, flow.start(intake.query)))
            assertTrue(request, result is OwnerAppActionFlow.Step.Done)
            assertTrue(flow.lookupVerified)
            assertFalse(flow.exactMatchVerified)
            assertTrue(OwnerAppLookupIntent.shouldFallbackToConversation(
                intake, flow.lookupVerified, flow.candidates.size, flow.exactMatchVerified))
            assertFalse(OwnerAppLookupIntent.shouldClarifyMessage(
                intake, flow.lookupVerified, flow.candidates.size, flow.exactMatchVerified))
            assertNull(flow.choose(app))
            assertNull(flow.choose(null))
            assertNull(flow.confirm(true))
            assertEquals(listOf("find_app"), calls)
        }
    }

    @Test fun exactImplicitAmbiguityPreservesCancellationReceipt() {
        val intake = OwnerAppLookupIntent.inspect("Ich brauche Kalender") as OwnerAppLookupIntent.Intake.Lookup
        val apps = available + NativeAppCandidate("org.example.calendar.work", "Kalender")
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { lookup(it, apps) })
        val flow = OwnerAppActionFlow(intake.opening, requireExactInstalledCandidate = intake.requireInstalledCandidate)
        assertTrue(flow.returned(invoke(gate, flow.start(intake.query))) is OwnerAppActionFlow.Step.Choose)
        val result = flow.choose(null) as OwnerAppActionFlow.Step.Done
        assertTrue(result.message.contains("App-Auswahl abgebrochen"))
        assertFalse(OwnerAppLookupIntent.shouldFallbackToConversation(
            intake, flow.lookupVerified, flow.candidates.size, flow.exactMatchVerified))
        assertNull(flow.confirm(true))
    }

    @Test fun explicitFuzzyLaunchPreservesApprovedAndRejectedReceipts() {
        for (approved in listOf(true, false)) {
            val intake = OwnerAppLookupIntent.inspect("Öffne Sig") as OwnerAppLookupIntent.Intake.Lookup
            val calls = mutableListOf<String>()
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                calls += it.name
                if (it.name == "find_app") lookup(it)
                else JSONObject().put("schema", "CONTINUUM_DEVICE_ACTION_V1").put("action", "LAUNCH_APP")
                    .put("package", "org.example.messenger").put("status", "VERIFIED_FOREGROUND")
                    .put("windowRevision", 7).toString()
            })
            val flow = OwnerAppActionFlow(intake.opening, requireExactInstalledCandidate = intake.requireInstalledCandidate)
            val confirmation = flow.returned(invoke(gate, flow.start(intake.query))) as OwnerAppActionFlow.Step.Confirm
            assertEquals("org.example.messenger", confirmation.candidate.packageName)
            assertEquals(listOf("find_app"), calls)
            val result = flow.returned(invoke(gate, checkNotNull(flow.confirm(approved)))) as OwnerAppActionFlow.Step.Done
            assertFalse(OwnerAppLookupIntent.shouldFallbackToConversation(
                intake, flow.lookupVerified, flow.candidates.size, flow.exactMatchVerified))
            assertTrue(result.message.contains(if (approved) "neuen aktiven Fenster" else "abgelehnt"))
            assertEquals(if (approved) listOf("find_app", "launch_app") else listOf("find_app"), calls)
        }
    }

    @Test fun readOnlyDiscoveryNeverSuggestsImplicitOpeningOrExecutesLaunch() {
        val calls = mutableListOf<String>()
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { call ->
            calls.add(call.name)
            check(call.name == "find_app")
            lookup(call)
        })
        val flow = OwnerAppActionFlow(false)
        val done = flow.returned(invoke(gate, flow.start("Signal"))) as OwnerAppActionFlow.Step.Done
        assertEquals(listOf("find_app"), calls)
        assertTrue(done.message.contains("nichts geöffnet"))
        assertFalse(done.message.contains("möchtest du öffnen"))
        assertNull(flow.confirm(true))
    }

}
