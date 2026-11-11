package at.heinz.deviceai.cognitive

/** One answer, never execution consent. */
internal class OwnerAppClarification {
    private data class Pending(val session: String, val revision: Long, val opening: Boolean)
    private var pending: Pending? = null
    private val name = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N} ._+-]{0,79}")
    private val independent = Regex("(?iu)(?<![\\p{L}\\p{N}_])(?:" +
        "ja|nein|danke|okay|ok|stop|stopp|abbrechen|egal|nichts|nicht|kein\\p{L}*|" +
        "und|oder|danach|stattdessen|bitte|ich|du|sie|es|diese|app|" +
        "was|wie|warum|wer|wann|wo|welche\\p{L}*|kann\\p{L}*|soll\\p{L}*|möchte|will|" +
        "prüf\\p{L}*|pruef\\p{L}*|analysier\\p{L}*|untersuch\\p{L}*|erklär\\p{L}*|" +
        "erinner\\p{L}*|merke?|zeig\\p{L}*|schreib\\p{L}*|sag(?:e)?|" +
        "hallo|guten|morgen|später|spaeter)(?![\\p{L}\\p{N}_])")

    fun requestName(session: String, revision: Long, opening: Boolean) {
        pending = Pending(session, revision, opening)
    }

    fun clear() { pending = null }

    fun consume(session: String, revision: Long, answer: String, assistantName: String? = null): OwnerAppLookupIntent.Intake.Lookup? {
        val expected = pending ?: return null
        pending = null
        if (session != expected.session || revision != expected.revision) return null
        val value = OwnerAppLookupIntent.stripAddress(answer.trim(), assistantName).trimEnd('.', '!').trim()
        if (!name.matches(value) || value.contains("..") || value.contains(Regex("\\.\\s")) ||
            independent.containsMatchIn(value) || OwnerAppLookupIntent.looksLikeFileTarget(value) ||
            OwnerInternalNavigationIntent.resolve(value, assistantName) != null ||
            OwnerAppLookupIntent.isReference(value) || OwnerAppLookupIntent.inspect(value, assistantName) != null ||
            OwnerDeviceRequestSafety.inspect(value).blocked) return null
        return OwnerAppLookupIntent.Intake.Lookup(value, expected.opening)
    }
}
