package at.heinz.deviceai.learning

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import at.heinz.deviceai.cognitive.AndroidKeystoreIdentitySigner
import at.heinz.deviceai.cognitive.ContinuumDnaGuardian
import at.heinz.deviceai.cognitive.DeviceTeamleader
import java.util.concurrent.atomic.AtomicBoolean

/** Android is the primary coordinator now. A PC can later consume the SAME
 * canonical DevelopmentBacklog; there is no second agent identity.
 */
data class DeviceTeamleaderJobSpec(
    val serviceClass: String,
    val periodMs: Long,
    val persisted: Boolean,
    val charging: Boolean,
    val networkType: Int,
    val batteryNotLow: Boolean,
    val storageNotLow: Boolean
)

interface DeviceTeamleaderJobPort {
    fun pending(): DeviceTeamleaderJobSpec?
    fun schedule(spec: DeviceTeamleaderJobSpec): Boolean
}

private class AndroidDeviceTeamleaderJobPort(
    private val app: Context,
    private val jobId: Int
) : DeviceTeamleaderJobPort {
    private val jobs get() = app.getSystemService(JobScheduler::class.java)
    override fun pending(): DeviceTeamleaderJobSpec? =
        jobs.getPendingJob(jobId)?.let { job ->
            DeviceTeamleaderJobSpec(job.service.className, job.intervalMillis,
                job.isPersisted, job.isRequireCharging, job.networkType,
                job.isRequireBatteryNotLow, job.isRequireStorageNotLow)
        }

    override fun schedule(spec: DeviceTeamleaderJobSpec): Boolean {
        val info = JobInfo.Builder(jobId, ComponentName(app.packageName, spec.serviceClass))
            .setPeriodic(spec.periodMs)
            .setPersisted(spec.persisted)
            .setRequiresCharging(spec.charging)
            .setRequiredNetworkType(spec.networkType)
            .setRequiresBatteryNotLow(spec.batteryNotLow)
            .setRequiresStorageNotLow(spec.storageNotLow)
            .build()
        return jobs.schedule(info) == JobScheduler.RESULT_SUCCESS
    }
}

class ContinuumDeviceTeamleaderScheduler internal constructor(
    private val jobs: DeviceTeamleaderJobPort
) {
    constructor(context: Context) : this(
        AndroidDeviceTeamleaderJobPort(context.applicationContext, JOB_ID))

    /** Avoid repeatedly replacing jobs on every app start: it would stop a
     * currently running cycle and defer the next run unnecessarily.
     */
    fun restore(): Boolean = try {
        jobs.pending() == WANTED || jobs.schedule(WANTED)
    } catch (_: RuntimeException) { false }

    companion object {
        const val JOB_ID = 620135
        const val PERIOD_MS = 60L * 60L * 1000L
        const val SERVICE_CLASS =
            "at.heinz.deviceai.learning.ContinuumDeviceTeamleaderJobService"
        val WANTED = DeviceTeamleaderJobSpec(
            SERVICE_CLASS, PERIOD_MS, persisted = true,
            charging = false, networkType = JobInfo.NETWORK_TYPE_NONE,
            batteryNotLow = false, storageNotLow = true
        )
    }
}

/** Periodic Android-side integrity and backlog housekeeping.
 * JobScheduler runs this when device resources permit; Android may defer or
 * stop it. No shell, network, repository clone, build or unreviewed code write.
 */
class ContinuumDeviceTeamleaderJobService : JobService() {
    @Volatile private var activeWorker: Thread? = null
    @Volatile private var activeCancellation: AtomicBoolean? = null

    private fun currentResources():ContinuumLearningResources = continuumLearningResources()

    @Synchronized override fun onStartJob(params: JobParameters): Boolean {
        // Android may stop one job while its thread is still cleaning up.
        // Never re-enable cancellation on that earlier thread.
        if (activeWorker?.isAlive == true) return false
        val cancellation = AtomicBoolean(false)
        activeCancellation = cancellation
        val next = Thread({
            try {
                val teamleader = DeviceTeamleader(
                    filesDir,
                    dnaAudit = {
                        ContinuumDnaGuardian(java.io.File(filesDir, "continuum")) {
                            AndroidKeystoreIdentitySigner()
                        }.inspect()
                    }
                )
                val reason = ContinuumOfflineLearningBudget.deferralReason(currentResources())
                if (reason != null) {
                    if (!cancellation.get()) teamleader.defer(reason)
                } else if (!cancellation.get()) {
                    teamleader.runOnce {
                        cancellation.get() || Thread.currentThread().isInterrupted
                    }
                    // A corrupted model or backlog is a persistent failure,
                    // not a reason to spin an immediate JobScheduler retry.
                    // This periodic job already has a future scheduled run.
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                Log.e(TAG, "Android Teamleader cycle failed", e)
            } finally {
                try {
                    // Finish the previous JobScheduler generation before it
                    // becomes possible for another generation to be started.
                    if (!cancellation.get()) jobFinished(params, false)
                } finally {
                    synchronized(this@ContinuumDeviceTeamleaderJobService) {
                        if (activeCancellation === cancellation) {
                            activeCancellation = null
                            activeWorker = null
                        }
                    }
                }
            }
        }, "continuum-device-teamleader").apply { isDaemon = true }
        activeWorker = next
        next.start()
        return true
    }

    @Synchronized override fun onStopJob(params: JobParameters): Boolean {
        activeCancellation?.set(true)
        activeWorker?.interrupt()
        return true // Android may reschedule this bounded local diagnostic.
    }

    companion object {
        private const val TAG = "ContinuumTeamleader"
    }
}
