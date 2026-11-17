package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStoreSnapshotTest {
    private fun record() = MemoryRecord(
        id = "memory-1", type = MemoryType.SEMANTIC, content = "Maren besucht den Hafen.",
        provenance = ProvenanceType.USER_PROVIDED, confidence = .9,
        createdAt = 1L, updatedAt = 2L,
        tags = mutableListOf("Hafen", "Besuch"),
        parentIds = mutableListOf("parent-1", "parent-2"),
        validationState = ValidationState.VERIFIED,
        validationEvidenceReferences = mutableListOf("proof-1", "proof-2")
    )

    private fun experience() = Experience(
        id = "experience-1", taskType = "debugging", requestSummary = "Repair parser error",
        memoryIds = mutableListOf("memory-1", "memory-2"),
        modelIds = mutableListOf("model-1", "model-2"),
        sourceIds = mutableListOf("source-1", "source-2"),
        toolIds = mutableListOf("tool-1", "tool-2"),
        failures = mutableListOf("compiler failure", "verification failure"),
        evaluatorScores = linkedMapOf("compile" to .25, "independent" to .1),
        outcome = ExperienceOutcome.FAILURE, startedAt = 1L, completedAt = 2L,
        evaluation = Evaluation(false, .1)
    )

    private fun attemptReplace(list: List<String>) {
        try { (list as MutableList<String>)[0] = "injected-caller-value" }
        catch (_: UnsupportedOperationException) { }
    }

    @Test fun mutatingAGetResultCannotChangeTheStoredTagsParentsOrEvidence() {
        val store = InMemoryMemoryStore()
        val original = record()
        store.put(original)
        val exposed = store.get(original.id)!!
        attemptReplace(exposed.tags)
        attemptReplace(exposed.parentIds)
        attemptReplace(exposed.validationEvidenceReferences)

        assertEquals(original, store.get(original.id))
    }

    @Test fun clearingEvidenceThroughAllCannotLeaveAVerifiedRecordWithoutProof() {
        val store = InMemoryMemoryStore()
        store.put(record())
        val exposed = store.all().single()
        try { (exposed.validationEvidenceReferences as MutableList<String>).clear() }
        catch (_: UnsupportedOperationException) { }

        val retained = store.get(exposed.id)!!
        assertEquals(ValidationState.VERIFIED, retained.validationState)
        assertEquals(listOf("proof-1", "proof-2"), retained.validationEvidenceReferences)
    }

    @Test fun callerCannotChangeFutureRecallByEditingAReturnedTag() {
        val store = InMemoryMemoryStore()
        store.put(record())
        attemptReplace(store.get("memory-1")!!.tags)

        assertTrue(HybridMemoryRetriever.retrieve("injected-caller-value", store.all(), nowMillis = 10L).isEmpty())
        assertEquals("memory-1", HybridMemoryRetriever.retrieve("Hafen", store.all(), nowMillis = 10L).single().memory.id)
    }

    @Test fun mutatingReturnedExperienceCannotChangePastOutcomesOrProvenance() {
        val store = InMemoryExperienceStore()
        val original = experience()
        store.store(original)
        val exposed = store.all().single()
        listOf(exposed.memoryIds, exposed.modelIds, exposed.sourceIds, exposed.toolIds, exposed.failures)
            .forEach(::attemptReplace)
        try { (exposed.evaluatorScores as MutableMap<String, Double>)["compile"] = 1.0 }
        catch (_: UnsupportedOperationException) { }

        assertEquals(original, store.all().single())
    }

    @Test fun similarResultsCannotCorruptEvaluationScores() {
        val store = InMemoryExperienceStore()
        val original = experience()
        store.store(original)
        val exposed = store.similar("debugging", "parser error").single()
        try { (exposed.evaluatorScores as MutableMap<String, Double>)["compile"] = Double.NaN }
        catch (_: UnsupportedOperationException) { }

        assertEquals(original.evaluatorScores, store.similar("debugging", "parser error").single().evaluatorScores)
    }

    @Test fun storedSnapshotsAlsoRemainIndependentFromInputCollections() {
        val memories = InMemoryMemoryStore()
        val record = record()
        memories.put(record)
        attemptReplace(record.tags)
        attemptReplace(record.parentIds)
        attemptReplace(record.validationEvidenceReferences)
        assertEquals(listOf("Hafen", "Besuch"), memories.get(record.id)!!.tags)
        assertEquals(listOf("parent-1", "parent-2"), memories.get(record.id)!!.parentIds)
        assertEquals(listOf("proof-1", "proof-2"), memories.get(record.id)!!.validationEvidenceReferences)

        val experiences = InMemoryExperienceStore()
        val experience = experience()
        experiences.store(experience)
        attemptReplace(experience.failures)
        (experience.evaluatorScores as MutableMap<String, Double>)["compile"] = 1.0
        assertEquals(listOf("compiler failure", "verification failure"), experiences.all().single().failures)
        assertEquals(.25, experiences.all().single().evaluatorScores.getValue("compile"), .0)
    }
}
