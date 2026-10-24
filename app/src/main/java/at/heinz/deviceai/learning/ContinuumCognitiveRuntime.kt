package at.heinz.deviceai.learning

import java.io.File
import java.security.MessageDigest

/** One transparent, side-effect-bounded conversation turn through the existing
 * SWB1 dialogue path and a research-only CRE1 semantic observer. The observer
 * never grants memory provenance, creates facts, invokes tools or routes LLM
 * output back into the learning ledger.
 */
data class ContinuumShadowScore(
    val memoryId:String,
    val semanticScore:Double,
    val lexicalScore:Double
)
enum class ContinuumCre1State {
    TRAINED_SHADOW, TRAINED_ACTIVE_RERANK, UNTRAINED_SHADOW, INVALID_SNAPSHOT
}
data class ContinuumCognitiveTurn(
    val dialogue:ContinuumDialogueTurn,
    val cre1State:ContinuumCre1State,
    val cre1ModelHash:String?,
    val observations:List<ContinuumShadowScore>,
    val localModelPrompt:String,
    val trace:String,
    /** Authorized raw evidence selected in this exact dialogue turn. */
    val evidenceHits:List<ContinuumMemoryHit> = emptyList()
) {
    val grounded:Boolean get()=dialogue.grounded
    val answer:String get()=dialogue.answer
}

/** JVM-only: safe to run on Android's existing learning I/O executor.
 * It does not fetch the network or replace the production retrieval policy.
 */
class ContinuumCognitiveRuntime(
    private val appDirectory:File,
    private val core:ContinuumLearningCore=ContinuumLearningCore(appDirectory),
    private val conversationId:String=java.util.UUID.randomUUID().toString()
) {
    private val cre1Checkpoint=File(appDirectory,
        "learning-core-v2/cre1-research/cre1-research-v1.cre1")

    private val cre1Manifest=File(appDirectory,
        "learning-core-v2/cre1-research/cre1-promotion.properties")

    fun reply(input:String):ContinuumCognitiveTurn {
        require(input.trim().length in 1..512)
        // A damaged checkpoint cannot be used, even with a plausible manifest.
        val snapshot=if(cre1Checkpoint.exists()) {
            runCatching {
                require(cre1Checkpoint.length()==Cre1InferenceEncoder.SNAPSHOT_LENGTH.toLong()) {
                    "CRE1 snapshot length"
                }
                val bytes=cre1Checkpoint.readBytes()
                Cre1InferenceEncoder.restore(bytes) to sha256(bytes)
            }.getOrNull()
        } else null
        val approved=if(snapshot!=null && snapshot.first.steps>0) {
            Cre1PromotionGate.readQualified(cre1Manifest,snapshot.second,snapshot.first.steps)
        } else null
        val state=when {
            snapshot!=null && snapshot.first.steps>0 && approved!=null ->
                ContinuumCre1State.TRAINED_ACTIVE_RERANK
            snapshot!=null && snapshot.first.steps>0 -> ContinuumCre1State.TRAINED_SHADOW
            snapshot!=null -> ContinuumCre1State.UNTRAINED_SHADOW
            cre1Checkpoint.exists() -> ContinuumCre1State.INVALID_SNAPSHOT
            else -> ContinuumCre1State.UNTRAINED_SHADOW
        }
        var shadow=emptyList<ContinuumShadowScore>()
        var didRerank=false
        val (turn,ranked)=core.replyWithEvidenceInConversation(input,conversationId) { resolved,candidates ->
            // The callback runs before the dialogue file is written. It may
            // ONLY permute currently authorized SWB1 hits; the memory engine
            // independently enforces set equality and byte-identical content.
            val observer=snapshot?.first ?: if(state!=ContinuumCre1State.INVALID_SNAPSHOT)
                UNTRAINED_SHADOW_ENCODER else null
            shadow=candidates.mapNotNull { hit ->
                if(observer==null)return@mapNotNull null
                val semantic=runCatching { observer.score(resolved,hit.text.take(512)) }
                    .getOrNull() ?: return@mapNotNull null
                if(!semantic.isFinite())return@mapNotNull null
                ContinuumShadowScore(hit.id,semantic,hit.score)
            }
            if(approved==null || snapshot==null || shadow.size!=candidates.size)
                candidates
            else {
                val order=Cre1PromotionGate.rerank(resolved,candidates,
                    shadow.associate { it.memoryId to it.semanticScore },
                    approved,snapshot.second,snapshot.first.steps)
                didRerank=order.map { it.id }!=candidates.map { it.id }
                order
            }
        }
        val byId=shadow.associateBy { it.memoryId }
        val scores=ranked.mapNotNull { byId[it.id] }
        val contextPrompt=localPrompt(input,turn.resolvedQuery)
        val trace=buildString {
            append("SWB1=").append(turn.modelHash.take(12))
            append(" · Fakten=").append(turn.memoryIds.size)
            append(" · CRE1=").append(state.name)
            if(snapshot!=null)append("@").append(snapshot.second.take(12))
            append(" · verglichen=").append(scores.size)
            append(" · Modus=").append(
                if(didRerank)"CRE1_ACTIVE_RERANK"
                else if(approved!=null)"CRE1_GATED_SWB1_ORDER"
                else "CRE1_SHADOW"
            )
            if(turn.contextReset)append(" · Kontext wiederhergestellt")
        }
        return ContinuumCognitiveTurn(turn,state,snapshot?.second,
            scores,contextPrompt,trace,ranked)
    }

    /**
     * A profile-wide dialogue history has no conversation ID. Passing its last
     * subject to an LLM would leak a previous session even after a chat reset.
     * Only this turn's resolved query is allowed to supply continuation context.
     * If it has not resolved the pronoun, ask for clarification instead.
     * This neither promotes an unconfirmed topic to an owner fact nor trains a model.
     */
    internal fun localPrompt(question:String,resolved:String):String {
        val clean=question.trim().take(512)
        val candidate=resolved.trim().take(512)
        if (candidate==clean) {
            if (!REFERENTIAL.containsMatchIn(clean)) return clean
            return ("Aktuelle Frage: "+clean+
                "\nDer Bezug des Pronomens ist nicht eindeutig aufgelöst. "+
                "Bitte frage nach, statt frühere Gesprächsinhalte oder Besitzerfakten zu erfinden.").take(1024)
        }
        return ("Aktuelle Frage: "+clean+
            "\nVom bestehenden Gedächtnisdialog vorgeschlagene Anschlussfrage: "+candidate+
            "\nDer Bezug ist nicht unabhängig bestätigt. "+
            "Nutze nur ausdrücklich bestätigte Belege; frage bei Mehrdeutigkeit nach.").take(1024)
    }

    companion object {
        // Android creates a new cognitive runtime for each message. Do not
        // repeatedly allocate and initialize CRE1's ~4.2-MB untrained weights
        // inside the SWB1 conversation lock. This read-only instance remains
        // shadow-only, never trained or shared for mutation.
        private val UNTRAINED_SHADOW_ENCODER:Cre1InferenceEncoder by lazy(
            LazyThreadSafetyMode.SYNCHRONIZED
        ) { Cre1InferenceEncoder.createResearch(73) }

        private val REFERENTIAL=Regex(
            "(?iu)\\b(ihm|ihn|er|sie|ihr|ihre|seine|dessen|deren|darüber|dazu)\\b")
        private fun sha256(bytes:ByteArray):String=
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
