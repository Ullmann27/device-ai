package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Owner-turn network veto. Pure policy, no model output and no tool dispatch.
 *
 * A positive network setting or an armed Web button does not override an
 * explicit same-turn prohibition. Ambiguous mixed instructions fail closed:
 * the owner may submit a new, unambiguous request separately.
 *
 * This recognizes a bounded set of instructions, not arbitrary German
 * semantics. It never grants internet access or any other capability.
 */
internal object NetworkTurnConstraint {
    private val without = Regex(
        """(?iu)\bohne(?:\s+(?:jede|jegliche|jeglichen|eine|einen|externen|externe))?\s+(?:internet(?:recherche|suche|zugriff|verbindung)?|web(?:recherche|suche|zugriff)?|online(?:recherche|suche)?|netz(?:werk)?(?:zugriff)?|cloud(?:zugriff)?)\b"""
    )
    private val noCapability = Regex(
        """(?iu)\bkein(?:e|en|es|em|er)?\s+(?:internet(?:recherche|suche|zugriff)?|web(?:recherche|suche|zugriff)?|online(?:recherche|suche)?|netz(?:werk)?zugriff|cloudzugriff)\b"""
    )
    private val negativeVerb = Regex(
        """(?iu)\b(?:suche|such|recherchiere|recherchier|nutze|verwende|benutze|öffne|oeffne|verwende)\s+(?:(?:bitte|jetzt|dabei)\s+)?nicht\s+(?:(?:im|ins|das|die|den|dem)\s+)?(?:internet|web|netz|online|browser|cloud)\b"""
    )
    private val negativeAction = Regex(
        """(?iu)\bnicht\s+(?:(?:mehr|bitte|direkt)\s+)?(?:(?:im|ins)\s+)?(?:internet|web|netz|online|browser|cloud)\s+(?:suchen|recherchieren|nachschlagen|surfen|nutzen|verwenden|gehen)\b"""
    )
    private val negativeMedium = Regex(
        """(?iu)\b(?:nicht|niemals|keinesfalls)\s+(?:(?:ins|im|auf|das)\s+)?(?:internet|web|online|cloud)\b"""
    )
    private val localOnly = Regex(
        """(?iu)\b(?:antworte|beantworte|arbeite|bleib|bleibe|denke|denk|nutze|suche|such|recherchiere|recherchier)\s+(?:bitte\s+)?(?:(?:nur|ausschließlich)\s+)?(?:offline|lokal|gerätelokal)\b"""
    )
    // "nicht nur lokal" is explicitly NOT an offline-only constraint.
    private val onlyLocal = Regex("""(?iu)(?<!nicht\s)\b(?:nur|ausschließlich)\s+(?:lokal|gerätelokal|offline)\b""")
    private val explicitBlockedCapability = Regex(
        """(?iu)\b(?:internet(?:recherche|suche|zugriff)?|web(?:recherche|suche|zugriff)?|online(?:recherche|suche)?|cloud(?:zugriff)?|netz(?:werk)?(?:zugriff)?)\s+(?:(?:ist|bleibt)\s+)?(?:verboten|untersagt|gesperrt|deaktiviert)\b"""
    )
    private val noConnection = Regex("""(?iu)\bverbinde\s+(?:dich\s+)?nicht\s+mit\s+dem\s+internet\b""")
    private val explicitOfflineAnswer = Regex(
        """(?iu)\b(?:antworte|beantworte|arbeite|erkläre|loese|löse)\b[^.!?;]{0,48}\boffline\b"""
    )

    private const val transport = "(?:internet(?:zugriff|verbindung)?|netz(?:werk)?(?:zugriff|verbindung)?|cloud(?:zugriff)?)"
    private val speechTransportVeto = listOf(
        Regex("""(?iuU)(?<!nicht\s)(?<!niemals\s)(?<!keinesfalls\s)\bohne(?:\s+(?:jede|jegliche|jeglichen|eine|einen|externen|externe))?\s+$transport\b"""),
        Regex("""(?iuU)\bkein(?:e|en|es|em|er)?\s+$transport\b"""),
        Regex("""(?iuU)\b$transport\s+(?:(?:ist|bleibt)\s+)?(?:verboten|untersagt|gesperrt|deaktiviert)\b"""),
        Regex("""(?iuU)\b(?:nutze|verwende|benutze|aktiviere)\s+(?:(?:bitte|jetzt|dabei)\s+)?nicht\s+(?:(?:im|ins|das|die|den|dem)\s+)?$transport\b"""),
        Regex("""(?iuU)\b(?:nutze|verwende|benutze|aktiviere)\s+(?:(?:bitte|jetzt|dabei)\s+)?(?:(?:das|die|den|dem)\s+)?$transport\s+(?:(?:bitte|jetzt|dabei)\s+)?(?:nicht|niemals|keinesfalls)\b(?!\s+(?:nur|ausschließlich)\b)"""),
        Regex("""(?iuU)(?:^|[.!?;,])\s*(?:bitte\s+)?(?:nicht|niemals|keinesfalls)\s+(?:(?:in\s+die|ins|im|auf|das)\s+)?$transport\b(?=\s*(?:$|[.!?,;]|(?:nutzen|verwenden|benutzen|gehen|verbinden|senden|schicken|übertragen)\b))"""),
        Regex("""(?iuU)\b(?:sende|schicke|übertrage|übermittle|lade)\b[^.!?;]{0,64}\b(?:nicht|niemals|keinesfalls)\s+(?:(?:in\s+die|ins|im|auf|das)\s+)?$transport\b""")
    )
    private val localSpeech = Regex(
        """(?iuU)\b(?:antworte|beantworte|arbeite|bleib|bleibe|sprich|spreche|lies)\s+(?:bitte\s+)?(?:(?:nur|ausschließlich)\s+)?(?:offline|lokal|gerätelokal)\b"""
    )
    private val offlineSpeechAnswer = Regex(
        """(?iuU)\b(?:antworte|beantworte|arbeite|erkläre|loese|löse|sprich|lies)\b(?:(?!\bnicht\b)[^.!?;]){0,48}\b(?:offline|gerätelokal)\b"""
    )
    private val onlyLocalSpeech = Regex(
        """(?iuU)(?:^|[.!?;,])\s*(?:bitte\s+)?(?:nur|ausschließlich)\s+(?:lokal|gerätelokal|offline)\b"""
    )
    private val offlineSpeechCorrection = Regex(
        """(?iuU)\b(?:sondern|stattdessen)\s+(?:bitte\s+)?(?:(?:nur|ausschließlich)\s+)?(?:offline|gerätelokal)\b"""
    )
    private val quotedSpeechMention = Regex(
        """(?iuU)\b(?:was\s+(?:bedeutet|heißt|heisst)|(?:erkläre|übersetze|zitiere)(?:\s+mir)?(?:\s+(?:den|das|die))?(?:\s+(?:ausdruck|begriff|satz|formulierung|wort))?)\s*(?:"[^"\r\n]{1,512}"|„[^“\r\n]{1,512}“|‚[^‘\r\n]{1,512}‘|'[^'\r\n]{1,512}')"""
    )

    /** Explicit veto, never network permission. */
    fun forbidsExternalSpeech(userTurn: String): Boolean {
        if (userTurn.length > 8192) return true
        val text = userTurn.trim().lowercase(Locale.ROOT)
            .replace(quotedSpeechMention, " ").replace(Regex("\\s+"), " ")
        return speechTransportVeto.any { it.containsMatchIn(text) } ||
            noConnection.containsMatchIn(text) || localSpeech.containsMatchIn(text) ||
            offlineSpeechAnswer.containsMatchIn(text) || onlyLocalSpeech.containsMatchIn(text) ||
            offlineSpeechCorrection.containsMatchIn(text)
    }

    /** Remove-only execution boundary, bound to OWNER text rather than a rewritten model query.
     * Generic to keep this pure policy usable in the existing JVM research checks. */
    fun <T> withOriginalTurn(originalUserTurn: String, toolName: String, execute: () -> T): T {
        check(!(toolName in setOf("web_search", "web_read") && forbidsExternalResearch(originalUserTurn))) {
            "Externe Recherche ist für diese ursprüngliche Nutzeranfrage verboten"
        }
        return execute()
    }

    fun forbidsExternalResearch(userTurn: String): Boolean {
        // Oversized text cannot become an implicit web authorization.
        if (userTurn.length > 8192) return true
        // These bounded requests concern this assistant or this device. Keep
        // their remove-only boundary even if a model rewrites the web query or
        // the owner has globally allowed research for other public questions.
        if (OwnerChatIntentRouter.route(userTurn) != null) return true
        val text = userTurn.trim().lowercase(Locale.ROOT)
        return without.containsMatchIn(text) ||
            noCapability.containsMatchIn(text) ||
            negativeVerb.containsMatchIn(text) ||
            negativeAction.containsMatchIn(text) ||
            negativeMedium.containsMatchIn(text) ||
            localOnly.containsMatchIn(text) ||
            onlyLocal.containsMatchIn(text) ||
            explicitBlockedCapability.containsMatchIn(text) ||
            noConnection.containsMatchIn(text) ||
            explicitOfflineAnswer.containsMatchIn(text)
    }
}
