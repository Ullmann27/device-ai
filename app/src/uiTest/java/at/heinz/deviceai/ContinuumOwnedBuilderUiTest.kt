package at.heinz.deviceai

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

/** Real Android widgets under Robolectric; no Termux service or device build is simulated as success. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumOwnedBuilderUiTest {
    private fun buttons(view: View): List<Button> = buildList {
        if (view is Button) add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(buttons(view.getChildAt(i)))
    }

    @Test fun counterSelectionRequiresItsOwnConfirmationAndUnavailableTermuxNeverGetsAJob() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences(ContinuumMobileBuildResultReceiver.PREFERENCES, 0)
        prefs.edit().clear().commit()
        val controller = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            val activity = controller.get()
            val all = buttons(activity.findViewById(android.R.id.content))
            val selector = all.single { it.text.toString().startsWith("Projekt:") }
            assertTrue(selector.isEnabled)
            selector.performClick()
            assertTrue(selector.text.toString().contains("Zähler"))
            all.single { it.text == "M4 · Gewähltes Projekt erzeugen" }.performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull(dialog)
            assertTrue(dialog.findViewById<TextView>(android.R.id.message).text.toString().contains("m4-counter"))
            assertNull(shadowOf(app).nextStartedService)
            assertNull(prefs.getString(ContinuumMobileBuildResultReceiver.JOB_ID, null))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            // No installed/granted Termux here: even confirmation cannot manufacture a running job.
            assertNull(shadowOf(app).nextStartedService)
            assertNull(prefs.getString(ContinuumMobileBuildResultReceiver.JOB_ID, null))
        } finally {controller.pause().stop().destroy();}
    }

    @Test fun recoveryQueryKeepsTheOriginalPendingBuildAndItsCallbackIdentity() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences(ContinuumMobileBuildResultReceiver.PREFERENCES, 0)
        prefs.edit().clear().putString("job_id", "pending-build")
            .putString("job_operation", "PROJECT_BUILD").putString("job_project_v1", "m4-notes")
            .putLong("job_start", System.currentTimeMillis())
            .putString("selected_m4_project_v1", "m4-counter").commit()
        shadowOf(app).grantPermissions(ContinuumMobileBuildPlan.TERMUX_PERMISSION)
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.termux"
            applicationInfo = ApplicationInfo().apply { packageName = "com.termux" }
        })
        val controller = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            val activity = controller.get()
            buttons(activity.findViewById(android.R.id.content))
                .single { it.text == "M4 · Gespeicherten Projektbeleg prüfen" }.performClick()
            val command = shadowOf(app).nextStartedService
            assertNotNull("read-only status must work before a lost build callback returns", command)
            assertEquals("pending-build", prefs.getString("job_id", null))
            assertEquals("m4-notes", prefs.getString("job_project_v1", null))
            assertEquals("m4-counter", prefs.getString("project_status_project_v1", null))
            @Suppress("DEPRECATION")
            val callback = command.getParcelableExtra<PendingIntent>(ContinuumMobileBuildResultReceiver.COMMAND_PENDING_INTENT)!!
            val result = Intent(shadowOf(callback).savedIntent)
            val stdout = "{\"status\":\"NO_PROJECT_BUILD_REPORT\",\"recipe\":\"m4-counter\"}"
            result.putExtra("result", Bundle().apply {
                putInt("exitCode", 0); putInt("err", -1); putString("stdout", stdout)
                putString("stdout_original_length", stdout.length.toString())
            })
            ContinuumMobileBuildResultReceiver().onReceive(activity, result)
            assertNull(prefs.getString("project_status_job_id_v1", null))
            assertEquals("pending-build", prefs.getString("job_id", null))
            assertEquals("PROJECT_BUILD", prefs.getString("job_operation", null))
            assertTrue(prefs.getString("project_status_message_v1", "")!!.contains("noch kein"))
        } finally {controller.pause().stop().destroy();}
    }
}
