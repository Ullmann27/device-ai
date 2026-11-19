package at.heinz.deviceai.gmail

import java.nio.charset.Charset
import java.util.Base64
import java.util.Locale

/** One outgoing plain-text mail. [inReplyTo]/[references]: the Message-ID headers of the mail it answers. */
internal data class GmailOutgoing(
    val to: String,
    val subject: String,
    val body: String,
    val threadId: String? = null,
    val inReplyTo: String? = null,
    val references: String? = null
)

/**
 * RFC 2822 messages for drafts.create and messages.send, and the decoding helpers for what Gmail returns.
 *  - Header values lose CR/LF (no header injection); the subject is an RFC 2047 encoded-word in UTF-8 (B encoding,
 *    folded into words of at most 75 characters) whenever it is not plain ASCII.
 *  - The body is text/plain; charset=UTF-8 in base64, lines of 76 characters.
 *  - The whole message is base64url (RFC 4648 §5), as the Gmail API's `raw` field wants it.
 */
internal object GmailMime {
    private val ADDRESS = Regex("^[A-Za-z0-9.!#\$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")
    private val MESSAGE_ID = Regex("^<[^<>\\s]{3,250}>$")
    const val MAX_BODY_CHARS = 20_000
    const val MAX_SUBJECT_CHARS = 250

    fun validAddress(address: String): Boolean = address.length <= 254 && ADDRESS.matches(address)

    /** The `raw` value of a message. Throws [IllegalArgumentException] for an invalid recipient or an empty body. */
    fun raw(mail: GmailOutgoing): String = encodeBase64Url(message(mail).toByteArray(Charsets.UTF_8))

    /** The RFC 2822 text before base64url. */
    fun message(mail: GmailOutgoing): String {
        val to = mail.to.trim()
        require(validAddress(to)) { "Empfänger ist keine gültige E-Mail-Adresse" }
        require(mail.body.isNotBlank()) { "Der Text ist leer" }
        require(mail.body.length <= MAX_BODY_CHARS) { "Der Text ist zu lang" }
        val subject = headerValue(mail.subject).take(MAX_SUBJECT_CHARS)
        val lines = buildList {
            add("To: $to")
            add("Subject: " + encodeHeader(subject))
            mail.inReplyTo?.let(::headerValue)?.takeIf { MESSAGE_ID.matches(it) }?.let { add("In-Reply-To: $it") }
            references(mail.references, mail.inReplyTo)?.let { add("References: $it") }
            add("MIME-Version: 1.0")
            add("Content-Type: text/plain; charset=UTF-8")
            add("Content-Transfer-Encoding: base64")
        }
        val body = Base64.getMimeEncoder(76, "\r\n".toByteArray()).encodeToString(
            mail.body.replace("\r\n", "\n").replace("\n", "\r\n").toByteArray(Charsets.UTF_8))
        return lines.joinToString("\r\n") + "\r\n\r\n" + body + "\r\n"
    }

    /** „Re: …“ unless the subject already starts with a reply prefix. */
    fun replySubject(subject: String): String {
        val clean = headerValue(subject).trim()
        return if (Regex("(?i)^(re|aw|antw)\\s*:").containsMatchIn(clean)) clean else "Re: " + clean.ifEmpty { "(kein Betreff)" }
    }

    private fun references(references: String?, inReplyTo: String?): String? {
        val ids = (headerValue(references.orEmpty()).split(Regex("\\s+")) + listOfNotNull(inReplyTo?.let(::headerValue)))
            .filter { MESSAGE_ID.matches(it) }.distinct()
        return ids.takeLast(20).takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    /** A header value as one line: control characters (CR, LF, …) become spaces. */
    fun headerValue(value: String): String = value.map { if (it.isISOControl()) ' ' else it }.joinToString("")
        .replace(Regex("\\s+"), " ").trim()

    /** RFC 2047: plain ASCII stays as it is; anything else becomes UTF-8 B-encoded words of at most 75 characters. */
    fun encodeHeader(value: String): String {
        if (value.all { it.code in 0x20..0x7e } && !value.contains("=?")) return value
        val words = ArrayList<String>()
        val chunk = StringBuilder()
        var bytes = 0
        var index = 0
        // 75 - "=?UTF-8?B?".length - "?=".length = 63 base64 characters = 45 bytes per word; never split a code point.
        while (index < value.length) {
            val cp = value.codePointAt(index)
            val size = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + size > 45) {
                words += word(chunk.toString()); chunk.clear(); bytes = 0
            }
            chunk.appendCodePoint(cp); bytes += size
            index += Character.charCount(cp)
        }
        if (chunk.isNotEmpty()) words += word(chunk.toString())
        return words.joinToString("\r\n ")
    }

    private fun word(text: String) = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8)) + "?="

    private val ENCODED_WORD = Regex("=\\?([^?]+)\\?([BbQq])\\?([^?]*)\\?=")

    /** Decodes RFC 2047 encoded-words (B and Q); whitespace between two encoded-words disappears. */
    fun decodeHeader(value: String): String {
        val unfolded = value.replace(Regex("\\r?\\n[ \\t]+"), " ")
        val joined = unfolded.replace(Regex("(\\?=)\\s+(?==\\?)"), "$1")
        return ENCODED_WORD.replace(joined) { match ->
            val charset = runCatching { Charset.forName(match.groupValues[1]) }.getOrNull() ?: return@replace match.value
            val bytes = when (match.groupValues[2].uppercase(Locale.ROOT)) {
                "B" -> runCatching { Base64.getDecoder().decode(match.groupValues[3]) }.getOrNull()
                else -> quoted(match.groupValues[3])
            } ?: return@replace match.value
            String(bytes, charset)
        }
    }

    private fun quoted(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '_' -> out.write(' '.code)
                c == '=' && i + 2 < text.length -> {
                    val hex = text.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex != null) { out.write(hex); i += 2 } else out.write(c.code)
                }
                else -> out.write(c.code)
            }
            i++
        }
        return out.toByteArray()
    }

    fun encodeBase64Url(bytes: ByteArray): String = Base64.getUrlEncoder().encodeToString(bytes)

    /** Gmail's `data` fields: base64url with or without padding; null when it is not base64url. */
    fun decodeBase64Url(data: String?): ByteArray? {
        if (data.isNullOrEmpty()) return ByteArray(0)
        val clean = data.trim().replace("\n", "").replace("\r", "")
        return runCatching { Base64.getUrlDecoder().decode(clean) }.getOrNull()
            ?: runCatching { Base64.getDecoder().decode(clean) }.getOrNull()
    }
}
