package at.heinz.deviceai.cognitive

/**
 * In-memory monotonic timing for one local voice turn. No transcripts, audio,
 * model tokens, account labels or owner identifiers are accepted.
 * TTS_STARTED_CALLBACK is NOT proof of an audible speaker frame.
 */
internal class VoiceTurnLatencyTrace(
    private val clockNanos: () -> Long = System::nanoTime
) {
    enum class Stage {
        MIC_TAPPED,
        ASR_READY,
        SPEECH_BEGAN,
        LAST_VOICE_ACTIVITY_HINT,
        ASR_FINAL_RECEIVED,
        MESSAGE_ACCEPTED,
        INTENT_ROUTED,
        MEMORY_READY,
        PROMPT_READY,
        MODEL_LEASE_READY,
        MODEL_STARTED,
        FIRST_TEXT_DELTA,
        FIRST_APPROVED_SPEECH_CHUNK,
        TTS_QUEUED,
        TTS_SYNTHESIS_BEGAN,
        TTS_STARTED_CALLBACK,
        FINAL_TTS_FINISHED,
        LISTENING_REOPENED
    }

    private val times = LongArray(Stage.entries.size) { Long.MIN_VALUE }

    @Synchronized fun mark(stage: Stage): Boolean {
        if (times[stage.ordinal] != Long.MIN_VALUE) return false
        times[stage.ordinal] = clockNanos()
        return true
    }

    @Synchronized fun recorded(stage: Stage): Boolean =
        times[stage.ordinal] != Long.MIN_VALUE

    @Synchronized fun durationMillis(start: Stage, end: Stage): Double? {
        val a = times[start.ordinal]
        val b = times[end.ordinal]
        if (a == Long.MIN_VALUE || b == Long.MIN_VALUE || b < a) return null
        return (b - a) / 1_000_000.0
    }

    /** Raw monotonic stamps only. Never attach user content to these markers. */
    @Synchronized fun snapshot(): Map<Stage, Long> =
        Stage.entries.mapNotNull { stage ->
            times[stage.ordinal].takeUnless { it == Long.MIN_VALUE }?.let { stage to it }
        }.toMap()
}

internal object VoiceTurnLatencyPercentiles {
    /** Nearest-rank percentile; null when no successful samples exist. */
    fun percentileMillis(samples: Collection<Double>, percentile: Int): Double? {
        require(percentile in 1..100)
        val valid = samples.filter { it.isFinite() && it >= 0.0 }.sorted()
        if (valid.isEmpty()) return null
        val index = kotlin.math.ceil(percentile * valid.size / 100.0).toInt().coerceAtLeast(1) - 1
        return valid[index]
    }
}
