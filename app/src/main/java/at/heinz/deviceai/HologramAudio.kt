package at.heinz.deviceai

import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/** Normalized RMS for Android's PCM encodings; no Android dependency or buffer copies. */
internal object HologramPcmLevel {
    fun rms(audio: ByteArray, encoding: Int): Float {
        val stride = when (encoding) {
            2 -> 2 // AudioFormat.ENCODING_PCM_16BIT, signed little endian
            3 -> 1 // AudioFormat.ENCODING_PCM_8BIT, unsigned
            4 -> 4 // AudioFormat.ENCODING_PCM_FLOAT, little endian
            else -> return 0f
        }
        val count = audio.size / stride
        if (count == 0) return 0f
        var sum = 0.0
        var offset = 0
        repeat(count) {
            val sample = when (stride) {
                1 -> ((audio[offset].toInt() and 255) - 128) / 128f
                2 -> ((audio[offset].toInt() and 255) or
                    (audio[offset + 1].toInt() shl 8)) / 32768f
                else -> {
                    val bits = (audio[offset].toInt() and 255) or
                        ((audio[offset + 1].toInt() and 255) shl 8) or
                        ((audio[offset + 2].toInt() and 255) shl 16) or
                        (audio[offset + 3].toInt() shl 24)
                    val value = Float.fromBits(bits)
                    if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
                }
            }
            sum += sample.toDouble() * sample
            offset += stride
        }
        return sqrt(sum / count).toFloat().coerceIn(0f, 1f)
    }
}

/** Fast attack and soft release measured in time, including release after missing audio. */
internal class HologramAudioEnvelope {
    private var target = 0f
    private var value = 0f
    private var lastSampleMs = Long.MIN_VALUE
    private var lastFrameMs = Long.MIN_VALUE

    fun submit(level: Float, nowMs: Long) {
        valueAt(nowMs)
        target = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
        lastSampleMs = nowMs
    }

    fun valueAt(nowMs: Long): Float {
        if (lastFrameMs == Long.MIN_VALUE) {
            lastFrameMs = nowMs
            return value
        }
        val start = min(lastFrameMs, nowMs)
        val freshUntil = if (lastSampleMs == Long.MIN_VALUE) start else lastSampleMs + 180L
        val activeMs = (min(nowMs, freshUntil) - start).coerceAtLeast(0)
        if (activeMs > 0) {
            val responseMs = if (target > value) 35f else 150f
            value += (target - value) * (1f - exp(-activeMs / responseMs))
        }
        val releaseMs = (nowMs - maxOf(start, freshUntil)).coerceAtLeast(0)
        if (releaseMs > 0) value *= exp(-releaseMs / 150f)
        lastFrameMs = nowMs
        if (value < .0001f) value = 0f
        return value
    }

    fun reset() {
        target = 0f
        value = 0f
        lastSampleMs = Long.MIN_VALUE
        lastFrameMs = Long.MIN_VALUE
    }
}
