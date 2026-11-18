package at.heinz.deviceai.cognitive

/**
 * Timing of spoken conversation, taken from published measurements instead of fixed engineering delays.
 *
 * Sources (values as quoted in the papers; see docs/research/HUMAN-SPEECH-TIMING-20261117.md):
 * - Levinson & Torreira 2015, Frontiers in Psychology 6:731 – speaker transitions cluster around a gap of
 *   about 200 ms; 51–55 % of transitions take under 200 ms and 70–82 % under 500 ms; gaps of 600–700 ms or
 *   more are read as trouble or an unwelcome answer; a continuation by the same speaker follows about
 *   140 ms later than a speaker change; planning one new word takes about 600 ms.
 * - Campione & Véronis 2002, Speech Prosody – silent pauses are trimodal: brief (< 200 ms), medium
 *   (200–1000 ms) and long (> 1000 ms); long pauses occur only in spontaneous speech.
 * - Coupé et al. 2019, Science Advances 5(9) – read speech across 17 languages averages 6.63 syllables/s
 *   (SD 1.15); individual speakers range from about 4.3 to 9.1 syllables/s.
 *
 * What this object does NOT claim: the assistant cannot answer inside 200 ms while a local model needs
 * seconds. It therefore (a) signals the delay within the tolerated window instead of staying silent,
 * (b) places pauses of human length and human variability between spoken pieces, and (c) never inserts a
 * pause above one second inside its own reply, because such pauses belong to hesitation, not delivery.
 */
internal object HumanSpeechTiming {
    /** A silent gap from here on is heard as hesitation or reluctance (Levinson & Torreira 2015, section 2). */
    const val TROUBLE_GAP_MS = 600L
    /** The first audible sign of life after the owner's turn must start before [TROUBLE_GAP_MS]. */
    const val FIRST_CUE_DELAY_MS = 450L
    /** A long model turn gets one more short sign of life; never a third (it would become chatter). */
    const val SECOND_CUE_DELAY_MS = 7_000L
    const val MAX_CUES_PER_TURN = 2

    /** Medium pause band (200–1000 ms): sentence boundary inside one turn. */
    private const val SENTENCE_PAUSE_MS = 420L
    /** Upper part of the medium band: new paragraph or change of topic. */
    private const val PARAGRAPH_PAUSE_MS = 780L
    /** Lower edge of the medium band: enumeration items, short add-ons. */
    private const val CLAUSE_PAUSE_MS = 230L
    /** Pauses stay inside the medium band; above one second only spontaneous hesitation is found. */
    const val MIN_PAUSE_MS = 200L
    const val MAX_PAUSE_MS = 950L

    private val paragraphEnd = Regex("\\n\\s*\\n\\s*$|\\n\\s*$")
    private val sentenceEnd = Regex("[.!?…][\"”“’»)\\]]*\\s*$")
    private val questionEnd = Regex("\\?[\"”“’»)\\]]*\\s*$")

    /**
     * Silence between the spoken piece [finished] and [next] of the same reply.
     *
     * [variation] is any changing integer (for example the piece index plus a per-reply seed): people do not
     * repeat the same pause length, so the value moves by up to ±18 % around the base. The result is
     * deterministic for a given input, which keeps it testable and replayable.
     */
    fun pauseBetweenMs(finished: String, next: String, register: ConversationRegister, variation: Int): Long {
        if (next.isBlank()) return 0L
        val base = when {
            paragraphEnd.containsMatchIn(finished) -> PARAGRAPH_PAUSE_MS
            // After a question the speaker leaves room before going on.
            questionEnd.containsMatchIn(finished) -> SENTENCE_PAUSE_MS + 140L
            sentenceEnd.containsMatchIn(finished) -> SENTENCE_PAUSE_MS
            else -> CLAUSE_PAUSE_MS
        }
        val scaled = base * registerPauseFactor(register) / 100L
        // Deterministic spread in −18 … +18 %.
        val step = ((variation % SPREAD_STEPS) + SPREAD_STEPS) % SPREAD_STEPS
        val percent = 100L - SPREAD_PERCENT + step * (2L * SPREAD_PERCENT) / (SPREAD_STEPS - 1)
        return (scaled * percent / 100L).coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)
    }

    /**
     * Speech-rate factor relative to the app's base rate. People speak faster when joking and slower on
     * serious matters; the spread stays small (±6 %) so the voice remains one recognisable speaker and well
     * inside the 4.3–9.1 syllables/s range of real speakers.
     */
    fun rateFactor(register: ConversationRegister): Float = when (register) {
        ConversationRegister.PLAYFUL -> 1.05f
        ConversationRegister.CASUAL -> 1.0f
        ConversationRegister.TASK -> 1.0f
        ConversationRegister.SERIOUS -> 0.96f
        ConversationRegister.DISTRESSED -> 0.94f
    }

    /** True while silence after the owner's turn is still inside what people tolerate without a signal. */
    fun withinToleratedGap(elapsedMs: Long): Boolean = elapsedMs in 0 until TROUBLE_GAP_MS

    /** Elapsed time after which cue number [index] (0-based) is due, or null when no further cue is allowed. */
    fun cueDelayMs(index: Int): Long? = when (index) {
        0 -> FIRST_CUE_DELAY_MS
        1 -> SECOND_CUE_DELAY_MS
        else -> null
    }

    private fun registerPauseFactor(register: ConversationRegister): Long = when (register) {
        ConversationRegister.PLAYFUL -> 88L
        ConversationRegister.CASUAL, ConversationRegister.TASK -> 100L
        ConversationRegister.SERIOUS -> 112L
        ConversationRegister.DISTRESSED -> 120L
    }

    private const val SPREAD_PERCENT = 18L
    private const val SPREAD_STEPS = 7
}
