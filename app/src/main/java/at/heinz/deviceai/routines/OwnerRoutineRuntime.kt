package at.heinz.deviceai.routines

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import at.heinz.deviceai.DeviceToolExecutor
import at.heinz.deviceai.DeviceToolExecutorRunner
import at.heinz.deviceai.FileToolExecutionReceiptStore
import at.heinz.deviceai.ShizukuBridge
import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.chief.ChiefJobDeps
import at.heinz.deviceai.chief.ChiefJobs
import at.heinz.deviceai.chief.ChiefWorkerPool
import at.heinz.deviceai.chief.RoutineDispatcher
import at.heinz.deviceai.chief.UnattendedApproval
import at.heinz.deviceai.chief.WorkerBlocked
import at.heinz.deviceai.chief.WorkerBrief
import at.heinz.deviceai.chief.WorkerJob
import at.heinz.deviceai.cognitive.AgentLimits
import at.heinz.deviceai.cognitive.ConstrainedResourceFabric
import at.heinz.deviceai.cognitive.ContinuumAutonomyControl
import at.heinz.deviceai.cognitive.ContinuumAutonomyLevel
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.cognitive.LocalChatTranscriptStore
import rikka.shizuku.Shizuku
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class OwnerRoutineRuntime private constructor(private val app: Context) {
    private val files = app.filesDir.canonicalFile
    val book = RoutineBook(stateStore = FileRoutineStateStore(File(files, "routines")))
    private val handler = Handler(Looper.getMainLooper())
    private val chief = AtomicReference<(() -> Unit)?>()
    val mailbox = AgentMailbox(wake = { handler.post { chief.get()?.invoke() } })
    private val receipts = FileToolExecutionReceiptStore(File(files, "tool-receipts"))
    private val bridge = ShizukuBridge(app) { }
    @Volatile private var bridgeNeeded = book.registrations().any {
        OwnerRoutineTarget.from(it.routine)?.requiresShizuku == true
    }
    private val control = Executors.newSingleThreadExecutor { r ->
        Thread(r, "continuum-owner-routines").apply { isDaemon = true }
    }
    private class Permit(val routineId: String, val generation: AtomicBoolean) { val cancelled = AtomicBoolean() }
    private val permits = ConcurrentHashMap<String, Permit>()
    private val stopping = ConcurrentHashMap.newKeySet<String>()
    private val stoppingAll = AtomicBoolean()
    private val admitting = ThreadLocal<AtomicBoolean>()
    private val dispatchLock = Any()
    @Volatile var lastProblem: String? = null
        private set
    private val pool = ChiefWorkerPool(1, 4, ::job)
    private val dispatcher = RoutineDispatcher(book, pool, mailbox,
        resultDigest = { routine, result -> requireNotNull(OwnerRoutineTarget.from(routine)).digest(result) },
        reportWhen = { routine, result -> requireNotNull(OwnerRoutineTarget.from(routine)).reports(result) },
        onAdmission = { brief ->
            val generation = admitting.get() ?: throw WorkerBlocked("Routine generation missing.")
            if (generation.get()) throw WorkerBlocked("Routine generation stopped.")
            val routine = book.authorizedRun(brief.id.removePrefix("rt-"))
                ?: throw WorkerBlocked("Routine authorization missing.")
            permits[brief.id] = Permit(routine.routine.id, generation)
        },
        onRetired = { permits.remove(it) },
        onStorageFailure = { recordFailure(it) })

    data class Registration(val routine: Routine, val schedule: OwnerRoutineSchedule)

    fun confirm(draft: OwnerRoutineDraft): Registration {
        check(!stoppingAll.get()) { "Routinen werden noch gestoppt." }
        val routine = draft.routine("r-" + UUID.randomUUID(), System.currentTimeMillis())
        check(book.addApproved(routine, RoutineOwnerApproval.confirmed(routine, System.currentTimeMillis()))) {
            "Routine konnte nicht gespeichert werden."
        }
        val schedule = restoreSchedule()
        if (draft.periodMillis != null && schedule == OwnerRoutineSchedule.SCHEDULED)
            control.execute { runSafely(AtomicBoolean()) }
        return Registration(routine, schedule)
    }

    fun stop(id: String): Boolean {
        signalStop(id)
        val removed = synchronized(book) {
            val run = book.registrations().singleOrNull { it.routine.id == id }?.runId?.let { "rt-$it" }
            run?.let { permits[it]?.cancelled?.set(true); pool.cancel(it) }
            book.remove(id)
        }
        stopping.remove(id)
        restoreSchedule()
        return removed
    }

    fun stopAll(): List<String> {
        signalStop(null)
        val removed = synchronized(book) {
            permits.forEach { (id, permit) -> permit.cancelled.set(true); pool.cancel(id) }
            book.removeAll()
        }
        stopping.clear()
        stoppingAll.set(false)
        restoreSchedule()
        return removed
    }

    fun signalStop(id: String?) {
        if (id == null) stoppingAll.set(true) else stopping.add(id)
        permits.forEach { (worker, permit) -> if (id == null || permit.routineId == id) {
            permit.cancelled.set(true)
            pool.cancel(worker)
        } }
    }

    fun pause() {
        permits.forEach { (id, permit) -> permit.cancelled.set(true); pool.cancel(id) }
        OwnerRoutineScheduler(app).cancel()
        handler.post { if (paused()) bridge.stop() }
    }

    fun restoreSchedule(): OwnerRoutineSchedule {
        val rows = book.registrations()
        bridgeNeeded = rows.any { OwnerRoutineTarget.from(it.routine)?.requiresShizuku == true }
        if (!bridgeNeeded) handler.post { if (!bridgeNeeded) bridge.stop() }
        val scheduler = OwnerRoutineScheduler(app)
        if (paused()) { scheduler.cancel(); return OwnerRoutineSchedule.PAUSED }
        if (rows.none { it.routine.trigger is RoutineTrigger.Every }) {
            scheduler.cancel()
            return OwnerRoutineSchedule.EVENT_ONLY
        }
        return if (scheduler.restore()) OwnerRoutineSchedule.SCHEDULED else OwnerRoutineSchedule.FAILED
    }

    fun ownerAppEntered(first: Boolean) {
        control.execute {
            runCatching { restoreSchedule() }.onFailure(::recordFailure)
            runCatching { dispatcher.retryReports() }.onFailure(::recordFailure)
            if (first) runSafely(AtomicBoolean(), OwnerRoutineDraft.APP_START)
        }
    }

    fun attachChief(listener: () -> Unit) { chief.set(listener) }
    fun detachChief(listener: () -> Unit) { chief.compareAndSet(listener, null) }

    fun delivery(transcript: LocalChatTranscriptStore) = RoutineChatDelivery(book, mailbox, transcript)

    fun cancelGeneration(generation: AtomicBoolean) {
        generation.set(true)
        permits.forEach { (id, permit) -> if (permit.generation === generation) {
            permit.cancelled.set(true)
            pool.cancel(id)
        } }
    }

    fun runCycle(generation: AtomicBoolean, event: String? = null) {
        try {
            if (generation.get() || paused()) return
            synchronized(dispatchLock) {
                admitting.set(generation)
                try {
                    val allowStart = { !generation.get() && !paused() }
                    dispatcher.tick(allowStart)
                    if (event != null) dispatcher.onEvent(event, allowStart)
                } finally { admitting.remove() }
            }
            if (!pool.awaitIdle(120_000)) {
                cancelGeneration(generation)
                lastProblem = "Routinezeitbudget erreicht; Werkzeugquittungen prüfen."
            }
        } catch (failure: InterruptedException) {
            cancelGeneration(generation)
            throw failure
        } catch (failure: Exception) {
            cancelGeneration(generation)
            recordFailure(failure)
            throw failure
        }
    }

    private fun runSafely(generation: AtomicBoolean, event: String? = null) {
        try { runCycle(generation, event) }
        catch (_: InterruptedException) { cancelGeneration(generation); Thread.currentThread().interrupt() }
        catch (failure: Exception) { recordFailure(failure) }
    }

    private fun paused() = ContinuumAutonomyControl.level(app) == ContinuumAutonomyLevel.PAUSED

    private fun checkActive(permit: Permit) {
        if (permit.cancelled.get() || permit.generation.get() || Thread.currentThread().isInterrupted)
            throw WorkerBlocked("Routine wurde gestoppt.")
        if (stoppingAll.get() || permit.routineId in stopping) throw WorkerBlocked("Routine wird gestoppt.")
        if (paused()) throw WorkerBlocked("NOT-STOPP ist aktiv.")
    }

    private fun admit(brief: WorkerBrief, permit: Permit): OwnerRoutineTarget = synchronized(book) {
        checkActive(permit)
        val stored = book.authorizedRun(brief.id.removePrefix("rt-"))
            ?: throw WorkerBlocked("Konkrete Routinefreigabe nicht mehr gültig.")
        if (stored.routine.goal != brief.goal || stored.routine.allowedTools != brief.allowedTools)
            throw WorkerBlocked("Routineziel oder Werkzeugumfang verändert.")
        val target = OwnerRoutineTarget.from(stored.routine)
            ?: throw WorkerBlocked("Gespeichertes Routineziel oder Auslöser nicht unterstützt.")
        val manager = app.getSystemService(ActivityManager::class.java)
            ?: throw WorkerBlocked("Android-Speicherbeobachtung nicht verfügbar.")
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        if (memory.lowMemory) throw WorkerBlocked("Android meldet zu wenig Arbeitsspeicher.")
        val thermal = if (Build.VERSION.SDK_INT >= 29)
            app.getSystemService(PowerManager::class.java)?.currentThermalStatus else null
        if (thermal != null && ConstrainedResourceFabric.plan(
                ConstrainedResourceFabric.Snapshot(memory.availMem, 0, 0, thermal)) ==
            ConstrainedResourceFabric.Plan.REFUSE_THERMAL) throw WorkerBlocked("Android-Thermalgrenze erreicht.")
        checkActive(permit)
        target
    }

    private fun job(brief: WorkerBrief): WorkerJob {
        val permit = permits[brief.id] ?: throw WorkerBlocked("Routinegeneration fehlt.")
        val target = admit(brief, permit)
        val plan = target.plan()
        val executor = DeviceToolExecutor(app, bridge::isReady, { command, callback ->
            bridge.executeCheckedGuarded(command, { operation -> synchronized(book) {
                admit(brief, permit)
                val result = operation()
                checkActive(permit)
                result
            } }, callback)
        }, null)
        val runner = DeviceToolExecutorRunner({ request, result ->
            synchronized(book) {
                if (admit(brief, permit) != target || !allows(target, request))
                    throw WorkerBlocked("Werkzeugaufruf außerhalb der bestätigten Routine.")
                executor.execute(request, result)
            }
        }, 18_000, { executor.cancelNativeRequest(it.callId) })
        val delegated = ChiefJobs.agentJob(brief, ChiefJobDeps(plan, runner,
            ToolPolicy(readOnlyEnabled = true, maxCalls = plan.stepCount, timeoutMillis = 18_000),
            null, plan, InMemoryExperienceStore(), UnattendedApproval,
            limits = AgentLimits(maxSteps = 24, maxToolCalls = plan.stepCount, maxRetries = 0), receiptStore = receipts))
        return object : WorkerJob {
            override fun run(): String {
                if (target.requiresShizuku) awaitBridge(brief, permit)
                admit(brief, permit)
                return delegated.run()
            }
            override fun cancel() { permit.cancelled.set(true); delegated.cancel() }
        }
    }

    private fun allows(target: OwnerRoutineTarget, request: ToolCallRequest): Boolean =
        request.name in target.allowedTools && request.arguments ==
            if (request.name == "list_files") mapOf("path" to "/sdcard/Download") else emptyMap<String, Any?>()

    private fun awaitBridge(brief: WorkerBrief, permit: Permit) {
        fun granted() = runCatching { Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        if (!granted()) throw WorkerBlocked("Downloads benötigen eine bereits erteilte Shizuku-Freigabe.")
        handler.post {
            if (permits[brief.id] === permit && !permit.cancelled.get() && !permit.generation.get())
                bridge.startGrantedOnly()
        }
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!bridge.isReady()) {
            admit(brief, permit)
            if (!granted() || SystemClock.elapsedRealtime() >= deadline)
                throw WorkerBlocked("Shizuku-Verbindung nicht verfügbar; Downloads nicht geprüft.")
            Thread.sleep(100)
        }
    }

    private fun recordFailure(failure: Throwable) {
        lastProblem = "Routine konnte nicht abgeschlossen werden (${failure.javaClass.simpleName})."
    }

    companion object {
        @Volatile private var shared: OwnerRoutineRuntime? = null
        private val ownerEntered = AtomicBoolean()
        fun markOwnerAppEntry(): Boolean = ownerEntered.compareAndSet(false, true)
        fun current(): OwnerRoutineRuntime? = shared
        fun get(context: Context): OwnerRoutineRuntime = synchronized(this) {
            shared ?: OwnerRoutineRuntime(context.applicationContext).also { shared = it }
        }
    }
}
