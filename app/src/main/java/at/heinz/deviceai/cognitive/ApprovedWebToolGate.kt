package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolProposal
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

sealed class WebToolRequest {
    data class Search(val query:String):WebToolRequest()
    data class Read(val url:String):WebToolRequest()
    val question:String get()=when(this){is Search->query;is Read->url}
    val operation:String get()=when(this){is Search->"web_search";is Read->"web_read"}
    fun proposal()=ToolProposal(operation,when(this){is Search->mapOf("query" to query);is Read->mapOf("url" to url)})
    companion object {
        fun fromTool(name:String,args:Map<String,Any?>):WebToolRequest {
            val key=when(name){"web_search"->"query";"web_read"->"url";else->error("Not a web capability")}
            require(args.keys==setOf(key) && args[key] is String)
            return if(name=="web_search")Search(args[key] as String) else Read(args[key] as String)
        }
        fun fromChat(text:String,armed:Boolean):WebToolRequest? {
            val value=text.trim()
            val search=Regex("(?is)^(?:/?web_search|/search|suche im internet|recherchiere)(?:\\s*:\\s*|\\s+)(.+)$").matchEntire(value)
            if(search!=null)return Search(search.groupValues[1].trim())
            val read=Regex("(?is)^(?:/?web_read|/read|lies die seite)(?:\\s*:\\s*|\\s+)(.+)$").matchEntire(value)
            if(read!=null)return Read(read.groupValues[1].trim())
            if(value.startsWith("https://",true)&&!value.contains(Regex("\\s")))return Read(value)
            return if(armed)Search(value) else null
        }
    }
}
class WebToolApproval internal constructor(val call:WebToolRequest,internal val ticket:InternetApprovalTicket)

/** ASK grants require a positive owner action; ALWAYS_ON grants require the owner's
 * persistent setting. Both still pass the common ToolGate's confirmation and replay checks.
 */
class ApprovedWebToolGate(private val consent:InternetConsentPolicy) {
    private var pending:WebToolApproval?=null
    @Synchronized fun prepare(call:WebToolRequest):WebToolApproval? {
        pending=null;consent.clearPending()
        if(consent.mode!=InternetAccessMode.ASK)return null
        if(!validCall(call))return null
        val ticket=consent.requestTicket(call.question)?:return null
        return WebToolApproval(call,ticket).also { pending=it }
    }
    @Synchronized fun authorizeAlwaysOn(call:WebToolRequest,mode:ResearchMode):ApprovedWebResearchSession? {
        pending=null;consent.clearPending()
        if(!validCall(call))return null
        val ticket=consent.grantAlwaysOn(call.question)?:return null
        return ApprovedWebResearchSession(call,mode,consent,ticket)
    }
    private fun validCall(call:WebToolRequest):Boolean=when(call) {
        is WebToolRequest.Search->call.query.isNotBlank()&&call.query.length<=512
        is WebToolRequest.Read->call.url.length in 10..2048&&runCatching {
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validateSyntax(URI(call.url))
        }.isSuccess
    }
    @Synchronized fun approve(approval:WebToolApproval,mode:ResearchMode):ApprovedWebResearchSession? {
        val matches=pending?.ticket?.nonce==approval.ticket.nonce&&pending?.call==approval.call
        pending=null
        if(!matches){consent.clearPending();return null}
        if(!consent.consume(approval.ticket))return null
        return ApprovedWebResearchSession(approval.call,mode,consent,approval.ticket)
    }
    @Synchronized fun cancel(){pending=null;consent.cancel()}
}

class ApprovedWebResearchSession internal constructor(val call:WebToolRequest,val mode:ResearchMode,
    private val consent:InternetConsentPolicy,private val ticket:InternetApprovalTicket) {
    private val used=AtomicBoolean(false)
    private val running=AtomicBoolean(false)
    private val cancelled=AtomicBoolean(false)
    private val deadline=consent.monotonicNow()+ResearchBudgetPolicy.forMode(mode).totalDeadlineMillis
    val question:String get()=call.question
    val seedUrls:List<String> get()=if(call is WebToolRequest.Read)listOf(call.url) else emptyList()
    fun cancel(){cancelled.set(true)}
    /** Also usable after execution, before publishing evidence or accepting a learning candidate. */
    fun isAuthorized()=!cancelled.get()&&!Thread.currentThread().isInterrupted&&consent.isCurrent(ticket)
    fun checkAuthorized(){check(isAuthorized()) { "Internetfreigabe widerrufen" }}
    private fun checkValid(enforceDeadline:Boolean=true) {
        check(isAuthorized()&&(!enforceDeadline||consent.monotonicNow()<=deadline)) { "Internetfreigabe widerrufen oder Zeitbudget abgelaufen" }
    }
    fun checkActive(){check(running.get()){ "Kein aktiver genehmigter Recherchelauf" };checkValid()}
    fun <T> execute(request:WebToolRequest,action:()->T):T {
        check(request==call) { "Webanfrage stimmt nicht mit der Freigabe überein" }
        checkValid();check(used.compareAndSet(false,true)) { "Internetfreigabe bereits verwendet" }
        running.set(true)
        // A timed-out network budget may still return already collected partial evidence.
        try { return action().also { checkValid(enforceDeadline=false) } } finally { running.set(false) }
    }
    fun discovery(delegate:ResearchDiscoveryProvider):ResearchDiscoveryProvider=object:ResearchDiscoveryProvider {
        override val isGeneralSearchAvailable:Boolean get()=call is WebToolRequest.Search&&delegate.isGeneralSearchAvailable
        override fun discover(query:ResearchQuery):List<ResearchSourceCandidate> {
            checkActive()
            if(call is WebToolRequest.Read)return emptyList()
            return delegate.discover(query).also { checkActive() }
        }
    }
    fun provider(delegate:ResearchProvider):ResearchProvider=object:ResearchProvider {
        override fun retrieve(request:ResearchRequest):ResearchResult {
            checkActive()
            if(call is WebToolRequest.Read)require(ResearchUrlCanonicalizer.canonicalize(request.url)==ResearchUrlCanonicalizer.canonicalize(call.url)) { "Direkte Lesefreigabe erlaubt keine zusätzliche Quelle" }
            return delegate.retrieve(request).also { checkActive() }
        }
    }
}
