package at.heinz.deviceai

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarEvent
import at.heinz.deviceai.avatar.AvatarSink
import at.heinz.deviceai.cognitive.CameraGazeSample
import at.heinz.deviceai.cognitive.CameraTrackingState
import at.heinz.deviceai.ui.ContinuumDashboardState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.ExecutorService

/** Host admission checks; no capture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumConversationCameraUiTest {
    private lateinit var activity: AgentActivity
    private val events = mutableListOf<AvatarEvent>()
    private fun field(name: String) = AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun set(name: String, value: Any?) = field(name).set(activity, value)
    private fun read(name: String): Any? = field(name).get(activity)
    private fun call(name: String): Any? = AgentActivity::class.java.getDeclaredMethod(name)
        .apply { isAccessible = true }.invoke(activity)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun consent() = read("cameraConsentDialog") as AlertDialog
    private fun presentation() = call("presentationStatus") as ContinuumDashboardState.PresentationStatus

    @Before fun setup() {
        activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        set("foregroundLearningActive", true)
        activity.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(activity.application).denyPermissions(Manifest.permission.CAMERA)
        (read("avatarDirector") as AvatarDirector).attach(AvatarSink { events += it })
        events.clear()
    }

    @After fun cleanup() {
        set("foregroundLearningActive", false)
        call("stopReadinessCamera")
        (read("readinessDialog") as? AlertDialog)?.dismiss()
        for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor")) {
            (read(name) as ExecutorService).shutdownNow()
        }
        idle()
    }

    @Test fun firstQuestionDoesNotStartCameraAndDeclineIsNotRepeated() {
        call("maybeAskForConversationCamera")
        val dialog = consent()
        assertTrue(dialog.isShowing)
        assertEquals("NEIN", dialog.getButton(AlertDialog.BUTTON_NEUTRAL).text.toString())
        assertEquals("SPÄTER", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString())
        assertEquals(false, read("readinessCameraEnabled"))
        assertEquals(CameraTrackingState.OFF, (read("readinessCamera") as AndroidAvatarCameraInput).state)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        idle()
        call("maybeAskForConversationCamera")
        assertNull(read("cameraConsentDialog"))
        call("toggleConversationCamera")
        assertTrue(consent().isShowing)
        assertEquals(false, read("readinessCameraEnabled"))
    }

    @Test fun laterAndBackAlsoLeaveCameraOffWithoutNagging() {
        for (later in listOf(true, false)) {
            call("showConversationCameraConsent")
            val dialog = consent()
            if (later) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() else dialog.cancel()
            idle()
            call("maybeAskForConversationCamera")
            assertNull(read("cameraConsentDialog"))
            assertFalse(presentation().cameraSessionActive)
        }
    }

    @Test fun permissionResultCannotStartCameraOrReuseDismissedApproval() {
        call("showConversationCameraConsent")
        val approval = consent().getButton(AlertDialog.BUTTON_POSITIVE)
        approval.performClick()
        idle()
        shadowOf(activity.application).grantPermissions(Manifest.permission.CAMERA)
        val request = AgentActivity::class.java.getDeclaredField("READINESS_CAMERA_PERMISSION_REQUEST")
            .apply { isAccessible = true }.getInt(null)
        activity.onRequestPermissionsResult(request, arrayOf(Manifest.permission.CAMERA),
            intArrayOf(PackageManager.PERMISSION_GRANTED))
        approval.performClick()
        idle()
        assertNull(read("cameraConsentDialog"))
        assertFalse(presentation().cameraSessionActive)
        assertEquals(CameraTrackingState.OFF, (read("readinessCamera") as AndroidAvatarCameraInput).state)
        assertTrue(events.none { it is AvatarEvent.GazeObserved })
    }

    @Test fun activeSessionHasVisibleImmediateStopOutsideSetup() {
        set("readinessCameraEnabled", true)
        val stop = call("buildCameraSessionStopControl") as Button
        set("cameraSessionStopButton", stop)
        assertNull(read("readinessDialog"))
        assertTrue(presentation().cameraSessionActive)
        assertEquals(View.VISIBLE, stop.visibility)
        assertTrue(stop.text.toString().contains("STOPPEN"))
        stop.performClick()
        assertFalse(presentation().cameraSessionActive)
        assertEquals(View.GONE, stop.visibility)
        assertTrue(events.last() is AvatarEvent.GazeCleared)
    }

    @Test fun activityPauseRevokesSessionAndRejectsQueuedApprovalAfterReturn() {
        call("showConversationCameraConsent")
        val dialog = consent()
        val approval = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        set("readinessCameraEnabled", true)
        call("onPause")
        idle()
        assertFalse(dialog.isShowing)
        assertNull(read("cameraConsentDialog"))
        assertEquals(false, read("readinessCameraEnabled"))
        assertEquals(false, read("foregroundLearningActive"))
        set("foregroundLearningActive", true)
        approval.performClick()
        call("maybeAskForConversationCamera")
        assertNull(read("cameraConsentDialog"))
        assertFalse(presentation().cameraSessionActive)
        assertEquals(CameraTrackingState.OFF, (read("readinessCamera") as AndroidAvatarCameraInput).state)
    }

    @Test fun configurationChangeRequiresFreshConsent() {
        set("readinessCameraEnabled", true)
        call("showConversationCameraConsent")
        activity.onConfigurationChanged(Configuration(activity.resources.configuration))
        idle()
        assertNull(read("cameraConsentDialog"))
        assertFalse(presentation().cameraSessionActive)
        assertEquals(false, read("readinessCameraEnabled"))
    }

    @Test fun mainConversationAcceptsFreshGazeButStopAndBackgroundRejectIt() {
        val camera = call("readinessCameraInput") as AndroidAvatarCameraInput
        @Suppress("UNCHECKED_CAST")
        val observe = AndroidAvatarCameraInput::class.java.getDeclaredField("onObservation")
            .apply { isAccessible = true }.get(camera) as (CameraGazeSample) -> Unit
        val sample = CameraGazeSample(.4f, -.2f, 90, SystemClock.elapsedRealtime())
        set("readinessCameraEnabled", true)
        observe(sample)
        assertNull(read("readinessDialog"))
        assertEquals(1, events.filterIsInstance<AvatarEvent.GazeObserved>().size)
        call("stopReadinessCamera")
        observe(sample)
        assertEquals(1, events.filterIsInstance<AvatarEvent.GazeObserved>().size)
        set("readinessCameraEnabled", true)
        set("foregroundLearningActive", false)
        observe(sample)
        assertEquals(1, events.filterIsInstance<AvatarEvent.GazeObserved>().size)
        assertTrue(events.last() is AvatarEvent.GazeCleared)
    }

    @Test fun backgroundCannotShowQuestionOrStartCamera() {
        set("foregroundLearningActive", false)
        call("maybeAskForConversationCamera")
        call("toggleConversationCamera")
        assertNull(read("cameraConsentDialog"))
        assertNull(read("readinessCamera"))
        assertFalse(presentation().cameraSessionActive)
    }
}
