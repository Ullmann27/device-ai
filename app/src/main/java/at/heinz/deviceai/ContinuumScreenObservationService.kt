/* Window observation and bounded tree traversal adapted from TokenArc Open Jarvis
 * @04873998933830ce582018fe4664ebce5ec64d2f, MIT Copyright(c)2024TokenArc.
 * Changes: observe window events only; no automatic text capture, tapping, overlay,
 * polling, networking or persistent data. Explicit Android owner enablement required. */
package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import at.heinz.deviceai.cognitive.*

class ContinuumScreenObservationService:AccessibilityService(){
    override fun onServiceConnected(){super.onServiceConnected();ScreenObservationBus.connect(this)}
    override fun onAccessibilityEvent(event:AccessibilityEvent?){
        if(event==null)return
        if(event.eventType==AccessibilityEvent.TYPE_VIEW_SCROLLED){
            ScreenObservationBus.scrolled(this,event)
            return
        }
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED||
            event.eventType==AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED){
            ScreenObservationBus.contentChanged(this,event.packageName?.toString(),event.windowId)
            return // Invalidate an in-flight read; do not capture text or invent a launch observation.
        }
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||
            event.eventType==AccessibilityEvent.TYPE_WINDOWS_CHANGED){
            if(event.eventType==AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
                ScreenObservationBus.ignoreVerifiedUnrelatedWindow(this,event))return
            val root=rootInActiveWindow
            if(root==null){ScreenObservationBus.observed(this, "",event.packageName?.toString());return}
            try{ScreenObservationBus.observed(this,root.packageName?.toString().orEmpty(),event.packageName?.toString())}
            finally{@Suppress("DEPRECATION") root.recycle()}
        }
    }
    override fun onInterrupt(){ScreenObservationBus.interrupt(this);AndroidLiveComputer.observationRevoked(this)}
    override fun onDestroy(){ScreenObservationBus.disconnect(this);AndroidLiveComputer.observationRevoked(this);super.onDestroy()}
    internal fun readScreen(expectedPackage:String,checkActive:()->Unit,bindWindow:(Int)->Unit):ScreenTextSnapshot{
        checkActive()
        val root=rootInActiveWindow?:error("NO_ACTIVE_WINDOW")
        try{
            checkActive()
            check(root.packageName?.toString()==expectedPackage){"UNEXPECTED_SCREEN_PACKAGE"}
            bindWindow(root.windowId)
        }catch(error:Throwable){@Suppress("DEPRECATION") root.recycle();throw error}
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
internal data class LiveObservationAccess(val service:ContinuumScreenObservationService,val epoch:Long)

/** In-process, event-driven metadata only. No screen text cached on events. */
internal object ScreenObservationBus:ForegroundEvents{
    private val lock=java.lang.Object();private var service:ContinuumScreenObservationService?=null
    private var epoch=0L;private var sequence=0L;private var wakeSequence=0L;private var latest:ForegroundWindow?=null
    private var windowSequence=0L
    private var readingPackage:String?=null
    private var operationWindowId:Int?=null
    private var clickInProgress=false
    private var clickTargetEvent=false
    private var clickUncertain=false
    private data class ScrollPosition(val x:Int?,val y:Int?,val fromIndex:Int?,val atMillis:Long)
    private class PendingScroll(val request:NodeActionRequest,val before:ObservationStamp,
                              val node:AccessibilityNodeInfo,val dispatchedAtMillis:Long){
        var position:ScrollPosition?=null
        var evidence:LiveScrollEvidence?=null
    }
    private var pendingScroll:PendingScroll?=null
    private val packageNamePattern=Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")
    internal fun liveAccess():LiveObservationAccess?=synchronized(lock){service?.let{LiveObservationAccess(it,epoch)}}
    internal fun liveRevision(access:LiveObservationAccess):Long=synchronized(lock){
        check(service===access.service&&epoch==access.epoch){"OBSERVATION_PERMISSION_CHANGED"}
        sequence
    }
    internal fun liveWindowRevision(access:LiveObservationAccess):Long=synchronized(lock){
        check(service===access.service&&epoch==access.epoch){"OBSERVATION_PERMISSION_CHANGED"}
        windowSequence
    }
    private fun clearLiveInputLocked(){
        pendingScroll?.let{@Suppress("DEPRECATION") it.node.recycle()}
        pendingScroll=null
    }
    internal fun prepareLiveInput(access:LiveObservationAccess,request:NodeActionRequest,
                                  before:ObservationStamp,node:AccessibilityNodeInfo)=synchronized(lock){
        check(liveRevision(access)==request.revision&&readingPackage==request.packageName){"STALE_LIVE_OPERATION"}
        check(before.taskId==request.taskId&&before.epoch==access.epoch&&before.revision==request.revision&&
            before.packageName==request.packageName&&before.windowId!=null&&
            node.windowId==before.windowId&&node.packageName?.toString()==request.packageName){"ACTION_WINDOW_MISMATCH"}
        clearLiveInputLocked()
        operationWindowId=before.windowId
        if(request.kind==NodeActionKind.SCROLL_FORWARD||request.kind==NodeActionKind.SCROLL_BACKWARD){
            @Suppress("DEPRECATION")
            val copy=AccessibilityNodeInfo.obtain(node)
            pendingScroll=PendingScroll(request,before,copy,SystemClock.uptimeMillis())
        }
    }
    internal fun liveScrollEvidence(access:LiveObservationAccess,request:NodeActionRequest,
                                     before:ObservationStamp):LiveScrollEvidence?=synchronized(lock){
        liveRevision(access)
        pendingScroll?.takeIf{it.request==request&&it.before==before}?.evidence
    }
    internal fun <T> withLiveOperation(pkg:String,body:(LiveObservationAccess)->T):T {
        val access=synchronized(lock){
            val connected=service?:error("ACCESSIBILITY_OBSERVATION_NOT_GRANTED")
            check(readingPackage==null&&!clickInProgress){"ANOTHER_SCREEN_READ_ACTIVE"}
            clearLiveInputLocked();readingPackage=pkg;operationWindowId=null
            LiveObservationAccess(connected,epoch)
        }
        try { return body(access) }
        finally { synchronized(lock){clearLiveInputLocked();readingPackage=null;operationWindowId=null;lock.notifyAll()} }
    }
    internal fun awaitLiveRevision(access:LiveObservationAccess,after:Long,deadlineNanos:Long,checkActive:()->Unit):Boolean {
        synchronized(lock){
            while(liveRevision(access)<=after){
                val remaining=(deadlineNanos-System.nanoTime())/1_000_000
                if(remaining<=0)return false
                checkActive()
                lock.wait(remaining.coerceAtMost(50).coerceAtLeast(1))
            }
            return true
        }
    }
    fun connect(s:ContinuumScreenObservationService)=synchronized(lock){clearLiveInputLocked();service=s;epoch++;latest=null;lock.notifyAll()}
    fun disconnect(s:ContinuumScreenObservationService)=synchronized(lock){if(service===s){clearLiveInputLocked();service=null;epoch++;latest=null;lock.notifyAll()}}
    fun interrupt(s:ContinuumScreenObservationService)=synchronized(lock){if(service===s){clearLiveInputLocked();epoch++;latest=null;wakeSequence++;lock.notifyAll()}}
    fun observed(s:ContinuumScreenObservationService,pkg:String,eventSender:String?=null)=synchronized(lock){if(service===s){
        windowSequence++
        if(clickInProgress){
            if(pkg==readingPackage && eventSender==readingPackage && operationWindowId!=null)clickTargetEvent=true
            else clickUncertain=true
        }
        sequence++;latest=if(pkg.isNotBlank())ForegroundWindow(pkg,sequence,epoch) else null;lock.notifyAll()}}
    fun contentChanged(s:ContinuumScreenObservationService,pkg:String?,windowId:Int?=null)=synchronized(lock){if(service===s){
        // Only a well-formed OS event sender known to differ from the active read
        // can be ignored. Missing/malformed senders remain uncertainty, never success.
        val target=readingPackage
        val knownPackage=pkg?.takeIf{packageNamePattern.matches(it)}
        // During a click, an unrelated sender is harmless only after the
        // operation has bound a concrete target window. Unknown stays closed.
        if(target!=null&&knownPackage!=null&&knownPackage!=target&&
            (!clickInProgress||operationWindowId!=null))return@synchronized
        if(knownPackage==target&&operationWindowId!=null&&windowId!=null&&windowId>=0&&
            windowId!=operationWindowId)return@synchronized
        if(clickInProgress){
            if(knownPackage==target&&operationWindowId!=null)clickTargetEvent=true else clickUncertain=true
        }
        sequence++;if(knownPackage==null)latest=null;lock.notifyAll()}}
    internal fun scrolled(s:ContinuumScreenObservationService,event:AccessibilityEvent){
        val inspectSource=synchronized(lock){service===s&&pendingScroll!=null}
        val source=if(inspectSource) runCatching{event.source}.getOrNull() else null
        try{synchronized(lock){
            if(service!==s)return
            val priorRevision=sequence
            contentChanged(s,event.packageName?.toString(),event.windowId)
            val pending=pendingScroll?:return
            val at=event.eventTime
            if(sequence==priorRevision||epoch!=pending.before.epoch||source==null||
                event.packageName?.toString()!=pending.before.packageName||event.windowId!=pending.before.windowId||
                source.windowId!=pending.before.windowId||source.packageName?.toString()!=pending.before.packageName||
                source!=pending.node||at<pending.dispatchedAtMillis||at>SystemClock.uptimeMillis())return
            val previous=pending.position
            if(previous!=null&&at<previous.atMillis)return
            fun coordinate(value:Int):Int?=value.takeIf{it>=0&&it!=Int.MAX_VALUE}
            val position=ScrollPosition(coordinate(event.scrollX),coordinate(event.scrollY),coordinate(event.fromIndex),at)
            fun measured(value:Int):Long?=value.takeUnless{it==-1||it==Int.MIN_VALUE||it==Int.MAX_VALUE}?.toLong()
            fun difference(current:Int?,previous:Int?):Long=
                if(current!=null&&previous!=null)current.toLong()-previous else 0L
            val deltaX=(if(Build.VERSION.SDK_INT>=28)measured(event.scrollDeltaX) else null)
                ?.takeIf{it!=0L}?:difference(position.x,previous?.x)
            val deltaY=(if(Build.VERSION.SDK_INT>=28)measured(event.scrollDeltaY) else null)
                ?.takeIf{it!=0L}?:difference(position.y,previous?.y)
            val indexDelta=difference(position.fromIndex,previous?.fromIndex)
            pending.position=position
            if(deltaX!=0L||deltaY!=0L||indexDelta!=0L){
                pending.evidence=LiveScrollEvidence(pending.before,pending.request.fingerprint,
                    pending.dispatchedAtMillis,at,sequence,deltaX,deltaY,indexDelta)
            }
        }}finally{@Suppress("DEPRECATION") source?.recycle()}
    }
    private fun bindWindow(s:ContinuumScreenObservationService,pkg:String,grant:Long,revision:Long,id:Int,
                           allowTargetTransition:Boolean=false)=synchronized(lock){
        check(service===s&&epoch==grant&&sequence==revision&&readingPackage==pkg){"SCREEN_CHANGED_DURING_READ"}
        if(id>=0){
            check(operationWindowId==null||operationWindowId==id||
                (allowTargetTransition&&clickInProgress&&clickTargetEvent&&!clickUncertain)){"SCREEN_CHANGED_DURING_READ"}
            operationWindowId=id
        }
    }
    /** Ignore only a verified different, nonoverlapping inactive window while the
     * original active app/window and permission epoch remain provably unchanged. */
    fun ignoreVerifiedUnrelatedWindow(s:ContinuumScreenObservationService,event:AccessibilityEvent):Boolean {
        val target=synchronized(lock){
            val pkg=readingPackage;val id=operationWindowId
            if(service!==s||pkg==null||id==null) null else WindowTicket(pkg,id,epoch,sequence)
        }?:return false
        // Older Android cannot report change kinds; unknown or focus/active changes
        // must not be mistaken for a harmless background-window update.
        if(android.os.Build.VERSION.SDK_INT<28)return false
        val harmless=AccessibilityEvent.WINDOWS_CHANGE_ADDED or AccessibilityEvent.WINDOWS_CHANGE_BOUNDS or
            AccessibilityEvent.WINDOWS_CHANGE_LAYER or AccessibilityEvent.WINDOWS_CHANGE_TITLE
        val changes=event.windowChanges
        if(changes==0 || changes and harmless.inv()!=0)return false
        val sender=event.packageName?.toString()?.takeIf{packageNamePattern.matches(it)}?:return false
        val eventId=event.windowId
        if(sender==target.pkg||eventId<0||eventId==target.windowId)return false
        val safe=runCatching {
            val root=s.rootInActiveWindow?:return@runCatching false
            try {
                if(root.packageName?.toString()!=target.pkg||root.windowId!=target.windowId)return@runCatching false
                val rootBounds=Rect().also(root::getBoundsInScreen)
                if(rootBounds.isEmpty)return@runCatching false
                val windows=s.windows?:return@runCatching false
                try {
                    val active=windows.singleOrNull{it.id==target.windowId}?:return@runCatching false
                    if(active.type!=AccessibilityWindowInfo.TYPE_APPLICATION||!active.isActive||!active.isFocused)return@runCatching false
                    val activeBounds=Rect().also(active::getBoundsInScreen)
                    if(activeBounds.isEmpty||!activeBounds.contains(rootBounds))return@runCatching false
                    val foreign=windows.singleOrNull{it.id==eventId}?:return@runCatching false
                    for(window in windows.filter{it.id!=target.windowId}){
                        if(window.type !in setOf(AccessibilityWindowInfo.TYPE_APPLICATION,AccessibilityWindowInfo.TYPE_SYSTEM)||
                            window.isActive||window.isFocused)return@runCatching false
                        val bounds=Rect().also(window::getBoundsInScreen)
                        if(bounds.isEmpty||Rect.intersects(bounds,rootBounds))return@runCatching false
                    }
                    val foreignRoot=foreign.root?:return@runCatching false
                    try {foreignRoot.packageName?.toString()==sender}
                    finally{@Suppress("DEPRECATION") foreignRoot.recycle()}
                } finally {windows.forEach{@Suppress("DEPRECATION") it.recycle()}}
            } finally {@Suppress("DEPRECATION") root.recycle()}
        }.getOrDefault(false)
        if(!safe)return false
        return synchronized(lock){service===s&&readingPackage==target.pkg&&operationWindowId==target.windowId&&
            epoch==target.epoch&&sequence==target.revision}
    }
    private data class WindowTicket(val pkg:String,val windowId:Int,val epoch:Long,val revision:Long)
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
    /** Experimental native port; no registry or production approval pathway yet. */
    internal fun clickCandidate(request:ClickRequest,ownerConfirmed:Boolean,checkRequest:()->Unit):ClickFrame {
        val started=System.nanoTime()
        fun checkBound(){
            checkRequest()
            if(Thread.currentThread().isInterrupted)throw ToolBackendFailure(ToolExecutionStatus.CANCELLED,"DEVICE_ACTION_CANCELLED")
            if(System.nanoTime()-started>2_000_000_000L)throw ToolBackendFailure(ToolExecutionStatus.TIMED_OUT,"SCREEN_CLICK_DEADLINE")
        }
        val current=synchronized(lock){
            val connected=service?:error("ACCESSIBILITY_OBSERVATION_NOT_GRANTED")
            check(readingPackage==null&&!clickInProgress){"ANOTHER_SCREEN_READ_ACTIVE"}
            check(epoch==request.epoch&&sequence==request.revision){"STALE_OR_UNSAFE_SCREEN"}
            clickInProgress=true;readingPackage=request.packageName;operationWindowId=null;clickTargetEvent=false;clickUncertain=false
            connected
        }
        fun state():Long=synchronized(lock){
            check(service===current&&epoch==request.epoch){"OBSERVATION_PERMISSION_CHANGED"}
            check(!clickUncertain){"SCREEN_CHANGED_DURING_READ"}
            sequence
        }
        val port=object:ClickObservationPort {
            private var before:ClickFrame?=null
            override fun checkActive(){ checkBound();state() }
            override fun current():ClickFrame {
                val rev=state()
                return current.snapshotForClick(request,rev,::checkBound) { id->bindWindow(current,request.packageName,request.epoch,rev,id) }.also {
                    check(state()==rev){"SCREEN_CHANGED_DURING_READ"};before=it
                }
            }
            override fun clickOnce(request:ClickRequest):Boolean {
                val rev=state();check(rev==request.revision){"SCREEN_CHANGED_DURING_READ"}
                return current.dispatchClick(request,rev,::checkBound,{ id->bindWindow(current,request.packageName,request.epoch,rev,id) }) { frame ->
                    check(frame==before&&state()==rev){"SCREEN_CHANGED_DURING_READ"}
                }
            }
            override fun awaitPostcondition():ClickFrame {
                synchronized(lock){while(service===current&&epoch==request.epoch&&!clickTargetEvent&&!clickUncertain){
                    checkBound();lock.wait(50)
                }}
                checkBound()
                val rev=state()
                return current.snapshotForClick(request,rev,::checkBound) { id->bindWindow(current,request.packageName,request.epoch,rev,id,allowTargetTransition=true) }.also {
                    check(state()==rev){"SCREEN_CHANGED_DURING_READ"}
                }
            }
        }
        return try { VerifiedNodeClick.execute(request,ownerConfirmed,port) }
        finally { synchronized(lock){clickInProgress=false;readingPackage=null;operationWindowId=null;clickTargetEvent=false;clickUncertain=false;lock.notifyAll()} }
    }
    fun read(expectedPackage:String,checkActive:()->Unit):ObservedAppScreen{
        val state=synchronized(lock){
            val current=service?:error("ACCESSIBILITY_OBSERVATION_NOT_GRANTED")
            check(readingPackage==null&&!clickInProgress){"ANOTHER_SCREEN_READ_ACTIVE"}
            readingPackage=expectedPackage;operationWindowId=null
            Triple(current,epoch,++sequence)
        }
        try {
            val observedAt=System.currentTimeMillis()
            val result=state.first.readScreen(expectedPackage,checkActive){ id->bindWindow(state.first,expectedPackage,state.second,state.third,id) }
            checkActive() // Preserve cancellation/deadline checks before publishing text.
            synchronized(lock){check(service===state.first&&epoch==state.second&&sequence==state.third){"SCREEN_CHANGED_DURING_READ"}}
            return ObservedAppScreen(result,state.second,state.third,observedAt)
        } finally {
            synchronized(lock){readingPackage=null;operationWindowId=null}
        }

    }
}
