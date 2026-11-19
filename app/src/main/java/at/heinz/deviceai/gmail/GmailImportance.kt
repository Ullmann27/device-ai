package at.heinz.deviceai.gmail

import java.util.Locale

/**
 * „Wichtig“ is a transparent, deterministic ranking – no model decides it. Points per mail:
 *  - ungelesen +3 · von einem Kontakt aus deinem Adressbuch +2 · direkt an dich (du stehst in „An“) +2 ·
 *    Fristwörter im Betreff oder Vorschautext (Frist, fällig, Mahnung, bis zum, spätestens, Termin, …) +2 ·
 *    aus den letzten 24 Stunden +1 · Newsletter/Werbung (Abmeldelink, Werbe-Kategorie) −3.
 * Ties: the newer mail first. The model only words a summary of what this ranking chose.
 */
internal object GmailImportance {
    data class Ranked(val message: GmailMessage, val points: Int, val reasons: List<String>)

    private val deadlineWords = Regex("(?iu)(?<![\\p{L}])(frist|fristen|fristablauf|fällig|faellig|mahnung|zahlungserinnerung|" +
        "spätestens|spaetestens|bis zum|bis spätestens|deadline|termin|termine|einladung|ladung|rückmeldung bis|antwort bis|" +
        "dringend|eilt|ablauf|läuft ab|laeuft ab|kündigung|kuendigung)(?![\\p{L}])")

    const val CRITERIA = "So bestimme ich, was wichtig ist – ohne Modell, nach festen Punkten: ungelesen +3, von einem Kontakt " +
        "aus deinem Adressbuch +2, direkt an dich adressiert +2, Fristwörter wie „Frist“, „fällig“, „Mahnung“ oder „bis zum“ " +
        "im Betreff oder Vorschautext +2, aus den letzten 24 Stunden +1, Newsletter oder Werbung −3. Bei Gleichstand " +
        "kommt die neuere Mail zuerst. Das Sprachmodell formuliert danach nur die Zusammenfassung."

    fun hasDeadlineWords(text: String): Boolean = deadlineWords.containsMatchIn(text)

    /**
     * [contacts]: lower-case addresses of the owner's address book, null when it may not be read (the criterion then
     * counts for nobody and the explanation says so). [owner]: the connected account's address.
     */
    fun rank(messages: List<GmailMessage>, contacts: Set<String>?, owner: String?, nowMillis: Long): List<Ranked> =
        messages.map { message ->
            val reasons = ArrayList<String>()
            var points = 0
            if (message.unread) { points += 3; reasons += "ungelesen" }
            if (contacts != null && message.fromAddress != null && message.fromAddress in contacts) { points += 2; reasons += "von einem Kontakt" }
            if (owner != null && message.to.any { it.equals(owner, ignoreCase = true) }) { points += 2; reasons += "direkt an dich" }
            if (hasDeadlineWords(message.ref.subject + " " + message.snippet)) { points += 2; reasons += "Fristwörter" }
            if (nowMillis - message.ref.dateMillis in 0..DAY_MILLIS) { points += 1; reasons += "von heute" }
            if (message.bulk) { points -= 3; reasons += "Newsletter oder Werbung" }
            Ranked(message, points, reasons)
        }.sortedWith(compareByDescending<Ranked> { it.points }.thenByDescending { it.message.ref.dateMillis })

    const val DAY_MILLIS = 24 * 3_600_000L
}

/**
 * „Was steht da wegen Fristen/Terminen?“ – the lines of the mail that carry a date, a time or a deadline word, quoted
 * exactly as they stand. Nothing is computed or guessed: a line is evidence, not an interpretation.
 */
internal object GmailDeadlines {
    data class Evidence(val line: String, val date: Boolean, val deadlineWord: Boolean)

    private val date = Regex("(?iu)(?<!\\d)(\\d{1,2}\\.\\s?\\d{1,2}\\.(\\s?\\d{2,4})?|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\.\\s?(jänner|januar|februar|" +
        "märz|maerz|april|mai|juni|juli|august|september|oktober|november|dezember)|(montag|dienstag|mittwoch|donnerstag|freitag|" +
        "samstag|sonntag)|(um\\s+\\d{1,2}([:.]\\d{2})?\\s*uhr)|(\\d{1,2}:\\d{2}\\s*uhr)|morgen|übermorgen|nächste[nr]? woche)")
    const val MAX_LINES = 8
    const val MAX_LINE_CHARS = 220

    fun find(text: String): List<Evidence> = text.lines().asSequence()
        .map { it.trim() }.filter { it.length in 4..2_000 }
        // Quoted history of earlier mails („> …“) is not this mail's statement.
        .filterNot { it.startsWith(">") }
        .mapNotNull { line ->
            val hasDate = date.containsMatchIn(line)
            val hasWord = GmailImportance.hasDeadlineWords(line)
            if (hasDate || hasWord) Evidence(shorten(line), hasDate, hasWord) else null
        }.distinctBy { it.line.lowercase(Locale.GERMAN) }
        .take(MAX_LINES).toList()

    private fun shorten(line: String): String = if (line.length <= MAX_LINE_CHARS) line else line.take(MAX_LINE_CHARS - 1).trimEnd() + "…"
}
