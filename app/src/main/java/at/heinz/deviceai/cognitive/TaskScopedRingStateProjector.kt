package at.heinz.deviceai.cognitive

/**
 * Opt-in, ephemeral experiment for quantified ring-box tasks.
 * Reads only the current session's owner turns; never persists, promotes memory
 * provenance, calls tools, or interprets model output as a verified fact.
 * Unrecognized or ambiguous sentences produce no state projection.
 */
internal object TaskScopedRingStateProjector {
    private val box = Regex("""(?iu)(?<![\p{L}\p{N}])(?:die|der|den|in\s+der)?\s*(roten?|blauen?)\s+kiste\b""")
    private val quantity = Regex("""(?iu)\b(\d{1,3})\s+ringe?\b""")
    private val invalidatingNegation = Regex("""(?iu)\b(?:nicht|keine?|ohne|niemals|weder|angeblich)\b""")
    private val tomorrow = Regex("""(?iu)\b(?:ab\s+morgen|morgen\s+(?:sind|hat|liegen))\b""")
    private val hypothesisCue = Regex("""(?iu)\b(?:beispiel|testaufgabe|denkaufgabe)\b""")
    private val ambiguousQualifier = Regex("""(?iu)\b(?:vielleicht|eventuell|oder|ungefähr)\b""")
    private val deniedTomorrow = Regex("""(?iu)\b(?:nicht|kein\p{L}*)\b[^.!?]{0,25}\bmorgen\b""")
    private val newScenario = Regex(
        """(?iu)\b(?:neues?|andere\p{L}*|zweites?|weitere\p{L}*|nächste\p{L}*)\s+(?:beispiel|testaufgabe|denkaufgabe)\b"""
    )

    data class Slot(val original: Int, val current: Int, val future: Int? = null)
    data class Projection(val red: Slot, val blue: Slot) {
        fun render(): String = buildString {
            append("\nNUR FLÜCHTIGES TESTBEISPIEL (unbestätigte Nutzeraussagen, keine Erinnerung):\n")
            append("Rote Kiste: ursprünglich=").append(red.original)
            append(", aktuell=").append(red.current)
            red.future?.let { append(", ab morgen=").append(it) }
            append(" Ringe\nBlaue Kiste: ursprünglich=").append(blue.original)
            append(", aktuell=").append(blue.current)
            blue.future?.let { append(", ab morgen=").append(it) }
            append(" Ringe\nDie aktuelle Nutzeraussage hat Vorrang; nicht dauerhaft speichern.\n")
        }
    }

    fun project(previousOwnerTurns: List<String>, currentTurn: String): Projection? {
        if (previousOwnerTurns.isEmpty() || previousOwnerTurns.size > 4 ||
            previousOwnerTurns.any { it.length !in 1..1000 } || currentTurn.length !in 1..1000 ||
            previousOwnerTurns.none { hypothesisCue.containsMatchIn(it) }) return null
        var redOriginal: Int? = null
        var blueOriginal: Int? = null
        var redNow: Int? = null
        var blueNow: Int? = null
        var redFuture: Int? = null
        var blueFuture: Int? = null
        for ((turnIndex, text) in (previousOwnerTurns + currentTurn).withIndex()) {
            // A new hypothetical problem must not inherit earlier box values.
            if (turnIndex > 0 && newScenario.containsMatchIn(text)) {
                redOriginal = null; blueOriginal = null; redNow = null; blueNow = null
                redFuture = null; blueFuture = null
            }
            val candidates = box.findAll(text).toList()
            // Mixed same-sentence current/future updates need a real temporal parser.
            if (deniedTomorrow.containsMatchIn(text) ||
                (tomorrow.containsMatchIn(text) && candidates.size > 1)) return null
            for ((i, match) in candidates.withIndex()) {
                val end = if (i + 1 < candidates.size) candidates[i + 1].range.first else text.length
                val clause = text.substring(match.range.last + 1, end).take(75)
                val number = quantity.find(clause) ?: continue
                val beforeQuantity = clause.take(number.range.first)
                if (invalidatingNegation.containsMatchIn(beforeQuantity)) continue
                // Ambiguous language is not a reliable source of state data.
                if (ambiguousQualifier.containsMatchIn(beforeQuantity)) continue
                val value = number.groupValues[1].toIntOrNull() ?: continue
                val future = tomorrow.containsMatchIn(text.take(match.range.first)) &&
                    match.range.first < 60
                when {
                    match.groupValues[1].startsWith("rot", ignoreCase = true) && future -> redFuture = value
                    match.groupValues[1].startsWith("blau", ignoreCase = true) && future -> blueFuture = value
                    match.groupValues[1].startsWith("rot", ignoreCase = true) -> {
                        if (redOriginal == null) redOriginal = value
                        redNow = value
                    }
                    match.groupValues[1].startsWith("blau", ignoreCase = true) -> {
                        if (blueOriginal == null) blueOriginal = value
                        blueNow = value
                    }
                }
            }
        }
        return if (redOriginal != null && blueOriginal != null && redNow != null && blueNow != null)
            Projection(Slot(redOriginal, redNow, redFuture), Slot(blueOriginal, blueNow, blueFuture))
        else null
    }
}
