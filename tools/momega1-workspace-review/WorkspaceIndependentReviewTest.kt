package at.heinz.deviceai.chief

import at.heinz.deviceai.routines.AgentMailbox
import org.junit.Assert.*
import org.junit.Test

class WorkspaceIndependentReviewTest {
    private fun scope() = WorkspaceTaskScope("worker-review", "t-" + "1".repeat(16), "2".repeat(40),
        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "3".repeat(64), "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        "node-continuum", 256, 1, 64, 1000)
    @Test fun repeatedTerminalReportMustNotBypassExistingMailboxDeduplication() {
        val scope = scope(); val mailbox = AgentMailbox()
        val statuses = mutableListOf<WorkspaceDeliveryStatus>()
        val sink = ChiefWorkspaceWiring.mailboxReporter(mailbox, scope, onDelivery = { statuses += it.status })
        val report = WorkerReport(scope.workerId, WorkerStatus.DONE, "HOST_PROFILE_REPORTED verification=PENDING", 1)
        sink(report); sink(report)
        println("independent duplicate deliveries=$statuses queued=${mailbox.pending("chief")}")
        assertEquals("One execution must not create two fresh terminal messages", 1, mailbox.pending("chief"))
    }
    @Test fun oldSameWorkerTaskMustNotBeRelabelledAsCurrentTask() {
        val scope = scope(); val mailbox = AgentMailbox()
        val sink = ChiefWorkspaceWiring.mailboxReporter(mailbox, scope, onDelivery = {})
        sink(WorkerReport(scope.workerId, WorkerStatus.DONE,
            "HOST_PROFILE_REPORTED task=t-9999999999999999 request=cccccccc-cccc-4ccc-8ccc-cccccccccccc verification=PENDING", 1))
        val messages = mailbox.drain("chief")
        println("independent stale messages=${messages.map { it.body }}")
        assertTrue("Old task report must not receive current task/source/request prefix", messages.isEmpty())
    }
}
