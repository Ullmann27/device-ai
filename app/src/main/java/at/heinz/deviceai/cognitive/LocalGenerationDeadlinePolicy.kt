package at.heinz.deviceai.cognitive

/**
 * A native on-device language model may stream for substantially longer than
 * two minutes on a phone. Active progress is never stopped by a wall clock.
 *
 * A watchdog protects against a model that never starts or stops emitting
 * text altogether. The owner can always cancel through the existing STOP
 * button; this policy neither grants tools nor alters privacy boundaries.
 */
internal object LocalGenerationDeadlinePolicy {
    const val STARTUP_STALL_MILLIS = 180_000L
    const val STREAM_STALL_MILLIS = 120_000L
    const val MAX_CAPTURED_CHARACTERS = 20_000

    enum class StallReason { NO_FIRST_TEXT, NO_RECENT_TEXT, OUTPUT_LIMIT }

    fun stalled(
        elapsedMillis: Long,
        lastTextMillis: Long?,
        capturedCharacters: Int
    ): StallReason? {
        if (capturedCharacters > MAX_CAPTURED_CHARACTERS) return StallReason.OUTPUT_LIMIT
        if (lastTextMillis == null) {
            return if (elapsedMillis >= STARTUP_STALL_MILLIS) StallReason.NO_FIRST_TEXT else null
        }
        return if (elapsedMillis - lastTextMillis >= STREAM_STALL_MILLIS)
            StallReason.NO_RECENT_TEXT else null
    }
}
