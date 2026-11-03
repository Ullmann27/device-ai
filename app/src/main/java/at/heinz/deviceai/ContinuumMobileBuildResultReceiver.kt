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
        const val JOB_PROJECT = "job_project_v1"
        const val JOB_START = "job_start"
        /** Read-only recovery must remain available while a build callback is missing. */
        const val STATUS_JOB_ID = "project_status_job_id_v1"
        const val STATUS_JOB_START = "project_status_job_start_v1"
        const val STATUS_PROJECT = "project_status_project_v1"
        const val STATUS_MESSAGE = "project_status_message_v1"
        const val LAST_MESSAGE = "last_message"
        const val LAST_SHA = "last_sha"
        const val TYPED_EXPECTED_ID = "typed_coding_expected_id_v1"
        const val TYPED_EXPECTED_SHA = "typed_coding_expected_sha_v1"
        const val TYPED_EXPECTED_COMMIT = "typed_coding_expected_commit_v1"
        const val TYPED_EXPECTED_JSON = "typed_coding_expected_json_v1"
        const val TYPED_PREPARED_ID = "typed_coding_prepared_id_v1"
        const val TYPED_PREPARED_SHA = "typed_coding_prepared_sha_v1"
        const val TYPED_PREPARED_COMMIT = "typed_coding_prepared_commit_v1"
        const val TYPED_PREPARED_JSON = "typed_coding_prepared_json_v1"
        const val TYPED_PREPARED_CCC1_TASK = "typed_coding_prepared_ccc1_task_v1"
        /** CCC1 task the owner can cancel next (validated id only). */
        const val CCC1_TASK = "ccc1_task"
        /** Own one-shot slot of a CCC1 abort, so it can reach a running step without dropping that step's result. */
        const val ABORT_JOB_ID = "ccc1_abort_job_id"
        const val ABORT_JOB_START = "ccc1_abort_job_start"
        const val ABORT_TASK = "ccc1_abort_task_v1"
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
        val projectStatusSlot = operationName == ContinuumMobileBuildPlan.Operation.PROJECT_STATUS.name
        if (abortSlot) {
            if (id != prefs.getString(ABORT_JOB_ID, null)) return
        } else if (projectStatusSlot) {
            if (id != prefs.getString(STATUS_JOB_ID, null)) return
        } else if (id != prefs.getString(JOB_ID, null) ||
            operationName != prefs.getString(JOB_OPERATION, null)
        ) return

        val codingOrder = operationName == "CODE_TEST"
        val typed = ContinuumTypedCodingBridge.Operation.ofCallback(operationName)
        val operation = if (codingOrder || ccc1 != null || typed != null) null else try {
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
            val evaluated = ContinuumCcc1Bridge.evaluate(ccc1, exitCode, errorCode, if (truncated) null else stdout,
                bundle?.getString("stderr"))
            val expectedAbortTask = if (abortSlot) prefs.getString(ABORT_TASK, null) else null
            // evaluate already strictly validated/bounded this JSON. Bind its task as well as its callback id.
            val abortTargetMatches = !abortSlot || (expectedAbortTask != null &&
                evaluated.outcome == ContinuumCcc1Bridge.Outcome.OK && runCatching {
                    JSONObject(stdout).getJSONObject("result").getString("taskId") == expectedAbortTask
                }.getOrDefault(false))
            val result = if (abortSlot && evaluated.outcome == ContinuumCcc1Bridge.Outcome.OK && !abortTargetMatches)
                evaluated.copy(outcome = ContinuumCcc1Bridge.Outcome.INVALID_RESULT, status = "INVALID_RESULT",
                    message = "Keine auftragsgebundene CCC1-Abbruchrückmeldung; nichts bestätigt.", taskId = null)
                else evaluated
            // Only the validated summary, the task id and a verified patch hash; never Termux output or code.
            val editor = prefs.edit()
            if (abortSlot) {
                editor.remove(ABORT_JOB_ID).remove(ABORT_JOB_START).remove(ABORT_TASK).putString(ABORT_MESSAGE, result.message)
            } else {
                editor.remove(JOB_ID).remove(JOB_OPERATION).remove(JOB_PROJECT).remove(JOB_START).remove(LAST_SHA)
                    .putString(LAST_MESSAGE, result.message)
                if (result.verifiedPatchSha256 != null) editor.putString(LAST_SHA, result.verifiedPatchSha256)
            }
            // A late abort(A) must not replace/clear B reported by a newer MAIN status callback.
            if (!abortSlot || (abortTargetMatches && prefs.getString(CCC1_TASK, null) == expectedAbortTask)) {
                if (result.taskId != null) editor.putString(CCC1_TASK, result.taskId)
                else if (result.outcome == ContinuumCcc1Bridge.Outcome.OK &&
                    (ccc1 == ContinuumCcc1Bridge.Operation.STATUS || ccc1 == ContinuumCcc1Bridge.Operation.ABORT)
                ) editor.remove(CCC1_TASK)
            }
            editor.commit()
            return
        }

        if (typed != null) {
            val expectedId=prefs.getString(TYPED_EXPECTED_ID,null)
            val expectedHash=prefs.getString(TYPED_EXPECTED_SHA,null)
            val expectedCommit=prefs.getString(TYPED_EXPECTED_COMMIT,null)
            val expectedJson=prefs.getString(TYPED_EXPECTED_JSON,null)
            val authorization=ContinuumTypedCodingBridge.parseBoundAuthorization(
                expectedJson,expectedHash,expectedId,expectedCommit)
            val result=if(authorization!=null && expectedId!=null && expectedHash!=null && expectedCommit!=null)
                ContinuumTypedCodingBridge.evaluate(typed,expectedId,expectedHash,
                    exitCode,errorCode,if(truncated)null else stdout,expectedCommit)
                else null
            val message=result?.message ?:
                "Coding-Auftragsbindung fehlte. Keine Modell- oder Patch-Freigabe bestätigt."
            val editor=prefs.edit().remove(JOB_ID).remove(JOB_OPERATION)
                .remove(JOB_PROJECT).remove(JOB_START).remove(LAST_SHA)
                .remove(TYPED_EXPECTED_ID).remove(TYPED_EXPECTED_SHA).remove(TYPED_EXPECTED_COMMIT)
                .remove(TYPED_EXPECTED_JSON)
                .putString(LAST_MESSAGE,message)
            if(typed==ContinuumTypedCodingBridge.Operation.PREPARE) {
                editor.remove(TYPED_PREPARED_ID).remove(TYPED_PREPARED_SHA).remove(TYPED_PREPARED_COMMIT)
                    .remove(TYPED_PREPARED_JSON)
                    .remove(TYPED_PREPARED_CCC1_TASK)
                if(result?.prepared==true && expectedCommit!=null && authorization!=null && result.ccc1TaskId!=null)
                    editor.putString(TYPED_PREPARED_ID,result.id)
                        .putString(TYPED_PREPARED_SHA,result.hash)
                        .putString(TYPED_PREPARED_COMMIT,expectedCommit)
                        .putString(TYPED_PREPARED_JSON,authorization.task.canonicalJson())
                        .putString(TYPED_PREPARED_CCC1_TASK,result.ccc1TaskId)
                        .putString(CCC1_TASK,result.ccc1TaskId)
            }
            if(result?.patchSha256!=null)editor.putString(LAST_SHA,result.patchSha256)
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
                .remove(JOB_PROJECT)
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
        val sourceSha256 = payload?.optString("sourceSha256")
        val planSha256 = payload?.optString("planSha256")
        val recipeId = payload?.optString("recipe")
        val expectedProject = ContinuumOwnedProject.ofId(prefs.getString(if (projectStatusSlot) STATUS_PROJECT else JOB_PROJECT, null))
            ?: ContinuumOwnedProject.NOTES
        val profile = payload?.optString("toolchainProfile")
        val verification = payload?.optJSONObject("verification")?.optString("verdict")
            ?: payload?.optString("verification")
        val buildStatus = payload?.optString("buildStatus")
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
            versionCode = versionCode,
            sourceSha256 = sourceSha256,
            recipeId = recipeId, expectedProject = expectedProject, planSha256 = planSha256,
            toolchainProfile = profile, verification = verification, buildStatus = buildStatus
        )
        val projectHistoryVerified = outcome == ContinuumMobileResultPolicy.Outcome.PROJECT_STATUS_REPORTED &&
            reportedStatus == "PROJECT_BUILD_STATUS" && verification == "CONFIRMED"
        val text = if (outcome == ContinuumMobileResultPolicy.Outcome.PROJECT_STATUS_REPORTED) {
            ContinuumMobileResultPolicy.projectStatusExplanation(
                if (reportedStatus == "NO_PROJECT_BUILD_REPORT") reportedStatus else buildStatus, verification, profile)
        } else if (outcome == ContinuumMobileResultPolicy.Outcome.STATUS_REPORTED) {
            ContinuumMobileResultPolicy.statusExplanation(reportedStatus)
        } else {
            ContinuumMobileResultPolicy.explanation(outcome)
        }
        val buildProvenance =
            if (outcome == ContinuumMobileResultPolicy.Outcome.BUILD_REPORTED_UNVERIFIED_ON_DEVICE)
                "\nQuell-Commit: $sourceCommit\nPaket: $packageName · VersionCode: $versionCode"
            else ""
        val projectProvenance =
            if (outcome == ContinuumMobileResultPolicy.Outcome.PROJECT_BUILD_REPORTED_UNVERIFIED_ON_DEVICE || projectHistoryVerified)
                "\nM4-Projekt: $packageName · Versionscode: $versionCode" else ""
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
        if (projectStatusSlot) {
            prefs.edit().remove(STATUS_JOB_ID).remove(STATUS_JOB_START).remove(STATUS_PROJECT)
                .putString(STATUS_MESSAGE, text + projectProvenance +
                    if (projectHistoryVerified && artifactHash != null) "\nSHA-256: $artifactHash" else "")
                .commit()
            return
        }
        val editor = prefs.edit()
            .remove(JOB_ID)
            .remove(JOB_OPERATION)
            .remove(JOB_PROJECT)
            .remove(JOB_START)
            .putString(LAST_MESSAGE, text + buildProvenance + projectProvenance + ownerCopyMessage)
            .remove(LAST_SHA)
        if ((outcome == ContinuumMobileResultPolicy.Outcome.BUILD_REPORTED_UNVERIFIED_ON_DEVICE ||
                outcome == ContinuumMobileResultPolicy.Outcome.PROJECT_BUILD_REPORTED_UNVERIFIED_ON_DEVICE || projectHistoryVerified) &&
            artifactHash != null
        ) {
            editor.putString(LAST_SHA, artifactHash)
        }
        editor.commit()
    }
}
