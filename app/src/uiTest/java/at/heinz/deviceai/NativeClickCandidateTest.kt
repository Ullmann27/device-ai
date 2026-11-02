package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import at.heinz.deviceai.cognitive.ClickRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityNodeInfo
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.shadows.ShadowAccessibilityWindowInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], shadows=[NativeClickCandidateTest.ServiceShadow::class,NativeClickCandidateTest.NodeShadow::class,NativeClickCandidateTest.WindowShadow::class])
class NativeClickCandidateTest {
    @Implements(AccessibilityService::class)
    class ServiceShadow:ShadowAccessibilityService(){
        companion object { var template:AccessibilityNodeInfo?=null; var count=1; var targetId=42; var overlap=false; var otherType=AccessibilityWindowInfo.TYPE_APPLICATION }
        @Implementation protected fun getRootInActiveWindow()=template?.let { AccessibilityNodeInfo.obtain(it) }
        @Implementation public override fun getWindows()=List(count){index->
            AccessibilityWindowInfo.obtain().also { window ->
                val w=shadowOf(window);w.setId(if(index==0)targetId else 43)
                w.setType(if(index==0)AccessibilityWindowInfo.TYPE_APPLICATION else otherType)
                w.setActive(index==0);w.setFocused(index==0)
                if(index!=0)w.setRoot(AccessibilityNodeInfo.obtain().apply{packageName="com.other.app"})
                w.setBoundsInScreen(if(index==0||overlap) Rect(0,0,500,1000) else Rect(600,0,1000,1000))
            }
        }
    }
    @Implements(AccessibilityWindowInfo::class)
    class WindowShadow:ShadowAccessibilityWindowInfo()
    @Implements(AccessibilityNodeInfo::class)
    class NodeShadow:ShadowAccessibilityNodeInfo(){
        companion object { var action:(()->Boolean)?=null; var onRefresh:(()->Unit)?=null; var currentWindowId=42 }
        @Implementation public override fun getWindowId():Int=Companion.currentWindowId
        @Implementation public override fun refresh():Boolean { onRefresh?.invoke();return true }
        @Implementation public override fun performAction(action:Int):Boolean =
            if(action==AccessibilityNodeInfo.ACTION_CLICK) Companion.action?.invoke()?:false else false
    }
    private val service=ContinuumScreenObservationService()
    private fun node(text:String,pkg:String="com.android.settings",sensitive:Boolean=false)=AccessibilityNodeInfo.obtain().apply{
        packageName=pkg;this.text=text;isVisibleToUser=true;isEnabled=true;isClickable=true;isPassword=sensitive
        setBoundsInScreen(Rect(0,0,500,1000))
    }
    private fun setup():ClickRequest {
        ScreenObservationBus.connect(service);ServiceShadow.count=1;ServiceShadow.template=node("Akku")
        return ClickRequest("com.android.settings",ScreenObservationBus.grantEpoch()!!,ScreenObservationBus.revision(),"Akku","Akkuverbrauch")
    }
    @After fun cleanup(){ScreenObservationBus.disconnect(service);ServiceShadow.template=null;NodeShadow.action=null;NodeShadow.onRefresh=null;ServiceShadow.count=1;ServiceShadow.targetId=42;NodeShadow.currentWindowId=42;ServiceShadow.overlap=false;ServiceShadow.otherType=AccessibilityWindowInfo.TYPE_APPLICATION}
    private fun windowEvent(pkg:String?,id:Int,changes:Int=AccessibilityEvent.WINDOWS_CHANGE_BOUNDS){
        val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED)
        event.packageName=pkg;org.robolectric.util.ReflectionHelpers.setField(event,"mSourceWindowId",id)
        org.robolectric.util.ReflectionHelpers.setField(event,"mWindowChangeTypes",changes)
        try{service.onAccessibilityEvent(event)}finally{event.recycle()}
    }
    @Test fun unrelatedNonOverlappingWindowDoesNotInvalidateActiveRead(){
        setup();var checks=0;var delivered=false
        val result=ScreenObservationBus.read("com.android.settings"){
            if(++checks==3){
                delivered=true;ServiceShadow.count=2;windowEvent("com.other.app",43);ServiceShadow.count=1
            }
        }
        assertTrue(delivered);assertEquals(listOf("Akku"),result.snapshot.texts)
    }
    @Test fun overlappingWindowChangeStillInvalidatesRead(){
        setup();var checks=0;ServiceShadow.overlap=true
        val failure=assertThrows(IllegalStateException::class.java){
            ScreenObservationBus.read("com.android.settings"){
                if(++checks==3){ServiceShadow.count=2;windowEvent("com.other.app",43);ServiceShadow.count=1}
            }
        }
        assertEquals("SCREEN_CHANGED_DURING_READ",failure.message)
    }
    @Test fun foreignFocusOrUnknownSenderOrKeyboardInvalidatesRead(){
        for(case in 0..2){
            setup();var checks=0
            val failure=assertThrows(IllegalStateException::class.java){
                ScreenObservationBus.read("com.android.settings"){
                    if(++checks==3){
                        ServiceShadow.count=2
                        if(case==2)ServiceShadow.otherType=AccessibilityWindowInfo.TYPE_INPUT_METHOD
                        windowEvent(if(case==1)null else "com.other.app",43,
                            if(case==0)AccessibilityEvent.WINDOWS_CHANGE_FOCUSED else AccessibilityEvent.WINDOWS_CHANGE_BOUNDS)
                        ServiceShadow.count=1;ServiceShadow.otherType=AccessibilityWindowInfo.TYPE_APPLICATION
                    }
                }
            }
            assertEquals("SCREEN_CHANGED_DURING_READ",failure.message)
        }
    }
    @Test fun actualForegroundSwitchMissingRootAndUnknownWindowIdInvalidateRead(){
        for(case in 0..2){
            setup();var checks=0
            val failure=assertThrows(IllegalStateException::class.java){
                ScreenObservationBus.read("com.android.settings"){
                    if(++checks==3){
                        ServiceShadow.count=2
                        if(case==0)ServiceShadow.template=node("Andere App","com.other.app")
                        if(case==1)ServiceShadow.template=null
                        windowEvent("com.other.app",if(case==2)-1 else 43)
                        ServiceShadow.count=1
                    }
                }
            }
            assertEquals("SCREEN_CHANGED_DURING_READ",failure.message)
        }
    }
    @Test fun unknownContentDuringClickInvalidatesBeforeDispatch(){
        val request=setup();var calls=0;NodeShadow.action={calls++;true}
        NodeShadow.onRefresh={val e=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            try{service.onAccessibilityEvent(e)}finally{e.recycle()}}
        assertEquals("SCREEN_CHANGED_DURING_READ",assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){}}.message)
        assertEquals(0,calls)
    }
    @Test fun unrelatedNonOverlappingWindowDoesNotInvalidateClick(){
        val request=setup();var calls=0
        NodeShadow.onRefresh={ServiceShadow.count=2;windowEvent("com.other.app",43);ServiceShadow.count=1}
        NodeShadow.action={calls++;ServiceShadow.template=node("Akkuverbrauch")
            val e=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);e.packageName="com.android.settings"
            org.robolectric.util.ReflectionHelpers.setField(e,"mSourceWindowId",NodeShadow.currentWindowId)
            try{service.onAccessibilityEvent(e)}finally{e.recycle()};true}
        val result=ScreenObservationBus.clickCandidate(request,true){}
        assertEquals(1,calls);assertTrue(result.nodes.any{it.label=="Akkuverbrauch"})
    }
    @Test fun foreignFocusChangeDuringClickStillInvalidates(){
        val request=setup();var calls=0;NodeShadow.action={calls++;true}
        NodeShadow.onRefresh={ServiceShadow.count=2;windowEvent("com.other.app",43,AccessibilityEvent.WINDOWS_CHANGE_FOCUSED);ServiceShadow.count=1}
        assertEquals("SCREEN_CHANGED_DURING_READ",assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){}}.message)
        assertEquals(0,calls)
    }
    @Test fun foreignContentBeforeClickWindowIsBoundFailsClosed(){
        val request=setup();var calls=0;var delivered=false;NodeShadow.action={calls++;true}
        assertThrows(IllegalStateException::class.java){
            ScreenObservationBus.clickCandidate(request,true){
                if(!delivered){delivered=true;val e=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                    e.packageName="com.other.app";org.robolectric.util.ReflectionHelpers.setField(e,"mSourceWindowId",43);try{service.onAccessibilityEvent(e)}finally{e.recycle()}}
                if(calls>0)error("UNEXPECTED_ACTION")
            }
        }
        assertTrue(delivered);assertEquals(0,calls)
    }
    @Test fun foreignContentDuringClickDoesNotInvalidateOrProveSuccess(){
        val request=setup();var calls=0
        NodeShadow.onRefresh={val e=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            e.packageName="com.other.app";org.robolectric.util.ReflectionHelpers.setField(e,"mSourceWindowId",43);try{service.onAccessibilityEvent(e)}finally{e.recycle()}}
        NodeShadow.action={calls++;ServiceShadow.template=node("Akkuverbrauch")
            val e=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);e.packageName="com.android.settings"
            org.robolectric.util.ReflectionHelpers.setField(e,"mSourceWindowId",NodeShadow.currentWindowId)
            try{service.onAccessibilityEvent(e)}finally{e.recycle()};true}
        val result=ScreenObservationBus.clickCandidate(request,true){}
        assertEquals(1,calls);assertTrue(result.nodes.any{it.label=="Akkuverbrauch"})
    }
    @Test fun foreignContentAloneCannotProveClickSuccess(){
        val request=setup();var calls=0;var checks=0
        NodeShadow.action={calls++;val e=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            e.packageName="com.other.app";org.robolectric.util.ReflectionHelpers.setField(e,"mSourceWindowId",43);try{service.onAccessibilityEvent(e)}finally{e.recycle()};true}
        val failure=assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){if(++checks>30)error("TIMEOUT")}}
        assertEquals("TIMEOUT",failure.message);assertEquals(1,calls)
    }
    @Test fun verifiedActionRequiresNewObservedScreen(){
        val request=setup();var calls=0
        NodeShadow.action={calls++;ServiceShadow.template=node("Akkuverbrauch");
            val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);event.packageName="com.android.settings"
            org.robolectric.util.ReflectionHelpers.setField(event,"mSourceWindowId",NodeShadow.currentWindowId)
            try{service.onAccessibilityEvent(event)}finally{event.recycle()};true}
        val result=ScreenObservationBus.clickCandidate(request,true){}
        assertEquals(1,calls);assertTrue(result.nodes.any{it.label=="Akkuverbrauch"})
    }
    @Test fun targetNavigationToNewWindowCanBeVerifiedAfterRelevantEvent(){
        val request=setup();var calls=0
        NodeShadow.action={calls++;ServiceShadow.template=node("Akkuverbrauch")
            ServiceShadow.targetId=44;NodeShadow.currentWindowId=44
            val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);event.packageName="com.android.settings"
            org.robolectric.util.ReflectionHelpers.setField(event,"mSourceWindowId",NodeShadow.currentWindowId)
            try{service.onAccessibilityEvent(event)}finally{event.recycle()};true}
        val result=ScreenObservationBus.clickCandidate(request,true){}
        assertEquals(1,calls);assertTrue(result.nodes.any{it.label=="Akkuverbrauch"})
    }
    @Test fun unknownEventAfterDispatchCannotBecomeSuccessWithLaterTargetEvent(){
        val request=setup();var calls=0
        NodeShadow.action={calls++
            val unknown=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            try{service.onAccessibilityEvent(unknown)}finally{unknown.recycle()}
            ServiceShadow.template=node("Akkuverbrauch")
            val target=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);target.packageName="com.android.settings"
            org.robolectric.util.ReflectionHelpers.setField(target,"mSourceWindowId",NodeShadow.currentWindowId)
            try{service.onAccessibilityEvent(target)}finally{target.recycle()};true}
        assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){}}
        assertEquals(1,calls)
    }
    private fun assertUnidentifiedTargetWindowRejected(unknown:Boolean){
        val request=setup();var calls=0
        NodeShadow.action={calls++;ServiceShadow.template=node("Akkuverbrauch")
            val event=AccessibilityEvent.obtain(if(unknown)AccessibilityEvent.TYPE_WINDOWS_CHANGED
                else AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            event.packageName="com.android.settings"
            org.robolectric.util.ReflectionHelpers.setField(event,"mSourceWindowId",if(unknown)-1 else 43)
            try{service.onAccessibilityEvent(event)}finally{event.recycle()};true
        }
        assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){}}
        assertEquals(1,calls)
    }
    @Test fun samePackageDifferentWindowCannotConfirmClick()=assertUnidentifiedTargetWindowRejected(false)
    @Test fun unknownWindowChangeCannotConfirmClick()=assertUnidentifiedTargetWindowRejected(true)
    @Test fun samePackageOtherStateWindowCannotConfirmClick(){
        val request=setup();var calls=0
        NodeShadow.action={calls++;ServiceShadow.template=node("Akkuverbrauch")
            val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            event.packageName="com.android.settings"
            org.robolectric.util.ReflectionHelpers.setField(event,"mSourceWindowId",43)
            try{service.onAccessibilityEvent(event)}finally{event.recycle()};true
        }
        assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){}}
        assertEquals(1,calls)
    }
    @Test fun unrelatedWindowCannotConfirmForeground(){
        setup();val before=ScreenObservationBus.revision()
        val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName="com.android.settings"
        org.robolectric.util.ReflectionHelpers.setField(event,"mSourceWindowId",43)
        try{service.onAccessibilityEvent(event)}finally{event.recycle()}
        assertNull(ScreenObservationBus.awaitAfter(before,1))
    }
    @Test fun matchingWindowCanConfirmForeground(){
        setup();val before=ScreenObservationBus.revision()
        val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName="com.android.settings"
        org.robolectric.util.ReflectionHelpers.setField(event,"mSourceWindowId",NodeShadow.currentWindowId)
        try{service.onAccessibilityEvent(event)}finally{event.recycle()}
        assertEquals("com.android.settings",ScreenObservationBus.awaitAfter(before,1)?.packageName)
    }
    @Test fun unknownSenderCannotConfirmForeground(){
        setup();val before=ScreenObservationBus.revision()
        val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        org.robolectric.util.ReflectionHelpers.setField(event,"mSourceWindowId",NodeShadow.currentWindowId)
        try{service.onAccessibilityEvent(event)}finally{event.recycle()}
        assertNull(ScreenObservationBus.awaitAfter(before,1))
    }
    @Test fun noApprovalOrOverlayNeverClicks(){
        val request=setup();var calls=0;NodeShadow.action={calls++;true}
        assertEquals("OWNER_APPROVAL_REQUIRED",assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,false){}}.message)
        ServiceShadow.count=2
        assertEquals("AMBIGUOUS_OR_OVERLAID_WINDOWS",assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){}}.message)
        assertEquals(0,calls)
    }
    @Test fun wrongPackageSensitiveAndStaleNeverClick(){
        val request=setup();var calls=0;NodeShadow.action={calls++;true}
        ServiceShadow.template=node("Akku","com.other.app")
        assertEquals("UNEXPECTED_SCREEN_PACKAGE",assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){}}.message)
        ServiceShadow.template=node("Akku",sensitive=true)
        assertEquals("UNSAFE_OR_AMBIGUOUS_TARGET",assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){}}.message)
        ScreenObservationBus.observed(service,"com.android.settings")
        assertEquals("STALE_OR_UNSAFE_SCREEN",assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){}}.message)
        assertEquals(0,calls)
    }
    @Test fun overlayAppearingImmediatelyBeforeDispatchDoesNotClick(){
        val request=setup();var calls=0;NodeShadow.action={calls++;true}
        NodeShadow.onRefresh={ServiceShadow.count=2}
        assertEquals("AMBIGUOUS_OR_OVERLAID_WINDOWS",assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){if(calls>0)error("UNEXPECTED_ACTION")}}.message)
        assertEquals(0,calls)
    }
    @Test fun duplicateVisibleTargetsNeverClick(){
        val request=setup();var calls=0;NodeShadow.action={calls++;true}
        val root=node("Akku");shadowOf(root).addChild(node("Akku"));ServiceShadow.template=root
        assertEquals("UNSAFE_OR_AMBIGUOUS_TARGET",assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){}}.message)
        assertEquals(0,calls)
    }
    @Test fun revokedPermissionBeforeDispatchNeverClicks(){
        val request=setup();var calls=0;NodeShadow.action={calls++;true}
        NodeShadow.onRefresh={ScreenObservationBus.disconnect(service)}
        assertEquals("OBSERVATION_PERMISSION_CHANGED",assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){}}.message)
        assertEquals(0,calls)
    }
    @Test fun revokedPermissionAfterClickIsNeverSuccess(){
        val request=setup();var calls=0
        NodeShadow.action={calls++;ScreenObservationBus.disconnect(service);true}
        assertEquals("OBSERVATION_PERMISSION_CHANGED",assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){}}.message)
        assertEquals(1,calls)
    }
    @Test fun acceptedClickWithoutNewEvidenceIsNotSuccess(){
        val request=setup();var calls=0;var checks=0;NodeShadow.action={calls++;true}
        assertThrows(IllegalStateException::class.java){ScreenObservationBus.clickCandidate(request,true){if(++checks>12)error("TIMEOUT")}}
        assertEquals(1,calls)
    }
}
