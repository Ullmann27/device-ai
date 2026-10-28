package at.heinz.deviceai

import at.heinz.deviceai.learning.StrictJson

/**
 * One-shot, read-only Termux report for the fixed local specialist test roles.
 * It never interprets stdout as a command, a model answer, a verified patch,
 * or an authorization for Android/owner data changes.
 */
internal object ContinuumMicroAgentBridge {
    const val CALLBACK = "MICRO_AGENT_AUDIT"
    private const val MAX_REPORT_CHARS = 12_000
    private val SHA40 = Regex("[a-f0-9]{40}")
    private val SHA64 = Regex("[a-f0-9]{64}")
    private val BRANCH = Regex("[A-Za-z0-9._/-]{1,150}")
    private val orderedRoles = listOf(
        "gemma" to "GEMMA_IMPORT_AUDITOR",
        "deepseek" to "DEEPSEEK_PROTOCOL_AUDITOR",
        "coding" to "CCC1_CODING_AUDITOR",
        "transfer" to "EXPERIENCE_TRANSFER_AUDITOR",
        "update" to "FOLD7_UPDATE_AUDITOR"
    )
    data class Report(val passed:Boolean, val summary:String)

    fun command(): ContinuumMobileBuildPlan.Command =
        ContinuumMobileBuildPlan.Command(
            ContinuumMobileBuildPlan.NODE_EXECUTABLE,
            listOf(
                ContinuumMobileBuildPlan.PROJECT_DIR + "/scripts/continuum-microagents.mjs",
                "--run", "all"
            ),
            ContinuumMobileBuildPlan.PROJECT_DIR
        )

    fun evaluate(exitCode:Int?, termuxError:Int?, stdout:String?):Report {
        fun invalid() = Report(false,
            "Mini-Agenten: keine gültige oder vollständige Termux-Rückmeldung. Kein Test-Erfolg bestätigt.")
        if (exitCode == null || termuxError != -1 ||
            stdout == null || stdout.length !in 2..MAX_REPORT_CHARS) return invalid()
        val obj = runCatching { StrictJson.parseObject(stdout) }.getOrNull() ?: return invalid()
        if (obj["schema"] != "continuum-local-microagents" || obj["version"] != 1L ||
            obj["mode"] != "DETERMINISTIC_OFFLINE_CONTRACT_CHECKS" ||
            obj["deepSeekCodingConnected"] != false ||
            obj["gemmaNativeInferenceVerified"] != false ||
            obj["noApiCostRequired"] != true ||
            obj["codeGenerationAvailable"] != false ||
            obj["ownerPrivilegesGranted"] != false) return invalid()
        val commit = (obj["sourceCommit"] as? String)?.takeIf { SHA40.matches(it) } ?: return invalid()
        val branch = (obj["branch"] as? String)?.takeIf { BRANCH.matches(it) } ?: return invalid()
        if (branch in setOf("main", "master", "production", "release", "prod")) return invalid()
        val results = obj["results"] as? List<*> ?: return invalid()
        if (results.size !in 1..orderedRoles.size) return invalid()
        for (index in results.indices) {
            val item = results[index] as? Map<*, *> ?: return invalid()
            val (expectedId, expectedRole) = orderedRoles[index]
            val status = item["status"] as? String ?: return invalid()
            if (item["id"] != expectedId || item["role"] != expectedRole ||
                status !in setOf("CONTRACT_TESTS_PASS", "CONTRACT_TESTS_FAIL", "INTERRUPTED") ||
                !(item["durationMs"] is Long) ||
                (item["durationMs"] as Long) < 0L ||
                !SHA64.matches(item["outputSha256"] as? String ?: "") ||
                item["executedNativeModelInference"] != false ||
                item["verifiedCodingRepair"] != false ||
                item["androidDeviceTestsExecuted"] != false) return invalid()
            val exit = item["exitCode"]
            if (exit != null && (exit !is Long || exit !in 0L..255L)) return invalid()
            if (status == "CONTRACT_TESTS_PASS" && exit != 0L) return invalid()
        }
        val complete = obj["completed"] as? Boolean ?: return invalid()
        val allPass = results.size == orderedRoles.size &&
            results.all { (it as Map<*, *>)["status"] == "CONTRACT_TESTS_PASS" }
        if (complete != allPass || (exitCode == 0) != complete) return invalid()
        if (!complete) {
            val last = results.last() as Map<*, *>
            return Report(false,
                "Mini-Agenten: ${results.size} Prüfbereich(e) durchlaufen, " +
                    "Stopp bei ${last["id"]} (${last["status"]}). " +
                    "Kein Modell und kein Coding-Patch bestätigt. Quellstand ${commit.take(12)}.")
        }
        return Report(true,
            "Fünf lokale Prüfagenten abgeschlossen (5/5 Bereiche). " +
                "Nur bestehende Node-Vertragstests; keine Modell-Inferenz, " +
                "kein generierter Code und kein Android-Build. " +
                "Quellstand ${commit.take(12)} · Branch ${branch.take(70)}.")
    }
}
