package at.heinz.deviceai.cognitive

object ContextIntegrityFixture {
    fun input(request: String, memories: List<MemoryRecord> = emptyList(),
              contradictions: List<String> = emptyList(), dossier: ResearchDossier? = null): AiContextInput {
        val genome = CognitiveGenome("sovereign-context")
        val identity = IdentityState.initial(genome)
        val seed = ContinuumSeed(ContinuumInstanceId(identity.identityId), GovernanceVersion(1),
            StateFormatVersion(1), 1, 1, ModelAdapterAbiVersion(1), "owner:test", listOf(ComputeNodeId("fold")),
            ContinuumObjectId.of(ContinuumObjectType.STATE_MANIFEST, "fixture".toByteArray()), createdAtMillis = 1L)
        return AiContextInput(seed, genome, identity, request, memories,
            unresolvedContradictions = contradictions, researchDossier = dossier)
    }
    fun memory(id: String, content: String) = MemoryRecord(id, MemoryType.SEMANTIC, content,
        ProvenanceType.USER_PROVIDED, 0.9, 1, 1, privacyClass = PrivacyClass.DEVICE_LOCAL_ONLY)

    fun dossier(snippet: String): ResearchDossier {
        val hash = sha256Hex(snippet)
        val plan = ResearchPlan(ResearchMode.QUICK, "Wo ist Maren?",
            listOf(ResearchQuestion("q0", "Wo ist Maren?")), emptyList(), ResearchBudgetPolicy.forMode(ResearchMode.QUICK))
        return ResearchDossier("0123456789abcdef", 1, 1, plan, ResearchPhase.COMPLETE,
            listOf(ResearchSource("https://example.org/evidence", "Example", 1, hash, "example.org", snippet = snippet)),
            ResearchEvidenceGraph(emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap()),
            emptyList(), null, setOf(hash), false)
    }
}
