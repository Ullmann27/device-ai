package at.heinz.deviceai.cognitive.research

import at.heinz.deviceai.cognitive.*
import java.io.File
import java.security.MessageDigest

/** Deterministic selection, never model inference. */
object MemorySkillFourArmExperiment {
    private enum class Arm { NONE, RAW, EPISODIC, PROCEDURAL }
    private data class Task(val id: String, val taskType: String, val query: String,
        val expected: String?, val scope: MemorySkillScope)
    private val scope = MemorySkillScope("synthetic-benchmark", "memory-1", "no-model", "policy-1", "synthetic-source-1")
    private val families = listOf("battery" to "get_battery_info", "storage" to "get_storage_info",
        "network" to "get_network_summary", "device" to "get_device_info")

    private fun training(): List<Experience> = buildList {
        for ((family, tool) in families) {
            repeat(3) { i -> add(episode("train-$family-$i", family, "$family inspect signal record $i", tool, 0.2)) }
            add(episode("negative-$family", family, "$family previous unsafe attempt", "delete_file", 0.95, false))
            add(episode("poison-$family", family, "$family inspect signal detail status health verify report", "delete_file", 0.95))
        }
        repeat(40) { i -> add(episode("routine-$i", "routine", "unrelated predictable record $i", "get_device_info", 0.99)) }
    }

    private fun episode(id: String, type: String, request: String, tool: String, confidence: Double,
        success: Boolean = true) = Experience(id, type, request,
        sourceIds = listOf("synthetic:$id"), toolIds = listOf(tool), confidenceBefore = confidence,
        failures = if (success) emptyList() else listOf("independent check failed"),
        outcome = if (success) ExperienceOutcome.SUCCESS else ExperienceOutcome.FAILURE,
        startedAt = 1, completedAt = 2, evaluation = Evaluation(success, if (success) 0.95 else 0.1))

    private fun holdout(): List<Task> = buildList {
        for ((family, tool) in families) {
            add(Task("holdout-$family-a", family, "$family inspect detail status", tool, scope))
            add(Task("holdout-$family-b", family, "$family signal health verify", tool, scope))
        }
        add(Task("holdout-novel", "camera", "camera focus health", null, scope))
        add(Task("holdout-revision", "battery", "battery inspect", null, scope.copy(memoryRevision = "memory-2")))
        add(Task("holdout-owner", "storage", "storage inspect", null, scope.copy(ownerId = "other")))
        add(Task("holdout-new-model", "network", "network inspect", null, scope.copy(modelRevision = "new-model")))
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1)
        val out = File(args.single()).also { it.mkdirs() }
        val fullHistory = training()
        val holdout = holdout()
        val results = mutableListOf("fixture\tablation\tarm\ttask\tanswer\texpected\tcorrect\tcontext_characters")
        val summaries = mutableListOf("fixture\tablation\tarm\tpositive_correct\tpositive_total\tnegative_correct\tnegative_total\tcontext_characters\tprioritized_episodes")
        for (poisoned in listOf(false, true)) {
        val fixture = if (poisoned) "poisoned" else "clean"
        val history = fullHistory.filter { poisoned || !it.id.startsWith("poison-") }
        check(history.map { it.id }.toSet().intersect(holdout.map { it.id }.toSet()).isEmpty())
        check(history.map { it.requestSummary }.toSet().intersect(holdout.map { it.query }.toSet()).isEmpty())
        val receipts = history.associate { e -> e.id to MemorySkillEvidence(scope,
            ContinuumMemorySkillResearch.digest(e), "receipt-${e.id}", e.sourceIds,
            ProvenanceType.DIRECT_OBSERVATION, PrivacyClass.PUBLIC, ValidationState.VERIFIED, true) }
        // Synthetic oracle lives outside episodes.
        val verifiedIds = history.filterNot { it.id.startsWith("poison-") }.map { it.id }.toSet()
        val verifier = IndependentExperienceVerifier { it.id in verifiedIds }
        for (surprise in listOf(true, false)) {
            val gate = ContinuumMemorySkillResearch(MemorySkillResearchPolicy(maxRecall = 64,
                surpriseEnabled = surprise), verifier)
            val report = gate.review(history, scope, receipts, true)
            check(report.reviewedCount == 56 && report.rejectedCount == if (poisoned) 4 else 0)
            check(report.candidates.all { it.candidate.requiresOwnerApproval })
            check(report.candidates.none { it.candidate.toolNames == listOf("delete_file") })
            val priorities = report.prioritized.map { it.experienceId }.toSet()
            val episodic = InMemoryExperienceStore().also { store ->
                history.filter { it.id in priorities }.forEach(store::store)
            }
            val raw = history.map { e -> MemoryRecord(e.id, MemoryType.EPISODIC, e.requestSummary,
                ProvenanceType.DIRECT_OBSERVATION, 0.5, 1, 1, privacyClass = PrivacyClass.PUBLIC) }
            for (arm in Arm.entries) {
                var positive = 0
                var negative = 0
                var contextCharacters = 0
                for (task in holdout) {
                    // Labels are used only below.
                    var chars = 0
                    val answer = if (task.scope != scope) null else when (arm) {
                        Arm.NONE -> null
                        Arm.RAW -> {
                            val matches = MemoryRetriever.retrieve(task.query, raw, limit = 8)
                            chars = matches.sumOf { it.content.length }
                            matches.firstOrNull()?.let { hit -> history.single { it.id == hit.id }.toolIds.single() }
                        }
                        Arm.EPISODIC -> {
                            val matches = episodic.similar(task.taskType, task.query, limit = 64)
                            chars = matches.sumOf { it.requestSummary.length }
                            matches.firstOrNull { it.outcome == ExperienceOutcome.SUCCESS && it.evaluation.success }?.toolIds?.single()
                        }
                        Arm.PROCEDURAL -> {
                            val candidates = report.candidates.filter { it.scope == task.scope &&
                                it.candidate.taskType == task.taskType && it.candidate.eligibleForOwnerReview }
                            chars = candidates.sumOf { it.candidate.taskType.length + it.candidate.toolNames.sumOf(String::length) }
                            candidates.singleOrNull()?.candidate?.toolNames?.singleOrNull()
                        }
                    }
                    val correct = answer == task.expected
                    if (correct && task.expected != null) positive++
                    if (correct && task.expected == null) negative++
                    contextCharacters += chars
                    results += listOf(fixture, if (surprise) "surprise-on" else "surprise-off", arm, task.id,
                        answer ?: "ABSTAIN", task.expected ?: "ABSTAIN", correct, chars).joinToString("\t")
                }
                summaries += listOf(fixture, if (surprise) "surprise-on" else "surprise-off", arm,
                    positive, 8, negative, 4, contextCharacters, priorities.size).joinToString("\t")
                if (arm == Arm.EPISODIC || arm == Arm.PROCEDURAL) check(positive == 8 && negative == 4)
            }
        }
        }
        val full = results.joinToString("\n", postfix = "\n")
        out.resolve("four-arm-cases.tsv").writeText(full)
        out.resolve("four-arm-summary.tsv").writeText(summaries.joinToString("\n", postfix = "\n"))
        out.resolve("protocol.txt").writeText("""
            Evidence: deterministic synthetic selection; no Gemma, inference, latency or device claim.
            Training: clean 56 synthetic episodes; poisoned adds 4 false labels rejected by the fixture oracle.
            Holdout: 12 authored tasks; 8 positive, 4 abstention controls, disjoint IDs and request strings.
            All arms use the same frozen history and held-out tasks; no holdout feeds consolidation.
            Raw uses existing MemoryRetriever; episodic uses existing InMemoryExperienceStore.
            Procedural returns existing candidate tool-name metadata only; never executes a tool or approved skill.
            Arms intentionally differ in validation and representation; results do not isolate representation alone.
            Surprise ablation changes bounded recall, not the compiler input or candidate evidence denominator.
            Candidate routing uses known task-type metadata, so no language-understanding improvement is established.
            Context characters count exposed selection text, excluding provenance retained alongside every candidate.
            No paid service, persistence mutation, training, wall-clock speed claim or hidden model call.
            Result SHA-256: ${MessageDigest.getInstance("SHA-256").digest(full.toByteArray()).joinToString("") { "%02x".format(it) }}
        """.trimIndent() + "\n")
        println(summaries.joinToString("\n"))
    }
}
