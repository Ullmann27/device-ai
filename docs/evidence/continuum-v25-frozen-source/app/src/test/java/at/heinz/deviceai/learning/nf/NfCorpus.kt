package at.heinz.deviceai.learning.nf

import at.heinz.deviceai.learning.lm.LmBpeTokenizer
import at.heinz.deviceai.learning.lm.LmCorpus
import at.heinz.deviceai.learning.lm.LmExperiment
import java.io.File
import java.security.MessageDigest

/**
 * Research cycle NFC1 of the Neural Frontier Lab: its own TRAIN / VALIDATION / frozen TEST, fixed before any NF run.
 * - DOC: owner Markdown at [COMMIT] (without docs/evidence/ and THIRD_PARTY_NOTICES.md), LM1 privacy filter.
 * - CODE: owner Kotlin sources under app/src/main/java at [COMMIT]; names, links, mail addresses, hashes and long
 *   numbers replaced like in LM1; segments of at most 255 bytes cut at line ends.
 * - Every path that LM1 assigns to its TEST split is excluded, so the not yet evaluated LM1 TEST stays unseen by all
 *   NF models. NF splits use their own salt: first byte of SHA-256("nfc1\0" + path) mod 10 → 0 TEST, 1 VALIDATION.
 */
object NfCorpus {
    const val COMMIT = "68b0a33512d18570ddf450e8f9205a60dbee1c66"

    enum class Domain { DOC, CODE }
    data class Doc(val path: String, val domain: Domain, val split: LmCorpus.Split, val segments: List<String>)

    private val URL = Regex("\\b(?:https?|ftp)://\\S+|\\bwww\\.\\S+")
    private val EMAIL = Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")
    private val HEX = Regex("\\b[0-9a-fA-F]{10,}\\b")
    private val LONG_NUMBER = Regex("\\d{7,}")
    private val NAMES = listOf(Regex("(?i)ullmann\\w*") to "konto", Regex("(?i)heinz") to "besitzer")

    fun splitOf(path: String): LmCorpus.Split {
        val b = MessageDigest.getInstance("SHA-256").digest(("nfc1\u0000" + path).toByteArray(Charsets.UTF_8))[0].toInt() and 255
        return when (b % 10) { 0 -> LmCorpus.Split.TEST; 1 -> LmCorpus.Split.VALIDATION; else -> LmCorpus.Split.TRAIN }
    }

    fun cleanCode(source: String): String {
        var t = source.replace("\r\n", "\n").replace("\t", "    ")
        t = URL.replace(t, "<link>")
        t = EMAIL.replace(t, "<e-mail>")
        t = HEX.replace(t, "<hash>")
        t = LONG_NUMBER.replace(t, "<zahl>")
        for ((pattern, replacement) in NAMES) t = pattern.replace(t, replacement)
        return t.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    /** Consecutive lines up to [maxBytes] UTF-8 bytes; an overlong line is cut at a character boundary. */
    fun codeSegments(text: String, maxBytes: Int = LmCorpus.MAX_SEGMENT_BYTES): List<String> {
        fun utf8(s: String) = s.toByteArray(Charsets.UTF_8).size
        val out = ArrayList<String>()
        val cur = StringBuilder(); var bytes = 0
        fun flush() { if (cur.isNotBlank()) out += cur.toString().trimEnd(); cur.setLength(0); bytes = 0 }
        for (line in text.lines()) {
            var rest = line
            while (utf8(rest) > maxBytes) {
                flush()
                var cut = 0; var b = 0
                while (cut < rest.length) {
                    val cp = rest.codePointAt(cut)
                    val n = utf8(String(Character.toChars(cp)))
                    if (b + n > maxBytes) break
                    b += n; cut += Character.charCount(cp)
                }
                out += rest.substring(0, cut); rest = rest.substring(cut)
            }
            if (bytes + utf8(rest) + (if (cur.isEmpty()) 0 else 1) > maxBytes) flush()
            if (cur.isNotEmpty()) { cur.append('\n'); bytes += 1 }
            cur.append(rest); bytes += utf8(rest)
            if (rest.isBlank() && bytes > maxBytes / 2) flush()                // prefer cuts at blank lines
        }
        flush()
        return out.filter { s -> s.count { !it.isWhitespace() } >= 2 && utf8(s) <= maxBytes }
    }

    fun load(root: File): List<Doc> {
        val files = LmExperiment.git(root, "ls-tree", "-r", "--name-only", COMMIT).lines().filter { it.isNotBlank() }
        val out = ArrayList<Doc>()
        for (path in files) {
            if (LmCorpus.splitOf(path) == LmCorpus.Split.TEST) continue
            val domain = when {
                path.endsWith(".md") && !path.startsWith("docs/evidence/") && path != "THIRD_PARTY_NOTICES.md" -> Domain.DOC
                path.startsWith("app/src/main/java/") && path.endsWith(".kt") -> Domain.CODE
                else -> continue
            }
            val text = LmExperiment.git(root, "show", "$COMMIT:$path")
            val segments = if (domain == Domain.DOC) LmCorpus.segments(LmCorpus.clean(text)) else codeSegments(cleanCode(text))
            if (segments.isNotEmpty()) out += Doc(path, domain, splitOf(path), segments)
        }
        return out.sortedBy { it.path }
    }

    fun sha256(docs: List<Doc>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (d in docs.sortedBy { it.path }) {
            digest.update(d.path.toByteArray(Charsets.UTF_8)); digest.update(0)
            for (s in d.segments) { digest.update(s.toByteArray(Charsets.UTF_8)); digest.update(0) }
        }
        return LmBpeTokenizer.hex(digest.digest())
    }

    fun segments(docs: List<Doc>, domain: Domain, split: LmCorpus.Split): List<String> =
        docs.filter { it.domain == domain && it.split == split }.flatMap { it.segments }

    fun bytes(segments: List<String>) = segments.sumOf { it.toByteArray(Charsets.UTF_8).size }
}
