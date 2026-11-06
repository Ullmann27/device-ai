package at.heinz.deviceai.cognitive

/** Intents never grant execution. */
internal object OwnerAppLookupIntent {
    sealed interface Intake {
        data class Lookup(val query: String, val opening: Boolean) : Intake
        data class Planning(val original: String) : Intake
        data class Clarify(val subject: String) : Intake
    }

    private val negation = Regex("(?iu)\\b(nicht|niemals|kein|keine|keinen|keinem|keiner|ohne)\\b")
    private val additional = Regex("(?iu)\\b(und|oder|danach|anschließend|sende|senden|schreibe|schreiben|tippe|lösche|installiere|kaufe|bezahle|überweise)\\b")
    private val direct = Regex("(?iu)^(öffne|starte|suche|finde|zeige)\\s+(.+?)[.!?]*$")
    private val polite = Regex("(?iu)^(?:kannst|könntest|würdest|magst)\\s+du\\s+(.+?)\\s+(öffnen|starten|suchen|finden|zeigen)(?:\\s+bitte)?[.!?]*$")
    private val desire = Regex("(?iu)^ich\\s+(?:möchte|will|würde\\s+gern(?:e)?)\\s+(.+?)\\s+(öffnen|starten)[.!?]*$")
    private val openUp = Regex("(?iu)^mach(?:e)?\\s+(.+?)\\s+auf[.!?]*$")
    private val vague = Regex("(?iu)^(?:ich\\s+)?(?:bräuchte|brauche|hätte\\s+gern(?:e)?|möchte)\\s+(?:bitte\\s+)?(?:etwas|was)\\s+(?:von|aus|in)\\s+(.+?)[.!?]*$")
    private val action = Regex("(?iu)\\b(öffne|öffnen|starte|starten|suche|suchen|finde|finden|aufmachen)\\b")
    private val requestStart = Regex("(?iu)^(?:öffne|starte|suche|finde|zeige|mach(?:e)?|kannst|könntest|würdest|magst|ich)\\b")
    private val query = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N} ._+-]{1,79}")
    private val courtesy = Regex("(?iu)^(?:(?:bitte|mir|mal|kurz|eben)|für\\s+mich)\\s+")
    private val trailingCourtesy = Regex("(?iu)\\s+(?:bitte|für\\s+mich)$")
    private val reference = Regex("(?iu)^(?:sie|es|diese|diese\\s+app|die\\s+app|die|erste|zweite|dritte|vierte|fünfte|letzte|[1-9]|[1-9]\\.)$")

    fun parse(utterance: String): String? = (inspect(utterance) as? Intake.Lookup)?.query

    fun inspect(utterance: String, assistantName: String? = null): Intake? {
        if (utterance.length !in 5..1_000 || utterance.any { it.isISOControl() || it in "\"„“”«»" }) return null
        var text = stripAddress(utterance.trim(), assistantName)
        text = text.replace(Regex("(?iu)^bitte\\s+"), "")
        if (negation.containsMatchIn(text)) return null
        vague.matchEntire(text)?.let { return Intake.Clarify(it.groupValues[1].trim().take(100)) }
        val opening: Boolean
        val target: String
        val d = direct.matchEntire(text)
        val p = polite.matchEntire(text) ?: desire.matchEntire(text)
        val m = openUp.matchEntire(text)
        when {
            d != null -> { opening = d.groupValues[1].lowercase() in setOf("öffne", "starte"); target = d.groupValues[2] }
            p != null -> { opening = p.groupValues[2].lowercase() in setOf("öffnen", "starten"); target = p.groupValues[1] }
            m != null -> { opening = true; target = m.groupValues[1] }
            else -> return if (requestStart.containsMatchIn(text) && action.containsMatchIn(text)) Intake.Planning(utterance) else null
        }
        if (additional.containsMatchIn(target)) return Intake.Planning(utterance)
        var name = target.trim().trimEnd('.', '!', '?').trim()
        repeat(4) { name = name.replace(courtesy, "").replace(trailingCourtesy, "").trim() }
        if (isReference(name)) return Intake.Lookup(name, opening)
        name = name.replace(Regex("(?iu)^(?:die|den|das|meine|meinen)\\s+"), "")
            .replace(Regex("(?iu)^app\\s+"), "").replace(Regex("(?iu)\\s+app$"), "").trim()
        if (!query.matches(name) || name.contains("..") || name.contains(Regex("(?iu)\\b(etwas|irgendwas|irgendetwas)\\b")))
            return Intake.Planning(utterance)
        return Intake.Lookup(name, opening)
    }

    fun isReference(query: String): Boolean = reference.matches(query)

    fun contextualTarget(query: String, candidates: List<NativeAppCandidate>): String? {
        if (!isReference(query)) return query
        val reference = query.lowercase().trimEnd('.')
        val index = when (reference) {
            "erste", "1" -> 0
            "zweite", "2" -> 1
            "dritte", "3" -> 2
            "vierte", "4" -> 3
            "fünfte", "5" -> 4
            "letzte" -> candidates.lastIndex
            else -> reference.toIntOrNull()?.minus(1)
                ?: return candidates.singleOrNull()?.packageName
        }
        return candidates.getOrNull(index)?.packageName
    }

    private fun stripAddress(text: String, configured: String?): String {
        val names = (listOf("Jarvis", "Continuum") + listOfNotNull(configured))
            .filter { it.length in 2..80 && it.none(Char::isISOControl) }.distinct()
            .sortedByDescending(String::length).joinToString("|", transform = Regex::escape)
        return text.replace(Regex("(?iu)^(?:(?:hey|hallo)\\s+)?(?:$names)[,:]?\\s+"), "")
    }
}
