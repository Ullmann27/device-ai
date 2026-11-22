package at.heinz.deviceai.motion

import kotlin.math.PI
import kotlin.math.sin

/**
 * The one preset table for Jarvis motion (VIDEO-SPEC.md "Muster + Timings", JARVIS-DESIGN-BILDBESCHRIEB.md).
 * JarvisMotion (ui) reads its durations and curves from here. Values marked GESCHÄTZT are not stated in
 * the spec or the image description; everything else is quoted from them.
 */
object MotionPresets {
    // A – scene change in Z depth: 0,8 s exponential ease-in; old UI → >300 % + alpha 0, new 0 → 100 %.
    const val SCENE_MS = 800L
    const val SCENE_OLD_SCALE = 3.2f // "> 300 %"
    val sceneEasing: Easing = Easings.easeInExpo
    const val KEY_SCENE_OLD = "scene.old"
    const val KEY_SCENE_NEW = "scene.new"

    // B – panel/card fly-in: 0,5–0,6 s cubic ease-out, scale 80 → 100 %, stagger 0,05–0,1 s, centre first.
    const val FLY_IN_MS = 600L
    const val FLY_IN_FROM_SCALE = .8f
    const val STAGGER_MS = 80L
    const val STAGGER_SEC = STAGGER_MS / 1000f
    val flyInEasing: Easing = Easings.easeOutCubic
    val panelStagger: Stagger = Staggers.centerFirst(STAGGER_SEC)

    // C – avatar idle float / breathing: 4 s sine, Y ±2–3 px.
    const val IDLE_PERIOD_MS = 4000L
    const val IDLE_AMPLITUDE_PX = 2.5f

    // D – laser connection: 0,3 s draw-on (spec names no curve; JarvisMotion.laser keeps linear).
    const val LASER_MS = 300L
    val laserEasing: Easing = Easings.linear

    // F – Gmail envelope: 0,5 s from depth + 0,3 s flap.
    const val ENVELOPE_IN_MS = 500L
    const val ENVELOPE_FLAP_MS = 300L
    const val KEY_ENVELOPE_IN = "envelope.in"
    const val KEY_ENVELOPE_FLAP = "envelope.flap"

    // G – question pills: from the edges, no fade, hard and slightly springy stop (Bildbeschrieb 5).
    // Settle 0,45 s / ζ 0,8 (≈ 1,5 % overshoot): GESCHÄTZT from "schnell, hart, leicht federnd".
    val pillSpring = DampedSpring.forSettle(.45f, .8f)

    // Widgets (Bildbeschrieb 6): 50 → 100 %, springy with slight overshoot, ~0,5 s. ζ 0,7 (≈ 4,6 %) GESCHÄTZT.
    const val WIDGET_FROM_SCALE = .5f
    val widgetSpring = DampedSpring.forSettle(.5f, .7f)
    /** General "Video" spring (~0,5 s, small overshoot) for panels/widgets that should land with weight. */
    val videoSpring: DampedSpring = widgetSpring

    // H – alarm: shake 2 px, glow ×3 ("+200 %").
    const val SHAKE_AMPLITUDE_PX = 2f
    /** New random target every 40 ms (25 Hz), smoothly interpolated. GESCHÄTZT. */
    const val SHAKE_STEP_MS = 40L
    const val ALARM_GLOW_FACTOR = 3f

    // Video 3 (Lückenfüller): warp 0,8 s exponential ease-in, elements 0 → > 500 %.
    const val WARP_MS = 800L
    const val WARP_MAX_SCALE = 5.2f // "> 500 %", same margin as A's 3,2 for "> 300 %"
    val warpEasing: Easing = Easings.easeInExpo
    /** Z zoom towards the face: ~0,5 s ease-in (target scale is the caller's framing). */
    const val FACE_ZOOM_MS = 500L
    val faceZoomEasing: Easing = Easings.easeInCubic
    /** Energy orb pulse (Video 3 "pulsierende Energie-Kugel"): period and depth GESCHÄTZT. */
    const val ORB_PULSE_PERIOD_MS = 2000L
    const val ORB_PULSE_DEPTH = .08f

    // Z camera (Bildbeschrieb 1): continuous linear dolly-in 1,00 → 1,03 "über mehrere Sekunden",
    // then the same way back (ping-pong). Leg length GESCHÄTZT.
    const val DOLLY_MAX_SCALE = 1.03f
    const val DOLLY_PERIOD_MS = 8000L

    // Avatar face (VIDEO-SPEC "Avatar-Sprechen").
    const val BLINK_MS = 150L

    /** Muster C: Y offset in px, amplitude·sin(2πt/4 s); continuous across the loop. */
    fun idleFloat(timeMs: Long, amplitudePx: Float = IDLE_AMPLITUDE_PX, periodMs: Long = IDLE_PERIOD_MS): Float =
        amplitudePx * sin(2f * PI.toFloat() * Math.floorMod(timeMs, periodMs).toFloat() / periodMs)

    /** Linear dolly-in camera scale 1 → [maxScale] over [periodMs], then back (ping-pong). Negative time mirrors. */
    fun dollyScale(timeMs: Long, maxScale: Float = DOLLY_MAX_SCALE, periodMs: Long = DOLLY_PERIOD_MS): Float {
        val t = Math.floorMod(timeMs, 2 * periodMs).toFloat() / periodMs
        val k = if (t <= 1f) t else 2f - t
        return 1f + (maxScale - 1f) * k
    }

    /** Muster A old layer scale for progress e (eased): 1 → [SCENE_OLD_SCALE]. */
    fun sceneOldScale(eased: Float) = 1f + (SCENE_OLD_SCALE - 1f) * eased

    /** Video 3 warp element scale 0 → [WARP_MAX_SCALE] after [elapsedMs]. */
    fun warpScale(elapsedMs: Long): Float =
        WARP_MAX_SCALE * warpEasing.at((elapsedMs.toFloat() / WARP_MS).coerceIn(0f, 1f))

    /** Orb pulse scale factor 1 .. 1 + depth (smooth raised cosine, starts and ends each period at 1). */
    fun orbPulse(timeMs: Long, periodMs: Long = ORB_PULSE_PERIOD_MS, depth: Float = ORB_PULSE_DEPTH): Float {
        val ph = Math.floorMod(timeMs, periodMs).toFloat() / periodMs
        return 1f + depth * (.5f - .5f * kotlin.math.cos(2f * PI.toFloat() * ph))
    }

    /**
     * Muster H: deterministic screen shake offset in px for one [axis] (0 = x, 1 = y), |value| ≤ [amplitudePx].
     * Same (timeMs, seed, axis) → same value; random targets every [SHAKE_STEP_MS], smoothstep in between.
     */
    fun shake(timeMs: Long, seed: Long, axis: Int, amplitudePx: Float = SHAKE_AMPLITUDE_PX): Float {
        val step = Math.floorDiv(timeMs, SHAKE_STEP_MS)
        val f = Math.floorMod(timeMs, SHAKE_STEP_MS).toFloat() / SHAKE_STEP_MS
        val a = Hash.signed(seed, step, axis.toLong()); val b = Hash.signed(seed, step + 1, axis.toLong())
        val s = f * f * (3f - 2f * f)
        return amplitudePx * (a + (b - a) * s)
    }
}

/** SplitMix64-based stateless hash → uniform values; deterministic per seed. */
internal object Hash {
    fun mix(x0: Long): Long {
        var z = x0 + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
    /** Uniform in [0,1). */
    fun unit(seed: Long, a: Long, b: Long = 0L): Float =
        (mix(mix(mix(seed) + a) + b) ushr 40).toFloat() / (1L shl 24).toFloat()
    /** Uniform in [-1,1). */
    fun signed(seed: Long, a: Long, b: Long = 0L): Float = unit(seed, a, b) * 2f - 1f
}
