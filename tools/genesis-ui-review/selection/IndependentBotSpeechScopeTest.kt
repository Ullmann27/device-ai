package at.heinz.deviceai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.speech.SpeechRecognizer
import android.widget.EditText
import android.widget.LinearLayout
import at.heinz.deviceai.chief.ChiefBotConversation
import at.heinz.deviceai.chief.ChiefBotProfile
import at.heinz.deviceai.chief.ChiefBotProfileStore
import at.heinz.deviceai.cognitive.ContinuumReasoningAdapter
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.cognitive.DevelopmentCoordinator
import at.heinz.deviceai.cognitive.ReasoningPassRequest
import at.heinz.deviceai.cognitive.ReasoningPassResult
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
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
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.util.concurrent.ExecutorService

/** Actual Activity + on-device ASR controller with Android shadow callbacks; no real audio/model. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class IndependentBotSpeechScopeTest {
    private lateinit var activity: AgentActivity
    private lateinit var input: EditText
    private lateinit var alpha: ChiefBotProfile
    private lateinit var beta: ChiefBotProfile
    private lateinit var runtime: RuntimeSpy

    private class RuntimeSpy : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel: ImportedLocalModel? = null
        var cancellations = 0
        var inferences = 0
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) { error("No model load permitted in speech contract") }
        override fun unload(callback: (Result<Unit>) -> Unit) { error("No model unload permitted in speech contract") }
        override fun stream(prompt: String, callback: LocalInferenceCallback) { inferences++; error("Dictation must not infer") }
        override fun cancel() { cancellations++ }
    }
    private fun field(name: String) = AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun get(name: String): Any? = field(name).get(activity)
    private fun set(name: String, value: Any?) { field(name).set(activity, value) }
    private fun call(name: String, vararg args: Any?): Any? = AgentActivity::class.java.declaredMethods.single {
        it.name == name && it.parameterCount == args.size
    }.apply { isAccessible = true }.invoke(activity, *args)
    private fun constant(name: String) = field(name).getInt(null)
    private fun main() { Shadows.shadowOf(Looper.getMainLooper()).idle() }
    private fun result(text: String) = Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) }
    private fun start(): SpeechRecognizer {
        call("startVoiceInput", constant("SPEECH_REQUEST")); main()
        return requireNotNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    @Before fun setup() {
        ShadowSpeechRecognizer.reset()
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        // Constructor + attached context only: no onCreate/native runtime/background bootstrap.
        activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        input = EditText(activity)
        set("input", input)
        set("chatContainer", LinearLayout(activity))
        set("foregroundLearningActive", true)
        runtime = RuntimeSpy(); set("localRuntime", runtime)
        val store = call("getBotProfiles") as ChiefBotProfileStore
        alpha = store.create("scope-alpha", "Scope Alpha", "Privater Kontext", "Sorgfältig")
        beta = store.create("scope-beta", "Scope Beta", "Anderer Kontext", "Sorgfältig")
        call("enterBotConversation", alpha)
    }

    @After fun cleanup() {
        call("cancelSpeechInputScope")
        call("stopVoiceAnimation", true)
        set("foregroundLearningActive", false)
        for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor")) {
            (get(name) as ExecutorService).shutdownNow()
        }
        main()
        java.io.File(activity.filesDir, "chief-bots").deleteRecursively()
    }

    private fun rejectLateResultAfter(change: () -> Unit) {
        input.setText("Bereits sichtbarer Entwurf")
        val old = start()
        val generation = get("speechScopeGeneration") as Long
        change()
        val expected = input.text.toString()
        Shadows.shadowOf(old).triggerOnResults(result("PRIVATE_ALPHA_LATE_TRANSCRIPT")); main()
        assertEquals("Old bot speech cannot enter the newly selected scope", expected, input.text.toString())
        assertTrue((get("speechScopeGeneration") as Long) > generation)
        assertTrue(Shadows.shadowOf(old).isDestroyed)
        assertEquals(false, get("voiceListening"))
        assertNull(get("activeLocalSpeechRequest"))
        assertEquals(0, runtime.inferences)
    }

    @Test fun botSwitchRejectsOldRecognizerResult() = rejectLateResultAfter { call("enterBotConversation", beta) }

    @Test fun returnToJarvisRejectsOldBotRecognizerResult() = rejectLateResultAfter { call("returnToJarvis") }

    @Test fun selectedContextEditRejectsOldRecognizerResult() = rejectLateResultAfter { call("botContextChanging", alpha.agentId) }

    @Test fun delayedPermissionGrantCannotStartMicrophoneForAnotherBot() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.RECORD_AUDIO)
        call("startVoiceInput", constant("SPEECH_REQUEST")); main()
        assertEquals(constant("SPEECH_REQUEST"), get("pendingLocalSpeechRequest"))
        assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        call("enterBotConversation", beta)
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        activity.onRequestPermissionsResult(constant("LOCAL_SPEECH_PERMISSION_REQUEST"),
            arrayOf(Manifest.permission.RECORD_AUDIO), intArrayOf(PackageManager.PERMISSION_GRANTED))
        main()
        assertNull("Stale owner permission must not activate another bot's microphone", ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        assertNull(get("pendingLocalSpeechRequest"))
        assertEquals(false, get("voiceListening"))
    }

    @Test fun microphoneDictationDoesNotCancelOrExecuteBoundDevelopmentJob() {
        var adapterCancels = 0
        var coordinatorRuntimeCancels = 0
        var modelCalls = 0
        val adapter = object : ContinuumReasoningAdapter {
            override fun execute(request: ReasoningPassRequest): ReasoningPassResult { modelCalls++; error("No coding inference permitted") }
            override fun cancel() { adapterCancels++ }
        }
        val job = DevelopmentCoordinator(adapter, { coordinatorRuntimeCancels++ })
        val cancellation = DeliberationCancellation()
        set("activeDevCoordinator", job)
        set("activeChatCancellation", cancellation)
        set("activeLocalRequest", true)
        set("busy", true)
        input.setText("")
        val recognizer = start()
        Shadows.shadowOf(recognizer).triggerOnResults(result("Nur ein neuer Entwurf")); main()
        assertEquals("Microphone activation cannot cancel a coding job", 0, adapterCancels)
        assertEquals(0, coordinatorRuntimeCancels)
        assertEquals(0, runtime.cancellations)
        assertEquals(0, modelCalls)
        assertEquals(0, runtime.inferences)
        assertSame(job, get("activeDevCoordinator"))
        assertEquals(true, get("busy"))
        cancellation.throwIfCancelled()
        assertEquals("Nur ein neuer Entwurf", input.text.toString())
    }
}
