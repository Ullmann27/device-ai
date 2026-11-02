package at.heinz.deviceai.cognitive

import java.util.UUID

enum class InternetAccessMode { OFF, ASK, ALWAYS_ON }
class InternetApprovalTicket internal constructor(internal val nonce:String,internal val epoch:Long,
    val question:String,internal val expiresAt:Long,internal val accessMode:InternetAccessMode)

/** Pending dialogs are not grants. ASK tickets are exact-request, expiring and single use.
 * Replacing a pending request preserves already approved evidence. Mode changes and
 * explicit cancellation revoke every session. Tickets and approvals are never serialized.
 */
class InternetConsentPolicy(initialMode:InternetAccessMode=InternetAccessMode.ASK,
    private val nowMillis:()->Long={System.nanoTime()/1_000_000}) {
    @Volatile var mode=initialMode
        private set
    private var epoch=0L
    private var pending:InternetApprovalTicket?=null
    @Synchronized fun setMode(value:InternetAccessMode) { mode=value;cancel() }
    @Synchronized fun requestTicket(question:String):InternetApprovalTicket? {
        clearPending()
        if(mode!=InternetAccessMode.ASK||question.isBlank()||question.length>2048)return null
        return InternetApprovalTicket(UUID.randomUUID().toString(),epoch,question,nowMillis()+60000,mode).also { pending=it }
    }
    /** Persistent owner consent issues an exact-request capability, never an ASK approval. */
    @Synchronized internal fun grantAlwaysOn(question:String):InternetApprovalTicket? {
        clearPending()
        if(mode!=InternetAccessMode.ALWAYS_ON||question.isBlank()||question.length>2048)return null
        return InternetApprovalTicket(UUID.randomUUID().toString(),epoch,question,nowMillis()+60000,mode)
    }
    @Synchronized fun consume(ticket:InternetApprovalTicket):Boolean {
        val matches=pending?.nonce==ticket.nonce && pending?.question==ticket.question &&
            ticket.epoch==epoch && mode==InternetAccessMode.ASK && nowMillis()<=ticket.expiresAt
        pending=null
        return matches
    }
    @Synchronized internal fun isCurrent(ticket:InternetApprovalTicket)=
        mode!=InternetAccessMode.OFF&&mode==ticket.accessMode&&epoch==ticket.epoch
    internal fun monotonicNow()=nowMillis()
    @Synchronized internal fun clearPending() { pending=null }
    @Synchronized fun cancel() { pending=null;epoch++ }
    // Legacy question-only methods remain for callers without a network execution path.
    @Synchronized fun request(question:String)=requestTicket(question)!=null
    @Synchronized fun approve(question:String):Boolean {
        val ticket=pending
        if(ticket==null||ticket.question!=question){clearPending();return false}
        return consume(ticket)
    }
}
class InternetResearchGate(private val consent:InternetConsentPolicy) {
    fun authorize(question:String)=consent.approve(question)
    fun revoke()=consent.cancel()
}
