package at.heinz.deviceai.learning.span

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Collections

/** Immutable, read-only copy of a bounded retrieval result; never a new memory database. */
class LocationEvidenceSnapshot private constructor(
    internal val sources: List<EvidenceBoundNaturalLocationProbe.Evidence>, val sha256: String
) {
    companion object {
        fun freeze(sources: List<EvidenceBoundNaturalLocationProbe.Evidence>): LocationEvidenceSnapshot {
            require(sources.size in 1..4 && sources.sumOf { it.text.length.toLong() } <= 4096)
            require(sources.map { it.sourceId }.distinct().size == sources.size)
            require(sources.all {
                it.sourceId.matches(Regex("[a-zA-Z0-9._:-]{1,96}")) && it.revision > 0 &&
                    it.text.isNotBlank() && it.text.none(Char::isISOControl) &&
                    Charsets.UTF_8.newEncoder().canEncode(it.text) &&
                    it.sha256 == EvidenceBoundNaturalLocationProbe.digest(it.text)
            })
            val copy = Collections.unmodifiableList(sources.map { it.copy() })
            // Length-delimited, ordered identity: no delimiter or source-order collisions.
            val bytes = ByteArrayOutputStream().also { buffer ->
                DataOutputStream(buffer).use { out ->
                    out.writeInt(1); out.writeInt(copy.size)
                    for (source in copy) {
                        out.writeUTF(source.sourceId); out.writeInt(source.revision); out.writeUTF(source.sha256)
                    }
                }
            }.toByteArray()
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            return LocationEvidenceSnapshot(copy, hash)
        }
    }
}

/** Frozen learned ranker + independent verifier, shadow only. No production promotion or tool execution. */
class VerifiedLocationSpanResearch(checkpoint: ByteArray) {
    private val model = ContinuumResidualSpanCore.restore(checkpoint.copyOf()).also {
        require(it.features.naturalEntityBindings)
    }
    val checkpointSha256: String = java.security.MessageDigest.getInstance("SHA-256").digest(checkpoint)
        .joinToString("") { "%02x".format(it) }

    data class Result(val decision: EvidenceBoundNaturalLocationProbe.Decision, val snapshotSha256: String,
        val modelSha256: String, val confidence: Double?)

    fun answer(question: String, snapshot: LocationEvidenceSnapshot): Result {
        fun abstain(reason: String) = Result(EvidenceBoundNaturalLocationProbe.Decision.Abstained(reason),
            snapshot.sha256, checkpointSha256, null)
        if (question.toByteArray(Charsets.UTF_8).size > 256 || question.isBlank() || question.any(Char::isISOControl))
            return abstain("INVALID_QUESTION")
        val joined = snapshot.sources.joinToString("; ") { it.text }
        if (joined.toByteArray(Charsets.UTF_8).size > 1024) return abstain("RANKER_RESOURCE_LIMIT")
        val prediction = try { model.predict(joined, question) } catch (_: IllegalArgumentException) {
            return abstain("RANKER_RESOURCE_LIMIT")
        }
        if (prediction.answer == null) return abstain("LEARNED_NO_ANSWER")
        var offset = 0
        for (source in snapshot.sources) {
            if (prediction.start >= offset && prediction.end <= offset + source.text.length) {
                val candidate = EvidenceBoundNaturalLocationProbe.Decision.Found(prediction.answer, source.sourceId,
                    source.revision, source.sha256, prediction.start - offset, prediction.end - offset)
                if (!EvidenceBoundNaturalLocationVerifier.verify(question, snapshot.sources, candidate))
                    return abstain("INDEPENDENT_VERIFICATION_REJECTED")
                return Result(candidate, snapshot.sha256, checkpointSha256, prediction.confidence)
            }
            offset += source.text.length + 2
        }
        return abstain("CROSS_SOURCE_SPAN")
    }
}
