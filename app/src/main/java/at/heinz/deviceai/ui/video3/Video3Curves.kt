package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.motion.Easings
import at.heinz.deviceai.motion.MotionPresets

/**
 * Curves of the Video 3 scenes. All motion maths comes from Jarvis Mathematik (at.heinz.deviceai.motion, PR #489);
 * this object only adapts units (seconds ↔ ms) and keeps the scenes' call sites short.
 */
object Video3Curves {
    /** Muster A / warp: exponential ease-in. */
    val easeInExpo: (Float) -> Float = { MotionPresets.warpEasing.at(it) }
    val easeOutCubic: (Float) -> Float = { Easings.easeOutCubic.at(it) }
    /** Z-zoom into the face. */
    val easeIn: (Float) -> Float = { MotionPresets.faceZoomEasing.at(it) }

    const val ALARM_ZOOM_S = MotionPresets.FACE_ZOOM_MS / 1000f
    const val WARP_BURST_S = MotionPresets.WARP_MS / 1000f
    const val SHAKE_PX = MotionPresets.SHAKE_AMPLITUDE_PX
    const val GLOW_FACTOR = MotionPresets.ALARM_GLOW_FACTOR
    const val LASER_S = MotionPresets.LASER_MS / 1000f

    /** Warp element scale 0 → MotionPresets.WARP_MAX_SCALE after [seconds]. */
    fun warpScale(seconds: Float): Float = MotionPresets.warpScale((seconds * 1000f).toLong())

    /** Deterministic 2 px shake (same seed and time ⇒ same offset). */
    fun shakeX(seed: Int, timeMs: Long, amplitudePx: Float = SHAKE_PX): Float = MotionPresets.shake(timeMs, seed.toLong(), 0, amplitudePx)
    fun shakeY(seed: Int, timeMs: Long, amplitudePx: Float = SHAKE_PX): Float = MotionPresets.shake(timeMs, seed.toLong(), 1, amplitudePx)

    /** Orb pulse scale 1 … 1 + depth. */
    fun orbPulse(seconds: Float): Float = MotionPresets.orbPulse((seconds * 1000f).toLong())

    /** Laser draw-on progress 0..1 (cubic ease-out over LASER_MS). */
    fun laser(seconds: Float): Float = easeOutCubic((seconds / LASER_S).coerceIn(0f, 1f))

    /** Integer hash for fixed, precomputed layouts (positions only, never a value source). */
    fun hash(x: Int): Int {
        var h = x * -0x61c88647
        h = h xor (h ushr 15); h *= 0x2c1b3c6d; h = h xor (h ushr 12); h *= 0x297a2d39; h = h xor (h ushr 15)
        return h and 0x7fffffff
    }

    /** Deterministic 0..1 from [seed] (layout only). */
    fun unit(seed: Int): Float = (hash(seed) and 0xffff) / 65535f

    /** Default alarm base effects for branches without JarvisSequenceEffects: glow ×3 and the 2 px shake. */
    class Fallback(private val seed: Int = 3) : AlarmBaseEffects {
        override fun glowBoost(nowMillis: Long) = GLOW_FACTOR
        override fun shakeOffsetX(nowMillis: Long) = shakeX(seed, nowMillis)
        override fun shakeOffsetY(nowMillis: Long) = shakeY(seed, nowMillis)
    }
}
