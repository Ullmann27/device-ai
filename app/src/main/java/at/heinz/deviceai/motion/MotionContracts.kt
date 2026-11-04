package at.heinz.deviceai.motion

/*
 * Pure-Kotlin motion contracts (owner of the interface: Jarvis Kern; implementation: Jarvis Mathematik).
 * No Android imports. The cockpit timeline uses an implementation when one is registered in [MotionLibrary]
 * and falls back to the current JarvisMotion defaults otherwise.
 */

fun interface Easing { fun at(t: Float): Float }

interface SpringCurve {
    fun valueAt(tSec: Float): Float
    val settleSec: Float
}

fun interface Stagger { fun delaySec(index: Int, count: Int): Float }

/**
 * Visemes/blendshapes, blink, breathing. Writes into [out] (size = channelNames.size) in the
 * FilamentAvatarRenderer (#478) 21-blendshape order; must not allocate.
 */
interface FaceWeightsSource {
    val channelNames: List<String>
    fun weightsAt(timeMs: Long, out: FloatArray)
}

data class MotionTrack(val key: String, val startMs: Long, val durationMs: Long, val easing: Easing)

/**
 * Agreed shape (interface only, Mathematik ships the class): evaluates every track at [tMs] into [out]
 * (index = track order, value 0..1 eased progress); must not allocate.
 */
interface MotionTimeline {
    val tracks: List<MotionTrack>
    val totalMs: Long
    fun evaluate(tMs: Long, out: FloatArray)
}

/** 0..1 normalised RMS of the real TTS/mic output; silence = 0. Drives waveform AND lip-sync. */
fun interface AudioLevelSource { fun levelAt(timeMs: Long): Float }

/** Optional: viseme index only if the TTS engine provides phonemes; null = none. */
fun interface VisemeHintSource { fun visemeAt(timeMs: Long): Int? }

/**
 * Registration point; null = cockpit defaults. Defaults are the Mathematik presets (approved by Kern).
 *
 * [audioLevel] stays a constant 0 (silence → mouth at rest, no invented signal) until Kern installs the real
 * TTS/mic RMS source; [visemeHint] stays null until the TTS engine provides phonemes. [faceWeights] reads both
 * properties on every frame, so whatever Kern assigns later drives the face without re-registering it.
 */
object MotionLibrary {
    /** Production instance set by the app from TtsHologramSignal (clock: SystemClock.elapsedRealtime). */
    @Volatile var audioLevel: AudioLevelSource = AudioLevelSource { 0f }
    @Volatile var visemeHint: VisemeHintSource? = null
    @Volatile var easing: Easing? = MotionPresets.flyInEasing
    @Volatile var spring: SpringCurve? = MotionPresets.widgetSpring
    @Volatile var stagger: Stagger? = MotionPresets.panelStagger
    @Volatile var faceWeights: FaceWeightsSource? = LipSyncFaceSource(
        AudioLevelSource { audioLevel.levelAt(it) },
        VisemeHintSource { visemeHint?.visemeAt(it) },
    )
}
