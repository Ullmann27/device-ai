package at.heinz.deviceai.cognitive

/**
 * Separates an owner fact from a trailing dialogue/meta instruction such as
 * „Danach fragen: ‚Wie lautet mein Testwort?‘“ (#640).
 *
 * Pure text logic, no storage access. Used in two places:
 *  - on write („Merke dir: …“) so that only the fact itself is remembered and
 *    the instruction is acknowledged separately;
 *  - on display of already stored entries, presentation-only, so older records
 *    are never rewritten silently.
 */
internal object OwnerFactMetaSeparator {
    data class Split(val fact: String, val instruction: String?)

    // Owner meta instructions that describe what to do AFTER remembering; they
    // are never part of the fact. Only matched after a sentence boundary or a
    // closing quotation mark so that facts containing these words stay intact.
    private val metaStart = Regex(
        "(?iu)(?:(?<=[.!?“”\"»«‘’'])|^)\\s*(?:[„“\"»«]\\s*)?" +
            "(?:danach|dann|anschließend|später|nachher|zum test|als test|zur kontrolle)?\\s*" +
            "(?:frag(?:e|en)?(?:\\s+mich)?(?:\\s+danach|\\s+dann|\\s+später)?|" +
            "prüf(?:e|en)?(?:\\s+(?:das|dies|es))?(?:\\s+danach|\\s+dann)?|" +
            "teste(?:\\s+(?:das|dies|es))?(?:\\s+danach|\\s+dann)?|" +
            "abfrage|rückfrage|kontrollfrage|testfrage)\\s*[:：]"
    )

    fun split(raw: String): Split {
        val text = raw.trim()
        val match = metaStart.find(text) ?: return Split(text, null)
        if (match.range.first == 0) return Split(text, null)
        val fact = text.substring(0, match.range.first).trim().trimEnd('„', '“', '"', ' ')
        val instruction = text.substring(match.range.first).trim().trimStart('„', '“', '”', '"', ' ')
        if (fact.length < 3 || instruction.isEmpty()) return Split(text, null)
        return Split(fact, instruction)
    }

    fun hasInstruction(raw: String): Boolean = split(raw).instruction != null

    fun writeNote(instruction: String): String =
        "Die angehängte Anweisung („${instruction.take(120)}“) habe ich nicht als Fakt gespeichert; " +
            "sie ist eine Dialoganweisung. Stell die Frage einfach, wenn du prüfen willst."
}
