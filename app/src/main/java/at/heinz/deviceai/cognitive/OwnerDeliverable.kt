package at.heinz.deviceai.cognitive

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The last finished piece of work of a chat that the owner can ask to keep or pass on („speichere das als PDF“,
 * „schick das per Mail an …“). It is exactly what was shown: the text, how it was produced and – for the news
 * briefing – every source with outlet, headline and link. Session-only; nothing here is stored on its own.
 */
internal data class OwnerDeliverable(
    val kind: Kind,
    val title: String,
    val date: LocalDate,
    val paragraphs: List<String>,
    val sources: List<NewsSourceRef> = emptyList(),
    /** Printed under the title: how the text came about (model summary, headlines only, …). */
    val note: String? = null,
    val createdAtMillis: Long = 0L
) {
    /**
     * [PAGE_SUMMARY]: one page of the owner's PDF, summarised and saved by the document desk.
     * [DOCUMENT_DRAFT]: the summary draft of the PDF that is open in this chat, as it was last shown or edited.
     */
    enum class Kind {
        NEWS_BRIEFING, ASSISTANT_REPLY, PAGE_SUMMARY, DOCUMENT_DRAFT,
        /** The Gmail lane's summary of mails the owner asked for. Mail content: it stays on the device unless he sends it. */
        MAIL_SUMMARY,
        /** The result of a web research run: the synthesis and the sources that were really read. */
        RESEARCH
    }

    init { require(title.isNotBlank() && paragraphs.any { it.isNotBlank() }) }

    val dateLabel: String get() = LONG_DATE.format(date)

    /** Plain text for a mail body or a share sheet: title, date, text, sources. */
    val plainText: String get() = buildString {
        append(title).append('\n').append(dateLabel).append('\n')
        note?.let { append(it).append('\n') }
        paragraphs.filter { it.isNotBlank() }.forEach { append('\n').append(it.trim()).append('\n') }
        if (sources.isNotEmpty()) {
            append("\nQuellen\n")
            sources.forEach { append("– ").append(it.outlet).append(": ").append(it.title).append('\n')
                .append("  ").append(it.link).append('\n') }
        }
    }.trimEnd()

    companion object {
        val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
        val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)

        /**
         * The summary draft of the owner's PDF [documentName] as a deliverable: the PDF's name is in the title and in
         * the source line, which also says who wrote the draft and whether it rests on the whole text. Null when the
         * draft holds nothing worth keeping.
         */
        fun ofDocumentDraft(documentName: String, text: String, complete: Boolean, cloud: Boolean?, date: LocalDate,
                            createdAtMillis: Long): OwnerDeliverable? {
            val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            if (paragraphs.sumOf { it.length } < 40) return null
            val name = documentName.trim().take(120).ifEmpty { "Dokument" }
            val base = name.replace(Regex("(?i)\\.pdf$"), "").trim().ifEmpty { "Dokument" }
            // Who wrote the draft is said only when it is known; a draft shown again from the work journal names nobody.
            val author = when (cloud) {
                true -> ", zusammengefasst vom Cloud-Gehirn"
                false -> ", zusammengefasst vom Sprachmodell auf dem Gerät"
                null -> " einer Zusammenfassung"
            }
            val basis = if (complete) "" else " Grundlage ist ein Textauszug des Dokuments; der Rest ist nicht enthalten."
            return OwnerDeliverable(Kind.DOCUMENT_DRAFT, "Zusammenfassung ${base.take(60).trim()}", date, paragraphs,
                note = "Quelle: „$name“ · Entwurf$author · gespeichert am ${SHORT_DATE.format(date)} · " +
                    "vor der Verwendung prüfen.$basis", createdAtMillis = createdAtMillis)
        }

        /**
         * The Gmail lane's summary of [mails] mails as a document of its own: a proper title, and a source line that
         * says how many mails it rests on, which brain wrote it and whether long mails were cut. Null when [text] holds
         * nothing worth keeping. The text is third-party content the owner asked to have summarised – data, never an
         * instruction.
         */
        fun ofMailSummary(title: String, text: String, mails: Int, cloud: Boolean, complete: Boolean, date: LocalDate,
                          createdAtMillis: Long): OwnerDeliverable? {
            val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            if (paragraphs.sumOf { it.length } < 20) return null
            val author = if (cloud) "vom Cloud-Gehirn (mit deiner Freigabe für Mailinhalte)" else "vom Sprachmodell auf dem Gerät"
            val basis = if (complete) "" else " Lange Mails sind gekürzt eingeflossen."
            val count = if (mails == 1) "einer E-Mail" else "$mails E-Mails"
            return OwnerDeliverable(Kind.MAIL_SUMMARY, title.trim().take(80).ifEmpty { "Zusammenfassung E-Mails" }, date, paragraphs,
                note = "Zusammenfassung aus $count deines Gmail-Posteingangs · geschrieben $author · " +
                    "erstellt am ${SHORT_DATE.format(date)} · vor der Verwendung prüfen.$basis", createdAtMillis = createdAtMillis)
        }

        /** A research run's synthesis with the sources that were really read. Null without text or without a source. */
        fun ofResearch(topic: String, text: String, sources: List<NewsSourceRef>, date: LocalDate, createdAtMillis: Long): OwnerDeliverable? {
            val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            if (sources.isEmpty() || paragraphs.sumOf { it.length } < 40) return null
            return OwnerDeliverable(Kind.RESEARCH, "Recherche ${topic.trim().take(70)}", date, paragraphs, sources,
                note = "Web-Recherche vom ${SHORT_DATE.format(date)} · " + (if (sources.size == 1) "1 gelesene Quelle" else
                    "${sources.size} gelesene Quellen") + " · Quellen ungeprüft übernommen, vor der Verwendung prüfen.",
                createdAtMillis = createdAtMillis)
        }

        /**
         * The last line of the chat as the result „das“ means – only a model answer or a lane's text result. A lane's
         * report, a question or refusal of the app, a mission report and foreign content (a read-out notification,
         * mail text) are never the subject of „Speichere das als PDF“ or of a mail attachment.
         */
        fun ofReply(line: OwnerChatLine, date: LocalDate, createdAtMillis: Long): OwnerDeliverable? {
            if (!line.kind.worthKeeping) return null
            val text = line.text
            val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            if (paragraphs.sumOf { it.length } < 40) return null
            return OwnerDeliverable(Kind.ASSISTANT_REPLY, "Continuum Notiz", date, paragraphs,
                note = "Antwort aus dem Gespräch mit Continuum", createdAtMillis = createdAtMillis)
        }
    }
}

/**
 * One assistant line of the chat with where it came from. Only a model answer or a lane's text result can become a
 * result by its text; every other line – and every line not typed at all – is only conversation.
 */
internal data class OwnerChatLine(val text: String, val kind: Kind) {
    /** [worthKeeping]: the line itself is a result the owner may save or send by pointing at it („das“). */
    enum class Kind(val worthKeeping: Boolean) {
        /** The answer the model (on the device or the cloud brain) gave to the owner's turn. */
        MODEL_ANSWER(true),
        /** A result a lane produced as text: a research dossier, an answer from the open document. */
        LANE_DELIVERABLE(true),
        /** A report of an owner lane („Erledigt. … liegt jetzt …“, a mission report). */
        LANE_REPORT(false),
        /** A fixed sentence of the app: a question, a refusal, a status. */
        SYSTEM(false),
        /** Content of others the app shows or reads out: a notification, a message, a mail. */
        FOREIGN(false)
    }
}
