package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Owner wish (18.11.2026): „Schick die Zusammenfassung per Mail an meinen Chef“ / „… an anna@example.org“.
 * Stage 1 only prepares: the owner's own mail app opens with subject, text and the saved file, and he presses send.
 * No account, no password, no contact access, nothing is sent by the app.
 * Only with the owner's standing „E-Mails über Gmail selbst senden“ grant the same parsed order is handed to the
 * send lane (sending/OwnerSendDirector), which may resolve a name in the contacts and press send in Gmail.
 *
 * The recipient is taken from the owner's own sentence and from nowhere else (see [ExternalContentActionGuard]):
 * an address is passed on, a name („meinen Chef“) is only repeated back – the address field stays empty.
 * Fail closed: every word before „an …“ must be vocabulary of a mail order about the result; messenger words,
 * negations and anything else leave the turn to the other lanes.
 */
internal object OwnerShareCommand {
    /** [mailNamed]: he said „Mail“; without it the order only counts while a result is the current subject. */
    data class Request(val address: String?, val recipientName: String?, val mailNamed: Boolean, val original: String)

    private val verbs = setOf("schick", "schicke", "schicken", "send", "sende", "senden", "verschick", "verschicke",
        "versende", "mail", "maile", "mailen", "leite", "weiterleiten", "übermittle", "uebermittle")
    private val mailWords = setOf("mail", "e-mail", "email", "mails", "e-mails", "gmail", "maile", "mailen")
    private val references = setOf("das", "die", "sie", "es", "dies", "diese", "dieses", "den", "zusammenfassung",
        "überblick", "ueberblick", "übersicht", "text", "antwort", "ergebnis", "bericht", "kurzfassung", "nachrichten",
        "schlagzeilen", "briefing", "pdf", "datei", "dokument", "word-datei", "pdf-datei", "anhang")
    private val fillers = setOf("ja", "dann", "bitte", "mal", "noch", "auch", "gleich", "jetzt", "und", "ok", "okay",
        "gut", "super", "danke", "kannst", "könntest", "koenntest", "würdest", "du", "ich", "möchte", "will", "per",
        "via", "als", "mit", "im", "eine", "einer", "ein", "weiter", "doch", "einfach", "kurz", "schnell", "mir",
        "von", "heute", "samt", "inklusive")
    private val foreign = setOf("nicht", "kein", "keine", "keinen", "niemals", "nie", "ohne", "whatsapp", "sms",
        "signal", "telegram", "messenger", "chat", "nachricht", "sprachnachricht", "facebook", "instagram",
        "lösche", "lösch", "löschen", "foto", "bild", "screenshot", "kontakt", "passwort", "standort", "geld")
    private val address = Regex("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]{1,63}(?:\\.[A-Za-z0-9-]{1,63})+")
    private val recipient = Regex("(?iu)\\s+an\\s+(?!\\s)(.+)$")
    private val recipientTail = Regex(
        "(?iu)(?:[,;]?\\s+(?:bitte|danke|weiter|per\\s+(?:e-?mail|mail)|als\\s+(?:e-?mail|mail)|via\\s+(?:e-?mail|mail)|" +
            "mit\\s+(?:dem\\s+)?(?:pdf|anhang|der\\s+datei)(?:\\s+im\\s+anhang)?))+[.!?]*$")
    private val namePhrase = Regex("^[\\p{L}][\\p{L}.' -]{1,79}$")
    private val pathOrLink = Regex("(?i)(https?://|www\\.|[/\\\\])")

    fun parse(utterance: String, assistantName: String? = null): Request? {
        if (utterance.length !in 10..300 || utterance.any { it.isISOControl() }) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trimEnd('.', '!', '?', ' ')
        if (pathOrLink.containsMatchIn(text)) return null
        val target = recipient.find(text) ?: return null
        val tail = recipientTail.find(target.groupValues[1])?.value.orEmpty()
        val whom = target.groupValues[1].removeSuffix(tail).trim().trim('"', '„', '“', '”', ',', '.').trim()
        val order = text.substring(0, target.range.first) + " " + tail
        val words = order.lowercase(Locale.GERMAN).replace(Regex("[’'`´]"), "").replace(Regex("[^\\p{L}\\p{N}-]+"), " ")
            .split(' ').map { it.trim('-') }.filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 24 || words.any { it in foreign }) return null
        if (words.any { it !in verbs && it !in mailWords && it !in references && it !in fillers }) return null
        if (words.none { it in verbs }) return null
        val mailNamed = words.any { it in mailWords }
        // Without „Mail“ the sentence must at least point at the result („schick das an …“).
        if (!mailNamed && words.none { it in references }) return null
        val found = address.findAll(whom).map { it.value }.toList()
        return when {
            found.size == 1 && whom.replace(found.single(), "").trim().trim('<', '>', '(', ')').isEmpty() ->
                Request(found.single(), null, mailNamed, utterance.trim())
            found.isNotEmpty() -> null // several addresses or an address inside other text: not guessed
            whom.contains('@') -> null
            namePhrase.matches(whom) && whom.lowercase(Locale.GERMAN).split(' ').none { it in foreign } ->
                Request(null, whom, mailNamed, utterance.trim())
            else -> null
        }
    }
}
