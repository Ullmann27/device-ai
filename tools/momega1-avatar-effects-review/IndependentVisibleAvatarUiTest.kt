package at.heinz.deviceai

import android.app.Activity
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.GazeTarget
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.ContinuumHologramRenderer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController
import java.util.concurrent.TimeUnit

/** Actual pane builder/navigation methods; a plain Activity window avoids unrelated full app startup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class IndependentVisibleAvatarUiTest {
    private fun set(a:AgentActivity,n:String,v:Any?)=AgentActivity::class.java.getDeclaredField(n).apply {isAccessible=true}.set(a,v)
    private fun get(a:AgentActivity,n:String)=AgentActivity::class.java.getDeclaredField(n).apply {isAccessible=true}.get(a)
    private fun invoke(a:AgentActivity,n:String)=AgentActivity::class.java.getDeclaredMethod(n).apply {isAccessible=true}.invoke(a)
    private fun field(v:ContinuumHologramView,n:String)=ContinuumHologramView::class.java.getDeclaredField(n).apply {isAccessible=true}.get(v)
    private fun descendants(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap {descendants(v.getChildAt(it))}else emptyList()
    private fun measure(v:View,w:Int,h:Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h)
    }
    private data class Fixture(val a:AgentActivity,val host:ActivityController<Activity>,val pane:View,
        val head:ContinuumHologramView,val home:ContinuumHomeView,val chat:LinearLayout,val director:AvatarDirector)
    private fun fixture(wide:Boolean=false,w:Int=412,h:Int=900):Fixture {
        val a=Robolectric.buildActivity(AgentActivity::class.java).get()
        set(a,"bootstrapMemory",BootstrapMemoryStore(a));set(a,"foregroundLearningActive",true)
        set(a,"renderedWindowWidthDp",w.toFloat());set(a,"renderedWindowHeightDp",h.toFloat())
        val pane=AgentActivity::class.java.getDeclaredMethod("buildConversationPane",Boolean::class.javaPrimitiveType)
            .apply {isAccessible=true}.invoke(a,wide) as View
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible();host.get().setContentView(pane)
        shadowOf(Looper.getMainLooper()).idle();measure(pane,w,h)
        return Fixture(a,host,pane,get(a,"hologramView") as ContinuumHologramView,get(a,"homeView") as ContinuumHomeView,
            get(a,"chatColumn") as LinearLayout,get(a,"avatarDirector") as AvatarDirector)
    }
    private fun chat(f:Fixture,show:Boolean,compact:Boolean=false) {set(f.a,"conversationActive",show);set(f.a,"keyboardCompact",compact);invoke(f.a,"refreshConversationLayout")}
    private fun close(f:Fixture) {f.head.setForegroundActive(false);f.home.setForegroundActive(false);f.host.pause().stop().destroy()}
    @Test fun actualPaneHasOneHeadAndOneDirectorAcrossRepeatedNavigation() {
        val f=fixture()
        try {
            repeat(12) {i ->
                chat(f,i%2==0)
                assertSame(if(i%2==0)f.chat else f.home,f.head.parent)
                assertSame(f.head,get(f.a,"hologramView"));assertSame(f.director,get(f.a,"avatarDirector"))
                assertEquals(1,descendants(f.pane).count {it is ContinuumHologramView})
                assertSame(f.head,AvatarDirector::class.java.getDeclaredField("sink").apply {isAccessible=true}.get(f.director))
            }
        }finally {close(f)}
    }
    @Test fun phoneAndBothFoldCompositionsRestoreOriginalBoundsAndGeometry() {
        for((w,h) in listOf(412 to 900,1100 to 750,750 to 1100)) {
            val f=fixture(w>500,w,h)
            try {
                val renderer=field(f.head,"renderer") as ContinuumHologramRenderer
                val original=renderer.geometry
                val bounds=listOf(f.head.left,f.head.top,f.head.right,f.head.bottom)
                chat(f,true);measure(f.pane,w,h/2)
                assertSame(f.chat,f.head.parent)
                chat(f,false);measure(f.pane,w,h)
                assertEquals(original,renderer.geometry)
                assertEquals(bounds,listOf(f.head.left,f.head.top,f.head.right,f.head.bottom))
            }finally {close(f)}
        }
    }
    @Test fun compactActualComposerRetainsUsableHeightBelowVisibleHead() {
        val f=fixture()
        try {
            chat(f,true);measure(f.pane,412,640)
            val full=f.head.height
            chat(f,true,true);measure(f.pane,412,420)
            val input=get(f.a,"input") as EditText
            assertSame(f.chat,f.head.parent);assertTrue(f.head.height in 1 until full)
            assertTrue("The real composer must retain a usable input",input.height>=32)
            val r=android.graphics.Rect();input.getDrawingRect(r);f.chat.offsetDescendantRectToMyCoords(input,r)
            assertTrue("Composer must remain within compact visible column",r.bottom<=f.chat.height)
            assertTrue("Avatar must not cover the composer",f.head.bottom<=r.top)
        }finally {close(f)}
    }
    @Test fun foreignHomeLayoutCannotClobberChatHeadButRestoresOnReturn() {
        val f=fixture()
        try {
            chat(f,true,true);measure(f.pane,412,420)
            val dims=listOf(f.head.measuredWidth,f.head.measuredHeight,f.head.left,f.head.top,f.head.right,f.head.bottom)
            repeat(3) {measure(f.home,800,1200)}
            assertEquals(dims,listOf(f.head.measuredWidth,f.head.measuredHeight,f.head.left,f.head.top,f.head.right,f.head.bottom))
            chat(f,false);measure(f.pane,412,900)
            assertSame(f.home,f.head.parent);assertTrue(f.head.height>88)
        }finally {close(f)}
    }
    @Test fun actualTtsListenerAndDirectorKeepCurrentUtteranceOnMovedHead() {
        val f=fixture();val tts=TextToSpeech(f.a){}
        try {
            f.head.setNativeHeadEnabled(true)
            set(f.a,"voiceEngine",tts);set(f.a,"voiceUtteranceId","review-utterance");set(f.a,"voiceAvatarText","synthetic speech")
            invoke(f.a,"installVoiceProgressListener")
            val listener=shadowOf(tts).utteranceProgressListener!!;listener.onStart("review-utterance")
            f.director.audioLevel("review-utterance",.8f)
            val pose=field(f.head,"avatarPose") as AvatarPoseReducer
            assertTrue(pose.pose(System.currentTimeMillis()).jawOpen>0)
            chat(f,true)
            assertSame(f.chat,f.head.parent);assertTrue(f.head.isNativeHeadEnabled())
            f.director.audioLevel("review-utterance",.8f)
            assertTrue("Reparent must reattach the still-active utterance",pose.pose(System.currentTimeMillis()).jawOpen>0)
            listener.onDone("review-utterance")
            assertEquals(0f,pose.pose(System.currentTimeMillis()).jawOpen,0f)
        }finally {tts.shutdown();close(f)}
    }
    @Test fun movedHeadHasNoHiddenCopyAndBackgroundStopsExistingClock() {
        val f=fixture()
        try {
            fun snapshot(label:String) {
                fun describe(v:View)="${v.javaClass.simpleName}: shown=${v.isShown} attached=${v.isAttachedToWindow} window=${v.windowVisibility} visibility=${v.visibility}"
                println("HOST_DIAGNOSTIC $label hostFinishing=${f.host.get().isFinishing} hostFocus=${f.host.get().hasWindowFocus()} host=${describe(f.host.get().window.decorView)} pane=${describe(f.pane)} home=${describe(f.home)} chat=${describe(f.chat)} head=${describe(f.head)}")
            }
            snapshot("before-chat");chat(f,true);snapshot("after-reparent")
            shadowOf(Looper.getMainLooper()).idleFor(100,TimeUnit.MILLISECONDS);snapshot("after-idle")
            val state="shown=${f.head.isShown} attached=${f.head.isAttachedToWindow} window=${f.head.windowVisibility} motion=${f.head.sampleMotion()} pending=${field(f.head,"framePending")} foreground=${field(f.head,"foregroundActive")} allowed=${field(f.head,"motionAllowed") }"
            println("VISIBILITY_DIAGNOSTIC $state")
            assertTrue("Head must be shown: $state",f.head.isShown);assertTrue("Visible clock must advance: $state",f.head.sampleMotion().callbackCount>0)
            invoke(f.a,"onPause")
            val stopped=f.head.sampleMotion().callbackCount
            shadowOf(Looper.getMainLooper()).idleFor(200,TimeUnit.MILLISECONDS)
            assertEquals(stopped,f.head.sampleMotion().callbackCount)
            assertEquals(1,descendants(f.pane).count {it is ContinuumHologramView})
        }finally {close(f)}
    }
}
