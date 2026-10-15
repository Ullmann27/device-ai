package at.heinz.deviceai.learning

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log

/** Offline-only, bounded learning jobs. No web permission or owner data is used. */
class ContinuumAutonomousScheduler(context: Context) {
    private val app=context.applicationContext
    private val jobs get()=app.getSystemService(JobScheduler::class.java)
    fun restore():Boolean {
        val info=JobInfo.Builder(JOB_ID, ComponentName(app,ContinuumAutonomousJobService::class.java))
            .setRequiresCharging(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .setPersisted(true)
            .setPeriodic(PERIOD_MS)
            .build()
        val accepted=jobs.schedule(info)==JobScheduler.RESULT_SUCCESS
        ContinuumOfflineJobStatus.scheduled(app,accepted)
        return accepted
    }
    companion object {
        const val JOB_ID=620134
        const val PERIOD_MS=24*60*60*1000L
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
