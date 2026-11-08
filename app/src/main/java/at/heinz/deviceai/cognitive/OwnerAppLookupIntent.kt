package at.heinz.deviceai.cognitive

/** Intents never grant execution. */
internal object OwnerAppLookupIntent {
    sealed interface Intake {
        data class Lookup(
            val query: String,
            val opening: Boolean,
            val requireInstalledCandidate: Boolean = false,
            val messagePlanningIfMatched: Boolean = false
        ) : Intake
        data class Planning(val original: String, val clarification: String? = null) : Intake
        data class Clarify(val subject: String) : Intake
    }

    private val negation = Regex("(?iu)\\b(nicht|niemals|kein|keine|keinen|keinem|keiner|ohne)\\b")
    private val additional = Regex("(?iu)\\b(und|oder|danach|anschließend|sende|senden|schreibe|schreiben|tippe|lösche|installiere|kaufe|bezahle|überweise)\\b")
    private val direct = Regex("(?iu)^(öffne|starte|suche|finde|zeige)\\s+(.+?)[.!?]*$")
    private val polite = Regex("(?iu)^(?:kannst|könntest|würdest|magst)\\s+du\\s+(.+?)\\s+(öffnen|starten|suchen|finden|zeigen)(?:\\s+bitte)?[.!?]*$")
    private val desire = Regex("(?iu)^ich\\s+(?:möchte|will|würde\\s+gern(?:e)?)\\s+(.+?)\\s+(öffnen|starten)[.!?]*$")
    private val openUp = Regex("(?iu)^mach(?:e)?\\s+(.+?)\\s+auf[.!?]*$")
    private val needPrefix = Regex("(?iu)^ich\\s+(?:brauche|bräuchte|benötige)\\b")
    private val needExplicitApp = Regex(
        "(?iu)^ich\\s+(?:brauche|bräuchte|benötige)\\s+(?:bitte\\s+)?(?:(?:die|eine)\\s+)?app\\s+" +
            "([\\p{L}\\p{N}][\\p{L}\\p{N} ._+-]{0,79})[.!?]*$"
    )
    private val needTrailingApp = Regex(
        "(?iu)^ich\\s+(?:brauche|bräuchte|benötige)\\s+(?:bitte\\s+)?([\\p{L}\\p{N}][\\p{L}\\p{N}._+-]{1,39})\\s+app[.!?]*$"
    )
    private val needSingleApp = Regex(
        "(?iu)^ich\\s+(?:brauche|bräuchte|benötige)\\s+(?:bitte\\s+)?([\\p{L}\\p{N}][\\p{L}\\p{N}._+-]{1,39})[.!?]*$"
    )
    private val nonAppNeedTargets = setOf("hilfe", "pause", "rat", "dich", "mich", "dir", "mir",
        "zeit", "ruhe", "mehr", "antwort", "auskunft")
    private val vague = Regex("(?iu)^(?:ich\\s+)?(?:bräuchte|brauche|hätte\\s+gern(?:e)?|möchte|will)\\s+(?:bitte\\s+)?(?:etwas|was)\\s+(?:von|aus|in)\\s+(.+?)[.!?]*$")
    private val action = Regex("(?iu)\\b(öffne|öffnen|starte|starten|suche|suchen|finde|finden|aufmachen)\\b")
    private val requestStart = Regex("(?iu)^(?:öffne|starte|suche|finde|zeige|mach(?:e)?|kannst|könntest|würdest|magst|ich)\\b")
    private val query = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N} ._+-]{0,79}")
    private val courtesy = Regex("(?iu)^(?:(?:bitte|mir|mal|kurz|eben)|für\\s+mich)\\s+")
    private val trailingCourtesy = Regex("(?iu)\\s+(?:bitte|für\\s+mich)$")
    private val reference = Regex("(?iu)^(?:sie|es|diese|diese\\s+app|die\\s+app|die|erste|zweite|dritte|vierte|fünfte|letzte|[1-9]|[1-9]\\.)$")
    private val fileTarget = Regex("(?iu)\\b(datei|dateien|ordner|verzeichnis|download-ordner|downloads|pdf|dokument|dokumente)\\b|[/\\\\]|\\.(?:pdf|txt|docx|csv|json|jpg|png)\\b")
    private val messageAction = Regex("(?iu)\\b(schreib|schreibe|schreiben|sende|senden|schick|schicke|schicken|nachricht)\\b")
    private val explicitMessageApp = Regex(
        "(?iu)^(?:schreib|schreibe|sende|schick|schicke)\\s+(?:(?:in|über|auf)\\s+)?(?:(?:die|eine)\\s+)?" +
            "(?:app\\s+([\\p{L}\\p{N}._+-]{2,40})|([\\p{L}\\p{N}._+-]{2,40})\\s+app)[.!?]*$"
    )
    private val shortMessageTarget = Regex(
        "(?iu)^(?:schreib|schreibe|sende|schick|schicke)\\s+(?:(?:in|über|auf)\\s+)?(?:(?:die|eine)\\s+)?" +
            "([\\p{L}\\p{N}._+-]{2,40})[.!?]*$"
    )

    fun parse(utterance: String): String? = (inspect(utterance) as? Intake.Lookup)
        ?.takeUnless { it.requireInstalledCandidate }
        ?.query

    fun inspect(utterance: String, assistantName: String? = null): Intake? {
        if (utterance.length !in 5..1_000 || utterance.any { it.isISOControl() || it in "\"„“”«»" }) return null
        var text = stripAddress(utterance.trim(), assistantName)
        text = text.replace(Regex("(?iu)^bitte\\s+"), "")
        if (negation.containsMatchIn(text)) return null
        vague.matchEntire(text)?.let { return Intake.Clarify(it.groupValues[1].trim().take(100)) }
        explicitMessageApp.matchEntire(text)?.let { match ->
            val app = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            if (app != null) return planning(utterance)
        }
        shortMessageTarget.matchEntire(text)?.let { match ->
            return Intake.Lookup(
                query = match.groupValues[1].trim(),
                opening = false,
                requireInstalledCandidate = true,
                messagePlanningIfMatched = true
            )
        }
        if (needPrefix.containsMatchIn(text) && messageAction.containsMatchIn(text)) return planning(utterance)
        val explicitNeedTarget = needExplicitApp.matchEntire(text)?.groupValues?.get(1)?.trim()
            ?: needTrailingApp.matchEntire(text)?.groupValues?.get(1)?.trim()
        val implicitNeedTarget = if (explicitNeedTarget == null) {
            needSingleApp.matchEntire(text)?.groupValues?.get(1)?.trim()?.takeIf {
                it.lowercase() !in nonAppNeedTargets && !isReference(it) &&
                    it.lowercase() !in setOf("das", "den", "ihn", "ihm", "ihr", "uns", "euch")
            }
        } else null
        val needTarget = explicitNeedTarget ?: implicitNeedTarget
        val needRequiresInstalledCandidate = explicitNeedTarget == null && implicitNeedTarget != null
        val opening: Boolean
        val target: String
        val d = direct.matchEntire(text)
        val p = polite.matchEntire(text) ?: desire.matchEntire(text)
        val m = openUp.matchEntire(text)
        when {
            d != null -> { opening = d.groupValues[1].lowercase() in setOf("öffne", "starte"); target = d.groupValues[2] }
            p != null -> { opening = p.groupValues[2].lowercase() in setOf("öffnen", "starten"); target = p.groupValues[1] }
            m != null -> { opening = true; target = m.groupValues[1] }
            needTarget != null -> { opening = true; target = needTarget }
            else -> return if (requestStart.containsMatchIn(text) && action.containsMatchIn(text)) planning(utterance) else null
        }
        if (!Regex("(?iu)\\bapp\\b").containsMatchIn(target) && fileTarget.containsMatchIn(target)) return null
        if (additional.containsMatchIn(target)) return planning(utterance)
        var name = target.trim().trimEnd('.', '!', '?').trim()
        repeat(4) { name = name.replace(courtesy, "").replace(trailingCourtesy, "").trim() }
        if (isReference(name)) return Intake.Lookup(name, opening)
        name = name.replace(Regex("(?iu)^(?:die|den|das|meine|meinen)\\s+"), "")
            .replace(Regex("(?iu)^app\\s+"), "").replace(Regex("(?iu)\\s+app$"), "").trim()
        if (!query.matches(name) || name.contains("..") || name.contains(Regex("(?iu)\\b(etwas|irgendwas|irgendetwas)\\b")))
            return planning(utterance)
        return Intake.Lookup(
            query = name,
            opening = opening,
            requireInstalledCandidate = needRequiresInstalledCandidate
        )
    }

    fun shouldFallbackToConversation(
        intake: Intake.Lookup,
        lookupVerified: Boolean,
        candidateCount: Int
    ): Boolean = intake.requireInstalledCandidate && lookupVerified && candidateCount == 0

    fun shouldClarifyMessage(
        intake: Intake.Lookup,
        lookupVerified: Boolean,
        candidateCount: Int
    ): Boolean = intake.messagePlanningIfMatched && lookupVerified && candidateCount > 0

    fun messageClarification(): String =
        "Welche Nachricht soll ich für welchen konkreten Empfänger vorbereiten? " +
            "Wenn du eine App meinst, kann ich einen Entwurf vorbereiten und sie nach deiner Bestätigung öffnen. " +
            "Direktes Eingeben oder Senden in fremden Apps ist hier noch nicht angebunden."

    private fun planning(original: String) = Intake.Planning(
        original,
        if (messageAction.containsMatchIn(original)) messageClarification() else null
    )

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

    fun stripAddress(text: String, configured: String?): String {
        val names = (listOf("Jarvis", "Continuum") + listOfNotNull(configured))
            .filter { it.length in 2..80 && it.none(Char::isISOControl) }.distinct()
            .sortedByDescending(String::length).joinToString("|", transform = Regex::escape)
        return text.replace(Regex("(?iu)^(?:(?:hey|hallo)\\s+)?(?:$names)[,:]?\\s+"), "")
    }
}
