package at.heinz.deviceai.cockpit

import at.heinz.deviceai.chief.TurnMilestone
import at.heinz.deviceai.chief.WorkerReport

/** RED stub: real mapping follows in the next commit. */
class CockpitAnimationTriggers(private val bus: CockpitAnimationBus, private val clock: () -> Long = System::currentTimeMillis) {
    fun commandAcknowledged(turnId: String, command: String) {}
    fun milestone(turnId: String, kind: TurnMilestone, detail: String = "") {}
    fun toolStarted(turnId: String, callId: String, toolName: String) {}
    fun toolFinished(turnId: String, callId: String, toolName: String, succeeded: Boolean) {}
    fun workerStarted(workerId: String, goal: String) {}
    fun workerReported(report: WorkerReport) {}
    fun turnEnded(turnId: String, delivered: Boolean) {}
    fun accountToolUsed(turnId: String, provider: String, accountLabel: String) {}
    fun documentReceived(turnId: String, documentName: String, mimeType: String, question: String) {}
    fun timeline(turnId: String): List<ProcessStep> = emptyList()
}
