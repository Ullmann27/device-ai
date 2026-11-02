package at.heinz.deviceai.learning

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local, bounded metadata about the actual JobScheduler path. No source contents,
 * owner facts, embeddings or model weights are written to preferences.
 */
data class ContinuumOfflineJobSnapshot(
    val scheduled: Boolean?,
    val phase: String,
    val lastStartedAt: Long,
    val lastFinishedAt: Long,
    val lastError: String,
    val scheduleError: String = "",
    val recentEvents: String = ""
) {
    fun display(): String = buildString {
        append("Täglicher Offline-Lernjob: ")
        append(when(scheduled) { true -> "eingeplant"; false -> "Einplanung fehlgeschlagen"; null -> "nicht geprüft" })
        if(scheduled==false && scheduleError.isNotBlank())append(" (").append(scheduleError.take(60)).append(")")
        append("\nLetzter Zustand: ").append(phase)
        fun date(timestamp: Long): String =
            SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.GERMANY).format(Date(timestamp))
        if(lastStartedAt>0L)append("\nBeginn: ").append(date(lastStartedAt))
        if(lastFinishedAt>0L)append("\nLetzter Abschluss: ").append(date(lastFinishedAt))
        if(lastError.isNotBlank())append("\nLetzter Fehler: ").append(lastError.take(120))
        if(recentEvents.isNotBlank())append("\nLetzte Lernläufe: ").append(recentEvents.take(512))
        append("\nKeine Ladepflicht. Android kann den Job bei niedrigem Akku, Hitze, Speichermangel oder aus Systemgründen verschieben. Der angezeigte letzte Zustand ist historisch.")
    }
}
object ContinuumOfflineJobStatus {
    private const val PREFS="continuum-offline-job-state"
    private fun prefs(context:Context)=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    /** Rolling, bounded phase audit. Never stores messages, learning examples or URLs. */
    @Synchronized private fun logPhase(context:Context,phase:String,now:Long) {
        val prefs=prefs(context)
        val old=prefs.getString("events","").orEmpty().split('|').filter(String::isNotBlank)
        val next=(old + (now.toString()+":"+phase)).takeLast(8).joinToString("|")
        prefs.edit().putString("events",next).apply()
    }

    fun scheduled(context:Context,success:Boolean,error:String?=null) {
        val edit=prefs(context).edit().putBoolean("scheduled",success)
        // Exception class name only, like failed(): no messages in preferences.
        if(error==null)edit.remove("schedule_error") else edit.putString("schedule_error",error.take(60))
        edit.apply()
    }
    fun started(context:Context,now:Long=System.currentTimeMillis()) {
        logPhase(context,"RUNNING",now)
        prefs(context).edit().putString("phase","RUNNING")
            .putLong("started",now).remove("error").apply()
    }
    fun finished(context:Context,now:Long=System.currentTimeMillis()) {
        logPhase(context,"FINISHED",now)
        prefs(context).edit().putString("phase","FINISHED")
            .putLong("finished",now).remove("error").apply()
    }
    fun stopped(context:Context,now:Long=System.currentTimeMillis()) {
        logPhase(context,"INTERRUPTED",now)
        prefs(context).edit().putString("phase","INTERRUPTED")
            .putLong("finished",now).apply()
    }
    fun failed(context:Context,error:Throwable,now:Long=System.currentTimeMillis()) {
        logPhase(context,"FAILED",now)
        // Never store exception messages: they may include source URLs or owner data.
        prefs(context).edit().putString("phase","FAILED")
            .putLong("finished",now)
            .putString("error",error.javaClass.simpleName.take(60)).apply()
    }
    fun deferred(context:Context,reason:String,now:Long=System.currentTimeMillis()) {
        require(reason in setOf("BATTERY_LOW","BATTERY_UNKNOWN","THERMAL_LIMIT","STORAGE_LOW","TIME_BUDGET"))
        val phase="DEFERRED_"+reason
        logPhase(context,phase,now)
        prefs(context).edit().putString("phase",phase)
            .putLong("finished",now).remove("error").apply()
    }
    fun read(context:Context):ContinuumOfflineJobSnapshot {
        val p=prefs(context)
        return ContinuumOfflineJobSnapshot(
            if(p.contains("scheduled"))p.getBoolean("scheduled",false) else null,
            p.getString("phase","NOT_RUN") ?: "NOT_RUN",
            p.getLong("started",0L),p.getLong("finished",0L),
            p.getString("error","").orEmpty(),
            p.getString("schedule_error","").orEmpty(),
            p.getString("events","").orEmpty()
        )
    }
}
