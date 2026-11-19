package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Resolves one explicit German contrast to its affirmative command:
 * „Öffne nicht WhatsApp, sondern Chrome“, „Öffne Chrome statt WhatsApp“, „Öffne Chrome, nicht WhatsApp“.
 *
 * Before this resolver every app lane refused such a sentence as soon as it saw a negation, so the
 * owner's actual request (the affirmative part) was lost or answered with „verneinter Auftrag“.
 *
 * Pure text analysis. It grants no permission and starts nothing: callers still run their normal
 * parsing, approval and safety checks on [Resolution.affirmative], and must never act on
 * [Resolution.rejected]. It returns null for anything that is not an unambiguous single-verb
 * contrast, so each existing fail-closed negation path keeps deciding those sentences.
 */
internal object OwnerContrastResolver {
    data class Resolution(val affirmative: String, val rejected: String) {
        /** True when [candidate] names the target the owner explicitly excluded. */
        fun rejects(candidate: String): Boolean {
            val wanted = normalizeTarget(candidate)
            val excluded = normalizeTarget(rejected)
            if (wanted.isEmpty() || excluded.isEmpty()) return true
            return wanted == excluded || containsWord(excluded, wanted) || containsWord(wanted, excluded)
        }
    }

    private const val VERB = "(?:öffne|oeffne|starte|zeige|zeig|mache|mach|wechsle\\s+zu|wechsel\\s+zu|open)"
    private const val FILL = "(?:\\s+(?:bitte|mir|mal|kurz))*"
    private const val END = "\\s*[.!?]*$"
    private val notBut = Regex("(?iu)^($VERB$FILL)\\s+nicht\\s+(.+?)\\s*,?\\s+sondern\\s+(.+?)$END")
    private val insteadOf = Regex(
        "(?iu)^($VERB$FILL)\\s+(.+?)\\s+(?:statt|anstatt|anstelle\\s+von|anstelle)\\s+(.+?)$END")
    private val commaNot = Regex(
        "(?iu)^($VERB$FILL)\\s+(.+?)\\s*,\\s*(?:(?:und|aber|jedoch)\\s+)?nicht\\s+(.+?)$END")
    private val contrastWord = Regex(
        "(?iu)(?<![\\p{L}\\p{N}_])(?:nicht|nie|niemals|ohne|weder|noch|kein\\p{L}*|sondern|statt|anstatt|anstelle)" +
            "(?![\\p{L}\\p{N}_])")
    private val secondAction = Regex(
        "(?iu)(?<![\\p{L}\\p{N}_])(?:und|oder|dann|danach|anschließend|anschliessend|dass|ob|weil|wenn)" +
            "(?![\\p{L}\\p{N}_])")
    private val particle = Regex("(?iu)\\s+auf$")
    private val leadingDeterminer = Regex(
        "(?iu)^(?:der|die|das|den|dem|des|ein|eine|einen|einem|einer|mein|meine|meinen|meinem|meiner)\\s+")

    fun resolve(utterance: String): Resolution? {
        if (utterance.length !in 5..400 || utterance.any { it.isISOControl() } || ':' in utterance) return null
        val text = utterance.trim().replace(Regex("\\s+"), " ")
        notBut.matchEntire(text)?.let { m ->
            return build(m.groupValues[1], wanted = m.groupValues[3], rejected = m.groupValues[2])
        }
        insteadOf.matchEntire(text)?.let { m ->
            return build(m.groupValues[1], wanted = m.groupValues[2], rejected = m.groupValues[3])
        }
        commaNot.matchEntire(text)?.let { m ->
            return build(m.groupValues[1], wanted = m.groupValues[2], rejected = m.groupValues[3])
        }
        return null
    }

    private fun build(verbHead: String, wanted: String, rejected: String): Resolution? {
        val separable = verbHead.trim().lowercase(Locale.ROOT).startsWith("mach")
        var yes = wanted.trim()
        var no = rejected.trim()
        var suffix = ""
        if (separable) {
            // „Mach nicht die Galerie auf, sondern die Kamera“ / „Mach die Kamera statt der Galerie auf“.
            val had = particle.containsMatchIn(yes) || particle.containsMatchIn(no)
            if (!had) return null
            yes = particle.replace(yes, "").trim()
            no = particle.replace(no, "").trim()
            suffix = " auf"
        }
        if (yes.isEmpty() || no.isEmpty() || yes.length > 80 || no.length > 80) return null
        // One wanted target and one excluded target only: any further negation, contrast or action
        // makes the sentence ambiguous, and an ambiguous sentence is never rewritten.
        if (contrastWord.containsMatchIn(yes) || contrastWord.containsMatchIn(no)) return null
        if (secondAction.containsMatchIn(yes) || secondAction.containsMatchIn(no)) return null
        if (',' in yes || ',' in no) return null
        val resolution = Resolution("${verbHead.trim()} $yes$suffix", no)
        return resolution.takeUnless { it.rejects(yes) }
    }

    internal fun normalizeTarget(target: String): String {
        var text = target.trim().trimEnd('.', '!', '?').lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        repeat(2) { text = leadingDeterminer.replace(text, "") }
        return text.replace(Regex("(?iu)^app\\s+"), "").replace(Regex("(?iu)\\s+app$"), "").trim()
    }

    private fun containsWord(haystack: String, needle: String): Boolean =
        Regex("(?iu)(?<![\\p{L}\\p{N}_])${Regex.escape(needle)}(?![\\p{L}\\p{N}_])").containsMatchIn(haystack)
}
