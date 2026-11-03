package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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

/** Independent frozen-case review. Real Android classes under Robolectric; no device/model claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IndependentTypedAuthorizationUiTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences(ContinuumMobileBuildResultReceiver.PREFERENCES, 0)
    private val target = "t-6f404f9a8a6af4df"
    private val task = ContinuumCodingTask("independent-review", "c".repeat(40),
        ContinuumCodingTask.Kind.BUGFIX, "Preserve final clauses and negative values",
        listOf("src/a.mjs", "src/b.mjs", "src/c.mjs"),
        (1..8).map { "Preserve acceptance criterion $it including its final clause" },
        ContinuumCodingTask.Chain.NODE, ContinuumCodingTask.Limits(3, 24000))
    @Before fun setup() {
        prefs.edit().clear().commit()
        shadowOf(app).grantPermissions(ContinuumMobileBuildPlan.TERMUX_PERMISSION)
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.termux"
            applicationInfo = ApplicationInfo().apply { packageName = "com.termux" }
        })
    }
    private fun seed(t: ContinuumCodingTask = task) {
        prefs.edit().putString("typed_coding_prepared_id_v1", t.id)
            .putString("typed_coding_prepared_sha_v1", t.fingerprint())
            .putString("typed_coding_prepared_commit_v1", t.sourceCommit)
            .putString("typed_coding_prepared_json_v1", t.canonicalJson())
            .putString("typed_coding_prepared_ccc1_task_v1", target)
            .putString("ccc1_task", target).commit()
    }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun runButton(a: ContinuumMobileBuilderActivity) =
        all(a.findViewById(android.R.id.content)).filterIsInstance<Button>()
            .single { it.text == "Vorbereiteten Coding-Auftrag ausführen" }
    private fun review(a: ContinuumMobileBuilderActivity): AlertDialog {
        runButton(a).performClick()
        return ShadowAlertDialog.getLatestAlertDialog()
    }
    private fun approve(d: AlertDialog) {
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    private fun callback(id: String, operation: String, stdout: String): Intent =
        Intent().setData(Uri.parse("continuum://termux-callback/$operation/$id"))
            .putExtra("job_id", id).putExtra("job_operation", operation)
            .putExtra("result", Bundle().apply {
                putInt("exitCode", 0); putInt("err", -1); putString("stdout", stdout)
                putString("stdout_original_length", stdout.length.toString())
            })
    private fun preparePending(id: String = "pending-current") {
        prefs.edit().putString("job_id", id).putString("job_operation", "TYPED_CODING_PREPARE")
            .putString("typed_coding_expected_id_v1", task.id)
            .putString("typed_coding_expected_sha_v1", task.fingerprint())
            .putString("typed_coding_expected_commit_v1", task.sourceCommit)
            .putString("typed_coding_expected_json_v1", task.canonicalJson()).commit()
    }
    private fun preparedOutput() = """{"schema":"continuum-typed-coding-bridge","version":1,"operation":"prepare","id":"${task.id}","status":"PREPARED_NOT_EXECUTED","taskSha256":"${task.fingerprint()}","sourceCommit":"${task.sourceCommit}","ccc1TaskId":"$target","replayed":false,"approvedForMerge":false,"installed":false,"deviceVerified":false}"""
    private fun confirmedAbort() {
        prefs.edit().putString("ccc1_abort_job_id", "abort-current")
            .putString("ccc1_abort_task_v1", target).commit()
        val output = requireNotNull(javaClass.getResource("/ccc1-bridge/abort-aborted.json")).readText()
        ContinuumMobileBuildResultReceiver().onReceive(app, callback("abort-current", "CCC1_ABORT", output))
        assertTrue("Fixture must be a validated terminal abort", prefs.getString("ccc1_abort_message", "")!!.contains("abgebrochen"))
    }
    @Test fun everyPreparedTupleFieldFailsClosedWhenMissingOrChanged() {
        val keys = listOf("typed_coding_prepared_id_v1", "typed_coding_prepared_sha_v1",
            "typed_coding_prepared_commit_v1", "typed_coding_prepared_json_v1", "typed_coding_prepared_ccc1_task_v1")
        for (key in keys) for (missing in listOf(true, false)) {
            seed()
            prefs.edit().apply { if (missing) remove(key) else putString(key, "tampered") }.commit()
            val c = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
            try { assertFalse("Field $key missing=$missing must disable RUN", runButton(c.get()).isEnabled) }
            finally { c.pause().stop().destroy() }
        }
        assertNull(shadowOf(app).nextStartedService)
    }
    @Test fun fullyValidReplacementWhileDialogOpenNeedsFreshApproval() {
        seed()
        val c = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            val d = review(c.get())
            seed(task.copy(goal = "Different complete valid task"))
            approve(d)
            assertNull(shadowOf(app).nextStartedService)
            assertNull(prefs.getString("job_id", null))
        } finally { c.pause().stop().destroy() }
    }
    @Test fun duplicatePositiveClickDispatchesAtMostOnce() {
        seed()
        val c = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            val d = review(c.get()); approve(d); approve(d)
            assertNotNull(shadowOf(app).nextStartedService)
            assertNull(shadowOf(app).nextStartedService)
            assertNull(prefs.getString("typed_coding_prepared_json_v1", null))
        } finally { c.pause().stop().destroy() }
    }
    @Test fun destroyedActivityCannotConsumeOrDispatchItsOldDialog() {
        seed()
        val c = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        val d = review(c.get())
        c.pause().stop().destroy(); approve(d)
        assertNull(shadowOf(app).nextStartedService)
        assertEquals(task.canonicalJson(), prefs.getString("typed_coding_prepared_json_v1", null))
    }
    @Test fun oldCallbackCannotConsumeTheReplacementPendingSlot() {
        preparePending()
        ContinuumMobileBuildResultReceiver().onReceive(app, callback("old-request", "TYPED_CODING_PREPARE", preparedOutput()))
        assertEquals("pending-current", prefs.getString("job_id", null))
        assertNull(prefs.getString("typed_coding_prepared_json_v1", null))
    }
    @Test fun matchingExtrasWithWrongImmutableUriCannotPrepare() {
        preparePending()
        val forged = callback("pending-current", "TYPED_CODING_PREPARE", preparedOutput())
            .setData(Uri.parse("continuum://termux-callback/TYPED_CODING_PREPARE/wrong-request"))
        ContinuumMobileBuildResultReceiver().onReceive(app, forged)
        assertEquals("pending-current", prefs.getString("job_id", null))
        assertNull(prefs.getString("typed_coding_prepared_json_v1", null))
    }
    @Test fun duplicatePreparationCallbackCannotResurrectConsumedPreparation() {
        preparePending()
        val receipt = callback("pending-current", "TYPED_CODING_PREPARE", preparedOutput())
        ContinuumMobileBuildResultReceiver().onReceive(app, receipt)
        val c = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            approve(review(c.get()))
            assertNotNull(shadowOf(app).nextStartedService)
            val running = prefs.getString("job_id", null)
            ContinuumMobileBuildResultReceiver().onReceive(app, receipt)
            assertEquals(running, prefs.getString("job_id", null))
            assertNull(prefs.getString("typed_coding_prepared_json_v1", null))
        } finally { c.pause().stop().destroy() }
    }
    @Test fun canceledPreparedTaskCannotBeOfferedForExecution() {
        seed(); confirmedAbort()
        val c = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try { assertFalse("Terminal cancellation must revoke prepared execution", runButton(c.get()).isEnabled) }
        finally { c.pause().stop().destroy() }
    }
    @Test fun terminalCancellationWhileApprovalDialogOpenCannotDispatch() {
        seed()
        val c = Robolectric.buildActivity(ContinuumMobileBuilderActivity::class.java).setup()
        try {
            val d = review(c.get()); confirmedAbort(); approve(d)
            assertNull("A confirmed canceled CCC1 job must not be re-dispatched", shadowOf(app).nextStartedService)
        } finally { c.pause().stop().destroy() }
    }
}
