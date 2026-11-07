package at.heinz.deviceai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real receiver/state transitions; a synthetic callback is never an on-device success claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumCcc1AbortReceiptUiTest {
    private val target = "t-6f404f9a8a6af4df"
    private val newer = "t-2222222222222222"
    private val abortId = "48c959ef-ba32-4df0-af31-bb333bac6469"
    private fun receive(currentTask: String, expectedTarget: String?, payloadTask: String) =
        RuntimeEnvironment.getApplication().getSharedPreferences(ContinuumMobileBuildResultReceiver.PREFERENCES, 0).also { prefs ->
            prefs.edit().clear().putString(ContinuumMobileBuildResultReceiver.CCC1_TASK, currentTask)
                .putString(ContinuumMobileBuildResultReceiver.ABORT_JOB_ID, abortId)
                .putString("ccc1_abort_task_v1", expectedTarget)
                .putString(ContinuumMobileBuildResultReceiver.JOB_ID, "still-running-main")
                .putString(ContinuumMobileBuildResultReceiver.JOB_OPERATION, "CCC1_RUN_ONCE").commit()
            val output = requireNotNull(javaClass.getResource("/ccc1-bridge/abort-aborted.json")).readText().replace(target, payloadTask)
            val intent = Intent().setData(Uri.parse("continuum://termux-callback/CCC1_ABORT/$abortId"))
                .putExtra(ContinuumMobileBuildResultReceiver.JOB_ID, abortId)
                .putExtra(ContinuumMobileBuildResultReceiver.JOB_OPERATION, "CCC1_ABORT")
                .putExtra("result", Bundle().apply {
                    putInt("exitCode", 0); putInt("err", -1); putString("stdout", output)
                    putString("stdout_original_length", output.length.toString())
                })
            ContinuumMobileBuildResultReceiver().onReceive(RuntimeEnvironment.getApplication(), intent)
            assertNull(prefs.getString(ContinuumMobileBuildResultReceiver.ABORT_JOB_ID, null))
            assertEquals("still-running-main", prefs.getString(ContinuumMobileBuildResultReceiver.JOB_ID, null))
        }

    @Test fun aLateAbortReceiptCannotEraseANewerTasksCancelIdentity() {
        val prefs = receive(newer, target, target)
        assertEquals(newer, prefs.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null))
    }

    @Test fun aWrongTargetReceiptCannotConfirmOrClearTheExpectedTask() {
        val prefs = receive(target, target, newer)
        assertEquals(target, prefs.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null))
        assertFalse(prefs.getString(ContinuumMobileBuildResultReceiver.ABORT_MESSAGE, "")!!.contains("abgebrochen"))
    }

    @Test fun anOlderPendingSlotWithoutATargetCannotClearTaskState() {
        val prefs = receive(target, null, target)
        assertEquals(target, prefs.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null))
        assertFalse(prefs.getString(ContinuumMobileBuildResultReceiver.ABORT_MESSAGE, "")!!.contains("abgebrochen"))
    }

    @Test fun aMatchingAbortStillClosesOnlyItsOwnTaskAndSlot() {
        val prefs = receive(target, target, target)
        assertNull(prefs.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null))
        assertNull(prefs.getString("ccc1_abort_task_v1", null))
        assertTrue(prefs.getString(ContinuumMobileBuildResultReceiver.ABORT_MESSAGE, "")!!.contains("abgebrochen"))
    }
}
