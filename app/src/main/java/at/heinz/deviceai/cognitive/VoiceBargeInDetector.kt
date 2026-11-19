package at.heinz.deviceai.cognitive

/**
 * Level-only voice barge-in detection while the assistant is speaking.
 *
 * The owner must be able to interrupt a spoken reply by simply talking ("Stopp, erklär nur den
 * zweiten."). While the assistant speaks, a monitor feeds this detector one microphone level per
 * audio frame together with the level of the assistant's own playback. The detector answers
 * [Decision.BARGE_IN] exactly once per armed period, when the microphone carries sustained sound
 * that neither the background noise nor the echo of the assistant's own voice explains.
 *
 * Pure state machine without Android dependencies, deterministic, bounded state, no allocation
 * per frame. It never sees audio samples, only one normalised RMS level per frame.
 *
 * Scope and honesty: this is a level heuristic, not speech recognition and not speaker
 * verification. It cannot tell the owner's voice from another person talking, and an abrupt,
 * sustained loud sound that appears in the middle of a playback (a vacuum cleaner switching on)
 * looks exactly like speech and triggers. The price of such a false trigger is that the assistant
 * stops talking and listens. All thresholds are starting values that need calibration on the real
 * device (speaker volume, microphone gain and the quality of the platform echo canceller differ
 * per device and per audio route).
 */
class VoiceBargeInDetector {
    enum class Decision { NONE, BARGE_IN }

    /** True between [arm] and the trigger, [disarm] or [reset]. */
    @Volatile var armed: Boolean = false
        private set

    /** Current estimate of the non-speech microphone level (0..1); diagnostics and calibration. */
    @Volatile var noiseFloor: Float = 0f
        private set

    /**
     * Estimated ratio "echo level at the microphone / playback level" for the current playback.
     * Holds [ECHO_COUPLING_MAX] (assume the worst) until [echoCouplingLearned] is true.
     */
    @Volatile var echoCoupling: Float = ECHO_COUPLING_MAX
        private set

    /** True once the coupling of the current playback has been measured. */
    @Volatile var echoCouplingLearned: Boolean = false
        private set

    private var floorSeeded = false
    private var armedMs = 0
    private var learnedMs = 0
    private var micPeak = 0f
    private var playbackPeak = 0f
    private var echoReference = 0f
    private var referenceHoldMs = 0

    // Sliding window of the current speech candidate: one entry per frame, +ms for a qualifying
    // frame and -ms for a tolerated dropout. Preallocated; every entry covers at least 1 ms.
    private val window = IntArray(SPEECH_WINDOW_MS + 1)
    private var windowHead = 0
    private var windowSize = 0
    private var windowMs = 0
    private var qualifiedMs = 0
    private var gapMs = 0

    /**
     * Starts a new armed period; call when a playback of the assistant starts. The noise floor of
     * the previous period is kept as a prior (and re-seeded during the guard interval), the echo
     * coupling is measured again because the media volume or audio route may have changed.
     */
    @Synchronized fun arm() {
        armed = true
        armedMs = 0
        clearCandidate()
        clearEchoModel()
    }

    /** Stops evaluating frames; learned state is kept. Frames are ignored until [arm]. */
    @Synchronized fun disarm() {
        armed = false
        clearCandidate()
    }

    /** Disarms and forgets everything that was learned (noise floor and echo coupling). */
    @Synchronized fun reset() {
        armed = false
        armedMs = 0
        noiseFloor = 0f
        floorSeeded = false
        clearCandidate()
        clearEchoModel()
    }

    /**
     * Consumes one microphone frame.
     *
     * @param rms normalised RMS level of the microphone frame, 0..1
     * @param playbackLevel normalised RMS level of the assistant's own audio that is audible now, 0..1.
     *   A source that reports 0 while audio is audible disables the echo protection.
     * @param frameMs duration of the frame in milliseconds, 1..[MAX_FRAME_MS]
     * @return [Decision.BARGE_IN] exactly once per armed period, otherwise [Decision.NONE].
     *   Invalid input and frames while disarmed change nothing and return [Decision.NONE].
     */
    @Synchronized fun onFrame(rms: Float, playbackLevel: Float, frameMs: Int): Decision {
        if (!armed) return Decision.NONE
        // The comparisons are written so that NaN fails them.
        if (!(rms >= 0f && rms <= 1f) || !(playbackLevel >= 0f && playbackLevel <= 1f)) return Decision.NONE
        if (frameMs <= 0 || frameMs > MAX_FRAME_MS) return Decision.NONE

        updateEchoReference(playbackLevel, frameMs)
        val learning = !echoCouplingLearned && echoReference >= PLAYBACK_ACTIVE_LEVEL
        if (learning) learnEchoCoupling(rms, frameMs)

        if (armedMs < GUARD_INTERVAL_MS) {
            armedMs += frameMs
            seedNoiseFloor(rms, frameMs)
            return Decision.NONE
        }

        val noiseThreshold = maxOf(MIN_SPEECH_RMS, noiseFloor * NOISE_MARGIN_RATIO)
        val echoThreshold = echoReference * echoCoupling * ECHO_MARGIN_RATIO
        // A frame that is still used to measure the echo is by definition not the owner.
        val qualifies = !learning && rms > noiseThreshold && rms > echoThreshold

        if (qualifies) {
            gapMs = 0
            push(frameMs)
            if (qualifiedMs >= SUSTAINED_SPEECH_MS) {
                armed = false
                clearCandidate()
                return Decision.BARGE_IN
            }
        } else {
            adaptNoiseFloor(rms, frameMs)
            if (windowSize > 0) {
                gapMs += frameMs
                if (gapMs > DROPOUT_TOLERANCE_MS) clearCandidate() else push(-frameMs)
            }
        }
        return Decision.NONE
    }

    /** Peak-hold of the playback level so a delayed or reverberating echo is still explained. */
    private fun updateEchoReference(playbackLevel: Float, frameMs: Int) {
        if (playbackLevel >= echoReference) {
            echoReference = playbackLevel
            referenceHoldMs = 0
        } else if (referenceHoldMs < ECHO_TAIL_HOLD_MS) {
            referenceHoldMs += frameMs
        } else {
            echoReference += (playbackLevel - echoReference) * frameMs / (frameMs + ECHO_TAIL_RELEASE_MS)
        }
    }

    /**
     * Peak microphone level over peak playback level, without subtracting the noise. Under the
     * stated assumption (owner silent, echo roughly proportional to the playback level) this
     * never under-estimates the coupling; background noise only inflates it, which makes the
     * detector harder to trigger, never easier.
     */
    private fun learnEchoCoupling(rms: Float, frameMs: Int) {
        if (rms > micPeak) micPeak = rms
        // The playback peak is taken from the learning window only; the microphone peak is
        // collected one echo tail longer so the delayed echo of the last peak is included.
        if (learnedMs < ECHO_LEARN_MS && echoReference > playbackPeak) playbackPeak = echoReference
        learnedMs += frameMs
        if (learnedMs >= ECHO_LEARN_MS + ECHO_TAIL_HOLD_MS) {
            echoCoupling = (micPeak / playbackPeak).coerceIn(ECHO_COUPLING_MIN, ECHO_COUPLING_MAX)
            echoCouplingLearned = true
        }
    }

    private fun seedNoiseFloor(rms: Float, frameMs: Int) {
        if (!floorSeeded) {
            noiseFloor = rms
            floorSeeded = true
        } else {
            noiseFloor += (rms - noiseFloor) * frameMs / (frameMs + NOISE_SEED_TAU_MS)
        }
    }

    private fun adaptNoiseFloor(rms: Float, frameMs: Int) {
        val tau = if (rms > noiseFloor) NOISE_RISE_TAU_MS else NOISE_FALL_TAU_MS
        noiseFloor += (rms - noiseFloor) * frameMs / (frameMs + tau)
    }

    private fun push(signedMs: Int) {
        window[(windowHead + windowSize) % window.size] = signedMs
        windowSize++
        if (signedMs > 0) { windowMs += signedMs; qualifiedMs += signedMs } else windowMs -= signedMs
        while (windowMs > SPEECH_WINDOW_MS) {
            val oldest = window[windowHead]
            windowHead = (windowHead + 1) % window.size
            windowSize--
            if (oldest > 0) { windowMs -= oldest; qualifiedMs -= oldest } else windowMs += oldest
        }
    }

    private fun clearCandidate() {
        windowHead = 0
        windowSize = 0
        windowMs = 0
        qualifiedMs = 0
        gapMs = 0
    }

    private fun clearEchoModel() {
        echoReference = 0f
        referenceHoldMs = 0
        learnedMs = 0
        micPeak = 0f
        playbackPeak = 0f
        echoCoupling = ECHO_COUPLING_MAX
        echoCouplingLearned = false
    }

    companion object {
        /**
         * Sustained speech: this much qualifying sound is needed before a barge-in. A cough, a
         * door slam or a single short syllable is shorter and must not stop the assistant.
         */
        const val SUSTAINED_SPEECH_MS = 240

        /**
         * The [SUSTAINED_SPEECH_MS] must fall into a window of this length, so loose fragments
         * that are spread over a longer time never add up to a trigger.
         */
        const val SPEECH_WINDOW_MS = 400

        /**
         * A gap of at most this length inside a word (stop consonant, breath) is tolerated and
         * keeps the candidate alive; a longer gap ends the candidate and the count restarts.
         */
        const val DROPOUT_TOLERANCE_MS = 60

        /**
         * No trigger during the first milliseconds of an armed period: the start of a playback
         * produces clicks, the echo canceller has not converged and the audio route is still
         * settling. The noise floor is seeded quickly in this interval instead.
         */
        const val GUARD_INTERVAL_MS = 300

        /**
         * Lowest microphone level that can count as speech at all (about -38 dBFS). Keeps the
         * self-noise of a quiet room from qualifying when the noise floor is close to zero.
         */
        const val MIN_SPEECH_RMS = 0.012f

        /**
         * Speech must exceed the noise floor by this factor (about 8 dB). The floor tracks the
         * lower envelope of a fluctuating noise (car cabin, fan), so the margin has to cover the
         * frame-to-frame variation of that noise as well.
         */
        const val NOISE_MARGIN_RATIO = 2.5f

        /**
         * Time constant of the slow upward adaptation of the noise floor outside of speech
         * candidates. Slow, so that the echo of single loud syllables and short events do not
         * raise the floor, yet a gradually louder environment is followed.
         */
        const val NOISE_RISE_TAU_MS = 1_500

        /**
         * Time constant of the downward adaptation. Faster than the rise, so the floor returns to
         * the real background in the pauses of the playback and the detector regains sensitivity
         * quickly when the environment becomes quieter.
         */
        const val NOISE_FALL_TAU_MS = 300

        /**
         * Fast time constant used only inside the guard interval, where the owner is assumed
         * silent: a playback that starts in a louder place than the previous one must not
         * trigger from a stale, too low noise floor.
         */
        const val NOISE_SEED_TAU_MS = 80

        /**
         * Playback below this level counts as silent: it is not used to measure the echo and its
         * time does not count as learning time.
         */
        const val PLAYBACK_ACTIVE_LEVEL = 0.02f

        /**
         * Active playback time at the start of each armed period that is used to measure the echo
         * coupling. The owner is assumed silent in this time; if the owner already talks, the
         * coupling is over-estimated and the barge-in merely becomes harder for this playback.
         */
        const val ECHO_LEARN_MS = 400

        /**
         * Lower clamp of the learned coupling. Even with headphones or a perfect echo canceller a
         * small residual is assumed, so one quiet measurement cannot remove the echo protection.
         */
        const val ECHO_COUPLING_MIN = 0.05f

        /**
         * Upper clamp and the value before the coupling is measured: the echo at the microphone is
         * assumed to be at most as loud as the playback level itself. A larger measured value
         * means the owner talked or something loud happened during learning.
         */
        const val ECHO_COUPLING_MAX = 1.0f

        /**
         * Speech must exceed the modelled residual echo (playback level x coupling) by this
         * factor (6 dB). Covers a speaker that couples some sounds more strongly than the average
         * and the non-linear distortion of a small loudspeaker at high volume.
         */
        const val ECHO_MARGIN_RATIO = 2.0f

        /**
         * The playback level is held this long after it drops, because the echo arrives late at
         * the microphone (output latency, capture latency, room reverberation).
         */
        const val ECHO_TAIL_HOLD_MS = 100

        /** Release time constant of the held playback level after [ECHO_TAIL_HOLD_MS]. */
        const val ECHO_TAIL_RELEASE_MS = 100

        /**
         * Longest accepted frame. One level averaged over a longer time cannot resolve the
         * timing rules above, so such a frame is ignored like any other invalid input.
         */
        const val MAX_FRAME_MS = 100
    }
}
