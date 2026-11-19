package at.heinz.deviceai.sending

import java.util.Locale

/** One phone number of one address-book contact. [e164] is null when Android could not derive the country code. */
internal data class OwnerContactPhone(val contactId: Long, val displayName: String, val raw: String,
                                      val e164: String?, val mobile: Boolean)

/** One e-mail address of one address-book contact. */
internal data class OwnerContactMail(val contactId: Long, val displayName: String, val address: String)

/** The device address book, read only for an owner send order (READ_CONTACTS). Fakes in host tests. */
internal interface OwnerSendContactSource {
    fun phones(): List<OwnerContactPhone>
    fun mails(): List<OwnerContactMail>
}

/**
 * Fixed rule (b): a recipient is exactly one address-book entry – or a question. Several plausible contacts
 * (two „Anna“), several mobile numbers of one contact, no contact, a number without country code: never a guess.
 * Pure Kotlin; the address book comes in as plain rows.
 */
internal object OwnerSendRecipientResolver {
    /** [value]: WhatsApp digits without „+“ or the e-mail address. [label] tells same-named candidates apart. */
    data class Candidate(val displayName: String, val value: String, val label: String)

    sealed interface Resolution {
        data class Resolved(val candidate: Candidate) : Resolution
        data class Ambiguous(val asked: String, val candidates: List<Candidate>, val total: Int) : Resolution
        data class NotFound(val asked: String) : Resolution
        /** A contact matched, but has nothing this channel can use; [reason] is an owner-facing sentence. */
        data class Unusable(val reason: String) : Resolution
    }

    const val MAX_LISTED = 5
    private val leading = setOf("mein", "meine", "meinen", "meinem", "meiner", "unser", "unsere", "unseren", "unserem",
        "unserer", "dem", "der", "den", "die", "da", "an")
    private val digitsOnly = Regex("[1-9][0-9]{6,14}")
    private val mailAddress = Regex("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]{1,63}(?:\\.[A-Za-z0-9-]{1,63})+")

    private fun tokens(name: String): List<String> = name.lowercase(Locale.GERMAN)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(' ').filter { it.isNotEmpty() }

    private fun asked(phrase: String): List<String> = tokens(phrase).dropWhile { it in leading }

    private fun matches(query: List<String>, displayName: String): Boolean =
        query.isNotEmpty() && tokens(displayName).let { name -> query.all { it in name } }

    /** WhatsApp digits of an E.164 number, or null when it is not a full international number. */
    fun waDigits(e164: String?): String? = e164?.trim()?.removePrefix("+")?.filter { it.isDigit() }
        ?.takeIf { e164.trim().startsWith("+") && digitsOnly.matches(it) }

    fun whatsapp(phrase: String, rows: List<OwnerContactPhone>): Resolution {
        val query = asked(phrase)
        if (query.isEmpty()) return Resolution.NotFound(phrase.trim())
        val matched = rows.filter { matches(query, it.displayName) }
        if (matched.isEmpty()) return Resolution.NotFound(phrase.trim())
        // Per contact: its mobile numbers, or – only when none is marked mobile – its other numbers.
        val perContact = matched.groupBy { it.contactId }.values.map { numbers ->
            numbers.filter { it.mobile }.ifEmpty { numbers }
        }
        val pairs = perContact.flatten().distinctBy { tokens(it.displayName) to (waDigits(it.e164) ?: it.raw.filter(Char::isDigit)) }
        val usable = pairs.mapNotNull { row -> waDigits(row.e164)?.let { row to it } }
        if (pairs.size == 1 && usable.isEmpty()) return Resolution.Unusable(
            "Die Nummer von ${pairs.single().displayName} ist ohne Ländervorwahl gespeichert. " +
                "Trag sie bitte mit +43 … im Kontakt ein, dann kann ich den Chat öffnen. Es wurde nichts gesendet.")
        if (usable.size != pairs.size) {
            // Some candidates have no usable number: they still make the name ambiguous, so the owner decides.
            return Resolution.Ambiguous(phrase.trim(), candidates(pairs.map { it to (waDigits(it.e164) ?: "") }), pairs.size)
        }
        return when (usable.size) {
            1 -> Resolution.Resolved(candidates(usable).single())
            else -> Resolution.Ambiguous(phrase.trim(), candidates(usable), usable.size)
        }
    }

    /**
     * „Ruf Anna an“ (briefing lane): the number the dialer is opened with – exactly one number of exactly one
     * contact, or a question. Same name matching and same preference for mobile numbers as [whatsapp]; unlike there,
     * a number stored without country code is usable, because the phone dials it as stored. [Candidate.value] is the
     * number to dial.
     */
    fun dial(phrase: String, rows: List<OwnerContactPhone>): Resolution {
        val query = asked(phrase)
        if (query.isEmpty()) return Resolution.NotFound(phrase.trim())
        val matched = rows.filter { matches(query, it.displayName) }
        if (matched.isEmpty()) return Resolution.NotFound(phrase.trim())
        val perContact = matched.groupBy { it.contactId }.values.map { numbers -> numbers.filter { it.mobile }.ifEmpty { numbers } }
        val pairs = perContact.flatten().mapNotNull { row ->
            val number = (row.e164?.trim()?.takeIf { it.startsWith("+") } ?: row.raw.trim()).filter { it.isDigit() || it == '+' }
            if (number.count(Char::isDigit) < 3) null else row to number
        }.distinctBy { tokens(it.first.displayName) to it.second.filter(Char::isDigit) }
        return when (pairs.size) {
            0 -> Resolution.Unusable("Bei ${matched.first().displayName} ist keine wählbare Nummer gespeichert. Angerufen habe ich niemanden.")
            1 -> Resolution.Resolved(candidates(pairs).single())
            else -> Resolution.Ambiguous(phrase.trim(), candidates(pairs), pairs.size)
        }
    }

    fun mail(phrase: String, rows: List<OwnerContactMail>): Resolution {
        val query = asked(phrase)
        if (query.isEmpty()) return Resolution.NotFound(phrase.trim())
        val matched = rows.filter { matches(query, it.displayName) && mailAddress.matches(it.address.trim()) }
            .distinctBy { tokens(it.displayName) to it.address.trim().lowercase(Locale.ROOT) }
        return when (matched.size) {
            0 -> Resolution.NotFound(phrase.trim())
            1 -> matched.single().let { Resolution.Resolved(Candidate(it.displayName, it.address.trim(), it.displayName)) }
            else -> Resolution.Ambiguous(phrase.trim(), matched.take(MAX_LISTED).map {
                Candidate(it.displayName, it.address.trim(), "${it.displayName} (${it.address.trim()})")
            }, matched.size)
        }
    }

    private fun candidates(pairs: List<Pair<OwnerContactPhone, String>>): List<Candidate> {
        val sameName = pairs.groupBy { tokens(it.first.displayName) }
        return pairs.take(MAX_LISTED).map { (row, digits) ->
            val label = if ((sameName[tokens(row.displayName)]?.size ?: 0) > 1 && digits.length >= 2)
                "${row.displayName} (Nummer endet auf ${digits.takeLast(2)})" else row.displayName
            Candidate(row.displayName, digits, label)
        }
    }

    /**
     * „an Anna Beispiel ich komme später“ without a separator: the longest leading run of words (at most four) that
     * names at least one contact is the recipient, the rest is the text. Null when no leading run names a contact.
     */
    fun split(unsplit: String, names: List<String>): Pair<String, String>? {
        val words = Regex("\\S+").findAll(unsplit).take(4).toList()
        for (count in minOf(4, words.size) downTo 1) {
            val boundary = words[count - 1].range.last + 1
            val prefix = unsplit.substring(0, boundary).trim()
            val query = asked(prefix)
            if (query.isNotEmpty() && names.any { matches(query, it) })
                return prefix to unsplit.substring(boundary).trimStart()
        }
        return null
    }

    /** The owner's answer to „Welche meinst du?“: an ordinal, the full name, a unique part of it, or the end digits. */
    fun pick(answer: String, candidates: List<Candidate>): Candidate? {
        val said = tokens(answer).dropWhile { it in setOf("die", "der", "den", "das", "dem", "nummer", "ich", "meine", "nimm", "bitte") }
        if (said.isEmpty() || candidates.isEmpty()) return null
        ORDINALS.entries.firstOrNull { said.first() in it.value && said.size <= 2 }?.let { return candidates.getOrNull(it.key) }
        if (said.first() in setOf("letzte", "letzter", "letzten") && said.size <= 2) return candidates.last()
        candidates.filter { tokens(it.displayName) == said }.singleOrNull()?.let { return it }
        candidates.filter { candidate -> said.all { it in tokens(candidate.displayName) } }.singleOrNull()?.let { return it }
        val digits = answer.filter(Char::isDigit)
        if (digits.length >= 2) candidates.filter { it.value.endsWith(digits) }.singleOrNull()?.let { return it }
        val address = answer.trim().lowercase(Locale.ROOT)
        return candidates.filter { it.value.lowercase(Locale.ROOT) == address }.singleOrNull()
    }

    private val ORDINALS = mapOf(
        0 to setOf("erste", "erster", "ersten", "erstes", "1", "eins"),
        1 to setOf("zweite", "zweiter", "zweiten", "zweites", "2", "zwei"),
        2 to setOf("dritte", "dritter", "dritten", "drittes", "3", "drei"),
        3 to setOf("vierte", "vierter", "vierten", "viertes", "4", "vier"),
        4 to setOf("fünfte", "fünfter", "fünften", "fünftes", "fuenfte", "5", "fünf", "fuenf"))
}
