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
    enum class Kind { NEWS_BRIEFING, ASSISTANT_REPLY }

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

        /** Any other assistant answer of the chat as a deliverable; null when there is nothing worth keeping. */
        fun ofReply(text: String, date: LocalDate, createdAtMillis: Long): OwnerDeliverable? {
            val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            if (paragraphs.sumOf { it.length } < 40) return null
            return OwnerDeliverable(Kind.ASSISTANT_REPLY, "Continuum Notiz", date, paragraphs,
                note = "Antwort aus dem Gespräch mit Continuum", createdAtMillis = createdAtMillis)
        }
    }
}
