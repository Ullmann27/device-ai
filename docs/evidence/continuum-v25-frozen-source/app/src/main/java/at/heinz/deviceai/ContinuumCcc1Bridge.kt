package at.heinz.deviceai

import at.heinz.deviceai.learning.StrictJson

/**
 * CCC1 through the existing Termux bridge (`scripts/continuum-mobile-builder.mjs ccc1-*`).
 *
 * Android never sends text for a shell: only the fixed builder script, one fixed CCC1 operation and validated
 * values (test chain, the app's own request id as idempotent order id, a task id). Termux answers with one
 * `continuum-ccc1-bridge` JSON document that is checked strictly here before anything is shown. A repair counts as
 * verified only when the CCC1 status reports the task COMPLETED with an intact patch whose hash matches the
 * verifier's confirmation; even then it is a proposal for the owner's review: nothing is applied, merged or installed.
 */
internal object ContinuumCcc1Bridge {
    enum class Operation(val jobOperation: String, val cli: String, val title: String) {
        COMPILE_REPAIR("CCC1_COMPILE_REPAIR", "ccc1-compile-repair", "CCC1-Vorprüfung"),
        SUBMIT_REPAIR("CCC1_SUBMIT_REPAIR", "ccc1-submit-repair", "CCC1-Reparaturauftrag einreichen"),
        RUN_ONCE("CCC1_RUN_ONCE", "ccc1-run-once", "CCC1: einen Schritt ausführen"),
        STATUS("CCC1_STATUS", "ccc1-status", "CCC1-Status abrufen"),
        ABORT("CCC1_ABORT", "ccc1-abort", "CCC1-Auftrag abbrechen");

        companion object {
            fun ofJobOperation(name: String): Operation? = entries.firstOrNull { it.jobOperation == name }
        }
    }

    enum class Chain(val cli: String) { NODE("node"), ANDROID("android"), JVM("jvm") }

    enum class Outcome { OK, BLOCKED, FAILED, INVALID_RESULT }

    data class Result(
        val outcome: Outcome,
        val operation: Operation,
        val status: String,
        val message: String,
        /** Task the owner can cancel next (submitted, aborted or the newest open one); null if none. */
        val taskId: String? = null,
        /** Hash of a patch that is confirmed AND intact on disk; null for everything else. */
        val verifiedPatchSha256: String? = null
    )

    private val ORDER = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val TASK = Regex("t-[0-9a-f]{16}")
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val COMMIT = Regex("[0-9a-f]{40}")
    private val CODE = Regex("[A-Z][A-Z0-9_]{1,47}")
    private val BRANCH = Regex("[A-Za-z0-9._/-]{1,100}")
    /** Repository-relative file (".github/..." allowed); absolute paths and ".." are refused below. */
    private val FILE = Regex("[A-Za-z0-9._-][A-Za-z0-9._/-]{0,199}")
    private val REPAIR_CONFIRMED = Regex("repair:(node|android|jvm)@([0-9a-f]{12})")
    private val CONTRACT_CONFIRMED = Regex("[A-Za-z0-9._/-]{1,200}#[A-Za-z_][A-Za-z0-9_]{0,63}")
    private val STATES = setOf("DRAFT", "VALIDATED", "APPROVED", "COMPLETED", "ABSTAINED", "FAILED", "ABORTED", "CORRUPT")
    private val TERMINAL = setOf("COMPLETED", "ABSTAINED", "FAILED", "ABORTED")
    private val RUN_OUTCOMES = setOf("VERIFIED_PATCH_PENDING_REVIEW", "DONE_INVESTIGATION", "NO_READY_INVESTIGATION",
        "IDLE", "INTERRUPTED", "ERROR")
    const val MAX_RESULT_CHARS = 32_768
    const val MAX_TASKS = 40

    fun command(operation: Operation, chain: Chain? = null, orderId: String? = null, taskId: String? = null): ContinuumMobileBuildPlan.Command {
        val args = mutableListOf(ContinuumMobileBuildPlan.BUILDER_SCRIPT, operation.cli, "--project", ContinuumMobileBuildPlan.PROJECT_DIR)
        when (operation) {
            Operation.COMPILE_REPAIR -> args += listOf("--chain", requireNotNull(chain) { "Testkette fehlt" }.cli)
            Operation.SUBMIT_REPAIR -> {
                require(orderId != null && ORDER.matches(orderId)) { "Auftrags-ID ist keine UUIDv4" }
                args += listOf("--chain", requireNotNull(chain) { "Testkette fehlt" }.cli, "--order", orderId)
            }
            Operation.ABORT -> {
                require(taskId != null && TASK.matches(taskId)) { "Keine gültige CCC1-Auftragskennung" }
                args += listOf("--task", taskId)
            }
            Operation.RUN_ONCE, Operation.STATUS -> Unit
        }
        return ContinuumMobileBuildPlan.Command(ContinuumMobileBuildPlan.NODE_EXECUTABLE, args.toList(), ContinuumMobileBuildPlan.PROJECT_DIR)
    }

    private fun invalid(operation: Operation, why: String? = null) =
        Result(Outcome.INVALID_RESULT, operation, "INVALID", why ?: "Keine verlässliche CCC1-Rückmeldung; nichts bestätigt.")

    /**
     * Why Termux gave no bridge answer at all: the result channel itself, or fixed markers of our own builder script
     * in stderr (its usage line without CCC1 operations, a missing module). Only matched, never shown; null otherwise.
     */
    fun diagnose(exitCode: Int?, termuxError: Int?, stderr: String?): String? {
        val text = stderr?.take(4096).orEmpty()
        return when {
            termuxError != null && termuxError != -1 ->
                "Termux konnte den Befehl nicht starten (Node.js in Termux installiert, allow-external-apps=true?). Nichts ausgeführt."
            "Mobile Builder: Usage:" in text && "ccc1-" !in text ->
                "Der Termux-Checkout ~/device-ai ist älter als die App und kennt CCC1 noch nicht. Bitte in Termux aktualisieren " +
                    "(Anleitung: docs/CONTINUUM-CCC1-AUF-DEM-GERAET.md). Nichts ausgeführt."
            "Cannot find module" in text && "continuum-mobile-builder.mjs" in text ->
                "Im Termux-Checkout ~/device-ai fehlt das Builder-Skript. Checkout prüfen. Nichts ausgeführt."
            exitCode == 127 -> "Termux fand ein benötigtes Programm nicht (Node.js oder Git). Nichts ausgeführt."
            else -> null
        }
    }

    /** App-owned next steps for refusal codes; the text Termux sends is never displayed. */
    private fun guidance(codes: String): String = buildList {
        if ("CCC1_BRANCH_NOT_ISOLATED" in codes)
            add("Termux-Checkout steht auf main/master/release oder auf keinem Branch – in Termux: bash scripts/continuum-teamleader-termux.sh ccc1-prepare")
        if ("CCC1_WORKTREE_NOT_CLEAN" in codes) add("Ungesicherte Änderungen im Termux-Checkout – in Termux: git -C ~/device-ai status --short")
        if ("CCC1_REQUIRED_FILE" in codes) add("Termux-Checkout ist zu alt für CCC1 – bitte aktualisieren")
        if ("CCC1_NODE_VERSION" in codes) add("Node.js 20 oder neuer nötig – in Termux: pkg upgrade nodejs")
        if ("CCC1_STATE" in codes) add("Zustandsordner liegt im Checkout – den Standardordner verwenden")
    }.joinToString("") { "\n• $it" }

    /** Finding codes named in a refusal (e.g. CCC1_BRANCH_NOT_ISOLATED), never the raw text. */
    private fun findings(text: Any?): String =
        Regex("CCC1_[A-Z_]{3,40}").findAll(text as? String ?: "").map { it.value }.distinct().take(6).joinToString(", ")

    fun evaluate(expected: Operation, exitCode: Int?, termuxError: Int?, stdout: String?, stderr: String? = null): Result {
        val unreadable = invalid(expected, diagnose(exitCode, termuxError, stderr))
        if (exitCode == null || termuxError != -1 || stdout == null || stdout.length > MAX_RESULT_CHARS) return unreadable
        val r = try { StrictJson.parseObject(stdout) } catch (_: IllegalArgumentException) { return unreadable }
        if (r["schema"] != "continuum-ccc1-bridge" || r["version"] != 1L || r["operation"] != expected.cli) return invalid(expected)
        val status = r["status"] as? String ?: return invalid(expected)
        if (status != "OK") {
            val failure = r["failure"] as? Map<*, *> ?: return invalid(expected)
            val code = (failure["code"] as? String)?.takeIf { CODE.matches(it) } ?: return invalid(expected)
            val blocked = status.startsWith("BLOCKED_")
            if (exitCode == 0 || !CODE.matches(status) || !(blocked || status == "FAILED_CCC1")) return invalid(expected)
            val named = findings(failure["message"]).ifEmpty { code }
            return Result(if (blocked) Outcome.BLOCKED else Outcome.FAILED, expected, status,
                (if (blocked) "CCC1 hat nichts ausgeführt: " else "CCC1-Schritt fehlgeschlagen: ") + named + "." + guidance(named + " " + code))
        }
        if (exitCode != 0) return invalid(expected)
        val result = r["result"] as? Map<*, *> ?: return invalid(expected)
        return when (expected) {
            Operation.COMPILE_REPAIR -> compiled(result)
            Operation.SUBMIT_REPAIR -> submitted(result)
            Operation.RUN_ONCE -> ran(result)
            Operation.STATUS -> statusOf(result)
            Operation.ABORT -> aborted(result)
        } ?: invalid(expected)
    }

    private fun compiled(r: Map<*, *>): Result? {
        if (r["schema"] != "continuum-ccc1-compiled-goal" || r["kind"] != "REPAIR") return null
        val pf = r["preflight"] as? Map<*, *>
        return when (r["status"]) {
            "READY" -> {
                val commit = (pf?.get("sourceCommit") as? String)?.takeIf { COMMIT.matches(it) } ?: return null
                val branch = (pf["checkedBranch"] as? String)?.takeIf { BRANCH.matches(it) } ?: return null
                val chain = (r["chain"] as? String)?.takeIf { c -> Chain.entries.any { it.cli == c } } ?: return null
                Result(Outcome.OK, Operation.COMPILE_REPAIR, "READY",
                    "Vorprüfung bestanden: Testkette $chain am Commit ${commit.take(12)} auf Branch $branch. Noch nichts ausgeführt.")
            }
            "BLOCKED" -> {
                val named = (pf?.get("findings") as? List<*>)?.joinToString(", ") { findings(it) }?.ifEmpty { null } ?: "unbekannt"
                Result(Outcome.BLOCKED, Operation.COMPILE_REPAIR, "BLOCKED", "Vorprüfung blockiert: $named. Nichts ausgeführt." + guidance(named))
            }
            "NEEDS_OWNER" -> Result(Outcome.BLOCKED, Operation.COMPILE_REPAIR, "NEEDS_OWNER", "Bitte eine Testkette wählen.")
            else -> null
        }
    }

    private fun submitted(r: Map<*, *>): Result? {
        val task = (r["taskId"] as? String)?.takeIf { TASK.matches(it) } ?: return null
        val duplicate = r["duplicate"] as? Boolean ?: return null
        val commit = (r["sourceCommit"] as? String)?.takeIf { COMMIT.matches(it) } ?: return null
        if ((r["branch"] as? String)?.let { BRANCH.matches(it) } != true) return null
        return Result(Outcome.OK, Operation.SUBMIT_REPAIR, if (duplicate) "DUPLICATE" else "SUBMITTED",
            if (duplicate) "Dieser Auftrag ist schon als $task angelegt; nichts wurde doppelt angelegt oder ausgeführt."
            else "CCC1-Auftrag $task für Commit ${commit.take(12)} angelegt. Noch nichts ausgeführt.", taskId = task)
    }

    private fun ran(r: Map<*, *>): Result? {
        val outcome = (r["outcome"] as? String)?.takeIf { it in RUN_OUTCOMES } ?: return null
        val task = r["taskId"] as? String
        if (task != null && !TASK.matches(task)) return null
        val ccc1 = r["ccc1"] as? String
        if (ccc1 != null && !CODE.matches(ccc1)) return null
        if (outcome == "VERIFIED_PATCH_PENDING_REVIEW" && (ccc1 != "COMPLETED" || task == null)) return null
        val text = when (outcome) {
            "VERIFIED_PATCH_PENDING_REVIEW" -> "CCC1 meldet für $task einen unabhängig geprüften Reparatur-Patch. " +
                "Bestätigt ist er erst, wenn der Status ihn als unversehrt zeigt. Nichts wurde übernommen."
            "DONE_INVESTIGATION" -> "CCC1 hat $task ohne Patch beendet ($ccc1). Nichts bestätigt."
            "NO_READY_INVESTIGATION" -> "Ein CCC1-Auftrag wartet auf deine Entscheidung."
            "IDLE" -> "Kein offener CCC1-Auftrag."
            "INTERRUPTED" -> "CCC1-Schritt nicht abgeschlossen (${ccc1 ?: "unterbrochen"}). Nichts bestätigt; später erneut ausführen."
            else -> "CCC1-Schritt mit Fehler beendet. Nichts bestätigt; Status prüfen."
        }
        return Result(Outcome.OK, Operation.RUN_ONCE, outcome, text, taskId = task)
    }

    private fun statusOf(r: Map<*, *>): Result? {
        if (r["schema"] != "continuum-ccc1-status" || r["version"] != 1L) return null
        val raw = r["tasks"] as? List<*> ?: return null
        if (raw.size > MAX_TASKS) return null
        val count = r["taskCount"] as? Long ?: raw.size.toLong()
        val lines = mutableListOf<String>()
        var verified: String? = null
        var open: String? = null
        for (item in raw) {
            val t = item as? Map<*, *> ?: return null
            val id = (t["taskId"] as? String)?.takeIf { TASK.matches(it) } ?: return null
            val state = (t["state"] as? String)?.takeIf { it in STATES } ?: return null
            val waiting = t["waitingForOwner"] as? Boolean ?: return null
            val confirmed = t["confirmed"] as? String
            if (confirmed != null && !REPAIR_CONFIRMED.matches(confirmed) && !CONTRACT_CONFIRMED.matches(confirmed)) return null
            if (state !in TERMINAL && state != "CORRUPT") open = id
            var line = "$id: $state" + if (waiting) " – wartet auf dich" else ""
            val review = t["review"] as? Map<*, *>
            if (review != null) {
                val sha = (review["patchSha256"] as? String)?.takeIf { SHA256.matches(it) } ?: return null
                val intact = review["intact"] as? Boolean ?: return null
                val files = (review["files"] as? List<*>)?.map { f -> (f as? String)?.takeIf { FILE.matches(it) && ".." !in it } ?: return null }
                    ?: return null
                val strategy = (review["strategy"] as? String)?.takeIf { CODE.matches(it) }
                val matches = REPAIR_CONFIRMED.matchEntire(confirmed ?: "")?.groupValues?.get(2) == sha.take(12)
                line += if (state == "COMPLETED" && intact && matches) {
                    verified = sha
                    "\n  Geprüfter Patch ${sha.take(12)} (${files.joinToString(", ").take(300)}" +
                        (if (strategy != null) "; $strategy" else "") + "). NICHT übernommen – bitte in Termux prüfen."
                } else "\n  Patch-Datei fehlt oder wurde verändert – NICHT als geprüft werten."
            }
            lines += line
        }
        val head = "$count CCC1-Auftrag/Aufträge" + if (raw.size < count) " (neueste ${raw.size})" else ""
        return Result(Outcome.OK, Operation.STATUS, "STATUS", (listOf(head) + lines.takeLast(5)).joinToString("\n"),
            taskId = open, verifiedPatchSha256 = verified)
    }

    private fun aborted(r: Map<*, *>): Result? {
        val task = (r["taskId"] as? String)?.takeIf { TASK.matches(it) } ?: return null
        val state = (r["state"] as? String)?.takeIf { it in STATES } ?: return null
        val job = (r["job"] as? Map<*, *>)?.get("status") as? String
        if (job != null && !CODE.matches(job)) return null
        val text = when (r["journal"]) {
            "ABORTED" -> "CCC1-Auftrag $task abgebrochen." + if (job != null) " Job: $job." else ""
            "ALREADY_ENDED" -> "CCC1-Auftrag $task war bereits beendet ($state); nichts geändert."
            "WRITER_ACTIVE" -> "CCC1 rechnet gerade an $task. Der laufende Job wurde gestoppt (${job ?: "kein Job"}); " +
                "den Abbruch nach dem Lauf erneut senden."
            else -> return null
        }
        return Result(Outcome.OK, Operation.ABORT, r["journal"] as String, text, taskId = task.takeIf { r["journal"] == "WRITER_ACTIVE" })
    }
}
