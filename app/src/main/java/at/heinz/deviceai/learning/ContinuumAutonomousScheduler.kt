package at.heinz.deviceai.learning

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log

/** What [ContinuumAutonomousScheduler.restore] plans; compared field by field with the pending job. */
data class ContinuumJobSpec(
    val serviceClass:String,
    val periodMs:Long,
    val requiresCharging:Boolean,
    val networkType:Int,
    val persisted:Boolean
)

/** The part of JobScheduler the restore decision needs (replaced by a fake in JVM tests). */
interface ContinuumJobPort {
    fun pending():ContinuumJobSpec?
    fun schedule(spec:ContinuumJobSpec):Boolean
}

private class AndroidContinuumJobPort(private val app:Context, private val id:Int):ContinuumJobPort {
    private val jobs get()=app.getSystemService(JobScheduler::class.java)
    override fun pending():ContinuumJobSpec?=jobs.getPendingJob(id)?.let { info ->
        @Suppress("DEPRECATION")
        ContinuumJobSpec(info.service.className,info.intervalMillis,info.isRequireCharging,info.networkType,info.isPersisted)
    }
    override fun schedule(spec:ContinuumJobSpec):Boolean {
        val info=JobInfo.Builder(id,ComponentName(app.packageName,spec.serviceClass))
            .setRequiresCharging(spec.requiresCharging)
            .setRequiredNetworkType(spec.networkType)
            .setPersisted(spec.persisted)
            .setPeriodic(spec.periodMs)
            .build()
        return jobs.schedule(info)==JobScheduler.RESULT_SUCCESS
    }
}

/** Offline-only, bounded learning jobs. No web permission or owner data is used. */
class ContinuumAutonomousScheduler internal constructor(
    private val jobs:ContinuumJobPort,
    private val record:(accepted:Boolean,error:String?)->Unit
) {
    constructor(context:Context):this(AndroidContinuumJobPort(context.applicationContext,JOB_ID),
        { accepted,error -> ContinuumOfflineJobStatus.scheduled(context.applicationContext,accepted,error) })

    /**
     * Called on every app start. JobScheduler.schedule() replaces a pending job and
     * STOPS it when it is running, and a replaced periodic job starts its period
     * again, so an identical pending job is left alone. Scheduling errors (quota,
     * missing permission) are recorded as "not scheduled" instead of crashing the
     * activity.
     */
    fun restore():Boolean {
        var error:String?=null
        val accepted=try {
            jobs.pending()==WANTED || jobs.schedule(WANTED)
        } catch (e:RuntimeException) {
            error=e.javaClass.simpleName
            false
        }
        record(accepted,error)
        return accepted
    }
    companion object {
        const val JOB_ID=620134
        const val PERIOD_MS=24*60*60*1000L
        /** By name: JVM tests must not load the JobService class (android-wiring test checks it). */
        const val SERVICE_CLASS="at.heinz.deviceai.learning.ContinuumAutonomousJobService"
        val WANTED=ContinuumJobSpec(SERVICE_CLASS,PERIOD_MS,
            requiresCharging=true,networkType=JobInfo.NETWORK_TYPE_NONE,persisted=true)
    }
}

/** Android may delay this work; each stage is idempotent and only commits evaluated weights. */
class ContinuumAutonomousJobService:JobService() {
    private val stopped=AtomicBoolean(false)
    private var worker:Thread?=null
    companion object { private const val TAG="ContinuumOfflineLearning" }
    override fun onStartJob(params:JobParameters):Boolean {
        stopped.set(false)
        ContinuumOfflineJobStatus.started(this)
        worker=Thread({
            var retryAfterFailure=false
            try {
                if(!stopped.get()) {
                    val core=ContinuumLearningCore(filesDir)
                    core.bootstrapOfflineCurriculum(stopped::get)
                    if(!stopped.get())core.bootstrapSemanticCurriculum(stopped::get)
                    // Learn only from sources already recorded by the consent-gated
                    // research flow. The job itself never initiates internet access.
                    if(!stopped.get()) {
                        val approved=MentorSourceNotebook(this@ContinuumAutonomousJobService)
                            .entries().takeLast(12)
                        val fresh=ContinuumSourceNovelty.pending(
                            approved, ContinuumSourceNovelty.completedHashes(filesDir)
                        )
                        if(fresh.isNotEmpty() && !stopped.get()) {
                            val report=core.learnFromSources(fresh,stopped::get)
                            // A stopped or timed-out training round is NOT a completed
                            // source evaluation: the same source needs another chance.
                            if(report?.completedEvaluation==true && !stopped.get()) {
                                ContinuumSourceNovelty.markCompleted(filesDir,fresh)
                            }
                        }
                    }
                }
                if(!stopped.get())ContinuumOfflineJobStatus.finished(this@ContinuumAutonomousJobService)
            } catch (e: Exception) {
                // Never silently report success when the offline learning job fails.
                // Preserve the durable model; JobScheduler may retry this job.
                retryAfterFailure=!stopped.get()
                ContinuumOfflineJobStatus.failed(this@ContinuumAutonomousJobService,e)
                Log.e(TAG,"Offline learning consolidation failed",e)
            } finally {
                if(!stopped.get())jobFinished(params,retryAfterFailure)
            }
        },"continuum-autonomous-offline").apply { isDaemon=true;start() }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {
        stopped.set(true)
        ContinuumOfflineJobStatus.stopped(this)
        worker?.interrupt()
        return true // Request a reschedule when Android interrupts the job.
    }
}
