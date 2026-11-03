package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import at.heinz.deviceai.ui.ContinuumTaskEffects
import at.heinz.deviceai.ui.ContinuumUiFeedback
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Actual Activity adapters and ToolGate. Injected device port, no physical device/model claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], shadows=[Pcm16FeedbackAudioTrackShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumTaskEffectsCallerUiTest {
    private fun set(a:AgentActivity,n:String,v:Any?)=AgentActivity::class.java.getDeclaredField(n).apply {isAccessible=true}.set(a,v)
    private fun get(a:AgentActivity,n:String)=AgentActivity::class.java.getDeclaredField(n).apply {isAccessible=true}.get(a)
    private fun call(a:AgentActivity,n:String,vararg args:Any?) = AgentActivity::class.java.declaredMethods
        .single {it.name==n && it.parameterCount==args.size}.apply {isAccessible=true}.invoke(a,*args)
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).apply {isAccessible=true}.get(o)
    private fun pulses(h:ContinuumHologramView)=field(field(h,"taskEffects")!!,"pulses") as List<*>
    private fun idle()=shadowOf(Looper.getMainLooper()).idle()
    private fun advance()=shadowOf(Looper.getMainLooper()).idleFor(20,TimeUnit.MILLISECONDS)
    private fun withFixture(block:(AgentActivity,ContinuumHologramView)->Unit) {
        val a=Robolectric.buildActivity(AgentActivity::class.java).get()
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val h=ContinuumHologramView(a)
        set(a,"hologramView",h);set(a,"foregroundLearningActive",true)
        set(a,"chatContainer",android.widget.LinearLayout(a))
        set(a,"receiptStore",FileToolExecutionReceiptStore(java.io.File(a.filesDir,"effects-"+UUID.randomUUID())))
        host.get().setContentView(h);advance()
        h.measure(View.MeasureSpec.makeMeasureSpec(412,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(307,View.MeasureSpec.EXACTLY))
        h.layout(0,0,412,307);h.setForegroundActive(true);advance()
        assertEquals("Positive control: the actual host window must be visible",View.VISIBLE,h.windowVisibility)
        try {block(a,h)} finally {
            (get(a,"chiefStatusPool") as? at.heinz.deviceai.chief.ChiefWorkerPool)?.shutdown()
            AgentActivity::class.java.declaredFields.firstOrNull {it.name=="uiFeedback"}?.apply {isAccessible=true}
                ?.get(a)?.let {(it as ContinuumUiFeedback).close()}
            h.setForegroundActive(false);host.pause().stop().destroy()
        }
    }
    @Test fun busyProposalAloneNeverAnimatesAnActualWorker() = withFixture {a,h ->
        call(a,"setBusy",true);advance()
        assertTrue(pulses(h).isEmpty())
    }
    @Test fun authorizedRealRunnerEntryAndExitReachTheVisibleSurface() = withFixture {a,h ->
        val started=CountDownLatch(1);val finish=AtomicReference<((Result<String>)->Unit)?>()
        set(a,"executor",DeviceToolExecutor(a,{true},{_,cb -> finish.set(cb);started.countDown()},null))
        call(a,"setBusy",true);advance()
        val gate=call(a,"createToolGate",null,null,({false} as ()->Boolean)) as ToolGate
        val result=AtomicReference<ToolExecutionResult>()
        val t=Thread {result.set(gate.execute(ToolProposal("get_device_info",emptyMap()).invocation("actual-device-read")))}
        t.start()
        try {
            assertTrue(started.await(2,TimeUnit.SECONDS));idle()
            assertTrue("A real authorized backend entry has no arrival",pulses(h).any {field(it!!,"type")==0})
            finish.get()!!(Result.success("synthetic injected device result"));t.join(2000);idle()
            assertFalse(t.isAlive);assertEquals(ToolExecutionStatus.SUCCEEDED,result.get().record.status)
            assertTrue("Actual runner exit has no departure",pulses(h).any {field(it!!,"type")==2})
            assertFalse("Device diagnostics are not a document read",pulses(h).any {field(it!!,"type")==1})
        } finally {finish.get()?.invoke(Result.failure(IllegalStateException("fixture cleanup")));t.join(2000);gate.cancel()}
    }
    @Test fun deniedToolHasNoBackendAndNoArrival() = withFixture {a,h ->
        var dispatched=false
        set(a,"executor",DeviceToolExecutor(a,{true},{_,cb -> dispatched=true;cb(Result.success("unexpected"))},null))
        call(a,"setBusy",true);advance()
        val gate=call(a,"createToolGate",null,null,({false} as ()->Boolean)) as ToolGate
        val result=gate.execute(ToolProposal("web_read",mapOf("url" to "https://example.org/")).invocation("denied-read"))
        idle();assertEquals(ToolExecutionStatus.REJECTED,result.record.status)
        assertFalse(dispatched);assertTrue(pulses(h).isEmpty())
    }
    @Test fun actualChiefStatusWorkerProducesAWorkerArrival() = withFixture {a,h ->
        call(a,"showChiefWorkerStatus")
        val pool=get(a,"chiefStatusPool") as at.heinz.deviceai.chief.ChiefWorkerPool
        assertTrue(pool.awaitIdle(2000));idle()
        assertTrue("Actual WorkerJob.run has no worker arrival",pulses(h).any {
            field(it!!,"type")==0 && field(it,"kind")==ContinuumTaskEffects.Kind.WORKER
        })
    }
    @Test fun actualActionButtonUsesBoundedFeedbackAndPendingSpeechMutesIt() = withFixture {a,_ ->
        Settings.System.putInt(a.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,1)
        (a.getSystemService(Context.AUDIO_SERVICE) as AudioManager).apply {
            ringerMode=AudioManager.RINGER_MODE_NORMAL;setStreamVolume(AudioManager.STREAM_SYSTEM,5,0)
        }
        set(a,"voiceEnabled",true)
        val b=call(a,"actionButton","synthetic action",false,false) as Button
        var clicks=0;b.setOnClickListener {clicks++}
        assertFalse("Avoid overlapping Android and local button sounds",b.isSoundEffectsEnabled)
        b.performClick();assertEquals(1,clicks)
        val f=AgentActivity::class.java.declaredFields.firstOrNull {it.name=="uiFeedback"}?.apply {isAccessible=true}?.get(a)
        assertNotNull("Actual action button never reaches bounded local feedback",f)
        assertEquals(AudioTrack.PLAYSTATE_PLAYING,(field(f!!,"track") as AudioTrack).playState)
        set(a,"voiceUtteranceId","pending-not-yet-onStart")
        call(a,"refreshHologramState")
        assertNull("Queued speech must release an existing chirp before onStart",field(f,"track"))
        b.performClick();assertEquals(2,clicks);assertNull(field(f,"track"))
    }
}
