package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Research-only before/after comparison for the existing HybridMemoryRetriever.
 *
 * This class never installs a scorer or promotes model weights, memory claims,
 * or owner permissions. The candidate scorer must be a trusted local callback:
 * an arbitrary callback could export the memory/query text it receives.
 *
 * The caller is responsible for freezing independent holdout questions before
 * tuning the candidate, and for retaining independent build and device evidence.
 */
data class MemoryResearchShadowComparison(
    val developmentBaseline: MemoryResearchSummary,
    val developmentCandidate: MemoryResearchSummary,
    val holdoutBaseline: MemoryResearchSummary,
    val holdoutCandidate: MemoryResearchSummary,
    val developmentImproved: Boolean,
    val holdoutImproved: Boolean,
    val noPerProbeRegression: Boolean,
    val eligibleForOwnerReview: Boolean
)

object MemoryResearchShadowComparator {
    /** Fixed synthetic plumbing check, never a measurement of owner-memory recall
     * or learned semantic quality. No owner store, network, model or tool access.
     * Zero candidate scores deliberately cannot earn promotion/review eligibility.
     */
    fun syntheticSelfCheck(): MemoryResearchShadowComparison {
        val records = listOf(
            "static" to "synthetic battery capacity",
            "dynamic" to "synthetic engine version changed",
            "workflow" to "synthetic approve tool execution",
            "environment" to "synthetic thermal device limit"
        ).map { (id, content) ->
            MemoryRecord(
                id = id, type = MemoryType.SEMANTIC, content = content,
                provenance = ProvenanceType.DIRECT_OBSERVATION, confidence = 0.9,
                createdAt = 1_000L, updatedAt = 1_000L,
                privacyClass = PrivacyClass.PUBLIC,
                validationState = ValidationState.VERIFIED,
                validationEvidenceReferences = listOf("synthetic-fixture:$id")
            )
        }
        return compare(
            developmentProbes = listOf(MemoryResearchProbe(
                "selfcheck-dev", MemoryResearchAbility.STATIC_RECALL,
                "battery", setOf("static")
            )),
            frozenHoldoutProbes = listOf(
                MemoryResearchProbe("selfcheck-static", MemoryResearchAbility.STATIC_RECALL,
                    "battery capacity", setOf("static")),
                MemoryResearchProbe("selfcheck-dynamic", MemoryResearchAbility.DYNAMIC_STATE,
                    "engine version", setOf("dynamic")),
                MemoryResearchProbe("selfcheck-workflow", MemoryResearchAbility.WORKFLOW,
                    "approve tool", setOf("workflow")),
                MemoryResearchProbe("selfcheck-environment", MemoryResearchAbility.ENVIRONMENT_GOTCHA,
                    "thermal device", setOf("environment")),
                MemoryResearchProbe("selfcheck-unknown", MemoryResearchAbility.PREMISE_AWARENESS,
                    "unrelatedwombat", emptySet())
            ),
            records = records, allowedPrivacy = setOf(PrivacyClass.PUBLIC),
            candidateSemanticScorer = { _, _ -> 0.0 }, nowMillis = 1_000L
        )
    }

    fun compare(
        developmentProbes: List<MemoryResearchProbe>,
        frozenHoldoutProbes: List<MemoryResearchProbe>,
        records: Collection<MemoryRecord>,
        allowedPrivacy: Set<PrivacyClass>,
        candidateSemanticScorer: (String, MemoryRecord) -> Double,
        limit: Int = 8,
        nowMillis: Long = System.currentTimeMillis()
    ): MemoryResearchShadowComparison {
        require(developmentProbes.size in 1..64)
        require(frozenHoldoutProbes.size in 1..64)
        require(records.size <= 2_000)
        require(records.map { it.id }.toSet().size == records.size) {
            "Duplicate memory identifiers"
        }
        val allProbes = developmentProbes + frozenHoldoutProbes
        require(allProbes.map { it.id }.toSet().size == allProbes.size) {
            "Development and holdout probe IDs overlap"
        }
        val developmentQueries = developmentProbes.map { it.query.trim().lowercase(Locale.ROOT) }.toSet()
        val holdoutQueries = frozenHoldoutProbes.map { it.query.trim().lowercase(Locale.ROOT) }.toSet()
        require(developmentQueries.intersect(holdoutQueries).isEmpty()) {
            "Development and holdout questions overlap"
        }
        require(frozenHoldoutProbes.map { it.ability }.toSet() ==
            MemoryResearchAbility.values().toSet()) {
            "Frozen holdout must cover all five memory abilities"
        }

        val memoryById = records.associateBy { it.id }
        allProbes.flatMap { it.expectedMemoryIds }.forEach { id ->
            val gold = memoryById[id]
            require(gold != null && gold.privacyClass in allowedPrivacy &&
                gold.validationState == ValidationState.VERIFIED &&
                gold.provenance !in setOf(
                    ProvenanceType.MODEL_GENERATED,
                    ProvenanceType.INFERENCE,
                    ProvenanceType.UNVERIFIED
                )) {
                "Expected answer is missing, privacy-forbidden or not independently verified"
            }
        }

        fun run(probes: List<MemoryResearchProbe>, scorer: ((String, MemoryRecord) -> Double)?):
            MemoryResearchSummary = MemoryRetrievalResearchGate.evaluate(
                probes = probes,
                records = records,
                allowedPrivacy = allowedPrivacy,
                limit = limit,
                nowMillis = nowMillis,
                semanticScorer = scorer
            )

        val developmentBaseline = run(developmentProbes, null)
        val developmentCandidate = run(developmentProbes, candidateSemanticScorer)
        val holdoutBaseline = run(frozenHoldoutProbes, null)
        val holdoutCandidate = run(frozenHoldoutProbes, candidateSemanticScorer)

        val developmentSafe = doesNotRegress(developmentBaseline, developmentCandidate)
        val holdoutSafe = doesNotRegress(holdoutBaseline, holdoutCandidate)
        val developmentImproved = developmentSafe &&
            strictlyImproves(developmentBaseline, developmentCandidate)
        val holdoutImproved = holdoutSafe &&
            strictlyImproves(holdoutBaseline, holdoutCandidate)

        return MemoryResearchShadowComparison(
            developmentBaseline = developmentBaseline,
            developmentCandidate = developmentCandidate,
            holdoutBaseline = holdoutBaseline,
            holdoutCandidate = holdoutCandidate,
            developmentImproved = developmentImproved,
            holdoutImproved = holdoutImproved,
            noPerProbeRegression = developmentSafe && holdoutSafe,
            eligibleForOwnerReview = developmentImproved && holdoutImproved
        )
    }

    private fun doesNotRegress(
        baseline: MemoryResearchSummary,
        candidate: MemoryResearchSummary
    ): Boolean = baseline.results.size == candidate.results.size &&
        baseline.results.zip(candidate.results).all { (before, after) ->
            before.probeId == after.probeId &&
                (!before.correctTopOneOrAbstention || after.correctTopOneOrAbstention) &&
                (!before.anyExpectedWithinLimit || after.anyExpectedWithinLimit) &&
                after.reciprocalRank + 1e-12 >= before.reciprocalRank
        }

    private fun strictlyImproves(
        baseline: MemoryResearchSummary,
        candidate: MemoryResearchSummary
    ): Boolean = baseline.results.zip(candidate.results).any { (before, after) ->
        (!before.correctTopOneOrAbstention && after.correctTopOneOrAbstention) ||
            (!before.anyExpectedWithinLimit && after.anyExpectedWithinLimit) ||
            after.reciprocalRank > before.reciprocalRank + 1e-12
    }
}
