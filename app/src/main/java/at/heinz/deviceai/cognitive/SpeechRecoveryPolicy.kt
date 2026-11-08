package at.heinz.deviceai.cognitive

/**
 * Bounded recovery from Android recognizer handover failures (ERROR_CLIENT, ERROR_RECOGNIZER_BUSY).
 *
 * Fold7 finding (Code50 0.3.49): after a reply the hands-free microphone reported „Spracherkenner
 * belegt“ permanently; a single 250 ms warm retry was not enough and every owner tap recreated
 * the same situation. This policy escalates: warm retries with growing delays on the same native
 * recognizer, then ONE cold restart (destroy + recreate the native recognizer), then it gives up
 * for this turn so the hands-free state machine can decide. No unbounded loops: at most
 * [MAX_ATTEMPTS] re-starts and [MAX_TOTAL_DELAY_MS] of waiting per turn.
 *
 * Pure Kotlin, no Android dependency; the adapter maps Android error codes to [failure] calls.
 */
internal class SpeechRecoveryPolicy(
    private val warmDelaysMs: List<Long> = DEFAULT_WARM_DELAYS_MS,
    private val coldDelayMs: Long = DEFAULT_COLD_DELAY_MS
) {
    sealed class Action {
        /** Start the same native recognizer again after [delayMs]. */
        data class RetryWarm(val delayMs: Long) : Action()
        /** Destroy the native recognizer, create a fresh one and start it after [delayMs]. */
        data class RetryCold(val delayMs: Long) : Action()
        /** Report the failure to the session; this turn is over. */
        object GiveUp : Action()
    }

    private var attempts = 0
    private var coldUsed = false
    private var waitedMs = 0L

    val attemptsUsed: Int get() = attempts
    val coldRestartUsed: Boolean get() = coldUsed

    /** Decision for one recoverable failure. Non-recoverable failures must never reach this policy. */
    fun failure(): Action {
        if (attempts >= MAX_ATTEMPTS) return Action.GiveUp
        val action = if (attempts < warmDelaysMs.size) Action.RetryWarm(warmDelaysMs[attempts])
        else if (!coldUsed) Action.RetryCold(coldDelayMs)
        else return Action.GiveUp
        val delay = when (action) { is Action.RetryWarm -> action.delayMs; is Action.RetryCold -> action.delayMs; Action.GiveUp -> 0L }
        if (waitedMs + delay > MAX_TOTAL_DELAY_MS) return Action.GiveUp
        attempts++
        waitedMs += delay
        if (action is Action.RetryCold) coldUsed = true
        return action
    }

    companion object {
        /** Warm retries: the service usually frees the previous session within a few hundred ms. */
        val DEFAULT_WARM_DELAYS_MS = listOf(250L, 700L)
        /** Cold restart delay: give the on-device service time to drop the stale client binding. */
        const val DEFAULT_COLD_DELAY_MS = 1_200L
        const val MAX_ATTEMPTS = 3
        const val MAX_TOTAL_DELAY_MS = 4_000L
    }
}
