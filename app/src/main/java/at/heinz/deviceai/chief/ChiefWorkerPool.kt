package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolBackendFailure
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.AgentModelOutput
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** A bounded background assignment: own goal, own tool allowlist, own deadline. */
data class WorkerBrief(
    val id: String,
    val goal: String,
    val allowedTools: Set<String>,
    val deadlineMillis: Long = 120_000
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        require(goal.isNotBlank() && goal.length <= 2_000)
        require(deadlineMillis in 1..600_000)
    }
}

private fun immutableToolScope(tools: Set<String>): Set<String> =
    Collections.unmodifiableSet(LinkedHashSet(tools))

enum class WorkerStatus { DONE, BLOCKED, FAILED, TIMED_OUT, CANCELLED }
data class WorkerReport(val id: String, val status: WorkerStatus, val summary: String, val durationMillis: Long)

/** The work itself; typically wraps BoundedAgentOrchestrator.run(brief.goal). Throw ToolBackendFailure/any error to fail. */
fun interface WorkerJob {
    fun run(): String

    /** Cooperative stop for agent generation and ToolGate; interrupting a Future alone is insufficient. */
    fun cancel() {}
}

/**
 * Chief pattern point 2: the chief (AgentActivity's conversation) stays responsive and hands
 * heavy work to bounded workers. submit() never blocks; capacity is fixed; each brief gets
 * exactly one [WorkerReport] (DONE/FAILED/TIMED_OUT/CANCELLED), never a silent drop.
 */
class ChiefWorkerPool(
    private val maxConcurrent: Int = 2,
    private val maxQueued: Int = 4,
    private val factory: (WorkerBrief) -> WorkerJob
) {
    init { require(maxConcurrent in 1..4 && maxQueued in 0..16) }

    private class Slot(val brief: WorkerBrief, val onReport: (WorkerReport) -> Unit) {
        val reported = AtomicBoolean()
        val cancelled = AtomicBoolean()
        // Either the executor supervises this slot or shutdown accounts for a discarded runner.
        val supervisorClaimed = AtomicBoolean()
        @Volatile var startedNanos: Long = 0
        @Volatile var work: Future<String>? = null
        @Volatile var job: WorkerJob? = null
        private val cancelForwarded = AtomicBoolean(false)

        fun forwardCancellation() {
            val current = job ?: return
            if (cancelForwarded.compareAndSet(false, true)) runCatching { current.cancel() }
        }
    }

    private val runners: ExecutorService = Executors.newFixedThreadPool(maxConcurrent) { r -> Thread(r, "chief-worker").apply { isDaemon = true } }
    // Neither threads nor queued closures may grow when a native call ignores stop.
    private val workers = ThreadPoolExecutor(maxConcurrent, maxConcurrent, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(maxConcurrent + maxQueued),
        { r -> Thread(r, "chief-worker-job").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())
    private val slots = ConcurrentHashMap<String, Slot>()
    // Capacity is released before reporting so callbacks may submit again; completion includes delivery.
    private val unfinishedAssignments = AtomicInteger()
    // Guarded by this pool's monitor, shared with admission. Never reopened.
    private var closed = false

    @Synchronized
    fun submit(brief: WorkerBrief, onReport: (WorkerReport) -> Unit): Boolean {
        if (closed) throw RejectedExecutionException("Chief worker pool is closed")
        if (slots.containsKey(brief.id) || slots.size >= maxConcurrent + maxQueued) return false
        // A queued assignment retains exactly the scope accepted at admission.
        val admitted = brief.copy(allowedTools = immutableToolScope(brief.allowedTools))
        val slot = Slot(admitted, onReport)
        unfinishedAssignments.incrementAndGet()
        slots[brief.id] = slot
        try {
            runners.execute { supervise(slot) }
        } catch (e: RejectedExecutionException) {
            slots.remove(brief.id, slot)
            unfinishedAssignments.decrementAndGet()
            throw e
        }
        return true
    }

    fun cancel(id: String): Boolean {
        val slot = slots[id] ?: return false
        return cancel(slot)
    }

    private fun cancel(slot: Slot): Boolean {
        synchronized(slot) {
            if (slot.reported.get()) return false
            slot.cancelled.set(true)
        }
        slot.forwardCancellation()
        cancelWork(slot)
        return true
    }

    private fun cancelWork(slot: Slot) {
        val future = slot.work ?: return
        future.cancel(true)
        // FutureTask cancellation alone leaves it in ThreadPoolExecutor's work queue.
        // The task has already been submitted before slot.work can refer to it.
        if (future is Runnable) workers.remove(future)
    }

    fun active(): Set<String> = slots.keys.toSet()

    /** Read-only view of one assignment for the cockpit; no progress is invented (there is none). */
    data class WorkerSnapshot(val id: String, val goal: String, val running: Boolean, val cancelling: Boolean,
                              val elapsedMillis: Long, val deadlineMillis: Long)

    /** Current assignments (queued and running), oldest first by start; never blocks the pool. */
    fun snapshot(nowNanos: Long = System.nanoTime()): List<WorkerSnapshot> = slots.values
        .filterNot { it.reported.get() }
        .map { slot ->
            val started = slot.startedNanos
            WorkerSnapshot(slot.brief.id, slot.brief.goal, running = started != 0L, cancelling = slot.cancelled.get(),
                elapsedMillis = if (started == 0L) 0L else TimeUnit.NANOSECONDS.toMillis((nowNanos - started).coerceAtLeast(0L)),
                deadlineMillis = slot.brief.deadlineMillis)
        }
        .sortedWith(compareByDescending<WorkerSnapshot> { it.running }.thenByDescending { it.elapsedMillis })

    /** Fixed admission capacity (running + queued). */
    val capacity: Int get() = maxConcurrent + maxQueued

    /** Idle includes report delivery, even when callbacks submit another job. It does not certify
     * termination of a native call that ignores cancellation; inspect its execution receipt. */
    fun awaitIdle(timeoutMillis: Long): Boolean {
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (unfinishedAssignments.get() != 0) {
            if (System.nanoTime() > end) return false
            Thread.sleep(10)
        }
        return true
    }

    fun shutdown() {
        val pending = synchronized(this) {
            if (closed) return
            closed = true
            slots.values.toList()
        }
        // Close admission before callbacks can submit again; do not invoke client code under
        // the admission monitor. Cancellation remains cooperative for already running work.
        pending.forEach { cancel(it) }
        runners.shutdownNow(); workers.shutdownNow()
        pending.forEach { slot ->
            if (slot.supervisorClaimed.compareAndSet(false, true)) {
                report(slot, WorkerStatus.CANCELLED, "Abgebrochen, bevor der Auftrag startete.")
            }
        }
    }

    private fun report(slot: Slot, status: WorkerStatus, summary: String) {
        val terminal = synchronized(slot) {
            if (!slot.reported.compareAndSet(false, true)) return
            if (slot.cancelled.get() && status != WorkerStatus.TIMED_OUT) WorkerStatus.CANCELLED else status
        }
        val ms = if (slot.startedNanos == 0L) 0L
            else TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - slot.startedNanos)
        val message = if (terminal != status) "Abbruch angefordert; Werkzeugquittungen auf offene Wirkungen prüfen." else summary
        slots.remove(slot.brief.id, slot)
        try {
            runCatching { slot.onReport(WorkerReport(slot.brief.id, terminal, at.heinz.deviceai.ToolOutputCap.cap(message), ms)) }
        } finally {
            unfinishedAssignments.decrementAndGet()
        }
    }

    private fun supervise(slot: Slot) {
        if (!slot.supervisorClaimed.compareAndSet(false, true)) return
        slot.startedNanos = System.nanoTime()
        fun report(status: WorkerStatus, summary: String) = report(slot, status, summary)
        if (slot.cancelled.get()) return report(WorkerStatus.CANCELLED, "Abgebrochen, bevor der Auftrag startete.")
        try {
            val job = factory(slot.brief)
            slot.job = job
            if (slot.cancelled.get()) {
                slot.forwardCancellation()
                return report(WorkerStatus.CANCELLED, "Abbruch angefordert, bevor der Worker startete.")
            }
            val future = workers.submit<String> { job.run() }
            slot.work = future
            if (slot.cancelled.get()) {
                slot.forwardCancellation()
                cancelWork(slot)
            }
            val result = future.get(slot.brief.deadlineMillis, TimeUnit.MILLISECONDS)
            report(if (slot.cancelled.get()) WorkerStatus.CANCELLED else WorkerStatus.DONE,
                if (slot.cancelled.get()) "Abbruch angefordert; Werkzeugquittungen auf offene Wirkungen prüfen." else result)
        } catch (_: TimeoutException) {
            slot.cancelled.set(true)
            slot.forwardCancellation()
            cancelWork(slot)
            report(WorkerStatus.TIMED_OUT, "Frist überschritten; Werkzeugquittungen auf offene Wirkungen prüfen.")
        } catch (_: CancellationException) {
            slot.forwardCancellation()
            report(WorkerStatus.CANCELLED, "Abbruch angefordert; Werkzeugquittungen auf offene Wirkungen prüfen.")
        } catch (_: InterruptedException) {
            slot.cancelled.set(true)
            slot.forwardCancellation()
            cancelWork(slot)
            report(WorkerStatus.CANCELLED, "Abbruch angefordert; Werkzeugquittungen auf offene Wirkungen prüfen.")
        } catch (e: Exception) {
            val cause = (e as? java.util.concurrent.ExecutionException)?.cause ?: e
            if (cause is WorkerBlocked && !slot.cancelled.get()) report(WorkerStatus.BLOCKED, cause.message ?: "Blockiert.")
            else if (slot.cancelled.get()) report(WorkerStatus.CANCELLED, "Vom Chef abgebrochen.")
            else report(WorkerStatus.FAILED, "Fehlgeschlagen: ${cause.javaClass.simpleName}")
        }
    }
}

/** Tool scoping for a worker brief, enforced twice: before the approval prompt (model) and at execution (runner). */
object WorkerScope {
    fun runner(brief: WorkerBrief, delegate: ToolRunner): ToolRunner {
        val allowedTools = immutableToolScope(brief.allowedTools)
        return ToolRunner { call ->
            if (call.name !in allowedTools)
                throw ToolBackendFailure(ToolExecutionStatus.FAILED, "Werkzeug ${call.name} ist für Auftrag ${brief.id} nicht freigegeben.")
            at.heinz.deviceai.ToolOutputCap.cap(delegate.execute(call))
        }
    }

    fun model(brief: WorkerBrief, delegate: AgentModel): AgentModel {
        val allowedTools = immutableToolScope(brief.allowedTools)
        return AgentModel { task, memories, reflection, observations ->
            val out = delegate.generate(task, memories, reflection, observations)
            val proposal = out.proposal
            if (proposal == null || proposal.toolName in allowedTools) out
            else throw WorkerBlocked("Werkzeug ${proposal.toolName} ist für Auftrag ${brief.id} nicht freigegeben.")
        }
    }
}
