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

    private class Run { @Volatile var stoppedByHeat = false }

    private class Guarded(
        private val delegate: LocalModelRuntime,
        private val thermalStatus: () -> Int?
    ) : LocalModelRuntime by delegate {
        private val lock = Any()
        private var inFlight: Run? = null

        override fun stream(prompt: String, callback: LocalInferenceCallback) =
            guarded(callback) { delegate.stream(prompt, it) }

        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) =
            guarded(callback) { delegate.streamThinking(prompt, it) }

        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
            guarded(callback) { delegate.respondToTools(responses, it) }

        private fun guarded(callback: LocalInferenceCallback, start: (LocalInferenceCallback) -> Unit) {
            val status = runCatching(thermalStatus).getOrNull()
            if (status != null && status >= SEVERE) {
                callback.onEvent(LocalInferenceEvent.Failed(PAUSED_THERMAL))
                return
            }
            val run = Run()
            synchronized(lock) { inFlight = run }
            start(LocalInferenceCallback { event ->
                val terminal = event is LocalInferenceEvent.Completed || event is LocalInferenceEvent.Cancelled ||
                    event is LocalInferenceEvent.Failed
                if (terminal) synchronized(lock) { if (inFlight === run) inFlight = null }
                callback.onEvent(if (terminal && run.stoppedByHeat && event !is LocalInferenceEvent.Completed)
                    LocalInferenceEvent.Failed(STOPPED_THERMAL) else event)
            })
        }

        fun onThermalStatus(status: Int) {
            if (status < SEVERE) return
            val run = synchronized(lock) {
                inFlight?.takeIf { !it.stoppedByHeat }?.also { it.stoppedByHeat = true }
            } ?: return
            if (run.stoppedByHeat) delegate.cancel()
        }
    }
}
