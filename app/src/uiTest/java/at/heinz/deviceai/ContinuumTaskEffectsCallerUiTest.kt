package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import at.heinz.deviceai.ui.ContinuumTaskEffects
import at.heinz.deviceai.ui.ContinuumUiFeedback
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Actual Activity adapters and ToolGate. Injected device port, no physical device/model claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], shadows=[Pcm16FeedbackAudioTrackShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumTaskEffectsCallerUiTest {
    private var framePump: CockpitHostFramePump? = null
    @Before fun configureVirtualVsyncClock() {
        // Activity setup can start the real avatar loop before fixture code can pause it.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun advanceFrames(totalMillis: Long) {
        var remaining = totalMillis
        while (remaining > 0) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            framePump?.drawRequestedFrame()
            remaining -= step
        }
    }

    private fun set(a:AgentActivity,n:String,v:Any?)=AgentActivity::class.java.getDeclaredField(n).apply {isAccessible=true}.set(a,v)
    private fun get(a:AgentActivity,n:String)=AgentActivity::class.java.getDeclaredField(n).apply {isAccessible=true}.get(a)
    private fun call(a:AgentActivity,n:String,vararg args:Any?) = AgentActivity::class.java.declaredMethods
        .single {it.name==n && it.parameterCount==args.size}.apply {isAccessible=true}.invoke(a,*args)
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).apply {isAccessible=true}.get(o)
    private fun pulses(h:ContinuumHologramView)=field(field(h,"taskEffects")!!,"pulses") as List<*>
    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        framePump?.drawRequestedFrame()
    }
    private fun advance()=advanceFrames(20)
    private fun withFixture(realUi: Boolean = false, block:(AgentActivity,ContinuumHologramView)->Unit) {
        val controller=Robolectric.buildActivity(AgentActivity::class.java)
        val a=controller.get()
        val host = if (realUi) null else Robolectric.buildActivity(Activity::class.java).setup().visible()
        val h: ContinuumHologramView
        if (realUi) {
            a.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
                .putBoolean("voice_enabled", false).commit()
            a.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
                .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
            a.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
                .putBoolean("native-head", false).putBoolean("native-head-3d", false).commit()
            controller.setup().visible()
            val root = a.window.decorView
            framePump = CockpitHostFramePump { root }
            root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 884, 760)
            advanceFrames(32L)
            controller.windowFocusChanged(true)
            h = get(a, "hologramView") as ContinuumHologramView
        } else {
            h = ContinuumHologramView(a).apply { setNativeHeadEnabled(false) }
            set(a,"hologramView",h);set(a,"foregroundLearningActive",true)
            set(a,"chatContainer",android.widget.LinearLayout(a))
            host!!.get().setContentView(h,ViewGroup.LayoutParams(412,307))
            framePump = CockpitHostFramePump { host.get().window.decorView }
            advance()
            assertEquals(412,h.width);assertEquals(307,h.height)
        }
        set(a,"receiptStore",FileToolExecutionReceiptStore(java.io.File(a.filesDir,"effects-"+UUID.randomUUID())))
        assertTrue("Caller fixture must be attached to its real host window",h.isAttachedToWindow)
        assertTrue("Caller fixture must be shown",h.isShown)
        assertTrue("Caller surface must be measured", h.width > 0 && h.height > 0)
        val before = h.sampleMotion()
        h.setForegroundActive(true);advanceFrames(200)
        val active = h.sampleMotion()
        assertTrue("Caller events require actual frame callbacks",active.callbackCount > before.callbackCount)
        assertTrue("Caller events require actual phase progress",active.phase > before.phase)
        assertTrue("Caller events require an actual host Canvas draw",active.drawCount > before.drawCount)
        assertEquals("Positive control: the actual host window must be visible",View.VISIBLE,h.windowVisibility)
        try {block(a,h)} finally {
            (get(a,"chiefStatusPool") as? at.heinz.deviceai.chief.ChiefWorkerPool)?.shutdown()
            AgentActivity::class.java.declaredFields.firstOrNull {it.name=="uiFeedback"}?.apply {isAccessible=true}
                ?.get(a)?.let {(it as ContinuumUiFeedback).close()}
            framePump?.close(); framePump = null
            h.setForegroundActive(false);host?.pause()?.stop()?.destroy()
            if (realUi) controller.close()
        }
    }
    @Test fun busyProposalAloneNeverAnimatesAnActualWorker() = withFixture {a,h ->
        call(a,"setBusy",true);advance()
        assertTrue(pulses(h).isEmpty())
    }
    @Test fun authorizedRealRunnerEntryAndExitReachTheVisibleSurface() = withFixture(realUi = true) {a,h ->
        val started=CountDownLatch(1);val finish=AtomicReference<((Result<String>)->Unit)?>()
        set(a,"executor",DeviceToolExecutor(a,{true},{_,cb -> finish.set(cb);started.countDown()},null))
        call(a,"setBusy",true);advance()
        val gate=call(a,"createToolGate",null,null,({false} as ()->Boolean),null,null) as ToolGate
        val result=AtomicReference<ToolExecutionResult>()
        val t=Thread {result.set(gate.execute(ToolProposal("list_processes",emptyMap()).invocation("actual-device-read")))}
        t.start()
        try {
            assertTrue("Bridge tool never started: ${result.get()}",started.await(2,TimeUnit.SECONDS));advanceFrames(16)
            assertTrue("A real authorized backend entry has no arrival",pulses(h).any {field(it!!,"type")==0})
            finish.get()!!(Result.success("synthetic injected device result"));t.join(2000);advanceFrames(16)
            assertFalse(t.isAlive);assertEquals(ToolExecutionStatus.SUCCEEDED,result.get().record.status)
            assertTrue("Actual runner exit has no departure",pulses(h).any {field(it!!,"type")==2})
            assertFalse("Device diagnostics are not a document read",pulses(h).any {field(it!!,"type")==1})
        } finally {finish.get()?.invoke(Result.failure(IllegalStateException("fixture cleanup")));t.join(2000);gate.cancel()}
    }
    @Test fun deniedToolHasNoBackendAndNoArrival() = withFixture {a,h ->
        var dispatched=false
        set(a,"executor",DeviceToolExecutor(a,{true},{_,cb -> dispatched=true;cb(Result.success("unexpected"))},null))
        call(a,"setBusy",true);advance()
        val gate=call(a,"createToolGate",null,null,({false} as ()->Boolean),null,null) as ToolGate
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
