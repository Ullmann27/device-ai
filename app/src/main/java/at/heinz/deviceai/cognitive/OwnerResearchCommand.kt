package at.heinz.deviceai.cognitive

/**
 * Owner wish (#643, P1): „Jarvis recherchiere …“, „Such nach …“, „Prüfe neueste Quellen zu …“ start a research cycle
 * straight from chat or speech – no Web button. This object only recognises the command and extracts the public
 * topic. Authorisation stays where it is: [ApprovedWebToolGate] (ASK ticket / ALWAYS_ON grant), [InternetConsentPolicy]
 * OFF means no request at all. The topic is the only text that may reach a search provider; personal markers,
 * credentials, links, paths and references to this dialogue are refused instead of sent.
 *
 * Single conversational research entry (Code51 reconciliation of #650/#653 and #656): forms that only the
 * [PublicKnowledgeTopicRouter] recognises („Suche die neuesten Quellen zu …“, „Prüfe im RIS …“) are routed here
 * too, and a topic carrying a second action („… und sende es an Anna“) is refused instead of searched.
 */
internal object OwnerResearchCommand {
    sealed interface Outcome {
        data class Research(val topic: String, val original: String) : Outcome {
            fun activityLine(mode: InternetAccessMode): String = when (mode) {
                InternetAccessMode.ALWAYS_ON ->
                    "WEB-RESEARCH · Sprach-/Chatbefehl · dauerhafte Freigabe ALWAYS_ON · öffentliches Thema: $topic"
                InternetAccessMode.ASK -> "WEB-RESEARCH · Sprach-/Chatbefehl · einmalige Freigabe nötig · Thema: $topic"
                InternetAccessMode.OFF -> "WEB · AUS · keine Anfrage gesendet"
            }
        }
        data class Refused(val reason: String) : Outcome {
            val reply: String get() = "Diese Recherche sende ich nicht an Suchdienste: Der Auftrag enthält $reason. " +
                "Nenne mir das öffentliche Thema ohne diese Angaben, dann recherchiere ich."
        }
    }

    fun offlineReply(): String = "Internetzugang ist AUS. Keine Webanfrage gesendet. Zum Recherchieren stelle den " +
        "Internetzugang unter „Datenschutz & Freigaben“ auf „Nachfragen“ oder „Immer“."

    private const val FILLER = "(?:(?:mal|bitte|mir|kurz|doch|jetzt|noch|für\\s+mich)\\s+)*"
    private const val MEDIUM = "(?:(?:im\\s+(?:internet|netz|web)|online)\\s+)?"
    private val patterns: List<Regex> = listOf(
        Regex("(?iu)^recherchier(?:e)?\\s+(.+)$"),
        Regex("(?iu)^(?:such|suche|schau|schaue|sieh|guck|gucke)\\s+$FILLER$MEDIUM" + "nach\\s+(.+)$"),
        Regex("(?iu)^(?:prüf|prüfe|pruef|pruefe|check|checke)\\s+$FILLER(?:die\\s+)?(?:neueste[n]?|aktuell(?:e|en|ste|sten))\\s+" +
            "(?:quellen|informationen|infos|nachrichten|news|stand|daten)\\s+(?:zu|über|ueber|von|für|zum\\s+thema)\\s+(.+)$"),
        Regex("(?iu)^(?:google|googel|googl)\\s+(.+)$"),
        Regex("(?iu)^(?:finde?|hol|hole|besorg|besorge)\\s+$FILLER(?:aktuelle|neue|neueste|öffentliche|belegte|verlässliche)\\s+" +
            "(?:quellen|belege|informationen|infos|daten)\\s+(?:zu|über|ueber|für|zum\\s+thema)\\s+(.+)$"),
        Regex("(?iu)^(?:mach|mache|starte|beginn|beginne)\\s+$FILLER(?:eine\\s+)?(?:web|internet)?recherche\\s+" +
            "(?:zu|über|ueber|nach|zum\\s+thema)\\s+(.+)$"),
        Regex("(?iu)^was\\s+sag(?:en|t)\\s+(?:die\\s+)?(?:aktuellen?\\s+)?(?:quellen|das\\s+internet|das\\s+netz|das\\s+web)\\s+" +
            "(?:zu|über|ueber)\\s+(.+)$"),
        Regex("(?iu)^(?:web)?(?:recherche|websuche|internetsuche)\\s*[:\\-–]\\s*(.+)$"),
    )
    private val negation = Regex("(?iu)(?<![\\p{L}])(nicht|niemals|kein|keine|keinen|ohne|offline)(?![\\p{L}])")
    private val leadingFiller = Regex("(?iu)^(?:bitte|mal|mir|kurz|doch|jetzt|noch|für\\s+mich|im\\s+internet|im\\s+netz|" +
        "im\\s+web|online)\\s+")
    private val leadingPreposition = Regex("(?iu)^(?:nach|zu|über|ueber|zum\\s+thema|betreffend)\\s+")
    private val leadingArticle = Regex("(?iu)^(?:die|der|das|den|dem|des)\\s+")
    private val trailingFiller = Regex("(?iu)\\s+(?:bitte|für\\s+mich|im\\s+internet|im\\s+netz|im\\s+web|online|danke)$")
    private val linkOrPath = Regex("(?i)(https?://|www\\.|[/\\\\]|@)")

    fun parse(utterance: String, assistantName: String? = null): Outcome? {
        if (utterance.length !in 6..512 || utterance.any { it.isISOControl() }) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName)
            .replace(Regex("(?iu)^(?:hey\\s+|hallo\\s+)?bitte\\s+"), "").trim()
        val raw = patterns.firstNotNullOfOrNull { it.matchEntire(text)?.groupValues?.get(1) }
        if (negation.containsMatchIn(text) || NetworkTurnConstraint.forbidsExternalResearch(utterance)) return null
        if (raw == null) return routerForm(utterance)
        var topic = raw.trim().trimEnd('.', '!', '?').trim()
        repeat(4) { topic = topic.replace(leadingFiller, "").replace(trailingFiller, "").trim() }
        topic = topic.replace(leadingPreposition, "").trim()
        repeat(2) { topic = topic.replace(leadingFiller, "").trim() }
        topic = topic.replace(leadingArticle, "").trimEnd('.', '!', '?').trim()
        if (topic.length !in 2..200) return null
        if (linkOrPath.containsMatchIn(topic) || PublicKnowledgeTopicRouter.looksPrivate(topic))
            return Outcome.Refused("persönliche Angaben, Zugangsdaten oder einen Link/Pfad")
        if (PublicKnowledgeTopicRouter.hasAdditionalAction(topic)) return Outcome.Refused(ADDITIONAL_ACTION)
        return Outcome.Research(topic, utterance.trim())
    }

    private const val ADDITIONAL_ACTION = "eine zusätzliche Handlung (senden, löschen, installieren …)"

    private fun routerForm(utterance: String): Outcome? {
        if (!PublicKnowledgeTopicRouter.isResearchRequest(utterance)) return null
        val topic = PublicKnowledgeTopicRouter.explicitTopic(utterance)
        return when {
            topic != null -> Outcome.Research(topic, utterance.trim())
            PublicKnowledgeTopicRouter.hasAdditionalAction(utterance) -> Outcome.Refused(ADDITIONAL_ACTION)
            else -> Outcome.Refused("persönliche oder mehrdeutige Angaben")
        }
    }
}
