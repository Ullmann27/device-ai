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
    val trace:String
) {
    val grounded:Boolean get()=dialogue.grounded
    val answer:String get()=dialogue.answer
}

/** JVM-only: safe to run on Android's existing learning I/O executor.
 * It does not fetch the network or replace the production retrieval policy.
 */
class ContinuumCognitiveRuntime(
    private val appDirectory:File,
    private val core:ContinuumLearningCore=ContinuumLearningCore(appDirectory)
) {
    private val cre1Checkpoint=File(appDirectory,
        "learning-core-v2/cre1-research/cre1-research-v1.cre1")

    private val cre1Manifest=File(appDirectory,
        "learning-core-v2/cre1-research/cre1-promotion.properties")

    fun reply(input:String):ContinuumCognitiveTurn {
        require(input.trim().length in 1..512)
        val previous=runCatching { core.dialogueHistory() }.getOrDefault(emptyList())
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
        val (turn,ranked)=core.replyWithEvidence(input) { resolved,candidates ->
            // The callback runs before the dialogue file is written. It may
            // ONLY permute currently authorized SWB1 hits; the memory engine
            // independently enforces set equality and byte-identical content.
            val observer=snapshot?.first ?: if(state!=ContinuumCre1State.INVALID_SNAPSHOT)
                Cre1InferenceEncoder.createResearch(73) else null
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
        val contextPrompt=localPrompt(input,previous,turn.resolvedQuery)
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
            scores,contextPrompt,trace)
    }

    /** The local generator receives conversation context but not an invented
     * assertion that a pronoun has been resolved. A factual reply still needs
     * independent evidence or must disclose that none is available.
     */
    internal fun localPrompt(
        question:String,prior:List<ContinuumDialogueTurn>,resolved:String
    ):String {
        val clean=question.trim().take(512)
        if(!REFERENTIAL.containsMatchIn(clean))return resolved.take(512)
        val earlier=prior.lastOrNull { it.topic.isNotBlank() }?.resolvedQuery
            ?.take(320) ?: return clean
        return ("Vorherige Gesprächsfrage: "+earlier+
            "\nAktuelle Anschlussfrage: "+clean+
            "\nBeziehe Pronomen nur bei eindeutigem Kontext. "+
            "Erfinde keine persönlichen Fakten.").take(1024)
    }

    companion object {
        private val REFERENTIAL=Regex(
            "(?iu)\\b(ihm|ihn|er|sie|ihr|ihre|seine|dessen|deren|darüber|dazu)\\b")
        private fun sha256(bytes:ByteArray):String=
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
