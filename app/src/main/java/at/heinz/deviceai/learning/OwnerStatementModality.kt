package at.heinz.deviceai.learning

import at.heinz.deviceai.intelligence.ewm.Ewm1Lexicon
import java.text.Normalizer
import java.util.Locale

/**
 * What kind of statement did the owner make? (Argon-4 dossier 6.1: "Mein Auto ist rot" is a different kind of
 * statement than "Ich überlege, ein rotes Auto zu kaufen".)
 *
 * Deterministic, bounded and conservative: anything conditional, hedged, planned, questioned or reported is NOT an
 * assertion and must never become a confirmed personal fact. Uses the existing EWM lexicon (saying verbs, negations,
 * future cues) instead of a second vocabulary. A negation stays a confirmable statement, because the existing ledger
 * uses it as a remove-only barrier ("Klara ist nicht 29" invalidates "Klara ist 29"); it never supplies a value.
 */
enum class StatementModality { ASSERTED, NEGATED, HYPOTHETICAL, UNCERTAIN, INTENTION, QUESTION, REPORTED }

object OwnerStatementModality {
    private val correctionPrefix = Regex("(?iu)^\\s*(?:korrektur|berichtigung)\\s*:\\s*")
    private val interrogative = Regex("(?iu)^(?:wer|wen|wem|wessen|was|wo|wohin|woher|wann|wie|warum|wieso|weshalb|" +
        "weswegen|welche|welcher|welches|welchen|welchem|ob)\\b")
    private val hypotheticalWords = setOf("wenn", "falls", "sofern", "angenommen", "gesetzt", "würde", "würdest",
        "würden", "würdet", "hätte", "hättest", "hätten", "hättet", "wäre", "wärst", "wären", "wäret", "könnte",
        "könntest", "könnten", "theoretisch", "hypothetisch", "fiktiv")
    private val hypotheticalPhrases = Regex("(?iu)\\b(?:stell\\s+dir\\s+vor|nehmen\\s+wir\\s+an|im\\s+fall,?\\s+dass)\\b")
    private val uncertainWords = setOf("vielleicht", "eventuell", "evtl", "möglicherweise", "vermutlich",
        "wahrscheinlich", "womöglich", "ggf", "gegebenenfalls", "unsicher", "anscheinend", "scheinbar")
    private val uncertainPhrases = Regex("(?iu)\\b(?:ich\\s+(?:glaube|glaub|denke|vermute|schätze|meine)\\s*(?:,|\\bdass\\b)|" +
        "(?:glaube|denke|vermute|schätze|meine)\\s+ich\\b|nicht\\s+sicher|keine\\s+ahnung|weiß\\s+(?:ich\\s+)?nicht,?\\s+ob)")
    // Plans and wishes about a CHANGE of state are not states. A plain preference ("Ich wünsche Antworten auf
    // Deutsch") is an assertion about the owner and stays confirmable (existing ContinuumPublicCurriculumTest).
    private val intentionPhrases = Regex("(?iu)\\b(?:ich\\s+(?:überlege|plane|hoffe|träume|habe\\s+vor|hab\\s+vor)|" +
        "vorhaben|vorhabe)\\b|\\b(?:werde|wirst|wird|werden|werdet)\\b")
    private val wishVerb = Regex("(?iu)\\bich\\s+(?:möchte|will|wünsche\\s+mir|mag)\\b")
    private val stateChangeInfinitive = Regex("(?iu)\\b(?:zu\\s+)?(?:ziehen|umziehen|übersiedeln|kaufen|wohnen|leben|arbeiten|" +
        "werden|heiraten|wechseln|anfangen|aufhören|studieren|auswandern|kündigen|bauen)\\b")
    private val accordingTo = Regex("(?u)(?:^|[\\s,;(])[Ll]aut\\s+(?:\\p{Lu}|dem\\b|der\\b|den\\b|meinem\\b|meiner\\b)")
    private val reportedWords = Ewm1Lexicon.SAYING + setOf("angeblich", "erzählen", "behaupten", "sagen",
        "schrieb", "schreibt", "gesagt", "erzählt", "behauptet", "geschrieben")
    private val quoteChars = setOf('„', '“', '”', '"', '»', '«', '‚', '‘')

    fun classify(text:String):StatementModality {
        val raw = Normalizer.normalize(text.trim(), Normalizer.Form.NFC).take(2048)
        val body = raw.replaceFirst(correctionPrefix, "")
        val low = body.lowercase(Locale.ROOT)
        val words = low.split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotEmpty)
        val wordSet = words.toSet()
        if (body.trimEnd().endsWith("?") || interrogative.containsMatchIn(low.trimStart()))
            return StatementModality.QUESTION
        if (wordSet.any { it in hypotheticalWords } || hypotheticalPhrases.containsMatchIn(low))
            return StatementModality.HYPOTHETICAL
        if (wordSet.any { it in reportedWords } || accordingTo.containsMatchIn(body) || startsWithQuotedClause(body))
            return StatementModality.REPORTED
        if (wordSet.any { it in uncertainWords } || uncertainPhrases.containsMatchIn(low))
            return StatementModality.UNCERTAIN
        if (intentionPhrases.containsMatchIn(low) || wordSet.any { it in Ewm1Lexicon.FUTURE_CUES } ||
            (wishVerb.containsMatchIn(low) && stateChangeInfinitive.containsMatchIn(low)))
            return StatementModality.INTENTION
        val negated = wordSet.any { it in Ewm1Lexicon.NEGATIONS }
        val replaced = "sondern" in wordSet || "stattdessen" in wordSet
        if (negated && !replaced) return StatementModality.NEGATED
        return StatementModality.ASSERTED
    }

    /** Assertions, and negations as remove-only barriers. Everything else stays an unconfirmed statement. */
    fun mayBecomeConfirmedFact(modality:StatementModality):Boolean =
        modality == StatementModality.ASSERTED || modality == StatementModality.NEGATED

    /** Ledger annotation of a held (unconfirmed) owner statement; null for confirmable statements. */
    fun heldAnnotation(text:String):String? = classify(text).let { m ->
        if (mayBecomeConfirmedFact(m)) null else HELD_PREFIX + m.name
    }

    const val HELD_PREFIX = "unbestaetigt:"

    /** A sentence that opens with a quotation is someone else's wording ("„Ich wohne in Rom“, schrieb Lisa."). */
    private fun startsWithQuotedClause(text:String):Boolean {
        val trimmed = text.trimStart()
        if (trimmed.length < 6 || trimmed.first() !in quoteChars) return false
        val close = (3 until trimmed.length).firstOrNull { trimmed[it] in quoteChars } ?: return false
        return trimmed.substring(close + 1).any(Char::isLetter)
    }
}
