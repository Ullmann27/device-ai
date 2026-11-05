package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerFaceDialogsUiTest {
    private class Actions : OwnerFaceDialogs.Actions {
        var current = OwnerFaceStatus(OwnerFacePhase.NOT_ENROLLED,
            message = "Noch keine lokale Gesichtsvorlage vorhanden.")
        val names = mutableListOf<String>()
        var comparisons = 0
        var deletions = 0
        var stops = 0
        var comparing = false
        override fun status() = current
        override fun cameraActive() = current.sessionActive
        override fun startEnrollment(name: String): Boolean {
            comparing = false
            names += name
            current = current.copy(phase = OwnerFacePhase.ENROLLING, sessionActive = true,
                message = "Halte dein Gesicht ruhig vor die Frontkamera.")
            return true
        }
        override fun startRecognition(): Boolean {
            comparing = true
            comparisons++
            current = current.copy(phase = OwnerFacePhase.RECOGNIZING, sessionActive = true,
                message = "Lokaler Vergleich läuft.")
            return true
        }
        override fun canContinueConversation() = comparing && current.sessionActive
        override fun continueConversation() = canContinueConversation()
        override fun stop() {
            stops++
            comparing = false
            current = current.copy(phase = OwnerFacePhase.OFF, sessionActive = false, message = "Kamera gestoppt.")
        }
        override fun deleteProfile() {
            deletions++
            current = OwnerFaceStatus(OwnerFacePhase.DELETED, message = "Gesichtsvorlage gelöscht.")
        }
    }

    private fun withUi(actions: Actions = Actions(), block: (OwnerFaceDialogs, Actions, AlertDialog) -> Unit) {
        val controller: ActivityController<Activity> = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup().visible()
        shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
        controller.windowFocusChanged(true)
        val ui = OwnerFaceDialogs(controller.get(), actions)
        try {
            ui.show()
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            block(ui, actions, ShadowAlertDialog.getLatestAlertDialog())
        } finally {
            ui.dismiss()
            controller.pause().stop().destroy()
        }
    }

    private fun control(dialog: AlertDialog, tag: String): Button =
        dialog.window!!.decorView.findViewWithTag(tag)

    @Test fun openingControlsNeverStartsCaptureAndComparisonNeedsAProfile() = withUi { _, actions, dialog ->
        assertTrue(actions.names.isEmpty())
        assertEquals(0, actions.comparisons)
        assertFalse(control(dialog, "owner-face-compare").isEnabled)
        assertFalse(control(dialog, "owner-face-delete").isEnabled)
        assertFalse(control(dialog, "owner-face-continue").isEnabled)
        val status = dialog.window!!.decorView.findViewWithTag<TextView>("owner-face-status").text.toString()
        assertFalse(status.contains("erkannt", ignoreCase = true))
    }

    @Test fun enrollmentRequiresANameAndSeparateConsent() = withUi { _, actions, dialog ->
        control(dialog, "owner-face-enroll").performClick()
        val consent = ShadowAlertDialog.getLatestAlertDialog()
        assertNotSame(dialog, consent)
        assertTrue(actions.names.isEmpty())
        consent.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(actions.names.isEmpty())
        val name = consent.window!!.decorView.findViewWithTag<EditText>("owner-face-name")
        assertNotNull(name.error)
        name.setText("  Testperson  ")
        consent.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(listOf("Testperson"), actions.names)
        assertTrue(actions.current.sessionActive)
        assertFalse(control(dialog, "owner-face-enroll").isEnabled)
        assertFalse(control(dialog, "owner-face-continue").isEnabled)
        control(dialog, "owner-face-continue").performClick()
        assertTrue(dialog.isShowing)
    }

    @Test fun dismissedEnrollmentCannotStartFromAQueuedApproval() = withUi { ui, actions, dialog ->
        control(dialog, "owner-face-enroll").performClick()
        val consent = ShadowAlertDialog.getLatestAlertDialog()
        consent.window!!.decorView.findViewWithTag<EditText>("owner-face-name").setText("Testperson")
        val approval = consent.getButton(AlertDialog.BUTTON_POSITIVE)
        ui.dismiss()
        approval.performClick()
        assertTrue(actions.names.isEmpty())
        assertFalse(actions.current.sessionActive)
    }

    @Test fun comparisonRequiresItsOwnCameraConsentAndHasImmediateStop() {
        val actions = Actions().apply {
            current = OwnerFaceStatus(OwnerFacePhase.READY, hasProfile = true,
                displayName = "Testperson", message = "Lokale Vorlage vorhanden.")
        }
        withUi(actions) { _, calls, dialog ->
            control(dialog, "owner-face-compare").performClick()
            assertEquals(0, calls.comparisons)
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals(1, calls.comparisons)
            assertTrue(calls.current.sessionActive)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            assertFalse(calls.current.sessionActive)
            assertTrue(dialog.isShowing)
        }
    }

    @Test fun deletionNeedsConfirmationAndPreservesTheProfileWhenCancelled() {
        val actions = Actions().apply {
            current = OwnerFaceStatus(OwnerFacePhase.READY, hasProfile = true,
                displayName = "Testperson", message = "Lokale Vorlage vorhanden.")
        }
        withUi(actions) { _, calls, dialog ->
            control(dialog, "owner-face-delete").performClick()
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, calls.deletions)
            assertTrue(calls.current.hasProfile)
            control(dialog, "owner-face-delete").performClick()
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals(1, calls.deletions)
            assertFalse(calls.current.hasProfile)
            assertFalse(control(dialog, "owner-face-compare").isEnabled)
        }
    }

    @Test fun closeEndsAnActiveSession() {
        val actions = Actions().apply {
            current = OwnerFaceStatus(OwnerFacePhase.RECOGNIZING, hasProfile = true,
                displayName = "Testperson", sessionActive = true, message = "Lokaler Vergleich läuft.")
        }
        withUi(actions) { _, calls, dialog ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(calls.current.sessionActive)
            assertTrue(calls.stops > 0)
        }
    }

    @Test fun unreadableProfileCanStillBeDeletedWithConfirmation() {
        val actions = Actions().apply {
            current = OwnerFaceStatus(OwnerFacePhase.ERROR,
                message = "Das gespeicherte Gesichtsprofil konnte nicht geöffnet werden.")
        }
        withUi(actions) { _, calls, dialog ->
            val profile = dialog.window!!.decorView.findViewWithTag<TextView>("owner-face-profile")
            assertTrue(profile.text.toString().contains("nicht bestätigt"))
            assertTrue(control(dialog, "owner-face-delete").isEnabled)
            control(dialog, "owner-face-delete").performClick()
            assertEquals(0, calls.deletions)
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals(1, calls.deletions)
        }
    }

    @Test fun explicitConversationContinuationKeepsOnlyTheApprovedComparisonActive() {
        val actions = Actions().apply {
            current = OwnerFaceStatus(OwnerFacePhase.READY, hasProfile = true,
                displayName = "Testperson", message = "Lokale Vorlage vorhanden.")
        }
        withUi(actions) { ui, calls, dialog ->
            control(dialog, "owner-face-compare").performClick()
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue(control(dialog, "owner-face-continue").isEnabled)
            val stopsBefore = calls.stops
            control(dialog, "owner-face-continue").performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(ui.isShowing)
            assertTrue(calls.current.sessionActive)
            assertEquals(stopsBefore, calls.stops)
            calls.stop()
            assertFalse(calls.current.sessionActive)
        }
    }

    @Test fun continuationAfterSessionExpiryCannotKeepTheCameraActive() {
        val actions = Actions().apply {
            comparing = true
            current = OwnerFaceStatus(OwnerFacePhase.RECOGNIZING, hasProfile = true,
                displayName = "Testperson", sessionActive = true, message = "Lokaler Vergleich läuft.")
        }
        withUi(actions) { _, calls, dialog ->
            val continuation = control(dialog, "owner-face-continue")
            assertTrue(continuation.isEnabled)
            calls.stop()
            continuation.performClick()
            assertTrue(dialog.isShowing)
            assertFalse(calls.current.sessionActive)
        }
    }

    @Test fun narrowLayoutKeepsControlsReadableAndTouchable() = withUi { _, _, dialog ->
        val decor = dialog.window!!.decorView
        assertTrue("Capture requires an attached window", decor.isAttachedToWindow)
        decor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(740, View.MeasureSpec.AT_MOST))
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        for (tag in listOf("owner-face-enroll", "owner-face-compare", "owner-face-delete", "owner-face-continue")) {
            val button = control(dialog, tag)
            assertTrue("$tag touch height", button.height >= 48)
            assertTrue("$tag text fits width", button.width > 120)
            assertTrue("$tag inside window", button.width <= decor.width)
        }
        System.getProperty("continuum.ui.evidenceDir")?.let { directory ->
            val target = File(directory).apply { mkdirs() }
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            File(target, "owner-face-controls-360.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
