package at.heinz.deviceai

import at.heinz.deviceai.cognitive.*
import java.security.MessageDigest

/** Actual common ToolGate runner: the consumed owner grant wraps all discovery
 * and reading performed by the existing Research Engine, including follow-ups.
 */
class ApprovedWebResearchToolRunner(
    private val session:ApprovedWebResearchSession,
    discovery:ResearchDiscoveryProvider,
    provider:ResearchProvider,
    private val engineFactory:(ResearchDiscoveryProvider,ResearchProvider)->DeepResearchEngine={d,p->DeepResearchEngine(
        discovery=d,provider=p,validateTarget={HttpReadOnlyResearchProvider.ResearchUrlValidator.validateSyntax(it)})},
    private val cancellation:ResearchCancellation=ResearchCancellation(),
    private val progress:(ResearchPhase)->Unit={},
    private val resume:Boolean=false
):ToolRunner {
    private val guardedDiscovery=session.discovery(discovery)
    private val guardedProvider=session.provider(provider)
    @Volatile private var completedDossier:ResearchDossier?=null
    @Volatile private var completedRequest:ToolCallRequest?=null
    val dossier:ResearchDossier? get()=completedDossier?.takeIf { session.isAuthorized() }

    /**
     * Only a current owner grant and a durable, successfully completed ToolGate
     * receipt can expose a model-chat research dossier to the existing review
     * inbox. Saving it does not approve claims or update model weights.
     */
    fun publishForReview(receipts:ToolExecutionReceiptStore,repository:ResearchDossierRepository):Int {
        val request=completedRequest?:return 0
        val finished=dossier?:return 0
        val candidates=WebLearningInbox.candidates(finished)
        if(candidates.isEmpty())return 0
        val receipt=receipts.get(request.callId)?:return 0
        val output=format(finished)
        val digest=MessageDigest.getInstance("SHA-256")
            .digest(output.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        if(receipt.state!=ToolReceiptState.SUCCEEDED ||
            receipt.toolName!=session.call.operation || receipt.toolName!=request.name ||
            receipt.risk!=Risk.CONFIRM ||
            receipt.argumentFingerprint!=toolArgumentFingerprint(request.arguments) ||
            receipt.outputLength!=output.length || receipt.outputSha256!=digest)return 0
        session.checkAuthorized()
        repository.save(finished)
        session.checkAuthorized()
        return candidates.size
    }
    override fun execute(request:ToolCallRequest):String {
        val call=WebToolRequest.fromTool(request.name,request.arguments)
        val result=session.execute(call) {
            val engine=engineFactory(guardedDiscovery,guardedProvider)
            val result=if(resume)engine.resume(cancellation,progress)?:error("Kein fortsetzbarer Recherchelauf")
                else engine.research(session.question,session.mode,session.seedUrls,cancellation,progress)
            check(result.plan.question==session.question&&result.plan.mode==session.mode) { "Checkpoint stimmt nicht mit der Freigabe überein" }
            result
        }
        session.checkAuthorized()
        completedDossier=result
        return format(result).also { session.checkAuthorized();completedRequest=request }
    }
    companion object {
        fun format(dossier:ResearchDossier):String=buildString {
            append((dossier.synthesis?.text?:"Keine belegte Zusammenfassung verfügbar.").take(4000))
            dossier.sources.filter { it.duplicateOf==null }.take(16).forEachIndexed { i,s->
                append("\n\n[").append(i+1).append("] ").append(s.title?:s.domain)
                append("\n").append(s.canonicalUrl).append("\nSHA-256: ").append(s.contentHash.take(16))
            }
            if(dossier.sources.isEmpty())append("\n\nKeine abrufbaren Quellen; keine Antwort erfunden.")
            dossier.partialReason?.let { append("\n\nTeilrecherche: ").append(it.take(256)) }
        }.take(8000)
    }
}
