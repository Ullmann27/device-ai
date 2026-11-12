package at.heinz.deviceai.localmodel

/**
 * Code55 field finding (Fold7 screenshot 12.11.2026, Code54): a long owner sentence with existing dialogue history
 * failed with the raw native message "Status Code: 3 … Input token ids are too long. Exceeding the maximum number of
 * tokens allowed: 4220 >= 4096". The character/byte prompt budget does not count the system instruction and the tool
 * declarations that the runtime adds to every conversation. This maps the native overflow to an honest German owner
 * message; nothing is silently truncated and no retry is started here.
 */
internal object LocalModelTokenOverflow {
    private val overflow = Regex("(?i)input token ids are too long|exceeding the maximum number of tokens")
    private val counts = Regex("(\\d{3,6})\\s*>=\\s*(\\d{3,6})")

    fun isTokenOverflow(message: String?): Boolean = message != null && overflow.containsMatchIn(message)

    fun ownerMessage(message: String?): String {
        val numbers = message?.let { counts.find(it) }
        val detail = numbers?.let { " (${it.groupValues[1]} von höchstens ${it.groupValues[2]} Token)" }.orEmpty()
        return "Dein Auftrag war zusammen mit dem bisherigen Gesprächsverlauf zu lang für das lokale Modell$detail. " +
            "Ich habe nichts abgeschnitten und nichts erfunden. Stell die Frage bitte noch einmal kürzer oder " +
            "beginne ein neues Gespräch, dann ist wieder genug Platz."
    }
}
