package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.OwnerStatementModality

/**
 * Integration gate for "Merke dir" (GROK2-HANDOFF §5 H2). Delegates the modality decision to the
 * memory bot's OwnerStatementModality; only plain assertions (and negations as remove-only
 * barriers) may enter the app fact list as owner-confirmed. Everything else is held unconfirmed.
 */
internal object OwnerFactAdmission {
    enum class Kind { CONFIRMABLE, HELD_UNCONFIRMED }

    const val HELD_MESSAGE = "Nicht als Fakt gespeichert: Das klingt nach einer Möglichkeit, einem Plan, " +
        "einer Frage oder einem Bericht. Ich habe es nur als unbestätigte Aussage notiert. Wenn es stimmt, " +
        "sag es als feste Aussage, z. B. „Merke dir: Ich wohne in …“."

    fun classify(fact: String): Kind =
        if (OwnerStatementModality.heldAnnotation(fact) == null) Kind.CONFIRMABLE else Kind.HELD_UNCONFIRMED
}
