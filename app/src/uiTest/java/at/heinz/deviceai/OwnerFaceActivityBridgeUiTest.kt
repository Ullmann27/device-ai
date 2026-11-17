package at.heinz.deviceai

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.View
import android.widget.Button
import at.heinz.deviceai.cognitive.CameraTrackingState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import javax.crypto.spec.SecretKeySpec

/** Synthetic callbacks; no camera capture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerFaceActivityBridgeUiTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var activity: AgentActivity
    private lateinit var face: AndroidOwnerFaceRecognition
    private lateinit var speech: TextToSpeech
    private lateinit var worker: ManualWorker
    private lateinit var stop: Button
    private var callbacks = 0

    private class ManualWorker : AbstractExecutorService() {
        private val pending = ArrayDeque<Runnable>()
        private var closed = false
        override fun execute(command: Runnable) { check(!closed); pending.add(command) }
        fun drain() {
            var count = 0
            while (pending.isNotEmpty()) {
                check(++count <= 20)
                pending.removeFirst().run()
            }
        }
        override fun shutdown() { closed = true }
        override fun shutdownNow(): MutableList<Runnable> {
            closed = true
            return pending.toMutableList().also { pending.clear() }
        }
        override fun isShutdown() = closed
        override fun isTerminated() = closed && pending.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }

    private fun set(target: Any, name: String, value: Any?) =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    private fun read(name: String): Any? = AgentActivity::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(activity)
    private fun call(name: String): Any? = AgentActivity::class.java.getDeclaredMethod(name)
        .apply { isAccessible = true }.invoke(activity)
    private fun deliver(state: OwnerFaceStatus) = AgentActivity::class.java
        .getDeclaredMethod("onOwnerFaceState", OwnerFaceStatus::class.java)
        .apply { isAccessible = true }.invoke(activity, state)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before fun setup() {
        activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        set(activity, "foregroundLearningActive", true)
        shadowOf(activity.application).denyPermissions(Manifest.permission.CAMERA)
        worker = ManualWorker()
        val store = OwnerFaceProfileStore(temporary.newFolder("face"),
            { SecretKeySpec(ByteArray(32) { 11 }, "AES") })
        val profile = OwnerFaceProfile("Testperson", 1_700_000_000_000L,
            OwnerFaceModelContract.RECOGNIZER_SHA256, List(OwnerFacePolicy.ENROLLMENT_SAMPLES) {
                FloatArray(OwnerFaceModelContract.FEATURE_SIZE).apply { this[0] = 1f }
            })
        try { store.save(profile) } finally { profile.clear() }
        face = AndroidOwnerFaceRecognition(store, { error("No inference in UI bridge tests") }, worker,
            Handler(Looper.getMainLooper()), { 1_000L }, { 1_700_000_000_000L }) {
            callbacks++
            deliver(it)
        }
        set(activity, "ownerFace", face)
        worker.drain()
        idle()
        speech = TextToSpeech(activity) {}
        speech.setVoice(Voice("de-local", Locale.GERMANY, Voice.QUALITY_NORMAL,
            Voice.LATENCY_NORMAL, false, emptySet()))
        set(activity, "voiceEngine", speech)
        set(activity, "voiceEnabled", true)
        set(activity, "voiceReady", true)
        stop = call("buildCameraSessionStopControl") as Button
        set(activity, "cameraSessionStopButton", stop)
    }

    @After fun cleanup() {
        set(activity, "foregroundLearningActive", false)
        call("stopReadinessCamera")
        face.close()
        worker.drain()
        speech.shutdown()
        for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor")) {
            (read(name) as ExecutorService).shutdownNow()
        }
        idle()
    }

    private fun comparison(phase: OwnerFacePhase = OwnerFacePhase.MATCH): OwnerFaceStatus {
        assertTrue(face.beginRecognition(ownerConsent = true))
        val token = face.sessionToken
        assertTrue(token > 0)
        val state = OwnerFaceStatus(phase, hasProfile = true, displayName = "Testperson",
            sessionActive = true, message = "Synthetischer UI-Callback")
        set(face, "status", state)
        set(activity, "ownerFaceCameraToken", token)
        set(activity, "readinessCameraEnabled", true)
        (read("ownerFaceConversation") as OwnerFaceConversation).beginComparison(token)
        return state
    }

    @Test fun permissionReturnCannotStartEnrollmentOrCamera() {
        val start = AgentActivity::class.java.getDeclaredMethod("startOwnerFaceCamera", String::class.java)
            .apply { isAccessible = true }.invoke(activity, "Testperson")
        assertEquals(false, start)
        shadowOf(activity.application).grantPermissions(Manifest.permission.CAMERA)
        val code = AgentActivity::class.java.getDeclaredField("READINESS_CAMERA_PERMISSION_REQUEST")
            .apply { isAccessible = true }.getInt(null)
        activity.onRequestPermissionsResult(code, arrayOf(Manifest.permission.CAMERA),
            intArrayOf(PackageManager.PERMISSION_GRANTED))
        idle()
        assertFalse(face.status.sessionActive)
        assertEquals(false, read("readinessCameraEnabled"))
        assertEquals(CameraTrackingState.OFF, (read("readinessCamera") as AndroidAvatarCameraInput).state)
        assertTrue(shadowOf(speech).spokenTextList.isEmpty())
    }

    @Test fun stopEndsTheGreetingAndRejectsRepeatedOrDelayedMatches() {
        val match = comparison()
        deliver(match)
        assertEquals(1, shadowOf(speech).spokenTextList.size)
        assertNotNull(read("ownerFaceGreetingUtteranceId"))
        assertTrue(stop.text.toString().contains("Vorlage passt: Testperson"))
        deliver(match)
        assertEquals(1, shadowOf(speech).spokenTextList.size)
        stop.performClick()
        idle()
        deliver(match)
        assertNull(read("ownerFaceGreetingUtteranceId"))
        assertNull(read("voiceUtteranceId"))
        assertTrue(shadowOf(speech).isStopped)
        assertEquals(View.GONE, stop.visibility)
        assertFalse(stop.text.toString().contains("Testperson"))
        assertEquals(1, shadowOf(speech).spokenTextList.size)
    }

    @Test fun matchExpiryEndsItsGreetingAndClearsTheVisibleClaim() {
        deliver(comparison())
        val expired = face.status.copy(phase = OwnerFacePhase.NO_FACE, message = "Treffer abgelaufen")
        set(face, "status", expired)
        deliver(expired)
        assertNull(read("voiceUtteranceId"))
        assertTrue(shadowOf(speech).isStopped)
        assertTrue(stop.text.toString().contains("Kein Gesicht im Bild"))
        assertFalse(stop.text.toString().contains("Testperson"))
        assertEquals(View.VISIBLE, stop.visibility)
    }

    @Test fun pauseRejectsAMatchDeliveredAfterReturningToForeground() {
        val match = comparison()
        call("onPause")
        idle()
        set(activity, "foregroundLearningActive", true)
        deliver(match)
        assertTrue(shadowOf(speech).spokenTextList.isEmpty())
        assertNull(read("voiceUtteranceId"))
        assertFalse(face.status.sessionActive)
        assertEquals(View.GONE, stop.visibility)
    }

    @Test fun reflowEndsGreetingAndRejectsOldMatches() {
        val match = comparison()
        deliver(match)
        activity.onConfigurationChanged(Configuration(activity.resources.configuration))
        idle()
        deliver(match)
        assertNull(read("voiceUtteranceId"))
        assertTrue(shadowOf(speech).isStopped)
        assertFalse(face.status.sessionActive)
        assertEquals(View.GONE, stop.visibility)
        assertEquals(1, shadowOf(speech).spokenTextList.size)
    }

    @Test fun terminalControllerCallbackStopsCameraWithoutCallbackRecursion() {
        comparison(OwnerFacePhase.RECOGNIZING)
        val before = callbacks
        face.stop()
        idle()
        assertEquals(before + 1, callbacks)
        assertEquals(false, read("readinessCameraEnabled"))
        assertNull(read("ownerFaceCameraToken"))
        assertEquals(OwnerFacePhase.OFF, face.status.phase)
        call("stopReadinessCamera")
        idle()
        assertEquals(before + 1, callbacks)
    }

    @Test fun cameraStopLeavesUnrelatedConversationSpeechAlone() {
        val match = comparison()
        AgentActivity::class.java.getDeclaredMethod("speakBootstrap", String::class.java)
            .apply { isAccessible = true }.invoke(activity, "Deine Antwort läuft.")
        val utterance = read("voiceUtteranceId")
        assertNotNull(utterance)
        deliver(match)
        assertNull(read("ownerFaceGreetingUtteranceId"))
        call("stopReadinessCamera")
        idle()
        assertEquals(utterance, read("voiceUtteranceId"))
        assertFalse(shadowOf(speech).isStopped)
        assertEquals(1, shadowOf(speech).spokenTextList.size)
    }
}
