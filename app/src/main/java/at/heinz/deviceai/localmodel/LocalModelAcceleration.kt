package at.heinz.deviceai.localmodel

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owner switch "Beschleuniger: CPU (Standard) | GPU (experimentell)" for the LiteRT-LM engine
 * (docs/research/LOCAL-MODEL-SPEED-20261118.md). LiteRT-LM 0.18.0 ships `Backend.GPU()`; the native GPU start has
 * NOT been run on any device by this project and every owner text says so.
 *
 * Pure decision logic, no Android dependency:
 *  - CPU stays the default. GPU is tried only after the owner switched it on and only when the imported model file
 *    itself declares a GPU text backend.
 *  - A GPU start that throws falls back to CPU within the same load and switches the preference back to CPU, so a
 *    failing start is not repeated at every launch.
 *  - Crash-loop guard: a durable marker is written before the native GPU start and removed once it returned
 *    (success or exception). A marker still set at the next load means the process died inside the GPU start:
 *    GPU is switched off and the owner is told. The first GPU answer of a model file is guarded the same way.
 * Load admission, the thermal guard and the CPU thread budget are untouched; the thread budget simply does not apply
 * to a GPU engine.
 */
object LocalModelAcceleration {
    const val PREFERENCES = "continuum.accelerator"
    const val KEY_REQUESTED = "requested_backend"
    const val KEY_GPU_LOAD_PENDING = "gpu_load_pending"
    const val KEY_GPU_GENERATION_PENDING = "gpu_generation_pending"
    const val KEY_GPU_PROVEN_MODEL = "gpu_proven_model_sha256"
    const val KEY_NOTICE = "last_notice"

    const val EXPERIMENTAL = "experimentell – am Gerät ungeprüft"

    /** Why the engine did not (or no longer) start on the GPU the owner asked for. */
    enum class Notice(val ownerMessage: String) {
        GPU_START_FAILED("GPU-Beschleuniger ($EXPERIMENTAL): Der GPU-Start ist fehlgeschlagen. " +
            "Das Modell läuft auf der CPU und der Schalter steht wieder auf CPU."),
        GPU_LOAD_INTERRUPTED("GPU-Beschleuniger ($EXPERIMENTAL): Beim letzten GPU-Start wurde die App beendet, " +
            "bevor das Modell geladen war. Damit das nicht bei jedem Start wieder passiert, steht der Schalter " +
            "wieder auf CPU."),
        GPU_GENERATION_INTERRUPTED("GPU-Beschleuniger ($EXPERIMENTAL): Während der ersten GPU-Antwort wurde die " +
            "App beendet. Der Schalter steht wieder auf CPU."),
        GPU_NOT_IN_MODEL("GPU-Beschleuniger ($EXPERIMENTAL): Diese Modelldatei meldet keine GPU-Unterstützung. " +
            "Das Modell läuft auf der CPU."),
        GPU_GUARD_NOT_WRITABLE("GPU-Beschleuniger ($EXPERIMENTAL): Der Absturzschutz konnte nicht gespeichert " +
            "werden. Ohne ihn wird die GPU nicht versucht; das Modell läuft auf der CPU.")
    }

    data class Plan(val backend: LocalModelBackend, val notice: Notice?)

    /** Result of one load: the backend that really started and, if it is not the requested one, why. */
    data class Loaded<T>(val value: T, val backend: LocalModelBackend, val notice: Notice?, val gpuFailure: Throwable? = null) {
        /** Owner-facing sentence for this load, or null when the requested backend started without incident. */
        fun ownerNotice(): String? = notice?.let { ownerNotice(it, gpuFailure) }
    }

    fun ownerNotice(notice: Notice, gpuFailure: Throwable? = null): String = notice.ownerMessage +
        (gpuFailure?.let { " Technischer Hinweis: " + (it.message ?: it.javaClass.simpleName).take(160) } ?: "")

    /** Only CPU and GPU can be requested; anything else (including a stored NPU or garbage) means CPU. */
    fun parse(raw: String?): LocalModelBackend =
        if (raw.equals(LocalModelBackend.GPU.name, ignoreCase = true)) LocalModelBackend.GPU else LocalModelBackend.CPU

    fun parseNotice(raw: String?): Notice? = Notice.entries.firstOrNull { it.name == raw }

    fun actionLabel(requested: LocalModelBackend): String =
        if (requested == LocalModelBackend.GPU) "BESCHLEUNIGER · ZURÜCK AUF CPU (STANDARD)"
        else "BESCHLEUNIGER · GPU VERSUCHEN (EXPERIMENTELL)"

    /**
     * Model-centre status. [active] is the backend of the engine that is loaded right now (null: none);
     * [loadedModelBackends] is what the loaded model file declares (null: no model loaded).
     */
    fun statusText(
        requested: LocalModelBackend,
        active: LocalModelBackend?,
        loadedModelBackends: Set<LocalModelBackend>?,
        notice: Notice?
    ): String = buildString {
        append("Beschleuniger: ")
        append(if (requested == LocalModelBackend.GPU) "GPU ($EXPERIMENTAL)" else "CPU (Standard)")
        append(". ")
        append(when (active) {
            null -> if (loadedModelBackends == null) "Kein Modell geladen."
                else "Die geladene Laufzeit meldet kein Backend."
            LocalModelBackend.GPU -> "Das geladene Modell rechnet auf der GPU ($EXPERIMENTAL)."
            else -> "Das geladene Modell rechnet auf der CPU."
        })
        if (active != null && active != requested) append(" Die Einstellung wirkt beim nächsten Laden des Modells.")
        if (loadedModelBackends != null)
            append("\nDie Modelldatei meldet: " + loadedModelBackends.sortedBy { it.name }.joinToString(", ") { it.name } + ".")
        notice?.let { append("\nLetzter Hinweis: ").append(it.ownerMessage) }
        append("\nOb die GPU auf diesem Gerät schneller, kühler oder stabil ist, hat noch niemand gemessen. " +
            "Schlägt der GPU-Start fehl oder wird die App dabei beendet, lädt das Modell wieder auf der CPU.")
    }

    const val CONFIRM_TITLE = "GPU-Beschleuniger ($EXPERIMENTAL)"
    const val CONFIRM_MESSAGE = "Die GPU wird beim nächsten Laden des Modells versucht. Dieser Weg ist auf deinem " +
        "Gerät noch nie gelaufen: Geschwindigkeit, Wärme und Stabilität sind unbekannt, das erste Laden kann deutlich " +
        "länger dauern. Schlägt der Start fehl, lädt das Modell auf der CPU. Wird die App dabei beendet, steht der " +
        "Schalter beim nächsten Start automatisch wieder auf CPU. Hitzeschutz und Speicherprüfung bleiben aktiv."
}

/**
 * Durable owner preference and guard markers. A setter that cannot store its value durably MUST throw: the
 * controller then refuses the GPU instead of starting it without a crash guard.
 */
interface LocalModelAccelerationStore {
    var requested: LocalModelBackend
    /** Set before the native GPU start, cleared when it returned. Must be written synchronously. */
    var gpuLoadPending: Boolean
    /** Set before a GPU generation of a model file that has not finished one yet. Must be written synchronously. */
    var gpuGenerationPending: Boolean
    /** SHA-256 of the model file whose GPU generation completed at least once; null when none. */
    var gpuProvenModel: String?
    var notice: LocalModelAcceleration.Notice?
}

class LocalModelAccelerationController(private val store: LocalModelAccelerationStore) {
    val requested: LocalModelBackend
        @Synchronized get() = runCatching { store.requested }.getOrDefault(LocalModelBackend.CPU)
            .takeIf { it == LocalModelBackend.GPU } ?: LocalModelBackend.CPU

    val notice: LocalModelAcceleration.Notice?
        @Synchronized get() = runCatching { store.notice }.getOrNull()

    /** Explicit owner decision. It starts a new attempt: earlier markers and notices no longer apply. */
    @Synchronized fun request(backend: LocalModelBackend) {
        require(backend == LocalModelBackend.CPU || backend == LocalModelBackend.GPU) { "Only CPU or GPU can be requested." }
        store.requested = backend
        store.gpuLoadPending = false
        store.gpuGenerationPending = false
        store.notice = null
    }

    /**
     * Backend for the next load. Reading the plan applies the crash-loop guard: an interrupted GPU start or first
     * GPU answer switches the preference back to CPU here, before any native call.
     */
    @Synchronized fun plan(modelBackends: Set<LocalModelBackend>): LocalModelAcceleration.Plan {
        if (requested != LocalModelBackend.GPU) return LocalModelAcceleration.Plan(LocalModelBackend.CPU, null)
        val interrupted = runCatching {
            when {
                store.gpuLoadPending -> LocalModelAcceleration.Notice.GPU_LOAD_INTERRUPTED
                store.gpuGenerationPending -> LocalModelAcceleration.Notice.GPU_GENERATION_INTERRUPTED
                else -> null
            }
        }.getOrElse { LocalModelAcceleration.Notice.GPU_GUARD_NOT_WRITABLE }
        if (interrupted != null) {
            // Best effort: even when the store is broken the plan stays CPU for this load.
            runCatching { store.requested = LocalModelBackend.CPU }
            runCatching { store.gpuLoadPending = false }
            runCatching { store.gpuGenerationPending = false }
            runCatching { store.notice = interrupted }
            return LocalModelAcceleration.Plan(LocalModelBackend.CPU, interrupted)
        }
        if (LocalModelBackend.GPU !in modelBackends) {
            runCatching { store.notice = LocalModelAcceleration.Notice.GPU_NOT_IN_MODEL }
            return LocalModelAcceleration.Plan(LocalModelBackend.CPU, LocalModelAcceleration.Notice.GPU_NOT_IN_MODEL)
        }
        return LocalModelAcceleration.Plan(LocalModelBackend.GPU, null)
    }

    /**
     * Runs [attempt] on the planned backend. A GPU attempt that throws is followed by one CPU attempt unless
     * [fallbackAllowed] says the failed attempt still owns native resources (then its failure is rethrown).
     * [attempt] is never called while this controller's monitor is held.
     */
    fun <T> load(
        modelBackends: Set<LocalModelBackend>,
        fallbackAllowed: () -> Boolean = { true },
        attempt: (LocalModelBackend) -> T
    ): LocalModelAcceleration.Loaded<T> {
        val plan = plan(modelBackends)
        if (plan.backend != LocalModelBackend.GPU)
            return LocalModelAcceleration.Loaded(attempt(LocalModelBackend.CPU), LocalModelBackend.CPU, plan.notice)
        val guarded = synchronized(this) { runCatching { store.gpuLoadPending = true }.isSuccess }
        if (!guarded) {
            synchronized(this) { runCatching { store.notice = LocalModelAcceleration.Notice.GPU_GUARD_NOT_WRITABLE } }
            return LocalModelAcceleration.Loaded(attempt(LocalModelBackend.CPU), LocalModelBackend.CPU,
                LocalModelAcceleration.Notice.GPU_GUARD_NOT_WRITABLE)
        }
        val gpu = try {
            Result.success(attempt(LocalModelBackend.GPU))
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
        // The process survived the native GPU start, whatever its result.
        synchronized(this) { runCatching { store.gpuLoadPending = false } }
        if (gpu.isSuccess) {
            synchronized(this) { runCatching { store.notice = null } }
            return LocalModelAcceleration.Loaded(gpu.getOrThrow(), LocalModelBackend.GPU, null)
        }
        val gpuFailure = gpu.exceptionOrNull()!!
        synchronized(this) {
            runCatching { store.requested = LocalModelBackend.CPU }
            runCatching { store.notice = LocalModelAcceleration.Notice.GPU_START_FAILED }
        }
        if (!fallbackAllowed()) throw gpuFailure
        val cpu = try {
            attempt(LocalModelBackend.CPU)
        } catch (cpuFailure: Throwable) {
            cpuFailure.addSuppressed(gpuFailure)
            throw cpuFailure
        }
        return LocalModelAcceleration.Loaded(cpu, LocalModelBackend.CPU,
            LocalModelAcceleration.Notice.GPU_START_FAILED, gpuFailure)
    }

    /** True while the loaded GPU engine has not yet completed one generation of this model file. */
    @Synchronized fun generationNeedsMarker(backend: LocalModelBackend?, modelSha256: String?): Boolean =
        backend == LocalModelBackend.GPU && modelSha256 != null &&
            runCatching { store.gpuProvenModel }.getOrNull() != modelSha256

    @Synchronized fun generationStarting() {
        // An answer the owner asked for is not refused because the marker could not be written.
        runCatching { store.gpuGenerationPending = true }
    }

    /** Any terminal event proves the process survived; only a completed answer proves the GPU path for this file. */
    @Synchronized fun generationEnded(modelSha256: String, completed: Boolean) {
        runCatching { store.gpuGenerationPending = false }
        if (completed) runCatching { store.gpuProvenModel = modelSha256 }
    }
}

/**
 * Marks the first GPU generation of a model file (see [LocalModelAccelerationController]). CPU engines and model
 * files with a completed GPU answer pass through without any extra work; events are forwarded unchanged.
 */
object LocalModelAccelerationGuard {
    fun wrap(delegate: LocalModelRuntime, controller: LocalModelAccelerationController): LocalModelRuntime =
        Guarded(delegate, controller)

    private class Guarded(
        private val delegate: LocalModelRuntime,
        private val controller: LocalModelAccelerationController
    ) : LocalModelRuntime by delegate {
        // "by delegate" forwards interface default methods too; every generation entry point is listed.
        override fun stream(prompt: String, callback: LocalInferenceCallback) =
            guarded(callback) { delegate.stream(prompt, it) }

        override fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) =
            guarded(callback) { delegate.streamTextOnly(prompt, it) }

        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) =
            guarded(callback) { delegate.streamThinking(prompt, it) }

        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
            guarded(callback) { delegate.respondToTools(responses, it) }

        private fun guarded(callback: LocalInferenceCallback, start: (LocalInferenceCallback) -> Unit) {
            val model = delegate.loadedModel?.sha256
            if (model == null || !controller.generationNeedsMarker(delegate.activeBackend, model)) {
                start(callback)
                return
            }
            controller.generationStarting()
            val ended = AtomicBoolean()
            try {
                start(LocalInferenceCallback { event ->
                    val terminal = event is LocalInferenceEvent.Completed || event is LocalInferenceEvent.Cancelled ||
                        event is LocalInferenceEvent.Failed
                    if (terminal && ended.compareAndSet(false, true))
                        controller.generationEnded(model, event is LocalInferenceEvent.Completed)
                    callback.onEvent(event)
                })
            } catch (rejected: Throwable) {
                if (ended.compareAndSet(false, true)) controller.generationEnded(model, false)
                throw rejected
            }
        }
    }
}
