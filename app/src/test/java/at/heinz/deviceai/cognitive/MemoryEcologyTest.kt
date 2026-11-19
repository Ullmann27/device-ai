package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MemoryEcologyTest {
    @Test fun memoryValidationStoreRetrievalAndGraphAreDeterministic() {
        assertThrows(IllegalArgumentException::class.java) {
            memory("bad", provenance = ProvenanceType.MODEL_GENERATED, validation = ValidationState.VERIFIED)
        }
        assertEquals(
            ValidationState.VERIFIED,
            memory("validated", provenance = ProvenanceType.MODEL_GENERATED)
                .validated(ValidationState.VERIFIED, listOf("independent-source")).validationState
        )
        val store = InMemoryMemoryStore()
        store.put(memory("z", content = "Android device battery"))
        store.put(memory("a", content = "Android device usage"))
        store.put(memory("private", content = "Android account", privacy = PrivacyClass.SENSITIVE))
        assertEquals(
            listOf("a", "z", "private"),
            MemoryRetriever.retrieve("android device", store.all()).map { it.id }
        )
        assertEquals(
            listOf("a", "z"),
            MemoryRetriever.retrieve(
                "android device", store.all(), allowedPrivacy = setOf(PrivacyClass.PRIVATE)
            ).map { it.id }
        )
        assertThrows(IllegalArgumentException::class.java) { store.put(memory("a")) }
        val graph = MemoryGraph()
        graph.add("a", "z", MemoryRelation.DERIVED_FROM)
        graph.add("a", "z", MemoryRelation.SUPPORTS)
        assertEquals(2, graph.related("a").size)
    }

    @Test fun provenanceAndValidationStateArePreserved() {
        val record = memory(
            "observed", provenance = ProvenanceType.DEVICE_OBSERVATION,
            validation = ValidationState.CORROBORATED
        )
        val store = InMemoryMemoryStore()
        store.put(record)
        assertEquals(ProvenanceType.DEVICE_OBSERVATION, store.get("observed")!!.provenance)
        assertEquals(ValidationState.CORROBORATED, store.get("observed")!!.validationState)
    }

    @Test fun writePolicyAvoidsEphemeralMemoryAndPreservesPrivateClasses() {
        val policy = MemoryWritePolicy()
        assertFalse(policy.decide(MemoryWriteContext(MemoryType.EPISODIC, ProvenanceType.USER_PROVIDED, 1.0)).shouldWrite)
        val user = policy.decide(
            MemoryWriteContext(
                MemoryType.SEMANTIC, ProvenanceType.USER_PROVIDED, 0.9,
                explicitlyRequested = true, containsPersonalInformation = true
            )
        )
        assertTrue(user.shouldWrite)
        assertEquals(PrivacyClass.PRIVATE, user.privacyClass)
        assertEquals(
            PrivacyClass.PRIVATE,
            policy.decide(
                MemoryWriteContext(MemoryType.SEMANTIC, ProvenanceType.USER_PROVIDED, 0.9, explicitlyRequested = true)
            ).privacyClass
        )
        val device = policy.decide(
            MemoryWriteContext(
                MemoryType.SEMANTIC, ProvenanceType.DEVICE_OBSERVATION, 1.0,
                durableKnowledge = true, containsDeviceInformation = true
            )
        )
        assertEquals(PrivacyClass.DEVICE_LOCAL_ONLY, device.privacyClass)
        assertEquals(
            PrivacyClass.DEVICE_LOCAL_ONLY,
            policy.decide(
                MemoryWriteContext(MemoryType.SELF, ProvenanceType.DEVICE_OBSERVATION, 0.9, durableKnowledge = true)
            ).privacyClass
        )
        assertFalse(
            policy.decide(
                MemoryWriteContext(MemoryType.SEMANTIC, ProvenanceType.MODEL_GENERATED, 0.2, durableKnowledge = true)
            ).shouldWrite
        )
    }

    @Test fun experienceStoreRetrievesSimilarTasks() {
        val store = InMemoryExperienceStore()
        store.store(experience("1", "debug Android battery service"))
        store.store(experience("2", "plan a weekend trip"))
        assertEquals(listOf("1"), store.similar("debugging", "Android battery service fails").map { it.id })
        assertEquals(2, store.all().size)
    }

    @Test fun adaptersAreDeterministicAndProviderNeutral() {
        val request = ModelRequest("hello")
        val mock = MockModelAdapter("mock", setOf(ModelCapability.CODING), "fixed")
        val test = TestModelAdapter("test", setOf(ModelCapability.CODING)) { "answer:${it.prompt}" }
        assertEquals(Candidate("mock", "fixed"), mock.generate(request))
        assertEquals(Candidate("test", "answer:hello"), test.generate(request))
    }

    @Test fun modelReputationUpdatesGraduallyOnlyAfterEvaluation() {
        val reputation = ModelReputationStore(learningRate = 0.1)
        assertThrows(IllegalArgumentException::class.java) {
            reputation.updateAfterEvaluation(
                "m", ModelCapability.CODING, "code",
                ReputationEvidence(false, true, 1.0, latencyMillis = 20)
            )
        }
        val next = reputation.updateAfterEvaluation(
            "m", ModelCapability.CODING, "code",
            ReputationEvidence(true, true, 1.0, latencyMillis = 20)
        )
        assertEquals(0.55, next.successRate, 0.0001)
        assertEquals(0.55, next.evaluatorScore, 0.0001)
        assertEquals(1, next.taskCount)
        assertEquals(0.5, reputation.get("m", ModelCapability.CODING, "other-domain").evaluatorScore, 0.0)
    }

    @Test fun routerExplainsChoiceAndSelfModelInfluencesStrategy() {
        val adapters = listOf(
            MockModelAdapter("a", setOf(ModelCapability.CODING), "a"),
            MockModelAdapter("b", setOf(ModelCapability.CODING), "b")
        )
        val task = TaskDescriptor("coding", "implement a parser", ModelCapability.CODING, "code")
        val router = ModelRouter()
        val uncertain = router.route(task, SelfModel.uncertain(), adapters, ModelReputationStore())
        assertEquals(RoutingStrategy.ENSEMBLE, uncertain.strategy)
        assertEquals(2, uncertain.selectedModelIds.size)
        assertTrue(uncertain.reasons.any { it.contains("Self-model") })
        assertTrue(uncertain.evidence.isNotEmpty())

        var self = SelfModel.uncertain()
        repeat(10) { index ->
            self = self.withEvidence(
                CapabilityEvidence("task-$index", Capability.CODING, 1.0, 1.0, "test", index.toLong()),
                rate = 1.0
            )
        }
        val confident = router.route(task, self, adapters, ModelReputationStore())
        assertEquals(RoutingStrategy.SINGLE_MODEL, confident.strategy)
        assertEquals(1, confident.selectedModelIds.size)

        val three = adapters + MockModelAdapter("c", setOf(ModelCapability.CODING), "c")
        for (strategy in listOf(RoutingStrategy.DEBATE, RoutingStrategy.SPECIALIST_PIPELINE, RoutingStrategy.DYNAMIC_TEAM)) {
            assertEquals(
                strategy,
                router.route(task.copy(preferredStrategy = strategy), self, three, ModelReputationStore()).strategy
            )
        }
        val constrained = router.route(
            task.copy(preferredStrategy = RoutingStrategy.ENSEMBLE),
            SelfModel.uncertain(), three, ModelReputationStore(), ResourceConstraints(maxModels = 1)
        )
        assertEquals(RoutingStrategy.SINGLE_MODEL, constrained.strategy)
        assertEquals(1, constrained.selectedModelIds.size)
    }

    @Test fun sourceTrustIsGradualAndDomainAware() {
        val store = SourceTrustStore(0.1)
        val source = SourceIdentity("publisher", "science")
        assertEquals(0.5, store.get(source).historicalAccuracy, 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            store.observe(SourceObservation(source, false, 1.0, 1.0))
        }
        val updated = store.observe(SourceObservation(source, true, 1.0, 0.8, corroborated = true))
        assertEquals(0.55, updated.historicalAccuracy, 0.0001)
        assertEquals(1, updated.evaluatedClaims)
        assertEquals(0.1, updated.confidence, 0.0001)
        assertEquals(0.5, store.get(SourceIdentity("publisher", "finance")).historicalAccuracy, 0.0)
    }

    @Test fun claimsKeepEvidenceAndRelationsExplicit() {
        val generated = Claim("c1", "a generated claim", ProvenanceType.MODEL_GENERATED, createdAt = 1)
        assertEquals(ValidationState.UNVERIFIED, generated.validationState)
        assertThrows(IllegalArgumentException::class.java) {
            generated.validated(ValidationState.VERIFIED, emptyList())
        }
        assertEquals(
            listOf("independent-source"),
            generated.validated(ValidationState.VERIFIED, listOf("independent-source")).sourceReferences
        )
        val graph = ClaimGraph()
        graph.add("c2", "c1", ClaimRelation.SUPPORTS)
        graph.add("c3", "c1", ClaimRelation.CONTRADICTS)
        graph.add("c4", "c1", ClaimRelation.SUPERSEDES)
        assertEquals(3, graph.relations("c1").size)
    }

    @Test fun contentHashIsNotAnIdentitySignature() {
        val content = "identity payload".toByteArray()
        assertEquals(contentHash("identity payload"), contentHash("identity payload"))
        assertNotEquals(contentHash("identity payload"), contentHash("different"))
        val signer = TestIdentitySigner(byteArrayOf(1, 2, 3))
        val signature = signer.sign(content)
        assertTrue(signer.verify(content, signature))
        assertFalse(signer.verify("changed".toByteArray(), signature))
        assertNotEquals(contentHash("identity payload"), signature.joinToString("") { "%02x".format(it) })
    }

    private fun memory(
        id: String,
        content: String = "memory content",
        provenance: ProvenanceType = ProvenanceType.USER_PROVIDED,
        validation: ValidationState = ValidationState.UNVERIFIED,
        privacy: PrivacyClass = PrivacyClass.PRIVATE
    ) = MemoryRecord(id, MemoryType.SEMANTIC, content, provenance, 0.8, 1, 2, privacyClass = privacy, validationState = validation)

    private fun experience(id: String, request: String) = Experience(
        id = id, taskType = "debugging", requestSummary = request, startedAt = 1, completedAt = 2,
        evaluation = Evaluation(true, 0.8)
    )
}

private class TestIdentitySigner(private val key: ByteArray) : IdentitySigner {
    override fun sign(payload: ByteArray): ByteArray = mac(payload)
    override fun verify(payload: ByteArray, signature: ByteArray): Boolean =
        java.security.MessageDigest.isEqual(sign(payload), signature)

    private fun mac(payload: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(payload)
        }
}
