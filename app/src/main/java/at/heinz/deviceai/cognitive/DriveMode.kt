package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Explicit hands-free mode for driving.
 *
 * Pure policy and state without Android dependencies. The mode is entered and left only by one of
 * a few whole-utterance owner commands (see [command]); it grants NO tool or device-action
 * approval. Its only effects are the plain values in [Effects], which the caller applies:
 * a longer listening window, spoken notifications independent of an open conversation, short
 * answers and no approval dialogs that would need a look at the display.
 */
class DriveMode {
    enum class Command { ENTER, EXIT }

    /** Values the caller applies while the mode is active. */
    data class Effects(
        /** Listening window for [ContinuousConversationMode.silenceWindowMs]: its upper bound. */
        val silenceWindowMs: Long,
        /** Enabled notification announcements are spoken even without an active conversation. */
        val announcementsRegardlessOfConversation: Boolean,
        /** A spoken reply is limited to this many sentences. */
        val maxSpokenSentences: Int,
        /** Actions that need a visual approval are not shown now but noted via [deferVisualApproval]. */
        val deferVisualApprovals: Boolean,
        /** Spoken instead of an approval dialog. */
        val visualApprovalSentence: String,
    )

    /** Outcome of [deferVisualApproval]: what to say, and whether the request was noted. */
    data class Deferral(val spokenSentence: String, val remembered: Boolean)

    private val deferred = ArrayList<String>()

    @Volatile
    var active: Boolean = false
        private set

    /** Caller clock value of the last [enter], or null while inactive. */
    @Volatile
    var enteredAtMs: Long? = null
        private set

    /** [EFFECTS] while active, otherwise null (nothing to apply). */
    val effects: Effects? get() = if (active) EFFECTS else null

    /** Requests noted during the drive, oldest first. They stay available after [exit]. */
    val deferredRequests: List<String> get() = synchronized(deferred) { deferred.toList() }

    /** Returns false when the mode was already active. */
    fun enter(nowMs: Long): Boolean {
        if (active) return false
        active = true
        enteredAtMs = nowMs
        return true
    }

    /** Returns false when the mode was not active. Noted requests are kept for [takeDeferredRequests]. */
    fun exit(): Boolean {
        if (!active) return false
        active = false
        enteredAtMs = null
        return true
    }

    /**
     * [command] narrowed by the current state: the conversational phrases "Ich fahre jetzt los" and
     * "Ich bin angekommen" count only when they would change the mode, so they remain ordinary
     * sentences otherwise. The explicit "Fahrmodus an/ein/aus" is always reported.
     */
    fun commandFor(text: String?): Command? {
        val normalized = normalize(text) ?: return null
        return when {
            normalized in EXPLICIT_ENTER -> Command.ENTER
            normalized in EXPLICIT_EXIT -> Command.EXIT
            normalized in SITUATIONAL_ENTER && !active -> Command.ENTER
            normalized in SITUATIONAL_EXIT && active -> Command.EXIT
            else -> null
        }
    }

    /**
     * Notes an owner request whose action needs a look at the display. Returns null when the mode
     * is inactive (the caller shows its normal approval). The list holds at most
     * [MAX_DEFERRED_REQUESTS] bounded texts; when it is full the request is NOT noted and the
     * returned sentence says so, instead of silently forgetting an earlier one.
     *
     * The noted texts are for reading back after the drive. They are never executed from here.
     */
    fun deferVisualApproval(requestText: String): Deferral? {
        if (!active) return null
        val request = bounded(requestText)
        synchronized(deferred) {
            if (request.isEmpty()) return Deferral(VISUAL_APPROVAL_NOT_REMEMBERED_SENTENCE, false)
            if (deferred.any { it.equals(request, ignoreCase = true) }) {
                return Deferral(VISUAL_APPROVAL_DEFERRED_SENTENCE, true)
            }
            if (deferred.size >= MAX_DEFERRED_REQUESTS) return Deferral(VISUAL_APPROVAL_NOT_REMEMBERED_SENTENCE, false)
            deferred += request
            return Deferral(VISUAL_APPROVAL_DEFERRED_SENTENCE, true)
        }
    }

    /** Returns the noted requests, oldest first, and clears the list. */
    fun takeDeferredRequests(): List<String> = synchronized(deferred) {
        val taken = deferred.toList()
        deferred.clear()
        taken
    }

    private fun bounded(text: String): String {
        val collapsed = StringBuilder()
        var pendingSpace = false
        for (char in text) {
            if (char.isWhitespace() || char.isISOControl()) {
                pendingSpace = collapsed.isNotEmpty()
                continue
            }
            if (pendingSpace) collapsed.append(' ')
            pendingSpace = false
            collapsed.append(char)
            if (collapsed.length >= MAX_DEFERRED_REQUEST_CHARS) break
        }
        // Never end on half of a surrogate pair.
        if (collapsed.isNotEmpty() && collapsed.last().isHighSurrogate()) collapsed.setLength(collapsed.length - 1)
        return collapsed.toString()
    }

    companion object {
        const val MAX_SPOKEN_SENTENCES = 2
        const val MAX_DEFERRED_REQUESTS = 5
        const val MAX_DEFERRED_REQUEST_CHARS = 160

        const val VISUAL_APPROVAL_DEFERRED_SENTENCE =
            "Das braucht einen Blick aufs Display. Ich merke es mir, bis du stehst."
        const val VISUAL_APPROVAL_NOT_REMEMBERED_SENTENCE =
            "Das braucht einen Blick aufs Display. Mehr kann ich mir für diese Fahrt nicht merken. " +
                "Sag es mir bitte noch einmal, wenn du stehst."
        const val ENTER_CONFIRMATION = "Fahrmodus ist an. Ich halte mich kurz."
        const val EXIT_CONFIRMATION = "Fahrmodus ist aus."
        const val ALREADY_ACTIVE = "Der Fahrmodus ist schon an."
        const val ALREADY_INACTIVE = "Der Fahrmodus ist schon aus."

        val EFFECTS = Effects(
            silenceWindowMs = ContinuousConversationMode.MAX_SILENCE_WINDOW_MS,
            announcementsRegardlessOfConversation = true,
            maxSpokenSentences = MAX_SPOKEN_SENTENCES,
            deferVisualApprovals = true,
            visualApprovalSentence = VISUAL_APPROVAL_DEFERRED_SENTENCE,
        )

        private const val MAX_COMMAND_CHARS = 48
        // "fahr modus" covers a recogniser that writes the compound as two words or with a hyphen.
        private val EXPLICIT_ENTER = setOf("fahrmodus an", "fahrmodus ein", "fahr modus an", "fahr modus ein")
        private val EXPLICIT_EXIT = setOf("fahrmodus aus", "fahr modus aus")
        private val SITUATIONAL_ENTER = setOf("ich fahre jetzt los")
        private val SITUATIONAL_EXIT = setOf("ich bin angekommen")

        /**
         * Recognises only a complete utterance that is exactly one of the drive-mode commands
         * ("Fahrmodus an", "Fahrmodus ein", "Fahrmodus aus", "Ich fahre jetzt los", "Ich bin
         * angekommen"), ignoring case, punctuation and surrounding whitespace. A sentence that
         * merely contains such words, or negates them, is not a command and returns null.
         */
        fun command(text: String?): Command? {
            val normalized = normalize(text) ?: return null
            return when (normalized) {
                in EXPLICIT_ENTER, in SITUATIONAL_ENTER -> Command.ENTER
                in EXPLICIT_EXIT, in SITUATIONAL_EXIT -> Command.EXIT
                else -> null
            }
        }

        /** After the drive: one bounded spoken summary of the noted requests, or null if none. */
        fun deferredReadout(requests: List<String>): String? {
            val noted = requests.filter { it.isNotBlank() }.take(MAX_DEFERRED_REQUESTS)
            if (noted.isEmpty()) return null
            val intro = if (noted.size == 1) {
                "Während der Fahrt habe ich mir eine Sache gemerkt, die einen Blick aufs Display braucht:"
            } else {
                "Während der Fahrt habe ich mir ${noted.size} Dinge gemerkt, die einen Blick aufs Display brauchen:"
            }
            val items = noted.joinToString(" ") { "„$it“." }
            return "$intro $items Sag mir, womit ich weitermachen soll."
        }

        private fun normalize(text: String?): String? {
            if (text == null || text.length > MAX_COMMAND_CHARS) return null
            val words = StringBuilder()
            var pendingSpace = false
            for (char in text.lowercase(Locale.ROOT)) {
                if (char.isLetterOrDigit()) {
                    if (pendingSpace) words.append(' ')
                    pendingSpace = false
                    words.append(char)
                } else {
                    pendingSpace = words.isNotEmpty()
                }
            }
            return words.toString().ifEmpty { null }
        }
    }
}
