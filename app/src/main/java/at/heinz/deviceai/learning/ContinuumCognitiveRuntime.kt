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
    TRAINED_SHADOW, UNTRAINED_SHADOW, INVALID_SNAPSHOT
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

    fun reply(input:String):ContinuumCognitiveTurn {
        require(input.trim().length in 1..512)
        // The history is context only, never proof or training evidence.
        // Corrupt old history is recovered by the SWB1 dialogue itself.
        val previous=runCatching { core.dialogueHistory() }.getOrDefault(emptyList())
        // One lock-protected SWB1 model and experience snapshot supplies both
        // the visible answer and the CRE1-only shadow observation set.
        val (turn,ranked)=core.replyWithEvidence(input)
        val snapshot=if(cre1Checkpoint.exists()) {
            runCatching {
                require(cre1Checkpoint.length()==Cre1InferenceEncoder.SNAPSHOT_LENGTH.toLong()) {
                    "CRE1 snapshot length"
                }
                val bytes=cre1Checkpoint.readBytes()
                Cre1InferenceEncoder.restore(bytes) to sha256(bytes)
            }.getOrNull()
        } else null
        val state=when {
            snapshot!=null && snapshot.first.steps>0 ->
                ContinuumCre1State.TRAINED_SHADOW
            snapshot!=null -> ContinuumCre1State.UNTRAINED_SHADOW
            cre1Checkpoint.exists() -> ContinuumCre1State.INVALID_SNAPSHOT
            else -> ContinuumCre1State.UNTRAINED_SHADOW
        }
        // Run both existing encoders on the SAME grounded candidates; no CRE1
        // ranking ever creates new candidates or promotes a rejected response.
        val observer=if(ranked.isEmpty())null else when(state) {
            ContinuumCre1State.TRAINED_SHADOW -> snapshot!!.first
            ContinuumCre1State.UNTRAINED_SHADOW ->
                snapshot?.first ?: Cre1InferenceEncoder.createResearch(73)
            ContinuumCre1State.INVALID_SNAPSHOT -> null
        }
        val scores=ranked.mapNotNull { hit ->
            if(observer==null)return@mapNotNull null
            val score=runCatching {
                observer.score(turn.resolvedQuery,hit.text.take(512))
            }.getOrNull() ?: return@mapNotNull null
            if(!score.isFinite())return@mapNotNull null
            ContinuumShadowScore(hit.id,score,hit.score)
        }
        val contextPrompt=localPrompt(input,previous,turn.resolvedQuery)
        val trace=buildString {
            append("SWB1=").append(turn.modelHash.take(12))
            append(" · Fakten=").append(turn.memoryIds.size)
            append(" · CRE1=").append(state.name)
            if(snapshot!=null)append("@").append(snapshot.second.take(12))
            append(" · verglichen=").append(scores.size)
            append(" · Modus=CRE1_SHADOW")
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
