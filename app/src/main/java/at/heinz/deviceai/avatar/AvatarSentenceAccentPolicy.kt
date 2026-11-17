package at.heinz.deviceai.avatar

/** Prevents sentence-final head gestures at known title and legal-reference abbreviations. */
internal object AvatarSentenceAccentPolicy {
    private val titles = setOf("dr", "prof", "mag", "ing", "di", "dipl", "mr", "mrs", "ms")
    private val numberedReferences = setOf("abs", "art", "nr")

    /** Called only after a period with a following token. The real punctuation remains spoken. */
    fun suppress(text: String, first: Int, last: Int, next: Int): Boolean {
        if (first < 0 || last < first || next <= last || next >= text.length) return false
        val abbreviation = text.substring(first, last + 1).lowercase()
        val following = text[next]
        return (abbreviation in titles && following.isUpperCase()) ||
            (abbreviation in numberedReferences && following.isDigit())
    }
}
