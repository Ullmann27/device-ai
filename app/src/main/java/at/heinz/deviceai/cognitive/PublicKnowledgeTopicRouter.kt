package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Conservative public-topic extraction for owner-enabled web research.
 * Do not send raw chat transcripts, account details or private memory to a
 * search engine. Uncertain/personal questions stay on the existing local path.
 */
internal object PublicKnowledgeTopicRouter {
    private val startsAsKnowledgeQuestion = Regex(
        "^(was|wie|welche|welcher|welches|wer|wann|wo|what|how|which|who|when|where)\\b",
        RegexOption.IGNORE_CASE
    )
    private val privateMarkers = Regex(
        "(?iu)(@|https?://|[/\\\\]|[\"„“”«»]|\\b\\d{6,}\\b|\\b(passwort|password|pin|token|geheim|privat|private[nmrs]?|mein|meine|meinen|meiner|meinem|unser[enmsr]*|euer[enmsr]*|ihr[enmsr]*|my|our|your|konto|telefon|adresse|wohnort|sozialversicherung|iban|krankheit|arzt|datei|dateien|pdf|dokument[en]*|notizen|gespräch|chat|erinnerung[en]*|app)\\b)"
    )
    private val researchCommand = Regex(
        "(?iu)^(?:recherchier(?:e)?|such(?:e)?\\s+(?:nach|im\\s+(?:internet|web)|online)|prüfe\\s+im\\s+ris)(?:\\s*:\\s*|\\s+).+"
    )
    private val researchVerb = Regex("(?iu)^(?:such(?:e)?|finde|prüfe)\\s+")
    private val researchMarkers = Regex(
        "(?iu)\\b(?:quellen?|informationen|infos|internet|web|online|ris|aktuell[enmsr]*|neueste[nmsr]?)\\b"
    )
    /**
     * Code55 field P0 (#791, Fold7 offline): "Prüfe nicht das WLAN, sondern nur den Akku. Zeige mir den aktuellen
     * Akkustand …" matched "prüfe" + "aktuell" and became a web search (NO_USABLE_EVIDENCE) instead of the local
     * battery tool. A named state of the owner's own device keeps the weak verb+marker form local; explicit
     * research commands ("Recherchiere …", "Suche im Internet …") are unaffected.
     */
    private val ownDeviceStatus = Regex(
        "(?iu)(?<![\\p{L}])(?:(?:den|der|dem|des|das|mein\\p{L}*|dein\\p{L}*)\\s+(?:aktuellen?\\s+)?" +
            "(?:akku|batterie|wlan|speicher|handy|gerät|geraet|smartphone|bluetooth)\\p{L}*|akkustand|akkuladung|" +
            "ladestand|akkutemperatur|batterietemperatur|thermalstatus|thermischen\\s+status|speicherplatz)(?![\\p{L}])"
    )
    private val additionalAction = Regex(
        "(?iu)(?<![\\p{L}\\p{N}_])(?:sende|senden|schicke|schreib|schreibe|lösche|installiere|kaufe|bezahle|öffne|übertrage)(?![\\p{L}\\p{N}_])"
    )
    private val topicPattern = Regex("[\\p{L}][\\p{L}\\p{N} .,'()\\-]{2,79}")
    /**
     * #567: a question about what the owner told the assistant, or what the assistant did in this dialogue
     * („Welches Testwort habe ich dir gerade genannt?“, „den du gerade erfunden hast“), is personal dialogue.
     * It is never sent to a search engine; it stays on the local memory/model path.
     */
    private val dialogueReference = Regex(
        "(?iu)\\b(ich|wir)\\b.*\\b(dir|dich|euch)\\b|\\b(du|ihr)\\b.*\\b(dir|mir|uns|hast|hattest|habt)\\b|\\b(habe|hab|hatte)\\s+ich\\b"
    )

    /** Personal markers or a reference to this dialogue: such text never reaches a search provider. */
    fun looksPrivate(text: String): Boolean =
        privateMarkers.containsMatchIn(text) || dialogueReference.containsMatchIn(text)

    /** A second action (send, delete, install, pay …) inside a research topic is never forwarded or executed. */
    fun hasAdditionalAction(text: String): Boolean = additionalAction.containsMatchIn(text)

    fun topic(message: String): String? {
        if (message.length !in 8..220 || message.any { it.isISOControl() }) return null
        val cleaned = normalized(message)
        if (OwnerChatIntentRouter.route(cleaned) != null) return null
        if ((!startsAsKnowledgeQuestion.containsMatchIn(cleaned) && !isResearchRequest(cleaned)) ||
            privateMarkers.containsMatchIn(cleaned) || dialogueReference.containsMatchIn(cleaned) ||
            additionalAction.containsMatchIn(cleaned) ||
            NetworkTurnConstraint.forbidsExternalResearch(message)) return null
        val result = ResearchQuestionFocus.searchText(cleaned)
            .removeSuffix("?").trim().take(80).trim()
        if (!topicPattern.matches(result)) return null
        val normalized = result.lowercase(Locale.ROOT)
        if (normalized in setOf("wie gehts", "was kannst du", "wer bin ich", "wie geht es dir")) return null
        return result
    }

    fun isResearchRequest(message: String): Boolean {
        if (message.length !in 8..220 || message.any { it.isISOControl() }) return false
        val text = normalized(message)
        return researchCommand.matches(text) ||
            (researchVerb.containsMatchIn(text) && researchMarkers.containsMatchIn(text) &&
                !ownDeviceStatus.containsMatchIn(text))
    }

    fun explicitTopic(message: String): String? = if (isResearchRequest(message)) topic(message) else null

    private fun normalized(message: String): String =
        OwnerAppLookupIntent.stripAddress(message.trim(), null)
            .replace(Regex("(?iu)^bitte\\s+"), "").replace(Regex("\\s+"), " ")
}
