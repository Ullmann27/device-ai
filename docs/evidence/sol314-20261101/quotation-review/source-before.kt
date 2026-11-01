package at.heinz.deviceai.learning

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Bounded quotation from an existing immutable source, never an answer proof,
 * owner fact, instruction or tool proposal. Offsets are UTF-16 String indices.
 */
object ContinuumEvidenceWindow {
    data class Quotation(val text: String, val start: Int, val endExclusive: Int,
        val sourceSha256: String, val quotationSha256: String,
        val utf8Start: Int, val utf8EndExclusive: Int)

    private val words = Regex("[\\p{L}\\p{M}\\p{N}]+(?:[-_][\\p{L}\\p{M}\\p{N}]+)*")
    private val stop = setOf("bitte", "eine", "einen", "einer", "einem", "eines", "dieser", "diese", "dieses",
        "welche", "welcher", "welches", "wurde", "wird", "sind", "nicht", "über", "erkläre", "erklären",
        "kurz", "etwas", "kannst", "könntest", "wissen", "weißt", "kennst", "bedeutet", "genau")
    private fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    fun select(question: String, source: String, sourceSha256: String, maxChars: Int = 350): Quotation? {
        require(maxChars in 80..1000)
        if(question.isBlank() || question.length > 512 || source.length !in 20..6000 ||
            sourceSha256 != digest(source))return null
        val terms = words.findAll(question).map { normalized(it.value) }
            .filter { it.length >= 4 && it !in stop }.distinct().take(24).toSet()
        if(terms.isEmpty())return null
        val tokens = words.findAll(source).toList()
        val normalizedTokens = tokens.map { normalized(it.value) }
        val anchors = tokens.indices.filter { normalizedTokens[it] in terms }
        if(anchors.isEmpty())return null
        // Bounded stored excerpts only (6000 chars); no index, file copy or new
        // source store. Score exact source windows by distinct question terms.
        val ranges = anchors.map { i ->
            var start = (tokens[i].range.first - minOf(64, maxChars / 4)).coerceAtLeast(0)
            if(start > 0 && source[start].isLowSurrogate())start--
            var end = minOf(source.length, start + maxChars)
            if(end < source.length && source[end-1].isHighSurrogate())end--
            start to end
        }.distinct()
        // Ranges preserve increasing anchor order. A two-pointer term count
        // scores them in linear time, including dense/repeated source terms.
        val counts = HashMap<String,Int>()
        var left = 0; var right = 0; var bestScore = -1
        var best = ranges.first()
        fun adjust(index: Int, delta: Int) {
            val word = normalizedTokens[index]
            if(word !in terms)return
            val next = (counts[word] ?: 0) + delta
            if(next <= 0)counts.remove(word) else counts[word] = next
        }
        for(range in ranges) {
            val (start, end) = range
            while(left < tokens.size && tokens[left].range.first < start) {
                if(left < right)adjust(left, -1)
                left++
            }
            right = maxOf(right, left)
            while(right < tokens.size && tokens[right].range.last < end) {
                adjust(right, 1); right++
            }
            if(counts.size > bestScore) { bestScore = counts.size; best = range }
        }
        val (start, end) = best
        val text = source.substring(start, end)
        val byteStart = source.substring(0, start).toByteArray(Charsets.UTF_8).size
        return Quotation(text, start, end, sourceSha256, digest(text),
            byteStart, byteStart + text.toByteArray(Charsets.UTF_8).size)
    }

    private fun digest(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
