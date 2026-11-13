package at.heinz.deviceai.localmodel

/**
 * #569: thermal guard in front of every local generation. The owner's Fold7 got very hot during several 47–63 s
 * Gemma answers; [LocalModelLoadAdmission] refused only the LOAD at SEVERE, every generation still started and
 * kept running. This guard uses only Android's own PowerManager thermal status (0..6), never a temperature value:
 *  - a generation (stream, thinking, tool continuation) does not start at SEVERE (3) or above; the native runtime
 *    is not called and the caller receives Failed([PAUSED_THERMAL]);
 *  - a running generation is stopped once when Android reports SEVERE or above; its terminal event becomes
 *    Failed([STOPPED_THERMAL]) instead of an unexplained cancel;
 *  - nothing is queued, retried or resumed in the background: the owner decides when to ask again.
 * A missing status (API < 29, unreadable) keeps the previous behaviour; it is never treated as a measured "cool".
 * Rendering budgets (JarvisRenderQuality) are separate and stay in place.
 */
object LocalModelThermalGuard {
    const val PAUSED_THERMAL = "LOCAL_MODEL_PAUSED_THERMAL"
    const val STOPPED_THERMAL = "LOCAL_MODEL_STOPPED_THERMAL"
    /** PowerManager.THERMAL_STATUS_SEVERE, the same threshold as the load admission. */
    const val SEVERE = 3

    /** The process runtime as [LocalModelHost] exposes it to every caller. */
    fun wrap(delegate: LocalModelRuntime, thermalStatus: () -> Int?): LocalModelRuntime =
        Guarded(delegate, thermalStatus)

    /** PowerManager thermal listener entry; a no-op for a runtime this guard did not wrap. */
    fun onThermalStatus(runtime: LocalModelRuntime, status: Int) {
        (runtime as? Guarded)?.onThermalStatus(status)
    }

    fun isThermal(problem: String?): Boolean = problem == PAUSED_THERMAL || problem == STOPPED_THERMAL

    fun ownerMessage(problem: String?): String = when (problem) {
        PAUSED_THERMAL -> "Ich rechne gerade nicht: Android meldet, dass das Gerät stark erwärmt ist. " +
            "Bitte lass es abkühlen und frag mich dann noch einmal. Ich starte nichts automatisch nach."
        STOPPED_THERMAL -> "Ich habe die Antwort angehalten: Android meldet starke Erwärmung des Geräts. " +
            "Bitte lass es abkühlen und frag mich dann noch einmal. Ich starte nichts automatisch nach."
        else -> "Das lokale Modell wurde wegen der Gerätetemperatur nicht ausgeführt."
    }

    private class Run {
        var stoppedByHeat = false
        var startReturned = false
        var cancelIssued = false
        var cancelling = false
        var terminal = false
        val delivery = LocalInferenceDelivery()
    }

    private class Guarded(
        private val delegate: LocalModelRuntime,
        private val thermalStatus: () -> Int?
    ) : LocalModelRuntime by delegate {
        private val lock = Any()
        private val inFlight = LinkedHashSet<Run>()
        private var nativeOwner: Run? = null

        override fun stream(prompt: String, callback: LocalInferenceCallback) =
            guarded(callback) { delegate.stream(prompt, it) }

        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) =
            guarded(callback) { delegate.streamThinking(prompt, it) }

        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
            guarded(callback) { delegate.respondToTools(responses, it) }

        private fun guarded(callback: LocalInferenceCallback, start: (LocalInferenceCallback) -> Unit) {
            // Register before sampling Android.
            val run = Run()
            synchronized(lock) { inFlight += run }
            try {
                val status = runCatching(thermalStatus).getOrNull()
                val paused = synchronized(lock) {
                    if (status != null && status >= SEVERE) run.stoppedByHeat = true
                    if (run.stoppedByHeat) {
                        finishLocked(run)
                        true
                    } else {
                        check(nativeOwner == null) { "A local model is not ready." }
                        nativeOwner = run
                        false
                    }
                }
                if (paused) {
                    callback.onEvent(LocalInferenceEvent.Failed(PAUSED_THERMAL))
                    return
                }
                start(LocalInferenceCallback { event ->
                    val terminal = event is LocalInferenceEvent.Completed || event is LocalInferenceEvent.Cancelled ||
                        event is LocalInferenceEvent.Failed
                    synchronized(lock) {
                        if (run.terminal) return@LocalInferenceCallback
                        if (terminal) finishLocked(run)
                        run.delivery.enqueue {
                            val delivered = synchronized(lock) {
                                if (!run.stoppedByHeat) event
                                else if (terminal) LocalInferenceEvent.Failed(STOPPED_THERMAL)
                                else null
                            }
                            delivered?.let(callback::onEvent)
                        }
                    }
                    run.delivery.drain()
                    if (!terminal) cancelHeated(run)
                })
                synchronized(lock) { run.startReturned = true }
                cancelHeated(run)
            } catch (rejected: Throwable) {
                synchronized(lock) { finishLocked(run) }
                throw rejected
            }
        }

        // Cancellation retains native ownership.
        private fun finishLocked(run: Run) {
            run.terminal = true
            inFlight -= run
            if (nativeOwner === run && !run.cancelling) nativeOwner = null
        }

        private fun cancelHeated(run: Run) {
            val cancel = synchronized(lock) {
                if (nativeOwner !== run || run.terminal || !run.stoppedByHeat || run.cancelIssued ||
                    (!run.startReturned && delegate.state != LocalModelState.GENERATING)) false
                else {
                    run.cancelIssued = true
                    run.cancelling = true
                    true
                }
            }
            if (!cancel) return
            try {
                delegate.cancel()
            } finally {
                synchronized(lock) {
                    run.cancelling = false
                    if (run.terminal && nativeOwner === run) nativeOwner = null
                }
            }
        }

        fun onThermalStatus(status: Int) {
            if (status < SEVERE) return
            val owner = synchronized(lock) {
                inFlight.forEach { it.stoppedByHeat = true }
                nativeOwner
            }
            owner?.let(::cancelHeated)
        }
    }
}
