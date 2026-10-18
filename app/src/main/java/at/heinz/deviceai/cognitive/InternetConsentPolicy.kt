package at.heinz.deviceai.cognitive

import java.util.UUID

enum class InternetAccessMode { OFF, ASK, ALWAYS_ON }
class InternetApprovalTicket internal constructor(internal val nonce:String,internal val epoch:Long,
    val question:String,internal val expiresAt:Long)

/** Pending dialogs are not grants. Tickets are exact-request, expiring and single use.
 * Neither tickets nor approvals are serialized; mode changes revoke queued sessions.
 */
class InternetConsentPolicy(initialMode:InternetAccessMode=InternetAccessMode.ASK,
    private val nowMillis:()->Long={System.nanoTime()/1_000_000}) {
    @Volatile var mode=initialMode
        private set
    private var epoch=0L
    private var pending:InternetApprovalTicket?=null
    @Synchronized fun setMode(value:InternetAccessMode) { mode=value;cancel() }
    @Synchronized fun requestTicket(question:String):InternetApprovalTicket? {
        cancel()
        if(mode!=InternetAccessMode.ASK||question.isBlank()||question.length>2048)return null
        return InternetApprovalTicket(UUID.randomUUID().toString(),epoch,question,nowMillis()+60000).also { pending=it }
    }
    @Synchronized fun consume(ticket:InternetApprovalTicket):Boolean {
        val matches=pending?.nonce==ticket.nonce && pending?.question==ticket.question &&
            ticket.epoch==epoch && mode==InternetAccessMode.ASK && nowMillis()<=ticket.expiresAt
        pending=null
        if(!matches)epoch++
        return matches
    }
    /**
     * One per-request, bounded capability under the owner's persisted consent.
     * No grant survives a policy change, cancellation or subsequent new grant.
     */
    @Synchronized internal fun automaticTicket(question:String):InternetApprovalTicket? {
        if(mode!=InternetAccessMode.ALWAYS_ON||question.isBlank()||question.length>2048)return null
        cancel()
        return InternetApprovalTicket(UUID.randomUUID().toString(),epoch,question,nowMillis()+60000)
    }
    @Synchronized internal fun isCurrent(ticket:InternetApprovalTicket)=
        mode in setOf(InternetAccessMode.ASK,InternetAccessMode.ALWAYS_ON)&&epoch==ticket.epoch
    internal fun monotonicNow()=nowMillis()
    @Synchronized fun cancel() { pending=null;epoch++ }
    // Legacy question-only methods remain for callers without a network execution path.
    @Synchronized fun request(question:String)=requestTicket(question)!=null
    @Synchronized fun approve(question:String):Boolean {
        val ticket=pending
        if(ticket==null||ticket.question!=question){cancel();return false}
        return consume(ticket)
    }
}
class InternetResearchGate(private val consent:InternetConsentPolicy) {
    fun authorize(question:String)=consent.approve(question)
    fun revoke()=consent.cancel()
}
