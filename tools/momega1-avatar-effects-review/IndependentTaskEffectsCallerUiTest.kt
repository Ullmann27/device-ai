package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import at.heinz.deviceai.chief.ChiefWorkerPool
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
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Actual Activity ToolGate/WorkerJob/button callers. Shared short-JNI shadow is explicitly controlled host audio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],shadows=[Pcm16FeedbackAudioTrackShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndependentTaskEffectsCallerUiTest {
    private fun set(a:AgentActivity,n:String,v:Any?)=AgentActivity::class.java.getDeclaredField(n).apply {isAccessible=true}.set(a,v)
    private fun get(a:AgentActivity,n:String)=AgentActivity::class.java.getDeclaredField(n).apply {isAccessible=true}.get(a)
    private fun call(a:AgentActivity,n:String,vararg args:Any?)=AgentActivity::class.java.declaredMethods.single {it.name==n&&it.parameterCount==args.size}.apply {isAccessible=true}.invoke(a,*args)
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).apply {isAccessible=true}.get(o)
    private fun feedback(a:AgentActivity)=AgentActivity::class.java.declaredFields.firstOrNull {it.name=="uiFeedback"}?.apply {isAccessible=true}?.get(a)
    private fun assertSilent(a:AgentActivity) {feedback(a)?.let {assertNull(field(it,"track"))}}
    private fun pulses(h:ContinuumHologramView)=field(field(h,"taskEffects")!!,"pulses") as List<*>
    private fun starts(h:ContinuumHologramView)=pulses(h).filter {field(it!!,"type")==0}.map {field(it!!,"id").toString()}.toSet()
    private fun idle()=shadowOf(Looper.getMainLooper()).idle()
    private fun tick(ms:Long=5) {ShadowSystemClock.advanceBy(Duration.ofMillis(ms));idle()}
    private fun fixture(body:(AgentActivity,ContinuumHologramView)->Unit) {
        val a=Robolectric.buildActivity(AgentActivity::class.java).get()
        val host=Robolectric.buildActivity(Activity::class.java).setup()
        // The view uses its real host's Context; app caller methods are injected with this production view.
        val h=ContinuumHologramView(host.get())
        set(a,"hologramView",h);set(a,"foregroundLearningActive",true);set(a,"chatContainer",LinearLayout(a))
        set(a,"input",EditText(a));set(a,"bootstrapMemory",BootstrapMemoryStore(a))
        set(a,"receiptStore",FileToolExecutionReceiptStore(java.io.File(a.filesDir,"independent-effects-"+UUID.randomUUID())))
        host.get().setContentView(h);host.visible().windowFocusChanged(true);tick()
        h.measure(View.MeasureSpec.makeMeasureSpec(412,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(307,View.MeasureSpec.EXACTLY));h.layout(0,0,412,307)
        h.setForegroundActive(true);tick()
        try {
            assertTrue("Fixture positive control: actual production view must be attached",h.isAttachedToWindow)
            assertEquals("Fixture positive control: real host window must be visible",View.VISIBLE,h.windowVisibility)
            assertTrue("Fixture positive control: real view must be shown",h.isShown)
            body(a,h)
        }finally {
            (get(a,"chiefStatusPool") as? ChiefWorkerPool)?.shutdown()
            AgentActivity::class.java.declaredFields.firstOrNull {it.name=="uiFeedback"}?.apply {isAccessible=true}?.get(a)?.let {(it as ContinuumUiFeedback).close()}
            h.setForegroundActive(false);host.pause().stop().destroy()
        }
    }
    private fun gate(a:AgentActivity,ephemeral:Boolean=false)=call(a,"createToolGate",null,null,({ephemeral} as ()->Boolean)) as ToolGate
    private fun request()=ToolProposal("get_device_info",emptyMap()).invocation("review-"+UUID.randomUUID())
    private fun running(a:AgentActivity,g:ToolGate,body:(()->Unit)->Unit) {
        val entered=CountDownLatch(1);val callback=AtomicReference<((Result<String>)->Unit)?>()
        set(a,"executor",DeviceToolExecutor(a,{true},{_,cb->callback.set(cb);entered.countDown()},null))
        val result=AtomicReference<ToolExecutionResult>();val t=Thread {result.set(g.execute(request()))}.apply {isDaemon=true;start()}
        try {
            assertTrue("Real injected backend must be entered",entered.await(2,TimeUnit.SECONDS));idle()
            body {callback.get()!!(Result.success("synthetic owner-authorized device result"));t.join(2000);idle();assertFalse(t.isAlive)}
        }finally {callback.get()?.invoke(Result.failure(IllegalStateException("review cleanup")));g.cancel();t.join(2000)}
    }
    @Test fun recordedProposalAndBusyDoNotRepresentExecution()=fixture {a,h ->
        var executed=false;set(a,"executor",DeviceToolExecutor(a,{true},{_,cb->executed=true;cb(Result.success("unexpected"))},null))
        call(a,"setBusy",true);tick();gate(a).recordProposal(request());idle()
        assertFalse(executed);assertTrue(pulses(h).isEmpty())
    }
    @Test fun innerEphemeralContextRejectionDoesNotInventAdmittedToolActivity()=fixture {a,h ->
        var executed=false;set(a,"executor",DeviceToolExecutor(a,{true},{_,cb->executed=true;cb(Result.success("unexpected"))},null))
        call(a,"setBusy",true);tick();val result=gate(a,ephemeral=true).execute(request());idle()
        assertNotEquals(ToolExecutionStatus.SUCCEEDED,result.record.status)
        assertFalse(executed);assertTrue("Inner screen-context rejection precedes actual work",pulses(h).isEmpty())
    }
    @Test fun gateCreatedForOldTurnCannotBorrowNewTurnScopeAtRunnerEntry()=fixture {a,h ->
        call(a,"setBusy",true);tick();val old=gate(a)
        call(a,"setBusy",false);set(a,"sessionId",UUID.randomUUID().toString());call(a,"setBusy",true);tick()
        running(a,old) {finish ->assertTrue("Old gate entry is not new-turn activity",pulses(h).isEmpty());finish();assertTrue(pulses(h).isEmpty())}
    }
    @Test fun delayedRealExitCannotRenderIntoReplacementTurn()=fixture {a,h ->
        call(a,"setBusy",true);tick()
        running(a,gate(a)) {finish ->
            assertTrue(starts(h).isNotEmpty())
            call(a,"setBusy",false);set(a,"sessionId",UUID.randomUUID().toString());call(a,"setBusy",true);tick()
            assertTrue(pulses(h).isEmpty());finish()
            assertTrue("Old completion cannot decorate replacement context",pulses(h).isEmpty())
        }
    }
    @Test fun actualCancelPathRevokesScopeBeforeLateBackendCompletion()=fixture {a,h ->
        call(a,"setBusy",true);tick();val g=gate(a);set(a,"activeBootstrapGate",g)
        running(a,g) {finish ->
            assertTrue(starts(h).isNotEmpty());call(a,"cancelLocalRequest");idle()
            assertTrue("Owner cancel must synchronously clear current effects",pulses(h).isEmpty())
            finish();assertTrue(pulses(h).isEmpty())
        }
    }
    @Test fun actualChiefJobIsSilentWhileQueuedAndUsesRunTimeAfterLongQueue()=fixture {a,h ->
        call(a,"showChiefWorkerStatus");val pool=get(a,"chiefStatusPool") as ChiefWorkerPool
        assertTrue(pool.awaitIdle(2000));idle();val oldIds=starts(h);assertTrue(oldIds.isNotEmpty())
        val workers=field(pool,"workers") as ExecutorService
        val occupied=CountDownLatch(1);val release=CountDownLatch(1)
        val blocker=workers.submit {occupied.countDown();release.await(3,TimeUnit.SECONDS)}
        try {
            assertTrue(occupied.await(2,TimeUnit.SECONDS));call(a,"showChiefWorkerStatus");idle()
            assertTrue("Accepted queued job is not a running worker",starts(h).all {it in oldIds})
            tick(800);assertTrue(starts(h).all {it in oldIds})
            release.countDown();blocker.get(2,TimeUnit.SECONDS);assertTrue(pool.awaitIdle(2000));idle()
            assertTrue("Actual run after queue must use fresh run timestamp",starts(h).any {it !in oldIds})
            assertTrue(pulses(h).any {field(it!!,"kind")==ContinuumTaskEffects.Kind.WORKER&&field(it,"type")==2})
        }finally {release.countDown();blocker.cancel(true)}
    }
    @Test fun microphoneAndSavedMuteBlockActualButtonFeedback()=fixture {a,_ ->
        Settings.System.putInt(a.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,1)
        (a.getSystemService(Context.AUDIO_SERVICE) as AudioManager).apply {ringerMode=AudioManager.RINGER_MODE_NORMAL;setStreamVolume(AudioManager.STREAM_SYSTEM,5,0)}
        val b=call(a,"actionButton","review action",false,false) as Button;b.setOnClickListener {}
        set(a,"voiceEnabled",true);set(a,"voiceListening",true);b.performClick()
        assertSilent(a)
        set(a,"voiceListening",false);set(a,"voiceEnabled",false);b.performClick();assertSilent(a)
    }
    @Test fun actualPauseReleasesButtonTrackAndBackgroundCannotReviveIt()=fixture {a,_ ->
        Settings.System.putInt(a.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,1)
        (a.getSystemService(Context.AUDIO_SERVICE) as AudioManager).apply {ringerMode=AudioManager.RINGER_MODE_NORMAL;setStreamVolume(AudioManager.STREAM_SYSTEM,5,0)}
        set(a,"voiceEnabled",true);val b=call(a,"actionButton","review action",false,false) as Button;b.setOnClickListener {}
        b.performClick();val f=feedback(a);assertNotNull("Actual button must reach bounded feedback",f);val first=field(f!!,"track") as AudioTrack
        assertEquals(AudioTrack.PLAYSTATE_PLAYING,first.playState)
        call(a,"onPause");assertNull(field(f,"track"));assertEquals(AudioTrack.STATE_UNINITIALIZED,first.state)
        tick(200);b.performClick();assertNull(field(f,"track"))
    }
}
