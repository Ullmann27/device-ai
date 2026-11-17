package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Is a recognized utterance audibly unfinished?
 *
 * The recognizer ends a turn after a fixed silence. Research on conversation timing shows that a fixed
 * silence threshold cannot tell a thinking pause from the end of a turn (see
 * docs/research/HUMAN-SPEECH-TIMING-20261117.md): people pause for more than a second in the middle of
 * spontaneous speech. When the words themselves show that more must follow – a trailing „weil“, „und“,
 * „dass“, a hesitation sound, an open comma – the owner is still talking, and sending the fragment would
 * make the assistant answer half a request.
 *
 * Deliberately narrow, because German puts verb particles and pronouns at the end of complete sentences:
 * „Mach das Licht an.“, „Ruf Anna an.“, „Was ist das?“, „Das passt so, oder?“ are complete. Only endings
 * that cannot close a German sentence count. Text analysis only; it grants and starts nothing.
 */
internal object OwnerTurnCompletion {
    /** Conjunctions that open a clause. „oder“ is excluded: „…, oder?“ is a complete tag question. */
    private val openConjunctions = setOf("und", "aber", "weil", "dass", "daß", "wenn", "ob", "sondern", "denn",
        "sowie", "damit", "obwohl", "falls", "bevor", "nachdem", "während", "sodass", "indem", "sobald", "solange",
        "beziehungsweise", "nämlich")
    /** Prepositions that are never a separable verb particle („an“, „auf“, „aus“, „zu“, „mit“ … are, so they are absent). */
    private val openPrepositions = setOf("in", "im", "am", "zum", "zur", "vom", "für", "gegen", "ohne", "bis", "seit",
        "wegen", "von", "trotz", "statt")
    private val hesitations = setOf("äh", "ähm", "öh", "öhm", "ehm", "hm", "hmm", "mhm")
    private val openPunctuation = Regex("[,:;–—-]\\s*$")
    private val words = Regex("[\\p{L}\\p{N}]+")

    fun looksUnfinished(utterance: String): Boolean {
        val text = utterance.trim()
        if (text.isEmpty() || text.length > MAX_CHARACTERS) return false
        // Whole-utterance controls are complete by definition.
        if (ContinuousConversationMode.command(text) != null) return false
        if (openPunctuation.containsMatchIn(text)) return true
        // A sentence the recognizer closed itself is finished, whatever its last word is.
        if (text.last() in ".!?…") return false
        val tokens = words.findAll(text.lowercase(Locale.GERMAN)).map { it.value }.toList()
        val last = tokens.lastOrNull() ?: return false
        if (last in hesitations) return true
        // A single word is an answer („in“ is unlikely, but „und“ alone asks the assistant to go on).
        if (tokens.size < 2) return false
        return last in openConjunctions || last in openPrepositions
    }

    private const val MAX_CHARACTERS = 2_000
}

/**
 * Joins the pieces of one spoken turn that the recognizer cut at a thinking pause.
 *
 * Bounded on purpose: at most [maxContinuations] extra listening rounds and [maxHoldMs] in total, after
 * which the text is sent as it is. A silent round after a fragment also sends it – the owner stopped
 * there. Nothing is ever dropped, and nothing is added to the owner's words.
 */
internal class OwnerTurnAssembler(
    private val maxContinuations: Int = 2,
    private val maxHoldMs: Long = 20_000L
) {
    sealed interface Result {
        /** The turn is complete (or the bounds are reached): send [text]. */
        data class Send(val text: String) : Result
        /** More is expected: listen again and keep what was said. */
        data object Hold : Result
    }

    private var held: String? = null
    private var continuations = 0
    private var firstHeldAtMs = 0L

    val holding: Boolean get() = held != null

    fun onRecognized(text: String, nowMs: Long): Result {
        val piece = text.trim()
        // An empty result is silence: whatever was held is all the owner said.
        if (piece.isEmpty()) return Result.Send(onSilence().orEmpty())
        val combined = listOfNotNull(held, piece).joinToString(" ")
        val withinBounds = continuations < maxContinuations &&
            (held == null || (nowMs >= firstHeldAtMs && nowMs - firstHeldAtMs < maxHoldMs))
        if (withinBounds && combined.length <= MAX_HELD_CHARACTERS && OwnerTurnCompletion.looksUnfinished(combined)) {
            if (held == null) firstHeldAtMs = nowMs
            held = combined
            continuations++
            return Result.Hold
        }
        reset()
        return Result.Send(combined)
    }

    /** A silent round: the held fragment, if any, is all the owner said. */
    fun onSilence(): String? = held.also { reset() }

    fun reset() {
        held = null
        continuations = 0
        firstHeldAtMs = 0L
    }

    private companion object {
        const val MAX_HELD_CHARACTERS = 4_000
    }
}
