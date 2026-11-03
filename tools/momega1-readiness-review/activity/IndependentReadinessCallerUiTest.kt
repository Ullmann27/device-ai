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
@Config(sdk=[28],shadows=[IndependentReadinessCallerUiTest.ControlledTts::class])
class IndependentReadinessCallerUiTest {
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
    @Test fun permissionCallbacksDoNotStartOrRestoreAnySensor() {
        val(a,d)=open()
        try {
            for((name,permission) in listOf("READINESS_CAMERA_PERMISSION_REQUEST" to Manifest.permission.CAMERA,
                "READINESS_MIC_PERMISSION_REQUEST" to Manifest.permission.RECORD_AUDIO))
                a.onRequestPermissionsResult(code(name),arrayOf(permission),intArrayOf(PackageManager.PERMISSION_GRANTED))
            assertEquals(false,get(a,"readinessCameraEnabled"));assertEquals(false,get(a,"voiceListening"))
            assertNull(get(a,"activeLocalSpeechRequest"));assertNull(shadowOf(a.application).nextStartedService)
        }finally {d.dismiss()}
    }
    @Test fun successfulSpeakReturnAloneCannotEnableHeardConfirmation() {
        val(a,d)=open();val(tts,listener)=voice(a)
        try {
            invoke(a,"playReadinessVoiceSample")
            assertNotNull(get(a,"readinessVoiceSampleId"));assertEquals(false,get(a,"readinessVoiceSampleStarted"))
            invoke(a,"refreshReadinessGuide");assertNull(hearingButton(d))
            listener.onStart(get(a,"readinessVoiceSampleId") as String)
            assertNotNull(hearingButton(d))
        }finally {d.dismiss();tts.shutdown()}
    }
    @Test fun queuedHeardTapAfterTtsErrorCannotConfirmFailedTest() {
        val(a,d)=open();val(tts,listener)=voice(a)
        try {
            val id=started(a,listener);val old=checkNotNull(hearingButton(d))
            listener.onError(id,TextToSpeech.ERROR)
            assertEquals(false,get(a,"readinessVoiceSampleStarted"));assertNull(hearingButton(d))
            old.performClick()
            assertFalse("An old tap cannot certify the failed voice sample",get(a,"readinessVoiceResult").toString().contains("Von dir"))
        }finally {d.dismiss();tts.shutdown()}
    }
    @Test fun queuedHeardTapAfterDismissCannotConfirmClosedTest() {
        val(a,d)=open();val(tts,listener)=voice(a)
        try {
            started(a,listener);val old=checkNotNull(hearingButton(d));d.dismiss();old.performClick()
            assertNull(get(a,"readinessDialog"))
            assertFalse("A dismissed guide cannot accept an old confirmation",get(a,"readinessVoiceResult").toString().contains("Von dir"))
        }finally {d.dismiss();tts.shutdown()}
    }
    @Test fun oldHeardTapCannotConfirmAnUnstartedReplacementSample() {
        val(a,d)=open();val(tts,listener)=voice(a)
        try {
            val first=started(a,listener);val old=checkNotNull(hearingButton(d))
            invoke(a,"playReadinessVoiceSample")
            assertNotEquals(first,get(a,"readinessVoiceSampleId"));assertEquals(false,get(a,"readinessVoiceSampleStarted"))
            old.performClick()
            assertFalse("Replacement sample requires its own start and owner confirmation",get(a,"readinessVoiceResult").toString().contains("Von dir"))
        }finally {d.dismiss();tts.shutdown()}
    }
    @Test fun lateStartCallbackAfterDismissCannotReopenOrEnableConfirmation() {
        val(a,d)=open();val(tts,listener)=voice(a)
        try {
            invoke(a,"playReadinessVoiceSample");val id=get(a,"readinessVoiceSampleId") as String
            d.dismiss();listener.onStart(id);shadowOf(Looper.getMainLooper()).idle()
            assertNull(get(a,"readinessDialog"));assertEquals(false,get(a,"readinessVoiceSampleStarted"))
        }finally {d.dismiss();tts.shutdown()}
    }
    @Test fun pauseClearsTemporarySpeechTextWithoutChangingComposer() {
        val(a,d)=open()
        try {
            val input=EditText(a).apply {setText("unchanged composer")};set(a,"input",input)
            AgentActivity::class.java.getDeclaredMethod("applyLocalSpeechText",Int::class.javaPrimitiveType,String::class.java)
                .apply {isAccessible=true}.invoke(a,code("READINESS_SPEECH_REQUEST"),"synthetic private sample")
            assertTrue(get(a,"readinessSpeechResult").toString().contains("synthetic private sample"))
            invoke(a,"onPause")
            assertFalse(get(a,"readinessSpeechResult").toString().contains("synthetic private sample"))
            assertEquals("unchanged composer",input.text.toString());assertEquals(false,get(a,"readinessCameraEnabled"))
            assertNull(shadowOf(a.application).nextStartedService)
        }finally {d.dismiss()}
    }
    @Test fun rotationRevokesCameraOptInWithoutAutoResume() {
        val(a,d)=open()
        try {
            set(a,"readinessCameraEnabled",true)
            a.onConfigurationChanged(Configuration())
            assertEquals(false,get(a,"readinessCameraEnabled"));assertNull(shadowOf(a.application).nextStartedService)
        }finally {d.dismiss()}
    }
}
