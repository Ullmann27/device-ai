package at.heinz.deviceai.cognitive

/**
 * M2: domain-neutral, strictly ephemeral timeline of the owner's own quantity
 * statements in the current session (Argon-4-Dossier 6.2 "bitemporal
 * corrections"), successor of the ring-only TaskScopedRingStateProjector.
 *
 * Reads ONLY the owner's own messages of this session (max. 4 previous + the
 * current one). Never model text, web data or long-term memory; never stored;
 * no tool or memory authority. It recognises "<Artikel> [Adjektiv] Nomen … <Zahl>
 * [Einheit]" statements, including the elliptical "in der blauen 7" that
 * inherits noun and unit from the preceding statement of the same sentence.
 *
 * Fail-closed rules (the whole projection is dropped, nothing is guessed):
 * negation or hedge before a value, "4 oder 5", conditional ("wenn/falls"),
 * explicit past ("gestern/früher/damals"), mixed future in one sentence,
 * two values for the same entity in one message, unit conflicts, >8 entities,
 * oversized/too many turns. Questions ("…?") never assert values.
 * "statt 4", "anstelle von 4" and "von 4 auf 9" select the NEW value.
 */
internal object OwnerStatementTimeline {
    data class Entry(val display: String, val unit: String?, val original: Long, val current: Long,
                     val future: Long?, val changed: Boolean)

    data class Projection(val entries: List<Entry>) {
        fun entry(display: String): Entry? = entries.firstOrNull { it.display == display }
        fun render(): String = buildString {
            append("\nFLÜCHTIGER GESPRÄCHSZUSTAND (nur Aussagen des Nutzers in diesem Gespräch; ")
            append("unbestätigt; nicht speichern; spätere Angaben ersetzen frühere; ")
            append("\"ursprünglich\" = erste Angabe):\n")
            entries.forEach { e ->
                val unit = e.unit?.let { " $it" } ?: ""
                append("- ").append(e.display).append(": ")
                if (e.changed) append("ursprünglich ").append(e.original).append(unit)
                    .append(", aktuell ").append(e.current).append(unit)
                else append(e.current).append(unit).append(" (unverändert)")
                e.future?.let { append(", ab morgen ").append(it).append(unit) }
                append('\n')
            }
        }
    }

    private const val MAX_TURNS = 4
    private const val MAX_ENTITIES = 8
    private val sentence = Regex("""[^.!?;]+[.!?;]?""")
    private const val DET = """(?<![\p{L}\p{N}_])(?:[Dd]er|[Dd]ie|[Dd]as|[Dd]em|[Dd]en)"""
    private val fullEntity = Regex("""$DET\s+(?:([a-zäöüß]+)\s+)?([A-ZÄÖÜ][a-zäöüßA-ZÄÖÜ]+)(?![\p{L}\p{N}_])""")
    private val ellipticEntity = Regex("""$DET\s+([a-zäöüß]+e[nmrs]?)(?=\s+\d)""")
    private val number = Regex("""(?<![\p{L}\p{N}_.,])(\d{1,6})(?![\p{N}.,]\d)(?:\s+(\p{L}+))?""")
    private val replacedMarker = Regex("""(?iu)(?:statt|anstatt|anstelle(?:\s+von)?|von)\s*$""")
    private val negation = Regex("""(?iu)(?<![\p{L}])(?:nicht|kein\p{L}*|niemals|ohne|weder)(?![\p{L}])""")
    private val hedge = Regex("""(?iu)(?<![\p{L}])(?:vielleicht|eventuell|ungefähr|etwa|circa|ca|angeblich|wahrscheinlich|oder)(?![\p{L}])""")
    private val conditional = Regex("""(?iu)(?<![\p{L}])(?:wenn|falls|angenommen|hätte\p{L}*|wäre\p{L}*)(?![\p{L}])""")
    private val pastMarker = Regex("""(?iu)(?<![\p{L}])(?:gestern|früher|damals|vorgestern|letzte\p{L}*)(?![\p{L}])""")
    private val futureMarker = Regex("""(?iu)(?<![\p{L}])(?:ab\s+morgen|morgen|übermorgen)(?![\p{L}])""")
    private val newScenario = Regex(
        """(?iu)(?<![\p{L}])(?:neues?|andere\p{L}*|zweites?|weitere\p{L}*|nächste\p{L}*)\s+(?:beispiel|testaufgabe|denkaufgabe|aufgabe)(?![\p{L}])""")
    private val unitStopwords = setOf("und", "statt", "anstatt", "sondern", "nun", "jetzt", "mehr", "weniger",
        "auf", "von", "hat", "haben", "sind", "ist", "liegen", "im", "in", "der", "die", "das")
    // Grammatical words that look like nouns after a determiner at sentence start.
    private val nonEntityNouns = setOf("Anzahl", "Zahl", "Menge", "Frage", "Antwort", "Aufgabe")

    private class Fact(val key: String, val display: String, val unit: String?, val value: Long, val future: Boolean)

    private fun stem(adjective: String) = adjective.lowercase().replace(Regex("(?:en|em|er|es|e)$"), "")
    // German compounds: "Kupferringe" and "Ringe" are the same counted unit.
    private fun compatibleUnits(a: String, b: String): Boolean {
        val x = unitKey(a); val y = unitKey(b)
        return x.endsWith(y) || y.endsWith(x)
    }
    private fun unitKey(unit: String) = unit.lowercase().replace(Regex("(?:en|e|n|s)$"), "")

    private class Poisoned : RuntimeException()

    /** Facts of one owner message, in order; throws Poisoned on any unsafe statement. */
    private fun facts(text: String): List<Fact> {
        val out = mutableListOf<Fact>()
        for (s in sentence.findAll(text).map { it.value }) {
            val body = s.trim()
            if (body.isEmpty() || body.endsWith("?")) continue
            data class Hit(val start: Int, val end: Int, val adj: String?, val noun: String?)
            val hits = (fullEntity.findAll(body).map { Hit(it.range.first, it.range.last + 1,
                    it.groupValues[1].ifEmpty { null }, it.groupValues[2]) } +
                ellipticEntity.findAll(body).map { Hit(it.range.first, it.range.last + 1, it.groupValues[1], null) })
                .filter { it.noun == null || it.noun !in nonEntityNouns }
                .sortedBy { it.start }.fold(mutableListOf<Hit>()) { acc, h ->
                    if (acc.isEmpty() || h.start >= acc.last().end) acc.add(h); acc }
            val sentenceFacts = mutableListOf<Fact>()
            var lastNoun: String? = null
            var lastUnit: String? = null
            for ((i, hit) in hits.withIndex()) {
                val segEnd = if (i + 1 < hits.size) hits[i + 1].start else body.length
                val segment = body.substring(hit.end, segEnd).take(80)
                val chosen = number.findAll(segment).firstOrNull { m ->
                    !replacedMarker.containsMatchIn(segment.substring(0, m.range.first))
                } ?: run { if (hit.noun != null) lastNoun = hit.noun; null } ?: continue
                val before = segment.substring(0, chosen.range.first)
                val rawUnit = chosen.groupValues[2].ifEmpty { null }
                if (negation.containsMatchIn(before) || hedge.containsMatchIn(before) ||
                    rawUnit?.lowercase() == "oder") throw Poisoned()
                val noun = hit.noun ?: lastNoun ?: continue
                val unit = rawUnit?.takeIf { it.lowercase() !in unitStopwords } ?: lastUnit.takeIf { hit.noun == null }
                val adj = hit.adj?.let(::stem)?.takeIf { it.isNotEmpty() }
                val display = if (adj != null) "$noun ($adj)" else noun
                sentenceFacts += Fact("${noun.lowercase()}|${adj.orEmpty()}", display, unit,
                    chosen.groupValues[1].toLong(), futureMarker.containsMatchIn(body))
                lastNoun = noun; if (unit != null) lastUnit = unit
            }
            if (sentenceFacts.isEmpty()) continue
            if (conditional.containsMatchIn(body) || pastMarker.containsMatchIn(body)) throw Poisoned()
            if (sentenceFacts.any { it.future } && sentenceFacts.size > 1) throw Poisoned()
            out += sentenceFacts
        }
        if (out.groupBy { it.key }.any { (_, v) -> v.size > 1 }) throw Poisoned()
        return out
    }

    fun project(previousOwnerTurns: List<String>, currentTurn: String): Projection? {
        if (previousOwnerTurns.isEmpty() || previousOwnerTurns.size > MAX_TURNS ||
            (previousOwnerTurns + currentTurn).any { it.length !in 1..1_000 }) return null
        class State(val display: String, var unit: String?, val original: Long, var current: Long, var future: Long?)
        val state = LinkedHashMap<String, State>()
        try {
            for ((index, turn) in (previousOwnerTurns + currentTurn).withIndex()) {
                if (index > 0 && newScenario.containsMatchIn(turn)) state.clear()
                for (f in facts(turn)) {
                    val s = state[f.key]
                    if (s == null) {
                        if (f.future) state[f.key] = State(f.display, f.unit, f.value, f.value, f.value).also { return null }
                        state[f.key] = State(f.display, f.unit, f.value, f.value, null)
                        continue
                    }
                    if (f.unit != null && s.unit != null && !compatibleUnits(f.unit, s.unit!!)) return null
                    if (s.unit == null) s.unit = f.unit
                    if (f.future) s.future = f.value else s.current = f.value
                }
            }
        } catch (_: Poisoned) { return null }
        if (state.isEmpty() || state.size > MAX_ENTITIES) return null
        return Projection(state.values.map { Entry(it.display, it.unit, it.original, it.current, it.future,
            it.current != it.original) })
    }
}
