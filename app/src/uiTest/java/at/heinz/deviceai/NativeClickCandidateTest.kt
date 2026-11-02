package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
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
        companion object { var template:AccessibilityNodeInfo?=null; var count=1 }
        @Implementation protected fun getRootInActiveWindow()=template?.let { AccessibilityNodeInfo.obtain(it) }
        @Implementation public override fun getWindows()=List(count){AccessibilityWindowInfo.obtain()}
    }
    @Implements(AccessibilityWindowInfo::class)
    class WindowShadow:ShadowAccessibilityWindowInfo(){
        @Implementation protected fun getId()=42
        @Implementation protected fun getType()=AccessibilityWindowInfo.TYPE_APPLICATION
        @Implementation protected fun isActive()=true
        @Implementation protected fun isFocused()=true
    }
    @Implements(AccessibilityNodeInfo::class)
    class NodeShadow:ShadowAccessibilityNodeInfo(){
        companion object { var action:(()->Boolean)?=null; var onRefresh:(()->Unit)?=null }
        @Implementation public override fun getWindowId()=42
        @Implementation public override fun refresh():Boolean { onRefresh?.invoke();return true }
        @Implementation public override fun performAction(action:Int):Boolean =
            if(action==AccessibilityNodeInfo.ACTION_CLICK) Companion.action?.invoke()?:false else false
    }
    private val service=ContinuumScreenObservationService()
    private fun node(text:String,pkg:String="com.android.settings",sensitive:Boolean=false)=AccessibilityNodeInfo.obtain().apply{
        packageName=pkg;this.text=text;isVisibleToUser=true;isEnabled=true;isClickable=true;isPassword=sensitive
    }
    private fun setup():ClickRequest {
        ScreenObservationBus.connect(service);ServiceShadow.count=1;ServiceShadow.template=node("Akku")
        return ClickRequest("com.android.settings",ScreenObservationBus.grantEpoch()!!,ScreenObservationBus.revision(),"Akku","Akkuverbrauch")
    }
    @After fun cleanup(){ScreenObservationBus.disconnect(service);ServiceShadow.template=null;NodeShadow.action=null;NodeShadow.onRefresh=null;ServiceShadow.count=1}
    @Test fun verifiedActionRequiresNewObservedScreen(){
        val request=setup();var calls=0
        NodeShadow.action={calls++;ServiceShadow.template=node("Akkuverbrauch");
            val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);event.packageName="com.android.settings"
            try{service.onAccessibilityEvent(event)}finally{event.recycle()};true}
        val result=ScreenObservationBus.clickCandidate(request,true){}
        assertEquals(1,calls);assertTrue(result.nodes.any{it.label=="Akkuverbrauch"})
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
