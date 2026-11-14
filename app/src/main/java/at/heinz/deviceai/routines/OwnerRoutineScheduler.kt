package at.heinz.deviceai.routines

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

internal enum class OwnerRoutineSchedule { SCHEDULED, EVENT_ONLY, PAUSED, FAILED }

internal class OwnerRoutineScheduler(context: Context) {
    private val app = context.applicationContext
    private val scheduler get() = app.getSystemService(JobScheduler::class.java)
    fun restore(): Boolean = runCatching {
        val service = ComponentName(app, OwnerRoutineJobService::class.java)
        val old = scheduler.getPendingJob(JOB_ID)
        if (old != null && old.service == service && old.isPeriodic && old.isPersisted &&
            old.intervalMillis == RoutineBook.MIN_PERIOD_MILLIS && !old.isRequireBatteryNotLow &&
            !old.isRequireCharging && !old.isRequireStorageNotLow && !old.isRequireDeviceIdle && old.networkType == JobInfo.NETWORK_TYPE_NONE) true
        else scheduler.schedule(JobInfo.Builder(JOB_ID, service)
            .setPeriodic(RoutineBook.MIN_PERIOD_MILLIS).setPersisted(true)
            .setRequiresBatteryNotLow(false).setRequiresCharging(false).setRequiresStorageNotLow(false)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE).build()) == JobScheduler.RESULT_SUCCESS
    }.getOrDefault(false)
    fun cancel() { scheduler.cancel(JOB_ID) }
    companion object { const val JOB_ID = 620159 }
}

class OwnerRoutineJobService : JobService() {
    @Volatile private var worker: Thread? = null
    @Volatile private var cancellation: AtomicBoolean? = null
    @Volatile private var stopped: AtomicBoolean? = null
    @Volatile private var runtime: OwnerRoutineRuntime? = null

    @Synchronized override fun onStartJob(params: JobParameters): Boolean {
        if (worker?.isAlive == true) return false
        val token = AtomicBoolean()
        val stop = AtomicBoolean()
        cancellation = token
        stopped = stop
        val next = Thread({
            try {
                val active = OwnerRoutineRuntime.get(this).also { runtime = it }
                if (!token.get()) active.runCycle(token)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (failure: Exception) {
                Log.e("ContinuumRoutines", "Owner routine job failed: ${failure.javaClass.simpleName}")
            } finally {
                try { if (!stop.get()) jobFinished(params, false) }
                finally { synchronized(this) {
                    if (cancellation === token) { cancellation = null; stopped = null; worker = null }
                } }
            }
        }, "continuum-owner-routine-job").apply { isDaemon = true }
        worker = next
        next.start()
        return true
    }

    @Synchronized override fun onStopJob(params: JobParameters): Boolean {
        stopped?.set(true)
        cancellation?.let { it.set(true); runtime?.cancelGeneration(it) }
        worker?.interrupt()
        return true
    }

    override fun onDestroy() {
        stopped?.set(true)
        cancellation?.let { it.set(true); runtime?.cancelGeneration(it) }
        worker?.interrupt()
        super.onDestroy()
    }
}
