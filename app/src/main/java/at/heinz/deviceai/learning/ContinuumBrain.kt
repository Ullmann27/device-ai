package at.heinz.deviceai.learning

import at.heinz.deviceai.intelligence.ewm.Ewm1Brain
import at.heinz.deviceai.intelligence.ewm.Ewm1SourceIndex
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import org.json.JSONObject

/**
 * EWM2 brain on the existing Continuum architecture, without a second memory store: owner facts come from
 * [ContinuumLearningCore.activeOwnerFacts] (revoked facts are never part of it), ranking from the unchanged
 * SWB1 dialogue ([ContinuumMemoryDialogue.rank]), public sources from [ContinuumKnowledgeArchive].
 *
 * The owner switches it ([ContinuumBrainSettings]):
 * - OFF (default, and after any read error): nothing is computed, today's answer is returned as is.
 * - SHADOW: the brain runs beside today's path; the answer, evidence line and routing stay identical,
 *   only content-free counts are kept locally ([ContinuumBrainShadowLog]).
 * - ON (only after the owner's confirmation): the brain's evidence decides; any error, a fact revoked in the
 *   meantime or a case it may not decide keeps today's answer.
 */
enum class ContinuumBrainMode { OFF, SHADOW, ON }

/** How today's path grounded its answer; decides what the brain may replace in ON. */
enum class ContinuumBaselineSource { MEMORY, ARCHIVE, WEB_NOTES, NONE }

/**
 * Today's chat result of one turn, exactly as AgentActivity shows it without the brain.
 * The displayed evidence line is "[evidence] · [trace]".
 */
data class ContinuumChatOutcome(
    val answer:String,
    val evidence:String,
    val trace:String,
    val grounded:Boolean,
    val source:ContinuumBaselineSource,
    val memoryIds:List<String> = emptyList(),
    val archiveId:String? = null
) {
    val evidenceLine:String get()=evidence+" · "+trace
}

enum class ContinuumBrainStatus {
    OFF, SHADOW, ON_MEMORY, ON_SOURCE, ON_CLARIFY, ON_ABSTAIN, ON_KEPT_BASELINE, FALLBACK_ERROR, FALLBACK_REVOKED
}

data class ContinuumBrainResult(
    val outcome:ContinuumChatOutcome,
    val mode:ContinuumBrainMode,
    val status:ContinuumBrainStatus
)

/** Owner setting, stored next to the learning core; unreadable, missing or unknown = OFF. */
class ContinuumBrainSettings(appDirectory:File) {
    private val directory=File(appDirectory,"learning-core-v2/ewm-brain")
    private val file=File(directory,"mode.properties")

    fun mode():ContinuumBrainMode = runCatching {
        if(!file.isFile || file.length() !in 1L..256L) ContinuumBrainMode.OFF
        else Properties().apply { file.inputStream().use { load(it) } }.getProperty("mode")
            ?.let { ContinuumBrainMode.valueOf(it.trim()) } ?: ContinuumBrainMode.OFF
    }.getOrDefault(ContinuumBrainMode.OFF)

    /** Owner action only (chat command; ON after a confirmation dialog). Atomic replace. */
    fun set(mode:ContinuumBrainMode) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create brain settings" }
        val temp=File(directory,"mode.properties.tmp")
        temp.writeText("mode=${mode.name}\n")
        Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)
    }
}

/**
 * Content-free SHADOW diagnostics for the owner's Fold7 comparison (AP-7): route, decision, reason code,
 * how today's path grounded, agreement class and latency. Never a question, fact, id or source title.
 */
class ContinuumBrainShadowLog(appDirectory:File) {
    private val file=File(appDirectory,"learning-core-v2/ewm-brain/shadow-v1.jsonl")

    fun record(answer:Ewm1Brain.Answer?,baseline:ContinuumChatOutcome,millis:Long,at:Long) {
        val entry=JSONObject().put("t",at)
            .put("route",answer?.route ?: "ERROR")
            .put("decision",answer?.decision ?: "ERROR")
            .put("reason",answer?.reason?.substringBefore(':')?.take(40) ?: "ERROR")
            .put("k0",baseline.source.name)
            .put("agree",agreement(answer,baseline))
            .put("ms",millis)
        file.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
        val kept=lines().takeLast(MAX_LINES-1)
        val temp=File(file.parentFile,"shadow-v1.jsonl.tmp")
        temp.writeText((kept+entry.toString()).joinToString("\n",postfix="\n"))
        Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)
    }

    fun lines():List<String> = runCatching {
        if(!file.isFile || file.length()>MAX_BYTES) emptyList() else file.readLines().filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    /** One owner-readable line per class; numbers only. */
    fun summary():String {
        val rows=lines().mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        if(rows.isEmpty())return "Noch keine Schattenvergleiche aufgezeichnet."
        val agree=rows.groupingBy { it.optString("agree") }.eachCount()
        val ms=rows.map { it.optLong("ms") }.sorted()
        return "Schattenvergleiche: "+rows.size+" Züge · gleich "+(agree["SAME"] ?: 0)+
            " · anders "+(agree["DIFFERENT"] ?: 0)+
            " · Gehirn enthält sich, bisheriger Weg zitierte "+(agree["K1_ABSTAINS"] ?: 0)+
            " · Gehirn antwortet, bisheriger Weg nicht "+(agree["K1_ANSWERS"] ?: 0)+
            " · Rückfragen "+((agree["K1_CLARIFIES"] ?: 0)+(agree["K1_CLARIFIES_K0_NONE"] ?: 0))+
            " · beide ohne Beleg "+(agree["BOTH_NONE"] ?: 0)+
            " · Fehler "+(agree["ERROR"] ?: 0)+
            " · Rechenzeit Median "+ms[ms.size/2]+" ms, p95 "+ms[(ms.size*95)/100]+" ms."
    }

    companion object {
        private const val MAX_LINES=500
        private const val MAX_BYTES=256L * 1024L

        internal fun agreement(answer:Ewm1Brain.Answer?,baseline:ContinuumChatOutcome):String = when {
            answer==null -> "ERROR"
            answer.decision=="QUERY" && answer.route=="MEMORY" && baseline.source==ContinuumBaselineSource.MEMORY ->
                if(answer.cited.firstOrNull()==baseline.memoryIds.firstOrNull()) "SAME" else "DIFFERENT"
            answer.decision=="QUERY" && answer.route=="SOURCE" && baseline.source==ContinuumBaselineSource.ARCHIVE ->
                if(answer.cited.firstOrNull()=="SOURCE:"+baseline.archiveId) "SAME" else "DIFFERENT"
            answer.decision=="QUERY" -> if(baseline.grounded) "DIFFERENT" else "K1_ANSWERS"
            answer.decision=="CLARIFY" -> if(baseline.grounded) "K1_CLARIFIES" else "K1_CLARIFIES_K0_NONE"
            baseline.grounded -> "K1_ABSTAINS"
            else -> "BOTH_NONE"
        }
    }
}

/** Owner chat commands: "Gehirnmodus" (status), "Gehirnmodus aus|schatten|an". */
object ContinuumBrainCommand {
    private val PATTERN=Regex("(?iu)^\\s*gehirn\\s?modus\\s*(status|aus|schatten|an|ein)?\\s*[.!?]?\\s*$")

    /** null = not a brain command; a mode = switch request; [STATUS] = report only. */
    fun parse(text:String):String? {
        if(text.length>40)return null
        val word=PATTERN.find(text)?.groupValues?.get(1)?.lowercase() ?: return null
        return when(word) {
            "", "status" -> STATUS
            "aus" -> ContinuumBrainMode.OFF.name
            "schatten" -> ContinuumBrainMode.SHADOW.name
            else -> ContinuumBrainMode.ON.name
        }
    }

    const val STATUS="STATUS"
}

/**
 * The connection between one chat turn and the brain. Session state (discourse focus, pronouns, the last
 * source topic) lives per conversation in memory only, bounded to [MAX_SESSIONS], and is rebuilt whenever
 * the owner facts, the SWB1 model or the archive change - so a revoked fact can never linger in a session.
 */
object ContinuumBrainBridge {
    private const val MAX_SESSIONS=8

    private class Session(val brain:Ewm1Brain,val state:String)

    private val sessions=object:LinkedHashMap<String,Session>(16,0.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Session>?):Boolean=size>MAX_SESSIONS
    }
    private var indexKey:String?=null
    private var index:Ewm1SourceIndex?=null

    /** Fault boundary for JVM regression tests (a revocation racing the turn), never an external callback. */
    internal var beforeRevocationCheck:(()->Unit)?=null

    fun decide(
        appDirectory:File,
        core:ContinuumLearningCore,
        conversationId:String,
        request:String,
        baseline:ContinuumChatOutcome,
        settings:ContinuumBrainSettings=ContinuumBrainSettings(appDirectory),
        nowMillis:Long=System.currentTimeMillis()
    ):ContinuumBrainResult {
        val mode=settings.mode()
        if(mode==ContinuumBrainMode.OFF)return ContinuumBrainResult(baseline,mode,ContinuumBrainStatus.OFF)
        val started=System.nanoTime()
        val asked=runCatching { ask(appDirectory,core,conversationId,request) }.getOrNull()
        val millis=(System.nanoTime()-started)/1_000_000L
        if(mode==ContinuumBrainMode.SHADOW) {
            runCatching { ContinuumBrainShadowLog(appDirectory).record(asked?.first,baseline,millis,nowMillis) }
            return ContinuumBrainResult(baseline,mode,ContinuumBrainStatus.SHADOW)
        }
        val (answer,facts)=asked ?: return ContinuumBrainResult(baseline,mode,ContinuumBrainStatus.FALLBACK_ERROR)
        return runCatching { decideOn(core,answer,facts,baseline) }
            .getOrElse { ContinuumBrainResult(baseline,mode,ContinuumBrainStatus.FALLBACK_ERROR) }
    }

    /** Drops the session state of one conversation (owner corrected the context or started over). */
    @Synchronized fun forget(conversationId:String) {
        sessions.keys.removeAll { it.substringAfter('\u0000')==conversationId }
    }

    @Synchronized private fun ask(
        appDirectory:File,core:ContinuumLearningCore,conversationId:String,request:String
    ):Pair<Ewm1Brain.Answer,Set<String>> {
        ContinuumMemoryDialogue.requireConversationId(conversationId)
        val question=request.trim().take(512)
        require(question.isNotEmpty())
        val (model,facts)=core.brainView()
        val (sourceState,sources)=sourceIndex(appDirectory)
        val state=digest(ContinuumMemoryDialogue.hash(model)+"\n"+facts.joinToString("\n") { it.id }+"\n"+sourceState)
        val key=appDirectory.canonicalPath+"\u0000"+conversationId
        val session=sessions[key]?.takeIf { it.state==state } ?: Session(
            Ewm1Brain(facts.map { it.id to it.content },sources) { query,subset ->
                ContinuumMemoryDialogue.rank(model,query.take(1000),facts.filter { it.id in subset }).map { it.id }
            },state).also { sessions[key]=it }
        return session.brain.ask(question) to facts.map { it.id }.toSet()
    }

    /** One index per archive state, shared by all sessions; at most MAX_RECORDS records, text cut. */
    private fun sourceIndex(appDirectory:File):Pair<String,Ewm1SourceIndex> {
        val archive=ContinuumKnowledgeArchive(appDirectory)
        val root=appDirectory.canonicalPath+"\u0000"
        val cached=index
        if(cached!=null && indexKey==root+archive.indexState())return indexKey!! to cached
        val snapshot=archive.indexSnapshot()
        val built=Ewm1SourceIndex(snapshot.records)
        indexKey=root+snapshot.state
        index=built
        return indexKey!! to built
    }

    private fun decideOn(
        core:ContinuumLearningCore,answer:Ewm1Brain.Answer,ownerFacts:Set<String>,baseline:ContinuumChatOutcome
    ):ContinuumBrainResult {
        val on=ContinuumBrainMode.ON
        if(answer.decision=="QUERY" && answer.route=="MEMORY") {
            val ids=answer.cited
            // Revocation protection: read the active facts again; a fact revoked meanwhile is never shown.
            beforeRevocationCheck?.invoke()
            val active=core.activeOwnerFacts().associate { it.id to it.content }
            if(ids.isEmpty() || ids.any { it !in active })
                return ContinuumBrainResult(baseline,on,ContinuumBrainStatus.FALLBACK_REVOKED)
            val text=("Dazu finde ich in meinem Gedächtnis:"+ids.joinToString("") {
                "\n\n"+active.getValue(it).take(250)+"\n(Bestätigte Nutzererinnerung)"
            }).take(1000)
            return ContinuumBrainResult(ContinuumChatOutcome(text,
                "CONTINUUM · EWM2-Gehirn · "+ids.size+" aktuelle Erinnerungen",baseline.trace,true,
                ContinuumBaselineSource.MEMORY,ids),on,ContinuumBrainStatus.ON_MEMORY)
        }
        if(answer.decision=="QUERY" && answer.route=="SOURCE") {
            val record=answer.source!!.evidence.first().record
            return ContinuumBrainResult(ContinuumChatOutcome(archiveAnswer(record),
                "QUELLENABRUF · EWM2-Gehirn · lokal archivierter, nicht bestätigter Beleg",baseline.trace,true,
                ContinuumBaselineSource.ARCHIVE,emptyList(),record.id),on,ContinuumBrainStatus.ON_SOURCE)
        }
        if(answer.decision=="CLARIFY") {
            val titles=answer.source?.evidence.orEmpty().map { it.record.title }.distinct().take(3)
            val text=if(answer.route=="SOURCE" && titles.isNotEmpty())
                "Dazu gibt es im lokalen Wissensarchiv mehrere Einträge: "+titles.joinToString(", ")+". Welchen meinst du?"
            else "Das ist für mich nicht eindeutig. Wen oder was genau meinst du? Bitte nenne den Namen, dann suche ich gezielt."
            return ContinuumBrainResult(ContinuumChatOutcome(text,
                "CONTINUUM · EWM2-Gehirn · Rückfrage statt Vermutung",baseline.trace,true,
                ContinuumBaselineSource.NONE),on,ContinuumBrainStatus.ON_CLARIFY)
        }
        // ABSTAIN: replace only a citation of owner memory or of the archive that the brain found unsupported.
        // Reviewed web notes and source observations in the ledger are outside the brain and stay as they are.
        val ownerOnly=baseline.source==ContinuumBaselineSource.MEMORY && baseline.memoryIds.isNotEmpty() &&
            baseline.memoryIds.all { it in ownerFacts }
        if(baseline.source==ContinuumBaselineSource.ARCHIVE || ownerOnly)
            return ContinuumBrainResult(ContinuumChatOutcome(ContinuumMemoryDialogue.NO_EVIDENCE_ANSWER,
                "CONTINUUM · EWM2-Gehirn · kein passender Beleg",baseline.trace,false,
                ContinuumBaselineSource.NONE),on,ContinuumBrainStatus.ON_ABSTAIN)
        return ContinuumBrainResult(baseline,on,ContinuumBrainStatus.ON_KEPT_BASELINE)
    }

    /** Same wording as the existing archive answer in AgentActivity. */
    internal fun archiveAnswer(document:ContinuumKnowledgeArchive.SourceRecord):String=
        "LOKALES WISSENSARCHIV · öffentlicher, nicht unabhängig verifizierter "+
            "Quellenauszug: "+
            document.text.take(350)+"\nQuelle: "+document.url+
            "\nHerkunft: "+document.attribution+
            "\nSHA-256: "+document.sha256.take(16)+"…"+
            "\nFür zeitabhängige oder rechtliche Aussagen Originalquelle "+
            "und relevanten Stichtag kontrollieren."

    private fun digest(value:String):String=MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
