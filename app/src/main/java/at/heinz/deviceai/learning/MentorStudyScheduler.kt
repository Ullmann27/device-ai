package at.heinz.deviceai.learning

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import at.heinz.deviceai.cognitive.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** A researched excerpt is evidence, not trusted knowledge or a neural training label. */
data class MentorSource(val subject:String,val url:String,val sha:String,val excerpt:String)

class MentorSourceNotebook(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("device_ai_mentor_studies_v1",Context.MODE_PRIVATE)
    val enabled:Boolean get()=prefs.getBoolean("enabled",false)
    val attempts:Long get()=prefs.getLong("attempts",0)
    val successes:Long get()=prefs.getLong("successes",0)
    val status:String get()=prefs.getString("status","Noch kein Studienzyklus.") ?: "Kein Status"
    fun enable():Boolean=prefs.edit().putBoolean("enabled",true).putString("status","Forschung freigegeben; warte auf Ladebetrieb.").commit()
    fun disable():Boolean=prefs.edit().putBoolean("enabled",false).putString("status","Forschung angehalten.").commit()
    fun entries():List<MentorSource> = synchronized(LOCK) {
        val text=prefs.getString("entries","[]") ?: "[]"
        require(text.length<=70000)
        val a=JSONArray(text)
        require(a.length()<=48)
        (0 until a.length()).map { i ->
            val o=a.getJSONObject(i)
            MentorSource(o.getString("subject"),o.getString("url"),o.getString("sha"),o.getString("excerpt"))
                .also(::checkSource)
        }
    }
    /** Restores an exact-source-bound associative model, rebuilding older notebooks on read. */
    fun conceptNetwork():MentorConceptNetwork=synchronized(LOCK) {
        val current=entries()
        val encoded=prefs.getString("associations",null)
        if(encoded==null)MentorConceptNetwork.build(current)
        else MentorConceptNetwork.restore(encoded,current)
    }
    /** Restores the source-bound distributional representation learned from the same notes. */
    fun semanticModel():MentorSemanticModel=synchronized(LOCK) {
        val current=entries()
        val encoded=prefs.getString("semantics",null)
        if(encoded==null)MentorSemanticModel.build(current)
        else MentorSemanticModel.restore(encoded,current)
    }
    fun add(newItems:List<MentorSource>):Int=synchronized(LOCK) {
        newItems.forEach(::checkSource)
        val old=entries()
        val merged=(old+newItems).distinctBy { it.sha+it.excerpt }.takeLast(48)
        val added=merged.count { next -> old.none { it.sha==next.sha && it.excerpt==next.excerpt } }
        val data=JSONArray()
        for (item in merged) data.put(JSONObject().put("subject",item.subject).put("url",item.url)
            .put("sha",item.sha).put("excerpt",item.excerpt))
        // Compute a new graph before the write. Both records and graph are published
        // atomically in one SharedPreferences editor, or neither replaces the old state.
        val network=MentorConceptNetwork.build(merged).serialize()
        val semantics=MentorSemanticModel.build(merged).serialize()
        check(prefs.edit().putString("entries",data.toString())
            .putString("associations",network)
            .putString("semantics",semantics).commit())
        added
    }
    fun record(subject:String,found:Int,message:String) {
        synchronized(LOCK) {
            val edit=prefs.edit()
                .putLong("attempts",(attempts+1).coerceAtMost(Long.MAX_VALUE-1))
                .putString("status",(subject+": "+message).take(400))
            if(found>0)edit.putLong("successes",(successes+1).coerceAtMost(Long.MAX_VALUE-1))
            check(edit.commit())
        }
    }
    fun clear():Boolean=synchronized(LOCK){
        prefs.edit().remove("entries").remove("associations").remove("semantics").commit()
    }
    companion object {
        private val LOCK=Any()
        fun checkSource(x:MentorSource) {
            require(x.subject.length in 3..80 && x.url.startsWith("https://de.wikipedia.org/wiki/") &&
                x.url.length<=420 && x.sha.matches(Regex("[0-9a-f]{64}")) &&
                x.excerpt.length in 20..400)
        }
    }
}

/** One bounded topic study; never downloads model weights or grants tool authority. */
class MentorResearchCycle {
    fun read(topic:MentorCurriculum.Topic):List<MentorSource> {
        val policy=ResearchBudgetPolicy.forMode(ResearchMode.QUICK)
        val engine=DeepResearchEngine(
            discovery=WikipediaResearchDiscoveryProvider(),
            provider=HttpReadOnlyResearchProvider(ResearchProviderPolicy(
                maxBytes=policy.maxBytesPerSource,
                totalTimeoutMillis=policy.totalDeadlineMillis.toInt()
            ))
        )
        val dossier=engine.research(topic.query,ResearchMode.QUICK)
        val claims=WebLearningInbox.candidates(dossier).filter { !it.contradictory }.take(3)
            .map { MentorSource(topic.area,it.sourceUrl,it.sourceHash,it.text.take(390)) }
        val excerpts=if(claims.isEmpty()) dossier.sources.filter { it.duplicateOf==null }.take(2)
            .map { source -> MentorSource(topic.area,source.canonicalUrl,source.contentHash,
                source.snippet.replace(Regex("\\s+")," ").trim().take(390)) }
        else emptyList()
        return (claims+excerpts).filter { runCatching { MentorSourceNotebook.checkSource(it) }.isSuccess }
            .distinctBy { it.sha+it.excerpt }.take(3)
    }
}

class MentorStudyScheduler(context:Context) {
    private val app=context.applicationContext
    private val jobs get()=app.getSystemService(JobScheduler::class.java)
    val notebook get()=MentorSourceNotebook(app)
    fun enable():Boolean {
        if(!schedule())return false
        if(!notebook.enable()){jobs.cancel(JOB_ID);return false}
        return true
    }
    fun disable(){notebook.disable();jobs.cancel(JOB_ID)}
    fun restore(){if(notebook.enabled && jobs.getPendingJob(JOB_ID)==null)schedule()}
    fun runOnce(cancelled:()->Boolean={false}):String {
        val book=notebook
        if(!book.enabled || cancelled())return "Wissensforscher ausgeschaltet."
        if(!BUSY.compareAndSet(false,true))return "Ein Studienzyklus läuft bereits."
        val topic=MentorCurriculum.select(book.attempts)
        try {
            val found=MentorResearchCycle().read(topic)
            if(cancelled() || !book.enabled)return "Studium abgebrochen."
            val count=book.add(found)
            val msg=if(count>0) "Neue unbestätigte Quellenbeobachtungen: "+count
                else "Keine neuen Quellen; kein Wissen erfunden."
            book.record(topic.area,count,msg)
            return topic.area+": "+msg
        } catch(e:Exception) {
            if(cancelled())return "Studium abgebrochen."
            val msg="Recherche fehlgeschlagen ("+e.javaClass.simpleName+"); keine Daten erfunden."
            runCatching { book.record(topic.area,0,msg) }
            return topic.area+": "+msg
        } finally { BUSY.set(false) }
    }
    private fun schedule():Boolean {
        val job=JobInfo.Builder(JOB_ID,ComponentName(app,MentorStudyJobService::class.java))
            .setRequiresCharging(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(6*60*60*1000L).setPersisted(true).build()
        return jobs.schedule(job)==JobScheduler.RESULT_SUCCESS
    }
    companion object {
        const val JOB_ID=620133
        private val BUSY=AtomicBoolean(false)
    }
}
class MentorStudyJobService:JobService() {
    private val cancelled=AtomicBoolean(false)
    private var worker:Thread?=null
    override fun onStartJob(params:JobParameters):Boolean {
        if(!MentorStudyScheduler(this).notebook.enabled)return false
        cancelled.set(false)
        worker=Thread({
            try {MentorStudyScheduler(applicationContext).runOnce(cancelled::get)}
            finally {if(!cancelled.get())jobFinished(params,false)}
        },"device-ai-studies").apply {isDaemon=true;start()}
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {
        cancelled.set(true)
        worker?.interrupt()
        return false
    }
}
