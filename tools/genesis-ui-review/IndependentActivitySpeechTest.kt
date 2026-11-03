package at.heinz.deviceai

import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarEvent
import at.heinz.deviceai.avatar.AvatarSink
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

/** Actual Activity listener, Android TTS shadow only. No onCreate, model, device or PCM claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class IndependentActivitySpeechTest {
    private lateinit var activity: AgentActivity
    private lateinit var engine: TextToSpeech
    private lateinit var callback: UtteranceProgressListener
    private lateinit var signal: TtsHologramSignal
    private lateinit var director: AvatarDirector
    private val events = CopyOnWriteArrayList<AvatarEvent>()

    private fun field(name: String) = AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun set(name: String, value: Any?) { field(name).set(activity, value) }
    private fun call(name: String) { AgentActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity) }
    private fun main() { Shadows.shadowOf(Looper.getMainLooper()).idle() }
    private fun active(id: String, text: String) {
        set("voiceUtteranceId", id); set("voiceAvatarText", text)
        signal.activate(id)
    }

    @Before fun setup() {
        // Deliberately do not create/resume the screen: those start unrelated app services.
        activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        engine = TextToSpeech(RuntimeEnvironment.getApplication()) { }
        set("voiceEngine", engine)
        set("foregroundLearningActive", true)
        signal = field("voiceSignal").get(activity) as TtsHologramSignal
        director = field("avatarDirector").get(activity) as AvatarDirector
        director.attach(AvatarSink { events += it })
        events.clear()
        call("installVoiceProgressListener")
        callback = requireNotNull(Shadows.shadowOf(engine).utteranceProgressListener)
    }

    @After fun cleanup() {
        call("stopVoiceAnimation")
        set("foregroundLearningActive", false)
        engine.shutdown()
        for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor")) {
            (field(name).get(activity) as ExecutorService).shutdownNow()
        }
        main()
    }

    @Test fun normalStopRejectsLateStartRangeAudioAndDone() {
        active("accepted-A", "Private Antwort A")
        callback.onStart("accepted-A"); main()
        assertEquals(1, events.filterIsInstance<AvatarEvent.SpeechStarted>().size)
        call("stopVoiceAnimation")
        val afterStop = events.size
        callback.onStart("accepted-A")
        callback.onRangeStart("accepted-A", 0, 7, 0)
        callback.onAudioAvailable("accepted-A", byteArrayOf(127, 127))
        callback.onDone("accepted-A"); main()
        assertEquals(afterStop, events.size)
        assertNull(field("voiceUtteranceId").get(activity))
        assertEquals(false, field("voiceSpeaking").get(activity))
    }

    @Test fun oldCompletionCannotEndNewSpeechOrChangeItsBoundText() {
        active("accepted-A", "Antwort A")
        callback.onStart("accepted-A"); main()
        call("stopVoiceAnimation")
        active("accepted-B", "Antwort B")
        callback.onStart("accepted-B"); main()
        val afterB = events.size
        callback.onStop("accepted-A", true)
        callback.onDone("accepted-A")
        callback.onRangeStart("accepted-A", 0, 7, 0)
        main()
        assertEquals(afterB, events.size)
        assertEquals("accepted-B", field("voiceUtteranceId").get(activity))
        assertEquals("Antwort B", events.filterIsInstance<AvatarEvent.SpeechStarted>().last().text)
        assertEquals(true, field("voiceSpeaking").get(activity))
    }

    @Test fun startInterruptedAfterInitialIdentityCheckCannotReviveAvatarOrReplayOnAttach() {
        active("accepted-A", "Unterbrochene Antwort A")
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { callback.onStart("accepted-A") } catch (error: Throwable) { failure.set(error) }
        }
        var afterStop = -1
        synchronized(signal) {
            worker.start()
            // Baseline blocks at anchor after its initial ID check. A serialized fix may
            // instead post immediately and terminate. Both schedules must survive stop.
            val deadline = System.nanoTime() + 2_000_000_000L
            while (worker.isAlive && worker.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                LockSupport.parkNanos(1_000_000L)
            }
            assertTrue("Callback must reach its anchor or finish queuing", !worker.isAlive || worker.state == Thread.State.BLOCKED)
            call("stopVoiceAnimation")
            afterStop = events.size
        }
        worker.join(2_000)
        assertFalse("Callback must terminate", worker.isAlive)
        failure.get()?.let { throw AssertionError("Actual listener callback crashed", it) }
        main()
        assertTrue("Interrupted speech must not emit a new start after stop",
            events.drop(afterStop).none { it is AvatarEvent.SpeechStarted })
        val replay = mutableListOf<AvatarEvent>()
        director.attach(AvatarSink { replay += it })
        assertTrue("A replacement avatar sink must not replay interrupted speech",
            replay.none { it is AvatarEvent.SpeechStarted })
        assertNull(field("voiceUtteranceId").get(activity))
        assertEquals(false, field("voiceSpeaking").get(activity))
    }
}
