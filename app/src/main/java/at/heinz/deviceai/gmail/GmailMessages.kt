package at.heinz.deviceai.gmail

import org.json.JSONObject
import java.util.Locale

/**
 * One mail as the mission layer and the stage refer to it: ids and the headers the owner sees. No body.
 * [from] is the display form („Anna Beispiel“, else the address).
 */
internal data class MailRef(val id: String, val threadId: String, val from: String, val subject: String, val dateMillis: Long)

/** One attachment as Gmail lists it; its bytes are fetched only on the owner's explicit order. */
internal data class GmailAttachmentRef(val messageId: String, val attachmentId: String, val name: String, val mime: String, val size: Int)

/**
 * A parsed mail. [body] is null for metadata reads. [truncated]: the body was longer than the bound and is cut.
 * Everything here is untrusted data of a third party; it is never an instruction to the app.
 */
internal data class GmailMessage(
    val ref: MailRef,
    val fromAddress: String?,
    val to: List<String>,
    val cc: List<String>,
    val labels: Set<String>,
    val snippet: String,
    val messageIdHeader: String?,
    val references: String?,
    val bulk: Boolean,
    val body: String? = null,
    val truncated: Boolean = false,
    val fromHtml: Boolean = false,
    val attachments: List<GmailAttachmentRef> = emptyList()
) {
    val unread: Boolean get() = "UNREAD" in labels

    /** The text a lane may show for this mail: the body when read, else Gmail's snippet. */
    val text: String get() = body ?: snippet

    override fun toString(): String = "GmailMessage(${ref.id}, ${labels.size} Labels, Text ${text.length} Zeichen)"
}

/**
 * Gmail's message resource (format=metadata or format=full) as a [GmailMessage]. The body is the first non-empty
 * text/plain part; without one, the first text/html part stripped to text. Bounded by [MAX_BODY_CHARS]; parts are
 * walked at most [MAX_DEPTH] deep and [MAX_PARTS] wide.
 */
internal object GmailPayloadParser {
    const val MAX_BODY_CHARS = 12_000
    const val MAX_DEPTH = 8
    const val MAX_PARTS = 64

    /** [withBody]: the resource was fetched with format=full; a metadata read has no body (null, not empty). */
    fun parse(json: JSONObject, withBody: Boolean, maxBodyChars: Int = MAX_BODY_CHARS): GmailMessage? {
        val id = json.optString("id").takeIf(GmailApi::validId) ?: return null
        val threadId = json.optString("threadId").takeIf(GmailApi::validId) ?: id
        val payload = json.optJSONObject("payload")
        val headers = headers(payload)
        fun header(name: String) = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
        val (fromName, fromAddress) = address(header("From").orEmpty())
        val labels = json.optJSONArray("labelIds")?.let { rows -> (0 until rows.length()).map { rows.optString(it) }.toSet() } ?: emptySet()
        val date = json.optString("internalDate").toLongOrNull() ?: 0L
        val subject = GmailMime.headerValue(GmailMime.decodeHeader(header("Subject").orEmpty())).take(200).ifEmpty { "(kein Betreff)" }
        val ref = MailRef(id, threadId, (fromName ?: fromAddress ?: "unbekannt").take(120), subject, date)
        val parts = Parts()
        if (payload != null && withBody) walk(payload, parts, 0, id)
        val plain = parts.plain.firstOrNull { it.isNotBlank() }
        val html = if (plain == null) parts.html.firstOrNull { it.isNotBlank() }?.let(::htmlToText) else null
        val full = (plain ?: html)?.let(::normalise)
        val body = full?.let { if (it.length > maxBodyChars) cut(it, maxBodyChars) else it }
        return GmailMessage(ref, fromAddress, addresses(header("To")), addresses(header("Cc")), labels,
            GmailMime.headerValue(htmlEntities(json.optString("snippet"))).take(300), header("Message-ID")?.trim(),
            header("References")?.trim(), bulk = header("List-Unsubscribe") != null || labels.any { it in BULK_LABELS },
            body = if (withBody) body.orEmpty() else null,
            truncated = full != null && full.length > maxBodyChars, fromHtml = plain == null && html != null,
            attachments = parts.attachments.take(20))
    }

    private val BULK_LABELS = setOf("CATEGORY_PROMOTIONS", "CATEGORY_SOCIAL", "CATEGORY_FORUMS", "SPAM")

    private class Parts {
        val plain = ArrayList<String>()
        val html = ArrayList<String>()
        val attachments = ArrayList<GmailAttachmentRef>()
        var count = 0
    }

    private fun headers(part: JSONObject?): List<Pair<String, String>> {
        val rows = part?.optJSONArray("headers") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
            .map { it.optString("name") to it.optString("value") }
    }

    private fun walk(part: JSONObject, out: Parts, depth: Int, messageId: String) {
        if (depth > MAX_DEPTH || ++out.count > MAX_PARTS) return
        val mime = part.optString("mimeType").lowercase(Locale.ROOT)
        val children = part.optJSONArray("parts")
        if (mime.startsWith("multipart/") || children != null) {
            children?.let { rows -> (0 until rows.length()).forEach { index -> rows.optJSONObject(index)?.let { walk(it, out, depth + 1, messageId) } } }
            return
        }
        val body = part.optJSONObject("body")
        val filename = part.optString("filename").trim()
        val attachmentId = body?.optString("attachmentId")?.takeIf { it.isNotBlank() }
        if (filename.isNotEmpty() || (attachmentId != null && !mime.startsWith("text/"))) {
            if (attachmentId != null && GmailApi.validId(attachmentId))
                out.attachments += GmailAttachmentRef(messageId, attachmentId, GmailMime.headerValue(GmailMime.decodeHeader(filename)).take(160)
                    .ifEmpty { "Anhang" }, mime.ifEmpty { "application/octet-stream" }, body.optInt("size", 0))
            return
        }
        if (mime != "text/plain" && mime != "text/html") return
        val bytes = GmailMime.decodeBase64Url(body?.optString("data")) ?: return
        val charset = charset(headers(part).firstOrNull { it.first.equals("Content-Type", true) }?.second)
        val text = String(bytes, charset)
        if (mime == "text/plain") out.plain += text else out.html += text
    }

    private fun charset(contentType: String?): java.nio.charset.Charset {
        val name = contentType?.let { Regex("(?i)charset\\s*=\\s*\"?([A-Za-z0-9._-]+)").find(it)?.groupValues?.get(1) }
        return name?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
    }

    /** „Anna Beispiel <anna@example.org>“ → (name, address); a bare address gives (null, address). */
    fun address(raw: String): Pair<String?, String?> {
        val value = GmailMime.headerValue(GmailMime.decodeHeader(raw))
        val angle = Regex("<([^<>\\s]+@[^<>\\s]+)>").find(value)
        val address = (angle?.groupValues?.get(1) ?: value.takeIf { GmailMime.validAddress(it) })?.lowercase(Locale.ROOT)
        val name = (if (angle != null) value.substring(0, angle.range.first) else "").trim().trim('"', '\'', ' ').takeIf { it.isNotEmpty() }
        return name to address
    }

    fun addresses(raw: String?): List<String> = raw.orEmpty().split(',').mapNotNull { address(it).second }.distinct().take(50)

    /** Text of an HTML mail: no scripts, styles, tags or comments; block ends become line breaks; entities decoded. */
    fun htmlToText(html: String): String {
        var text = html.take(400_000)
        text = text.replace(Regex("(?is)<!--.*?-->"), " ")
        text = text.replace(Regex("(?is)<(script|style|head|title)\\b.*?</\\1\\s*>"), " ")
        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?i)</(p|div|tr|li|h[1-6]|table|blockquote)\\s*>"), "\n")
        text = text.replace(Regex("(?i)<li\\b[^>]*>"), "\n– ")
        text = text.replace(Regex("(?s)<[^>]{0,2000}>"), " ")
        return htmlEntities(text)
    }

    fun htmlEntities(text: String): String = text
        .replace(Regex("&#(\\d{1,7});")) { m -> m.groupValues[1].toIntOrNull()?.takeIf { it in 1..0x10FFFF && it !in 0xD800..0xDFFF }
            ?.let { String(Character.toChars(it)) } ?: " " }
        .replace(Regex("(?i)&#x([0-9a-f]{1,6});")) { m -> m.groupValues[1].toIntOrNull(16)?.takeIf { it in 1..0x10FFFF && it !in 0xD800..0xDFFF }
            ?.let { String(Character.toChars(it)) } ?: " " }
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&apos;", "'").replace("&auml;", "ä").replace("&ouml;", "ö").replace("&uuml;", "ü").replace("&Auml;", "Ä")
        .replace("&Ouml;", "Ö").replace("&Uuml;", "Ü").replace("&szlig;", "ß").replace("&euro;", "€").replace("&amp;", "&")

    /** Line breaks kept, runs of blanks and blank lines collapsed, control characters removed. */
    fun normalise(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')
        .filter { it == '\n' || it == '\t' || !it.isISOControl() }
        .lines().map { it.replace(Regex("[ \\t\\u00a0]+"), " ").trim() }
        .joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trim()

    private fun cut(text: String, max: Int): String {
        var end = max
        if (end > 0 && text[end - 1].isHighSurrogate()) end--
        val space = text.lastIndexOf(' ', end).coerceAtLeast(text.lastIndexOf('\n', end))
        return text.substring(0, if (space >= end * 3 / 4) space else end).trimEnd()
    }
}
