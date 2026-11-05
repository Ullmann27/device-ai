package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme

/** Presentation mapping only. No state derivation, speech clock, listener or retained text. */
class AvatarMorphMapper(targetNames: List<String>, private val cameraAzimuthDegrees: Double = 0.0) {
    private val indices = targetNames.withIndex().associate { it.value to it.index }
    val weights = FloatArray(targetNames.size)
    var headYaw = 0f; private set
    var headPitch = 0f; private set
    var eyeYaw = 0f; private set
    var eyePitch = 0f; private set
    var jawPitch = 0f; private set
    /** Crossfaded viseme weights (VIDEO-SPEC: syllable-synchronous O/A; 70 ms crossfade between visemes). */
    private val visemeBlend = FloatArray(Viseme.entries.size)
    private var lastSeconds = Float.NaN
    private val idleMotion = AvatarIdleMotion()

    init {
        require(targetNames.toSet().size == targetNames.size && indices.keys == AvatarGlbContract.TARGETS.toSet())
        require(cameraAzimuthDegrees.isFinite() && cameraAzimuthDegrees in -45.0..45.0)
    }

    fun update(pose: AvatarPose, seconds: Float, motion: Boolean) {
        weights.fill(0f)
        fun weight(name: String, value: Float) { weights[indices.getValue(name)] = finite(value, 0f, 1f) }
        val gx = finite(pose.gazeX, -1f, 1f)
        val gy = finite(pose.gazeY, -1f, 1f)
        // Right-handed Y-up +Z front: positive yaw goes viewer right, negative pitch looks up.
        headYaw = gx * 8f; headPitch = -gy * 5f
        eyeYaw = cameraAzimuthDegrees.toFloat() + gx * 17f; eyePitch = -gy * 10f
        jawPitch = 0f
        when (pose.expression) {
            AvatarExpression.ATTENTIVE -> weight("BrowUp", .20f)
            AvatarExpression.CURIOUS -> weight("BrowUp", .32f)
            AvatarExpression.FOCUSED -> weight("BrowDown", .18f)
            AvatarExpression.CONCERNED -> weight("BrowDown", .28f)
            AvatarExpression.NEUTRAL -> Unit
        }
        if (!motion) { visemeBlend.fill(0f); lastSeconds = Float.NaN; return }
        idleMotion.update(seconds)
        // Cosmetic idle motion, not observed gaze or invented speech/work. The existing view
        // owns the clock and stops it on pause/reduced motion. Actual task/observed gaze wins.
        if (pose.state == AvatarState.IDLE && !pose.gazeObserved) {
            headYaw += idleMotion.headYaw; headPitch += idleMotion.headPitch
            eyeYaw += idleMotion.eyeYaw; eyePitch += idleMotion.eyePitch
        }
        val cueWeight = if (pose.viseme == Viseme.SIL) 0f else finite(pose.visemeWeight, 0f, 1f)
        val bilabialClosure = pose.viseme == Viseme.PP && cueWeight > 0f
        // M/B/P require a closed lip seal even while the measured audio remains loud.
        // An outgoing vowel contains authored opening, so its morph must also retire now.
        val jaw = if (bilabialClosure) 0f else finite(pose.jawOpen, 0f, 1f)
        // The existing reducer expires stale cues/audio and resets interrupted utterances.
        // Linear crossfade over CROSSFADE_S toward the current cue; first frame / clock jump snaps.
        val dt = if (lastSeconds.isFinite() && seconds.isFinite()) seconds - lastSeconds else Float.NaN
        lastSeconds = seconds
        // Crossfade only between two active cues; silence/interruption closes at once (reducer owns stop).
        // Repeated render times are idempotent. A reset/wrapped clock initializes a new epoch.
        if (pose.viseme == Viseme.SIL || cueWeight <= 0f) visemeBlend.fill(0f)
        if (bilabialClosure) {
            visemeBlend.fill(0f)
            visemeBlend[Viseme.PP.ordinal] = cueWeight
        }
        val step = when {
            dt == 0f -> 0f
            dt.isFinite() && dt > 0f && dt <= 1f -> (dt / CROSSFADE_S).coerceIn(0f, 1f)
            else -> 1f
        }
        var renderedOpening = 0f
        for (v in Viseme.entries) {
            val target = if (v == pose.viseme && v != Viseme.SIL) cueWeight else 0f
            val cur = visemeBlend[v.ordinal]
            visemeBlend[v.ordinal] = if (target > cur) minOf(target, cur + step) else maxOf(target, cur - step)
            if (v != Viseme.SIL && visemeBlend[v.ordinal] > 0f) weight(v.name, visemeBlend[v.ordinal])
            renderedOpening += sharedOpeningRatio(v) * visemeBlend[v.ordinal]
        }
        // The pinned CC0 recipes already include mouth-open inside the viseme targets.
        // Compensate the visible blend, including outgoing cues, once; preserve lip shapes.
        weight("JawOpen", (jaw - renderedOpening).coerceAtLeast(0f))
        jawPitch = maxOf(jaw, renderedOpening) * 12f
        weight("BlinkLeft", idleMotion.blinkWeight); weight("BlinkRight", idleMotion.blinkWeight)
    }

    private companion object { const val CROSSFADE_S = .07f }

    private fun finite(value: Float, min: Float, max: Float) = if (value.isFinite()) value.coerceIn(min, max) else 0f

    /** PR404 build_bust.py at 0862cfc8: authored mouth-open coefficient / JawOpen's .8.
     * Bound to the packaged asset family; a different morph recipe requires a new review. */
    private fun sharedOpeningRatio(viseme: Viseme): Float = when (viseme) {
        Viseme.SIL, Viseme.PP, Viseme.SS -> 0f
        Viseme.FF -> .06f / .8f
        Viseme.TH -> .18f / .8f
        Viseme.DD -> .20f / .8f
        Viseme.KK -> .30f / .8f
        Viseme.CH -> .15f / .8f
        Viseme.NN -> .11f / .8f
        Viseme.RR -> .22f / .8f
        Viseme.AA -> .65f / .8f
        Viseme.E -> .23f / .8f
        Viseme.I, Viseme.U -> .10f / .8f
        Viseme.O -> .36f / .8f
    }
}
