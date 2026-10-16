package at.heinz.deviceai.learning

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.util.Log
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
            batteryNotLow = true, storageNotLow = true
        )
    }
}

/** Periodic Android-side integrity and backlog housekeeping.
 * JobScheduler runs this when device resources permit; Android may defer or
 * stop it. No shell, network, repository clone, build or unreviewed code write.
 */
class ContinuumDeviceTeamleaderJobService : JobService() {
    private val stopped = AtomicBoolean(false)
    @Volatile private var activeWorker: Thread? = null

    private fun currentResources(): ContinuumLearningResources {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            getSystemService(PowerManager::class.java)?.currentThermalStatus else null
        val space = StatFs(filesDir.absolutePath).availableBytes
        return ContinuumLearningResources(percent, false, thermal, space)
    }

    override fun onStartJob(params: JobParameters): Boolean {
        stopped.set(false)
        activeWorker = Thread({
            var reschedule = false
            try {
                val teamleader = DeviceTeamleader(filesDir)
                val reason = ContinuumOfflineLearningBudget.deferralReason(currentResources())
                if (reason != null) {
                    teamleader.defer(reason)
                } else if (!stopped.get()) {
                    val result = teamleader.runOnce { stopped.get() }
                    reschedule = result.state == "ERROR" && !stopped.get()
                }
            } catch (e: Exception) {
                reschedule = !stopped.get()
                Log.e(TAG, "Android Teamleader cycle failed", e)
            } finally {
                if (!stopped.get()) jobFinished(params, reschedule)
            }
        }, "continuum-device-teamleader").apply { isDaemon = true; start() }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped.set(true)
        activeWorker?.interrupt()
        return true
    }

    companion object {
        private const val TAG = "ContinuumTeamleader"
    }
}
