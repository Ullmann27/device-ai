package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OwnerAppSingleCandidateTest {
    private val app = NativeAppCandidate("org.example.whatsapp", "WhatsApp")
    private fun lookup(total: Int) = JSONObject().put("schema", "CONTINUUM_APP_DISCOVERY_V1")
        .put("readOnly", true).put("totalMatches", total).put("candidates", JSONArray().put(
            JSONObject().put("packageName", app.packageName).put("label", app.label))).toString()
    private fun launch(pkg: String = app.packageName) = JSONObject().put("schema", "CONTINUUM_DEVICE_ACTION_V1")
        .put("action", "LAUNCH_APP").put("package", pkg).put("status", "VERIFIED_FOREGROUND")
        .put("windowRevision", 7).toString()
    private fun run(gate: ToolGate, step: OwnerAppActionFlow.Step.Run) =
        gate.execute(ToolInvocation(step.call.callId, step.call), step.confirmed)

    @Test fun singleFuzzyCandidateNeedsApprovalButNoRedundantChoice() {
        for (approved in listOf(false, true)) {
            var launched = 0
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                if (it.name == "find_app") lookup(1) else { launched++; launch() }
            })
            val flow = OwnerAppActionFlow(true)
            val next = flow.returned(run(gate, flow.start("Whats")))
            assertTrue(next is OwnerAppActionFlow.Step.Confirm)
            assertFalse(flow.exactMatchVerified)
            assertNull(flow.launchedCandidate)
            assertEquals(0, launched)
            val call = checkNotNull(flow.confirm(approved))
            assertEquals(approved, call.confirmed)
            val result = run(gate, call)
            assertEquals(if (approved) ToolExecutionStatus.SUCCEEDED else ToolExecutionStatus.REJECTED,
                result.record.status)
            assertTrue(flow.returned(result) is OwnerAppActionFlow.Step.Done)
            assertEquals(if (approved) 1 else 0, launched)
            assertEquals(if (approved) app else null, flow.launchedCandidate)
        }
    }

    @Test fun oneReturnedRowIsNotSingleMatchWhenDiscoveryReportsMore() {
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { lookup(2) })
        val flow = OwnerAppActionFlow(true)
        val next = flow.returned(run(gate, flow.start("Whats")))
        assertTrue(next is OwnerAppActionFlow.Step.Choose)
        assertNull(flow.confirm(true))
        assertNull(flow.launchedCandidate)
    }

    @Test fun implicitFuzzyNeedStillDoesNotBecomeAnOpening() {
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { lookup(1) })
        val flow = OwnerAppActionFlow(true, requireExactInstalledCandidate = true)
        assertTrue(flow.returned(run(gate, flow.start("Whats"))) is OwnerAppActionFlow.Step.Done)
        assertNull(flow.confirm(true))
        assertNull(flow.launchedCandidate)
    }

    @Test fun wrongPackageAndReplayedObservationNeverBecomeLiveSessionCandidate() {
        for (replayed in listOf(false, true)) {
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                if (it.name == "find_app") lookup(1)
                else launch(if (replayed) app.packageName else "org.example.unrelated")
            })
            val flow = OwnerAppActionFlow(true)
            assertTrue(flow.returned(run(gate, flow.start("WhatsApp"))) is OwnerAppActionFlow.Step.Confirm)
            val result = run(gate, checkNotNull(flow.confirm(true))).copy(replayed = replayed)
            val done = flow.returned(result) as OwnerAppActionFlow.Step.Done
            assertNull(flow.launchedCandidate)
            assertFalse(done.message.contains("wurde in einem neuen aktiven Fenster beobachtet"))
        }
    }
}
