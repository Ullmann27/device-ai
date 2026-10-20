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
            "\nJeweils eine Wikipedia-Quelle, nur beim Laden und mit Netzwerk; "+
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
        if(!enabled) { jobs.cancel(JOB_ID); return false }
        return try {
            val pending=jobs.getPendingJob(JOB_ID)
            if(pending!=null &&
                pending.service.className==ContinuumPublicStudyJobService::class.java.name &&
                pending.isRequireCharging &&
                pending.networkType==JobInfo.NETWORK_TYPE_ANY &&
                pending.isPersisted &&
                pending.intervalMillis==ContinuumPublicStudyPolicy.PERIOD_MS &&
                pending.isRequireBatteryNotLow &&
                pending.isRequireStorageNotLow) return true
            val request=JobInfo.Builder(JOB_ID,
                ComponentName(app,ContinuumPublicStudyJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
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
                                  weightsAccepted:Boolean=false) {
        val edit=prefs.edit()
            .putLong("attempts",(prefs.getLong("attempts",0L)+1L).coerceAtMost(Long.MAX_VALUE-1))
            .putString("phase",phase.take(65))
        if(topic!=null && ContinuumPublicStudyPolicy.validArticleTitle(topic) &&
            phase!="CANCELLED" && phase!="INTERRUPTED" &&
            phase!="PROVIDER_RATE_LIMIT") {
            val (remaining,history)=ContinuumTopicFrontier.afterAttempt(
                loadTopics("topic_pending",ContinuumTopicFrontier.MAX_PENDING),
                loadTopics("topic_visited",ContinuumTopicFrontier.MAX_VISITED),
                topic,!phase.startsWith("FAILED_"))
            edit.putString("topic_pending",remaining.joinToString("\n"))
                .putString("topic_visited",history.joinToString("\n"))
        }
        if(sourceAdded)edit.putLong("sources",prefs.getLong("sources",0L)+1L)
        if(weightsAccepted)edit.putLong("accepted",prefs.getLong("accepted",0L)+1L)
        check(edit.commit()) { "Study audit not persisted" }
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

/** Source retrieval is isolated from user permission scopes and model ToolGate. */
internal object ContinuumPublicStudyFetch {
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
        check(status==200) { "Wikipedia API HTTP_"+status }
        return status
    }

    fun fetch(title:String,cancelled:()->Boolean):MentorSource {
        check(!cancelled()) { "Cycle cancelled" }
        val url=URL(ContinuumPublicStudyPolicy.encyclopediaApiUrl(title))
        check(url.protocol=="https" && url.host=="de.wikipedia.org")
        val conn=(url.openConnection() as HttpsURLConnection)
        conn.instanceFollowRedirects=false
        conn.connectTimeout=6_000
        conn.readTimeout=6_000
        conn.setRequestProperty("User-Agent","DeviceAIContinuumResearchBot/0.5 (https://github.com/Ullmann27; one public article)")
        conn.setRequestProperty("Accept","application/json")
        try {
            requireSuccessfulResponse(conn)
            check(conn.contentType?.lowercase()?.contains("json")==true) { "Unexpected response type" }
            if(conn.contentLengthLong>ContinuumPublicStudyPolicy.MAX_RESPONSE_BYTES)
                error("Source too large")
            val bytes=ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer=ByteArray(2048)
                while(true) {
                    check(!cancelled()) { "Cycle cancelled" }
                    val count=input.read(buffer)
                    if(count<0)break
                    if(count==0)continue
                    check(bytes.size()+count<=ContinuumPublicStudyPolicy.MAX_RESPONSE_BYTES) {
                        "Source exceeded byte limit"
                    }
                    bytes.write(buffer,0,count)
                }
            }
            val text=String(bytes.toByteArray(),StandardCharsets.UTF_8)
            val pages=JSONObject(text).getJSONObject("query").getJSONObject("pages")
            check(pages.length()>0 && pages.length()<=4)
            val keys=pages.keys()
            var extract:String?=null
            while(keys.hasNext()) {
                val next=pages.getJSONObject(keys.next())
                if(next.has("missing") || next.has("invalid"))continue
                val candidate=next.optString("extract")
                if(candidate.isNotBlank()){ extract=candidate; break }
            }
            val excerpt=ContinuumPublicStudyPolicy.normalizeExcerpt(
                extract ?: error("No encyclopedia extract"))
            val result=MentorSource(title,ContinuumPublicStudyPolicy.publicArticleUrl(title),
                ContinuumPublicStudyPolicy.digest(excerpt),excerpt)
            MentorSourceValidation.checkSource(result)
            check(!cancelled())
            return result
        } finally { conn.disconnect() }
    }

    /** One additional, bounded HTTPS request for outbound article links. */
    fun discover(sourceTitle:String,cancelled:()->Boolean):List<String> {
        check(!cancelled())
        val url=URL(ContinuumPublicStudyPolicy.encyclopediaLinksApiUrl(sourceTitle))
        check(url.protocol=="https" && url.host=="de.wikipedia.org")
        val conn=url.openConnection() as HttpsURLConnection
        conn.instanceFollowRedirects=false
        conn.connectTimeout=4_000
        conn.readTimeout=4_000
        conn.setRequestProperty("Accept","application/json")
        conn.setRequestProperty("User-Agent",
            "DeviceAIContinuumResearchBot/0.5 (https://github.com/Ullmann27; source links)")
        try {
            requireSuccessfulResponse(conn)
            check(conn.contentType?.lowercase()?.contains("json")==true)
            check(conn.contentLengthLong<=ContinuumPublicStudyPolicy.MAX_RESPONSE_BYTES)
            val bytes=ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer=ByteArray(1024)
                while(true) {
                    check(!cancelled())
                    val n=input.read(buffer)
                    if(n<0)break
                    if(n==0)continue
                    check(bytes.size()+n<=ContinuumPublicStudyPolicy.MAX_RESPONSE_BYTES)
                    bytes.write(buffer,0,n)
                }
            }
            val result=JSONObject(String(bytes.toByteArray(),StandardCharsets.UTF_8))
                .optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
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
            check(!cancelled())
            return links.distinct()
        } finally { conn.disconnect() }
    }
}

/**
 * JobScheduler invokes one finite cycle per interval, never an endless foreground loop.
 * It can run via cellular or Wi-Fi; the charging and resource constraints remain mandatory.
 */
class ContinuumPublicStudyJobService:JobService() {
    private val stopping=AtomicBoolean(false)
    private var thread:Thread?=null
    override fun onStartJob(params:JobParameters):Boolean {
        val scheduler=ContinuumPublicStudyScheduler(this)
        if(!scheduler.enabled || scheduler.isRateLimited())return false
        stopping.set(false)
        thread=Thread({
            var reschedule=false
            var phase="CANCELLED"
            var added=false
            var accepted=false
            var selectedTopic:String?=null
            val start=SystemClock.elapsedRealtime()
            val cancelled:()->Boolean = {
                stopping.get() || Thread.currentThread().isInterrupted ||
                    SystemClock.elapsedRealtime()-start >=
                        ContinuumPublicStudyPolicy.MAX_ROUND_WALL_MS
            }
            try {
                val researchTitle=scheduler.nextTopic()
                selectedTopic=researchTitle
                val source=ContinuumPublicStudyFetch.fetch(researchTitle,cancelled)
                check(!cancelled())
                // Keep discovery optional: failures here never discard a valid
                // article or interrupt the existing learning pipeline.
                if(SystemClock.elapsedRealtime()-start <
                    ContinuumPublicStudyPolicy.MAX_ROUND_WALL_MS-6_000L) {
                    val discovered=runCatching {
                        ContinuumPublicStudyFetch.discover(source.subject,cancelled)
                    }.onFailure { error ->
                        if(error is WikimediaRateLimited)
                            scheduler.postponeDueToProvider(error.waitSeconds)
                    }.getOrDefault(emptyList())
                    if(!cancelled())scheduler.addDiscovered(discovered)
                }
                val notebook=MentorSourceNotebook(this)
                val pending=ContinuumSourceNovelty.pending(listOf(source),
                    ContinuumSourceNovelty.completedHashes(filesDir))
                if(pending.isEmpty()) {
                    phase="SOURCE_UNCHANGED"
                } else {
                    // Store citation and source hash before any training decision.
                    val archive=ContinuumKnowledgeArchive(filesDir)
                    val archiveCountBefore=archive.stats().values.sum()
                    archive.save(
                        ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                        source.subject,source.url,
                        "Wikipedia-Mitwirkende · CC BY-SA 4.0",
                        source.excerpt
                    )
                    added=archive.stats().values.sum()>archiveCountBefore
                    // Mentor indexing and durable source archiving are separate
                    // results; only the latter changes the archive source count.
                    notebook.add(pending)
                    check(!cancelled())
                    val core=ContinuumLearningCore(filesDir)
                    val outcome=core.learnFromSources(pending,cancelled)
                    if(outcome?.completedEvaluation==true && !cancelled()) {
                        ContinuumSourceNovelty.markCompleted(filesDir,pending)
                        accepted=outcome.accepted
                        phase=if(accepted)"TRAINING_ACCEPTED" else "TRAINING_REJECTED"

                    } else {
                        phase="TRAINING_DEFERRED"
                    }
                }
            } catch(e:Exception) {
                if(e is WikimediaRateLimited)scheduler.postponeDueToProvider(e.waitSeconds)
                phase=if(cancelled())"INTERRUPTED" else if(e is WikimediaRateLimited)
                    "PROVIDER_RATE_LIMIT" else "FAILED_"+e.javaClass.simpleName.take(30)
                reschedule=!stopping.get() && e !is WikimediaRateLimited
                Log.w("ContinuumPublicStudy","Bounded research cycle deferred: "+e.javaClass.simpleName)
            } finally {
                // This is a separate bounded step, so already-known sources or
                // temporarily unavailable Wikipedia do not block local rehearsal.
                // The same deadline, battery and owner-enabled job governs both.
                if(!cancelled() && scheduler.enabled) {
                    runCatching {
                        ContinuumLearningCore(filesDir).trainExtendedCurriculum(cancelled)
                    }.onSuccess { stage ->
                        if(stage!=null && stage.completed)
                            phase=(phase+";STAGE_"+stage.stage+
                                if(stage.accepted)"_ACCEPTED" else "_REJECTED").take(65)
                    }.onFailure { err ->
                        Log.w("ContinuumPublicStudy",
                            "Local rehearsal postponed: "+err.javaClass.simpleName)
                    }
                }
                if(!stopping.get()) {
                    runCatching { scheduler.recordCycle(phase,selectedTopic,added,accepted) }
                    jobFinished(params,reschedule)
                }
            }
        },"continuum-public-study").apply { isDaemon=true;start() }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {
        stopping.set(true)
        thread?.interrupt()
        return ContinuumPublicStudyScheduler(this).enabled
    }
}
