package at.heinz.deviceai.chief

import at.heinz.deviceai.routines.AgentMailbox
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in host integration only. Process commands live in this trusted test;
 * production receives no host transport and synthetic fixtures grant no real task. */
class ChiefWorkspaceWiringHostTest {
    private val node = "/opt/codex/runtimes/codex-primary-runtime/dependencies/node/bin/node"
    private val script = File(System.getProperty("continuum.workspace.repo", System.getProperty("user.dir")),
        ".github/continuum/ccc1-workspace-chief-host.mjs").absolutePath

    private fun command(verb: String, home: File, index: Int? = null): Process =
        ProcessBuilder(listOf(node, script, verb, home.absolutePath) + (index?.let { listOf(it.toString()) } ?: emptyList()))
            .redirectErrorStream(true).start()

    private fun output(process: Process): String {
        val text = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor(20, TimeUnit.SECONDS)) { "HOST_PROCESS_TIMEOUT" }
        check(text.length <= 100_000 && process.exitValue() == 0) { "HOST_PROCESS_FAILED: ${text.takeLast(1_000)}" }
        return text
    }

    private fun scope(json: JSONObject) = WorkspaceTaskScope(json.getString("workerId"), json.getString("taskId"),
        json.getString("sourceCommit"), json.getString("requestMessageId"), json.getString("requestHash"),
        json.getString("jobId"), json.getString("profile"), json.getInt("memoryMb"), json.getInt("cpus"),
        json.getInt("pids"), json.getLong("timeoutMillis"))

    private inner class FixedHostPort(private val home: File, private val scopes: List<WorkspaceTaskScope>) : ChiefWorkspacePort {
        private val active = ConcurrentHashMap<String, Process>()
        private val cancelled = ConcurrentHashMap.newKeySet<String>()
        private val startLock = Any()
        override fun execute(scope: WorkspaceTaskScope): WorkspaceObservation {
            val index = scopes.indexOf(scope)
            require(index in 0..1)
            val process = synchronized(startLock) {
                if (scope.workerId in cancelled) throw CancellationException()
                command("run", home, index).also { check(active.putIfAbsent(scope.workerId, it) == null) }
            }
            try {
                val text = output(process)
                if (scope.workerId in cancelled) throw CancellationException()
                val json = JSONObject(text)
                return WorkspaceObservation(scope(json.getJSONObject("scope")), json.getString("workspaceId"),
                    json.getString("evidenceSha256"), json.getBoolean("teardownConfirmed"), json.getInt("profileExitCode"))
            } finally { active.remove(scope.workerId, process) }
        }
        override fun cancel(scope: WorkspaceTaskScope) {
            val process = synchronized(startLock) {
                cancelled += scope.workerId
                active[scope.workerId]
            } ?: return
            val index = scopes.indexOf(scope)
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(18)
            // Handles cancellation before Docker creation too: keep the Node
            // process alive long enough for its own executor's finally to reap.
            while (process.isAlive && System.nanoTime() < end) {
                output(command("cancel", home, index))
                process.waitFor(100, TimeUnit.MILLISECONDS)
            }
            check(!process.isAlive) { "HOST_CANCEL_RUNNER_UNCONFIRMED" }
            check(JSONObject(output(command("active", home, index))).getInt("active") == 0) { "HOST_CANCEL_CONTAINER_UNCONFIRMED" }
        }
    }

    private fun withHost(label: String, body: (File, List<WorkspaceTaskScope>, FixedHostPort) -> Unit) {
        assumeTrue(System.getenv("CONTINUUM_WORKSPACE_CHIEF_HOST_PROOF") == "1")
        val evidence = System.getenv("CONTINUUM_WORKSPACE_CHIEF_EVIDENCE_DIR")
        val home = if (evidence == null) Files.createTempDirectory("chief-workspace-$label-").toFile()
            else File(evidence, label).also { check(it.mkdirs()) { "FRESH_HOST_EVIDENCE_REQUIRED" } }
        try {
            val json = JSONArray(output(command("prepare", home)))
            val scopes = (0 until json.length()).map { scope(json.getJSONObject(it)) }
            body(home, scopes, FixedHostPort(home, scopes))
        } finally { if (evidence == null) home.deleteRecursively() }
    }

    @Test fun actualPoolMailboxAndTwoFixedNodeContainersReturnOnlyObservations() = withHost("pool-mailbox") { home, scopes, port ->
        val mailbox = AgentMailbox()
        val delivered = CountDownLatch(2)
        val events = ConcurrentLinkedQueue<WorkspaceDelivery>()
        val pool = ChiefWorkerPool(2, 0) { brief -> ChiefWorkspaceWiring.job(brief, scopes.single { ChiefWorkspaceWiring.briefId(it) == brief.id }, port) }
        try {
            for (scope in scopes) assertTrue(pool.submit(WorkerBrief(ChiefWorkspaceWiring.briefId(scope), "synthetic fixed host profile", emptySet(), 20_000),
                ChiefWorkspaceWiring.mailboxReporter(mailbox, scope, onDelivery = { events += it; delivered.countDown() })))
            assertTrue(pool.awaitIdle(30_000)); assertTrue(delivered.await(3, TimeUnit.SECONDS))
            assertEquals(2, events.size); assertTrue(events.all { it.status == WorkspaceDeliveryStatus.DELIVERED })
            val messages = mailbox.drain("chief")
            assertEquals(2, messages.size)
            for (scope in scopes) {
                val message = messages.single { it.body.contains("task=" + scope.taskId) }
                assertTrue(message.body, message.body.contains("workerStatus=DONE"))
                assertTrue(message.body.contains("verification=PENDING"))
                val receipt = JSONObject(File(home, "receipt-${scopes.indexOf(scope)}.json").readText())
                assertEquals(0, receipt.getInt("status"))
                assertTrue(receipt.getJSONObject("isolation").getBoolean("teardownConfirmed"))
            }
            File(home, "chief-mailbox-observations.json").writeText(JSONArray(messages.map { JSONObject()
                .put("id", it.id).put("from", it.from).put("to", it.to).put("body", it.body) }).toString(2))
        } finally { pool.shutdown() }
    }

    @Test fun cancellationOfRealContainerClosesRunnerAndReportsToChiefMailbox() = withHost("cancel") { home, scopes, port ->
        val scope = scopes.first()
        val mailbox = AgentMailbox()
        val delivered = CountDownLatch(1)
        val pool = ChiefWorkerPool(1, 0) { ChiefWorkspaceWiring.job(it, scope, port) }
        try {
            assertTrue(pool.submit(WorkerBrief(ChiefWorkspaceWiring.briefId(scope), "synthetic cancellation probe", emptySet(), 20_000),
                ChiefWorkspaceWiring.mailboxReporter(mailbox, scope, onDelivery = { delivered.countDown() })))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var observed = false
            var observedBefore = JSONObject()
            while (!observed && System.nanoTime() < deadline) {
                observedBefore = JSONObject(output(command("active", home, 0)))
                observed = observedBefore.getInt("active") == 1
                if (!observed) Thread.sleep(50)
            }
            assertTrue("real running container observed before cancellation", observed)
            assertTrue(pool.cancel(ChiefWorkspaceWiring.briefId(scope))); assertTrue(pool.awaitIdle(10_000))
            assertTrue(delivered.await(2, TimeUnit.SECONDS))
            val message = mailbox.drain("chief").single()
            assertTrue(message.body, message.body.contains("workerStatus=CANCELLED"))
            val observedAfter = JSONObject(output(command("active", home, 0)))
            assertEquals(0, observedAfter.getInt("active"))
            assertFalse(File(home, "receipt-0.json").exists())
            File(home, "chief-cancel-observation.json").writeText(JSONObject().put("body", message.body)
                .put("observedBefore", observedBefore).put("observedAfter", observedAfter)
                .put("runnerClosed", true).put("containerClosed", true).put("successReceiptCreated", false).toString(2))
        } finally { pool.shutdown() }
    }
}
