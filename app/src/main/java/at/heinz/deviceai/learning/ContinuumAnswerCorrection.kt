package at.heinz.deviceai.learning

import java.security.MessageDigest
import java.util.Locale

/** Owner-corrected text for an exact normalized question, never an automatic truth label. */
object ContinuumAnswerCorrection {
    fun queryKey(query:String):String {
        val normalized=query.trim().replace(Regex("\\s+")," ").lowercase(Locale.ROOT)
        require(normalized.length in 3..512)
        val hash=MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "query-sha256:"+hash
    }

    fun find(query:String,items:List<LearningExperience>):LearningExperience? {
        // A one-character greeting or symbol can still reach the dialogue path.
        val tag=runCatching { queryKey(query) }.getOrNull() ?: return null
        return items.asSequence().filter {
            it.type==LearningExperienceType.ANSWER_CORRECTION &&
            it.provenance==LearningProvenance.USER_CONFIRMED &&
            it.trust==LearningTrustClass.OWNER_CONFIRMED &&
            it.status==LearningStatus.CONFIRMED && it.annotation==tag
        }.maxWithOrNull(compareBy<LearningExperience>{it.updatedAt}.thenBy{it.id})
    }
}
