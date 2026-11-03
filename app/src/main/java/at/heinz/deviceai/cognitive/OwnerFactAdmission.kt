package at.heinz.deviceai.cognitive

/** RED stub: every "Merke dir" sentence is still admitted as an owner-confirmed fact. */
internal object OwnerFactAdmission {
    enum class Kind { CONFIRMABLE, HELD_UNCONFIRMED }
    const val HELD_MESSAGE = ""
    fun classify(fact: String): Kind = Kind.CONFIRMABLE
}
