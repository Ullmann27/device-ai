package at.heinz.deviceai.cognitive

/**
 * Explicitly started hands-free conversation for the owner chat.
 *
 * A recognized utterance becomes one ordinary chat turn and the microphone reopens only
 * after the assistant's reply has finished (spoken or, with voice output off, rendered).
 * Ordinary dictation is unaffected: it is never routed through this mode.
 *
 * Pure state machine without Android dependencies. It grants NO tool or device-action
 * approval: every turn uses the normal chat path, and the activity pauses the mode while
 * any dialog (for example a consent or approval prompt) owns the window.
 */
class ContinuousConversationMode {
    enum class State { OFF, LISTENING, THINKING, SPEAKING, RESUMING, PAUSED }
    enum class Decision { IGNORE, SEND, STOP, PAUSE, RETRY_LISTEN, LISTEN }
    enum class Command { STOP, PAUSE }

    var state: State = State.OFF
        private set

    /** Increments for every listening turn; stale recognizer callbacks carry an older value. */
    var turn: Long = 0L
        private set

    private var session: String? = null
    private var replyUtterance: String? = null
    private var silentAttempts = 0

    val active: Boolean get() = state != State.OFF

    /** Explicit owner gesture. Returns false when the mode is already active. */
    fun start(sessionId: String): Boolean {
        if (active || sessionId.isBlank()) return false
        session = sessionId
        replyUtterance = null
        silentAttempts = 0
        state = State.LISTENING
        turn++
        return true
    }

    fun stop() {
        state = State.OFF
        session = null
        replyUtterance = null
        silentAttempts = 0
        turn++
    }

    /** Token for a recognizer started now, or null when this is ordinary dictation. */
    fun listeningTurn(sessionId: String): Long? =
        if (state == State.LISTENING && session == sessionId) turn else null

    fun recognized(turnToken: Long, sessionId: String, text: String): Decision {
        if (!current(turnToken, sessionId)) return Decision.IGNORE
        return when (command(text)) {
            Command.STOP -> { stop(); Decision.STOP }
            Command.PAUSE -> { pause(); Decision.PAUSE }
            null -> if (text.isBlank()) silence() else {
                state = State.THINKING
                silentAttempts = 0
                replyUtterance = null
                Decision.SEND
            }
        }
    }

    /** A silent recognizer timeout is retried once; a second silence or any error pauses. */
    fun listenFailed(turnToken: Long, sessionId: String, silence: Boolean): Decision {
        if (!current(turnToken, sessionId)) return Decision.IGNORE
        if (silence) return silence()
        pause()
        return Decision.PAUSE
    }

    /** The chat path did not accept the turn (for example busy or a pending draft). */
    fun sendRejected(): Decision {
        if (state != State.THINKING) return Decision.IGNORE
        pause()
        return Decision.PAUSE
    }

    /** Any assistant speech queued while a turn is answered becomes the reply to wait for. */
    fun assistantSpeechQueued(utteranceId: String) {
        if (state == State.THINKING || state == State.SPEAKING || state == State.RESUMING) {
            state = State.SPEAKING
            replyUtterance = utteranceId
        }
    }

    /**
     * Only the bound reply utterance counts. A completed (or failed, but visible) reply
     * reopens listening; an interruption by the owner or a stop action pauses the mode.
     */
    fun assistantSpeechFinished(utteranceId: String, interrupted: Boolean): Decision {
        if (state != State.SPEAKING || utteranceId != replyUtterance) return Decision.IGNORE
        replyUtterance = null
        if (interrupted) {
            pause()
            return Decision.PAUSE
        }
        state = State.RESUMING
        return Decision.LISTEN
    }

    /** The reply was rendered without audible speech (voice output off or unavailable). */
    fun replyIdle(): Decision {
        if (state != State.THINKING) return Decision.IGNORE
        state = State.RESUMING
        return Decision.LISTEN
    }

    /** The microphone reopens only when nothing else needs the owner's attention. */
    fun resume(sessionId: String, canListen: Boolean, busy: Boolean): Decision {
        if (state != State.RESUMING) return Decision.IGNORE
        if (sessionId != session) {
            stop()
            return Decision.STOP
        }
        if (busy) {
            state = State.THINKING // More of the reply may still follow.
            return Decision.IGNORE
        }
        if (!canListen) {
            pause()
            return Decision.PAUSE
        }
        state = State.LISTENING
        turn++
        return Decision.LISTEN
    }

    /** Microphone tap while the mode is active: speak now (barge-in) or resume after a pause. */
    fun ownerTap(sessionId: String): Decision {
        if (!active) return Decision.IGNORE
        if (sessionId != session) {
            stop()
            return Decision.STOP
        }
        state = State.LISTENING
        silentAttempts = 0
        replyUtterance = null
        turn++
        return Decision.LISTEN
    }

    private fun current(turnToken: Long, sessionId: String) =
        state == State.LISTENING && turnToken == turn && sessionId == session

    private fun silence(): Decision {
        if (silentAttempts < MAX_SILENT_RETRIES) {
            silentAttempts++
            turn++
            return Decision.RETRY_LISTEN
        }
        pause()
        return Decision.PAUSE
    }

    private fun pause() {
        state = State.PAUSED
        replyUtterance = null
        silentAttempts = 0
        turn++
    }

    companion object {
        const val MAX_SILENT_RETRIES = 1

        private val stopPhrases = setOf(
            "stopp", "stop", "halt", "ende", "beenden", "gespräch beenden", "gespräch stoppen",
            "gesprächsmodus beenden", "gesprächsmodus aus", "beende das gespräch"
        )
        private val pausePhrases = setOf("pause", "pause bitte", "warte", "warte kurz", "kurz warten")

        /** Only a whole utterance is a control command; "stopp die Musik" stays a normal turn. */
        fun command(text: String): Command? {
            val normalized = text.lowercase()
                .replace(Regex("[\\p{Punct}„“”‚‘’«»…]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            return when (normalized) {
                in stopPhrases -> Command.STOP
                in pausePhrases -> Command.PAUSE
                else -> null
            }
        }
    }
}
