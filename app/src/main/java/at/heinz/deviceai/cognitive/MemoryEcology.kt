package at.heinz.deviceai.cognitive

import java.util.Collections
import java.util.Locale

enum class MemoryType { WORKING, EPISODIC, SEMANTIC, PROCEDURAL, SELF, MODEL, SOURCE }
enum class ProvenanceType {
    DIRECT_OBSERVATION, DEVICE_OBSERVATION, USER_PROVIDED, EXTERNAL_SOURCE,
    MODEL_GENERATED, INFERENCE, UNVERIFIED
}
enum class ValidationState { UNVERIFIED, CORROBORATED, VERIFIED, CONTRADICTED, STALE }
enum class PrivacyClass { PUBLIC, PRIVATE, SENSITIVE, DEVICE_LOCAL_ONLY }
enum class MemoryRelation { DERIVED_FROM, SUPPORTS, CONTRADICTS, SUPERSEDES, RELATED_TO, USED_IN }

data class MemoryRecord(
    val id: String,
    val type: MemoryType,
    val content: String,
    val provenance: ProvenanceType,
    val confidence: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String> = emptyList(),
    val parentIds: List<String> = emptyList(),
    val sourceReference: String? = null,
    val validationState: ValidationState = ValidationState.UNVERIFIED,
    val privacyClass: PrivacyClass = PrivacyClass.PRIVATE,
    val validationEvidenceReferences: List<String> = emptyList()
) {
    init {
        require(id.isNotBlank() && content.isNotBlank())
        require(confidence.isFinite() && confidence in 0.0..1.0)
        require(createdAt >= 0 && updatedAt >= createdAt)
        require(tags.none { it.isBlank() } && parentIds.none { it.isBlank() })
        require(validationEvidenceReferences.none { it.isBlank() })
        require(validationState != ValidationState.VERIFIED || validationEvidenceReferences.isNotEmpty()) {
            "verified memories require independent evidence"
        }
    }

    fun validated(state: ValidationState, evidenceReferences: List<String>): MemoryRecord {
        require(state != ValidationState.VERIFIED || evidenceReferences.isNotEmpty()) {
            "verified memories require independent evidence"
        }
        require(evidenceReferences.none { it.isBlank() })
        return copy(
            validationState = state,
            validationEvidenceReferences = (validationEvidenceReferences + evidenceReferences).distinct()
        )
    }
}

interface MemoryStore {
    fun put(record: MemoryRecord)
    fun get(id: String): MemoryRecord?
    fun all(): List<MemoryRecord>
}

// A Kotlin read-only List can still wrap a mutable JVM collection. Stored
// provenance must remain stable when a caller receives a record from get/all.
// Freeze once on write, so repeated recall does not need another deep copy.
private fun <T> frozenListSnapshot(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

class InMemoryMemoryStore : MemoryStore {
    private val records = linkedMapOf<String, MemoryRecord>()

    @Synchronized override fun put(record: MemoryRecord) {
        require(record.id !in records) { "memory id already exists: ${record.id}" }
        records[record.id] = record.copy(
            tags = frozenListSnapshot(record.tags),
            parentIds = frozenListSnapshot(record.parentIds),
            validationEvidenceReferences = frozenListSnapshot(record.validationEvidenceReferences)
        )
    }

    @Synchronized override fun get(id: String): MemoryRecord? = records[id]
    @Synchronized override fun all(): List<MemoryRecord> = records.values.toList()
}

object MemoryRetriever {
    fun retrieve(
        query: String,
        records: Collection<MemoryRecord>,
        limit: Int = 5,
        allowedPrivacy: Set<PrivacyClass> = PrivacyClass.values().toSet()
    ): List<MemoryRecord> {
        require(limit >= 0)
        val terms = tokens(query)
        if (limit == 0 || terms.isEmpty()) return emptyList()
        return records.asSequence()
            .filter { it.privacyClass in allowedPrivacy }
            .map { it to score(terms, tokens(it.content + " " + it.tags.joinToString(" "))) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<MemoryRecord, Int>> { it.second }.thenBy { it.first.id })
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun score(query: Set<String>, content: Set<String>) = query.count { it in content }
    private fun tokens(text: String) =
        text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank).toSet()
}

data class MemoryEdge(val fromId: String, val toId: String, val relation: MemoryRelation)

class MemoryGraph {
    private val edges = linkedSetOf<MemoryEdge>()

    @Synchronized fun add(fromId: String, toId: String, relation: MemoryRelation) {
        require(fromId.isNotBlank() && toId.isNotBlank() && fromId != toId)
        edges += MemoryEdge(fromId, toId, relation)
    }

    @Synchronized fun related(id: String): List<MemoryEdge> =
        edges.filter { it.fromId == id || it.toId == id }
            .sortedWith(compareBy<MemoryEdge> { it.fromId }.thenBy { it.relation.name }.thenBy { it.toId })
}

data class Claim(
    val id: String,
    val text: String,
    val provenance: ProvenanceType,
    val sourceReferences: List<String> = emptyList(),
    val validationState: ValidationState = ValidationState.UNVERIFIED,
    val confidence: Double = 0.5,
    val createdAt: Long,
    val validationEvidenceReferences: List<String> = emptyList()
) {
    init {
        require(id.isNotBlank() && text.isNotBlank() && createdAt >= 0)
        require(confidence.isFinite() && confidence in 0.0..1.0)
        require(sourceReferences.none(String::isBlank))
        require(validationEvidenceReferences.none(String::isBlank))
        require(validationState != ValidationState.VERIFIED || validationEvidenceReferences.isNotEmpty()) {
            "verified claims require independent evidence"
        }
    }

    fun validated(state: ValidationState, evidenceReferences: List<String>): Claim {
        require(state != ValidationState.VERIFIED || evidenceReferences.isNotEmpty()) {
            "verified claims require independent evidence"
        }
        return copy(
            validationState = state,
            sourceReferences = (sourceReferences + evidenceReferences).distinct(),
            validationEvidenceReferences = (validationEvidenceReferences + evidenceReferences).distinct()
        )
    }
}

enum class ClaimRelation { SUPPORTS, CONTRADICTS, SUPERSEDES }

data class ClaimEdge(val fromId: String, val toId: String, val relation: ClaimRelation)

class ClaimGraph {
    private val edges = linkedSetOf<ClaimEdge>()

    @Synchronized fun add(fromId: String, toId: String, relation: ClaimRelation) {
        require(fromId.isNotBlank() && toId.isNotBlank() && fromId != toId)
        edges += ClaimEdge(fromId, toId, relation)
    }

    @Synchronized fun relations(id: String): List<ClaimEdge> =
        edges.filter { it.fromId == id || it.toId == id }
            .sortedWith(compareBy<ClaimEdge> { it.fromId }.thenBy { it.relation.name }.thenBy { it.toId })
}

data class MemoryWriteContext(
    val memoryType: MemoryType,
    val provenance: ProvenanceType,
    val confidence: Double,
    val durableKnowledge: Boolean = false,
    val explicitlyRequested: Boolean = false,
    val reusableForFutureTasks: Boolean = false,
    val containsPersonalInformation: Boolean = false,
    val containsSensitiveInformation: Boolean = false,
    val containsDeviceInformation: Boolean = false
)

data class MemoryWriteDecision(val shouldWrite: Boolean, val privacyClass: PrivacyClass, val reason: String)

class MemoryWritePolicy(private val minimumConfidence: Double = 0.6) {
    init { require(minimumConfidence.isFinite() && minimumConfidence in 0.0..1.0) }

    fun decide(context: MemoryWriteContext): MemoryWriteDecision {
        require(context.confidence.isFinite() && context.confidence in 0.0..1.0)
        val privacy = when {
            context.containsSensitiveInformation -> PrivacyClass.SENSITIVE
            context.containsDeviceInformation || context.provenance == ProvenanceType.DEVICE_OBSERVATION ->
                PrivacyClass.DEVICE_LOCAL_ONLY
            context.containsPersonalInformation || context.provenance == ProvenanceType.USER_PROVIDED ->
                PrivacyClass.PRIVATE
            else -> PrivacyClass.PUBLIC
        }
        val durable = context.durableKnowledge || context.explicitlyRequested || context.reusableForFutureTasks
        val trustworthy = context.provenance !in setOf(ProvenanceType.MODEL_GENERATED, ProvenanceType.UNVERIFIED) ||
            context.confidence >= minimumConfidence
        return when {
            !durable -> MemoryWriteDecision(false, privacy, "interaction is not durable or explicitly requested")
            !trustworthy -> MemoryWriteDecision(false, privacy, "unverified content is below the confidence threshold")
            else -> MemoryWriteDecision(true, privacy, "durable memory is permitted")
        }
    }
}

interface ExperienceLookup {
    fun similar(taskType: String, request: String, limit: Int = 5): List<Experience>

    // Inclusive bounds; no implicit clock.
    // Legacy shortlist may omit matches.
    fun similarWithin(
        taskType: String,
        request: String,
        completedAtFrom: Long,
        completedAtThrough: Long,
        limit: Int = 5
    ): List<Experience> {
        require(limit >= 0)
        require(completedAtFrom >= 0 && completedAtThrough >= completedAtFrom)
        if (limit == 0) return emptyList()
        return similar(taskType, request, limit).asSequence()
            .filter { it.completedAt in completedAtFrom..completedAtThrough }
            .take(limit)
            .toList()
    }
}

class InMemoryExperienceStore : ExperienceStore, ExperienceLookup {
    private val experiences = linkedMapOf<String, Experience>()

    @Synchronized override fun store(e: Experience) {
        require(e.id !in experiences) { "experience id already exists: ${e.id}" }
        experiences[e.id] = e.copy(
            memoryIds = frozenListSnapshot(e.memoryIds), modelIds = frozenListSnapshot(e.modelIds),
            sourceIds = frozenListSnapshot(e.sourceIds), toolIds = frozenListSnapshot(e.toolIds),
            failures = frozenListSnapshot(e.failures),
            evaluatorScores = Collections.unmodifiableMap(LinkedHashMap(e.evaluatorScores))
        )
    }

    @Synchronized override fun all(): List<Experience> = experiences.values.toList()

    override fun similar(taskType: String, request: String, limit: Int): List<Experience> =
        similarWithin(taskType, request, 0, Long.MAX_VALUE, limit)

    override fun similarWithin(
        taskType: String,
        request: String,
        completedAtFrom: Long,
        completedAtThrough: Long,
        limit: Int
    ): List<Experience> {
        require(limit >= 0)
        require(completedAtFrom >= 0 && completedAtThrough >= completedAtFrom)
        val queryTerms = terms(request)
        if (limit == 0) return emptyList()
        return all().asSequence()
            .filter { it.completedAt in completedAtFrom..completedAtThrough }
            .filter { it.taskType.equals(taskType, ignoreCase = true) }
            .map { it to jaccard(queryTerms, terms(it.requestSummary)) }
            .filter { it.second > 0.0 || queryTerms.isEmpty() }
            .sortedWith(compareByDescending<Pair<Experience, Double>> { it.second }
                .thenByDescending { it.first.completedAt }.thenBy { it.first.id })
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun terms(text: String) = text.lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank).toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() && b.isEmpty()) 1.0 else a.intersect(b).size.toDouble() / a.union(b).size
}

enum class ModelCapability {
    CODING, RESEARCH, MATHEMATICS, PLANNING, FACTUAL_ACCURACY,
    DOCUMENT_ANALYSIS, ANDROID_DEBUGGING, TOOL_USE, SOURCE_EVALUATION
}

interface ModelAdapter {
    val id: String
    val capabilities: Set<ModelCapability>
    fun generate(request: ModelRequest): Candidate
}

class MockModelAdapter(
    override val id: String,
    override val capabilities: Set<ModelCapability>,
    private val fixedText: String
) : ModelAdapter {
    init { require(id.isNotBlank() && fixedText.isNotBlank()) }
    override fun generate(request: ModelRequest) = Candidate(id, fixedText)
}

class TestModelAdapter(
    override val id: String,
    override val capabilities: Set<ModelCapability>,
    private val response: (ModelRequest) -> String
) : ModelAdapter {
    init { require(id.isNotBlank()) }
    override fun generate(request: ModelRequest) = Candidate(id, response(request))
}

data class ReputationEvidence(
    val evaluated: Boolean,
    val success: Boolean,
    val evaluatorScore: Double,
    val failure: Boolean = false,
    val hallucination: Boolean = false,
    val correction: Boolean = false,
    val latencyMillis: Long
) {
    init {
        require(evaluatorScore.isFinite() && evaluatorScore in 0.0..1.0)
        require(latencyMillis >= 0)
    }
}

data class CapabilityReputation(
    val successRate: Double = 0.5,
    val evaluatorScore: Double = 0.5,
    val failureRate: Double = 0.5,
    val hallucinationRate: Double = 0.0,
    val correctionRate: Double = 0.0,
    val meanLatencyMillis: Double = 0.0,
    val taskCount: Int = 0
) {
    init {
        require((listOf(successRate, evaluatorScore, failureRate, hallucinationRate, correctionRate)
            .all { it.isFinite() && it in 0.0..1.0 }) && meanLatencyMillis.isFinite() &&
            meanLatencyMillis >= 0 && taskCount >= 0)
    }
}

class ModelReputationStore(private val learningRate: Double = 0.1) {
    private val reputation = linkedMapOf<Triple<String, ModelCapability, String>, CapabilityReputation>()

    init { require(learningRate.isFinite() && learningRate in 0.0..1.0) }

    @Synchronized fun get(modelId: String, capability: ModelCapability, domain: String): CapabilityReputation =
        reputation[Triple(modelId, capability, domain)] ?: CapabilityReputation()

    @Synchronized fun updateAfterEvaluation(
        modelId: String,
        capability: ModelCapability,
        domain: String,
        evidence: ReputationEvidence
    ): CapabilityReputation {
        require(modelId.isNotBlank() && domain.isNotBlank())
        require(evidence.evaluated) { "reputation updates require an evaluated result" }
        val old = get(modelId, capability, domain)
        fun smooth(before: Double, after: Double) = before + learningRate * (after - before)
        val updated = CapabilityReputation(
            successRate = smooth(old.successRate, if (evidence.success) 1.0 else 0.0),
            evaluatorScore = smooth(old.evaluatorScore, evidence.evaluatorScore),
            failureRate = smooth(old.failureRate, if (evidence.failure) 1.0 else 0.0),
            hallucinationRate = smooth(old.hallucinationRate, if (evidence.hallucination) 1.0 else 0.0),
            correctionRate = smooth(old.correctionRate, if (evidence.correction) 1.0 else 0.0),
            meanLatencyMillis = if (old.taskCount == 0) evidence.latencyMillis.toDouble()
            else smooth(old.meanLatencyMillis, evidence.latencyMillis.toDouble()),
            taskCount = old.taskCount + 1
        )
        reputation[Triple(modelId, capability, domain)] = updated
        return updated
    }
}

enum class RoutingStrategy { SINGLE_MODEL, ENSEMBLE, DEBATE, SPECIALIST_PIPELINE, DYNAMIC_TEAM }

data class TaskDescriptor(
    val taskType: String,
    val request: String,
    val capability: ModelCapability,
    val domain: String,
    val complexity: Double = 0.5,
    val requiresIndependentVerification: Boolean = false,
    val preferredStrategy: RoutingStrategy? = null
) {
    init {
        require(taskType.isNotBlank() && request.isNotBlank() && domain.isNotBlank())
        require(complexity.isFinite() && complexity in 0.0..1.0)
    }
}

data class ResourceConstraints(
    val maxModels: Int = 3,
    val maxLatencyMillis: Long? = null,
    val allowEnsemble: Boolean = true
) {
    init { require(maxModels >= 1 && (maxLatencyMillis == null || maxLatencyMillis >= 0)) }
}

data class RoutingPlan(
    val selectedModelIds: List<String>,
    val strategy: RoutingStrategy,
    val reasons: List<String>,
    val confidence: Double,
    val evidence: List<String>
)

class ModelRouter {
    fun route(
        task: TaskDescriptor,
        selfModel: SelfModel,
        adapters: List<ModelAdapter>,
        reputation: ModelReputationStore,
        resources: ResourceConstraints = ResourceConstraints()
    ): RoutingPlan {
        val eligible = adapters.distinctBy { it.id }.filter { adapter ->
            task.capability in adapter.capabilities &&
                (resources.maxLatencyMillis == null ||
                    reputation.get(adapter.id, task.capability, task.domain).meanLatencyMillis <= resources.maxLatencyMillis)
        }.sortedWith(
            compareByDescending<ModelAdapter> {
                reputation.get(it.id, task.capability, task.domain).evaluatorScore
            }.thenBy { it.id }
        )
        require(eligible.isNotEmpty()) { "no adapter satisfies the requested capability and resources" }

        val selfCapability = task.capability.toSelfCapability()
        val estimate = selfCapability?.let(selfModel::get)
        val supportsMultiple = eligible.size >= 2 && resources.maxModels >= 2
        val selectedStrategy = when {
            task.preferredStrategy == RoutingStrategy.DEBATE && supportsMultiple -> RoutingStrategy.DEBATE
            task.preferredStrategy == RoutingStrategy.SPECIALIST_PIPELINE && supportsMultiple -> RoutingStrategy.SPECIALIST_PIPELINE
            task.preferredStrategy == RoutingStrategy.DYNAMIC_TEAM && supportsMultiple -> RoutingStrategy.DYNAMIC_TEAM
            task.preferredStrategy == RoutingStrategy.ENSEMBLE && supportsMultiple && resources.allowEnsemble -> RoutingStrategy.ENSEMBLE
            task.requiresIndependentVerification && supportsMultiple && resources.allowEnsemble -> RoutingStrategy.ENSEMBLE
            estimate?.confidence?.let { it < 0.35 } == true && supportsMultiple && resources.allowEnsemble ->
                RoutingStrategy.ENSEMBLE
            task.complexity >= 0.85 && supportsMultiple && eligible.size >= 3 -> RoutingStrategy.DYNAMIC_TEAM
            else -> RoutingStrategy.SINGLE_MODEL
        }
        val count = when (selectedStrategy) {
            RoutingStrategy.SINGLE_MODEL -> 1
            RoutingStrategy.ENSEMBLE, RoutingStrategy.DEBATE -> 2
            RoutingStrategy.SPECIALIST_PIPELINE -> minOf(2, eligible.size)
            RoutingStrategy.DYNAMIC_TEAM -> minOf(resources.maxModels, eligible.size)
        }.coerceAtMost(resources.maxModels).coerceAtMost(eligible.size)
        val chosen = eligible.take(count)
        val reasons = mutableListOf("Adapters are ranked by evaluated reputation for ${task.capability} in ${task.domain}.")
        if (estimate != null) {
            reasons += "Self-model ${selfCapability} confidence=${"%.2f".format(Locale.ROOT, estimate.confidence)} " +
                "and competence=${"%.2f".format(Locale.ROOT, estimate.competence)} informed strategy selection."
        }
        if (chosen.size > 1) reasons += "Multiple eligible adapters are selected for ${selectedStrategy.name.lowercase(Locale.ROOT)}."
        val score = chosen.map { reputation.get(it.id, task.capability, task.domain).evaluatorScore }.average()
        val confidence = (score * (estimate?.confidence?.let { 0.75 + 0.25 * it } ?: 1.0)).coerceIn(0.0, 1.0)
        return RoutingPlan(
            selectedModelIds = chosen.map { it.id },
            strategy = selectedStrategy,
            reasons = reasons,
            confidence = confidence,
            evidence = chosen.map {
                "${it.id}: evaluated score=${reputation.get(it.id, task.capability, task.domain).evaluatorScore}, " +
                    "tasks=${reputation.get(it.id, task.capability, task.domain).taskCount}"
            }
        )
    }

    private fun ModelCapability.toSelfCapability(): Capability? = when (this) {
        ModelCapability.CODING -> Capability.CODING
        ModelCapability.RESEARCH -> Capability.RESEARCH
        ModelCapability.MATHEMATICS -> Capability.MATHEMATICS
        ModelCapability.PLANNING -> Capability.PLANNING
        ModelCapability.FACTUAL_ACCURACY -> Capability.FACTUAL_ACCURACY
        ModelCapability.DOCUMENT_ANALYSIS -> Capability.DOCUMENT_ANALYSIS
        ModelCapability.ANDROID_DEBUGGING -> Capability.ANDROID_DEBUGGING
        ModelCapability.TOOL_USE -> Capability.TOOL_USE
        ModelCapability.SOURCE_EVALUATION -> Capability.SOURCE_EVALUATION
    }
}

data class SourceIdentity(val id: String, val domain: String) {
    init { require(id.isNotBlank() && domain.isNotBlank()) }
}

data class SourceObservation(
    val source: SourceIdentity,
    val evaluated: Boolean,
    val accuracy: Double,
    val freshness: Double,
    val corroborated: Boolean = false,
    val contradicted: Boolean = false,
    val corrected: Boolean = false
) {
    init {
        require(listOf(accuracy, freshness).all { it.isFinite() && it in 0.0..1.0 })
    }
}

data class SourceTrustProfile(
    val source: SourceIdentity,
    val historicalAccuracy: Double = 0.5,
    val freshness: Double = 0.5,
    val corroborationRate: Double = 0.5,
    val contradictionRate: Double = 0.0,
    val correctionRate: Double = 0.0,
    val evaluatedClaims: Int = 0,
    val confidence: Double = 0.0
) {
    init {
        require(listOf(historicalAccuracy, freshness, corroborationRate, contradictionRate, correctionRate)
            .all { it.isFinite() && it in 0.0..1.0 } &&
            confidence.isFinite() && confidence in 0.0..1.0 && evaluatedClaims >= 0)
    }
}

class SourceTrustStore(private val learningRate: Double = 0.1) {
    private val profiles = linkedMapOf<SourceIdentity, SourceTrustProfile>()
    init { require(learningRate.isFinite() && learningRate in 0.0..1.0) }

    @Synchronized fun get(source: SourceIdentity): SourceTrustProfile =
        profiles[source] ?: SourceTrustProfile(source)

    @Synchronized fun observe(observation: SourceObservation): SourceTrustProfile {
        require(observation.evaluated) { "source trust changes require an evaluated claim" }
        val old = get(observation.source)
        fun smooth(before: Double, after: Double) = before + learningRate * (after - before)
        return SourceTrustProfile(
            source = observation.source,
            historicalAccuracy = smooth(old.historicalAccuracy, observation.accuracy),
            freshness = smooth(old.freshness, observation.freshness),
            corroborationRate = smooth(old.corroborationRate, if (observation.corroborated) 1.0 else 0.0),
            contradictionRate = smooth(old.contradictionRate, if (observation.contradicted) 1.0 else 0.0),
            correctionRate = smooth(old.correctionRate, if (observation.corrected) 1.0 else 0.0),
            evaluatedClaims = old.evaluatedClaims + 1,
            confidence = smooth(old.confidence, 1.0)
        ).also { profiles[observation.source] = it }
    }
}

fun contentHash(content: String): String = sha256Hex(content)

interface IdentitySigner {
    fun sign(payload: ByteArray): ByteArray
    fun verify(payload: ByteArray, signature: ByteArray): Boolean
}
