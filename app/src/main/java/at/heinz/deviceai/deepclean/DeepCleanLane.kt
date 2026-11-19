package at.heinz.deviceai.deepclean

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerLaneResult
import at.heinz.deviceai.briefing.ContinuumSystemFacts
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The small surface the storage lane needs from the Activity. Chat, busy lane and owner turn come from the existing
 * paths (the briefing adapter); the file system, Android's volume figures and Android's system dialogs are the only
 * device boundaries. Every default is the honest „not available“.
 */
internal interface DeepCleanHost {
    val sessionId: String
    /** Number of owner turns shown so far in this chat; unchanged means „the very next turn“. */
    val ownerTurn: Long
    val assistantName: String?
    fun ownerName(): String?
    fun personalTone(): Boolean = true
    fun nowMillis(): Long

    fun acceptOwnerTurn(request: String)
    /** A reply that did not come from the model: [shown] in the chat, [spoken] aloud. */
    fun answer(request: String?, spoken: String, shown: String = spoken)
    fun activity(line: String)
    fun idle(): Boolean
    fun later(action: () -> Unit)
    /** Runs [work] off the UI thread as the single busy job; [done] is always called on the UI thread. */
    fun <T> runJob(request: String, label: String, work: () -> T, done: (Result<T>) -> Unit)
    /** The stage snapshot changed (progress, phase). */
    fun stageChanged() {}
    /** How the owner's order ended – for the mission layer. */
    fun outcome(outcome: OwnerLaneOutcome) {}

    fun files(): DeepCleanFiles
    fun access(): DeepCleanAccess
    /** Opens Android's page „Zugriff auf alle Dateien“; false = it could not be opened. */
    fun openFileAccessSettings(): Boolean = false
    /** Opens Android's own storage manager (ACTION_MANAGE_STORAGE); false = not available. */
    fun openStorageSettings(): Boolean = false
    /** The data volume as Android measures it now; null = not readable. */
    fun volume(): DeepCleanVolume?
    /** Keys of files that are in use right now (running downloads, the open document, the update download). */
    fun activeFiles(): Set<String> = emptySet()
    /** PowerManager's thermal status 0..6, or null. */
    fun thermalStatus(): Int? = null
    /** The app and device state for the optimisation (temperature, memory, model, running work). */
    fun systemFacts(): ContinuumSystemFacts? = null
    /** The memory this app itself uses (PSS), or null. */
    fun ownMemoryBytes(): Long? = null
    /** Unloads the on-device model (the existing owner action); null = started, otherwise why not. */
    fun unloadLocalModel(): String? = "Das Entladen des Modells ist hier nicht verfügbar."
    /**
     * Android's trash dialog for [items] (MediaStore.createTrashRequest; [restore] = put back). [done] on the UI
     * thread with Android's answer. A host without the dialog answers „not granted“.
     */
    fun trash(items: List<DeepCleanItem>, restore: Boolean, done: (DeepCleanSystemResult) -> Unit) =
        done(DeepCleanSystemResult(false, "Der Android-Papierkorb ist hier nicht verfügbar."))
    /** Android's dialog that clears the cache folders of all apps (ACTION_CLEAR_APP_CACHE). */
    fun clearAllAppCaches(done: (DeepCleanSystemResult) -> Unit) = done(DeepCleanSystemResult(false, "Dieser Android-Dialog ist hier nicht verfügbar."))
    /** Opens the native screen „Speicher & Bereinigung“. */
    fun openPanel() {}
    fun store(): DeepCleanStore
}

/**
 * The deterministic data the Command03 stage renders (no file names, no paths – counts, measured bytes, phase).
 * Written by the lane on the UI thread and by the running job; read by the snapshot path.
 */
internal class DeepCleanStage {
    enum class Phase { IDLE, SCAN, CLASSIFY, CLEAN, CHOICE, DONE, STOPPED, FAILED }

    @Volatile var session: String? = null
    @Volatile var runId: String? = null
    @Volatile var mode: DeepCleanMode? = null
    @Volatile var phase: Phase = Phase.IDLE
    val progressDone = AtomicInteger(0)
    val progressTotal = AtomicInteger(0)
    @Volatile var unit: String = "files"
    @Volatile var categories: List<Triple<DeepCleanCategory, Int, Long>> = emptyList()
    @Volatile var levels: Map<DeepCleanLevel, Pair<Int, Long>> = emptyMap()
    @Volatile var before: DeepCleanVolume? = null
    @Volatile var after: DeepCleanVolume? = null
    @Volatile var removedFiles: Int = 0
    @Volatile var removedBytes: Long = 0
    @Volatile var choiceCount: Int = 0
    @Volatile var choiceBytes: Long = 0
    @Volatile var sharedAccess: DeepCleanAccess.Shared? = null
    @Volatile var updatedAtMillis: Long = 0

    fun start(session: String, mode: DeepCleanMode, now: Long) {
        this.session = session; this.mode = mode; runId = null; phase = Phase.SCAN; progressDone.set(0); progressTotal.set(0)
        unit = "files"; categories = emptyList(); levels = emptyMap(); before = null; after = null; removedFiles = 0; removedBytes = 0
        choiceCount = 0; choiceBytes = 0; updatedAtMillis = now
    }

    /** The `deepClean` record of the Command03 snapshot, or null when there is nothing real to show in [session]. */
    fun snapshot(session: String): JSONObject? {
        if (phase == Phase.IDLE || this.session != session) return null
        fun volume(v: DeepCleanVolume?) = v?.let { JSONObject().put("freeBytes", it.freeBytes).put("totalBytes", it.totalBytes).put("atMs", it.atMillis) }
            ?: JSONObject.NULL
        return JSONObject().put("schema", SCHEMA).put("runId", runId ?: JSONObject.NULL).put("mode", mode?.name ?: JSONObject.NULL)
            .put("phase", phase.name)
            .put("progress", JSONObject().put("done", progressDone.get()).put("total", progressTotal.get()).put("unit", unit))
            .put("categories", JSONArray().apply {
                categories.forEach { (category, count, bytes) ->
                    put(JSONObject().put("key", category.name).put("label", category.label).put("count", count).put("bytes", bytes))
                }
            })
            .put("levels", JSONObject().apply {
                DeepCleanLevel.entries.forEach { level ->
                    val value = levels[level] ?: (0 to 0L)
                    put(level.name, JSONObject().put("count", value.first).put("bytes", value.second))
                }
            })
            .put("before", volume(before)).put("after", volume(after))
            .put("removed", JSONObject().put("files", removedFiles).put("bytes", removedBytes).put("source", "sum_of_removed_file_sizes"))
            .put("choice", JSONObject().put("count", choiceCount).put("bytes", choiceBytes))
            .put("sharedAccess", sharedAccess?.name ?: JSONObject.NULL)
            .put("updatedAtMs", updatedAtMillis)
    }

    companion object { const val SCHEMA = "continuum.deepclean.v1" }
}

/** What one job established; built off the UI thread, reported on it. */
internal class DeepCleanOutcome(
    val command: DeepCleanCommand,
    val scan: DeepCleanScan,
    val findings: Map<String, DeepCleanFinding>,
    val run: DeepCleanRun?,
    val choice: List<DeepCleanItem>,
    val before: DeepCleanVolume?,
    val after: DeepCleanVolume?,
    val audit: DeepCleanAudit.Result?,
    val pruned: Int,
    val stopped: Boolean,
    val access: DeepCleanAccess,
    val learnedSkipped: Int
)

/**
 * The storage lane („Speicher & Bereinigung“, owner order 19.11.2026). It decides and words; scanning, removing and
 * Android's dialogs go through [DeepCleanHost]. Free of Android, so every flow runs in host tests.
 *
 * Fixed rules: only [DeepCleanMode.mayRemove] orders without a „nicht löschen“ ever reach [DeepCleanExecutor]; GREEN
 * is decided by [DeepCleanClassifier] alone; YELLOW only after the owner chose numbered items and said yes, and then
 * through Android's own trash dialog; RED never. Every number said is measured; a refusal of Android is said as such.
 */
internal class DeepCleanDirector(private val host: DeepCleanHost) {
    private val journal by lazy { DeepCleanJournal(host.store(), host::nowMillis) }
    private val learning by lazy { DeepCleanLearning(host.store()) }
    private val forecast by lazy { DeepCleanForecast(host.store()) }
    /** Built on the files of this moment: the shared root comes and goes with Android's file access. */
    private val executor: DeepCleanExecutor get() = DeepCleanExecutor(host.files(), journal, host::nowMillis)
    val stage = DeepCleanStage()
    private val stopFlag = AtomicBoolean(false)
    @Volatile private var running = false
    private var recovered = false

    /** The lane's open question: a numbered selection or a confirmation, for the owner's next turns in this chat. */
    private inner class Pending(val run: DeepCleanRun?, val items: List<DeepCleanItem>, val findings: Map<String, DeepCleanFinding>,
                                val readOnly: Boolean, val offerAppCaches: Boolean) {
        val session = host.sessionId
        var askedAt = host.nowMillis()
        var shown: List<DeepCleanItem> = items
        var chosen: List<DeepCleanItem>? = null
        var appCachesChosen = false
        val open: Boolean get() = session == host.sessionId && host.nowMillis() - askedAt in 0..PENDING_MILLIS
    }
    private var pending: Pending? = null
    private var pendingUnload: Long? = null
    /** The last result shown, for the native panel. */
    @Volatile var lastSummary: String? = null
        private set

    val busy: Boolean get() = running
    /** The items currently offered for selection (panel). */
    fun offered(): List<DeepCleanItem> = pending?.takeIf { it.open }?.shown.orEmpty()
    fun offeredReadOnly(): Boolean = pending?.readOnly ?: true
    fun finding(item: DeepCleanItem): DeepCleanFinding? = pending?.findings?.get(item.key)

    // ── owner turns ─────────────────────────────────────────────────────────────────────────────────────────

    /** While a job runs: „Stopp“ ends it before the next file. true = handled. */
    fun intercept(request: String): Boolean {
        if (!running) return false
        if (DeepCleanCommands.reply(request, host.assistantName) != DeepCleanCommands.Reply.Stop) return false
        host.acceptOwnerTurn(request)
        stop()
        host.answer(request, "Ich stoppe sofort – vor der nächsten Datei. Was bis jetzt entfernt ist, sage ich dir gleich genau.")
        return true
    }

    /** The owner's STOP control (button, stop word elsewhere). */
    fun stop() {
        if (running) stopFlag.set(true)
        pending = null
        pendingUnload = null
    }

    /** true = [request] was for this lane and is handled. Call on the UI thread while no other job runs. */
    fun handle(request: String): Boolean {
        if (answerPending(request)) return true
        val command = DeepCleanCommands.parse(request, host.assistantName) ?: return false
        clearPending()
        host.acceptOwnerTurn(request)
        start(request, command)
        return true
    }

    /** Starts [command] as if the owner had said it (native panel). */
    fun start(request: String, command: DeepCleanCommand) {
        val untold = recoverOnce()
        when (command.mode) {
            DeepCleanMode.DECLINE -> answer(request, DeepCleanText.DECLINE, done(command, null, null, 0, 0L, 0))
            DeepCleanMode.CLARIFY -> {
                val all = DeepCleanCommands.plain(request, host.assistantName)?.contains(Regex("\\balle|alles|fotos|bilder|videos|dokumente|musik")) == true
                host.answer(request, untold + if (all) DeepCleanText.CLARIFY_ALL else DeepCleanText.CLARIFY)
                host.outcome(OwnerLaneOutcome.NeedsOwner(OwnerLaneOutcome.Need.DETAIL, DeepCleanText.CLARIFY))
            }
            DeepCleanMode.FORGET_PROTECTION -> {
                val n = learning.forget()
                host.answer(request, untold + if (n == 0) "Ich hatte mir keine Ausnahmen gemerkt." else
                    "In Ordnung. Ich habe ${if (n == 1) "eine gemerkte Ausnahme" else "$n gemerkte Ausnahmen"} vergessen und schlage wieder alles vor, " +
                    "was in Frage kommt – gelöscht wird weiterhin nur, was du auswählst.")
            }
            DeepCleanMode.PANEL -> { host.openPanel(); host.answer(request, untold + "Ich öffne „Speicher & Bereinigung“.") }
            DeepCleanMode.REPORT -> report(request, untold)
            DeepCleanMode.RESTORE -> restore(request, untold)
            DeepCleanMode.OPTIMISE -> optimise(request, untold)
            else -> scanJob(request, command, untold)
        }
    }

    private fun done(command: DeepCleanCommand, before: DeepCleanVolume?, after: DeepCleanVolume?, files: Int, bytes: Long, offered: Int) =
        OwnerLaneOutcome.Done(OwnerLaneResult.Storage(command.mode.name, before?.freeBytes, after?.freeBytes, files, bytes, offered))

    private fun answer(request: String, text: String, outcome: OwnerLaneOutcome) {
        host.answer(request, text)
        host.outcome(outcome)
    }

    private fun clearPending() { pending = null; pendingUnload = null }

    /** A restarted run is reported once – before anything else this lane says. */
    private fun recoverOnce(): String {
        if (recovered) return ""
        recovered = true
        val files = host.files()
        val untold = runCatching { journal.recover { files.stat(it.root, it.relative) } }.getOrDefault(emptyList())
        if (untold.isEmpty()) return ""
        val removed = untold.sumOf { it.removed.size }
        val bytes = untold.sumOf { it.removedBytes }
        val unconfirmed = untold.sumOf { run -> run.items.count { it.state == DeepCleanItemState.GONE_UNCONFIRMED } }
        return "Die letzte Bereinigung wurde unterbrochen (Neustart). Bis dahin entfernt: ${DeepCleanText.files(removed)}, ${DeepCleanText.bytes(bytes)}." +
            (if (unconfirmed > 0) " ${DeepCleanText.files(unconfirmed).replaceFirstChar { it.uppercase() }} " + (if (unconfirmed == 1) "war" else "waren") +
                " danach weg, ohne dass ich das Löschen bestätigen konnte – das zähle ich nicht mit." else "") +
            " Fortgesetzt habe ich nichts. "
    }

    // ── scan, clean, choose ─────────────────────────────────────────────────────────────────────────────────

    private fun rootsFor(command: DeepCleanCommand): Set<DeepCleanRoot> = when (command.mode) {
        DeepCleanMode.TARGETED -> setOf(DeepCleanRoot.SHARED)
        else -> DeepCleanRoot.entries.toSet()
    }

    private fun scanJob(request: String, command: DeepCleanCommand, untold: String) {
        val name = host.ownerName()
        val personal = host.personalTone()
        stopFlag.set(false)
        running = true
        stage.start(host.sessionId, command.mode, host.nowMillis())
        host.stageChanged()
        host.activity("SPEICHER · ${command.code} · nur festgelegte Regeln, kein Modell" + if (command.mayRemove) "" else " · nichts wird gelöscht")
        val withDuplicates = command.mode == DeepCleanMode.DEEP_ANALYSE || command.mode == DeepCleanMode.DEEP_CLEAN ||
            (command.mode == DeepCleanMode.TARGETED && (command.target == DeepCleanGroup.DUPLICATES || command.name == null))
        host.runJob(request, if (command.mayRemove) "Speicher bereinigen" else "Speicher prüfen", {
            work(command, withDuplicates)
        }) { result ->
            running = false
            val outcome = result.getOrElse { error ->
                stage.phase = if (error is CancellationException) DeepCleanStage.Phase.STOPPED else DeepCleanStage.Phase.FAILED
                host.stageChanged()
                val text = if (error is CancellationException) "Gestoppt. Gelöscht habe ich dabei nichts – die Suche lief noch."
                    else "Die Prüfung ist fehlgeschlagen: ${error.message ?: error.javaClass.simpleName}. Gelöscht habe ich nichts."
                host.answer(request, untold + text)
                host.outcome(if (error is CancellationException) OwnerLaneOutcome.Stopped else OwnerLaneOutcome.Failed(text))
                return@runJob
            }
            present(request, outcome, untold, name, personal)
        }
    }

    /** Runs off the UI thread. The only place where a removal can start, and only for [DeepCleanCommand.mayRemove]. */
    private fun work(command: DeepCleanCommand, withDuplicates: Boolean): DeepCleanOutcome {
        val files = host.files()
        val now = host.nowMillis()
        val access = host.access()
        stage.sharedAccess = access.shared
        val before = host.volume()
        stage.before = before
        before?.let { forecast.add(DeepCleanSample(it.atMillis, it.freeBytes, it.totalBytes)) }
        val cancelled = { stopFlag.get() }
        val scan = DeepCleanScanner.scan(files, rootsFor(command), now, host.activeFiles(), withDuplicates, cancelled,
            throttled = { (host.thermalStatus() ?: 0) >= HOT_STATUS }) { seen ->
            stage.progressDone.set(seen); stage.updatedAtMillis = host.nowMillis(); host.stageChanged()
        }
        stage.phase = DeepCleanStage.Phase.CLASSIFY
        // The owner's restrictions and the protections he taught narrow the verdicts – never the other way.
        var learnedSkipped = 0
        val narrowed = scan.findings.map { finding ->
            when {
                finding.level == DeepCleanLevel.RED || finding.level == DeepCleanLevel.EXCLUDED -> finding
                DeepCleanClassifier.protectedBy(finding, command.keep) -> finding.copy(level = DeepCleanLevel.EXCLUDED, reason = "Von dir ausgenommen.")
                finding.level == DeepCleanLevel.YELLOW && (learning.protects(DeepCleanClassifier.folderKey(finding.entry)) ||
                    DeepCleanGroup.entries.any { learning.protects(DeepCleanLearning.groupKey(it)) && DeepCleanClassifier.protectedBy(finding, setOf(it)) }) -> {
                    learnedSkipped++
                    finding.copy(level = DeepCleanLevel.EXCLUDED, reason = "Das hast du schon mehrmals behalten – ich schlage es nicht mehr vor.")
                }
                else -> finding
            }
        }
        val targeted = when {
            command.mode != DeepCleanMode.TARGETED -> narrowed
            command.name != null -> narrowed.map { finding ->
                if (!finding.entry.name.equals(command.name, ignoreCase = true) || finding.level == DeepCleanLevel.EXCLUDED) finding
                else if (finding.level == DeepCleanLevel.RED && finding.category in NAMEABLE)
                    finding.copy(level = DeepCleanLevel.YELLOW, origin = "von dir genau benannt", recovery = DeepCleanRecovery.TRASH,
                        reason = "Persönliche Datei, die du genau benannt hast – nur nach deiner Bestätigung.")
                else finding
            }
            else -> narrowed
        }
        val scanNarrowed = scan.copy(findings = targeted)
        val byCategory = scanNarrowed.byCategory()
        stage.categories = byCategory.entries.sortedByDescending { it.value.second }.map { Triple(it.key, it.value.first, it.value.second) }
        stage.levels = targeted.groupBy { it.level }.mapValues { (_, list) -> list.size to list.sumOf { it.entry.sizeBytes } }
        // Only the deep modes and a targeted order offer YELLOW files for selection; a safe clean and an analysis
        // only say how many there would be.
        val offersChoice = command.mode == DeepCleanMode.DEEP_CLEAN || command.mode == DeepCleanMode.DEEP_ANALYSE ||
            command.mode == DeepCleanMode.TARGETED
        val choice = targeted.filter { offersChoice && it.level == DeepCleanLevel.YELLOW && inTarget(command, it) }
            .sortedWith(compareByDescending<DeepCleanFinding> { it.entry.sizeBytes }.thenBy { it.entry.relative })
        val findingsByKey = targeted.associateBy { it.entry.key }
        if (!command.mayRemove) {
            stage.phase = if (choice.isNotEmpty() && command.mode != DeepCleanMode.ANALYSE) DeepCleanStage.Phase.CHOICE else DeepCleanStage.Phase.DONE
            stage.choiceCount = choice.size; stage.choiceBytes = choice.sumOf { it.entry.sizeBytes }
            stage.after = before
            host.stageChanged()
            return DeepCleanOutcome(command, scanNarrowed, findingsByKey, null, choice.take(MAX_CHOICE).mapIndexed { i, f -> DeepCleanItem.of(i + 1, f) },
                before, null, null, 0, scan.complete.not() && stopFlag.get(), access, learnedSkipped)
        }
        // Self-maintenance of the lane's own records (E4).
        val pruned = journal.prune()
        val green = if (command.mode == DeepCleanMode.TARGETED) emptyList() else targeted.filter { it.level == DeepCleanLevel.GREEN }
        val run = journal.begin(command.mode, green + choice.take(MAX_CHOICE), before?.freeBytes)
        stage.runId = run.id
        stage.phase = DeepCleanStage.Phase.CLEAN
        stage.unit = "files"
        stage.progressDone.set(0)
        stage.progressTotal.set(green.size)
        host.stageChanged()
        val prints = DeepCleanAudit.fingerprint(files, DeepCleanAudit.sample(targeted), cancelled)
        executor.removeGreen(run, cancelled) { doneCount, total ->
            stage.progressDone.set(doneCount); stage.progressTotal.set(total); stage.removedFiles = run.removed.size
            stage.removedBytes = run.removedBytes; stage.updatedAtMillis = host.nowMillis(); host.stageChanged()
        }
        val audit = DeepCleanAudit.verify(files, prints)
        val after = host.volume()
        val yellow = run.items.filter { it.level == DeepCleanLevel.YELLOW && it.state == DeepCleanItemState.PLANNED }
        if (yellow.isEmpty()) executor.finish(run, after?.freeBytes) else { run.freeAfterBytes = after?.freeBytes; journal.save() }
        stage.after = after
        stage.removedFiles = run.removed.size
        stage.removedBytes = run.removedBytes
        stage.choiceCount = yellow.size
        stage.choiceBytes = yellow.sumOf { it.sizeBytes }
        stage.phase = when {
            run.stopped -> DeepCleanStage.Phase.STOPPED
            yellow.isNotEmpty() -> DeepCleanStage.Phase.CHOICE
            else -> DeepCleanStage.Phase.DONE
        }
        stage.updatedAtMillis = host.nowMillis()
        host.stageChanged()
        return DeepCleanOutcome(command, scanNarrowed, findingsByKey, run, yellow, before, after, audit, pruned, run.stopped, access, learnedSkipped)
    }

    private fun inTarget(command: DeepCleanCommand, finding: DeepCleanFinding): Boolean {
        if (command.mode != DeepCleanMode.TARGETED) return true
        if (command.name != null) return finding.entry.name.equals(command.name, ignoreCase = true)
        return when (command.target) {
            DeepCleanGroup.APKS -> finding.category == DeepCleanCategory.APK
            DeepCleanGroup.DUPLICATES -> finding.category == DeepCleanCategory.DUPLICATE
            DeepCleanGroup.PARTIAL -> finding.category == DeepCleanCategory.PARTIAL_DOWNLOAD
            DeepCleanGroup.LARGE -> finding.category == DeepCleanCategory.LARGE_DOWNLOAD
            else -> false
        }
    }

    private fun present(request: String, outcome: DeepCleanOutcome, untold: String, name: String?, personal: Boolean) {
        val command = outcome.command
        val run = outcome.run
        val removed = run?.removed.orEmpty()
        val removedBytes = run?.removedBytes ?: 0L
        if (run != null) { run.reported = outcome.choice.isEmpty(); journal.save() }
        val parts = ArrayList<String>()
        val lead = DeepCleanText.lead(name, personal)
        parts += untold.trim()
        when (command.mode) {
            DeepCleanMode.SAFE_CLEAN, DeepCleanMode.DEEP_CLEAN -> {
                parts += lead + (if (outcome.stopped) "Gestoppt." else "Ich habe deinen Speicher geprüft und nur entfernt, was eindeutig entbehrlich ist. " +
                    "Deine persönlichen Dateien habe ich nicht angefasst.")
                parts += DeepCleanText.removedLine(removed.size, removedBytes)
                val changed = run?.items?.count { it.state == DeepCleanItemState.CHANGED } ?: 0
                if (changed > 0) parts += "${DeepCleanText.files(changed)} hatte${if (changed == 1) "" else "n"} sich seit der Prüfung verändert und blieb${if (changed == 1) "" else "en"} deshalb."
                val failed = run?.items?.count { it.state == DeepCleanItemState.FAILED } ?: 0
                if (failed > 0) parts += "${DeepCleanText.files(failed)} hat Android nicht löschen lassen."
                DeepCleanText.volumeLine(outcome.before, outcome.after)?.let(parts::add)
                if (removed.isNotEmpty() && outcome.before != null && outcome.after != null) parts += DeepCleanText.DIFFERENCE_NOTE
                DeepCleanText.auditLine(outcome.audit)?.let(parts::add)
            }
            DeepCleanMode.TARGETED -> parts += lead.ifEmpty { "" } + "Ich habe gesucht" + (command.target?.let { " nach ${it.label}" } ?: command.name?.let { " nach „$it“" } ?: "") + "."
            else -> {
                parts += lead + "Ich habe deinen Speicher " + (if (command.mode == DeepCleanMode.DEEP_ANALYSE) "gründlich " else "") + "geprüft und nichts gelöscht."
                DeepCleanText.volumeLine(outcome.before, null)?.let(parts::add)
            }
        }
        DeepCleanText.categories(outcome.scan.byCategory().filterKeys { it !in setOf(DeepCleanCategory.OWN_PROTECTED, DeepCleanCategory.LINK) })
            ?.takeIf { command.mode != DeepCleanMode.TARGETED }?.let(parts::add)
        val yellowCount = outcome.scan.level(DeepCleanLevel.YELLOW).size
        if ((command.mode == DeepCleanMode.SAFE_CLEAN || command.mode == DeepCleanMode.ANALYSE) && yellowCount > 0)
            parts += "Weitere ${DeepCleanText.files(yellowCount)} kämen in Frage (große Downloads, Installationsdateien, Duplikate) – " +
                "die lösche ich nie von selbst; „Mach eine Tiefenbereinigung“ zeigt sie dir zur Auswahl."
        if (!command.mayRemove && command.mode != DeepCleanMode.TARGETED) {
            val green = outcome.scan.level(DeepCleanLevel.GREEN)
            if (green.isNotEmpty()) parts += "Sicher entbehrlich wären ${DeepCleanText.files(green.size)} von Continuum selbst, " +
                "${DeepCleanText.bytes(green.sumOf { it.entry.sizeBytes })} – die entferne ich nur auf „Bereinige mein Handy“."
        }
        outcome.scan.duplicates?.takeIf { command.mode != DeepCleanMode.ANALYSE }?.let { dup ->
            if (!dup.complete) parts += if (dup.stoppedByHeat) "Die Suche nach Duplikaten habe ich wegen der Gerätetemperatur früher beendet; sie ist unvollständig."
                else "Die Suche nach Duplikaten hat ihre Grenze erreicht und ist unvollständig."
            parts += "Duplikate erkenne ich nur an exakt gleichem Inhalt (SHA-256); ähnliche Fotos zählen nicht dazu."
        }
        DeepCleanText.accessLine(outcome.access)?.let(parts::add)
        if (command.mode == DeepCleanMode.ANALYSE || command.mode == DeepCleanMode.DEEP_ANALYSE) {
            DeepCleanText.otherAppsLine(outcome.access)?.let(parts::add)
            parts += DeepCleanText.forecastLine(forecast.outlook())
        }
        if (outcome.learnedSkipped > 0) parts += "${DeepCleanText.files(outcome.learnedSkipped)} schlage ich nicht mehr vor, weil du sie schon mehrmals behalten hast."
        if (command.unsure) parts += "Deinen Zusatz habe ich nicht sicher verstanden – deshalb habe ich nur geprüft und nichts gelöscht."
        if (outcome.pruned > 0) parts += "Meine eigenen alten Bereinigungsprotokolle habe ich aufgeräumt (${outcome.pruned})."

        val offerCaches = outcome.access.clearCacheDialog && command.mayRemove && command.mode == DeepCleanMode.DEEP_CLEAN
        val shown = ArrayList<String>()
        val readOnly = !command.mayRemove
        val choice = outcome.choice
        if (choice.isNotEmpty() && command.mode != DeepCleanMode.ANALYSE || offerCaches) {
            val total = choice.sumOf { it.sizeBytes }
            if (choice.isNotEmpty()) parts += (if (readOnly) "Zum Ansehen" else "Zur Auswahl") + " – nur mit deiner Zustimmung: ${DeepCleanText.files(choice.size)}, " +
                "zusammen ${DeepCleanText.bytes(total)}."
            choice.forEachIndexed { index, item -> shown += DeepCleanText.line(index + 1, item, outcome.findings[item.key]) }
            if (offerCaches) shown += "${choice.size + 1}. Zwischenspeicher aller Apps leeren · Android fragt dich selbst · Größe meldet Android vorher nicht"
            if (!readOnly) parts += "Sag mir die Nummern („die 1 und die 3“, „alle“ oder „keine“). Ich verschiebe nur, was du auswählst, in den Android-Papierkorb."
            else if (choice.isNotEmpty()) parts += DeepCleanText.READ_ONLY_PICK
            pending = Pending(run, choice, outcome.findings, readOnly, offerCaches)
        } else if (run != null && !run.finished) {
            executor.finish(run, outcome.after?.freeBytes)
        }
        val spoken = parts.filter { it.isNotBlank() }.joinToString(" ")
        lastSummary = spoken
        host.activity("SPEICHER · " + (if (run != null) "${removed.size} entfernt · " else "") + "${outcome.scan.visited} Einträge geprüft · " +
            "${choice.size} zur Auswahl · Gegenprobe ${outcome.audit?.let { "${it.unchanged}/${it.checked}" } ?: "–"}")
        host.answer(request, spoken, spoken + if (shown.isEmpty()) "" else "\n\n" + shown.joinToString("\n"))
        val result = done(command, outcome.before, outcome.after ?: outcome.before.takeIf { run == null }, removed.size, removedBytes, choice.size)
        host.outcome(if (outcome.stopped) OwnerLaneOutcome.Stopped
            else if (command.mode == DeepCleanMode.TARGETED && choice.isNotEmpty() && !readOnly)
                OwnerLaneOutcome.NeedsOwner(OwnerLaneOutcome.Need.CHOICE, "Welche Dateien soll ich entfernen?")
            else result)
    }

    /** The owner's answer to the open selection or confirmation; false = not such an answer. */
    private fun answerPending(request: String): Boolean {
        pendingUnload?.let { asked ->
            if (host.nowMillis() - asked !in 0..PENDING_MILLIS) { pendingUnload = null; return@let }
            when (DeepCleanCommands.reply(request, host.assistantName)) {
                DeepCleanCommands.Reply.Yes, DeepCleanCommands.Reply.All -> {
                    pendingUnload = null
                    host.acceptOwnerTurn(request)
                    val problem = host.unloadLocalModel()
                    host.answer(request, problem?.let { "Das Modell konnte ich nicht entladen: $it" }
                        ?: "Ich entlade das lokale Modell. Bis du es wieder lädst, antworte ich ohne es oder über das Cloud-Gehirn, falls du es gewählt hast.")
                    return true
                }
                DeepCleanCommands.Reply.No, DeepCleanCommands.Reply.None, DeepCleanCommands.Reply.Stop -> {
                    pendingUnload = null
                    host.acceptOwnerTurn(request)
                    host.answer(request, "In Ordnung, das Modell bleibt geladen.")
                    return true
                }
                else -> Unit
            }
        }
        val open = pending?.takeIf { it.open } ?: run { pending = null; return false }
        val reply = DeepCleanCommands.reply(request, host.assistantName) ?: return false
        host.acceptOwnerTurn(request)
        open.askedAt = host.nowMillis()
        val chosen = open.chosen
        if (chosen != null || open.appCachesChosen) {
            // The confirmation question is open.
            when (reply) {
                DeepCleanCommands.Reply.Yes, DeepCleanCommands.Reply.All -> { confirm(request, open); return true }
                DeepCleanCommands.Reply.No, DeepCleanCommands.Reply.None, DeepCleanCommands.Reply.Stop -> {
                    decline(request, open, "In Ordnung, ich habe nichts davon gelöscht."); return true
                }
                else -> Unit
            }
        }
        when (reply) {
            DeepCleanCommands.Reply.Stop -> decline(request, open, "Gestoppt. Gelöscht habe ich davon nichts.")
            DeepCleanCommands.Reply.No, DeepCleanCommands.Reply.None -> decline(request, open, "In Ordnung, die Dateien bleiben alle.")
            DeepCleanCommands.Reply.Restore -> { pending = null; restore(request, "") }
            is DeepCleanCommands.Reply.Show -> {
                val filtered = open.items.filter { item ->
                    open.findings[item.key]?.let { DeepCleanClassifier.protectedBy(it, setOf(reply.group)) } == true ||
                        (reply.group == DeepCleanGroup.LARGE && item.sizeBytes >= DeepCleanClassifier.LARGE_BYTES)
                }
                open.shown = filtered
                if (filtered.isEmpty()) host.answer(request, "Darunter sind keine ${reply.group.label}.")
                else host.answer(request, "Das sind die ${reply.group.label} – ${DeepCleanText.files(filtered.size)}, " +
                    "${DeepCleanText.bytes(filtered.sumOf { it.sizeBytes })}. " + (if (open.readOnly) "Gelöscht wird nichts." else "Sag mir die Nummern."),
                    "Das sind die ${reply.group.label}:\n" + filtered.mapIndexed { i, item -> DeepCleanText.line(i + 1, item, open.findings[item.key]) }.joinToString("\n"))
            }
            is DeepCleanCommands.Reply.Keep -> {
                val kept = open.shown.filter { item -> open.findings[item.key]?.let { DeepCleanClassifier.protectedBy(it, reply.groups) } == true }
                reply.groups.forEach { learning.refused(listOf(DeepCleanLearning.groupKey(it))) }
                open.shown = open.shown - kept.toSet()
                host.answer(request, "Gut, ${reply.groups.joinToString(" und ") { it.label }} bleiben" +
                    (if (kept.isNotEmpty()) " – ${DeepCleanText.files(kept.size)} habe ich aus der Auswahl genommen." else ".") +
                    if (open.shown.isEmpty()) " Zur Auswahl bleibt nichts mehr." else " Übrig zur Auswahl: ${DeepCleanText.files(open.shown.size)}.")
                if (open.shown.isEmpty()) finishSelection(open)
            }
            DeepCleanCommands.Reply.All -> pick(request, open, open.shown.indices.toList(), open.offerAppCaches)
            DeepCleanCommands.Reply.Yes -> host.answer(request, "Welche Nummern meinst du? Sag zum Beispiel „die 1 und die 2“ oder „alle“.")
            is DeepCleanCommands.Reply.Count -> {
                val size = open.shown.size + if (open.offerAppCaches) 1 else 0
                when {
                    reply.count == size -> pick(request, open, open.shown.indices.toList(), open.offerAppCaches)
                    reply.count in 1..size -> host.answer(request, "Meinst du Nummer ${reply.count} oder die ersten ${reply.count}? " +
                        "Sag bitte „Nummer ${reply.count}“ oder die Nummern einzeln.")
                    else -> host.answer(request, "So viele stehen nicht zur Auswahl – es sind $size.")
                }
            }
            is DeepCleanCommands.Reply.Pick -> {
                val size = open.shown.size
                val caches = open.offerAppCaches && (size + 1) in reply.numbers
                val wrong = reply.numbers.filter { it !in 1..size && !(open.offerAppCaches && it == size + 1) }
                if (wrong.isNotEmpty()) host.answer(request, "Die Nummer ${wrong.first()} gibt es nicht – zur Auswahl stehen 1 bis ${size + if (open.offerAppCaches) 1 else 0}.")
                else pick(request, open, reply.numbers.filter { it in 1..size }.map { it - 1 }, caches)
            }
        }
        return true
    }

    private fun pick(request: String, open: Pending, indices: List<Int>, appCaches: Boolean) {
        if (open.readOnly) { host.answer(request, DeepCleanText.READ_ONLY_PICK); return }
        val chosen = indices.map { open.shown[it] }
        if (chosen.isEmpty() && !appCaches) { host.answer(request, "Ausgewählt ist nichts."); return }
        open.chosen = chosen
        open.appCachesChosen = appCaches
        val lines = chosen.mapIndexed { i, item -> DeepCleanText.line(i + 1, item, open.findings[item.key]) }
        val total = chosen.sumOf { it.sizeBytes }
        val spoken = (if (chosen.isNotEmpty()) "Probelauf: Ich würde ${DeepCleanText.files(chosen.size)} mit zusammen ${DeepCleanText.bytes(total)} " +
            "in den Android-Papierkorb verschieben – von dort lassen sie sich wiederherstellen, bis Android den Papierkorb leert. " else "") +
            (if (appCaches) "Außerdem öffne ich Androids Dialog zum Leeren der Zwischenspeicher aller Apps. " else "") +
            "Android fragt dich danach selbst noch einmal. Soll ich?"
        host.answer(request, spoken, spoken + if (lines.isEmpty()) "" else "\n\n" + lines.joinToString("\n"))
    }

    private fun decline(request: String, open: Pending, text: String) {
        val folders = (open.chosen ?: open.shown).map { DeepCleanClassifier.folderKey(DeepCleanEntry(it.root, it.relative, it.sizeBytes, it.modifiedMillis)) }
        learning.refused(folders)
        finishSelection(open)
        host.answer(request, text)
    }

    private fun finishSelection(open: Pending) {
        pending = null
        open.run?.let { run ->
            run.items.filter { it.state == DeepCleanItemState.PLANNED && it.level == DeepCleanLevel.YELLOW }.forEach {
                it.state = DeepCleanItemState.SKIPPED; it.note = "nicht ausgewählt"
            }
            if (!run.finished) executor.finish(run, host.volume()?.freeBytes)
            run.reported = true
            journal.save()
        }
        stage.choiceCount = 0; stage.choiceBytes = 0
        stage.phase = DeepCleanStage.Phase.DONE
        host.stageChanged()
    }

    /** The owner said yes to the dry run: check every file once more, then Android's dialog, then measure. */
    private fun confirm(request: String, open: Pending) {
        val run = open.run ?: run { host.answer(request, DeepCleanText.READ_ONLY_PICK); pending = null; return }
        val chosen = open.chosen.orEmpty()
        val wantsCaches = open.appCachesChosen
        pending = null
        run.reported = false
        val before = host.volume()
        val ready = executor.prepareTrash(run, chosen.map { it.id }) { stopFlag.get() }
        val changed = chosen.size - ready.size
        stage.phase = DeepCleanStage.Phase.CLEAN
        host.stageChanged()
        fun afterTrash(trashed: Int, trashAnswer: DeepCleanSystemResult?) {
            fun close(cacheAnswer: DeepCleanSystemResult?) {
                run.items.filter { it.state == DeepCleanItemState.PLANNED && it.level == DeepCleanLevel.YELLOW }.forEach {
                    it.state = DeepCleanItemState.SKIPPED; it.note = "nicht ausgewählt"
                }
                val after = host.volume()
                executor.finish(run, after?.freeBytes)
                run.reported = true
                journal.save()
                val trashedItems = run.items.filter { it.state == DeepCleanItemState.TRASHED && it.id in chosen.map { c -> c.id } }
                val parts = ArrayList<String>()
                if (chosen.isNotEmpty()) parts += when {
                    trashAnswer?.granted == true && trashed > 0 -> "In den Android-Papierkorb verschoben: ${DeepCleanText.files(trashed)}, " +
                        "${DeepCleanText.bytes(trashedItems.sumOf { it.sizeBytes })} (Summe der Dateigrößen). Im Papierkorb belegen sie noch Platz, " +
                        "bis Android ihn leert – „Stell die gelöschten Dateien wieder her“ holt sie zurück."
                    trashAnswer?.granted == true -> "Android hat bestätigt, aber keine der Dateien ist von ihrem Platz verschwunden – ich zähle nichts als gelöscht."
                    else -> "Android hat nichts in den Papierkorb verschoben" + (trashAnswer?.reason?.let { ": $it." } ?: ".") + " Gelöscht ist davon nichts."
                }
                if (changed > 0) parts += "${DeepCleanText.files(changed)} hatte${if (changed == 1) "" else "n"} sich seit der Vorschau verändert – die habe ich nicht angefasst."
                cacheAnswer?.let {
                    parts += if (it.granted) "Android meldet: Die Zwischenspeicher der Apps sind geleert." else
                        "Die Zwischenspeicher der Apps hat Android nicht geleert" + (it.reason?.let { r -> " ($r)" } ?: "") + " – ich behaupte nichts anderes."
                }
                DeepCleanText.volumeLine(before, after)?.let(parts::add)
                stage.after = after
                stage.removedFiles = run.removed.size
                stage.removedBytes = run.removedBytes
                stage.choiceCount = 0; stage.choiceBytes = 0
                stage.phase = DeepCleanStage.Phase.DONE
                host.stageChanged()
                val text = parts.joinToString(" ")
                lastSummary = text
                host.activity("SPEICHER · Papierkorb ${trashed}/${chosen.size} · Android-Dialog " + (trashAnswer?.granted?.let { if (it) "bestätigt" else "abgelehnt" } ?: "–"))
                host.answer(request, text)
                host.outcome(done(DeepCleanCommand(run.mode), before, after, trashed, trashedItems.sumOf { it.sizeBytes }, 0))
            }
            if (wantsCaches) host.clearAllAppCaches { answer -> close(answer) } else close(null)
        }
        if (ready.isEmpty()) { afterTrash(0, if (chosen.isEmpty()) null else DeepCleanSystemResult(false, "keine Datei war mehr unverändert")); return }
        host.trash(ready, restore = false) { answer ->
            val trashed = executor.settleTrash(run, ready, answer.granted, answer.reason)
            afterTrash(trashed, answer)
        }
    }

    // ── report, restore, optimise ───────────────────────────────────────────────────────────────────────────

    private fun report(request: String, untold: String) {
        val last = journal.runs().lastOrNull { it.finished && it.mode.mayRemove }
        val now = host.volume()
        val parts = ArrayList<String>()
        if (untold.isNotEmpty()) parts += untold.trim()
        if (last == null) {
            parts += "Ich habe noch nichts bereinigt."
            DeepCleanText.volumeLine(null, now)?.let(parts::add)
            answer(request, parts.joinToString(" "), done(DeepCleanCommand(DeepCleanMode.REPORT), null, now, 0, 0, 0))
            return
        }
        val minutes = ((host.nowMillis() - last.createdAtMillis) / 60_000L).coerceAtLeast(0)
        parts += "Bei der letzten Bereinigung (vor " + (if (minutes < 120) "$minutes Minuten" else "${minutes / 60} Stunden") + ") " +
            DeepCleanText.removedLine(last.removed.size, last.removedBytes).replaceFirstChar { it.lowercase() }
        val before = last.freeBeforeBytes
        val after = last.freeAfterBytes
        if (before != null && after != null) {
            val delta = after - before
            parts += "Android maß davor ${DeepCleanText.bytes(before)} frei und danach ${DeepCleanText.bytes(after)} – " +
                (if (delta >= 0) "also ${DeepCleanText.bytes(delta)} mehr Platz." else "also ${DeepCleanText.bytes(-delta)} weniger; in der Zeit hat etwas anderes Platz belegt.")
            if (last.removed.isNotEmpty()) parts += DeepCleanText.DIFFERENCE_NOTE
        }
        now?.let { parts += "Jetzt gerade sind ${DeepCleanText.bytes(it.freeBytes)} frei." }
        val trashed = last.items.count { it.state == DeepCleanItemState.TRASHED }
        if (trashed > 0) parts += "${DeepCleanText.files(trashed)} davon liegen im Android-Papierkorb und belegen dort noch Platz."
        answer(request, parts.joinToString(" "), done(DeepCleanCommand(DeepCleanMode.REPORT), before?.let { DeepCleanVolume(it, 0, last.createdAtMillis) },
            after?.let { DeepCleanVolume(it, 0, last.createdAtMillis) }, last.removed.size, last.removedBytes, 0))
    }

    private fun restore(request: String, untold: String) {
        val runs = journal.runs().filter { it.finished }
        val items = runs.flatMap { executor.restorable(it) }
        val greenOnly = runs.lastOrNull()?.removed?.all { it.state == DeepCleanItemState.REMOVED } == true
        if (items.isEmpty()) {
            host.answer(request, untold + if (greenOnly) "Wiederherstellen lässt sich nichts: Entfernt habe ich zuletzt nur Continuums eigene " +
                "Zwischendateien, und die erzeugt Continuum bei Bedarf neu." else "Im Android-Papierkorb liegt nichts, das ich verschoben habe.")
            host.outcome(OwnerLaneOutcome.Failed("nichts wiederherzustellen"))
            return
        }
        host.answer(request, untold + "Ich hole ${DeepCleanText.files(items.size)} aus dem Android-Papierkorb zurück. Android fragt dich gleich selbst.")
        host.trash(items, restore = true) { answer ->
            val back = if (answer.granted) executor.settleRestore(items) else 0
            val text = when {
                !answer.granted -> "Android hat nichts zurückgeholt" + (answer.reason?.let { ": $it." } ?: ".")
                back == 1 && items.size == 1 -> "Die Datei ist wieder an ihrem Platz."
                back == items.size -> "Alle ${DeepCleanText.files(back)} sind wieder an ihrem Platz."
                else -> "Wieder an ihrem Platz: ${DeepCleanText.files(back)} von ${items.size}."
            }
            host.answer(request, text)
            host.outcome(if (answer.granted) done(DeepCleanCommand(DeepCleanMode.RESTORE), null, host.volume(), back, items.filter {
                it.state == DeepCleanItemState.RESTORED }.sumOf { it.sizeBytes }, 0) else OwnerLaneOutcome.Failed(text))
        }
    }

    /**
     * E3: Continuum's own load only – temperature, memory, the on-device model, its own running work. Nothing is
     * deleted and no other app is stopped; the one action it can offer is unloading its own model.
     */
    private fun optimise(request: String, untold: String) {
        val facts = runCatching { host.systemFacts() }.getOrNull()
        val volume = host.volume()
        val thermal = facts?.thermalStatus ?: host.thermalStatus()
        val memory = host.ownMemoryBytes()
        val parts = ArrayList<String>()
        if (untold.isNotEmpty()) parts += untold.trim()
        parts += DeepCleanText.lead(host.ownerName(), host.personalTone()) + "Ich schaue mir an, was Continuum selbst dem Handy abverlangt – gelöscht wird dabei nichts."
        parts += when (thermal) {
            null -> "Die Temperaturstufe meldet Android hier nicht."
            0 -> "Temperatur: unauffällig."
            1 -> "Temperatur: leicht erhöht."
            2 -> "Temperatur: erhöht – Android bremst schon etwas."
            else -> "Temperatur: hoch – Android bremst das Gerät."
        }
        memory?.let { parts += "Continuum belegt gerade ${DeepCleanText.bytes(it)} Arbeitsspeicher." }
        val brain = facts?.brain
        val modelLoaded = brain is ContinuumSystemFacts.Brain.Local && brain.state == ContinuumSystemFacts.Brain.State.READY
        if (modelLoaded) parts += "Das lokale KI-Modell ist geladen" + ((brain as ContinuumSystemFacts.Brain.Local).name?.let { " ($it)" } ?: "") + "."
        facts?.running?.takeIf { it.isNotEmpty() }?.let { parts += "Gerade läuft: ${it.joinToString(", ")}." }
        volume?.let {
            val percent = if (it.totalBytes > 0) it.freeBytes * 100 / it.totalBytes else null
            parts += "Frei sind ${DeepCleanText.bytes(it.freeBytes)} von ${DeepCleanText.bytes(it.totalBytes)}" + (percent?.let { p -> " ($p %)" } ?: "") + " (Android-Messung)."
            if (percent != null && percent < LOW_FREE_PERCENT) parts += "Das ist knapp. Sag „Bereinige mein Handy“, dann entferne ich Continuums eigene entbehrliche Dateien; " +
                "„Mach eine Tiefenbereinigung“ zeigt dir dazu große Dateien und Duplikate zur Auswahl."
        }
        parts += "Andere Apps beende ich nicht – das entscheidet Android selbst."
        val offerUnload = modelLoaded && ((thermal ?: 0) >= 2 || (memory ?: 0) > HIGH_MEMORY_BYTES) && host.idle()
        if (offerUnload) {
            parts += "Spürbar entlasten kann ich das Handy, indem ich das lokale Modell entlade. Soll ich?"
            pendingUnload = host.nowMillis()
        } else if (modelLoaded) parts += "Das Modell lasse ich geladen – Temperatur und Speicher geben keinen Grund, es zu entladen."
        val text = parts.joinToString(" ")
        host.activity("SPEICHER · Optimierung · nur gelesen · eigene Last" + if (offerUnload) " · Entladen angeboten" else "")
        answer(request, text, done(DeepCleanCommand(DeepCleanMode.OPTIMISE), volume, volume, 0, 0, 0))
    }

    /** The native panel's log: what the journal says was really done, newest first. */
    fun log(limit: Int = 40): List<String> = journal.runs().reversed().flatMap { run ->
        run.items.filter { it.state != DeepCleanItemState.PLANNED }.map { item ->
            java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMAN).format(java.util.Date(item.doneAtMillis ?: run.createdAtMillis)) +
                " · " + stateLabel(item.state) + " · " + DeepCleanText.shownPath(item.root, item.relative) + " · " + DeepCleanText.bytes(item.sizeBytes) +
                (item.note?.let { " · $it" } ?: "")
        }
    }.take(limit)

    private fun stateLabel(state: DeepCleanItemState) = when (state) {
        DeepCleanItemState.REMOVED -> "gelöscht"
        DeepCleanItemState.TRASHED -> "im Papierkorb"
        DeepCleanItemState.RESTORED -> "wiederhergestellt"
        DeepCleanItemState.CHANGED -> "verändert, nicht angefasst"
        DeepCleanItemState.REFUSED -> "von Android abgelehnt"
        DeepCleanItemState.FAILED -> "nicht gelöscht"
        DeepCleanItemState.GONE_UNCONFIRMED -> "fehlt, nicht bestätigt"
        DeepCleanItemState.SKIPPED -> "ausgelassen"
        DeepCleanItemState.INTENT -> "in Arbeit"
        DeepCleanItemState.PLANNED -> "geplant"
    }

    /** Panel: the owner chose items of the open selection by tapping. */
    fun choose(request: String, items: List<DeepCleanItem>) {
        val open = pending?.takeIf { it.open } ?: return
        pick(request, open, items.mapNotNull { item -> open.shown.indexOfFirst { it.id == item.id }.takeIf { it >= 0 } }, false)
    }

    /** Panel: the owner tapped „Ja, verschieben“. */
    fun confirmChoice(request: String) { pending?.takeIf { it.open && it.chosen != null }?.let { confirm(request, it) } }

    companion object {
        const val PENDING_MILLIS = 10 * 60_000L
        const val MAX_CHOICE = 25
        const val HOT_STATUS = 3
        const val LOW_FREE_PERCENT = 10L
        const val HIGH_MEMORY_BYTES = 3_000_000_000L
        /** Protected categories a file may still be trashed from when the owner names exactly that file. */
        val NAMEABLE = setOf(DeepCleanCategory.PHOTO, DeepCleanCategory.VIDEO, DeepCleanCategory.AUDIO, DeepCleanCategory.DOCUMENT,
            DeepCleanCategory.DOWNLOAD_OTHER, DeepCleanCategory.OTHER, DeepCleanCategory.DUPLICATE)
    }
}
