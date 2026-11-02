package at.heinz.deviceai.learning

import java.util.Locale

/**
 * Privacy-first active-learning question generator.
 * Stores one brief, *unlabeled* utterance in volatile memory, never disk.
 * A user must choose the correct label and then confirm the existing training dialog.
 * It cannot call device tools, collect speech in the background, or assign labels itself.
 */
class NeuralCuriosityRadar {
    private var pending: String? = null
    private val forbidden = Regex(
        """(?i)\b(passwort|password|pin|iban|token|secret|schlüssel|api.?key|lösch\w*|"""+
        """installier\w*|deinstallier\w*|überweis\w*|bezah\w*|trainier\w*|"""+
        """neuron|hilfe|system|developer|merke|vergiss|recherchier\w*|https?)\b"""
    )
    fun peek(): String? = pending

    /** A visible curiosity question, NOT automatic learning of the chat itself. */
    fun offer(text: String, active: Boolean, alreadyTaught: Boolean = false): Boolean {
        if (!active || alreadyTaught || pending != null) return false
        val line = text.trim().replace(Regex("\\s+")," ")
        if (line.length !in 10..110 || line.any { it.isDigit() } ||
            !line.matches(Regex("[\\p{L} .,!?-]+")) ||
            forbidden.containsMatchIn(line.lowercase(Locale.ROOT))) return false
        val terms = line.split(Regex("\\s+"))
        if (terms.size !in 3..16) return false
        pending = line
        return true
    }

    fun consume(): String? = pending.also { pending = null }
    fun clear() { pending = null }
}
