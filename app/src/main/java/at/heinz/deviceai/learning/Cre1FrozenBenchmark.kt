package at.heinz.deviceai.learning

import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Read-only experiment against the ACTUAL SWB1 candidate gate and CRE1
 * inference code. No memory files or model weights are changed.
 *
 * The benchmark provider must independently seal gold labels and holdout
 * examples before encoder training and alpha selection; this class cannot
 * authenticate that external experimental procedure.
 */
data class Cre1FrozenQuestion(
    val caseId:String,
    val query:String,
    val expectedMemoryId:String?,
    val memoryPool:List<LearningExperience>
) {
    init {
        require(caseId.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,79}")))
        require(query.isNotBlank() && query.length<=512)
        require(memoryPool.size in 1..256)
        require(memoryPool.map { it.id }.distinct().size==memoryPool.size)
        require(expectedMemoryId==null ||
            expectedMemoryId.matches(Regex("lx-[a-f0-9]{24}")))
    }
}

/** This does NOT write the approval file or enable CRE1 in chat. */
object Cre1FrozenBenchmark {
    private fun caseFingerprint(swbSha:String,item:Cre1FrozenQuestion):String {
        val md=MessageDigest.getInstance("SHA-256")
        fun put(value:String) {
            val bytes=value.toByteArray(StandardCharsets.UTF_8)
            md.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            md.update(bytes)
        }
        put("CRE1_FROZEN_CASE_V1")
        put(swbSha)
        put(item.caseId)
        put(item.query)
        put(item.expectedMemoryId?:"")
        for(e in item.memoryPool.sortedBy { it.id }) {
            put(e.id);put(e.contentHash);put(e.status.name)
            put(e.type.name);put(e.provenance.name);put(e.trust.name)
            put(e.sourceRef?:"")
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun run(
        swb1Snapshot:ByteArray,
        cre1Snapshot:ByteArray,
        sealedCases:List<Cre1FrozenQuestion>,
        frozenFusionWeight:Double,
        cancelled:()->Boolean={false}
    ):Cre1AuditResult {
        require(sealedCases.size in 1..1000)
        require(sealedCases.map { it.caseId }.distinct().size==sealedCases.size)
        require(frozenFusionWeight.isFinite() && frozenFusionWeight in 0.05..0.5)
        // Validate both models once before touching the benchmark corpus.
        TrainableSubwordEncoder.restore(swb1Snapshot)
        val cre1=Cre1InferenceEncoder.restore(cre1Snapshot)
        val swbSha=MessageDigest.getInstance("SHA-256").digest(swb1Snapshot)
            .joinToString("") { "%02x".format(it) }
        val sha=MessageDigest.getInstance("SHA-256").digest(cre1Snapshot)
            .joinToString("") { "%02x".format(it) }
        val outcomes=ArrayList<Cre1AuditOutcome>(sealedCases.size)
        for(item in sealedCases) {
            check(!cancelled() && !Thread.currentThread().isInterrupted) {
                "Frozen evaluation interrupted; no partial audit emitted"
            }
            val ranked=ContinuumMemoryDialogue.rank(swb1Snapshot,item.query,item.memoryPool)
            val scores=ranked.associate { hit ->
                hit.id to cre1.score(item.query,hit.text.take(512))
            }
            val proposed=Cre1PromotionGate.proposeOrder(item.query,ranked,
                scores,frozenFusionWeight)
            require(ranked.toSet()==proposed.toSet() &&
                ranked.map { it.id }.distinct().size==ranked.size) {
                "Benchmark violated production SWB1 candidate identity"
            }
            outcomes+=Cre1AuditOutcome(
                item.caseId,item.expectedMemoryId,ranked.map { it.id }.toSet(),
                ranked.firstOrNull()?.id,proposed.firstOrNull()?.id,
                caseFingerprint(swbSha,item)
            )
        }
        check(!cancelled() && !Thread.currentThread().isInterrupted) {
            "Frozen evaluation cancelled after final example"
        }
        return Cre1PromotionAudit.evaluate(sha,cre1.steps,outcomes,frozenFusionWeight)
    }
}
