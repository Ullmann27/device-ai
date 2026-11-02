package at.heinz.deviceai.cognitive

import java.util.concurrent.atomic.AtomicBoolean

/** Experimental protocol boundary. No production port is registered. A future Android port
 * must bind frames to a live service/epoch and recheck the target immediately before dispatch. */
internal data class ClickNode(val label:String,val visible:Boolean,val actionable:Boolean,val sensitive:Boolean)
internal data class ClickFrame(
    val packageName:String,val epoch:Long,val revision:Long,val digest:String,
    val safeWindows:Boolean,val complete:Boolean,val nodes:List<ClickNode>
)
internal data class ClickRequest(val packageName:String,val epoch:Long,val revision:Long,
    val label:String,val expectedLabel:String)
internal interface ClickObservationPort {
    fun current():ClickFrame
    /** Must revalidate package, window, grant, revision and unique node before one dispatch. */
    fun clickOnce(request:ClickRequest):Boolean
    /** Must acquire a new observed frame, bounded by the caller's deadline. */
    fun awaitPostcondition():ClickFrame
    /** Throws on revocation, cancellation, interruption or the overall deadline. */
    fun checkActive()
}

internal object VerifiedNodeClick {
    private val active=AtomicBoolean(false)
    fun execute(request:ClickRequest,ownerConfirmed:Boolean,port:ClickObservationPort):ClickFrame {
        require(request.packageName.length<=200 && request.packageName.matches(
            Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")))
        fun validLabel(s:String)=s.length in 1..200 && s==s.trim() && s.none{it.isISOControl()}
        require(validLabel(request.label)&&validLabel(request.expectedLabel))
        check(ownerConfirmed){"OWNER_APPROVAL_REQUIRED"}
        check(active.compareAndSet(false,true)){"ANOTHER_DEVICE_ACTION_ACTIVE"}
        try {
            port.checkActive()
            val before=port.current()
            fun acceptable(frame:ClickFrame)=frame.packageName==request.packageName && frame.epoch==request.epoch &&
                frame.safeWindows && frame.complete && frame.nodes.size<=256 && frame.digest.isNotBlank()
            check(acceptable(before)&&before.revision==request.revision){"STALE_OR_UNSAFE_SCREEN"}
            val selected=before.nodes.filter{it.label==request.label && it.visible}
            check(selected.size==1 && selected.single().actionable && !selected.single().sensitive){"UNSAFE_OR_AMBIGUOUS_TARGET"}
            // The expected state must be newly observed, not merely present before the click.
            check(before.nodes.none{it.visible && it.label==request.expectedLabel}){"POSTCONDITION_ALREADY_PRESENT"}
            port.checkActive()
            check(port.clickOnce(request)){"CLICK_NOT_ACCEPTED"}
            port.checkActive()
            val after=port.awaitPostcondition()
            port.checkActive()
            check(acceptable(after) && after.revision>before.revision && after.digest!=before.digest){"POSTCONDITION_NOT_VERIFIED"}
            val matching=after.nodes.filter{it.visible && it.label==request.expectedLabel}
            check(matching.size==1 && !matching.single().sensitive){"POSTCONDITION_NOT_VERIFIED"}
            return after
        } finally { active.set(false) }
    }
}
