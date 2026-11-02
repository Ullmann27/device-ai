/* Window observation and bounded tree traversal adapted from TokenArc Open Jarvis
 * @04873998933830ce582018fe4664ebce5ec64d2f, MIT Copyright(c)2024TokenArc.
 * Changes: observe window events only; no automatic text capture, tapping, overlay,
 * polling, networking or persistent data. Explicit Android owner enablement required. */
package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import at.heinz.deviceai.cognitive.*

class ContinuumScreenObservationService:AccessibilityService(){
    override fun onServiceConnected(){super.onServiceConnected();ScreenObservationBus.connect(this)}
    override fun onAccessibilityEvent(event:AccessibilityEvent?){
        if(event==null)return
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED){
            ScreenObservationBus.contentChanged(this)
            return // Invalidate an in-flight read; do not capture text or invent a launch observation.
        }
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||
            event.eventType==AccessibilityEvent.TYPE_WINDOWS_CHANGED){
            val root=rootInActiveWindow?:return
            try{ScreenObservationBus.observed(this,root.packageName?.toString().orEmpty())}
            finally{@Suppress("DEPRECATION") root.recycle()}
        }
    }
    override fun onInterrupt(){ScreenObservationBus.interrupt(this)}
    override fun onDestroy(){ScreenObservationBus.disconnect(this);super.onDestroy()}
    internal fun readScreen(expectedPackage:String,checkActive:()->Unit):ScreenTextSnapshot{
        checkActive()
        val root=rootInActiveWindow?:error("NO_ACTIVE_WINDOW")
        try{checkActive()}catch(error:RuntimeException){@Suppress("DEPRECATION") root.recycle();throw error}
        if(root.packageName?.toString()!=expectedPackage){@Suppress("DEPRECATION") root.recycle();error("UNEXPECTED_SCREEN_PACKAGE")}
        return BoundedScreenReader.read(AndroidScreenNode(root),checkActive=checkActive)
    }
    private class AndroidScreenNode(private val node:AccessibilityNodeInfo):ScreenTreeNode{
        override val text get()=node.text?.take(4_001)?.toString()
        override val description get()=node.contentDescription?.take(4_001)?.toString()
        override val visible get()=node.isVisibleToUser
        override val sensitive get()=node.isPassword||node.isEditable
        override val children get()=node.childCount
        override fun child(index:Int)=node.getChild(index)?.let(::AndroidScreenNode)
        @Suppress("DEPRECATION") override fun release(){node.recycle()}
    }
}

internal data class ObservedAppScreen(val snapshot:ScreenTextSnapshot,val epoch:Long,val revision:Long,val observedAtMillis:Long)

/** In-process, event-driven metadata only. No screen text cached on events. */
internal object ScreenObservationBus:ForegroundEvents{
    private val lock=java.lang.Object();private var service:ContinuumScreenObservationService?=null
    private var epoch=0L;private var sequence=0L;private var wakeSequence=0L;private var latest:ForegroundWindow?=null
    fun connect(s:ContinuumScreenObservationService)=synchronized(lock){service=s;epoch++;latest=null;lock.notifyAll()}
    fun disconnect(s:ContinuumScreenObservationService)=synchronized(lock){if(service===s){service=null;epoch++;latest=null;lock.notifyAll()}}
    fun interrupt(s:ContinuumScreenObservationService)=synchronized(lock){if(service===s){epoch++;latest=null;wakeSequence++;lock.notifyAll()}}
    fun observed(s:ContinuumScreenObservationService,pkg:String)=synchronized(lock){if(service===s&&pkg.isNotBlank()){
        latest=ForegroundWindow(pkg,++sequence,epoch);lock.notifyAll()}}
    fun contentChanged(s:ContinuumScreenObservationService)=synchronized(lock){if(service===s){
        sequence++;lock.notifyAll()}}
    override fun grantEpoch():Long?=synchronized(lock){if(service!=null)epoch else null}
    override fun revision():Long=synchronized(lock){sequence}
    override fun wake()=synchronized(lock){wakeSequence++;lock.notifyAll()}
    override fun awaitAfter(revision:Long,timeoutMillis:Long):ForegroundWindow?=synchronized(lock){
        val start=System.nanoTime();val wake=wakeSequence;val grant=epoch
        while(service!=null&&grant==epoch&&wake==wakeSequence&&(latest?.revision?:0)<=revision){
            val remaining=timeoutMillis-(System.nanoTime()-start).coerceAtLeast(0)/1_000_000
            if(remaining<=0)break
            lock.wait(remaining.coerceAtLeast(1))
        }
        latest?.takeIf{it.revision>revision}
    }
    fun read(expectedPackage:String,checkActive:()->Unit):ObservedAppScreen{
        val state=synchronized(lock){
            val current=service?:error("ACCESSIBILITY_OBSERVATION_NOT_GRANTED")
            Triple(current,epoch,++sequence)
        }
        val observedAt=System.currentTimeMillis()
        val result=state.first.readScreen(expectedPackage,checkActive)
        synchronized(lock){check(service===state.first&&epoch==state.second&&sequence==state.third){"SCREEN_CHANGED_DURING_READ"}}
        return ObservedAppScreen(result,state.second,state.third,observedAt)

    }
}
