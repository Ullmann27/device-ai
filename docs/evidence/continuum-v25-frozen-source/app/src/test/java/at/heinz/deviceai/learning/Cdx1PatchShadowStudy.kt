package at.heinz.deviceai.learning

import java.security.MessageDigest
import kotlin.math.ln

/**
 * Offline research-only pairing of a manually reviewed patch fragment and a counterfactual fragment.
 * CDX1 estimates byte-sequence likelihood, never code correctness or repair safety.
 * No training, disk reads/writes, model promotion, file patching, tool execution or network access.
 */
class Cdx1PatchShadowStudy(private val decoder: TinyContextDecoder) {
    data class ReviewedPair(
        val original: String,
        val counterfactual: String,
        val reviewId: String
    )
    data class Observation(
        val mode: String,
        val originalSha256: String,
        val counterfactualSha256: String,
        val originalMeanSurprisalBits: Double,
        val counterfactualMeanSurprisalBits: Double,
        val differenceCounterfactualMinusOriginalBits: Double,
        val modelUpdatesUnchanged: Boolean,
        val canApprovePatch: Boolean,
        val canOverrideVerifier: Boolean
    )

    fun observe(input: ReviewedPair): Observation {
        require(input.reviewId.matches(Regex("[A-Za-z0-9._-]{1,96}"))) { "SHADOW_REVIEW_ID_REQUIRED" }
        require(input.original != input.counterfactual) { "SHADOW_DISTINCT_COUNTERFACTUAL_REQUIRED" }
        for (text in listOf(input.original, input.counterfactual)) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            require(bytes.size in 1..512 && text.isNotBlank() && '\u0000' !in text) {
                "SHADOW_TEXT_LIMIT"
            }
        }

        val before = decoder.updates
        val a = decoder.evaluate(DecoderCorpus(listOf(input.original), DecoderSplit.AUDIT)) / ln(2.0)
        val b = decoder.evaluate(DecoderCorpus(listOf(input.counterfactual), DecoderSplit.AUDIT)) / ln(2.0)
        require(a.isFinite() && b.isFinite() && a >= 0.0 && b >= 0.0) { "SHADOW_NONFINITE_SCORE" }
        check(decoder.updates == before) { "SHADOW_UNEXPECTED_MODEL_UPDATE" }
        return Observation(
            mode = "SHADOW_ONLY_NO_SAFETY_VERDICT",
            originalSha256 = digest(input.original),
            counterfactualSha256 = digest(input.counterfactual),
            originalMeanSurprisalBits = a,
            counterfactualMeanSurprisalBits = b,
            differenceCounterfactualMinusOriginalBits = b - a,
            modelUpdatesUnchanged = true,
            canApprovePatch = false,
            canOverrideVerifier = false
        )
    }

    private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
