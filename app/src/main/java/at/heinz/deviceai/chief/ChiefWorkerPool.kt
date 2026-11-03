package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolBackendFailure
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.AgentModelOutput
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

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
        @Volatile var work: Future<String>? = null
        @Volatile var job: WorkerJob? = null
    }

    private val runners: ExecutorService = Executors.newFixedThreadPool(maxConcurrent) { r -> Thread(r, "chief-worker").apply { isDaemon = true } }
    private val workers: ExecutorService = Executors.newCachedThreadPool { r -> Thread(r, "chief-worker-job").apply { isDaemon = true } }
    private val slots = ConcurrentHashMap<String, Slot>()

    @Synchronized
    fun submit(brief: WorkerBrief, onReport: (WorkerReport) -> Unit): Boolean {
        if (slots.containsKey(brief.id) || slots.size >= maxConcurrent + maxQueued) return false
        val slot = Slot(brief, onReport)
        slots[brief.id] = slot
        runners.execute { supervise(slot) }
        return true
    }

    fun cancel(id: String): Boolean {
        val slot = slots[id] ?: return false
        slot.cancelled.set(true)
        runCatching { slot.job?.cancel() }
        slot.work?.cancel(true)
        return true
    }

    fun active(): Set<String> = slots.keys.toSet()

    fun awaitIdle(timeoutMillis: Long): Boolean {
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (slots.isNotEmpty()) {
            if (System.nanoTime() > end) return false
            Thread.sleep(10)
        }
        return true
    }

    fun shutdown() {
        slots.keys.forEach { cancel(it) }
        runners.shutdownNow(); workers.shutdownNow()
    }

    private fun supervise(slot: Slot) {
        val started = System.nanoTime()
        fun report(status: WorkerStatus, summary: String) {
            if (!slot.reported.compareAndSet(false, true)) return
            val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            slots.remove(slot.brief.id)
            runCatching { slot.onReport(WorkerReport(slot.brief.id, status, summary.take(4_000), ms)) }
        }
        if (slot.cancelled.get()) return report(WorkerStatus.CANCELLED, "Abgebrochen, bevor der Auftrag startete.")
        try {
            val job = factory(slot.brief)
            slot.job = job
            if (slot.cancelled.get()) {
                runCatching { job.cancel() }
                return report(WorkerStatus.CANCELLED, "Abbruch angefordert, bevor der Worker startete.")
            }
            val future = workers.submit<String> { job.run() }
            slot.work = future
            if (slot.cancelled.get()) {
                runCatching { job.cancel() }
                future.cancel(true)
            }
            val result = future.get(slot.brief.deadlineMillis, TimeUnit.MILLISECONDS)
            report(if (slot.cancelled.get()) WorkerStatus.CANCELLED else WorkerStatus.DONE,
                if (slot.cancelled.get()) "Abbruch angefordert; Werkzeugquittungen auf offene Wirkungen prüfen." else result)
        } catch (_: TimeoutException) {
            slot.cancelled.set(true)
            runCatching { slot.job?.cancel() }
            slot.work?.cancel(true)
            report(WorkerStatus.TIMED_OUT, "Frist überschritten; Werkzeugquittungen auf offene Wirkungen prüfen.")
        } catch (_: CancellationException) {
            runCatching { slot.job?.cancel() }
            report(WorkerStatus.CANCELLED, "Abbruch angefordert; Werkzeugquittungen auf offene Wirkungen prüfen.")
        } catch (_: InterruptedException) {
            slot.cancelled.set(true)
            runCatching { slot.job?.cancel() }
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
    fun runner(brief: WorkerBrief, delegate: ToolRunner): ToolRunner = ToolRunner { call ->
        if (call.name !in brief.allowedTools)
            throw ToolBackendFailure(ToolExecutionStatus.FAILED, "Werkzeug ${call.name} ist für Auftrag ${brief.id} nicht freigegeben.")
        delegate.execute(call)
    }

    fun model(brief: WorkerBrief, delegate: AgentModel): AgentModel = AgentModel { task, memories, reflection, observations ->
        val out = delegate.generate(task, memories, reflection, observations)
        val proposal = out.proposal
        if (proposal == null || proposal.toolName in brief.allowedTools) out
        else throw WorkerBlocked("Werkzeug ${proposal.toolName} ist für Auftrag ${brief.id} nicht freigegeben.")
    }
}
