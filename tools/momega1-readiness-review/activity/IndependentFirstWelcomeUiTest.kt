package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

/** Actual onResume and compiled onCreate TTS callback body, without unrelated full onCreate startup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],shadows=[IndependentFirstWelcomeUiTest.ControlledTts::class])
class IndependentFirstWelcomeUiTest {
    @Implements(TextToSpeech::class)
    class ControlledTts:ShadowTextToSpeech() {
        @Implementation override fun speak(text:CharSequence,queueMode:Int,params:Bundle?,utteranceId:String):Int {
            spoken.add(text.toString());return TextToSpeech.SUCCESS
        }
        companion object {val spoken=mutableListOf<String>()}
    }
    @Before fun reset() {ControlledTts.spoken.clear()}
    private fun set(a:AgentActivity,name:String,value:Any?)=AgentActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.set(a,value)
    private fun get(a:AgentActivity,name:String)=AgentActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.get(a)
    private fun invoke(a:AgentActivity,name:String)=AgentActivity::class.java.getDeclaredMethod(name).apply {isAccessible=true}.invoke(a)
    private fun fixture(muted:Boolean=false,network:Boolean=false):Pair<AgentActivity,TextToSpeech> {
        val a=Robolectric.buildActivity(AgentActivity::class.java).get()
        val memory=BootstrapMemoryStore(a)
        if(muted)memory.voiceEnabled=false
        set(a,"bootstrapMemory",memory);set(a,"voiceEnabled",memory.voiceEnabled)
        ShadowTextToSpeech.addLanguageAvailability(Locale.GERMAN)
        val v=Voice("independent-${if(network)"network" else "offline"}",Locale.GERMAN,Voice.QUALITY_NORMAL,Voice.LATENCY_NORMAL,network,emptySet())
        ShadowTextToSpeech.addVoice(v)
        val tts=TextToSpeech(a){};tts.voice=v;set(a,"voiceEngine",tts)
        return a to tts
    }
    private fun initialized(a:AgentActivity,recreated:Boolean=false) {
        // Invoke precisely the generated body wired to TextToSpeech.OnInitListener in onCreate.
        val method=AgentActivity::class.java.declaredMethods.single {m ->
            m.name.startsWith("onCreate\$lambda\$") && m.parameterTypes.firstOrNull()==AgentActivity::class.java &&
                m.parameterTypes.lastOrNull()==Int::class.javaPrimitiveType &&
                (m.parameterTypes.size==2 || (m.parameterTypes.size==3 && m.parameterTypes[1]==Bundle::class.java))
        }.apply {isAccessible=true}
        if(method.parameterTypes.size==2)method.invoke(null,a,TextToSpeech.SUCCESS)
        else method.invoke(null,a,if(recreated)Bundle() else null,TextToSpeech.SUCCESS)
    }
    private fun close(a:AgentActivity,tts:TextToSpeech) {
        invoke(a,"onPause");(get(a,"readinessDialog") as? AlertDialog)?.dismiss()
        shadowOf(Looper.getMainLooper()).idle();tts.shutdown()
    }
    @Test fun firstResumeWaitsForActualOfflineInitializationAndStartCallback() {
        val(a,tts)=fixture()
        try {
            invoke(a,"onResume");assertTrue((get(a,"readinessDialog") as AlertDialog).isShowing)
            assertTrue(ControlledTts.spoken.isEmpty());initialized(a)
            assertEquals(1,ControlledTts.spoken.size);assertEquals(false,get(a,"readinessVoiceSampleStarted"))
            shadowOf(tts).utteranceProgressListener!!.onStart(get(a,"readinessVoiceSampleId") as String)
            assertEquals(true,get(a,"readinessVoiceSampleStarted"))
            assertEquals(false,get(a,"readinessCameraEnabled"));assertEquals(false,get(a,"voiceListening"))
            assertNull(shadowOf(a.application).nextStartedService)
        }finally {close(a,tts)}
    }
    @Test fun savedMuteAndFactsSurviveLateInitialization() {
        val(a,tts)=fixture(muted=true)
        try {
            val memory=BootstrapMemoryStore(a);memory.addFact("synthetic reviewer fact")
            invoke(a,"onResume");initialized(a);invoke(a,"onResume");initialized(a)
            assertTrue(ControlledTts.spoken.isEmpty());assertFalse(memory.voiceEnabled)
            assertEquals(listOf("synthetic reviewer fact"),memory.facts())
        }finally {close(a,tts)}
    }
    @Test fun duplicateInitializationAndRepeatedResumeCannotReplayWelcome() {
        val(a,tts)=fixture()
        try {
            invoke(a,"onResume");initialized(a);assertEquals(1,ControlledTts.spoken.size)
            initialized(a);invoke(a,"onResume");initialized(a)
            assertEquals("Duplicate init cannot fall through into a second generic greeting",1,ControlledTts.spoken.size)
        }finally {close(a,tts)}
    }
    @Test fun initializationAfterDismissCannotSpeakAGenericFallback() {
        val(a,tts)=fixture()
        try {
            invoke(a,"onResume");(get(a,"readinessDialog") as AlertDialog).dismiss();shadowOf(Looper.getMainLooper()).idle()
            initialized(a)
            assertTrue("Dismissed welcome cannot restart through generic init greeting",ControlledTts.spoken.isEmpty())
        }finally {close(a,tts)}
    }
    @Test fun pausedThenResumedWelcomeDoesNotRestartOnLateInitialization() {
        val(a,tts)=fixture()
        try {
            invoke(a,"onResume");invoke(a,"onPause");invoke(a,"onResume");initialized(a)
            assertTrue("Foreground return does not revive revoked pending welcome",ControlledTts.spoken.isEmpty())
        }finally {close(a,tts)}
    }
    @Test fun lateBackgroundInitializationCannotSpeak() {
        val(a,tts)=fixture()
        try {
            invoke(a,"onResume");invoke(a,"onPause");initialized(a)
            assertTrue(ControlledTts.spoken.isEmpty());assertEquals(false,get(a,"readinessVoiceSampleStarted"))
        }finally {close(a,tts)}
    }
    @Test fun recreatedActivityKeepsIntroducedStateWithoutRepeatingWelcome() {
        val(a,tts)=fixture()
        invoke(a,"onResume")
        assertTrue(a.getSharedPreferences("continuum-readiness",Context.MODE_PRIVATE).getBoolean("introduced",false))
        close(a,tts)
        val(b,next)=fixture()
        try {
            invoke(b,"onResume");initialized(b,recreated=true)
            assertNull(get(b,"readinessDialog"));assertTrue(ControlledTts.spoken.isEmpty())
        }finally {close(b,next)}
    }
    @Test fun networkVoiceCannotProduceOfflineWelcome() {
        val(a,tts)=fixture(network=true)
        try {
            invoke(a,"onResume");initialized(a)
            assertTrue(ControlledTts.spoken.isEmpty());assertEquals(false,get(a,"voiceReady"))
            assertEquals(false,get(a,"readinessVoiceSampleStarted"))
        }finally {close(a,tts)}
    }
}
