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
        /** CCC1 task the owner can cancel next (validated id only). */
        const val CCC1_TASK = "ccc1_task"
        /** Own one-shot slot of a CCC1 abort, so it can reach a running step without dropping that step's result. */
        const val ABORT_JOB_ID = "ccc1_abort_job_id"
        const val ABORT_JOB_START = "ccc1_abort_job_start"
        const val ABORT_MESSAGE = "ccc1_abort_message"
        const val RESULT_BUNDLE = "result"
        const val COMMAND_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val id = intent.getStringExtra(JOB_ID) ?: return
        val operationName = intent.getStringExtra(JOB_OPERATION) ?: return

        val ccc1 = ContinuumCcc1Bridge.Operation.ofJobOperation(operationName)
        val abortSlot = ccc1 == ContinuumCcc1Bridge.Operation.ABORT
        if (abortSlot) {
            if (id != prefs.getString(ABORT_JOB_ID, null)) return
        } else if (id != prefs.getString(JOB_ID, null) ||
            operationName != prefs.getString(JOB_OPERATION, null)
        ) return

        val codingOrder = operationName == "CODE_TEST"
        val operation = if (codingOrder || ccc1 != null) null else try {
            ContinuumMobileBuildPlan.Operation.valueOf(operationName)
        } catch (_: IllegalArgumentException) {
            return
        }
        // Verify immutable routing identity in addition to the fillable
        // extras: a Termux result cannot silently substitute operation/id.
        val callbackData = intent.data ?: return
        if (callbackData.scheme != "continuum" ||
            callbackData.authority != "termux-callback" ||
            callbackData.pathSegments != listOf(operationName, id)
        ) return

        val bundle = intent.getBundleExtra(RESULT_BUNDLE)
        val hasResult = bundle != null && bundle.containsKey("exitCode") && bundle.containsKey("err")
        val exitCode = if (hasResult) bundle?.getInt("exitCode") else null
        val errorCode = if (hasResult) bundle?.getInt("err") else null

        val stdout = bundle?.getString("stdout") ?: ""
        val originalLength = bundle?.getString("stdout_original_length")?.toLongOrNull()
        val truncated = originalLength == null || originalLength > stdout.length.toLong()

        if (ccc1 != null) {
            // stderr only feeds fixed diagnosis patterns (old Termux checkout, missing Node); it is never stored or shown.
            val result = ContinuumCcc1Bridge.evaluate(ccc1, exitCode, errorCode, if (truncated) null else stdout,
                bundle?.getString("stderr"))
            // Only the validated summary, the task id and a verified patch hash; never Termux output or code.
            val editor = prefs.edit()
            if (abortSlot) {
                editor.remove(ABORT_JOB_ID).remove(ABORT_JOB_START).putString(ABORT_MESSAGE, result.message)
            } else {
                editor.remove(JOB_ID).remove(JOB_OPERATION).remove(JOB_START).remove(LAST_SHA)
                    .putString(LAST_MESSAGE, result.message)
                if (result.verifiedPatchSha256 != null) editor.putString(LAST_SHA, result.verifiedPatchSha256)
            }
            if (result.taskId != null) editor.putString(CCC1_TASK, result.taskId)
            else if (result.outcome == ContinuumCcc1Bridge.Outcome.OK &&
                (ccc1 == ContinuumCcc1Bridge.Operation.STATUS || ccc1 == ContinuumCcc1Bridge.Operation.ABORT)
            ) editor.remove(CCC1_TASK)
            editor.commit()
            return
        }

        if (codingOrder) {
            val receipt = ContinuumMobileCodingJob.evaluate(
                id, exitCode, errorCode, if (truncated) null else stdout
            )
            // Keep only the strictly validated summary; never persist raw Termux output,
            // generated code, arbitrary paths or the full candidate patch.
            prefs.edit()
                .remove(JOB_ID)
                .remove(JOB_OPERATION)
                .remove(JOB_START)
                .remove(LAST_SHA)
                .putString(LAST_MESSAGE, ContinuumMobileCodingJob.explanation(receipt))
                .commit()
            return
        }

        val payload = if (!truncated && stdout.length <= 32_768) {
            try { JSONObject(stdout) } catch (_: Exception) { null }
        } else null
        val reportedStatus = payload?.optString("status")
        val artifactHash = payload?.optString("apkSha256")
        val signerHash = payload?.optString("signerSha256")
        val sourceCommit = payload?.optString("commit")
        val packageName = payload?.optString("packageName")
        val versionCode = payload?.optInt("versionCode", -1)
        val ownerCopyStatus = payload?.optJSONObject("ownerInstallCopy")?.optString("status")

        val outcome = ContinuumMobileResultPolicy.evaluate(
            operation = operation ?: return,
            exitCode = exitCode,
            termuxError = errorCode,
            reportedStatus = reportedStatus,
            apkSha256 = artifactHash,
            signerSha256 = signerHash,
            sourceCommit = sourceCommit,
            packageName = packageName,
            versionCode = versionCode
        )
        val text = if (outcome == ContinuumMobileResultPolicy.Outcome.STATUS_REPORTED) {
            ContinuumMobileResultPolicy.statusExplanation(reportedStatus)
        } else {
            ContinuumMobileResultPolicy.explanation(outcome)
        }
        val buildProvenance =
            if (outcome == ContinuumMobileResultPolicy.Outcome.BUILD_REPORTED_UNVERIFIED_ON_DEVICE)
                "\nQuell-Commit: $sourceCommit\nPaket: $packageName · VersionCode: $versionCode"
            else ""
        val ownerCopyMessage =
            if ((outcome == ContinuumMobileResultPolicy.Outcome.BUILD_REPORTED_UNVERIFIED_ON_DEVICE ||
                    (outcome == ContinuumMobileResultPolicy.Outcome.STATUS_REPORTED &&
                        reportedStatus == "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED"))) {
                when (ownerCopyStatus) {
                    "COPIED" -> "\nTermux meldet Installationskopie unter Download/00-DEVICEAI-INSTALLATION. " +
                        "Installation bleibt deine Entscheidung."
                    "UNAVAILABLE" -> "\nDie zusätzliche Download-Kopie wurde nicht bestätigt. " +
                        "Das geprüfte Original bleibt im privaten Termux-Speicher."
                    else -> ""
                }
            } else ""
        val editor = prefs.edit()
            .remove(JOB_ID)
            .remove(JOB_OPERATION)
            .remove(JOB_START)
            .putString(LAST_MESSAGE, text + buildProvenance + ownerCopyMessage)
            .remove(LAST_SHA)
        if (outcome == ContinuumMobileResultPolicy.Outcome.BUILD_REPORTED_UNVERIFIED_ON_DEVICE &&
            artifactHash != null
        ) {
            editor.putString(LAST_SHA, artifactHash)
        }
        editor.commit()
    }
}
