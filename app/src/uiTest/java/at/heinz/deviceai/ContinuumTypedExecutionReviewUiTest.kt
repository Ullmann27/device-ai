package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

/** Actual widget/receiver behavior; host contract evidence, never a Termux/device execution claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumTypedExecutionReviewUiTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences(ContinuumMobileBuildResultReceiver.PREFERENCES, 0)
    private val task = ContinuumCodingTask("typed-review-job", "a".repeat(40),
        ContinuumCodingTask.Kind.BUGFIX, "Erhalte die vollständige späte Zielklausel ohne Kürzung",
        listOf("lib/distance.mjs"), listOf("Negative Werte bleiben negativ", "Null bleibt unverändert"),
        ContinuumCodingTask.Chain.NODE, ContinuumCodingTask.Limits(2, 8192))
    // Literal persisted keys let these regressions execute against the unchanged baseline.
    private val preparedJson = "typed_coding_prepared_json_v1"
    private val expectedJson = "typed_coding_expected_json_v1"
    private val preparedCcc1 = "typed_coding_prepared_ccc1_task_v1"

    @Before fun reset() {
        prefs.edit().clear().commit()
        shadowOf(app).grantPermissions(ContinuumMobileBuildPlan.TERMUX_PERMISSION)
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.termux"
            applicationInfo = ApplicationInfo().apply { packageName = "com.termux" }
        })
    }

    private fun views(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(views(view.getChildAt(i)))
    }
    private fun seed(full: Boolean = true) {
        prefs.edit().putString("typed_coding_prepared_id_v1", task.id)
            .putString("typed_coding_prepared_sha_v1", task.fingerprint())
            .putString("typed_coding_prepared_commit_v1", task.sourceCommit).apply {
                putString(preparedCcc1, "t-aaaaaaaaaaaaaaaa")
                if (full) putString(preparedJson, task.canonicalJson())
            }.commit()
    }
    private fun runButton(activity: ContinuumMobileBuilderActivity) =
        views(activity.findViewById(android.R.id.content)).filterIsInstance<Button>()
            .single { it.text == "Vorbereiteten Coding-Auftrag ausführen" }
    private fun clickRun(activity: ContinuumMobileBuilderActivity): AlertDialog {
        runButton(activity).performClick()
        return ShadowAlertDialog.getLatestAlertDialog()
    }

    @Test fun executionAfterRecreationDisplaysTheCompleteTaskBeforeDispatch() {
        seed()
        val controller = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            controller.recreate()
            val dialog = clickRun(controller.get())
            val displayed = views(dialog.window!!.decorView).filterIsInstance<TextView>()
                .joinToString("\n") { it.text }
            for (field in listOf(task.goal, task.sourceCommit, task.fingerprint(), task.kind.name,
                    task.allowedPaths.single(), task.acceptance[0], task.acceptance[1], "8192")) {
                assertTrue("Execution review omits $field", displayed.contains(field))
            }
            assertNull(shadowOf(app).nextStartedService)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun legacyPreparedTupleCannotEnableExecution() {
        seed(full = false)
        val controller = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try { assertFalse(runButton(controller.get()).isEnabled) }
        finally { controller.pause().stop().destroy() }
    }

    @Test fun alteredCommitWhileExecutionDialogIsOpenCannotDispatch() {
        seed()
        val controller = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            val dialog = clickRun(controller.get())
            prefs.edit().putString("typed_coding_prepared_commit_v1", "b".repeat(40)).commit()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNull("A stale review must not approve a replacement commit", shadowOf(app).nextStartedService)
            assertNull(prefs.getString("job_id", null))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun dispatchedRunConsumesThePreparedTaskAndCannotRearmAfterCallback() {
        seed()
        val controller = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            clickRun(controller.get()).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNotNull(shadowOf(app).nextStartedService)
            assertNull("RUN must consume the prepared authorization", prefs.getString("typed_coding_prepared_id_v1", null))
            assertNull(prefs.getString(preparedJson, null))
            // A terminal callback or explicit uncertainty resolution clears the pending slot.
            prefs.edit().remove("job_id").remove("job_operation").commit()
            controller.recreate()
            assertFalse(runButton(controller.get()).isEnabled)
        } finally { controller.pause().stop().destroy() }
    }

    private fun deliverPreparedReceipt(ccc1TaskId: String = "t-aaaaaaaaaaaaaaaa",
                                       json: String = task.canonicalJson()): Intent {
        prefs.edit().putString("job_id", "prepare-request")
            .putString("job_operation", "TYPED_CODING_PREPARE")
            .putString("typed_coding_expected_id_v1", task.id)
            .putString("typed_coding_expected_sha_v1", task.fingerprint())
            .putString("typed_coding_expected_commit_v1", task.sourceCommit)
            .putString(expectedJson, json).commit()
        val stdout = """{"schema":"continuum-typed-coding-bridge","version":1,"operation":"prepare","id":"${task.id}","status":"PREPARED_NOT_EXECUTED","taskSha256":"${task.fingerprint()}","sourceCommit":"${task.sourceCommit}","ccc1TaskId":"$ccc1TaskId","replayed":false,"approvedForMerge":false,"installed":false,"deviceVerified":false}"""
        val callback = Intent().setData(android.net.Uri.parse("continuum://termux-callback/TYPED_CODING_PREPARE/prepare-request"))
            .putExtra("job_id", "prepare-request").putExtra("job_operation", "TYPED_CODING_PREPARE")
            .putExtra("result", Bundle().apply {
                putInt("exitCode", 0); putInt("err", -1); putString("stdout", stdout)
                putString("stdout_original_length", stdout.length.toString())
            })
        ContinuumMobileBuildResultReceiver().onReceive(app, callback)
        return callback
    }

    @Test fun receiverCarriesOnlyTheValidatedFullTaskIntoPreparedState() {
        deliverPreparedReceipt()
        assertEquals(task.canonicalJson(), prefs.getString(preparedJson, null))
        assertNull(prefs.getString(expectedJson, null))
    }

    @Test fun foregroundPrepareCallbackEnablesRunWithoutStartingIt() {
        val controller = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            assertFalse(runButton(controller.get()).isEnabled)
            deliverPreparedReceipt()
            assertTrue("Foreground callback must refresh the prepared task", runButton(controller.get()).isEnabled)
            assertNull(shadowOf(app).nextStartedService)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun pausedBuilderRefreshesOnlyWhenResumed() {
        val controller = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            controller.pause()
            deliverPreparedReceipt()
            assertFalse("Paused UI must detach its preference listener", runButton(controller.get()).isEnabled)
            controller.resume()
            assertTrue(runButton(controller.get()).isEnabled)
            assertNull(shadowOf(app).nextStartedService)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun typedPreparationBindsTheExistingExplicitAbortTargetDuringRun() {
        deliverPreparedReceipt()
        val target = "t-aaaaaaaaaaaaaaaa"
        assertEquals("Fresh typed preparation must expose its actual CCC1 cancel target", target,
            prefs.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null))
        val controller = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            clickRun(controller.get()).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNotNull(shadowOf(app).nextStartedService)
            val running = prefs.getString("job_id", null)
            assertNotNull(running)
            views(controller.get().findViewById(android.R.id.content)).filterIsInstance<Button>()
                .single { it.text == "CCC1-Auftrag abbrechen" }.performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.findViewById<TextView>(android.R.id.message).text.contains(target))
            assertNull("Abort is an explicit owner action", shadowOf(app).nextStartedService)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val abort = shadowOf(app).nextStartedService
            assertNotNull(abort)
            val args = abort.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS")!!.toList()
            assertTrue(args.contains("ccc1-abort"))
            assertEquals(target, args[args.indexOf("--task") + 1])
            assertEquals(running, prefs.getString("job_id", null))
            assertEquals(target, prefs.getString(ContinuumMobileBuildResultReceiver.ABORT_TASK, null))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun invalidOrReplayedPrepareCallbackCannotReplaceTheCurrentAbortTarget() {
        val current = "t-bbbbbbbbbbbbbbbb"
        prefs.edit().putString(ContinuumMobileBuildResultReceiver.CCC1_TASK, current).commit()
        deliverPreparedReceipt("arbitrary-model-task")
        assertEquals(current, prefs.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null))
        assertNull(prefs.getString(preparedJson, null))
        val oldCallback = deliverPreparedReceipt()
        prefs.edit().putString(ContinuumMobileBuildResultReceiver.CCC1_TASK, current).commit()
        ContinuumMobileBuildResultReceiver().onReceive(app, oldCallback)
        assertEquals("Consumed callback cannot retarget a newer task", current,
            prefs.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null))
    }

    @Test fun changedFullTaskCannotBecomePreparedOrEnableRunWithTheOldDigest() {
        val changed = task.copy(goal = "Ein anderes Ziel").canonicalJson()
        deliverPreparedReceipt(json = changed)
        assertNull(prefs.getString(preparedJson, null))
        assertNull(prefs.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null))
        seed()
        prefs.edit().putString(preparedJson, changed).commit()
        val controller = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try { assertFalse(runButton(controller.get()).isEnabled) }
        finally { controller.pause().stop().destroy() }
    }

    @Test fun typedRunRestoresItsOwnCancelTargetAfterAnInterveningStatus() {
        deliverPreparedReceipt()
        prefs.edit().putString(ContinuumMobileBuildResultReceiver.CCC1_TASK, "t-bbbbbbbbbbbbbbbb").commit()
        val controller = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            clickRun(controller.get()).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNotNull(shadowOf(app).nextStartedService)
            assertEquals("RUN must restore its own validated prepared CCC1 task, not the newer status target",
                "t-aaaaaaaaaaaaaaaa", prefs.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun changedPreparedJournalIdWhileRunDialogIsOpenCannotDispatch() {
        deliverPreparedReceipt()
        val controller = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            val dialog = clickRun(controller.get())
            prefs.edit().putString(preparedCcc1, "t-bbbbbbbbbbbbbbbb").commit()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNull("A replaced journal ID needs a fresh owner review", shadowOf(app).nextStartedService)
            assertNull(prefs.getString("job_id", null))
        } finally { controller.pause().stop().destroy() }
    }
}
