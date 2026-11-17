package at.heinz.deviceai

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

/** Actual Activity/listener/widgets; controlled TTS callbacks are not proof of audible output. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],shadows=[IndependentReadinessVoiceCompletionUiTest.ControlledTts::class])
class IndependentReadinessVoiceCompletionUiTest {
    @Implements(TextToSpeech::class)
    class ControlledTts:ShadowTextToSpeech() {
        @Implementation override fun speak(text:CharSequence,queueMode:Int,params:Bundle?,utteranceId:String):Int = TextToSpeech.SUCCESS
    }
    private fun set(a:AgentActivity,name:String,value:Any?)=AgentActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.set(a,value)
    private fun get(a:AgentActivity,name:String)=AgentActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.get(a)
    private fun invoke(a:AgentActivity,name:String)=AgentActivity::class.java.getDeclaredMethod(name).apply {isAccessible=true}.invoke(a)
    private fun code(name:String)=AgentActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.getInt(null)
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap {views(v.getChildAt(it))}else emptyList()
    private fun open():Pair<AgentActivity,AlertDialog> {
        val a=Robolectric.buildActivity(AgentActivity::class.java).get();set(a,"foregroundLearningActive",true)
        invoke(a,"showReadinessGuide");return a to ShadowAlertDialog.getLatestAlertDialog()
    }
    private fun hearingButton(d:AlertDialog)=views(d.window!!.decorView).filterIsInstance<Button>().singleOrNull {it.text=="ICH HÖRE DICH KLAR"}
    private fun voice(a:AgentActivity):Pair<TextToSpeech,UtteranceProgressListener> {
        val tts=TextToSpeech(a){}
        tts.voice=Voice("independent-offline",Locale.GERMAN,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,false,emptySet())
        set(a,"voiceEngine",tts);set(a,"voiceInitialized",true);set(a,"voiceReady",true);set(a,"voiceEnabled",true)
        invoke(a,"installVoiceProgressListener")
        return tts to checkNotNull(shadowOf(tts).utteranceProgressListener)
    }
    private fun started(a:AgentActivity,listener:UtteranceProgressListener):String {
        invoke(a,"playReadinessVoiceSample")
        val id=get(a,"readinessVoiceSampleId") as String
        listener.onStart(id)
        return id
    }
    @Test fun successfulCompletedSampleRemainsConfirmableExactlyOnce() {
        val(a,d)=open();val(tts,listener)=voice(a)
        try {
            val id=started(a,listener);val button=checkNotNull(hearingButton(d))
            listener.onDone(id)
            assertNull(get(a,"voiceUtteranceId"));assertEquals(true,get(a,"readinessVoiceSampleStarted"))
            button.performClick()
            assertTrue(get(a,"readinessVoiceResult").toString().contains("Von dir"))
            assertEquals(false,get(a,"readinessVoiceSampleStarted"));assertNull(hearingButton(d))
            set(a,"readinessVoiceResult","consumed sentinel")
            button.performClick()
            assertEquals("consumed sentinel",get(a,"readinessVoiceResult"))
        }finally {d.dismiss();shadowOf(Looper.getMainLooper()).idle();tts.shutdown()}
    }
    @Test fun oldButtonCannotConfirmAReplacementEvenAfterItsStart() {
        val(a,d)=open();val(tts,listener)=voice(a)
        try {
            val first=started(a,listener);val old=checkNotNull(hearingButton(d))
            val second=started(a,listener);assertNotEquals(first,second)
            val current=checkNotNull(hearingButton(d))
            old.performClick()
            assertFalse(get(a,"readinessVoiceResult").toString().contains("Von dir"))
            assertEquals(true,get(a,"readinessVoiceSampleStarted"))
            current.performClick()
            assertTrue(get(a,"readinessVoiceResult").toString().contains("Von dir"))
        }finally {d.dismiss();shadowOf(Looper.getMainLooper()).idle();tts.shutdown()}
    }
    @Test fun oldDialogButtonCannotConfirmANewGuideSample() {
        val(a,d)=open();val(tts,listener)=voice(a)
        try {
            started(a,listener);val old=checkNotNull(hearingButton(d))
            d.dismiss();shadowOf(Looper.getMainLooper()).idle();invoke(a,"showReadinessGuide")
            val next=ShadowAlertDialog.getLatestAlertDialog();assertNotSame(d,next)
            started(a,listener);old.performClick()
            assertFalse(get(a,"readinessVoiceResult").toString().contains("Von dir"))
            checkNotNull(hearingButton(next)).performClick()
            assertTrue(get(a,"readinessVoiceResult").toString().contains("Von dir"))
            next.dismiss();shadowOf(Looper.getMainLooper()).idle()
        }finally {d.dismiss();tts.shutdown()}
    }
}
