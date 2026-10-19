package at.heinz.deviceai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/**
 * Explicit, non-exported callback target for Termux RUN_COMMAND_PENDING_INTENT.
 * We store only a small structured receipt in app-private preferences (never full logs).
 */
class ContinuumMobileBuildResultReceiver : BroadcastReceiver() {
    companion object {
        const val PREFERENCES = "continuum_mobile_build_v1"
        const val JOB_ID = "job_id"
        const val JOB_OPERATION = "job_operation"
        const val JOB_START = "job_start"
        const val LAST_MESSAGE = "last_message"
        const val LAST_SHA = "last_sha"
        const val RESULT_BUNDLE = "result"
        const val COMMAND_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val id = intent.getStringExtra(JOB_ID) ?: return
        val operationName = intent.getStringExtra(JOB_OPERATION) ?: return

        if (id != prefs.getString(JOB_ID, null) ||
            operationName != prefs.getString(JOB_OPERATION, null)
        ) return

        val operation = try {
            ContinuumMobileBuildPlan.Operation.valueOf(operationName)
        } catch (_: IllegalArgumentException) {
            return
        }

        val bundle = intent.getBundleExtra(RESULT_BUNDLE)
        val hasResult = bundle != null && bundle.containsKey("exitCode") && bundle.containsKey("err")
        val exitCode = if (hasResult) bundle?.getInt("exitCode") else null
        val errorCode = if (hasResult) bundle?.getInt("err") else null

        val stdout = bundle?.getString("stdout") ?: ""
        val originalLength = bundle?.getString("stdout_original_length")?.toLongOrNull()
        val truncated = originalLength == null || originalLength > stdout.length.toLong()

        val payload = if (!truncated && stdout.length <= 32_768) {
            try { JSONObject(stdout) } catch (_: Exception) { null }
        } else null
        val reportedStatus = payload?.optString("status")
        val artifactHash = payload?.optString("apkSha256")
        val signerHash = payload?.optString("signerSha256")

        val outcome = ContinuumMobileResultPolicy.evaluate(
            operation = operation,
            exitCode = exitCode,
            termuxError = errorCode,
            reportedStatus = reportedStatus,
            apkSha256 = artifactHash,
            signerSha256 = signerHash
        )
        val text = ContinuumMobileResultPolicy.explanation(outcome)
        val editor = prefs.edit()
            .remove(JOB_ID)
            .remove(JOB_OPERATION)
            .remove(JOB_START)
            .putString(LAST_MESSAGE, text)
            .remove(LAST_SHA)
        if (outcome == ContinuumMobileResultPolicy.Outcome.BUILD_REPORTED_UNVERIFIED_ON_DEVICE &&
            artifactHash != null
        ) {
            editor.putString(LAST_SHA, artifactHash)
        }
        editor.commit()
    }
}
