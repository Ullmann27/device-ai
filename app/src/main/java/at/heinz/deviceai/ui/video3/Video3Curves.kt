package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.ui.JarvisMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * Curves used by the Video 3 scenes.
 * Where #479 already has a curve, it is JarvisMotion.Easing (no copy). Everything marked MATHEMATIK-TAUSCH is a small
 * implementation standing in for Jarvis Mathematik (planned branch jarvis-mathematik/motion-math, package at.heinz.deviceai.motion:
 * Easing / SpringCurve / Stagger) and is meant to be replaced 1:1 – same name, same arguments, same range.
 */
object Video3Curves {
    /** Muster A – exponential ease-in (0,8 s). From #479. */
    val easeInExpo: (Float) -> Float = JarvisMotion.Easing.easeInExpo
    /** Cubic ease-out (0,3 s / 0,6 s). From #479. */
    val easeOutCubic: (Float) -> Float = JarvisMotion.Easing.easeOutCubic
    /** MATHEMATIK-TAUSCH "Ease-in 0,5 s" (Z-zoom into the face): cubic ease-in. */
    val easeIn: (Float) -> Float = { val x = it.coerceIn(0f, 1f); x * x * x }

    const val ALARM_ZOOM_S = .5f      // VIDEO-ANALYSE: harter Z-Zoom, ease-in ~0,5 s
    const val WARP_BURST_S = .8f      // Muster A: 0,8 s
    const val SHAKE_PX = 2f           // Muster H: 2 px

    /**
     * MATHEMATIK-TAUSCH "deterministischer 2-px-Shake mit Seed": same (seed, timeMs) ⇒ same offset, |x|,|y| ≤ amplitude.
     * Sum of two incommensurate sines with seed-derived phases; no allocation.
     */
    fun shakeX(seed: Int, timeMs: Long, amplitudePx: Float = SHAKE_PX): Float = amplitudePx * wobble(seed, timeMs, 0)
    fun shakeY(seed: Int, timeMs: Long, amplitudePx: Float = SHAKE_PX): Float = amplitudePx * wobble(seed, timeMs, 1)

    private fun wobble(seed: Int, timeMs: Long, axis: Int): Float {
        val h = hash(seed * 2 + axis)
        val p1 = (h and 1023) / 1023f * 2f * PI.toFloat()
        val p2 = ((h ushr 10) and 1023) / 1023f * 2f * PI.toFloat()
        val t = (timeMs % 60_000L).toFloat()
        return (.6f * sin(t * .071f + p1) + .4f * sin(t * .163f + p2)).coerceIn(-1f, 1f)
    }

    /** MATHEMATIK-TAUSCH "Kugel-Puls": 0..1, period [periodS] (GESCHÄTZT 1,6 s aus Video 3, 15–17 s). */
    fun orbPulse(seconds: Float, periodS: Float = 1.6f): Float =
        .5f + .5f * sin(2f * PI.toFloat() * (seconds % periodS) / periodS)

    /** Integer hash (deterministic pseudo random for fixed layouts; not a value source). */
    fun hash(x: Int): Int {
        var h = x * -0x61c88647
        h = h xor (h ushr 15); h *= 0x2c1b3c6d; h = h xor (h ushr 12); h *= 0x297a2d39; h = h xor (h ushr 15)
        return h and 0x7fffffff
    }

    /** Deterministic 0..1 from [seed]. */
    fun unit(seed: Int): Float = (hash(seed) and 0xffff) / 65535f

    /**
     * Default alarm base effects for branches without JarvisSequenceEffects (identical behaviour: glow ×3, 2 px shake).
     * Glow ×3 and 2 px shake like there, but deterministic (seeded) as Mathematik plans it.
     */
    class Fallback(private val seed: Int = 3) : AlarmBaseEffects {
        override fun glowBoost(nowMillis: Long) = 3f
        override fun shakeOffsetX(nowMillis: Long) = shakeX(seed, nowMillis)
        override fun shakeOffsetY(nowMillis: Long) = shakeY(seed, nowMillis)
    }
}
