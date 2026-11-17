package at.heinz.deviceai.learning

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/**
 * Explicitly owner-enabled, budgeted, recurrent public-source research.
 * No full web autonomy, no arbitrary URLs, no device commands and no LLM API.
 */
class ContinuumPublicStudyScheduler(context:Context) {
    private val app=context.applicationContext
    private val jobs get()=app.getSystemService(JobScheduler::class.java)
    private val prefs get()=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE)

    val enabled get()=prefs.getBoolean("enabled",false)
    fun internetAllowed():Boolean=app.getSharedPreferences("continuum-internet",Context.MODE_PRIVATE)
        .getString("mode","ASK")=="ALWAYS_ON"
    fun isRateLimited():Boolean = System.currentTimeMillis()<
        prefs.getLong("retry_after_at",0L)
    fun postponeDueToProvider(seconds:Long) {
        val waitMs=seconds.coerceAtLeast(3600L).coerceAtMost(30L*24L*3600L)*1000L
        val until=System.currentTimeMillis()+waitMs
        prefs.edit().putLong("retry_after_at",until)
            .putString("phase","PROVIDER_RATE_LIMIT").commit()
    }
    fun display():String {
        val phase=prefs.getString("phase","NOT_RUN") ?: "NOT_RUN"
        val nextAllowed=prefs.getLong("retry_after_at",0L)
        val pending=runCatching { jobs.getPendingJob(JOB_ID) }.getOrNull()
        val jobStatus=if(pending!=null)"REGISTRIERT" else "NICHT REGISTRIERT"
        val pendingReason=if(pending!=null && android.os.Build.VERSION.SDK_INT>=34)
            runCatching { jobs.getPendingJobReason(JOB_ID) }.getOrNull()
        else null
        val pendingTopics=loadTopics("topic_pending",ContinuumTopicFrontier.MAX_PENDING)
        val seenTopics=loadTopics("topic_visited",ContinuumTopicFrontier.MAX_VISITED)
        val nextTopic=ContinuumTopicFrontier.select(prefs.getLong("attempts",0L),pendingTopics)
        return "AUTONOME ÖFFENTLICHE FORSCHUNG: "+
            (if(enabled)"FREIGEGEBEN" else "AUS")+"\n"+
            "Android-Forschungsjob: "+jobStatus+
            (pendingReason?.let { " · Wartegrund-Code: "+it } ?: "")+"\n"+
            "Letzter Status: "+phase+(if(isRateLimited())
                " · Pause bis "+java.util.Date(nextAllowed) else "")+"\n"+
            "Versuche: "+prefs.getLong("attempts",0)+
            " · neue Quellen: "+prefs.getLong("sources",0)+
            " · akzeptierte SWB1-Runden: "+prefs.getLong("accepted",0)+
            "\nAutomatisch entdeckte Themen in Warteschlange: "+pendingTopics.size+
            " · untersuchte Thementitel: "+seenTopics.size+
            "\nNächstes Forschungsthema: "+nextTopic+
            "\nPro Zyklus eine externe öffentliche Webquelle, mit Beleg und Sicherheitsprüfung; "+
            "Android steuert die tatsächliche Häufigkeit. Jederzeit abschaltbar."
    }

    fun enable():Boolean {
        if(!prefs.edit().putBoolean("enabled",true).commit()) return false
        return restore()
    }

    fun disable():Boolean {
        val saved=prefs.edit().putBoolean("enabled",false)
            .putString("phase","DISABLED").commit()
        jobs.cancel(JOB_ID)
        return saved
    }

    fun restore():Boolean {
        if(!enabled || !internetAllowed()) { jobs.cancel(JOB_ID); return false }
        return try {
            val pending=jobs.getPendingJob(JOB_ID)
            if(pending!=null &&
                pending.service.className==ContinuumPublicStudyJobService::class.java.name &&
                !pending.isRequireCharging &&
                pending.networkType==JobInfo.NETWORK_TYPE_ANY &&
                pending.isPersisted &&
                pending.intervalMillis==ContinuumPublicStudyPolicy.PERIOD_MS &&
                !pending.isRequireBatteryNotLow &&
                pending.isRequireStorageNotLow) return true
            val request=JobInfo.Builder(JOB_ID,
                ComponentName(app,ContinuumPublicStudyJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(false)
                .setRequiresBatteryNotLow(false)
                .setRequiresStorageNotLow(true)
                .setPersisted(true)
                .setPeriodic(ContinuumPublicStudyPolicy.PERIOD_MS)
                .build()
            val ok=jobs.schedule(request)==JobScheduler.RESULT_SUCCESS
            if(!ok)recordPhase("SCHEDULE_FAILED")
            ok
        } catch(e:RuntimeException) {
            recordPhase("SCHEDULE_FAILED_"+e.javaClass.simpleName.take(30))
            false
        }
    }

    private fun loadTopics(key:String,limit:Int):List<String> =
        prefs.getString(key,"").orEmpty().split('\n')
            .filter(ContinuumPublicStudyPolicy::validArticleTitle)
            .distinct().take(limit)

    @Synchronized fun nextTopic():String =
        ContinuumTopicFrontier.select(prefs.getLong("attempts",0L),
            loadTopics("topic_pending",ContinuumTopicFrontier.MAX_PENDING))

    /** Every new topic is a trusted encyclopedia link title, not a web command. */
    @Synchronized fun addDiscovered(titles:List<String>):Int {
        if(!enabled || titles.isEmpty())return 0
        val before=loadTopics("topic_pending",ContinuumTopicFrontier.MAX_PENDING)
        val next=ContinuumTopicFrontier.enqueue(before,
            loadTopics("topic_visited",ContinuumTopicFrontier.MAX_VISITED),titles)
        if(next==before)return 0
        check(prefs.edit().putString("topic_pending",next.joinToString("\n")).commit()) {
            "Research topic queue not persisted"
        }
        return next.size-before.size
    }

    @Synchronized fun recordCycle(phase:String,topic:String?,sourceAdded:Boolean=false,
                                  weightsAccepted:Boolean=false,stopRequested:()->Boolean={ false }) {
        val edit=prefs.edit()
            .putLong("attempts",(prefs.getLong("attempts",0L)+1L).coerceAtMost(Long.MAX_VALUE-1))
            .putString("phase",phase.take(65))
        if(topic!=null && ContinuumPublicStudyPolicy.validArticleTitle(topic) &&
            ContinuumPublicStudyCycleResult.markTopicCompleted(phase)) {
            val (remaining,history)=ContinuumTopicFrontier.afterAttempt(
                loadTopics("topic_pending",ContinuumTopicFrontier.MAX_PENDING),
                loadTopics("topic_visited",ContinuumTopicFrontier.MAX_VISITED),
                topic,true)
            edit.putString("topic_pending",remaining.joinToString("\n"))
                .putString("topic_visited",history.joinToString("\n"))
        }
        if(sourceAdded)edit.putLong("sources",prefs.getLong("sources",0L)+1L)
        if(weightsAccepted)edit.putLong("accepted",prefs.getLong("accepted",0L)+1L)
        val prefsDir=java.io.File(app.applicationInfo.dataDir,"shared_prefs")
        ContinuumStudyAuditWrite.commit({ edit.commit() },stopRequested,
            { android.os.StatFs(prefsDir.path).availableBytes },{ prefsDir.canWrite() })
            ?.let { throw ContinuumStudyAuditNotPersisted(it) }
    }

    fun recordResourceDeferral(reason:String) {
        require(reason in setOf("DEFERRED_BATTERY_LOW","DEFERRED_BATTERY_UNKNOWN",
            "DEFERRED_THERMAL_LIMIT","DEFERRED_STORAGE_LOW"))
        prefs.edit().putString("phase",reason).commit()
    }

    private fun recordPhase(phase:String) {
        prefs.edit().putString("phase",phase).apply()
    }

    companion object {
        const val JOB_ID=620137
        private const val PREFS="continuum-public-study-v1"
    }
}

internal class WikimediaRateLimited(val waitSeconds:Long):
    java.io.IOException("Wikimedia API asked research bot to reduce requests")

/** A title without a suitable article is not an app crash or a learned source. */
internal class WikimediaSourceUnavailable(val reason:String = "EMPTY_EXCERPT"):
    java.io.IOException("SOURCE_UNAVAILABLE_"+reason)

/** Fixed codes only: never publish response bodies or exception messages. */
internal class WikimediaFetchFailure(val code:String): java.io.IOException(code)
internal class WikimediaFetchCancelled: java.io.IOException("CANCELLED")

/** Source retrieval is isolated from user permission scopes and model ToolGate. */
internal object ContinuumPublicStudyFetch {
    private fun decodePages(text:String):JSONObject {
        val json=try { JSONObject(text) } catch (_:org.json.JSONException) {
            throw WikimediaFetchFailure("FETCH_INVALID_JSON")
        }
        if(json.has("error"))throw WikimediaFetchFailure("FETCH_API_ERROR")
        val pages=json.optJSONObject("query")?.optJSONObject("pages")
            ?: throw WikimediaFetchFailure("FETCH_INVALID_RESPONSE")
        if(pages.length() !in 1..4)throw WikimediaFetchFailure("FETCH_INVALID_RESPONSE")
        return pages
    }

    private fun requireSuccessfulResponse(conn:HttpsURLConnection):Int {
        val status=conn.responseCode
        if(status==429 || status==503) {
            val retry=conn.getHeaderField("Retry-After")?.trim().orEmpty()
            val value=retry.toLongOrNull() ?: runCatching {
                val at=ZonedDateTime.parse(retry,DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli()
                ((at-System.currentTimeMillis()+999L)/1000L).coerceAtLeast(3600L)
            }.getOrDefault(6L*3600L)
            throw WikimediaRateLimited(value)
        }
        if(status!=200)throw WikimediaFetchFailure("FETCH_HTTP_"+status)
        return status
    }

    fun fetch(title:String,cancelled:()->Boolean,
              openConnection:(URL)->HttpsURLConnection = { it.openConnection() as HttpsURLConnection }):MentorSource {
        if(cancelled())throw WikimediaFetchCancelled()
        val url=URL(ContinuumPublicStudyPolicy.encyclopediaApiUrl(title))
        check(url.protocol=="https" && url.host=="de.wikipedia.org")
        val conn=openConnection(url)
        conn.instanceFollowRedirects=false
        conn.connectTimeout=6_000
        conn.readTimeout=6_000
        conn.setRequestProperty("User-Agent","DeviceAIContinuumResearchBot/0.5 (https://github.com/Ullmann27; one public article)")
        conn.setRequestProperty("Accept","application/json")
        try {
            requireSuccessfulResponse(conn)
            if(conn.contentType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT)!="application/json")
                throw WikimediaFetchFailure("FETCH_UNEXPECTED_CONTENT")
            if(conn.contentLengthLong>ContinuumPublicStudyPolicy.MAX_RESPONSE_BYTES)
                throw WikimediaFetchFailure("FETCH_TOO_LARGE")
            val bytes=ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer=ByteArray(2048)
                while(true) {
                    if(cancelled())throw WikimediaFetchCancelled()
                    val count=input.read(buffer)
                    if(count<0)break
                    if(count==0)continue
                    if(bytes.size()+count>ContinuumPublicStudyPolicy.MAX_RESPONSE_BYTES)
                        throw WikimediaFetchFailure("FETCH_TOO_LARGE")
                    bytes.write(buffer,0,count)
                }
            }
            val text=String(bytes.toByteArray(),StandardCharsets.UTF_8)
            val pages=decodePages(text)
            val keys=pages.keys()
            var extract:String?=null
            var missing=false
            while(keys.hasNext()) {
                val next=pages.optJSONObject(keys.next()) ?: throw WikimediaFetchFailure("FETCH_INVALID_RESPONSE")
                if(next.has("missing") || next.has("invalid")){ missing=true; continue }
                val value=next.opt("extract")
                if(value!=null && value !is String)throw WikimediaFetchFailure("FETCH_INVALID_RESPONSE")
                val candidate=value as? String ?: ""
                if(candidate.isNotBlank()){ extract=candidate; break }
            }
            val excerpt=try {
                ContinuumPublicStudyPolicy.normalizeExcerpt(
                    extract ?: throw WikimediaSourceUnavailable(if(missing)"MISSING_PAGE" else "EMPTY_EXCERPT"))
            } catch (_:IllegalArgumentException) {
                throw WikimediaSourceUnavailable()
            }
            val result=MentorSource(title,ContinuumPublicStudyPolicy.publicArticleUrl(title),
                ContinuumPublicStudyPolicy.digest(excerpt),excerpt)
            MentorSourceValidation.checkSource(result)
            if(cancelled())throw WikimediaFetchCancelled()
            return result
        } finally { conn.disconnect() }
    }

    /** One additional, bounded HTTPS request for outbound article links. */
    fun discover(sourceTitle:String,cancelled:()->Boolean,
                 openConnection:(URL)->HttpsURLConnection = { it.openConnection() as HttpsURLConnection }):List<String> {
        if(cancelled())throw WikimediaFetchCancelled()
        val url=URL(ContinuumPublicStudyPolicy.encyclopediaLinksApiUrl(sourceTitle))
        check(url.protocol=="https" && url.host=="de.wikipedia.org")
        val conn=openConnection(url)
        conn.instanceFollowRedirects=false
        conn.connectTimeout=4_000
        conn.readTimeout=4_000
        conn.setRequestProperty("Accept","application/json")
        conn.setRequestProperty("User-Agent",
            "DeviceAIContinuumResearchBot/0.5 (https://github.com/Ullmann27; source links)")
        try {
            requireSuccessfulResponse(conn)
            if(conn.contentType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT)!="application/json")
                throw WikimediaFetchFailure("FETCH_UNEXPECTED_CONTENT")
            if(conn.contentLengthLong>ContinuumPublicStudyPolicy.MAX_RESPONSE_BYTES)
                throw WikimediaFetchFailure("FETCH_TOO_LARGE")
            val bytes=ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer=ByteArray(1024)
                while(true) {
                    if(cancelled())throw WikimediaFetchCancelled()
                    val n=input.read(buffer)
                    if(n<0)break
                    if(n==0)continue
                    if(bytes.size()+n>ContinuumPublicStudyPolicy.MAX_RESPONSE_BYTES)
                        throw WikimediaFetchFailure("FETCH_TOO_LARGE")
                    bytes.write(buffer,0,n)
                }
            }
            val result=decodePages(String(bytes.toByteArray(),StandardCharsets.UTF_8))
            val links=ArrayList<String>(16)
            val pages=result.keys()
            while(pages.hasNext() && links.size<16) {
                val page=result.optJSONObject(pages.next()) ?: continue
                val entries=page.optJSONArray("links") ?: continue
                for(i in 0 until minOf(entries.length(),16)) {
                    val item=entries.optJSONObject(i) ?: continue
                    val title=item.optString("title")
                    if(item.optInt("ns",-1)==0 &&
                        ContinuumPublicStudyPolicy.validArticleTitle(title) &&
                        title!=sourceTitle) links.add(title)
                }
            }
            if(cancelled())throw WikimediaFetchCancelled()
            return links.distinct()
        } finally { conn.disconnect() }
    }
}

/**
 * JobScheduler invokes one finite cycle per interval, never an endless foreground loop.
 * It can run unplugged over cellular or Wi-Fi. Each cycle checks the locally
 * measured 20% battery threshold, keeping bounded source/gradient budgets.
 */
class ContinuumPublicStudyJobService:JobService() {
    private val stopping=AtomicBoolean(false)
    private var thread:Thread?=null
    @Synchronized override fun onStartJob(params:JobParameters):Boolean {
        if(thread?.isAlive==true)return false
        val scheduler=ContinuumPublicStudyScheduler(this)
        if(!scheduler.enabled || scheduler.isRateLimited())return false
        if(!scheduler.internetAllowed())return false
        // Do not create a false research attempt or run gradients below 20%.
        // JobScheduler itself must not impose the device-dependent low-battery gate.
        val deferral=ContinuumOfflineLearningBudget.deferralReason(continuumLearningResources())
            ?.let { "DEFERRED_"+it }
        if(deferral!=null) {
            scheduler.recordResourceDeferral(deferral)
            runCatching {
                ContinuumTrainingMonitor.begin(this,ContinuumTrainingMonitor.PUBLIC,
                    "Ressourcengrenzen prüfen","SWB1")
                ContinuumTrainingMonitor.end(this,ContinuumTrainingMonitor.PUBLIC,
                    "DEFERRED",deferral)
            }
            return false
        }
        stopping.set(false)
        thread=Thread({
            var reschedule=false
            var phase="CANCELLED"
            var added=false
            var accepted=false
            var selectedTopic:String?=null
            var operation="SELECT_TOPIC"
            val start=SystemClock.elapsedRealtime()
            val budget=ContinuumLearningRunBudget(start,ContinuumPublicStudyPolicy.MAX_ROUND_WALL_MS,
                { SystemClock.elapsedRealtime() }, { continuumLearningResources() })
            val cancelled:()->Boolean = {
                stopping.get() || Thread.currentThread().isInterrupted ||
                    !scheduler.enabled || !scheduler.internetAllowed() ||
                    !at.heinz.deviceai.cognitive.ContinuumAutonomyControl.mayResearch(
                        at.heinz.deviceai.cognitive.ContinuumAutonomyControl.level(this@ContinuumPublicStudyJobService)) || budget.cancelled()
            }
            try {
                val researchTitle=scheduler.nextTopic()
                selectedTopic=researchTitle
                ContinuumTrainingMonitor.begin(this@ContinuumPublicStudyJobService,
                    ContinuumTrainingMonitor.PUBLIC,researchTitle,"SWB1")
                operation="FETCH_SOURCE"
                ContinuumTrainingMonitor.phase(this@ContinuumPublicStudyJobService,
                    ContinuumTrainingMonitor.PUBLIC,"FETCH_SOURCE")
                val completedHashes=ContinuumSourceNovelty.knownHashes(filesDir)
                val acquisition=ContinuumOpenWebStudyFetch.acquire(
                    researchTitle,cancelled,completedHashes
                )
                val acquiredSource=acquisition.source
                check(!cancelled())
                // Reuse already discovered candidate titles: no second network search.
                if(SystemClock.elapsedRealtime()-start <
                    ContinuumPublicStudyPolicy.MAX_ROUND_WALL_MS-6_000L) {
                    ContinuumTrainingMonitor.phase(this@ContinuumPublicStudyJobService,
                        ContinuumTrainingMonitor.PUBLIC,"DISCOVER_TOPICS")
                    operation="DISCOVER_TOPICS"
                    val discovered=acquisition.discoveredTitles
                    if(!cancelled())scheduler.addDiscovered(discovered)
                }
                operation="READ_SOURCE_EVIDENCE"
                val notebook=MentorSourceNotebook(this)
                val pending=checkNotNull(ContinuumLearningCore.prepareSourceBatch(
                    ContinuumSourceNovelty.pending(listOf(acquiredSource),completedHashes))) {
                    "Source batch exceeds 17 records"
                }
                if(pending.isEmpty()) {
                    phase="SOURCE_UNCHANGED"
                } else {
                    val source=pending.single()
                    check(!cancelled())
                    operation="START_SOURCE_ROUND"
                    ContinuumSourceNovelty.markStarted(filesDir,pending)
                    operation="ARCHIVE_SOURCE"
                    ContinuumTrainingMonitor.phase(this@ContinuumPublicStudyJobService,
                        ContinuumTrainingMonitor.PUBLIC,"ARCHIVE_SOURCE")
                    // Store citation and source hash before any training decision.
                    val archive=ContinuumKnowledgeArchive(filesDir)
                    val archiveCountBefore=archive.stats().values.sum()
                    val archiveKind=if(source.url.startsWith("https://de.wikipedia.org/wiki/"))
                        ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT
                    else ContinuumKnowledgeArchive.Kind.PUBLIC_WEB_EXCERPT
                    archive.save(
                        archiveKind,
                        source.subject,source.url,
                        "Öffentliche HTTPS-Quelle · unbestätigter kurzer Auszug",
                        source.excerpt
                    )
                    added=archive.stats().values.sum()>archiveCountBefore
                    // Mentor indexing and durable source archiving are separate
                    // results; only the latter changes the archive source count.
                    operation="INDEX_SOURCE"
                    notebook.add(pending)
                    check(!cancelled())
                    operation="PREPARE_TRAINING"
                    val core=ContinuumLearningCore(filesDir).apply {
                        observeTraining { trainingPhase ->
                            operation=trainingPhase
                            ContinuumTrainingMonitor.phase(this@ContinuumPublicStudyJobService,
                                ContinuumTrainingMonitor.PUBLIC,trainingPhase,
                                "Quellenbezogenes SWB1 · Gradienten/Audit")
                        }
                    }
                    ContinuumTrainingMonitor.phase(this@ContinuumPublicStudyJobService,
                        ContinuumTrainingMonitor.PUBLIC,"PREPARING",
                        "Quellenbezogene Kandidatenrunde vorbereiten")
                    operation="TRAIN_SOURCE"
                    val outcome=core.learnFromSources(pending,cancelled)
                    operation="AUDIT_SOURCE"
                    ContinuumTrainingMonitor.phase(this@ContinuumPublicStudyJobService,
                        ContinuumTrainingMonitor.PUBLIC,"AUDIT","Qualitätsgate für Kandidatengewichte")
                    if(outcome?.completedEvaluation==true && !cancelled()) {
                        accepted=outcome.accepted
                        phase=if(accepted)"TRAINING_ACCEPTED" else "TRAINING_REJECTED"

                    } else {
                        phase="TRAINING_DEFERRED"
                    }
                }
            } catch(e:Exception) {
                if(e is WikimediaRateLimited)scheduler.postponeDueToProvider(e.waitSeconds)
                phase=if(e is ContinuumSourceEvidenceNotPersisted)
                    "FAILED_SAVE_EVIDENCE_"+e.cause!!.javaClass.simpleName
                    else ContinuumPublicStudyCycleResult.failurePhase(operation,e,cancelled())
                reschedule=!stopping.get() && e !is WikimediaRateLimited &&
                    e !is WikimediaSourceUnavailable && e !is WikimediaFetchCancelled && !cancelled()
                Log.w("ContinuumPublicStudy","Research cycle result: "+phase)
            } finally {
                // This is a separate bounded step, so already-known sources or
                // temporarily unavailable search providers do not block local rehearsal.
                // The same deadline, battery and owner-enabled job governs both.
                if(!cancelled() && scheduler.enabled) {
                    runCatching {
                        ContinuumTrainingMonitor.phase(this@ContinuumPublicStudyJobService,
                            ContinuumTrainingMonitor.PUBLIC,"PREPARING",
                            "Großes SWB1-Übungsarchiv: nächste offene Stufe prüfen")
                        ContinuumLearningCore(filesDir).apply {
                            observeTraining { trainingPhase ->
                                ContinuumTrainingMonitor.phase(this@ContinuumPublicStudyJobService,
                                    ContinuumTrainingMonitor.PUBLIC,trainingPhase,
                                    "Großes SWB1-Übungsarchiv · Gradienten/Audit")
                            }
                        }.trainExtendedCurriculum(cancelled)
                    }.onSuccess { stage ->
                        ContinuumTrainingMonitor.phase(this@ContinuumPublicStudyJobService,
                            ContinuumTrainingMonitor.PUBLIC,"AUDIT",
                            "Unabhängige Holdouts und Replay geprüft")
                        if(stage!=null && stage.completed)
                            phase=(phase+";STAGE_"+stage.stage+
                                if(stage.accepted)"_ACCEPTED" else "_REJECTED").take(65)
                    }.onFailure { err ->
                        Log.w("ContinuumPublicStudy",
                            "Local rehearsal postponed: "+err.javaClass.simpleName)
                    }
                }
                if(!stopping.get()) {
                    phase=ContinuumPublicStudyCycleResult.withDeferral(phase,budget.deferredReason)
                    val saved=runCatching {
                        scheduler.recordCycle(phase,selectedTopic,added,accepted,stopRequested={ stopping.get() })
                    }
                    if(saved.isFailure) {
                        // A failed durable audit is not a verified research success. The cause is a fixed code
                        // (issue #76); a stop that arrived during the write is an interruption, not a failure, and
                        // an impaired disk is not retried at once.
                        val reason=(saved.exceptionOrNull() as? ContinuumStudyAuditNotPersisted)?.reason
                        phase="FAILED_SAVE_CYCLE_"+
                            (reason ?: saved.exceptionOrNull()?.javaClass?.simpleName ?: "Unknown")
                        if(reason=="STOPPED")phase="INTERRUPTED"
                        phase=phase.take(65)
                        reschedule=false
                        Log.w("ContinuumPublicStudy","Research cycle audit not persisted")
                    }
                    val outcome=ContinuumPublicStudyCycleResult.outcome(phase)
                    runCatching {
                        ContinuumTrainingMonitor.end(this@ContinuumPublicStudyJobService,
                            ContinuumTrainingMonitor.PUBLIC,outcome,phase)
                    }
                    jobFinished(params,reschedule)
                }
            }
        },"continuum-public-study").apply { isDaemon=true;start() }
        return true
    }
    @Synchronized override fun onStopJob(params:JobParameters):Boolean {
        stopping.set(true)
        thread?.interrupt()
        runCatching {
            ContinuumTrainingMonitor.end(this,ContinuumTrainingMonitor.PUBLIC,
                "INTERRUPTED","Android hat den Forschungsjob unterbrochen")
        }
        return ContinuumPublicStudyScheduler(this).enabled
    }
}
