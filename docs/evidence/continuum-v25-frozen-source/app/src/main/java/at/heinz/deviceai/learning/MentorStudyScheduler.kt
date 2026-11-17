package at.heinz.deviceai.learning

import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.Context
import at.heinz.deviceai.cognitive.*
import org.json.JSONArray
import org.json.JSONObject


class MentorSourceNotebook(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("device_ai_mentor_studies_v1",Context.MODE_PRIVATE)
    val enabled:Boolean get()=prefs.getBoolean("enabled",false)
    val attempts:Long get()=prefs.getLong("attempts",0)
    val successes:Long get()=prefs.getLong("successes",0)
    val status:String get()=prefs.getString("status","Noch kein Studienzyklus.") ?: "Kein Status"
    fun enable():Boolean=prefs.edit().putBoolean("enabled",true).putString("status","Mentor bereit; jede Internetrecherche benötigt eine einmalige Freigabe.").commit()
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
        fun checkSource(x:MentorSource) = MentorSourceValidation.checkSource(x)
    }
}

/** Converts an already owner-approved dossier; performs no network access. */
class MentorResearchCycle {
    fun sources(area:String,dossier:ResearchDossier):List<MentorSource> {
        val claims=WebLearningInbox.candidates(dossier).filter { !it.contradictory }.take(3)
            .map { MentorSource(area,it.sourceUrl,it.sourceHash,it.text.take(390)) }
        val excerpts=if(claims.isEmpty())dossier.sources.filter { it.duplicateOf==null }.take(2)
            .map { MentorSource(area,it.canonicalUrl,it.contentHash,
                it.snippet.replace(Regex("\\s+")," ").trim().take(390)) } else emptyList()
        return (claims+excerpts).filter { runCatching { MentorSourceNotebook.checkSource(it) }.isSuccess }
            .distinctBy { it.sha+it.excerpt }.take(3)
    }
}

class MentorStudyScheduler(context:Context) {
    private val app=context.applicationContext
    private val jobs get()=app.getSystemService(JobScheduler::class.java)
    val notebook get()=MentorSourceNotebook(app)
    fun enable():Boolean { jobs.cancel(JOB_ID);return notebook.enable() }
    fun disable(){notebook.disable();jobs.cancel(JOB_ID)}
    // Legacy schedules have no one-shot owner grant and cannot perform network I/O.
    fun restore(){jobs.cancel(JOB_ID)}
    fun runOnce(cancelled:()->Boolean={false}):String {
        jobs.cancel(JOB_ID)
        return "Internet auf Nachfrage: Forschungsfrage im Chat einmal freigeben."
    }
    companion object {
        const val JOB_ID=620133
    }
}
/** Installed upgrades can retain an old job. Cancel it without any I/O. */
class MentorStudyJobService:JobService() {
    override fun onStartJob(params:JobParameters):Boolean {
        MentorStudyScheduler(this).restore()
        return false
    }
    override fun onStopJob(params:JobParameters)=false
}
