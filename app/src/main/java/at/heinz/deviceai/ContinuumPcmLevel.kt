package at.heinz.deviceai

import kotlin.math.min
import kotlin.math.sqrt

/** Normalized RMS of signed little-endian 16-bit PCM from Android TTS.
 * The caller must provide actual audio bytes from UtteranceProgressListener.
 * No data means no measured amplitude and never a fabricated waveform.
 */
object ContinuumPcmLevel {
    fun rms16Le(bytes: ByteArray): Float {
        if (bytes.size < 2) return 0f
        val sampleCount = min(bytes.size / 2, 4096)
        var sumSquares = 0.0
        for (sample in 0 until sampleCount) {
            val offset = sample * 2
            val low = bytes[offset].toInt() and 255
            val high = bytes[offset + 1].toInt()
            val signed = ((high shl 8) or low).toShort().toInt()
            val unit = signed / 32768.0
            sumSquares += unit * unit
        }
        return sqrt(sumSquares / sampleCount).toFloat().coerceIn(0f, 1f)
    }
}
