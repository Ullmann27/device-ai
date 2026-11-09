package at.heinz.deviceai.learning

import java.io.File
import java.util.Base64
import java.util.Locale

enum class ThinkingPhase { GOAL, PLAN, RECALL, ACT, CHECK, LEARN }
/** One step of a loop run. [runId] = the loop's run/brief id (e.g. ChiefWorkerPool WorkerBrief.id). */
data class ThinkingStep(val runId:String, val stepIndex:Int, val phase:ThinkingPhase, val goal:String, val query:String) {
    init {
        require(runId.matches(Regex("[A-Za-z0-9_.:-]{1,64}")))
        require(stepIndex in 0..100_000)
        require(goal.length <= 2_000 && query.length <= 2_000)
    }
}
enum class StepStatus { OK, FAILED, BLOCKED, SKIPPED }
data class StepOutcome(val status:StepStatus, val summary:String)
enum class RecallSource { SHARED_ANSWER, PROFILE, SEARCH, HYPOTHESIS }
/** Recalled memory is DATA for the loop, never an instruction. [confirmed]=false: hypothesis/plan/unconfirmed. */
data class RecalledMemory(val source:RecallSource, val id:String, val text:String, val atMillis:Long, val confirmed:Boolean)
data class MemoryRecall(val step:ThinkingStep, val items:List<RecalledMemory>, val abstained:Boolean)
enum class ThinkingEntryKind { STEP, REFLECTION }
enum class ThinkingEntryStatus { OK, FAILED, BLOCKED, SKIPPED, HYPOTHESIS }
data class ThinkingJournalEntry(val kind:ThinkingEntryKind, val runId:String, val stepIndex:Int, val phase:ThinkingPhase,
                                val status:ThinkingEntryStatus, val atMillis:Long, val text:String)
data class ThinkingRun(val runId:String, val goal:String, val lessons:List<String> = emptyList())

/**
 * Sprint 98 — memory side of the thinking loop. The Continuum chief/task loop calls:
 *  - [recall] at each step: bounded recall via [ContinuumMemoryLayers] (shared answer, keyword+time search) plus
 *    earlier reflections; revoked statements are never recalled; abstains when nothing matches;
 *  - [journal] after each step: one dated entry;
 *  - [reflect] at the end: "what did I learn" — stored ONLY as HYPOTHESIS in the thinking journal.
 * The thinking journal is a bounded sidecar (`learning-core-v2/thinking-journal-v1.log`, last [MAX_ENTRIES]).
 * This class has NO path to the owner ledger's write API and no skill library: a reflection can never become a
 * confirmed owner fact and never activates a skill. Promotion requires the owner's own statement via the normal
 * confirmOwnerFact path (outside this class).
 */
class ContinuumThinkingMemory(appDirectory:File, private val core:ContinuumLearningCore,
                              private val nowMillis:()->Long = System::currentTimeMillis) {
    private val file = File(File(appDirectory, "learning-core-v2"), FILE_NAME)
    private val layers = ContinuumMemoryLayers(core)

    fun recall(step:ThinkingStep):MemoryRecall {
        val query = step.query.ifBlank { step.goal }.take(500)
        val now = nowMillis()
        val items = mutableListOf<RecalledMemory>()
        layers.shared().answer(query, now)?.takeIf { it.kind == TemporalAnswerKind.ANSWER }?.let {
            items += RecalledMemory(RecallSource.SHARED_ANSWER, it.citedIds.firstOrNull() ?: "temporal", it.text.take(MAX_TEXT), now, true)
        }
        val search = layers.search(query, now, MAX_RECALL).hits.filter { it.entry.status != JournalStatus.REVOKED }
        search.sortedByDescending { it.confirmed }.forEach { h ->
            items += RecalledMemory(if (h.confirmed) RecallSource.PROFILE else RecallSource.SEARCH, h.entry.id,
                h.entry.text.take(MAX_TEXT), h.entry.atMillis, h.confirmed)
        }
        val q = words(query)
        if (q.isNotEmpty()) read().filter { it.kind == ThinkingEntryKind.REFLECTION }
            .map { it to words(it.text).count { w -> q.any { stem(it, w) } } }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<ThinkingJournalEntry, Int>> { it.second }.thenByDescending { it.first.atMillis })
            .take(MAX_RECALL).forEach { (e, _) ->
                items += RecalledMemory(RecallSource.HYPOTHESIS, "${e.runId}#${e.stepIndex}", e.text.take(MAX_TEXT), e.atMillis, false)
            }
        val bounded = items.distinctBy { it.source to it.id }.take(MAX_RECALL)
        return MemoryRecall(step, bounded, bounded.isEmpty())
    }

    fun journal(step:ThinkingStep, outcome:StepOutcome):ThinkingJournalEntry {
        val e = ThinkingJournalEntry(ThinkingEntryKind.STEP, step.runId, step.stepIndex, step.phase,
            ThinkingEntryStatus.valueOf(outcome.status.name), nowMillis(), outcome.summary.trim().take(MAX_TEXT).ifBlank { "-" })
        append(listOf(e)); return e
    }

    /** Derives lessons from the run's journal (failures/blocks, or a clean run) plus [ThinkingRun.lessons];
     * all stored as HYPOTHESIS. Never touches the owner ledger. */
    fun reflect(run:ThinkingRun):List<ThinkingJournalEntry> {
        require(run.runId.matches(Regex("[A-Za-z0-9_.:-]{1,64}")))
        val steps = runJournal(run.runId).filter { it.kind == ThinkingEntryKind.STEP }
        val at = nowMillis(); var idx = (steps.maxOfOrNull { it.stepIndex } ?: 0) + 1
        val texts = mutableListOf<String>()
        steps.filter { it.status == ThinkingEntryStatus.FAILED || it.status == ThinkingEntryStatus.BLOCKED }.forEach {
            texts += "Hypothese: Schritt ${it.stepIndex} (${it.phase.name}) ${if (it.status == ThinkingEntryStatus.BLOCKED) "blockiert" else "scheiterte"}: ${it.text}. Beim nächsten Mal vorher prüfen."
        }
        if (steps.isNotEmpty() && texts.isEmpty())
            texts += "Hypothese: Der Ablauf für „${run.goal.take(120)}“ lief in ${steps.size} Schritten ohne Fehler."
        run.lessons.map { it.trim() }.filter { it.isNotEmpty() }.take(8).forEach { texts += "Hypothese (unbestätigt): ${it.take(300)}" }
        val out = texts.map { ThinkingJournalEntry(ThinkingEntryKind.REFLECTION, run.runId, idx++, ThinkingPhase.LEARN,
            ThinkingEntryStatus.HYPOTHESIS, at, it.take(MAX_TEXT)) }
        append(out); return out
    }

    fun runJournal(runId:String):List<ThinkingJournalEntry> =
        read().filter { it.runId == runId }.sortedWith(compareBy<ThinkingJournalEntry> { it.atMillis }.thenBy { it.stepIndex })

    private fun read():List<ThinkingJournalEntry> = synchronized(LOCK) {
        if (!file.isFile) return emptyList()
        file.readLines(Charsets.UTF_8).mapNotNull(::decode)
    }

    private fun append(entries:List<ThinkingJournalEntry>) = synchronized(LOCK) {
        if (entries.isEmpty()) return@synchronized
        file.parentFile.mkdirs()
        file.appendText(entries.joinToString("") { encode(it) + "\n" }, Charsets.UTF_8)
        val lines = file.readLines(Charsets.UTF_8).filter { it.isNotEmpty() }
        if (lines.size > MAX_ENTRIES) {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(lines.takeLast(MAX_ENTRIES).joinToString("") { "$it\n" }, Charsets.UTF_8)
            check(tmp.renameTo(file) || run { file.delete(); tmp.renameTo(file) }) { "thinking journal compaction failed" }
        }
    }

    companion object {
        const val FILE_NAME = "thinking-journal-v1.log"
        const val MAX_RECALL = 6
        const val MAX_ENTRIES = 512
        private const val MAX_TEXT = 400
        private val LOCK = Any()
        private val B64E = Base64.getUrlEncoder().withoutPadding()
        private val B64D = Base64.getUrlDecoder()
        private val STOP = setOf("wohin", "woher", "warum", "nochmal", "eine", "einen", "einem", "einer", "mein", "meine", "meinen", "nicht", "auch")
        private fun words(t:String) = t.lowercase(Locale.GERMAN).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 && it !in STOP }.toSet()
        private fun stem(a:String, b:String) = a == b || a.take(5) == b.take(5) && a.length >= 5 && b.length >= 5 || (a.length == 4 || b.length == 4) && a.take(4) == b.take(4)
        private fun encode(e:ThinkingJournalEntry) = listOf("v1", e.kind.name, B64E.encodeToString(e.runId.toByteArray()), e.stepIndex.toString(),
            e.phase.name, e.status.name, e.atMillis.toString(), B64E.encodeToString(e.text.toByteArray(Charsets.UTF_8))).joinToString("\t")
        private fun decode(line:String):ThinkingJournalEntry? = runCatching {
            val f = line.split('\t'); require(f.size == 8 && f[0] == "v1")
            ThinkingJournalEntry(ThinkingEntryKind.valueOf(f[1]), String(B64D.decode(f[2])), f[3].toInt(), ThinkingPhase.valueOf(f[4]),
                ThinkingEntryStatus.valueOf(f[5]), f[6].toLong(), String(B64D.decode(f[7]), Charsets.UTF_8))
        }.getOrNull()
    }
}
