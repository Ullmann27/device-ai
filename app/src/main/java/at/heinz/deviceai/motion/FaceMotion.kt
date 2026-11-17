package at.heinz.deviceai.motion

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The 21 morph targets of the packaged avatar (FilamentAvatarRenderer / AvatarGlbContract.TARGETS, #478), in
 * renderer order: FaceMesh and NeuralThreadsMesh of avatar3d/continuum-avatar.glb carry exactly this
 * targetNames order. Indices 0..14 are the 15 OVR visemes in avatar.Viseme order (SIL..U).
 * A unit test guards this list against AvatarGlbContract.TARGETS.
 */
object FaceChannels {
    val RENDERER_ORDER: List<String> = listOf("SIL", "PP", "FF", "TH", "DD", "KK", "CH", "SS", "NN", "RR", "AA", "E", "I", "O", "U",
        "BlinkLeft", "BlinkRight", "BrowUp", "BrowDown", "Smile", "JawOpen")
    const val VISEME_COUNT = 15

    /**
     * Mouth opening already authored inside each viseme target, relative to JawOpen (PR404 build_bust.py @0862cfc8,
     * same values as AvatarMorphMapper.sharedOpeningRatio). JawOpen = max(0, jaw - Σ ratio·weight) avoids double opening.
     * Index = viseme index (SIL..U). Guarded by a test against AvatarMorphMapper.
     */
    internal val SHARED_OPENING = floatArrayOf(0f, 0f, .06f / .8f, .18f / .8f, .20f / .8f, .30f / .8f, .15f / .8f, 0f,
        .11f / .8f, .22f / .8f, .65f / .8f, .23f / .8f, .10f / .8f, .36f / .8f, .10f / .8f)
}

/**
 * Lip-sync, blink and breathing for the avatar head as a [FaceWeightsSource].
 *
 * Mouth (only from a real signal):
 *  - envelope of [audio] (normalised RMS 0..1) with one-pole attack [ATTACK_MS] / release [RELEASE_MS];
 *  - below [SILENCE_LEVEL] the target is 0, and the jaw has a dead zone of [SILENCE_LEVEL], so in silence every
 *    mouth channel (visemes, JawOpen) is exactly 0;
 *  - visemes only from [visemes] (TTS phonemes, index in avatar.Viseme order 0..14); without hints only JawOpen
 *    moves. Viseme changes crossfade linearly over [VISEME_CROSSFADE_MS]; viseme weight is gated by the envelope.
 * Blink: [MotionPresets.BLINK_MS] (sin bump), one per 4,5 s slot with a seeded jitter of ±0,75 s, i.e. gaps of 3–6 s,
 * deterministic per [seed] and stateless (any time order). Brows/Smile stay 0 (expression is the avatar's state).
 * Breathing/idle float: [breath] (4 s sine, −1..1) and [MotionPresets.idleFloat] for the Y offset.
 *
 * Time: [weightsAt] is meant for a monotone frame clock. Equal time → same frame again; time going backwards
 * resets the smoothing to rest (as on a fresh instance), so a replay from the same start is bit-identical.
 * Stateful but allocation-free; not thread-safe (call from the render thread).
 */
class LipSyncFaceSource(
    private val audio: AudioLevelSource,
    private val visemes: VisemeHintSource? = null,
    val seed: Long = 0L,
    override val channelNames: List<String> = FaceChannels.RENDERER_ORDER,
) : FaceWeightsSource {
    private val visemeChannel = IntArray(FaceChannels.VISEME_COUNT) { channelNames.indexOf(FaceChannels.RENDERER_ORDER[it]) }
    private val jawChannel = channelNames.indexOf("JawOpen")
    private val blinkLeft = channelNames.indexOf("BlinkLeft")
    private val blinkRight = channelNames.indexOf("BlinkRight")
    private val blend = FloatArray(FaceChannels.VISEME_COUNT)
    private var lastMs = Long.MIN_VALUE

    init {
        require(channelNames.toSet().size == channelNames.size) { "Duplicate channel names" }
        require(jawChannel >= 0 && blinkLeft >= 0 && blinkRight >= 0) { "JawOpen/BlinkLeft/BlinkRight channels required" }
    }

    /** Smoothed level 0..1 after the last [weightsAt]; drives speaking glow / speakEnergy. */
    var envelope = 0f; private set
    /** Raw jaw opening 0..1 (before viseme compensation) – AvatarPose.jawOpen. */
    var jawOpen = 0f; private set
    /** Strongest viseme index (0 = SIL) and its weight – AvatarPose.viseme / visemeWeight. */
    var dominantViseme = 0; private set
    var dominantVisemeWeight = 0f; private set
    /** Blink weight 0..1 of the last frame. */
    var blink = 0f; private set
    /** Speaking energy 0..1 (= [envelope]); avatar maps it to its glow. */
    val speakEnergy: Float get() = envelope

    override fun weightsAt(timeMs: Long, out: FloatArray) {
        require(out.size >= channelNames.size) { "out.size ${out.size} < ${channelNames.size}" }
        val dt: Long
        if (lastMs == Long.MIN_VALUE || timeMs < lastMs) { reset(); dt = 0L } else dt = timeMs - lastMs
        lastMs = timeMs

        val raw = audio.levelAt(timeMs)
        val level = if (raw.isFinite()) raw.coerceIn(0f, 1f) else 0f
        val target = if (level < SILENCE_LEVEL) 0f else level
        if (dt > 0L) {
            val tau = if (target > envelope) ATTACK_MS else RELEASE_MS
            envelope += (target - envelope) * (1f - exp(-dt / tau))
        }
        val gate = ((envelope - SILENCE_LEVEL) / (GATE_FULL_LEVEL - SILENCE_LEVEL)).coerceIn(0f, 1f)
        jawOpen = ((envelope - SILENCE_LEVEL) * JAW_GAIN).coerceIn(0f, 1f)

        // Viseme crossfade toward the hinted viseme (SIL / none / silence → all close).
        val hint = visemes?.visemeAt(timeMs)
        val active = if (hint != null && hint in 1 until FaceChannels.VISEME_COUNT && gate > 0f) hint else 0
        val step = if (dt <= 0L) 0f else (dt / VISEME_CROSSFADE_MS).coerceAtMost(1f)
        var cueOpening = 0f
        dominantViseme = 0; dominantVisemeWeight = 0f
        for (v in 1 until FaceChannels.VISEME_COUNT) {
            val goal = if (v == active) 1f else 0f
            val cur = blend[v]
            blend[v] = if (goal > cur) minOf(goal, cur + step) else maxOf(goal, cur - step)
            val w = blend[v] * gate
            if (w > dominantVisemeWeight) { dominantVisemeWeight = w; dominantViseme = v }
            cueOpening += FaceChannels.SHARED_OPENING[v] * w
        }

        out.fill(0f, 0, channelNames.size)
        for (v in 1 until FaceChannels.VISEME_COUNT) { val c = visemeChannel[v]; if (c >= 0) out[c] = (blend[v] * gate).coerceIn(0f, 1f) }
        out[jawChannel] = (jawOpen - cueOpening).coerceIn(0f, 1f)
        blink = blinkAt(timeMs, seed)
        out[blinkLeft] = blink; out[blinkRight] = blink
    }

    private fun reset() { envelope = 0f; blend.fill(0f) }

    companion object {
        /** RMS below this counts as silence. */
        const val SILENCE_LEVEL = .025f
        /** Level at which viseme shapes reach full weight. */
        const val GATE_FULL_LEVEL = .12f
        /** Jaw gain (AvatarPoseReducer: dead zone 0,025, gain 1,5). */
        const val JAW_GAIN = 1.5f
        const val ATTACK_MS = 30f
        const val RELEASE_MS = 120f
        const val VISEME_CROSSFADE_MS = 70f
        const val BLINK_SLOT_MS = 4500L
        const val BLINK_JITTER_MS = 750L
        const val BREATH_PERIOD_MS = MotionPresets.IDLE_PERIOD_MS

        /** Start of the blink in slot [slot] (gaps between consecutive blinks: 3–6 s). */
        fun blinkStartMs(slot: Long, seed: Long): Long =
            slot * BLINK_SLOT_MS + Math.round((Hash.unit(seed, slot, 0xB11L) * 2f - 1f) * BLINK_JITTER_MS.toDouble())

        /** Blink weight 0..1 at [timeMs]: sin bump over [MotionPresets.BLINK_MS], stateless and seeded. */
        fun blinkAt(timeMs: Long, seed: Long): Float {
            val slot = Math.floorDiv(timeMs, BLINK_SLOT_MS)
            for (s in slot - 1..slot + 1) {
                val u = timeMs - blinkStartMs(s, seed)
                if (u in 0 until MotionPresets.BLINK_MS) return sin(PI.toFloat() * u / MotionPresets.BLINK_MS)
            }
            return 0f
        }

        /** Breathing −1..1, slow 4 s sine (same loop as the Muster C idle float). */
        fun breath(timeMs: Long): Float = sin(2f * PI.toFloat() * Math.floorMod(timeMs, BREATH_PERIOD_MS).toFloat() / BREATH_PERIOD_MS)
    }
}
