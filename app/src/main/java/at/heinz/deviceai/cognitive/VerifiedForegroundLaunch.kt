package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolBackendFailure
import at.heinz.deviceai.ToolExecutionStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Existing ToolGate grants authority. This adapter grants none: dispatch and observed outcome
 * are separate. No nodes or screen text are retained, no automatic replay of a side effect. */
internal data class ForegroundWindow(val packageName:String,val revision:Long,val grantEpoch:Long)
internal interface ForegroundEvents {
    fun grantEpoch():Long?
    fun revision():Long
    fun awaitAfter(revision:Long,timeoutMillis:Long):ForegroundWindow?
    fun wake()
}
internal class VerifiedForegroundLaunch(
    private val events:ForegroundEvents,
    private val dispatch:(String)->Boolean,
    private val clockNanos:()->Long=System::nanoTime,
    private val timeoutMillis:Long=5_000
) {
    private val active=AtomicBoolean()
    private val cancellation=AtomicLong()
    init {require(timeoutMillis in 1..5_000)}
    fun cancel(){cancellation.incrementAndGet();events.wake()}
    fun launch(packageName:String,checkRequest:()->Unit = {}):ForegroundWindow {
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")))
        if(!active.compareAndSet(false,true))fail(ToolExecutionStatus.FAILED,"ANOTHER_DEVICE_ACTION_ACTIVE")
        try {
            val ticket=cancellation.get()
            val epoch=events.grantEpoch()?:fail(ToolExecutionStatus.FAILED,"ACCESSIBILITY_OBSERVATION_NOT_GRANTED")
            val before=events.revision();val started=clockNanos()
            fun checkActive(){
                checkRequest()
                if(Thread.currentThread().isInterrupted||ticket!=cancellation.get())
                    fail(ToolExecutionStatus.CANCELLED,"DEVICE_ACTION_CANCELLED")
                if(events.grantEpoch()!=epoch)fail(ToolExecutionStatus.CANCELLED,"OBSERVATION_PERMISSION_CHANGED")
            }
            checkActive()
            if(!dispatch(packageName))fail(ToolExecutionStatus.FAILED,"NATIVE_LAUNCH_NOT_DISPATCHED")
            var revision=before
            repeat(32){
                checkActive()
                val left=timeoutMillis-(clockNanos()-started).coerceAtLeast(0)/1_000_000
                if(left<=0)fail(ToolExecutionStatus.TIMED_OUT,"LAUNCH_POSTCONDITION_NOT_OBSERVED")
                val observed=try{events.awaitAfter(revision,left)}catch(_:InterruptedException){
                    Thread.currentThread().interrupt();fail(ToolExecutionStatus.CANCELLED,"DEVICE_ACTION_INTERRUPTED")}
                checkActive()
                if(observed==null)fail(ToolExecutionStatus.TIMED_OUT,"LAUNCH_POSTCONDITION_NOT_OBSERVED")
                if(observed.grantEpoch==epoch&&observed.revision>before&&observed.revision>revision){
                    revision=observed.revision
                    if(observed.packageName==packageName){checkActive();return observed}
                }
            }
            fail(ToolExecutionStatus.FAILED,"UNSTABLE_FOREGROUND_NO_VERIFIED_RESULT")
        }finally{active.set(false)}
    }
    private fun fail(status:ToolExecutionStatus,reason:String):Nothing=throw ToolBackendFailure(status,reason)
}
