package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Explicit owner opt-out from online research. This does not infer a web grant:
 * it can only prevent an automatic or previously armed web request.
 *
 * Important: this is a conservative *opt-out*, not a general semantic parser.
 * Questions still go through the existing local answer path.
 */
internal object ConversationResearchIntent {
    private val noWebPhrase = Regex(
        """\b(?:ohne|kein(?:e|en|em|er|es)?|without|no)\s+(?:weitere\s+)?(?:internet(?:recherche|suche)?|web(?:recherche|suche)?|online(?:-?recherche|-?suche)?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val noWebImperative = Regex(
        """\b(?:bitte\s+)?(?:nicht|niemals|nie)\s+(?:im\s+internet\s+|online\s+|im\s+web\s+)?recherchier(?:e|en|st|t)?\b|\brecherchier(?:e|en|st|t)?\s+(?:bitte\s+)?nicht\b""",
        RegexOption.IGNORE_CASE
    )
    private val noSearchEnglish = Regex(
        """\b(?:do\s+not|don't|never)\s+(?:search|browse|research)\s+(?:the\s+)?(?:web|internet|online)\b""",
        RegexOption.IGNORE_CASE
    )

    fun forbidsWeb(text: String): Boolean {
        if (text.length > 2_048) return false
        val normalized = text.trim().lowercase(Locale.ROOT)
        return noWebPhrase.containsMatchIn(normalized) ||
            noWebImperative.containsMatchIn(normalized) ||
            noSearchEnglish.containsMatchIn(normalized)
    }
}
