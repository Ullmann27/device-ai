package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Field finding 19.11.2026 (Fold7): the keyboard put dots where spaces belong – „Erstelle.bitte eine pdf und zeig
 * sie mir.dann“ – and no lane parser recognised the order any more. This repairs exactly that slip at the intake,
 * before any routing reads the turn, and nothing else.
 *
 * Conservative by construction. A dot is turned into a space only inside one token that consists of plain words
 * joined by single dots, where the word after each dot starts in lower case and at least one neighbour of every dot
 * is an everyday word of an order („bitte“, „mir“, „dann“, „das“, „öffne“ …). File names (`Bericht.pdf`), web
 * and mail addresses (`orf.at`, `anna@example.org`), numbers, dates, versions, abbreviations (`z.B.`, `Dr.Huber`)
 * and names (`heinz.ullmann`) never match and stay as they are. A real sentence end („gut. Dann“) is untouched.
 */
internal object OwnerInputRepair {
    private val token = Regex("\\S+")
    private val joinedWords = Regex("^\\p{L}{2,}(?:\\.\\p{Ll}\\p{L}+)+$")
    private val trailing = Regex("[.,!?;:]+$")

    private val everyday = setOf(
        "bitte", "dann", "danach", "und", "mir", "mich", "dir", "dich", "mal", "das", "die", "der", "den", "dem",
        "sie", "ich", "du", "wir", "ein", "eine", "einen", "einem", "einer", "als", "im", "am", "auf", "aus", "mit",
        "von", "vom", "für", "zum", "zur", "noch", "auch", "jetzt", "heute", "gleich", "nicht", "kein", "keine",
        "kannst", "könntest", "soll", "will", "wie", "was", "doch", "nur", "schon", "wieder", "hier", "dort", "alle",
        "alles", "mein", "meine", "meinen", "per", "bei", "nach", "vor", "über", "wenn", "aber",
        "öffne", "zeig", "zeige", "mach", "mache", "erstelle", "erstell", "speichere", "speicher", "schick", "schicke",
        "sende", "such", "suche", "finde", "fass", "fasse", "lies", "starte", "schreib", "schreibe", "sag", "gib",
        "hol", "stell", "lösch", "lösche")
    private val abbreviations = setOf("bzw", "usw", "ca", "nr", "dr", "prof", "etc", "evtl", "ggf", "inkl", "exkl",
        "max", "min", "tel", "str", "zb", "vgl", "sog", "bspw", "mag", "dipl", "ing", "abs", "art", "bzgl", "zzgl")
    private val domainEnds = setOf("at", "de", "com", "org", "net", "ch", "eu", "io", "info", "gv", "co", "tv", "me",
        "it", "es", "fr", "uk", "us", "app", "dev", "ai", "biz", "edu", "gov", "li", "be", "nl", "pl", "cz", "hu")
    private val fileEnds = setOf("pdf", "docx", "doc", "txt", "jpg", "jpeg", "png", "apk", "zip", "mp3", "mp4",
        "csv", "xlsx", "xls", "html", "htm", "json", "md", "odt", "rtf", "gif", "wav", "pptx", "ppt", "litertlm")

    /** [text] with the stray dots between everyday words turned into spaces; unchanged when there are none. */
    fun strayDots(text: String): String {
        if (text.length > 2_000 || '.' !in text) return text
        return token.replace(text) { match -> repaired(match.value) }
    }

    private fun repaired(raw: String): String {
        val tail = trailing.find(raw)?.value.orEmpty()
        val core = raw.dropLast(tail.length)
        if (!joinedWords.matches(core)) return raw
        val words = core.split('.')
        val lower = words.map { it.lowercase(Locale.GERMAN) }
        if (lower.last() in domainEnds || lower.last() in fileEnds || lower.first() == "www") return raw
        if (lower.any { it in abbreviations }) return raw
        for (index in 0 until lower.size - 1) {
            if (lower[index] !in everyday && lower[index + 1] !in everyday) return raw
        }
        return words.joinToString(" ") + tail
    }
}
