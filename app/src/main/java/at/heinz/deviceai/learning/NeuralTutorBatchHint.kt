package at.heinz.deviceai.learning

import java.util.Locale

/**
 * Read-only UX hint for an explicitly labeled multi-example Tutor paste.
 * This never starts neural training, parses device commands or stores lessons.
 */
internal object NeuralTutorBatchHint {
    private val quotedExample = Regex("„[^„“]{3,320}“")

    fun isInactiveTutorPaste(text: String): Boolean {
        if (text.length !in 12..1_600) return false
        val normalized = text.trimStart().lowercase(Locale.ROOT)
        if (!normalized.startsWith("frage:") && !normalized.startsWith("gruß:") &&
            !normalized.startsWith("gruss:")) return false
        return quotedExample.findAll(text).take(2).count() == 2
    }
}
