package at.heinz.deviceai.cognitive

/**
 * Turns a detected same-session follow-up into the task text the local model sees.
 * (Stub: unchanged behaviour of Code39 – the model only sees the newest message as task.)
 */
internal object LocalFollowUpTaskFrame {
    fun compose(currentRequest: String, resolvedQuestion: String, priorOwnerTurns: List<String>?): String =
        resolvedQuestion
}
