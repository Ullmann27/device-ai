package at.heinz.deviceai

/** Bounded amplitude metadata only. PCM is never retained. Playback callbacks
 * anchor synthesis chunks to audible output rather than synthesis arrival time. */
internal class TtsHologramSignal {
    private data class Point(val frame: Long, val end: Long, val level: Float)
    private val points = ArrayList<Point>()
    private var sampleRate = 0
    private var encoding = 0
    private var channels = 1
    private var nextFrame = 0L
    private var anchorFrame = 0L
    private var anchorMillis = 0L
    private var playing = false
    private var utterance: String? = null

    @Synchronized fun activate(id: String) {
        stop()
        utterance = id
        sampleRate = 0
    }

    @Synchronized fun begin(id: String, rate: Int, format: Int, channelCount: Int) {
        if (utterance != id) return
        points.clear(); nextFrame = 0
        sampleRate = rate.takeIf { it in 8_000..192_000 } ?: 0
        encoding = format
        channels = channelCount.coerceIn(1, 8)
    }

    @Synchronized fun append(id: String, audio: ByteArray) {
        if (utterance != id) return
        val bytesPerSample = when (encoding) { 2 -> 2; 3 -> 1; 4 -> 4; else -> return }
        if (sampleRate == 0 || points.size >= 1_024) return
        val frames = audio.size / (bytesPerSample * channels)
        if (frames == 0) return
        points.add(Point(nextFrame, nextFrame + frames, HologramPcmLevel.rms(audio, encoding)))
        nextFrame += frames
    }

    @Synchronized fun anchor(id: String, frame: Int, nowMillis: Long) {
        if (utterance != id) return
        anchorFrame = frame.toLong().coerceAtLeast(0)
        anchorMillis = nowMillis
        playing = true
    }

    @Synchronized fun level(nowMillis: Long): Float {
        if (!playing || sampleRate == 0) return 0f
        val frame = anchorFrame + (nowMillis - anchorMillis).coerceAtLeast(0) * sampleRate / 1_000
        return points.lastOrNull { frame >= it.frame && frame < it.end }?.level ?: 0f
    }

    @Synchronized fun stop() { utterance = null; playing = false; points.clear(); nextFrame = 0 }
}
