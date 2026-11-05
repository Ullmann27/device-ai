package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarGestureIntent
import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme

/** Presentation mapping only. No state derivation, speech clock, listener or retained text. */
class AvatarMorphMapper(targetNames: List<String>, private val cameraAzimuthDegrees: Double = 0.0) {
    private val indices = targetNames.withIndex().associate { it.value to it.index }
    val weights = FloatArray(targetNames.size)
    /** Total head orientation (degrees). The renderer splits it into the Neck and Head joints, see [neckYaw]. */
    var headYaw = 0f; private set
    var headPitch = 0f; private set
    /**
     * Part of [headYaw]/[headPitch] carried by the Neck joint; the Head joint carries the remainder. The packaged
     * face skin blends Neck→Head inside the chin/lower-lip band, so turning only the Head joint stretched that band
     * (measured edge strain up to 133 %) and the jaw looked detached. Splitting the turn keeps the face orientation.
     */
    var neckYaw = 0f; private set
    var neckPitch = 0f; private set
    var eyeYaw = 0f; private set
    var eyePitch = 0f; private set
    var jawPitch = 0f; private set
    /** Crossfaded viseme weights (VIDEO-SPEC: syllable-synchronous O/A; 70 ms crossfade between visemes). */
    private val visemeBlend = FloatArray(Viseme.entries.size)
    private var lastSeconds = Float.NaN
    private val idleMotion = AvatarIdleMotion()
    /** Damped head state behind the instantaneous gaze target (eyes lead, head follows). */
    private var followedYaw = 0f
    private var followedPitch = 0f
    private var followSeconds = Float.NaN

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
        // Owner request (Fold7, 05.11.): turn and nod in a wide, human radius; eyes lead, head follows.
        val targetHeadYaw = gx * HEAD_YAW_DEGREES; val targetHeadPitch = -gy * HEAD_PITCH_DEGREES
        // Head follows with a first-order lag; the first frame, clock jumps and reduced motion snap.
        val followDt = if (followSeconds.isFinite() && seconds.isFinite()) seconds - followSeconds else Float.NaN
        if (!motion || !followDt.isFinite() || followDt < 0f || followDt > 1f) {
            followedYaw = targetHeadYaw; followedPitch = targetHeadPitch
        } else if (followDt > 0f) {
            val a = 1f - kotlin.math.exp(-followDt / HEAD_FOLLOW_TAU_S)
            followedYaw += (targetHeadYaw - followedYaw) * a; followedPitch += (targetHeadPitch - followedPitch) * a
        }
        followSeconds = if (motion) seconds else Float.NaN
        headYaw = followedYaw; headPitch = followedPitch
        // Eyes lead: they hold the full gaze target while the head is still catching up.
        // Written as eye share + remaining head lag, so a settled head yields the exact eye share.
        eyeYaw = cameraAzimuthDegrees.toFloat() +
            (gx * EYE_YAW_DEGREES + (targetHeadYaw - followedYaw)).coerceIn(-EYE_MAX_YAW_DEGREES, EYE_MAX_YAW_DEGREES)
        eyePitch = (-gy * EYE_PITCH_DEGREES + (targetHeadPitch - followedPitch))
            .coerceIn(-EYE_MAX_PITCH_DEGREES, EYE_MAX_PITCH_DEGREES)
        jawPitch = 0f
        when (pose.expression) {
            AvatarExpression.ATTENTIVE -> weight("BrowUp", .20f)
            AvatarExpression.CURIOUS -> weight("BrowUp", .32f)
            AvatarExpression.FOCUSED -> weight("BrowDown", .18f)
            AvatarExpression.CONCERNED -> weight("BrowDown", .28f)
            AvatarExpression.NEUTRAL -> Unit
        }
        if (!motion) { visemeBlend.fill(0f); lastSeconds = Float.NaN; splitNeck(); return }
        idleMotion.update(seconds)
        // Cosmetic idle motion, not observed gaze or invented speech/work. The existing view
        // owns the clock and stops it on pause/reduced motion. Actual task/observed gaze wins.
        if (pose.state == AvatarState.IDLE && !pose.gazeObserved) {
            headYaw += idleMotion.headYaw * IDLE_HEAD_GAIN; headPitch += idleMotion.headPitch * IDLE_HEAD_GAIN
            eyeYaw += idleMotion.eyeYaw; eyePitch += idleMotion.eyePitch
            // Calm breathing: a slow, small nod (idle only; never during observed gaze or speech).
            if (seconds.isFinite()) headPitch += BREATH_PITCH_DEGREES *
                kotlin.math.sin(2.0 * Math.PI * seconds / BREATH_PERIOD_S).toFloat()
        }
        // Long-running work stays visually alive without inventing task progress.
        if (seconds.isFinite() && (pose.state == AvatarState.THINKING ||
                pose.state == AvatarState.RESEARCHING || pose.state == AvatarState.LEARNING)) {
            val cycle = 2.0 * Math.PI * seconds.toDouble()
            headPitch += (.35 * kotlin.math.sin(cycle / 5.3)).toFloat()
            headYaw += (.40 * kotlin.math.sin(cycle / 7.7)).toFloat()
            eyeYaw += (.65 * kotlin.math.sin(cycle / 2.9)).toFloat()
        }
        // Gesture motion reuses the renderer clock and never drives mouth morphs.
        val progress = finite(pose.gestureProgress, 0f, 1f)
        when (pose.gesture) {
            AvatarGestureIntent.CONFIRM_NOD -> headPitch -= 4.5f *
                kotlin.math.sin(Math.PI * progress).toFloat()
            AvatarGestureIntent.THINK_MICRO -> {
                headPitch += 1.2f * kotlin.math.sin(Math.PI * progress).toFloat()
                headYaw += 1.4f * kotlin.math.sin(2.0 * Math.PI * progress).toFloat()
            }
            AvatarGestureIntent.WINDOW_FOCUS, null -> Unit
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
        jawPitch = maxOf(jaw, renderedOpening) * JAW_BONE_DEGREES
        weight("BlinkLeft", idleMotion.blinkWeight); weight("BlinkRight", idleMotion.blinkWeight)
        splitNeck()
    }

    private fun splitNeck() {
        neckYaw = headYaw * NECK_SHARE
        neckPitch = headPitch * NECK_SHARE
    }

    companion object {
        private const val CROSSFADE_S = .07f
        /** Full gaze deflection (|gaze| = 1) of the head bone and the eyes, in degrees. */
        const val HEAD_YAW_DEGREES = 24f
        const val HEAD_PITCH_DEGREES = 12f
        const val EYE_YAW_DEGREES = 12f
        const val EYE_PITCH_DEGREES = 8f
        /** Inner jaw bone rotation at full mouth opening. */
        const val JAW_BONE_DEGREES = 15f
        /** Cosmetic idle head sway gain (idle only, never during observed gaze or speech). */
        const val IDLE_HEAD_GAIN = 2.5f
        /** Head follow time constant: eyes reach the target at once, the head ~95 % after 3τ. */
        const val HEAD_FOLLOW_TAU_S = .18f
        /** Human oculomotor range used while the head is still turning. */
        const val EYE_MAX_YAW_DEGREES = 30f
        const val EYE_MAX_PITCH_DEGREES = 20f
        const val BREATH_PITCH_DEGREES = .5f
        const val BREATH_PERIOD_S = 4.6
        /**
         * Share of the head turn carried by the Neck joint. Measured on the packaged GLB (linear-blend skinning,
         * renderer rotation formula, poses up to the full range): 0.65 balances the neck band (y .12–.20 m) and the
         * chin band (y .20–.27 m); worst-pose p99 edge strain falls from 95 % (Head only) to about 30 %.
         */
        const val NECK_SHARE = .65f
        /** Reachable total head rotation: full gaze plus idle sway and breathing. The camera framing reserves it. */
        const val MAX_HEAD_YAW_DEGREES = HEAD_YAW_DEGREES + IDLE_HEAD_GAIN * AvatarIdleMotion.HEAD_YAW_AMPLITUDE
        const val MAX_HEAD_PITCH_DEGREES = HEAD_PITCH_DEGREES + IDLE_HEAD_GAIN * AvatarIdleMotion.HEAD_PITCH_AMPLITUDE +
            BREATH_PITCH_DEGREES
    }

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
