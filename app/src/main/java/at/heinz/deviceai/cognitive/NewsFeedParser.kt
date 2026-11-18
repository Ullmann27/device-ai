package at.heinz.deviceai.cognitive

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One feed entry as plain, bounded text. Everything in it is untrusted external data. */
internal data class NewsFeedItem(
    val title: String,
    val teaser: String,
    val link: String,
    val publishedAtMillis: Long?,
    val categories: List<String> = emptyList()
)

/**
 * Tolerant reader for RSS 2.0, RSS 1.0/RDF and Atom. Pure Kotlin and deliberately NOT an XML parser: it scans for
 * item/entry blocks and reads a handful of fields as text, so there are no entities to expand, no DTDs, no external
 * references and nothing that could be executed. A feed that was cut off in the middle (byte limit) still yields
 * its complete items. Every bound is fixed: input size, items, block size, title, teaser, link and category length.
 * Markup inside titles and teasers (CDATA, escaped HTML, script/style) is removed; only text is kept.
 */
internal object NewsFeedParser {
    const val MAX_INPUT_CHARACTERS = 400_000
    const val MAX_ITEMS = 40
    const val MAX_TITLE = 200
    const val MAX_TEASER = 360
    const val MAX_LINK = 600
    const val MAX_CATEGORIES = 6
    private const val MAX_BLOCK = 40_000
    private const val MAX_FIELD = 8_000
    private const val PREFIX = "(?:[A-Za-z][\\w.-]{0,20}:)?"

    private val itemOpen = Regex("<($PREFIX(?:item|entry))(?=[\\s>/])", RegexOption.IGNORE_CASE)
    private val elementPatterns = HashMap<String, Regex>()
    private val linkTag = Regex("<${PREFIX}link(?=[\\s>/])([^>]{0,2000})>", RegexOption.IGNORE_CASE)
    private val categoryTag = Regex("<${PREFIX}category(?=[\\s>/])([^>]{0,500})>", RegexOption.IGNORE_CASE)
    private val cdata = Regex("<!\\[CDATA\\[(.*?)(?:]]>|$)", RegexOption.DOT_MATCHES_ALL)
    private val scriptOrStyle = Regex("<(script|style)\\b[^>]{0,500}>.*?(?:</\\1\\s*>|$)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val markup = Regex("<[/!?]?[A-Za-z][^<>]{0,2000}>|<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val entity = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,10});")
    private val whitespace = Regex("\\s+")
    private val named = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "auml" to "ä", "ouml" to "ö", "uuml" to "ü", "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü", "szlig" to "ß",
        "euro" to "€", "ndash" to "–", "mdash" to "—", "hellip" to "…", "laquo" to "«", "raquo" to "»",
        "bdquo" to "„", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚",
        "shy" to "", "eacute" to "é", "egrave" to "è", "agrave" to "à", "aacute" to "á", "ccedil" to "ç",
        "ntilde" to "ñ", "copy" to "©", "reg" to "®", "deg" to "°", "middot" to "·", "bull" to "•")
    private val zoneNames = mapOf("UT" to "GMT", "UTC" to "GMT", "Z" to "GMT", "EST" to "-0500", "EDT" to "-0400",
        "CST" to "-0600", "CDT" to "-0500", "MST" to "-0700", "MDT" to "-0600", "PST" to "-0800", "PDT" to "-0700",
        "CET" to "+0100", "MEZ" to "+0100", "CEST" to "+0200", "MESZ" to "+0200", "BST" to "+0100")
    private val trailingZone = Regex("\\s([A-Za-z]{1,4})$")

    fun parse(raw: String): List<NewsFeedItem> {
        val text = if (raw.length > MAX_INPUT_CHARACTERS) raw.substring(0, MAX_INPUT_CHARACTERS) else raw
        val items = ArrayList<NewsFeedItem>()
        val seenLinks = HashSet<String>()
        var position = 0
        while (items.size < MAX_ITEMS) {
            val open = itemOpen.find(text, position) ?: break
            val tagEnd = text.indexOf('>', open.range.last)
            if (tagEnd < 0) break
            val name = open.groupValues[1]
            if (text[tagEnd - 1] == '/') { position = tagEnd + 1; continue }
            val close = text.indexOf("</$name", tagEnd, ignoreCase = true)
            if (close < 0) break // cut-off tail: the incomplete last entry is ignored
            position = close + name.length + 2
            val attributes = text.substring(open.range.last + 1, tagEnd)
            val block = text.substring(tagEnd + 1, minOf(close, tagEnd + 1 + MAX_BLOCK))
            val item = item(block, attributes) ?: continue
            if (seenLinks.add(item.link)) items += item
        }
        return items
    }

    private fun item(block: String, attributes: String): NewsFeedItem? {
        val title = plain(element(block, "title").orEmpty(), MAX_TITLE)
        if (title.isEmpty()) return null
        val link = link(block, attributes) ?: return null
        val teaser = plain(element(block, "description") ?: element(block, "summary") ?: element(block, "subtitle")
            ?: "", MAX_TEASER).takeIf { !it.equals(title, ignoreCase = true) }.orEmpty()
        val date = sequenceOf("pubDate", "date", "published", "updated", "issued", "modified")
            .mapNotNull { element(block, it) }.mapNotNull { parseDate(plain(it, 64)) }.firstOrNull()
        return NewsFeedItem(title, teaser, link, date, categories(block))
    }

    /** RSS/RDF: element text. Atom: the href of the alternate link. Fallback: a permalink guid or rdf:about. */
    private fun link(block: String, attributes: String): String? {
        val candidates = ArrayList<String>()
        element(block, "link")?.let { candidates += plain(it, MAX_LINK + 1) }
        linkTag.findAll(block).take(8).forEach { tag ->
            val rel = attribute(tag.groupValues[1], "rel")?.lowercase()
            if (rel == null || rel == "alternate") attribute(tag.groupValues[1], "href")?.let { candidates += plain(it, MAX_LINK + 1) }
        }
        element(block, "guid")?.let { candidates += plain(it, MAX_LINK + 1) }
        attribute(attributes, "about")?.let { candidates += plain(it, MAX_LINK + 1) }
        return candidates.firstOrNull { it.length in 12..MAX_LINK && it.startsWith("https://", ignoreCase = true) &&
            it.none(Char::isWhitespace) }
    }

    private fun categories(block: String): List<String> {
        val found = LinkedHashSet<String>()
        categoryTag.findAll(block).take(12).forEach { tag ->
            attribute(tag.groupValues[1], "term")?.let { found += plain(it, 40) }
        }
        var position = 0
        for (round in 0 until 12) {
            val (inner, end) = elementAt(block, "category", position) ?: break
            found += plain(inner, 40)
            position = end
        }
        element(block, "subject")?.let { found += plain(it, 40) }
        return found.filter { it.isNotEmpty() }.take(MAX_CATEGORIES)
    }

    private fun element(block: String, name: String): String? = elementAt(block, name, 0)?.first

    /** Inner text of the first non-empty <[name]> from [from] (any namespace prefix) and the index after it. */
    private fun elementAt(block: String, name: String, from: Int): Pair<String, Int>? {
        val pattern = synchronized(elementPatterns) {
            elementPatterns.getOrPut(name) { Regex("<($PREFIX$name)(?=[\\s>/])[^>]{0,2000}>", RegexOption.IGNORE_CASE) }
        }
        var position = from
        repeat(6) {
            val open = pattern.find(block, position) ?: return null
            position = open.range.last + 1
            if (open.value.endsWith("/>")) return@repeat
            val close = block.indexOf("</${open.groupValues[1]}", position, ignoreCase = true)
            // Cut off inside the element: the text up to the limit is still usable.
            val inner = block.substring(position, if (close < 0) block.length else close)
            if (inner.isNotBlank()) return inner.take(MAX_FIELD) to (if (close < 0) block.length else close)
        }
        return null
    }

    private fun attribute(attributes: String, name: String): String? =
        Regex("(?:^|[\\s:])$name\\s*=\\s*(?:\"([^\"]{0,2000})\"|'([^']{0,2000})')", RegexOption.IGNORE_CASE)
            .find(attributes)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.takeIf { it.isNotBlank() }

    /**
     * Plain text of a feed field: CDATA unwrapped, entities decoded (twice, feeds escape HTML inside XML), markup,
     * scripts and styles removed, control and invisible characters dropped, whitespace collapsed, bounded.
     */
    internal fun plain(raw: String, max: Int): String {
        var text = cdata.replace(raw.take(MAX_FIELD)) { it.groupValues[1] }
        repeat(2) {
            text = scriptOrStyle.replace(text, " ")
            text = markup.replace(text, " ")
            text = decode(text)
        }
        text = markup.replace(scriptOrStyle.replace(text, " "), " ")
        val clean = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val cp = text.codePointAt(index)
            index += Character.charCount(cp)
            val type = Character.getType(cp)
            when {
                Character.isWhitespace(cp) || type == Character.LINE_SEPARATOR.toInt() ||
                    type == Character.PARAGRAPH_SEPARATOR.toInt() || type == Character.SPACE_SEPARATOR.toInt() -> clean.append(' ')
                Character.isISOControl(cp) || type == Character.FORMAT.toInt() || type == Character.SURROGATE.toInt() ||
                    type == Character.PRIVATE_USE.toInt() || type == Character.UNASSIGNED.toInt() -> Unit
                else -> clean.appendCodePoint(cp)
            }
        }
        return bound(whitespace.replace(clean, " ").trim(), max)
    }

    private fun decode(text: String): String = entity.replace(text) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x", ignoreCase = true) -> codePoint(body.substring(2).toIntOrNull(16))
            body.startsWith("#") -> codePoint(body.substring(1).toIntOrNull())
            else -> named[body] ?: named[body.lowercase()]?.takeIf { body.length > 2 }
        } ?: match.value
    }

    /** A numeric reference as text; NUL and invalid code points vanish instead of staying as „&#0;“. */
    private fun codePoint(value: Int?): String =
        value?.takeIf { Character.isValidCodePoint(it) && it != 0 }?.let { String(Character.toChars(it)) }.orEmpty()

    private fun bound(text: String, max: Int): String {
        if (text.length <= max) return text
        var end = max - 1
        if (end > 0 && Character.isHighSurrogate(text[end - 1])) end--
        val space = text.lastIndexOf(' ', end)
        return text.substring(0, if (space >= end * 3 / 4) space else end).trimEnd(' ', ',', ';', ':', '–', '-') + "…"
    }

    /** RFC 822/1123 (RSS pubDate) and ISO 8601 (dc:date, Atom) as epoch milliseconds; null when unreadable. */
    internal fun parseDate(value: String): Long? {
        val text = value.trim()
        if (text.length !in 8..64) return null
        runCatching { return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }
        val rfc = trailingZone.find(text)?.let { zone ->
            zoneNames[zone.groupValues[1].uppercase()]?.let { text.substring(0, zone.range.first) + " " + it }
        } ?: text
        runCatching { return ZonedDateTime.parse(rfc, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching {
            return LocalDateTime.parse(text.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        // A date without a time: noon UTC keeps the calendar day stable for European time zones.
        runCatching { return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() }
        return null
    }
}
