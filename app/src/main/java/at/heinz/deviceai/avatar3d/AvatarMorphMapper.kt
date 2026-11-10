package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarGestureIntent
import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.AvatarSentenceGesture
import at.heinz.deviceai.avatar.SpeechBoundary
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
    private val gazeBlink = GazeBlinkPolicy()
    /** Critically damped gaze states: eyes lead, head follows without overshoot. */
    private val headYawSpring = CriticalDampedSpring()
    private val headPitchSpring = CriticalDampedSpring()
    private val eyeYawSpring = CriticalDampedSpring()
    private val eyePitchSpring = CriticalDampedSpring()
    private var followSeconds = Float.NaN
    /** Smoothed real mouth opening while speaking; drives the speech accents. */
    private var speechAccent = 0f
    private var speechSeconds = Float.NaN
    /**
     * Weights 0..1 of the state-bound head motion (idle sway and breathing, speech accents, the work motion). They
     * follow the state with [LAYER_FADE_TAU_S] instead of switching in one frame, so the speaking head fades out
     * after the last word and the idle sway fades in (no pop of several degrees at a state change).
     */
    private var idleLayer = 0f
    private var speechLayer = 0f
    private var workLayer = 0f
    private var layerSeconds = Float.NaN
    /** Blink deferral while speaking, see [lid]. */
    private var blinkSeconds = Float.NaN
    private var lastScheduledBlink = 0f
    private var hideScheduledBlink = false
    private var deferredBlinkSince = Float.NaN
    private var ownBlinkOnset = Float.NaN

    init {
        require(targetNames.toSet().size == targetNames.size && indices.keys == AvatarGlbContract.TARGETS.toSet())
        require(cameraAzimuthDegrees.isFinite() && cameraAzimuthDegrees in -45.0..45.0)
    }

    fun update(pose: AvatarPose, seconds: Float, motion: Boolean,
               gazeBlinkEnabled: Boolean = false,
               eyeNeckCouplingEnabled: Boolean = true,
               sentenceGesturesEnabled: Boolean = true) {
        weights.fill(0f)
        fun weight(name: String, value: Float) { weights[indices.getValue(name)] = finite(value, 0f, 1f) }
        val staticWindow = !motion && (pose.gazeDirected || pose.gesture == AvatarGestureIntent.WINDOW_FOCUS)
        val gx = finite(if (staticWindow) pose.fallbackGazeX else pose.gazeX, -1f, 1f)
        val gy = finite(if (staticWindow) pose.fallbackGazeY else pose.gazeY, -1f, 1f)
        // Right-handed Y-up +Z front: positive yaw goes viewer right, negative pitch looks up.
        // Owner request (Fold7, 05.11.): turn and nod in a wide, human radius; eyes lead, head follows.
        val targetHeadYaw = gx * HEAD_YAW_DEGREES; val targetHeadPitch = -gy * HEAD_PITCH_DEGREES
        // Critically damped follow: eye spring reaches ~80 % in 55 ms; head spring in ~220 ms.
        // First frame, clock jumps and reduced motion snap. Repeated timestamps are strictly idempotent.
        val followDt = if (followSeconds.isFinite() && seconds.isFinite()) seconds - followSeconds else Float.NaN
        val snap = !motion || !followDt.isFinite() || followDt < 0f || followDt > 1f
        if (snap) {
            headYawSpring.snap(targetHeadYaw)
            headPitchSpring.snap(targetHeadPitch)
        } else {
            headYawSpring.step(targetHeadYaw, followDt, HEAD_SPRING_OMEGA)
            headPitchSpring.step(targetHeadPitch, followDt, HEAD_SPRING_OMEGA)
        }
        headYaw = headYawSpring.value
        headPitch = headPitchSpring.value

        // Eyes target the direct ocular share plus the head lag, then follow with their much faster spring.
        val targetEyeYaw = cameraAzimuthDegrees.toFloat() +
            (gx * EYE_YAW_DEGREES + (targetHeadYaw - headYaw))
                .coerceIn(-EYE_MAX_YAW_DEGREES, EYE_MAX_YAW_DEGREES)
        val targetEyePitch = (-gy * EYE_PITCH_DEGREES + (targetHeadPitch - headPitch))
            .coerceIn(-EYE_MAX_PITCH_DEGREES, EYE_MAX_PITCH_DEGREES)
        if (snap) {
            eyeYawSpring.snap(targetEyeYaw)
            eyePitchSpring.snap(targetEyePitch)
        } else {
            eyeYawSpring.step(targetEyeYaw, followDt, EYE_SPRING_OMEGA)
            eyePitchSpring.step(targetEyePitch, followDt, EYE_SPRING_OMEGA)
        }
        eyeYaw = eyeYawSpring.value
        eyePitch = eyePitchSpring.value
        followSeconds = if (motion) seconds else Float.NaN
        jawPitch = 0f
        when (pose.expression) {
            AvatarExpression.ATTENTIVE -> weight("BrowUp", .20f)
            AvatarExpression.CURIOUS -> weight("BrowUp", .32f)
            AvatarExpression.FOCUSED -> weight("BrowDown", .18f)
            AvatarExpression.CONCERNED -> weight("BrowDown", .28f)
            AvatarExpression.NEUTRAL -> Unit
        }
        if (!motion) {
            visemeBlend.fill(0f); lastSeconds = Float.NaN
            layerSeconds = Float.NaN; speechAccent = 0f; speechSeconds = Float.NaN; blinkSeconds = Float.NaN
            gazeBlink.sample(gx, gy, seconds, enabled = false)
            splitNeck(eyeNeckCouplingEnabled); return
        }
        idleMotion.update(seconds)
        val work = pose.state == AvatarState.THINKING || pose.state == AvatarState.RESEARCHING || pose.state == AvatarState.LEARNING
        val idleEyes = pose.state == AvatarState.IDLE && !pose.gazeObserved && !pose.gazeDirected && pose.confirmationProgress < 0f
        fadeLayers(idleEyes, pose.state == AvatarState.SPEAKING && !pose.gazeObserved && !pose.gazeDirected,
            work && !pose.gazeDirected, seconds)
        // Cosmetic idle motion, not observed gaze or invented speech/work. The existing view
        // owns the clock and stops it on pause/reduced motion. Actual task/observed gaze wins.
        // The eyes keep their saccades; the head part fades with the state and yields to the speech layer.
        if (idleEyes) { eyeYaw += idleMotion.eyeYaw; eyePitch += idleMotion.eyePitch }
        // Reading (owner brief 06.11.: the avatar watches the document while the model really processes it): the eyes
        // sweep the lines of the directed window – a slow pass to the right, a quick return, a little lower each line –
        // while the head stays on the window. Only for a real held work gaze (Director windowWorkStarted).
        if (pose.reading && seconds.isFinite()) {
            eyeYaw += readingYaw(seconds)
            eyePitch += READ_LINE_PITCH_DEGREES * (kotlin.math.floor(seconds / READ_LINE_S).toInt().mod(READ_LINES))
        }
        val idleHead = minOf(idleLayer, 1f - speechLayer)
        if (idleHead > 0f) {
            headYaw += idleMotion.headYaw * IDLE_HEAD_GAIN * idleHead; headPitch += idleMotion.headPitch * IDLE_HEAD_GAIN * idleHead
            // Calm breathing: a slow, small nod (idle only; never during observed gaze or speech).
            if (seconds.isFinite()) headPitch += idleHead * BREATH_PITCH_DEGREES *
                kotlin.math.sin(2.0 * Math.PI * seconds / BREATH_PERIOD_S).toFloat()
        }
        if (seconds.isFinite() && (workLayer > 0f || (work && !pose.gazeDirected))) {
            val cycle = 2.0 * Math.PI * seconds.toDouble()
            headPitch += workLayer * (.35 * kotlin.math.sin(cycle / 5.3)).toFloat()
            headYaw += workLayer * (.40 * kotlin.math.sin(cycle / 7.7)).toFloat()
            if (work && !pose.gazeDirected) eyeYaw += (.65 * kotlin.math.sin(cycle / 2.9)).toFloat()
        }
        // Speech accents (owner brief 05.11.): while the avatar really speaks, the head accents what it says – a small
        // nod following the smoothed real mouth opening (TTS word ranges via the director), a slow sway and a brow lift
        // on open syllables – so the face is not frozen around a moving jaw. It uses the reach the framing reserves for
        // idle motion (the idle head yields to it); an observed or directed gaze wins. After the last word the accent
        // decays and the layer fades out (owner brief 06.11.: no abrupt stop).
        if (seconds.isFinite() && (speechLayer > 0f || pose.state == AvatarState.SPEAKING)) {
            val open = if (pose.state == AvatarState.SPEAKING) finite(pose.jawOpen, 0f, 1f) else 0f
            val dt = if (speechSeconds.isFinite()) seconds - speechSeconds else Float.NaN
            speechAccent = if (!dt.isFinite() || dt < 0f || dt > 1f) open
                else speechAccent + (open - speechAccent) * (1f - kotlin.math.exp(-dt / SPEECH_ACCENT_TAU_S))
            speechSeconds = seconds
            headPitch += SPEECH_NOD_DEGREES * speechAccent * speechLayer
            headYaw += SPEECH_SWAY_DEGREES * speechLayer * kotlin.math.sin(2.0 * Math.PI * seconds / SPEECH_SWAY_PERIOD_S).toFloat()
            val brow = indices.getValue("BrowUp")
            weights[brow] = maxOf(weights[brow], finite(SPEECH_BROW * speechAccent * speechLayer, 0f, 1f))
        } else { speechAccent = 0f; speechSeconds = Float.NaN }
        // Sentence ends of the spoken text (Director: text + TTS word range, no audio inference): a statement closes
        // with a small settling nod and a soft rebound, a question lifts the brows and slightly the chin on its last
        // word. A directed gaze keeps the head; the brows still move.
        val sentenceEnd = AvatarSentenceGesture.published(
            pose.sentenceEnd, pose.sentenceEndProgress, sentenceGesturesEnabled)
        val sentence = pose.sentenceEndProgress
        if (sentenceEnd != null && sentence.isFinite() && sentence >= 0f && sentence <= 1f) when (sentenceEnd) {
            SpeechBoundary.QUESTION -> {
                val lift = questionLift(sentence)
                val brow = indices.getValue("BrowUp")
                weights[brow] = maxOf(weights[brow], QUESTION_BROW * lift)
                if (!pose.gazeDirected) headPitch -= QUESTION_CHIN_DEGREES * lift
            }
            SpeechBoundary.STATEMENT -> if (!pose.gazeDirected) headPitch += SETTLE_DEGREES * settle(sentence)
        }
        val progress = pose.confirmationProgress
        if (progress.isFinite() && progress in 0f..1f) {
            val wave = kotlin.math.sin(Math.PI * progress).toFloat()
            val nod = CONFIRMATION_PITCH_DEGREES * wave * wave
            headPitch += nod
            eyePitch = (eyePitch - nod).coerceIn(-EYE_MAX_PITCH_DEGREES, EYE_MAX_PITCH_DEGREES)
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
        val syllableOpen = pose.state == AvatarState.SPEAKING &&
            maxOf(jaw, renderedOpening) >= BLINK_DEFER_OPENING
        val scheduledLid = lid(idleMotion.blinkWeight, syllableOpen, seconds)
        val gazeLid = gazeBlink.sample(gx, gy, seconds,
            gazeBlinkEnabled && motion && pose.gazeDirected && !syllableOpen)
        val totalLid = maxOf(scheduledLid, gazeLid)
        weight("BlinkLeft", totalLid); weight("BlinkRight", totalLid)
        splitNeck(eyeNeckCouplingEnabled)
    }

    /** Diagnostic-only override after normal mapping; null keeps production blink untouched. */
    internal fun applyDiagnosticBlink(value: Float?) {
        if (value == null) return
        val blink = finite(value, 0f, 1f)
        weights[indices.getValue("BlinkLeft")] = blink
        weights[indices.getValue("BlinkRight")] = blink
    }

    private fun fadeLayers(idle: Boolean, speech: Boolean, work: Boolean, seconds: Float) {
        val idleTarget = if (idle) 1f else 0f
        val speechTarget = if (speech) 1f else 0f
        val workTarget = if (work) 1f else 0f
        val dt = if (layerSeconds.isFinite() && seconds.isFinite()) seconds - layerSeconds else Float.NaN
        // First frame, clock jumps and resumed motion snap, like the head follow.
        if (!dt.isFinite() || dt < 0f || dt > 1f) {
            idleLayer = idleTarget; speechLayer = speechTarget; workLayer = workTarget
        } else if (dt > 0f) {
            val a = 1f - kotlin.math.exp(-dt / LAYER_FADE_TAU_S)
            idleLayer = ease(idleLayer, idleTarget, a)
            speechLayer = ease(speechLayer, speechTarget, a)
            workLayer = ease(workLayer, workTarget, a)
        }
        layerSeconds = seconds
    }

    /** One time-based easing step; the last ten-thousandth snaps, so a layer really reaches 0 or 1. */
    private fun ease(value: Float, target: Float, amount: Float): Float {
        val next = value + (target - value) * amount
        return if (kotlin.math.abs(target - next) < 1e-4f) target else next
    }

    /**
     * Speech blinks land on word boundaries (owner brief 06.11.): a scheduled blink that would begin while the mouth is
     * open in a syllable ([openSyllable]: real jaw or cue opening) waits until the mouth closes at the next boundary
     * (word onset, M/B/P, pause, end of speech), at most [MAX_BLINK_DEFER_S], and then plays with the same lid shape.
     * A blink already under way always completes; outside speech the view clock's schedule is shown unchanged.
     */
    private fun lid(scheduled: Float, openSyllable: Boolean, seconds: Float): Float {
        val dt = if (blinkSeconds.isFinite() && seconds.isFinite()) seconds - blinkSeconds else Float.NaN
        if (!dt.isFinite() || dt < 0f || dt > 1f) {
            // First frame or clock jump: the schedule as it is; nothing is deferred across the gap.
            hideScheduledBlink = false; deferredBlinkSince = Float.NaN; ownBlinkOnset = Float.NaN
        } else if (dt > 0f) {
            if (lastScheduledBlink == 0f && scheduled > 0f) {
                if (openSyllable) {
                    hideScheduledBlink = true
                    if (deferredBlinkSince.isNaN()) deferredBlinkSince = seconds
                } else deferredBlinkSince = Float.NaN // This blink already lands on a boundary and replaces it.
            }
            if (hideScheduledBlink && scheduled == 0f) hideScheduledBlink = false
            if (!deferredBlinkSince.isNaN() && (!openSyllable || seconds - deferredBlinkSince >= MAX_BLINK_DEFER_S)) {
                ownBlinkOnset = seconds; deferredBlinkSince = Float.NaN
            }
            if (!ownBlinkOnset.isNaN() && seconds - ownBlinkOnset > AvatarIdleMotion.BLINK_S) ownBlinkOnset = Float.NaN
        }
        lastScheduledBlink = scheduled
        blinkSeconds = seconds
        val own = if (ownBlinkOnset.isNaN()) 0f else idleMotion.blinkAfterOnset(seconds - ownBlinkOnset)
        return maxOf(if (hideScheduledBlink) 0f else scheduled, own)
    }

    /** Question brows: up within about 180 ms on the last word, held past it, relaxed by the end of the gesture. */
    private fun questionLift(progress: Float) = minOf(smooth(progress / .15f), smooth((1f - progress) / .45f))

    /**
     * Statement settle: starts as the last word ends, dips once, rebounds softly (about a quarter of the dip) and comes
     * to rest, with zero velocity at both ends: sin²(πu)·(cos(πu) + 0.4), normalized to a peak of 1.
     */
    private fun settle(progress: Float): Float {
        val u = (progress - SETTLE_START) / (1f - SETTLE_START)
        if (u <= 0f || u >= 1f) return 0f
        val x = Math.PI * u
        val s = kotlin.math.sin(x)
        return (s * s * (kotlin.math.cos(x) + SETTLE_REBOUND) / SETTLE_PEAK).toFloat()
    }

    private fun smooth(x: Float): Float { val u = x.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

    private fun splitNeck(eyeNeckCouplingEnabled: Boolean) {
        headYaw = finite(headYaw, -MAX_HEAD_YAW_DEGREES, MAX_HEAD_YAW_DEGREES)
        headPitch = finite(headPitch, -MAX_HEAD_PITCH_DEGREES, MAX_HEAD_PITCH_DEGREES)
        if (eyeNeckCouplingEnabled) {
            neckYaw = headYaw * NECK_SHARE
            neckPitch = headPitch * NECK_SHARE
        } else {
            neckYaw = 0f
            neckPitch = 0f
        }
    }

    companion object {
        /** One line read: [READ_LINE_S] long, of which [READ_RETURN_SHARE] is the quick return sweep. */
        const val READ_LINE_S = 1.6f
        const val READ_RETURN_SHARE = .18f
        const val READ_SWEEP_DEGREES = 2.4f
        const val READ_LINE_PITCH_DEGREES = .45f
        const val READ_LINES = 4

        /** Eye yaw offset of the reading sweep at [seconds]: −A → +A slowly, then back quickly. */
        fun readingYaw(seconds: Float): Float {
            val phase = (seconds / READ_LINE_S).let { it - kotlin.math.floor(it) }
            val pass = 1f - READ_RETURN_SHARE
            return if (phase < pass) -READ_SWEEP_DEGREES + 2f * READ_SWEEP_DEGREES * phase / pass
                else READ_SWEEP_DEGREES - 2f * READ_SWEEP_DEGREES * (phase - pass) / READ_RETURN_SHARE
        }
        private const val CROSSFADE_S = .07f
        const val CONFIRMATION_PITCH_DEGREES = 3f
        /** Full gaze deflection (|gaze| = 1) of the head bone and the eyes, in degrees. */
        const val HEAD_YAW_DEGREES = 24f
        const val HEAD_PITCH_DEGREES = 12f
        const val EYE_YAW_DEGREES = 12f
        const val EYE_PITCH_DEGREES = 8f
        /** Inner jaw bone rotation at full mouth opening. */
        const val JAW_BONE_DEGREES = 15f
        /** Cosmetic idle head sway gain (idle only, never during observed gaze or speech). */
        const val IDLE_HEAD_GAIN = 2.5f
        /** Critically damped natural frequencies: step response reaches ~80 % at 55 ms / 220 ms. */
        const val EYE_SPRING_OMEGA = 54.44f
        const val HEAD_SPRING_OMEGA = 13.61f
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
        /** Speech accents stay inside the idle reach reserved above (speech and idle motion never overlap). */
        const val SPEECH_NOD_DEGREES = .6f * (MAX_HEAD_PITCH_DEGREES - HEAD_PITCH_DEGREES)
        const val SPEECH_SWAY_DEGREES = .5f * (MAX_HEAD_YAW_DEGREES - HEAD_YAW_DEGREES)
        const val SPEECH_SWAY_PERIOD_S = 4.1
        const val SPEECH_ACCENT_TAU_S = .12f
        const val SPEECH_BROW = .14f
        /** State-bound head layers reach 95 % of a change after about 1.2 s. */
        const val LAYER_FADE_TAU_S = .4f
        /** Statement settle: the rest of the reserved reach next to the speech nod, so both together stay inside it. */
        const val SETTLE_DEGREES = .4f * (MAX_HEAD_PITCH_DEGREES - HEAD_PITCH_DEGREES)
        private const val SETTLE_START = .22f
        private const val SETTLE_REBOUND = .4
        /** Maximum of sin²x·(cos x + 0.4) on [0, π]. */
        private const val SETTLE_PEAK = .678035
        const val QUESTION_BROW = .38f
        const val QUESTION_CHIN_DEGREES = .2f * (MAX_HEAD_PITCH_DEGREES - HEAD_PITCH_DEGREES)
        /** Visible mouth opening (jaw or authored cue opening) that counts as an open syllable for blinking. */
        const val BLINK_DEFER_OPENING = .35f
        const val MAX_BLINK_DEFER_S = .45f
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
