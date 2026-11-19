package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Owner goal (18.11.2026): „Öffne das PDF Argon4“ – a PDF the owner names is searched on the device, by name, without
 * network. This object only recognises the order and takes the name out of it; searching, ranking and opening are
 * [OwnerLibraryMatcher] and the document desk.
 *
 * Fail closed. A request without a name („Öffne eine PDF“, „Lies diese PDF …“) is not matched and keeps Android's
 * picker; negations, quotes, web wording, changing or sending verbs and a second order in the same sentence
 * („… und erkläre mir den Inhalt“) are not matched either and keep their present routes.
 */
internal object OwnerLibraryCommand {
    /**
     * [query]: the name as the owner said it, without „.pdf“. [openWhenClear]: false for a pure search („Such das PDF
     * Argon4“, „Wo ist …“) – the hit is then shown and offered, not opened. [folderHint]: one of
     * [OwnerLibraryMatcher.FOLDER_DOWNLOAD], [OwnerLibraryMatcher.FOLDER_DOCUMENTS] or null.
     */
    data class Open(val query: String, val openWhenClear: Boolean, val folderHint: String? = null)

    // Word edges as Unicode lookarounds: `\b` is ASCII-only on the JVM since JDK 19 („Öffne“) but Unicode on Android.
    private const val S = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"
    private const val COURTESY = "(?:(?:bitte|mir|uns|mal|kurz|eben|schnell|jetzt|doch|nochmal)\\s+)*"
    private const val PDF = "(?:pdf|p\\s?d\\s?f)(?:[- ]?(?:datei|dokument|file))?"
    private const val DET = "(?:(das|die|den|dem|mein|meine|meinen|meinem|unser|unsere|ein|eine|einen)\\s+)?"
    private const val NAMED = "((?:namens|mit\\s+dem\\s+namen|mit\\s+namen|genannt)\\s+)?"
    private const val VERBS = "öffne|öffnen|öffnet|zeig|zeige|lies|lese|mach|mache|such|suche|suchen|finde|find|hol|hole"
    private const val PARTICLE = "(?:\\s+(an|auf|vor|heraus|raus))?"

    private val quote = Regex("[\"„“”«»]")
    private val negation = Regex("(?iu)$S(?:nicht|nichts|nie|niemals|ohne|weder|kein\\p{L}*)$E")
    private val web = Regex("(?iu)$S(?:internet|netz|online|web|webseite|website|google|googeln|googel|googele|" +
        "herunter\\p{L}*|runterlad\\p{L}*|runter|downloade[nt]?|hochlad\\p{L}*|hochzuladen|hoch|upload\\p{L}*|cloud|drive|link|url)$E|" +
        "https?://|www\\.")
    private val prohibited = Regex("(?iu)$S(?:lösch|lösche|löschen|entferne|entfernen|sende|senden|schick|schicke|schicken|" +
        "teile|teilen|drucke|drucken|verschiebe|verschieben|benenne|umbenennen|kopiere|kopieren|bearbeite|bearbeiten|" +
        "überschreibe|überschreiben|mail|e-mail|whatsapp|unterschreibe|unterschreiben|signiere|signieren|installiere|" +
        "installieren|lade|laden)$E")
    private val folderTail = Regex("(?iu)\\s+(?:aus|in|im|vom|von)\\s+(?:(?:dem|den|der|meinem|meinen|meiner|meine)\\s+)?" +
        "(?:(download(?:s)?(?:-?ordner)?)|(dokumente[n]?(?:-?ordner)?|documents(?:-?ordner)?))$")
    private val openTail = Regex("(?iu)\\s+und\\s+(?:(?:dann|danach|anschließend|bitte)\\s+)*(?:öffne|zeig|zeige|lies|lese|mach|mache)" +
        "(?:\\s+(?:es|sie|ihn|das|die|mir|bitte|mal|an|auf|vor|datei|pdf)){0,5}$")

    private val imperative = Regex("(?iu)^($VERBS)\\s+$COURTESY(?:nach\\s+)?$DET$PDF\\s+$NAMED(.+)$")
    private val suffixed = Regex("(?iu)^($VERBS)\\s+$COURTESY(?:(?:die|das)\\s+(?:datei|dokument|pdf|pdf-datei)\\s+)?" +
        "([\\p{L}\\p{N}][\\p{L}\\p{N} _.()+&-]{0,79}\\.pdf)$PARTICLE$")
    private val polite = Regex("(?iu)^(?:(?:kannst|könntest|würdest|magst)\\s+du|ich\\s+(?:möchte|will|würde\\s+gern(?:e)?))\\s+" +
        "$COURTESY$DET$PDF\\s+$NAMED(.+?)\\s+(öffnen|aufmachen|anzeigen|zeigen|sehen|ansehen|lesen|vorlesen|suchen|" +
        "heraussuchen|raussuchen|finden)(?:\\s+bitte)?$")
    private const val WISH = "ich\\s+(?:suche|such|brauche|bräuchte|braeuchte)"
    private const val WHICH = "(?:die|das|der|welche|welches)"
    private val where = Regex("(?iu)^(?:wo\\s+(?:ist|liegt|steckt|finde\\s+ich)|$WISH)\\s+$COURTESY$DET$PDF\\s+$NAMED(.+)$")
    /**
     * Field finding 19.11.2026 (spoken): „ich schaue meinen Standort Ordner ich suche eine PDF die heißt a und 4
     * Experimente“. The name follows „heißt“ to the end of the sentence; a few spoken words may come before „ich suche“.
     * It is a search by name on the device (the hits are shown, nothing is opened by itself).
     */
    private val called = Regex("(?iu)^(?:(?:[\\p{L}\\p{N}-]+,?\\s+){0,8})?$WISH\\s+$COURTESY$DET$PDF\\s*,?\\s+$WHICH\\s+hei(?:ß|ss)t\\s+(.+)$")
    /** „Ich brauche das PDF, das Steuerbescheid 2025 heißt“. */
    private val calledAfter = Regex("(?iu)^$WISH\\s+$COURTESY$DET$PDF\\s*,?\\s+$WHICH\\s+(.+?)\\s+hei(?:ß|ss)t$")
    private val searchVerbs = setOf("such", "suche", "suchen", "finde", "find")
    /** Words that start a second order: a name said in a search may contain „und“, but not one of these after it. */
    private val orderVerbs = setOf("öffne", "öffnen", "zeig", "zeige", "lies", "lese", "mach", "mache", "such", "suche", "finde", "find",
        "hol", "hole", "fass", "fasse", "speicher", "speichere", "erklär", "erkläre", "sag", "sage", "schreib", "schreibe",
        "übersetz", "übersetze", "vergleich", "vergleiche", "druck", "drucke", "starte", "start", "ruf", "rufe", "spiel", "spiele",
        "stell", "stelle", "erstell", "erstelle", "merk", "merke", "prüf", "prüfe", "nenn", "nenne", "gib", "leg", "lege")
    private val nameFirst = Regex("(?iu)^($VERBS)\\s+$COURTESY(?:das|die|den|mein|meine)\\s+" +
        "([\\p{L}\\p{N}][\\p{L}\\p{N} _.+&-]{0,59}?)[- ]$PDF$PARTICLE$")

    private val trailingCourtesy = Regex("(?iu)(?:\\s+(?:bitte|mal|danke|jetzt|sofort|nochmal|für\\s+mich))+$")
    private val pdfSuffix = Regex("(?i)\\.pdf$")
    private val nameShape = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N} _.()+&,-]*")
    /** Words that point at a file instead of naming one, or start something that is not a name. */
    private val leading = setOf("diese", "dieses", "dieser", "diesen", "dies", "eine", "ein", "einen", "einer", "irgendeine",
        "irgendein", "welche", "welches", "welcher", "das", "die", "der", "den", "dem", "hier", "dort", "da", "dieselbe",
        "von", "vom", "aus", "über", "zu", "zum", "zur", "im", "in", "auf", "mit", "für", "bei", "an", "am", "ab", "nach",
        "vor", "und", "oder", "ist", "sind", "war", "wird", "was", "wie", "wo", "wegen", "bezüglich",
        // „Lies die PDF Seite 3“ speaks about a page of the open document, not about a file called „Seite 3“.
        "seite", "seiten", "absatz", "kapitel")
    private val pointingAdjective = Regex("(?iu)^(?:ausgewählt|letzt|gleich|selb|vorig|gestrig|erst|zweit|dritt|viert|fünft|" +
        "ander|neu|neuest|neust|aktuell|aktuellst|ganz|gesamt|nächst|vorherig|geöffnet|offen|richtig|falsch)e[nrsm]?$")
    private val joiners = setOf("und", "oder", "sowie", "dann", "danach", "anschließend", "als", "weil", "dass", "damit",
        "bevor", "aber", "sondern", "denn", "wenn")
    private val generic = setOf("bitte", "mal", "kurz", "eben", "jetzt", "hier", "nochmal", "noch", "einmal", "erneut",
        "wieder", "mir", "uns", "vor", "an", "auf", "zu", "zusammen", "weiter", "datei", "dokument", "pdf", "da", "dort",
        "schnell", "gleich", "danke", "heraus", "raus", "her", "es", "sie", "ihn", "doch", "auch")
    private val openingVerbs = setOf("öffne", "öffnen", "öffnet", "zeig", "zeige", "lies", "lese", "mach", "mache", "hol", "hole",
        "aufmachen", "anzeigen", "zeigen", "sehen", "ansehen", "lesen", "vorlesen")

    fun parseOpen(utterance: String, assistantName: String? = null): Open? {
        if (utterance.length !in 8..200 || utterance.any { it.isISOControl() }) return null
        var text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).replace(Regex("(?iu)^bitte\\s+"), "")
            .trim().trimEnd('.', '!', '?', ' ').trim()
        if (text.isEmpty() || ':' in text || quote.containsMatchIn(text) || negation.containsMatchIn(text) ||
            web.containsMatchIn(text) || prohibited.containsMatchIn(text)) return null
        var opens = false
        openTail.find(text)?.let { opens = true; text = text.substring(0, it.range.first).trimEnd() }
        var folder: String? = null
        folderTail.find(text)?.let { match ->
            folder = if (match.groupValues[1].isNotEmpty()) OwnerLibraryMatcher.FOLDER_DOWNLOAD else OwnerLibraryMatcher.FOLDER_DOCUMENTS
            text = text.substring(0, match.range.first).trimEnd()
        }
        fun result(verb: String, raw: String, particle: String?): Open? {
            val verbWord = verb.lowercase(Locale.GERMAN)
            // „Mach … auf“ is the only meaning of „mach“ here; the other particles belong to their own verbs.
            if (verbWord.startsWith("mach") && particle != "auf") return null
            // A pure search may carry a name with „und“ in it („Such das PDF Max und Moritz“); an order to open may not.
            val name = name(raw, lenient = !opens && particle == null && verbWord in searchVerbs) ?: return null
            return Open(name, opens || verbWord in openingVerbs, folder)
        }
        /** The verb particle at the end of a free name („Zeig mir das PDF Argon4 an“). */
        fun split(verb: String, rest: String): Pair<String, String?> {
            val wanted = when {
                verb.startsWith("mach", ignoreCase = true) -> "auf"
                verb.startsWith("zeig", ignoreCase = true) -> "an"
                verb.startsWith("lies", ignoreCase = true) || verb.startsWith("lese", ignoreCase = true) -> "vor"
                verb.startsWith("such", ignoreCase = true) || verb.startsWith("find", ignoreCase = true) -> "heraus|raus"
                else -> return rest to null
            }
            val cleaned = rest.replace(trailingCourtesy, "")
            val match = Regex("(?iu)\\s+($wanted)$").find(cleaned) ?: return rest to null
            return cleaned.substring(0, match.range.first) to match.groupValues[1].lowercase(Locale.GERMAN)
        }
        imperative.matchEntire(text)?.let { match ->
            val (verb, determiner, named, rest) = match.destructured
            if (determiner.startsWith("ein", ignoreCase = true) && named.isEmpty()) return null
            val (free, particle) = split(verb, rest)
            return result(verb, free, particle)
        }
        suffixed.matchEntire(text)?.let { match ->
            return result(match.groupValues[1], match.groupValues[2], match.groupValues[3].lowercase(Locale.GERMAN).ifEmpty { null })
        }
        polite.matchEntire(text)?.let { match ->
            val (determiner, named, rest, verb) = match.destructured
            if (determiner.startsWith("ein", ignoreCase = true) && named.isEmpty()) return null
            val name = name(rest) ?: return null
            return Open(name, opens || verb.lowercase(Locale.GERMAN) in openingVerbs, folder)
        }
        (called.matchEntire(text) ?: calledAfter.matchEntire(text))?.let { match ->
            val name = name(match.groupValues[2], lenient = !opens) ?: return null
            return Open(name, opens, folder)
        }
        where.matchEntire(text)?.let { match ->
            val (determiner, named, rest) = match.destructured
            if (determiner.startsWith("ein", ignoreCase = true) && named.isEmpty()) return null
            val name = name(rest, lenient = !opens) ?: return null
            return Open(name, opens, folder)
        }
        nameFirst.matchEntire(text)?.let { match ->
            val raw = match.groupValues[2]
            if (raw.trim().split(Regex("\\s+")).size > 3) return null
            return result(match.groupValues[1], raw, match.groupValues[3].lowercase(Locale.GERMAN).ifEmpty { null })
        }
        return null
    }

    /**
     * The file name in [raw], or null when what follows the order is not a name. [lenient]: the sentence is a pure
     * search, so one „und“ inside the name is accepted when nothing after it looks like a second order.
     */
    private fun name(raw: String, lenient: Boolean = false): String? {
        val name = raw.replace(trailingCourtesy, "").trim().replace(pdfSuffix, "").trim().trim(',', ';', '-', '–').trim()
        if (name.length !in 1..60 || !nameShape.matches(name)) return null
        val words = name.lowercase(Locale.GERMAN).split(Regex("\\s+")).map { it.trim(',', '.') }.filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 6) return null
        if (words.first() in leading || pointingAdjective.matches(words.first())) return null
        if (words.all { it in generic }) return null
        if (words.any { it in joiners }) {
            val at = words.indexOf("und")
            val tail = if (at < 0) emptyList() else words.drop(at + 1)
            if (!lenient || at <= 0 || words.count { it in joiners } != 1 || tail.size !in 1..3 ||
                tail.any { it in generic || it in leading || it in orderVerbs }) return null
        }
        return name
    }
}

/** Page numbers as the owner says them: „Seite 2“, „Seite zwei“, „die zweite Seite“, „die letzte Seite“. */
internal object OwnerPageReference {
    const val LAST = -1
    private const val S = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"
    private val numbers = listOf("eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf")
    private val ordinals = listOf("erste", "zweite", "dritte", "vierte", "fünfte", "sechste", "siebte", "achte", "neunte",
        "zehnte", "elfte", "zwölfte")
    const val NUMBER = "(\\d{1,4}|eins|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf)"
    const val ORDINAL = "(erste|zweite|dritte|vierte|fünfte|sechste|siebte|siebente|achte|neunte|zehnte|elfte|zwölfte|letzte)[nrsm]?"
    private const val CORE = "(?:seite\\s+(?:nummer\\s+|nr\\.?\\s*)?$NUMBER|$ORDINAL\\s+seite)"
    private const val DOCUMENT = "(?:\\s+(?:des|der|vom|von\\s+dem|von\\s+der|aus\\s+dem|aus\\s+der|im|in\\s+dem|in\\s+der|dieses|dieser)\\s+" +
        "(?:pdf(?:s|-datei|-dokument(?:s|es)?)?|dokument(?:s|es)?|datei)$E)?"

    /** One page named in a sentence; group 1 = number, group 2 = ordinal. */
    val one = Regex("(?iu)$S$CORE$E")
    /** [one] with the words that only attach it to the sentence („von Seite 2 des PDFs“). */
    val withContext = Regex("(?iu)(?:$S(?:von|aus|auf|zu|für)\\s+)?(?:$S(?:die|der)\\s+)?$S$CORE$E$DOCUMENT")
    /** Several pages at once („die Seiten 2 bis 4“). */
    val several = Regex("(?iu)(?:$S(?:von|aus|auf|zu|für)\\s+)?(?:$S(?:die|den)\\s+)?${S}seiten$E" +
        "(?:\\s+(?:$NUMBER|bis|und|oder|-|,|–)){0,8}$DOCUMENT")

    /** The 1-based page of a [one] match, [LAST] for „letzte“, null when it is no usable number. */
    fun page(match: MatchResult): Int? {
        val number = match.groupValues[1].lowercase(Locale.GERMAN)
        val ordinal = match.groupValues[2].lowercase(Locale.GERMAN)
        return when {
            number.isNotEmpty() -> (number.toIntOrNull() ?: (numbers.indexOf(number) + 1)).takeIf { it >= 1 }
            ordinal == "letzte" -> LAST
            ordinal == "siebente" -> 7
            else -> (ordinals.indexOf(ordinal) + 1).takeIf { it >= 1 }
        }
    }
}

/**
 * „Fass Seite 2 zusammen und speichere es als Word“ – one order, several real steps on the PDF that is open in this
 * chat. The page is taken out of the sentence; what remains must be a save order in the vocabulary of
 * [OwnerExportCommand] (same fail-closed rule: every word known, format named as a target, nothing foreign).
 * „Fass Seite 2 zusammen“ alone is not matched and keeps today's answer in the chat.
 */
internal object OwnerPageJobCommand {
    sealed interface Result {
        /** [page]: 1-based, or [OwnerPageReference.LAST]. [format]: null when the owner said „speichere es“ without one. */
        data class Order(val page: Int, val format: OwnerExportCommand.Format?, val name: String?, val original: String) : Result
        /** A save order for several pages at once: said plainly instead of doing half of it. */
        data object SeveralPages : Result
    }

    private const val S = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"
    private val summary = Regex("(?iu)$S(?:fass|fasse|fasst|zusammenfass\\p{L}*|zusammengefasst|zusammenzufassen|kurzfassung)$E")
    private val saveVerb = Regex("(?iu)$S(?:speicher\\p{L}*|abspeicher\\p{L}*|abzuspeichern|sichere|sichern|exportier\\p{L}*|ablegen)$E")
    private val naming = Regex("(?iu)[,;]?\\s+(?:unter\\s+dem\\s+(?:namen|titel)|mit\\s+dem\\s+(?:namen|titel)|(?:mit\\s+)?dateiname(?:n)?|" +
        "namens|(?:und\\s+)?nenn(?:e)?\\s+(?:sie|es|ihn|das|die\\s+datei|das\\s+dokument))$E")
    /** German speech recognition writes the program's name as the German word: „als Wort“, „als Wort-Datei“. */
    private val heardWord = Regex("(?iu)(${S}als\\s+(?:eine?\\s+)?)wort((?:-?(?:datei|dokument|format))?)$E")
    /** … and the imperative „Fass“ as „Fasst“ or „Fast“ at the beginning of the order. */
    private val heardSummarise = Regex("(?iu)^((?:bitte\\s+)?)(?:fasst|fast)(?=\\s+.*${S}zusammen$E)")

    /** [text] with the two usual mishearings of this order repaired; anything else is left as it was said. */
    fun heard(text: String): String = text.replace(heardWord) { it.groupValues[1] + "Word" + it.groupValues[2] }
        .replace(heardSummarise) { it.groupValues[1] + "Fass" }

    fun parse(utterance: String, assistantName: String? = null): Result? {
        if (utterance.length !in 12..300 || utterance.any { it.isISOControl() }) return null
        val said = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trimEnd('.', '!', '?', ' ')
        // The wished file name is free text: it is never „repaired“.
        val nameAt = naming.find(said)?.range?.first ?: said.length
        val text = heard(said.substring(0, nameAt)) + said.substring(nameAt)
        if (!summary.containsMatchIn(text)) return null
        // The wished file name is free text and may itself say „Seite fünf“: pages are only looked for in front of it.
        val split = naming.find(text)?.range?.first ?: text.length
        val head = text.substring(0, split)
        val tail = text.substring(split)
        val several = OwnerPageReference.several.containsMatchIn(head)
        val pages = OwnerPageReference.one.findAll(head).toList()
        if (pages.isEmpty() && !several) return null
        val order = head.replace(OwnerPageReference.several, " ").replace(OwnerPageReference.withContext, " ")
            .replace(Regex("\\s+"), " ").replace(Regex("\\s+,"), ",").trim()
        val export = OwnerExportCommand.parse(order + tail)
        // „… und speichere es“ without a format is still this order; the format is then asked for.
        val unformatted = if (export == null && saveVerb.containsMatchIn(order)) OwnerExportCommand.parse("$order als PDF$tail") else null
        val accepted = export ?: unformatted ?: return null
        if (several || pages.size != 1) return Result.SeveralPages
        val page = OwnerPageReference.page(pages.single()) ?: return null
        return Result.Order(page, export?.format, accepted.name, utterance.trim())
    }
}

/**
 * „Fass Seite 2 zusammen“, „analysiere Seite zwei und fasse sie zusammen“, „erkläre mir die dritte Seite“ – the summary
 * of ONE page of the PDF that is open in this chat, shown in the chat and NOT saved (owner sentence 19.11.2026: „Such
 * die PDF-Datei Argon4, öffne sie, analysiere Seite zwei und fasse sie zusammen“ must not leave a file nobody ordered).
 *
 * It is a step of a sequence only: said alone, „Fass Seite 2 zusammen“ keeps its answer through the reference
 * registry (LIB-160). Fail closed like [OwnerPageJobCommand]: exactly one page, a verb that asks for a summary, and
 * every other word from a small closed vocabulary – a negation, a save order, „in einem Wort“ or anything else
 * foreign is not this order.
 */
internal object OwnerPageSummaryCommand {
    sealed interface Result {
        /** [page]: 1-based, or [OwnerPageReference.LAST]. */
        data class Order(val page: Int, val original: String) : Result
        /** Several pages at once: not this order. */
        data object SeveralPages : Result
    }

    private val asks = Regex("(?iu)(?<![\\p{L}\\p{N}_])(?:fass|fasse|fasst|zusammenfass\\p{L}*|zusammenzufassen|zusammenfassung|" +
        "kurzfassung|analysier\\p{L}*|erklär\\p{L}*|erklaer\\p{L}*|erläuter\\p{L}*|erlaeuter\\p{L}*|beschreib\\p{L}*)(?![\\p{L}\\p{N}_])")
    private val verbs = setOf("fass", "fasse", "fasst", "zusammen", "zusammenfassen", "zusammenfassung", "kurzfassung",
        "analysier", "analysiere", "analysieren", "analysierst", "erklär", "erkläre", "erklären", "erklärst", "erklaer",
        "erklaere", "erläuter", "erläutere", "erläutern", "erlaeutere", "beschreib", "beschreibe", "beschreiben")
    private val fillers = setOf("bitte", "mir", "uns", "mal", "kurz", "knapp", "noch", "dann", "danach", "anschließend",
        "anschliessend", "auch", "und", "sie", "es", "das", "die", "den", "ihn", "inhalt", "text", "davon", "eine", "einen",
        "gib", "mach", "mache", "für", "mich", "einmal", "gleich", "jetzt", "du", "kannst", "könntest", "würdest", "von",
        "dort", "daraus", "ganz", "ganze", "komplett")

    fun parse(utterance: String, assistantName: String? = null): Result? {
        if (utterance.length !in 12..200 || utterance.any { it.isISOControl() }) return null
        val said = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trimEnd('.', '!', '?', ' ')
        val text = OwnerPageJobCommand.heard(said)
        if (!asks.containsMatchIn(text)) return null
        val several = OwnerPageReference.several.containsMatchIn(text)
        val pages = OwnerPageReference.one.findAll(text).toList()
        if (pages.isEmpty() && !several) return null
        val words = text.replace(OwnerPageReference.several, " ").replace(OwnerPageReference.withContext, " ")
            .lowercase(Locale.GERMAN).replace(Regex("[^\\p{L}\\p{N}-]+"), " ").split(' ').map { it.trim('-') }.filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 16 || words.any { it !in verbs && it !in fillers }) return null
        if (words.none { it in verbs && it != "zusammen" }) return null
        if (several || pages.size != 1) return Result.SeveralPages
        val page = OwnerPageReference.page(pages.single()) ?: return null
        return Result.Order(page, utterance.trim())
    }
}

/**
 * The owner corrects the document desk's last order: „Ich meinte Seite 3“, „Nein, Seite 3“, „Nicht Seite 2, sondern
 * Seite 3“, „Ich meinte als PDF“, „Ich meinte die andere Datei“, „Nein, die zweite“, „Nein, Argon4 Datenblatt“.
 * Recognition only – whether there is anything to correct, and what the corrected order is, decides the desk. A
 * correction always carries a correction word; „Schau auf Seite 3“ or „Speichere das als PDF“ are orders of their own.
 */
internal object OwnerDeskCorrection {
    sealed interface Correction {
        /** 1-based page or [OwnerPageReference.LAST]. */
        data class Page(val page: Int) : Correction
        data class Format(val format: OwnerExportCommand.Format) : Correction
        data object OtherFile : Correction
        /** 1-based number in the list of hits that was shown. */
        data class Pick(val number: Int) : Correction
        /** [namedPdf]: the owner said „das PDF …“ – a name that is no shown hit may then start a new search. */
        data class Name(val fragment: String, val namedPdf: Boolean) : Correction
    }

    private const val S = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"
    private val marker = Regex("(?iu)$S(?:meinte|gemeint|nein|nee|nö|sondern|doch|stattdessen|falsch|eigentlich|lieber|statt|" +
        "anstatt|sorry|entschuldigung|korrektur|verhört|versprochen|vertan)$E|${S}ich\\s+meine$E")
    private val negator = Regex("(?iu)$S(?:nicht|statt|anstatt|anstelle|kein|keine)\\s+(?:(?:die|der|das|als|von)\\s+)?$")
    private val common = setOf("nein", "nee", "nö", "ne", "ich", "meinte", "meine", "gemeint", "war", "ist", "nicht",
        "sondern", "doch", "lieber", "stattdessen", "bitte", "falsch", "die", "der", "das", "sorry", "entschuldigung",
        "eigentlich", "wollte", "hatte", "es", "sollte", "sein", "statt", "anstatt", "anstelle", "und", "zwar", "natürlich",
        "also", "ach", "oh", "hab", "habe", "mich", "vertan", "versprochen", "verhört", "korrektur", "nimm", "daraus", "draus",
        "von", "aus", "mit", "dann", "halt", "stopp", "stop", "ja", "mal", "nochmal", "noch", "einmal", "natürlich")
    private val formatWords = setOf("als", "speichern", "speichere", "speicher", "abspeichern", "datei", "format", "in", "im", "sie", "ihn")
    private val fileWords = setOf("öffne", "zeig", "zeige", "mir", "datei", "pdf", "treffer", "den", "dem", "auf", "mach")
    private val formatRef = Regex("(?iu)$S(?:(?:als|in|im)\\s+)?(?:(?:eine?|einer|einem)\\s+)?" +
        "(pdf|pdf-datei|pdf-dokument|pdf-format|word|word-datei|word-dokument|word-format|docx)$E")
    private val otherRef = Regex("(?iu)$S(?:die|das|den|der)\\s+ander(?:e|en|es|er)(?:\\s+(?:datei|pdf|pdf-datei|dokument|treffer))?$E")
    private val pickNumbers = listOf("eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht")
    private val pickOrdinals = listOf("erste", "zweite", "dritte", "vierte", "fünfte", "sechste", "siebte", "achte")
    private val pickRef = Regex("(?iu)$S(?:(?:die|das|den|der|nummer|nr|treffer)\\s+)?(\\d{1,2}|${pickNumbers.joinToString("|")}|" +
        "${pickOrdinals.joinToString("|")})[nrsm]?(?:\\s+(?:datei|pdf|treffer))?$E")
    private const val LEAD = "(?:(?:nein|nee|nö|falsch|sorry|entschuldigung|ach|oh)[,!.]?\\s+)*"
    private const val MEANT = "(?:(?:ich\\s+meinte|ich\\s+meine|gemeint\\s+war|gemeint\\s+ist|ich\\s+wollte)\\s+)?"
    private const val NAMED = "((?:(?:das|die|den)\\s+)?(?:pdf(?:-datei|-dokument)?|datei)\\s+)?"
    private val nameAfter = Regex("(?iu)^$LEAD$MEANT(?:(?:lieber|doch|eigentlich|natürlich)\\s+)*$NAMED(.+)$")
    private val nameBefore = Regex("(?iu)^$NAMED(.+?)\\s+(?:meinte\\s+ich|war\\s+gemeint|habe\\s+ich\\s+gemeint|hab\\s+ich\\s+gemeint)$")
    private val nameOpening = Regex("(?iu)^(?:(?:nein|nee|nö|falsch)$E|.*${S}ich\\s+mein(?:te|e)$E|.*${S}mein(?:te|e)\\s+ich$E|.*${S}gemeint$E)")
    private val nameShape = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N} _.()+&-]*")
    /** A fragment made only of these says nothing about a file. */
    private val empty = setOf("das", "die", "der", "den", "dem", "es", "so", "anders", "anderes", "andere", "etwas", "was",
        "wie", "warum", "ich", "du", "wir", "nicht", "mehr", "noch", "mal", "jetzt", "heute", "morgen", "hier", "danke",
        "passt", "gut", "ok", "okay", "egal", "später", "stimmt", "genau", "richtig", "klar", "doch", "schon", "alles",
        "nichts", "bitte", "ja", "nein", "ganz", "eigentlich", "eben", "halt", "nur", "auch", "gar", "überhaupt", "lieber",
        "gemeint", "meinte", "ernst", "sicher", "wirklich", "natürlich", "kein", "keine", "keinen", "problem", "schade",
        "nochmal", "wieder", "sorry", "entschuldigung", "falsch", "datei", "pdf", "dokument", "seite",
        // „Nein, ich meinte den letzten Entwurf“ / „… die PDF von gestern“ belong to the work journal's correction.
        "entwurf", "entwurfs", "zusammenfassung", "letzte", "letzten", "letztes", "vorige", "vorigen", "vorletzte",
        "vorletzten", "gestrige", "gestrigen", "gestern", "davor", "frühere", "früheren", "von", "vom")
    /** A fragment with one of these is a sentence, not a name. */
    private val sentence = setOf("ist", "sind", "war", "habe", "hab", "hast", "hat", "bin", "wird", "mach", "mache", "soll",
        "sollst", "geh", "gehe", "such", "suche", "öffne", "zeig", "zeige", "speichere", "speicher", "fass", "fasse", "lies",
        "schick", "schicke", "sag", "sage", "schreib", "schreibe", "schau", "schaue", "will", "möchte", "kann", "kannst",
        "weiß", "lass", "vergiss", "steht", "bedeutet", "heißt", "gibt", "nimm", "und", "oder", "aber", "weil", "dass",
        "wenn", "nicht", "kein", "keine", "im", "in", "auf", "über", "als", "internet", "sondern")

    private fun words(text: String): List<String> = text.lowercase(Locale.GERMAN).replace(Regex("[^\\p{L}\\p{N}-]+"), " ")
        .split(' ').map { it.trim('-') }.filter { it.isNotEmpty() }

    private fun ruledOut(text: String, match: MatchResult): Boolean = negator.containsMatchIn(text.substring(0, match.range.first))

    /** Of two named things the one that is not ruled out („nicht Seite 2, sondern Seite 3“); otherwise the last one. */
    private fun <T> meant(text: String, matches: List<MatchResult>, value: (MatchResult) -> T?): T? =
        value(matches.filter { !ruledOut(text, it) }.singleOrNull() ?: matches.last())

    /** „Seite 3, nicht Seite 2“ corrects without any further correction word: exactly one of two is ruled out. */
    private fun contrast(text: String, matches: List<MatchResult>): Boolean =
        matches.size == 2 && matches.count { ruledOut(text, it) } == 1

    fun parse(utterance: String, assistantName: String? = null): Correction? {
        if (utterance.length !in 4..140 || utterance.any { it.isISOControl() }) return null
        val text = OwnerPageJobCommand.heard(OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trimEnd('.', '!', '?', ' ').trim())
        if (text.isEmpty()) return null
        val marked = marker.containsMatchIn(text)
        val pages = OwnerPageReference.one.findAll(text).toList()
        if ((marked || contrast(text, pages)) && pages.size in 1..2 && words(text.replace(OwnerPageReference.one, " ")).all { it in common })
            meant(text, pages, OwnerPageReference::page)?.let { return Correction.Page(it) }
        if (pages.isNotEmpty()) return null
        val formats = formatRef.findAll(text).toList()
        if ((marked || contrast(text, formats)) && formats.size in 1..2 &&
            words(text.replace(formatRef, " ")).all { it in common || it in formatWords })
            meant(text, formats) { match ->
                if (match.groupValues[1].lowercase(Locale.GERMAN).startsWith("pdf")) OwnerExportCommand.Format.PDF
                else OwnerExportCommand.Format.DOCX
            }?.let { return Correction.Format(it) }
        val asksToOpen = words(text).any { it == "öffne" || it == "nimm" || it == "zeig" || it == "zeige" }
        if ((marked || asksToOpen) && otherRef.containsMatchIn(text) &&
            words(text.replace(otherRef, " ")).all { it in common || it in fileWords }) return Correction.OtherFile
        if (marked) pickRef.findAll(text).toList().singleOrNull()?.let { match ->
            if (words(text.replace(pickRef, " ")).all { it in common || it in fileWords }) {
                val raw = match.groupValues[1].lowercase(Locale.GERMAN)
                val number = raw.toIntOrNull() ?: (pickNumbers.indexOf(raw) + 1).takeIf { it > 0 } ?: (pickOrdinals.indexOf(raw) + 1)
                if (number in 1..OwnerLibraryMatcher.MAX_LISTED) return Correction.Pick(number)
            }
        }
        if (!nameOpening.containsMatchIn(text)) return null
        val match = nameBefore.matchEntire(text) ?: nameAfter.matchEntire(text) ?: return null
        // „Ich meinte das Datenblatt“: the article is not part of the name.
        val fragment = match.groupValues[2].replace(Regex("(?iu)(?:\\s+(?:bitte|danke))+$"), "").replace(Regex("(?i)\\.pdf$"), "")
            .trim().trim(',', ';').trim().replace(Regex("(?iu)^(?:das|die|den|der|dem)\\s+(?=\\S)"), "")
        if (fragment.length !in 2..60 || !nameShape.matches(fragment)) return null
        val parts = words(fragment)
        if (parts.isEmpty() || parts.size > 5 || parts.all { it in empty } || parts.any { it in sentence }) return null
        return Correction.Name(fragment, namedPdf = match.groupValues[1].isNotEmpty())
    }
}

/** The owner's answer to „Welches soll ich öffnen?“ about a numbered list of real hits. */
internal object OwnerLibraryChoice {
    sealed interface Answer {
        /** 0-based index into the list that was shown. */
        data class Pick(val index: Int) : Answer
        /** A number the list does not have. */
        data object Invalid : Answer
        data object Drop : Answer
    }

    private const val LEAD = "(?:(?:ja|nein|ich meine|ich meinte|ich nehme|nimm|öffne|zeig|zeige|mach|mir|bitte|doch|lieber|dann) )*"
    private val numbers = listOf("eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht")
    private val ordinals = listOf("erste", "zweite", "dritte", "vierte", "fünfte", "sechste", "siebte", "achte")
    private val drop = Regex("^(?:nein )?(?:keine[ns]?|keins|gar keine[ns]?|nichts)(?: (?:davon|von denen|von beiden|von den beiden))?$|" +
        "^(?:abbrechen|abbruch|lass (?:es|das)(?: (?:sein|bleiben))?|vergiss (?:es|das)|egal|doch nicht|weder noch)$")
    private val number = Regex("^$LEAD(?:(?:der|die|das|den|dem|nummer|nr|treffer|datei|pdf) )*(\\d{1,2}|${numbers.joinToString("|")}|" +
        "${ordinals.joinToString("|")}|letzte)[nrsm]?(?: (?:datei|pdf|treffer|eintrag|bitte|davon|auf|an))*$")
    private val byDate = Regex("^$LEAD(?:(?:der|die|das|den) )?(neueste|neuste|jüngste|aktuellste|älteste)[nrsm]?" +
        "(?: (?:datei|pdf|treffer|bitte|davon))*$")
    private val fillers = setOf("ja", "nein", "ich", "meine", "meinte", "nehme", "nimm", "öffne", "zeig", "zeige", "mir",
        "bitte", "das", "die", "den", "der", "dem", "mit", "pdf", "datei", "dokument", "von", "aus", "im", "in", "ordner",
        "mal", "doch", "lieber", "genau", "dann", "und", "zwar", "eine", "einen", "dieses", "diese", "auf", "mach", "dem")

    fun normalise(utterance: String, assistantName: String?): String =
        OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).lowercase(Locale.GERMAN)
            .replace(Regex("[^\\p{L}\\p{N}._-]+"), " ").replace(Regex("(?<![\\p{L}\\p{N}])[._-]+|[._-]+(?![\\p{L}\\p{N}])"), " ")
            .replace(Regex("\\s+"), " ").trim()

    fun parse(utterance: String, files: List<OwnerLibraryFile>, assistantName: String? = null): Answer? {
        if (utterance.length !in 1..80 || utterance.any { it.isISOControl() } || files.isEmpty()) return null
        val text = normalise(utterance, assistantName)
        if (text.isEmpty()) return null
        if (drop.matches(text)) return Answer.Drop
        number.matchEntire(text)?.let { match ->
            val raw = match.groupValues[1]
            val picked = when {
                raw == "letzte" -> files.size
                else -> raw.toIntOrNull() ?: (numbers.indexOf(raw) + 1).takeIf { it > 0 } ?: (ordinals.indexOf(raw) + 1)
            }
            return if (picked in 1..files.size) Answer.Pick(picked - 1) else Answer.Invalid
        }
        byDate.matchEntire(text)?.let { match ->
            val oldest = match.groupValues[1] == "älteste"
            val wanted = (if (oldest) files.minOf { it.modifiedMillis } else files.maxOf { it.modifiedMillis })
            return files.withIndex().singleOrNull { it.value.modifiedMillis == wanted && wanted > 0L }?.let { Answer.Pick(it.index) }
        }
        val rest = text.split(' ').filter { it.isNotEmpty() && it !in fillers }
        if (rest.isEmpty() || rest.size > 4) return null
        val picked = OwnerLibraryMatcher.pick(rest.joinToString(" "), files).singleOrNull() ?: return null
        return Answer.Pick(files.indexOf(picked))
    }
}

/** Short answers and controls of the document desk: yes, no, the format, and the owner's stop. */
internal object OwnerDeskReply {
    private val yes = Regex("^(?:ja|jo|jawohl|ja bitte|bitte|gern|gerne|ja gern|ja gerne|klar|ja klar|mach|ja mach|mach das|" +
        "ja mach das|ok|okay|genau|ja genau|richtig|stimmt|einverstanden|in ordnung|das meine ich|ja das meine ich|ja das|" +
        "ja die|ja genau das|ja genau die|(?:ja )?(?:bitte )?öffne(?:n)?(?: (?:sie|es|ihn|das|die datei|das pdf|" +
        "die einstellung|die einstellungen|die auswahl|die dateiauswahl))?(?: bitte)?|(?:ja )?mach (?:sie |es )?auf)$")
    private val no = Regex("^(?:nein|nö|nee|ne|nein danke|danke nein|nicht nötig|lass|lass es|lass das|später|jetzt nicht|" +
        "lieber nicht|nein lieber nicht|das meine ich nicht|nein das nicht|nein die nicht|nein das meine ich nicht|" +
        "auf keinen fall|nicht öffnen|nein nicht öffnen|bitte nicht)$")
    private val format = Regex("^(?:(?:als|in|im) )?(?:(?:eine|ein|einer|einem) )?(word|word-datei|word-dokument|word-format|docx|" +
        "pdf|pdf-datei|pdf-dokument|pdf-format)(?: (?:bitte|speichern))*$")
    private val stop = Regex("^(?:bitte )?(?:stopp?|halt|halt stopp?|stopp? halt|stopp? stopp?|abbrechen|abbruch|aufhören|hör auf|" +
        "brich (?:das |es |alles )?ab|anhalten|cancel|stopp? (?:das|es|alles|jetzt|sofort)|halt (?:an|ein)|das abbrechen|" +
        "vorgang abbrechen|auftrag abbrechen|nicht weitermachen)(?: (?:bitte|jetzt|sofort))*$")

    private fun plain(utterance: String, assistantName: String?): String =
        if (utterance.length > 60) "" else OwnerLibraryChoice.normalise(utterance, assistantName)

    fun isYes(utterance: String, assistantName: String? = null): Boolean = yes.matches(plain(utterance, assistantName))
    fun isNo(utterance: String, assistantName: String? = null): Boolean = no.matches(plain(utterance, assistantName))
    fun format(utterance: String, assistantName: String? = null): OwnerExportCommand.Format? =
        format.matchEntire(OwnerPageJobCommand.heard(plain(utterance, assistantName)).lowercase(Locale.GERMAN))?.let {
            if (it.groupValues[1].startsWith("pdf")) OwnerExportCommand.Format.PDF else OwnerExportCommand.Format.DOCX
        }

    /** The whole utterance is a stop word; „Stopp die Musik“ or „Halt das fest“ are orders of their own. */
    fun isStop(utterance: String, assistantName: String? = null): Boolean = stop.matches(plain(utterance, assistantName))
}
