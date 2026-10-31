package at.heinz.deviceai.learning

/**
 * Presentation-only owner perspective in the visible dialogue.
 *
 * Never rewrite the ledger, the evidence, the retrieval index or the model
 * prompt. A stored first-person user sentence belongs to the owner, not the
 * assistant. Only the unambiguous German possessive at sentence start is
 * converted; unsupported constructions keep their original wording.
 */
internal object OwnerMemoryPerspective {
    private val initialPossessive = Regex(
        """^(Mein|Meine|Meinen|Meinem|Meiner|Meines)(\s+)"""
    )
    private val secondPerson = mapOf(
        "Mein" to "Dein",
        "Meine" to "Deine",
        "Meinen" to "Deinen",
        "Meinem" to "Deinem",
        "Meiner" to "Deiner",
        "Meines" to "Deines"
    )

    fun display(fact: String): String {
        val literal = fact.take(250)
        val match = initialPossessive.find(literal)
        if (match != null) {
            val possessive = secondPerson[match.groupValues[1]]!!
            val retold = possessive + literal.substring(match.groupValues[1].length)
            // Preserve the exact owner-confirmed quotation for audits and
            // users who need to distinguish memory from generated phrasing.
            return if (literal.length <= 120)
                "$retold (gespeichert: „$literal“)"
            else retold
        }
        return if (literal.startsWith("Ich ")) "Du hast mir gesagt: „$literal“" else literal
    }
}
