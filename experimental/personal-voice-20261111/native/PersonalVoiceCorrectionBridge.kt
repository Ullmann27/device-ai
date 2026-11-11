package at.heinz.deviceai.research.voice

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** A port over the existing ephemeral conversation context, not another memory store. */
interface VoiceContextPort {
    val mutex: Any
    fun ownerEvidence(sessionId: String): List<String>?
    fun inspect(sessionId: String, request: String): VoiceInspection
}

enum class VoiceResolution { PASS_THROUGH, SCOPE_UPDATED, CLARIFY, REJECTED }
data class VoiceInspection(val resolution: VoiceResolution, val focus: String? = null)

/** The UI must bind a review to the complete observed turn AND its owner-only context. */
class OwnerVoiceReview(
    val sourceDigest: String,
    val contextDigest: String,
    val correctedTranscript: String? = null,
    val transcriptConfirmed: Boolean = false,
    val intendedInstruction: String? = null,
    val meaningConfirmed: Boolean = false
)

/** Deliberately not a data class: default logging must not reveal a private utterance. */
class VoiceTurnResult(
    val resolution: VoiceResolution,
    val effectiveRequest: String,
    val ownerContextBefore: List<String>?,
    val focus: String?,
    val reasonCode: String,
    val transcriptCorrected: Boolean,
    val meaningCorrected: Boolean,
    val advisoryModelIntent: Int?
) {
    val grantsToolExecution: Boolean get() = false
    val startsTraining: Boolean get() = false
}

/**
 * Research adapter at the existing ASR-text / dialogue boundary. No audio capture,
 * no network, no tool dispatch, no automatic learning and no owner-fact writes.
 * Human-reviewed repairs are reported separately from autonomous understanding.
 */
class PersonalVoiceCorrectionBridge(private val context: VoiceContextPort) {
    fun process(sessionId: String, observedTranscript: String, review: OwnerVoiceReview? = null,
                modelIntent: Int? = null): VoiceTurnResult = synchronized(context.mutex) {
        require(sessionId.isNotBlank() && sessionId.length <= 160)
        require(modelIntent == null || modelIntent in 0..3)
        val before = context.ownerEvidence(sessionId)?.toList()
        fun result(kind: VoiceResolution, effective: String, reason: String, focus: String? = null,
                   transcript: Boolean = false, meaning: Boolean = false) =
            VoiceTurnResult(kind, effective, before, focus, reason, transcript, meaning, modelIntent)

        if (observedTranscript.isBlank() || observedTranscript.length > MAX_TURN_CHARS)
            return@synchronized result(VoiceResolution.REJECTED, "", "INPUT_BOUNDS")
        if (before == null)
            return@synchronized result(VoiceResolution.CLARIFY, observedTranscript, "INCOMPLETE_OWNER_CONTEXT")
        if (review != null) {
            if (review.sourceDigest != VoiceDigests.text(observedTranscript) ||
                review.contextDigest != VoiceDigests.context(sessionId, before))
                return@synchronized result(VoiceResolution.REJECTED, observedTranscript, "STALE_OR_WRONG_REVIEW")
            if (review.correctedTranscript != null && !review.transcriptConfirmed ||
                review.intendedInstruction != null && !review.meaningConfirmed ||
                review.transcriptConfirmed && review.correctedTranscript == null ||
                review.meaningConfirmed && review.intendedInstruction == null)
                return@synchronized result(VoiceResolution.REJECTED, observedTranscript, "UNCONFIRMED_REPAIR")
            if (listOfNotNull(review.correctedTranscript, review.intendedInstruction)
                    .any { it.isBlank() || it.length > MAX_TURN_CHARS })
                return@synchronized result(VoiceResolution.REJECTED, observedTranscript, "REPAIR_BOUNDS")
        }
        val text = review?.intendedInstruction ?: review?.correctedTranscript ?: observedTranscript
        val transcriptRepaired = review?.transcriptConfirmed == true
        val meaningRepaired = review?.meaningConfirmed == true
        // Normalize only discourse control. Never guess spelling, strip a negation,
        // truncate an utterance, or substitute a model-proposed referent.
        val canonical = canonicalCorrection(text)
        if (canonical != null && canonical.length > MAX_SCOPE_CHARS)
            return@synchronized result(VoiceResolution.CLARIFY, text, "LONG_CORRECTION_REQUIRES_REVIEW",
                transcript = transcriptRepaired, meaning = meaningRepaired)
        val inspected = context.inspect(sessionId, canonical ?: text)
        result(inspected.resolution, text,
            when {
                inspected.resolution == VoiceResolution.CLARIFY -> "REFERENT_NOT_UNIQUE"
                meaningRepaired -> "OWNER_CONFIRMED_MEANING_REPAIR"
                transcriptRepaired -> "OWNER_CONFIRMED_TRANSCRIPT_REPAIR"
                canonical != null -> "DISCOURSE_CONTROL_NORMALIZED"
                else -> "UNCHANGED_TEXT"
            }, inspected.focus, transcriptRepaired, meaningRepaired)
    }

    companion object {
        const val MAX_TURN_CHARS = 8192
        const val MAX_SCOPE_CHARS = 512
        private val EXPANDED = Regex(
            "(?iu)^\\s*(?:nein[,!:]?\\s*)?so\\s+(?:habe\\s+ich|hab\\s+ich)\\s+(?:das\\s+)?nicht\\s+gemeint[.!,:]?\\s*ich\\s+(?:meinte|meine)\\s+(.+?)\\s*$")
        private val SIMPLE = Regex("(?iu)^\\s*(?:nein[,!:]?\\s+)?(?:ich\\s+(?:meinte|meine)|gemeint\\s+war)\\s+(.+?)\\s*$")
        internal fun canonicalCorrection(text: String): String? {
            val match = EXPANDED.matchEntire(text) ?: SIMPLE.matchEntire(text) ?: return null
            return "Nein, ich meinte " + match.groupValues[1]
        }
    }
}

object VoiceDigests {
    fun text(value: String): String = hash(value.toByteArray(Charsets.UTF_8))
    fun context(sessionId: String, turns: List<String>): String = fields(listOf(sessionId) + turns)
    fun fields(values: List<String>): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(values.size)
            for (value in values) {
                val encoded = value.toByteArray(Charsets.UTF_8)
                out.writeInt(encoded.size); out.write(encoded)
            }
        }
        return hash(bytes.toByteArray())
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
