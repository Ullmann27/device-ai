package at.heinz.deviceai.cognitive

import java.time.LocalDate
import java.util.Locale

/**
 * Owner wish (18.11.2026): after a result – „speichere das als PDF“, „fass mir die in Word zusammen und speichere
 * sie als PDF in meinen Download-Ordner als Nachrichten von heute“, „als Word-Datei speichern“. A follow-up order on
 * the last deliverable of the chat; it writes one new file into the owner's Download folder and nothing else.
 *
 * Fail closed like [OwnerNewsCommand]: every word in front of the file name must be vocabulary of a save order that
 * points at the result („das“, „die Zusammenfassung“, „die Nachrichten“). „Speichere das“ alone, „merk dir das“,
 * „speichere meine Nummer“ or „lies diese PDF“ are not export orders and keep their lanes.
 */
internal object OwnerExportCommand {
    enum class Format(val extension: String, val mime: String, val label: String) {
        PDF("pdf", "application/pdf", "PDF"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word-Datei")
    }

    /**
     * [name]: the file name the owner said, in his spelling, or null. [viaWord]: he asked to write it „in Word“ and
     * keep it as PDF. [aboutNews]: he named the news explicitly, not just „das“.
     */
    data class Request(val format: Format, val name: String?, val viaWord: Boolean, val aboutNews: Boolean, val original: String,
                       /** „Erstelle mir eine PDF“ without a word that points at a result ([parseMake]). */
                       val unpointed: Boolean = false)

    private val saveVerbs = setOf("speicher", "speichere", "speichern", "speicherst", "abspeichern", "sichere", "sichern",
        "exportiere", "exportier", "exportieren", "ablegen", "leg", "lege", "abzuspeichern")
    private val makeVerbs = setOf("mach", "mache", "machen", "erstell", "erstelle", "erstellen", "erzeug", "erzeuge",
        "erzeugen", "schreib", "schreibe", "schreiben", "fass", "fasse", "zusammenfassen")
    private val pdfWords = setOf("pdf", "pdf-datei", "pdf-dokument", "pdfdatei", "pdf-format")
    private val wordWords = setOf("word", "docx", "word-datei", "word-dokument", "worddatei", "worddokument", "word-format")
    private val formatTarget = Regex("(?:^| )(?:als|in|im|ein|eine|einem|einer|zum|zur) (" +
        (pdfWords + wordWords).joinToString("|") + ")(?= |$)")
    /** Words that point at the result itself. */
    private val pointing = setOf("das", "die", "sie", "es", "dies", "diese", "dieses", "daraus", "davon", "damit",
        "alles", "zusammenfassung", "überblick", "ueberblick", "übersicht", "text", "antwort", "ergebnis", "ergebnisse", "bericht",
        "kurzfassung", "inhalt", "nachrichten", "schlagzeilen", "briefing", "nachrichtenüberblick", "news")
    private val folderWords = setOf("download-ordner", "downloadordner", "downloads", "download", "downloads-ordner",
        "ordner")
    private val newsWords = setOf("nachrichten", "schlagzeilen", "briefing", "nachrichtenüberblick", "news")
    private val references = setOf("das", "die", "sie", "es", "dies", "diese", "dieses", "den", "dem", "der", "daraus",
        "davon", "damit", "alles", "zusammenfassung", "überblick", "ueberblick", "übersicht", "text", "antwort",
        "ergebnis", "ergebnisse", "bericht", "kurzfassung", "inhalt", "datei", "dokument", "ganze", "gesamte") + newsWords
    private val fillers = setOf("ja", "dann", "bitte", "mir", "mal", "noch", "auch", "gleich", "jetzt", "und", "ok",
        "okay", "gut", "super", "danke", "kannst", "könntest", "koenntest", "würdest", "du", "ich", "möchte", "will",
        "hätte", "gern", "gerne", "ab", "als", "in", "im", "ins", "meinen", "meinem", "meine", "mein", "den", "dem",
        "ein", "eine", "einen", "einer", "zusammen", "von", "heute", "für", "mich", "uns", "einfach", "doch", "so",
        "zusätzlich", "außerdem", "nochmal", "lokal", "auf", "handy", "gerät", "dort", "hin", "rein", "format",
        "zu", "kurz", "schnell")
    private val foreign = setOf("nicht", "kein", "keine", "keinen", "niemals", "nie", "ohne", "dir", "gedächtnis",
        "merk", "merke", "lösche", "lösch", "löschen", "öffne", "öffnen", "lies", "lese", "lesen", "schick", "schicke",
        "sende", "senden", "mail", "e-mail", "whatsapp", "kontakt", "foto", "bild", "screenshot", "passwort",
        "nummer", "telefonnummer", "cloud", "drive", "hochladen")
    private val formatHead = "(?:pdf|word|docx|datei|dokument|pdf-datei|pdf-dokument|word-datei|word-dokument)"
    /** „… unter dem Namen X“, „… nenn sie X“, „… als X“ (X is not a format word). The name runs to the end. */
    private val naming = listOf(
        Regex("(?iu)[,;]?\\s+(?:unter\\s+dem\\s+(?:namen|titel)|mit\\s+dem\\s+(?:namen|titel)|(?:mit\\s+)?dateiname(?:n)?|namens)\\s*:?\\s+(.+)$"),
        Regex("(?iu)[,;]?\\s+(?:und\\s+)?nenn(?:e)?\\s+(?:sie|es|ihn|das|die\\s+datei|das\\s+dokument)\\s+(.+)$"),
        Regex("(?iu)[,;]?\\s+als\\s+(?!(?:eine?\\s+|einer\\s+)?$formatHead(?![\\p{L}\\p{N}]))(.+)$")
    )
    private val nameTail = Regex(
        "(?iu)(?:[,;]?\\s+(?:bitte|ab|speichern|abspeichern|danke|in\\s+(?:meinen?|den|die|meinem|dem)\\s+" +
            "download(?:s|-?ordner)?(?:\\s+ab)?|im\\s+download(?:s|-?ordner)?|als\\s+(?:eine?\\s+)?$formatHead))+[.!?]*$")
    /** A slash in a wished file name is made harmless by [fileName]; links and addresses are never export orders. */
    private val linkOrPath = Regex("(?i)(https?://|www\\.|@)")

    fun parse(utterance: String, assistantName: String? = null): Request? {
        if (utterance.length !in 8..300 || utterance.any { it.isISOControl() }) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trimEnd('.', '!', '?', ' ')
        if (linkOrPath.containsMatchIn(text)) return null
        // Split off the file name first: it is free text and not part of the order's vocabulary.
        var order = text
        var name: String? = null
        for (pattern in naming) {
            val match = pattern.find(text) ?: continue
            val candidate = match.groupValues[1].replace(nameTail, "").trim().trim('"', '„', '“', '”', '\'', '.', ',').trim()
            if (candidate.isEmpty() || isPlace(candidate)) continue
            order = text.substring(0, match.range.first) + " " + (nameTail.find(match.groupValues[1])?.value ?: "")
            name = candidate
            break
        }
        val words = order.lowercase(Locale.GERMAN).replace(Regex("[’'`´]"), "").replace(Regex("[^\\p{L}\\p{N}-]+"), " ")
            .split(' ').map { it.trim('-') }.filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 32 || words.any { it in foreign }) return null
        val known = saveVerbs + makeVerbs + pdfWords + wordWords + folderWords + references + fillers
        if (words.any { it !in known }) return null
        // The format must be named as a target („als PDF“, „in Word“, „ein PDF“) – „fasse diese PDF zusammen“ and
        // „speichere die PDF“ speak about an existing file and belong to the document lane.
        val targets = formatTarget.findAll(words.joinToString(" ")).map { it.groupValues[1] }.toList()
        val pdf = targets.any { it in pdfWords }
        val word = targets.any { it in wordWords }
        val folder = words.any { it in folderWords }
        val saves = words.any { it in saveVerbs }
        val makes = words.any { it in makeVerbs }
        val refers = words.any { it in pointing } || name != null
        when {
            // „speichere das als PDF“, „als Word-Datei speichern“, „speichere die Zusammenfassung in meinen Downloads“
            saves && (pdf || word || (folder && refers)) -> Unit
            // „mach mir daraus ein PDF“, „fass mir die in Word zusammen“
            makes && (pdf || word) && refers -> Unit
            else -> return null
        }
        val format = if (pdf || !word) Format.PDF else Format.DOCX
        return Request(format, name?.take(120), viaWord = pdf && word, aboutNews = words.any { it in newsWords } ||
            name?.contains("nachrichten", ignoreCase = true) == true, original = utterance.trim())
    }

    private val makeOnlyVerbs = setOf("mach", "mache", "machen", "machst", "erstell", "erstelle", "erstellen", "erstellst",
        "erzeug", "erzeuge", "erzeugen")
    private val makeArticles = setOf("ein", "eine", "einen")
    private val makeReferences = setOf("davon", "daraus", "dazu", "hiervon", "hieraus", "datei", "dokument")
    private val makeFillers = setOf("bitte", "mir", "mal", "noch", "auch", "gleich", "jetzt", "kannst", "könntest",
        "koenntest", "würdest", "du", "einfach", "doch", "kurz", "schnell", "uns", "dann", "ja")
    private val makeTarget = Regex("(?:^| )(?:ein|eine|einen) (" + (pdfWords + wordWords).joinToString("|") + ")(?= |$)")

    /**
     * Field finding 19.11.2026: „Erstelle bitte eine pdf“, „Mach mir eine Word-Datei dazu“ – the order to make a file
     * of what the chat is about, said without a word that points at it. [parse] keeps refusing this form („of
     * what?“); the caller that knows the chat decides: with a subject worth keeping it is saved, without one the
     * owner is asked what should go into the file. Closed vocabulary: one make verb, an article with the format and
     * polite fillers – „Erstelle eine PDF über Katzen“ or „Mach die PDF auf“ are not this order.
     */
    fun parseMake(utterance: String, assistantName: String? = null): Request? {
        if (utterance.length !in 8..120 || utterance.any { it.isISOControl() }) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trimEnd('.', '!', '?', ' ')
        if (linkOrPath.containsMatchIn(text)) return null
        val words = text.lowercase(Locale.GERMAN).replace(Regex("[’'`´]"), "").replace(Regex("[^\\p{L}\\p{N}-]+"), " ")
            .split(' ').map { it.trim('-') }.filter { it.isNotEmpty() }
        if (words.size !in 3..12) return null
        val known = makeOnlyVerbs + makeArticles + makeReferences + makeFillers + pdfWords + wordWords
        if (words.any { it !in known } || words.count { it in makeOnlyVerbs } != 1) return null
        val targets = makeTarget.findAll(words.joinToString(" ")).map { it.groupValues[1] }.toList()
        if (targets.size != 1) return null
        val format = if (targets.single() in pdfWords) Format.PDF else Format.DOCX
        return Request(format, null, viaWord = false, aboutNews = false, original = utterance.trim(), unpointed = true)
    }

    private fun isPlace(candidate: String): Boolean =
        Regex("(?iu)^(?:in\\s+)?(?:meinen?|den|die|meinem|dem)?\\s*download(?:s|-?ordner)?$").matches(candidate.trim())

    /**
     * The file name for [title] (the deliverable's own title) or the owner's [requested] name: safe characters only,
     * the date added where the name says „heute“ or is the default, never an existing name („… (2)“, „… (3)“).
     */
    fun fileName(requested: String?, title: String, date: LocalDate, format: Format, taken: (String) -> Boolean): String {
        val stamp = OwnerDeliverable.SHORT_DATE.format(date)
        val wanted = requested?.let(::safe)?.removeSuffix("." + format.extension)?.removeSuffix(".pdf")?.removeSuffix(".docx")
            ?.trim()?.takeIf { it.isNotEmpty() }
        val dated = Regex("\\d{1,2}\\.\\s?\\d{1,2}\\.|\\d{4}|\\d{1,2}\\.\\s\\p{L}+")
        var base = when {
            wanted == null -> safe(title).ifEmpty { "Continuum Dokument" } + " " + stamp
            wanted.contains("heut", ignoreCase = true) && !dated.containsMatchIn(wanted) -> "$wanted $stamp"
            else -> wanted
        }
        if (base.length > MAX_BASE) base = base.substring(0, MAX_BASE).trimEnd(' ', '.', '-', '–')
        val first = base + "." + format.extension
        if (!taken(first)) return first
        for (number in 2..MAX_COPIES) {
            val next = "$base ($number)." + format.extension
            if (!taken(next)) return next
        }
        return base + " " + System.nanoTime().toString(36) + "." + format.extension
    }

    const val MAX_BASE = 80
    private const val MAX_COPIES = 99

    /** Characters every file system and share target accepts; no path separators, no control characters. */
    private fun safe(name: String): String = buildString {
        name.forEach { char ->
            when {
                char in "\\/:*?\"<>|" || char.isISOControl() -> append(' ')
                Character.getType(char) == Character.FORMAT.toInt() -> Unit
                else -> append(char)
            }
        }
    }.replace(Regex("\\.{2,}"), ".").replace(Regex("\\s+"), " ").trim('.', ' ')
}
