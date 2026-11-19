package at.heinz.deviceai.deepclean

/**
 * Carries out a run of the journal. Every removal is preceded by a check of exactly that file – it is still a regular
 * file below its root, reached without a link, with the size and modification time of the preview, and for a
 * duplicate still has the previewed SHA-256 while the copy that stays still exists with the same hash. The item is
 * marked INTENT in the journal before the call and its result right after it, so no file is removed twice.
 *
 * Direct removal ([removeGreen]) is only ever applied to GREEN items of the app's own roots. Everything else goes
 * through Android's trash ([prepareTrash] → the system dialog → [settleTrash]), which the owner confirms himself.
 */
internal class DeepCleanExecutor(
    private val files: DeepCleanFiles,
    private val journal: DeepCleanJournal,
    private val now: () -> Long
) {
    /** Why [item] must not be touched now; null = it is exactly what was previewed. */
    fun check(item: DeepCleanItem, cancelled: () -> Boolean = { false }): String? {
        val stamp = files.stat(item.root, item.relative) ?: return "nicht mehr vorhanden oder nicht mehr dieselbe Datei"
        if (stamp.sizeBytes != item.sizeBytes || stamp.modifiedMillis != item.modifiedMillis) return "seit der Vorschau verändert"
        item.sha256?.let { expected ->
            val actual = files.sha256(item.root, item.relative, cancelled) ?: return "Inhalt nicht mehr lesbar"
            if (actual != expected) return "Inhalt seit der Vorschau verändert"
        }
        item.keeperRelative?.let { keeper ->
            val kept = files.stat(item.root, keeper) ?: return "die Kopie, die bleiben soll, ist nicht mehr da"
            if (kept.sizeBytes != item.sizeBytes) return "die Kopie, die bleiben soll, hat sich verändert"
            val hash = files.sha256(item.root, keeper, cancelled) ?: return "die Kopie, die bleiben soll, ist nicht lesbar"
            if (hash != item.sha256) return "die Kopie, die bleiben soll, ist nicht mehr identisch"
        }
        return null
    }

    /**
     * Removes the PLANNED GREEN items of [run] one by one. Stops before the next item when [cancelled] says so; the
     * rest stays PLANNED and is marked SKIPPED by the caller's report. Returns the number of items removed now.
     */
    fun removeGreen(run: DeepCleanRun, cancelled: () -> Boolean, progress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val todo = run.items.filter { it.state == DeepCleanItemState.PLANNED && it.level == DeepCleanLevel.GREEN &&
            it.root != DeepCleanRoot.SHARED }
        var removed = 0
        for ((index, item) in todo.withIndex()) {
            if (cancelled()) { run.stopped = true; break }
            val problem = check(item, cancelled)
            if (problem != null) {
                item.state = DeepCleanItemState.CHANGED
                item.note = problem
                journal.save()
                continue
            }
            item.state = DeepCleanItemState.INTENT
            journal.save()
            val ok = files.delete(item.root, item.relative) && files.stat(item.root, item.relative) == null
            item.state = if (ok) DeepCleanItemState.REMOVED else DeepCleanItemState.FAILED
            if (!ok) item.note = "Android hat das Löschen nicht zugelassen"
            item.doneAtMillis = now()
            journal.save()
            if (ok) removed++
            progress(index + 1, todo.size)
        }
        return removed
    }

    /**
     * The YELLOW items the owner chose ([ids]), checked once more right before Android's trash dialog. The ones that
     * pass are marked INTENT; the others CHANGED. Returns the items to hand to the dialog.
     */
    fun prepareTrash(run: DeepCleanRun, ids: Collection<Int>, cancelled: () -> Boolean): List<DeepCleanItem> {
        val chosen = run.items.filter { it.id in ids && it.state == DeepCleanItemState.PLANNED && it.level == DeepCleanLevel.YELLOW }
        val ready = ArrayList<DeepCleanItem>()
        for (item in chosen) {
            if (cancelled()) { run.stopped = true; break }
            val problem = check(item, cancelled)
            if (problem != null) { item.state = DeepCleanItemState.CHANGED; item.note = problem; continue }
            item.state = DeepCleanItemState.INTENT
            ready += item
        }
        journal.save()
        return ready
    }

    /**
     * After Android's dialog: an item counts as trashed only when its original path is really gone now. [granted]
     * false (the owner declined in the dialog, or Android refused) leaves every item REFUSED – nothing is claimed.
     */
    fun settleTrash(run: DeepCleanRun, items: List<DeepCleanItem>, granted: Boolean, refusedReason: String? = null): Int {
        var trashed = 0
        for (item in items) {
            if (item.state != DeepCleanItemState.INTENT) continue
            val gone = files.stat(item.root, item.relative) == null
            when {
                granted && gone -> { item.state = DeepCleanItemState.TRASHED; item.doneAtMillis = now(); trashed++ }
                gone -> { item.state = DeepCleanItemState.GONE_UNCONFIRMED; item.note = "Android meldete keine Bestätigung" }
                else -> { item.state = DeepCleanItemState.REFUSED; item.note = item.note ?: refusedReason ?: "in Android nicht bestätigt" }
            }
        }
        journal.save()
        return trashed
    }

    /** Items of [run] that are in Android's trash and can be put back. */
    fun restorable(run: DeepCleanRun): List<DeepCleanItem> = run.items.filter {
        it.state == DeepCleanItemState.TRASHED && it.handle != null && (it.doneAtMillis ?: 0) > now() - TRASH_MILLIS
    }

    /** After Android's restore dialog: an item counts as restored only when its file is back at its place. */
    fun settleRestore(items: List<DeepCleanItem>): Int {
        var back = 0
        for (item in items) if (files.stat(item.root, item.relative) != null) { item.state = DeepCleanItemState.RESTORED; back++ }
        journal.save()
        return back
    }

    /** Closes [run]: whatever is still PLANNED (and could have been removed) becomes SKIPPED. */
    fun finish(run: DeepCleanRun, freeAfter: Long?) {
        run.items.filter { it.state == DeepCleanItemState.PLANNED && it.level == DeepCleanLevel.GREEN }.forEach {
            it.state = DeepCleanItemState.SKIPPED
            if (it.note == null) it.note = if (run.stopped) "gestoppt" else "nicht ausgeführt"
        }
        run.freeAfterBytes = freeAfter
        run.finished = true
        journal.save()
    }

    companion object {
        /** Android keeps trashed media for about 30 days (MediaStore DATE_EXPIRES); after that nothing is restorable. */
        const val TRASH_MILLIS = 30L * 24 * 3_600_000L
    }
}

/**
 * E7: proves that the protected files were not touched. Before a run a sample of RED files is fingerprinted (size,
 * modification time and, for files up to [HASH_LIMIT], the full SHA-256); after the run the same files are read
 * again. The result names every difference – it never says „unverändert“ for a file it could not read.
 */
internal object DeepCleanAudit {
    const val SAMPLE = 12
    const val HASH_LIMIT = 16L * 1024 * 1024

    data class Print(val root: DeepCleanRoot, val relative: String, val stamp: DeepCleanStamp, val sha256: String?)
    data class Result(val checked: Int, val unchanged: Int, val changed: List<String>, val unreadable: List<String>) {
        val clean: Boolean get() = changed.isEmpty() && unreadable.isEmpty() && checked == unchanged
    }

    /** Spread over the categories: the biggest protected files of each category first. */
    fun sample(findings: List<DeepCleanFinding>, size: Int = SAMPLE): List<DeepCleanFinding> {
        val red = findings.filter { it.level == DeepCleanLevel.RED && !it.entry.symlink }
        val byCategory = red.groupBy { it.category }.mapValues { (_, list) -> list.sortedByDescending { it.entry.sizeBytes } }
        val picked = ArrayList<DeepCleanFinding>()
        var round = 0
        while (picked.size < size && byCategory.values.any { it.size > round }) {
            byCategory.values.forEach { list -> list.getOrNull(round)?.let { if (picked.size < size) picked += it } }
            round++
        }
        return picked
    }

    fun fingerprint(files: DeepCleanFiles, findings: List<DeepCleanFinding>, cancelled: () -> Boolean = { false }): List<Print> =
        findings.mapNotNull { finding ->
            val entry = finding.entry
            val stamp = files.stat(entry.root, entry.relative) ?: return@mapNotNull null
            Print(entry.root, entry.relative, stamp, if (stamp.sizeBytes <= HASH_LIMIT) files.sha256(entry.root, entry.relative, cancelled) else null)
        }

    fun verify(files: DeepCleanFiles, prints: List<Print>, cancelled: () -> Boolean = { false }): Result {
        val changed = ArrayList<String>()
        val unreadable = ArrayList<String>()
        var unchanged = 0
        for (print in prints) {
            val stamp = files.stat(print.root, print.relative)
            when {
                stamp == null -> changed += print.relative
                stamp != print.stamp -> changed += print.relative
                print.sha256 != null -> when (val hash = files.sha256(print.root, print.relative, cancelled)) {
                    null -> unreadable += print.relative
                    print.sha256 -> unchanged++
                    else -> changed += print.relative
                }
                else -> unchanged++
            }
        }
        return Result(prints.size, unchanged, changed, unreadable)
    }
}
