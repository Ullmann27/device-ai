package at.heinz.deviceai.learning.lm

import java.security.MessageDigest

/**
 * Turns owner-authored Markdown documents into training segments.
 * - Privacy filter: links, e-mail addresses, hashes, long numbers and the owner's names are replaced by neutral
 *   placeholders; fenced code and HTML comments are dropped (code is not natural language).
 * - Segments are paragraphs of at most [MAX_SEGMENT_BYTES] UTF-8 bytes, so that every model (also CDX1 with its
 *   512-byte limit) is scored on exactly the same units, each starting without context.
 * - Documents, not segments, are assigned to TRAIN / VALIDATION / TEST by the SHA-256 of their path, so evaluation
 *   text comes from documents that no model has seen.
 */
object LmCorpus {
    const val MAX_SEGMENT_BYTES = 255

    enum class Split { TRAIN, VALIDATION, TEST }

    data class Document(val path: String, val split: Split, val segments: List<String>)

    private val FENCE = Regex("(?ms)^\\s*(```|~~~).*?^\\s*(```|~~~)[^\\n]*$")
    private val COMMENT = Regex("(?s)<!--.*?-->")
    private val MD_LINK = Regex("!?\\[([^\\]]*)\\]\\([^)]*\\)")
    private val URL = Regex("\\b(?:https?|ftp)://\\S+|\\bwww\\.\\S+")
    private val EMAIL = Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")
    private val HEX = Regex("\\b[0-9a-fA-F]{10,}\\b")
    private val LONG_NUMBER = Regex("\\d{7,}")
    private val TABLE_RULE = Regex("(?m)^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$\\n?")
    private val NAME_RULES = listOf(
        Regex("Heinz\\s+Ullmann") to "der Besitzer",
        Regex("(?i)ullmann\\w*") to "<konto>",
        Regex("Heinz'") to "des Besitzers",
        Regex("Heinz") to "der Besitzer",
        Regex("(?i)heinz") to "besitzer"
    )

    fun clean(markdown: String): String {
        var t = markdown.replace("\r\n", "\n")
        t = FENCE.replace(t, "\n")
        t = COMMENT.replace(t, "")
        t = MD_LINK.replace(t) { it.groupValues[1] }
        t = URL.replace(t, "<link>")
        t = EMAIL.replace(t, "<e-mail>")
        t = HEX.replace(t, "<hash>")
        t = LONG_NUMBER.replace(t, "<zahl>")
        t = TABLE_RULE.replace(t, "")
        for ((pattern, replacement) in NAME_RULES) t = pattern.replace(t, replacement)
        return t.replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    /** Paragraphs split at blank lines; longer paragraphs are cut at the last whitespace within the byte limit. */
    fun segments(text: String, maxBytes: Int = MAX_SEGMENT_BYTES): List<String> {
        val out = ArrayList<String>()
        for (raw in text.split(Regex("\\n\\s*\\n"))) {
            var rest = raw.trim()
            while (rest.isNotEmpty()) {
                if (rest.toByteArray(Charsets.UTF_8).size <= maxBytes) { out += rest; break }
                var cut = 0; var bytes = 0; var lastSpace = -1
                while (cut < rest.length) {
                    val cp = rest.codePointAt(cut)
                    val n = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
                    if (bytes + n > maxBytes) break
                    if (Character.isWhitespace(cp)) lastSpace = cut
                    bytes += n; cut += Character.charCount(cp)
                }
                val end = if (lastSpace > 0) lastSpace else cut
                out += rest.substring(0, end).trim()
                rest = rest.substring(end).trim()
            }
        }
        return out.filter { s -> s.count { !it.isWhitespace() } >= 2 }
    }

    /** Fixed split rule: first byte of SHA-256(path) mod 10 → 0 TEST, 1 VALIDATION, otherwise TRAIN. */
    fun splitOf(path: String): Split {
        val b = MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8))[0].toInt() and 255
        return when (b % 10) { 0 -> Split.TEST; 1 -> Split.VALIDATION; else -> Split.TRAIN }
    }

    fun document(path: String, markdown: String) = Document(path, splitOf(path), segments(clean(markdown)))

    /** SHA-256 over all segments of a split in path order (the freeze value of TEST). */
    fun sha256(documents: List<Document>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (d in documents.sortedBy { it.path }) {
            digest.update(d.path.toByteArray(Charsets.UTF_8)); digest.update(0)
            for (s in d.segments) { digest.update(s.toByteArray(Charsets.UTF_8)); digest.update(0) }
        }
        return LmBpeTokenizer.hex(digest.digest())
    }
}
