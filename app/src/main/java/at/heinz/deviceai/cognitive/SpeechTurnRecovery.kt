package at.heinz.deviceai.cognitive

/**
 * Drives [SpeechRecoveryPolicy] for one recognizer turn: schedules the warm or cold restart,
 * swallows the failure while a retry is pending and reports the final failure once the budget
 * is used up. Pure Kotlin so the Fold7 „belegt“ sequence can be replayed in a JVM test; the
 * Android adapter supplies the native start/restart and the main-thread scheduler.
 */
internal class SpeechTurnRecovery(
    private val policy: SpeechRecoveryPolicy = SpeechRecoveryPolicy(),
    /** Post [task] after the delay; returns a cancel handle. */
    private val schedule: (delayMs: Long, task: () -> Unit) -> (() -> Unit),
    /** Start the same native recognizer again; may throw. */
    private val startWarm: () -> Unit,
    /** Destroy the native recognizer, create a fresh one and start it; may throw. */
    private val restartCold: () -> Unit,
    /** Terminal failure for this turn after recovery is exhausted or a restart threw. */
    private val giveUp: (LocalSpeechInputPolicy.Failure) -> Unit
) {
    private var pendingCancel: (() -> Unit)? = null
    private var closed = false

    val retryPending: Boolean get() = pendingCancel != null
    val coldRestartUsed: Boolean get() = policy.coldRestartUsed
    val attemptsUsed: Int get() = policy.attemptsUsed

    /**
     * Handle a recoverable recognizer error. Returns true when a retry was scheduled (the
     * caller must not report the failure), false when the turn has to fail now.
     */
    fun recoverableError(failure: LocalSpeechInputPolicy.Failure): Boolean {
        // Duplicate error callbacks of one failed start, or errors after the turn ended, are swallowed.
        if (closed || pendingCancel != null) return true
        return when (val action = policy.failure()) {
            is SpeechRecoveryPolicy.Action.RetryWarm -> { arm(action.delayMs, startWarm, failure); true }
            is SpeechRecoveryPolicy.Action.RetryCold -> { arm(action.delayMs, restartCold, failure); true }
            SpeechRecoveryPolicy.Action.GiveUp -> false
        }
    }

    private fun arm(delayMs: Long, restart: () -> Unit, failure: LocalSpeechInputPolicy.Failure) {
        pendingCancel = schedule(delayMs) {
            pendingCancel = null
            if (closed) return@schedule
            val result = runCatching(restart)
            if (result.isFailure) {
                closed = true
                giveUp(if (failure == LocalSpeechInputPolicy.Failure.BUSY) LocalSpeechInputPolicy.Failure.START_FAILED else failure)
            }
        }
    }

    /** The turn ended (result, cancel, timeout, destroy): no retry may fire afterwards. */
    fun close() {
        closed = true
        pendingCancel?.invoke()
        pendingCancel = null
    }
}
