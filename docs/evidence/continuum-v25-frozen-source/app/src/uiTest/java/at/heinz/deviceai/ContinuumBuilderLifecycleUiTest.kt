package at.heinz.deviceai

import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

/** Host widget regressions only: dispatch is observed, never reported as a successful device job. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumBuilderLifecycleUiTest {
    private fun buttons(view: View): List<Button> = buildList {
        if (view is Button) add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(buttons(view.getChildAt(i)))
    }

    private fun prepareTermux() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences(ContinuumMobileBuildResultReceiver.PREFERENCES, 0).edit().clear().commit()
        shadowOf(app).grantPermissions(ContinuumMobileBuildPlan.TERMUX_PERMISSION)
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.termux"
            applicationInfo = ApplicationInfo().apply { packageName = "com.termux" }
        })
    }

    @Test fun anOldConfirmationCannotDispatchAfterItsActivityWasDestroyed() {
        prepareTermux()
        val old = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        buttons(old.get().findViewById(android.R.id.content))
            .single { it.text == "M4 · Gewähltes Projekt erzeugen" }.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        old.pause().stop().destroy()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
        assertNull(RuntimeEnvironment.getApplication()
            .getSharedPreferences(ContinuumMobileBuildResultReceiver.PREFERENCES, 0)
            .getString(ContinuumMobileBuildResultReceiver.JOB_ID, null))
    }

    @Test fun acknowledgingAnOldLostCallbackCannotEraseANewerJobsIdentity() {
        prepareTermux()
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences(ContinuumMobileBuildResultReceiver.PREFERENCES, 0)
        prefs.edit().putString("job_id", "old-job").putString("job_operation", "PROJECT_BUILD")
            .putString("job_project_v1", "m4-notes")
            .putLong("job_start", System.currentTimeMillis() - 3_700_000).commit()
        val c = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            buttons(c.get().findViewById(android.R.id.content)).single { it.text == "Werkzeuge prüfen" }.performClick()
            val stale = ShadowAlertDialog.getLatestAlertDialog()
            // The original callback has completed and another foreground instance has started a job.
            prefs.edit().putString("job_id", "new-job").putString("job_operation", "PROJECT_BUILD")
                .putString("job_project_v1", "m4-counter").putLong("job_start", 123L).commit()
            stale.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals("new-job", prefs.getString("job_id", null))
            assertEquals("PROJECT_BUILD", prefs.getString("job_operation", null))
            assertEquals("m4-counter", prefs.getString("job_project_v1", null))
            assertEquals(123L, prefs.getLong("job_start", 0L))
        } finally { c.pause().stop().destroy() }
    }

    @Test fun ccc1ConfirmationDispatchesTheChainShownInItsDialog() {
        prepareTermux()
        val c = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            val all = buttons(c.get().findViewById(android.R.id.content))
            all.single { it.text == "CCC1-Reparaturauftrag einreichen" }.performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            all.single { it.text.startsWith("Testkette:") }.performClick()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val sent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
            assertNotNull(sent)
            val args = sent.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS")!!.toList()
            assertEquals("node", args[args.indexOf("--chain") + 1])
        } finally { c.pause().stop().destroy() }
    }

    @Test fun aStaleAbortConfirmationCannotRetargetToANewerCcc1Task() {
        prepareTermux()
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences(ContinuumMobileBuildResultReceiver.PREFERENCES, 0)
        val oldTask = "t-1111111111111111"
        val newTask = "t-2222222222222222"
        prefs.edit().putString(ContinuumMobileBuildResultReceiver.CCC1_TASK, oldTask).commit()
        val c = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            buttons(c.get().findViewById(android.R.id.content))
                .single { it.text == "CCC1-Auftrag abbrechen" }.performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.findViewById<android.widget.TextView>(android.R.id.message).text.contains(oldTask))
            // A status callback updates the next cancellable task while the owner reads the dialog.
            prefs.edit().putString(ContinuumMobileBuildResultReceiver.CCC1_TASK, newTask).commit()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNull("A confirmation for the previous task must not abort its successor", shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
            assertEquals(newTask, prefs.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null))
            assertNull(prefs.getString(ContinuumMobileBuildResultReceiver.ABORT_JOB_ID, null))
        } finally { c.pause().stop().destroy() }
    }

    @Test fun anAbortDispatchPersistsTheExactTargetWithItsSeparateCallbackSlot() {
        prepareTermux()
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences(ContinuumMobileBuildResultReceiver.PREFERENCES, 0)
        val target = "t-1111111111111111"
        prefs.edit().putString(ContinuumMobileBuildResultReceiver.CCC1_TASK, target).commit()
        val c = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            buttons(c.get().findViewById(android.R.id.content)).single { it.text == "CCC1-Auftrag abbrechen" }.performClick()
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNotNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
            assertNotNull(prefs.getString(ContinuumMobileBuildResultReceiver.ABORT_JOB_ID, null))
            assertEquals(target, prefs.getString(ContinuumMobileBuildResultReceiver.ABORT_TASK, null))
            assertNull(prefs.getString(ContinuumMobileBuildResultReceiver.JOB_ID, null))
        } finally { c.pause().stop().destroy() }
    }
}
