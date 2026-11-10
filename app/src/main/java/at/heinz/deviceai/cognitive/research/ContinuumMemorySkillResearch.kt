package at.heinz.deviceai.cognitive.research

import at.heinz.deviceai.cognitive.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import kotlin.math.abs

data class MemorySkillScope(
    val ownerId: String,
    val memoryRevision: String,
    val modelRevision: String,
    val policyRevision: String,
    val sourceRevision: String
) {
    init {
        require(listOf(ownerId, memoryRevision, modelRevision, policyRevision, sourceRevision)
            .all { it.isNotBlank() && it.length <= 128 })
    }
}

/** Trusted receipt metadata; not authentication. */
data class MemorySkillEvidence(
    val scope: MemorySkillScope,
    val experienceDigest: String,
    val receiptId: String,
    val sourceReferences: List<String>,
    val provenance: ProvenanceType,
    val privacy: PrivacyClass,
    val validation: ValidationState,
    val consentForResearch: Boolean
) {
    init {
        require(experienceDigest.matches(Regex("[0-9a-f]{64}")))
        require(receiptId.isNotBlank() && receiptId.length <= 256)
        require(sourceReferences.size in 1..32)
        require(sourceReferences.all { it.isNotBlank() && it.length <= 256 })
    }
}

data class MemorySkillResearchPolicy(
    val maxExperiences: Int = 256,
    val maxRecall: Int = 8,
    val maxCandidates: Int = 16,
    val surpriseThreshold: Double = 0.35,
    val surpriseEnabled: Boolean = true,
    val allowedPrivacy: Set<PrivacyClass> = setOf(PrivacyClass.PUBLIC)
) {
    init {
        require(maxExperiences in 3..4096 && maxRecall in 1..64 && maxCandidates in 1..64)
        require(surpriseThreshold.isFinite() && surpriseThreshold in 0.0..1.0)
    }
}

data class PrioritizedEpisode(val experienceId: String, val residual: Double, val negative: Boolean)
data class ScopedSkillCandidate(
    val candidate: CrystallizedSkillCandidate,
    val scope: MemorySkillScope,
    val evidence: List<MemorySkillEvidence>,
    val negativeExperienceIds: List<String>
)
enum class MemorySkillReviewStatus { DISABLED, LIMIT_REJECTED, REVIEWED, VERIFIER_UNAVAILABLE }
data class MemorySkillReviewReport(
    val status: MemorySkillReviewStatus,
    val reviewedCount: Int = 0,
    val rejectedCount: Int = 0,
    val prioritized: List<PrioritizedEpisode> = emptyList(),
    val candidates: List<ScopedSkillCandidate> = emptyList()
)

/** Read-only, opt-in candidate review. */
class ContinuumMemorySkillResearch(
    policy: MemorySkillResearchPolicy = MemorySkillResearchPolicy(),
    private val independentVerifier: IndependentExperienceVerifier = IndependentExperienceVerifier { false }
) {
    private val policy = policy.copy(allowedPrivacy = Collections.unmodifiableSet(HashSet(policy.allowedPrivacy)))

    fun review(
        experiences: Collection<Experience>,
        scope: MemorySkillScope,
        evidence: Map<String, MemorySkillEvidence>,
        enabled: Boolean = false
    ): MemorySkillReviewReport {
        if (!enabled) return MemorySkillReviewReport(MemorySkillReviewStatus.DISABLED)
        if (experiences.size > policy.maxExperiences || evidence.size > policy.maxExperiences) {
            return MemorySkillReviewReport(MemorySkillReviewStatus.LIMIT_REJECTED)
        }
        val unique = linkedMapOf<String, Experience>()
        var characters = 0L
        for (experience in experiences) {
            if (listOf(experience.memoryIds.size, experience.modelIds.size, experience.sourceIds.size,
                    experience.toolIds.size, experience.failures.size, experience.evaluatorScores.size).any { it > 512 }) {
                return MemorySkillReviewReport(MemorySkillReviewStatus.LIMIT_REJECTED)
            }
            characters += textSize(experience)
            if (characters > 1_000_000L) return MemorySkillReviewReport(MemorySkillReviewStatus.LIMIT_REJECTED)
            val snapshot = freeze(experience)
            val previous = unique.putIfAbsent(snapshot.id, snapshot)
            if (previous != null && previous != snapshot) throw SecurityException("Conflicting experience identity")
        }
        val receipts = HashSet<String>()
        val accepted = linkedMapOf<String, Pair<Experience, MemorySkillEvidence>>()
        val unresolvedNegativeFamilies = HashSet<Pair<String, List<String>>>()
        var rejected = 0
        for (experience in unique.values) {
            val receipt = evidence[experience.id]?.let { it.copy(sourceReferences = frozen(it.sourceReferences)) }
            if (receipt == null || receipt.scope != scope || !receipt.consentForResearch ||
                receipt.privacy !in policy.allowedPrivacy || receipt.experienceDigest != digest(experience)) {
                rejected++
                continue
            }
            val eligible = receipt.validation == ValidationState.VERIFIED && receipt.provenance in HARD_PROVENANCE
            val verified = try {
                eligible && independentVerifier.independentlyVerified(experience)
            } catch (_: Exception) {
                return MemorySkillReviewReport(MemorySkillReviewStatus.VERIFIER_UNAVAILABLE)
            }
            if (!verified) {
                if (negative(experience)) unresolvedNegativeFamilies += family(experience)
                rejected++
                continue
            }
            if (!receipts.add(receipt.receiptId)) throw SecurityException("Repeated independent receipt")
            accepted[experience.id] = experience to receipt
        }
        val episodes = accepted.values.map { it.first }
        val priorities = episodes.map { episode ->
            PrioritizedEpisode(episode.id, abs((if (negative(episode)) 0.0 else 1.0) - episode.confidenceBefore), negative(episode))
        }.filter { !policy.surpriseEnabled || it.negative || it.residual >= policy.surpriseThreshold }
            .sortedWith(compareByDescending<PrioritizedEpisode> { it.negative }
                .thenByDescending { it.residual }.thenBy { it.experienceId }).take(policy.maxRecall)

        // Compilation retains every eligible outcome.
        val candidates = VerifiedSkillReview(independentVerifier = IndependentExperienceVerifier { it.id in accepted })
            .candidates(episodes)
            .filter { (it.taskType.trim().lowercase(Locale.ROOT) to it.toolNames) !in unresolvedNegativeFamilies }
            .take(policy.maxCandidates).map { candidate ->
                val sources = candidate.sourceExperienceIds.map { accepted.getValue(it) }
                ScopedSkillCandidate(
                    candidate.copy(toolNames = frozen(candidate.toolNames), sourceExperienceIds = frozen(candidate.sourceExperienceIds)),
                    scope,
                    frozen(sources.map { it.second }),
                    frozen(sources.filter { negative(it.first) }.map { it.first.id })
                )
            }
        return MemorySkillReviewReport(MemorySkillReviewStatus.REVIEWED, accepted.size, rejected,
            frozen(priorities), frozen(candidates))
    }

    companion object {
        private val HARD_PROVENANCE = setOf(ProvenanceType.DIRECT_OBSERVATION,
            ProvenanceType.DEVICE_OBSERVATION, ProvenanceType.USER_PROVIDED, ProvenanceType.EXTERNAL_SOURCE)

        private fun negative(e: Experience) = e.outcome != ExperienceOutcome.SUCCESS || !e.evaluation.success || e.failures.isNotEmpty()
        private fun family(e: Experience) = e.taskType.trim().lowercase(Locale.ROOT) to e.toolIds
        private fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
        private fun freeze(e: Experience) = e.copy(memoryIds = frozen(e.memoryIds), modelIds = frozen(e.modelIds),
            sourceIds = frozen(e.sourceIds), toolIds = frozen(e.toolIds), failures = frozen(e.failures),
            evaluatorScores = Collections.unmodifiableMap(LinkedHashMap(e.evaluatorScores)))
        private fun textSize(e: Experience): Long = listOf(e.id, e.taskType, e.requestSummary, e.planSummary,
            e.resultSummary, e.feedback.orEmpty(), e.evaluation.notes).sumOf { it.length.toLong() } +
            (e.memoryIds + e.modelIds + e.sourceIds + e.toolIds + e.failures + e.evaluatorScores.keys).sumOf { it.length.toLong() }

        /** Binds all existing experience fields. */
        fun digest(e: Experience): String {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                fun text(value: String) { val b = value.toByteArray(Charsets.UTF_8); out.writeInt(b.size); out.write(b) }
                fun list(values: List<String>) { out.writeInt(values.size); values.forEach(::text) }
                text("continuum-memory-skill-v1")
                text(e.id); text(e.taskType); text(e.requestSummary)
                list(e.memoryIds); list(e.modelIds); list(e.sourceIds); list(e.toolIds)
                text(e.planSummary); text(e.resultSummary); list(e.failures); out.writeInt(e.retries)
                out.writeInt(e.evaluatorScores.size)
                e.evaluatorScores.toSortedMap().forEach { (key, value) -> text(key); out.writeDouble(value) }
                out.writeBoolean(e.feedback != null); e.feedback?.let(::text)
                out.writeDouble(e.confidenceBefore); out.writeDouble(e.confidenceAfter)
                text(e.outcome.name); out.writeLong(e.startedAt); out.writeLong(e.completedAt)
                out.writeBoolean(e.evaluation.success); out.writeDouble(e.evaluation.score); text(e.evaluation.notes)
            }
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
