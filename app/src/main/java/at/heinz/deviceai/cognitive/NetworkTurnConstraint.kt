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
        """(?iu)\b(?:antworte|beantworte|arbeite|bleib|bleibe|denke|denk|nutze)\s+(?:bitte\s+)?(?:(?:nur|ausschließlich)\s+)?(?:offline|lokal|gerätelokal)\b"""
    )
    private val onlyLocal = Regex("""(?iu)\b(?:nur|ausschließlich)\s+(?:lokal|gerätelokal|offline)\b""")

    fun forbidsExternalResearch(userTurn: String): Boolean {
        // Oversized text cannot become an implicit web authorization.
        if (userTurn.length > 8192) return true
        val text = userTurn.trim().lowercase(Locale.ROOT)
        return without.containsMatchIn(text) ||
            noCapability.containsMatchIn(text) ||
            negativeVerb.containsMatchIn(text) ||
            negativeAction.containsMatchIn(text) ||
            negativeMedium.containsMatchIn(text) ||
            localOnly.containsMatchIn(text) ||
            onlyLocal.containsMatchIn(text)
    }
}
