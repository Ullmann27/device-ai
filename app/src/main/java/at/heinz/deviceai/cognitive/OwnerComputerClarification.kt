package at.heinz.deviceai.cognitive

/** One missing search term. */
internal class OwnerComputerClarification {
    private data class Pending(val session: String, val revision: Long, val app: ComputerApp)
    private var pending: Pending? = null
    private val refusal = Regex("(?iu)(?<![\\p{L}\\p{N}_])(?:nein|nicht|nichts|niemals|" +
        "kein\\p{L}*|stopp?|abbrechen|abbruch|stattdessen)(?![\\p{L}\\p{N}_])")
    private val independent = Regex("(?iu)^(?:(?:bitte|und|aber|also)\\s+)*(?:" +
        "ja|danke|okay|ok|egal|später|spaeter|was|wie|warum|wieso|weshalb|wer|wann|wo|" +
        "welche\\p{L}*|ich|du|kannst|könntest|koenntest|sollst|" +
        "erklär\\p{L}*|erzaehl\\p{L}*|erzähl\\p{L}*|schreib\\p{L}*|" +
        "sag(?:e)?|merke?|erinner\\p{L}*|prüf\\p{L}*|pruef\\p{L}*|" +
        "analysier\\p{L}*|untersuch\\p{L}*)(?![\\p{L}\\p{N}_])")

    fun request(session: String, revision: Long, question: OwnerComputerIntentParser.Result.Ask) {
        pending = question.searchApp?.let { Pending(session, revision, it) }
    }

    fun clear() { pending = null }

    fun consume(session: String, revision: Long, answer: String, assistantName: String? = null): OwnerComputerIntent? {
        val expected = pending ?: return null
        pending = null
        if (session != expected.session || revision != expected.revision ||
            answer.length > 300 || answer.any { it.isISOControl() }) return null
        val value = OwnerAppLookupIntent.stripAddress(answer.trim(), assistantName)
            .trimEnd('.', '!').trim()
        if (value.length !in 1..200 || value.contains('?') || refusal.containsMatchIn(value) ||
            independent.containsMatchIn(value) || OwnerDeviceRequestSafety.inspect(value).blocked ||
            OwnerChatIntentRouter.route(value) != null ||
            OwnerInternalNavigationIntent.resolve(value, assistantName) != null ||
            OwnerAppLookupIntent.inspect(value, assistantName) != null ||
            OwnerComputerIntentParser.parse(value) !is OwnerComputerIntentParser.Result.NotComputerCommand) return null
        val completed = OwnerComputerIntentParser.parse("Suche in ${expected.app.discoveryQuery} nach $value")
            as? OwnerComputerIntentParser.Result.Ready ?: return null
        return completed.intent.takeIf { it.app == expected.app && it.goal == ComputerGoal.SEARCH }
    }
}
