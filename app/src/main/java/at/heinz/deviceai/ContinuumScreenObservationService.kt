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
import at.heinz.deviceai.cognitive.*

class ContinuumScreenObservationService:AccessibilityService(){
    override fun onServiceConnected(){super.onServiceConnected();ScreenObservationBus.connect(this)}
    override fun onAccessibilityEvent(event:AccessibilityEvent?){
        if(event==null)return
        if(event.eventType==AccessibilityEvent.TYPE_VIEW_SCROLLED){
            ScreenObservationBus.scrolled(this,event)
            return
        }
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
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
    internal class ScrollWatch(val access:LiveObservationAccess,val dispatch:LiveScrollDispatch,
                               val source:AccessibilityNodeInfo) {
        var sample:LiveScrollSample?=null
        var lastEventTime=Long.MIN_VALUE
        var evidence:LiveScrollEvidence?=null
    }
    private var scrollWatch:ScrollWatch?=null
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
    internal fun <T> withLiveOperation(pkg:String,body:(LiveObservationAccess)->T):T {
        val access=synchronized(lock){
            val connected=service?:error("ACCESSIBILITY_OBSERVATION_NOT_GRANTED")
            check(readingPackage==null&&!clickInProgress){"ANOTHER_SCREEN_READ_ACTIVE"}
            readingPackage=pkg;operationWindowId=null
            LiveObservationAccess(connected,epoch)
        }
        try { return body(access) }
        finally { synchronized(lock){
            if(scrollWatch?.access==access)clearScrollLocked()
            readingPackage=null;operationWindowId=null;lock.notifyAll()
        } }
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
    fun connect(s:ContinuumScreenObservationService)=synchronized(lock){clearScrollLocked();service=s;epoch++;latest=null;lock.notifyAll()}
    fun disconnect(s:ContinuumScreenObservationService)=synchronized(lock){if(service===s){clearScrollLocked();service=null;epoch++;latest=null;lock.notifyAll()}}
    fun interrupt(s:ContinuumScreenObservationService)=synchronized(lock){if(service===s){clearScrollLocked();epoch++;latest=null;wakeSequence++;lock.notifyAll()}}
    fun observed(s:ContinuumScreenObservationService,pkg:String,eventSender:String?=null)=synchronized(lock){if(service===s){
        clearScrollLocked()
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
        // Continuum's own label/animation updates never change the foreign target (see LiveObservationEventPolicy).
        if(LiveObservationEventPolicy.ignoreOwnContent(knownPackage,runCatching{s.packageName}.getOrNull().orEmpty(),target))return@synchronized
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

    internal fun beginScroll(access:LiveObservationAccess,request:NodeActionRequest,before:ObservationStamp,
                             target:ActionNode,source:AccessibilityNodeInfo):ScrollWatch = synchronized(lock) {
        check(service===access.service && epoch==access.epoch && sequence==before.revision && scrollWatch==null &&
            before.captureSession>0 && before.windowId==source.windowId && before.epoch==access.epoch &&
            before.taskId==request.taskId && before.packageName==request.packageName &&
            source.packageName?.toString()==request.packageName && source.isScrollable && source.isEnabled &&
            source.isVisibleToUser && !source.isPassword) {
            "SCROLL_BINDING_UNAVAILABLE"
        }
        val dispatch=LiveScrollDispatch(before,request.stepId,request.fingerprint,
            LiveScrollDispatch.targetFingerprint(target),System.nanoTime(),android.os.SystemClock.uptimeMillis())
        @Suppress("DEPRECATION")
        ScrollWatch(access,dispatch,AccessibilityNodeInfo.obtain(source)).also { scrollWatch=it }
    }

    internal fun matchesScrollNode(watch:ScrollWatch,node:AccessibilityNodeInfo):Boolean = synchronized(lock) {
        scrollWatch===watch && service===watch.access.service && epoch==watch.access.epoch && node==watch.source
    }

    internal fun scrollEvidence(watch:ScrollWatch,observed:ObservationStamp):LiveScrollEvidence? = synchronized(lock) {
        if(scrollWatch!==watch || service!==watch.access.service || epoch!=watch.access.epoch ||
            observed.captureSession!=watch.dispatch.source.captureSession) null
        else watch.evidence?.takeIf { it.eventRevision<=observed.revision && it.receivedAtNanos<=observed.observedAtNanos }
    }

    internal fun finishScroll(watch:ScrollWatch?) = synchronized(lock) {
        if(watch!=null && scrollWatch===watch) clearScrollLocked()
    }

    private fun clearScrollLocked() {
        val old=scrollWatch
        scrollWatch=null
        @Suppress("DEPRECATION")
        old?.source?.recycle()
    }

    internal fun scrolled(s:ContinuumScreenObservationService,event:AccessibilityEvent) {
        if(event.eventType!=AccessibilityEvent.TYPE_VIEW_SCROLLED)return
        val source=runCatching { event.source }.getOrNull()
        try { synchronized(lock) {
            contentChanged(s,event.packageName?.toString(),event.windowId)
            val watch=scrollWatch?:return@synchronized
            val initial=watch.dispatch.source
            val received=System.nanoTime()
            if(service!==s || s!==watch.access.service || epoch!=watch.access.epoch || source==null ||
                source!=watch.source || source.windowId!=initial.windowId || event.windowId!=initial.windowId ||
                event.packageName?.toString()!=initial.packageName || source.packageName?.toString()!=initial.packageName ||
                !source.isScrollable || !source.isEnabled || !source.isVisibleToUser || source.isPassword ||
                event.eventTime<watch.dispatch.dispatchedAtUptimeMillis || event.eventTime<watch.lastEventTime ||
                event.eventTime>android.os.SystemClock.uptimeMillis() ||
                received<watch.dispatch.dispatchedAtNanos || sequence<=initial.revision) return@synchronized
            fun offset(value:Int,max:Int)=value.takeIf { max>0 && it in 0..max && it!=Int.MAX_VALUE }
            val sample=LiveScrollSample(
                if(android.os.Build.VERSION.SDK_INT>=30) event.scrollDeltaX else null,
                if(android.os.Build.VERSION.SDK_INT>=30) event.scrollDeltaY else null,
                offset(event.scrollX,event.maxScrollX),offset(event.scrollY,event.maxScrollY),
                event.fromIndex.takeIf { it>=0 },event.itemCount.takeIf { it>0 })
            sample.movementSince(watch.sample)?.let { movement ->
                watch.evidence=LiveScrollEvidence(watch.dispatch,sequence,received,event.eventTime,movement)
            }
            watch.sample=sample
            watch.lastEventTime=event.eventTime
            lock.notifyAll()
        } } finally { @Suppress("DEPRECATION") source?.recycle() }
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
