package at.heinz.deviceai.cognitive

/**
 * Keep a useful, actively streaming on-device answer from being discarded
 * solely because its first token took a long time on Android hardware.
 *
 * The original 120-second bound still applies to silent/stalled inference.
 * A *recent* text callback permits a bounded extension, never an infinite
 * wait and never an extension for a full 8,000-character output buffer.
 * Neither model/tool authorization nor cancellation is modified here.
 */
internal object LocalGenerationDeadlinePolicy {
    const val BASE_MILLIS = 120_000L
    const val ACTIVE_STREAM_MAX_MILLIS = 180_000L
    const val PROGRESS_STALE_MILLIS = 12_000L
    const val MAX_CAPTURED_CHARACTERS = 8_000

    fun expired(
        elapsedMillis: Long,
        lastTextMillis: Long?,
        capturedCharacters: Int
    ): Boolean {
        if (elapsedMillis < BASE_MILLIS) return false
        if (elapsedMillis >= ACTIVE_STREAM_MAX_MILLIS) return true
        if (lastTextMillis == null || capturedCharacters <= 0 ||
            capturedCharacters >= MAX_CAPTURED_CHARACTERS) return true
        return elapsedMillis - lastTextMillis > PROGRESS_STALE_MILLIS
    }
}
