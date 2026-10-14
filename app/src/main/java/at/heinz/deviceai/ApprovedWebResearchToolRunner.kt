package at.heinz.deviceai

import at.heinz.deviceai.cognitive.*

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
    @Volatile var dossier:ResearchDossier?=null
        private set
    override fun execute(request:ToolCallRequest):String {
        val call=WebToolRequest.fromTool(request.name,request.arguments)
        return session.execute(call) {
            val engine=engineFactory(guardedDiscovery,guardedProvider)
            val result=if(resume)engine.resume(cancellation,progress)?:error("Kein fortsetzbarer Recherchelauf")
                else engine.research(session.question,session.mode,session.seedUrls,cancellation,progress)
            check(result.plan.question==session.question&&result.plan.mode==session.mode) { "Checkpoint stimmt nicht mit der Freigabe überein" }
            dossier=result
            format(result)
        }
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
