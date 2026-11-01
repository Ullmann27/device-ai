package at.heinz.deviceai.cognitive

import java.security.MessageDigest
import java.net.URI

/**
 * A typed, bounded observation shared by live-web and offline-read paths.
 * Never grants owner-fact status, tool execution, model authority or memory writes.
 * Expiration and exact source revision are checked before model inclusion.
 */
internal object JarvisUnifiedEvidenceContract {
    enum class Origin { OWNER_LOCAL_VIEW, REVIEWED_ARCHIVE, PUBLIC_WEB }
    enum class Privacy { PUBLIC, DEVICE_LOCAL_ONLY }
    data class Evidence(val origin: Origin, val privacy: Privacy,
        val sourceId: String, val revision: Int, val observedAtMillis: Long,
        val expiresAtMillis: Long, val content: String, val sha256: String) {
        init {
            require(sourceId.length in 1..2048 && revision >= 1)
            require(observedAtMillis >= 0 && expiresAtMillis > observedAtMillis)
            require(content.length in 1..4096 && content.none(Char::isISOControl))
            require(sha256.matches(Regex("[0-9a-f]{64}")) && sha256 == hash(content))
            if (origin == Origin.PUBLIC_WEB) {
                require(privacy == Privacy.PUBLIC && runCatching {
                    val uri = URI(sourceId)
                    uri.scheme == "https" && uri.host != null && uri.userInfo == null &&
                        uri.port in setOf(-1,443)
                }.getOrDefault(false))
            }
            if (privacy == Privacy.DEVICE_LOCAL_ONLY)
                require(origin != Origin.PUBLIC_WEB)
        }
        val grantsOwnerAuthority: Boolean get() = false
    }

    fun useForAnswer(candidates: Collection<Evidence>, nowMillis: Long,
                     offline: Boolean, maxEvidence: Int = 4): List<Evidence> {
        require(candidates.size <= 256 && nowMillis >= 0 && maxEvidence in 0..8)
        return candidates.asSequence().filter {
            it.observedAtMillis <= nowMillis && it.expiresAtMillis > nowMillis &&
                (!offline || it.origin != Origin.PUBLIC_WEB) &&
                it.sha256 == hash(it.content)
        }.sortedWith(compareByDescending<Evidence> { it.observedAtMillis }
            .thenBy { it.sourceId }.thenByDescending { it.revision })
            .distinctBy { it.origin to it.sourceId }
            .take(maxEvidence).toList()
    }

    fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
