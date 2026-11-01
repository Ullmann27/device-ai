package at.heinz.deviceai.cognitive

import java.security.MessageDigest
import java.util.Locale

/**
 * Research-only, read-only evidence binding. Does NOT validate the truth of an
 * archive entry or prove an arbitrary model paraphrase. The existing trusted
 * archive reader must have checked provenance before setting sourceValidated.
 *
 * A source SHA attests to excerpt bytes; it is not proof of factual correctness.
 * This binder deliberately rejects free paraphrases and invented citation IDs.
 * It is NOT wired into the production CDX1 generator or acceptance benchmark.
 */
internal object Cdx1EvidenceSlotBinder {
    data class ArchivePassage(
        val canonicalId: String,
        val excerpt: String,
        val excerptSha256: String,
        val sourceValidated: Boolean
    )

    data class Bundle internal constructor(
        val promptEvidence: String,
        internal val slots: Map<String, ArchivePassage>
    )

    data class ExactQuoteBinding(
        val answerText: String,
        val canonicalId: String,
        val excerptSha256: String,
        val citation: String
    )

    fun create(passages: List<ArchivePassage>): Bundle {
        val eligible = passages.asSequence()
            .filter {
                it.sourceValidated &&
                    it.canonicalId.matches(Regex("[a-zA-Z0-9._:-]{1,160}")) &&
                    it.excerpt.length in 4..1200 &&
                    it.excerpt.none { c -> c == '\u0000' } &&
                    sha256(it.excerpt) == it.excerptSha256.lowercase(Locale.ROOT)
            }
            .distinctBy { it.canonicalId }
            .take(3)
            .toList()
        val slots = linkedMapOf<String, ArchivePassage>()
        val block = buildString {
            append("UNGEPRÜFTE FAKTENBEHAUPTUNGEN AUS INTEGRITÄTSGEPRÜFTEN QUELLTEXTEN:\n")
            for ((index, item) in eligible.withIndex()) {
                val slot = "Q" + (index + 1)
                slots[slot] = item
                append("[").append(slot).append("] ")
                    .append(item.excerpt.replace("\r", " ").replace("\n", " "))
                    .append("\n")
            }
        }
        return Bundle(block, slots.toMap())
    }

    /**
     * Only a verbatim excerpt span, immediately followed by a known slot,
     * can receive the corresponding canonical ID. Any unsupported claim is
     * rejected, never "repaired" with an unrelated reference.
     */
    fun bindExactQuote(response: String, bundle: Bundle): ExactQuoteBinding? {
        if (response.length !in 8..520) return null
        val match = Regex("""(?s)^(.{4,512}?)\s*\[(Q[1-3])\]\s*$""")
            .matchEntire(response.trim()) ?: return null
        val claim = match.groupValues[1].trim()
        if (claim.length < 4 || claim.any { it == '\n' || it == '\r' || it == '\u0000' }) return null
        val source = bundle.slots[match.groupValues[2]] ?: return null
        val wholeSpan = Regex(
            """(?iu)(?<![\p{L}\p{N}])""" + Regex.escape(claim) + """(?![\p{L}\p{N}])"""
        )
        if (!wholeSpan.containsMatchIn(source.excerpt)) return null
        return ExactQuoteBinding(
            answerText = claim,
            canonicalId = source.canonicalId,
            excerptSha256 = source.excerptSha256,
            citation = "[Quelle: " + source.canonicalId + "]"
        )
    }

    internal fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
