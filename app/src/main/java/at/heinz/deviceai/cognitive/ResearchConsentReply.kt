package at.heinz.deviceai.cognitive

/** Wording only; never grants authority. */
internal object ResearchConsentReply {
    private val accept = Regex("(?iu)^ja,?\\s+mach(?:e)?(?:\\s+bitte)?[.!]?$")
    private val decline = Regex("(?iu)^(?:nein(?:,?\\s+danke)?|ablehnen|abbrechen)[.!]?$")

    fun parse(text: String): Boolean? {
        if (text.length > 60 || text.any(Char::isISOControl)) return null
        val value = text.trim()
        return when {
            accept.matches(value) -> true
            decline.matches(value) -> false
            else -> null
        }
    }
}
