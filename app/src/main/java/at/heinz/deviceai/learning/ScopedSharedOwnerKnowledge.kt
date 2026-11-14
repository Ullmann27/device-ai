package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.DeliberationCancellation
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class SharedKnowledgeRecipient(val agentId: String, val profileVersion: Long, val displayName: String) {
    init {
        require(agentId.matches(Regex("[a-z][a-z0-9-]{2,63}")) && profileVersion > 0)
        require(displayName.isNotBlank() && displayName.length <= 80 && displayName.none(Char::isISOControl))
        require(Charsets.UTF_8.newEncoder().canEncode(displayName))
    }
}

/** One task and named recipients. */
class SharedOwnerKnowledgeScope internal constructor(
    private val authority: Any,
    val requestId: String,
    request: String,
    val teamRevision: Long,
    private val admissionRevision: Long,
    recipients: List<SharedKnowledgeRecipient>
) {
    val recipients: List<SharedKnowledgeRecipient> = Collections.unmodifiableList(ArrayList(recipients))
    val requestSha256: String = hash(request)
    init {
        require(requestId.matches(Regex("team-[a-f0-9-]{36}")) && teamRevision > 0 && admissionRevision >= 0)
        require(request.isNotBlank() && request.length <= 1_800 && Charsets.UTF_8.newEncoder().canEncode(request))
        require(recipients.size == 2 && recipients.map { it.agentId }.distinct().size == 2)
    }
    fun matchesRequest(id: String, request: String): Boolean = requestId == id && requestSha256 == hash(request)
    internal fun matches(other: SharedOwnerKnowledgeScope): Boolean = authority === other.authority &&
        requestId == other.requestId && requestSha256 == other.requestSha256 && teamRevision == other.teamRevision &&
        admissionRevision == other.admissionRevision && recipients == other.recipients

    private companion object {
        fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

/** Owner preview; no execution authority. */
class SharedOwnerKnowledgeReview internal constructor(
    private val core: ContinuumLearningCore,
    private val directory: File,
    val scope: SharedOwnerKnowledgeScope,
    facts: List<ProfileFact>
) {
    val facts: List<ProfileFact> = Collections.unmodifiableList(ArrayList(facts))
    private var approved = false
    private val revokedAll = AtomicBoolean()
    private val revokedContents = ConcurrentHashMap.newKeySet<String>()
    private val issued = AtomicReference<SharedOwnerKnowledgeGrant?>()

    internal fun invalidate(content: String?) {
        if (content == null) revokedAll.set(true) else revokedContents.add(content.trim())
        issued.get()?.takeIf { it.uses(content) }?.revoke()
    }

    internal fun selectionCurrent(selected: List<ProfileFact>): Boolean =
        !revokedAll.get() && selected.none { it.text in revokedContents }

    fun approve(selectedIds: Set<String>, ownerApproved: Boolean): SharedOwnerKnowledgeGrant =
        synchronized(ContinuumLearningCore.CORE_LOCK) {
            check(ownerApproved) { "BOT_SHARED_APPROVAL_REQUIRED" }
            check(!approved) { "BOT_SHARED_APPROVAL_CONSUMED" }
            val ids = selectedIds.toSet()
            require(ids.size in 1..5) { "BOT_SHARED_CONTEXT_BUDGET" }
            val selected = facts.filter { it.id in ids }
            require(selected.size == ids.size) { "BOT_SHARED_SELECTION_MISMATCH" }
            require(selected.all { it.text.length <= 1_000 } &&
                selected.sumOf { it.text.toByteArray(Charsets.UTF_8).size } <= 2_000) { "BOT_SHARED_CONTEXT_BUDGET" }
            check(selectionCurrent(selected)) { "BOT_SHARED_FACT_CHANGED" }
            val current = ContinuumMemoryLayers(core).profile().associateBy { it.id }
            check(selected.all { current[it.id] == it }) { "BOT_SHARED_FACT_CHANGED" }
            SharedOwnerKnowledgeGrant(core, directory, scope, selected, this).also {
                approved = true
                issued.set(it)
                if (!selectionCurrent(selected)) {
                    it.revoke()
                    throw CancellationException("BOT_SHARED_KNOWLEDGE_REVOKED")
                }
            }
        }
}

/** Sharing expires with this task. */
class SharedOwnerKnowledgeGrant internal constructor(
    private val core: ContinuumLearningCore,
    directory: File,
    val scope: SharedOwnerKnowledgeScope,
    facts: List<ProfileFact>,
    private val review: SharedOwnerKnowledgeReview
) {
    private val directory = directory.canonicalFile
    val facts: List<ProfileFact> = Collections.unmodifiableList(ArrayList(facts))
    private val revoked = AtomicBoolean()
    private var claimed = false
    private val cancellation = AtomicReference<DeliberationCancellation?>()

    init { synchronized(ContinuumLearningCore.CORE_LOCK) { active[this] = Unit } }

    internal fun requireScope(issuer: ContinuumLearningCore?, expected: SharedOwnerKnowledgeScope) = withCurrent {
        check(issuer === core && scope.matches(expected)) { "BOT_SHARED_SCOPE_CHANGED" }
    }

    internal fun claim(token: DeliberationCancellation) = withCurrent {
        check(!claimed) { "BOT_SHARED_APPROVAL_CONSUMED" }
        token.throwIfCancelled()
        cancellation.set(token)
        if (revoked.get()) token.cancel()
        token.throwIfCancelled()
        claimed = true
    }

    fun revoke() {
        revoked.set(true)
        cancellation.get()?.cancel()
    }

    internal fun uses(content: String?): Boolean = content == null || facts.any { it.text == content.trim() }

    internal fun forRecipient(agentId: String, profileVersion: Long): List<ProfileFact> = withCurrent {
        check(scope.recipients.any { it.agentId == agentId && it.profileVersion == profileVersion }) {
            "BOT_SHARED_RECIPIENT_MISMATCH"
        }
        facts
    }

    internal fun <T> withCurrent(block: () -> T): T = synchronized(ContinuumLearningCore.CORE_LOCK) {
        if (!review.selectionCurrent(facts)) revoke()
        if (revoked.get()) throw CancellationException("BOT_SHARED_KNOWLEDGE_REVOKED")
        val current = ContinuumMemoryLayers(core).profile().associateBy { it.id }
        if (facts.any { current[it.id] != it }) {
            revoke()
            throw CancellationException("BOT_SHARED_FACT_CHANGED")
        }
        cancellation.get()?.throwIfCancelled()
        block()
    }

    internal companion object {
        private val active = WeakHashMap<SharedOwnerKnowledgeGrant, Unit>()
        fun revokeForOwnerMutation(directory: File, content: String?) = synchronized(ContinuumLearningCore.CORE_LOCK) {
            val target = directory.canonicalFile
            active.keys.filter { it.directory == target && it.uses(content) }.forEach { it.revoke() }
        }
    }
}
