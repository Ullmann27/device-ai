package at.heinz.deviceai.learning

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import at.heinz.deviceai.BootstrapMemoryStore
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in scheduled practice of ONLY user-confirmed labels; charging-only, no internet. */
class NeuralPracticeScheduler(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val jobs get() = app.getSystemService(JobScheduler::class.java)

    val enabled: Boolean get() = prefs.getBoolean("enabled", false)
    val lastStatus: String get() = prefs.getString("status", "Noch kein automatisches Üben.")
        ?: "Noch keine Messung."

    fun enable(): Boolean {
        val lab = runCatching { BootstrapMemoryStore(app).neuralSnapshot()?.let(NeuralDialogue::restore) }.getOrNull()
        if (lab == null || lab.lessonCount() == 0) return false
        if (!schedule()) return false
        val saved = prefs.edit().putBoolean("enabled", true).putInt("stagnant", 0)
            .putString("status", "Aktiv: Wiederholung beim Laden, frühestens alle 15 Minuten.").commit()
        if (!saved) jobs.cancel(JOB_ID)
        return saved
    }

    fun restoreIfEnabled() {
        if (enabled && jobs.getPendingJob(JOB_ID) == null) schedule()
    }

    fun disable() {
        prefs.edit().putBoolean("enabled", false)
            .putString("status", "Automatisches Üben ausgeschaltet.").commit()
        jobs.cancel(JOB_ID)
    }

    internal fun record(message: String, accepted: Boolean) {
        if (!enabled) return
        val stagnant = if (accepted) 0 else (prefs.getInt("stagnant", 0) + 1).coerceAtMost(3)
        prefs.edit().putInt("stagnant", stagnant)
            .putString("status", message.take(450)).putLong("last_run", System.currentTimeMillis()).commit()
        if (stagnant >= 3) {
            prefs.edit().putBoolean("enabled", false)
                .putString("status", "Nach drei Übungszyklen ohne Fortschritt pausiert. " +
                    "Neue bestätigte Beispiele nötig. Letztes Ergebnis: " + message.take(170)).commit()
            jobs.cancel(JOB_ID)
        }
    }

    private fun schedule(): Boolean {
        val info = JobInfo.Builder(JOB_ID, ComponentName(app, NeuralPracticeJobService::class.java))
            .setRequiresCharging(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .setPeriodic(PERIOD_MS)
            .setPersisted(true)
            .build()
        return jobs.schedule(info) == JobScheduler.RESULT_SUCCESS
    }

    companion object {
        private const val PREFS = "device_ai_neural_practice_v1"
        const val JOB_ID = 620132
        const val PERIOD_MS = 15 * 60 * 1000L
    }
}

/** JobScheduler worker: bounded local SGD, frozen regression checks, no tools or microphone. */
class NeuralPracticeJobService : JobService() {
    private val cancelled = AtomicBoolean(false)
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val scheduler = NeuralPracticeScheduler(this)
        if (!scheduler.enabled) return false
        cancelled.set(false)
        worker = Thread({
            try {
                val memory = BootstrapMemoryStore(applicationContext)
                val original = memory.neuralSnapshot()
                if (original == null) {
                    scheduler.record("Kein trainiertes neuronales Experiment vorhanden.", false)
                    return@Thread
                }
                val model = NeuralDialogue.restore(original)
                if (model.lessonCount() == 0) {
                    scheduler.record("Keine bestätigten Lernbeispiele vorhanden.", false)
                    return@Thread
                }
                val result = model.practice(steps = 200, cancelled = cancelled::get)
                if (cancelled.get() || !scheduler.enabled) return@Thread
                if (!result.accepted) {
                    scheduler.record("Lernzyklus " + result.completedSteps + "/200, verworfen: " +
                        result.reason + ". Härtetest: " + result.unfamiliarAfter.correct + "/12.", false)
                    return@Thread
                }
                val report = "Lokaler Lernzyklus: " + result.completedSteps +
                    " Gewichtsaktualisierungen; Trainingsfehler " +
                    "%.5f → %.5f".format(Locale.ROOT, result.trainingLossBefore, result.trainingLossAfter) +
                    "; Härtetest " + result.unfamiliarBefore.correct + "/12 → " +
                    result.unfamiliarAfter.correct + "/12."
                val saved = scheduler.enabled && !cancelled.get() &&
                    memory.compareAndSaveNeuralSnapshot(original, model.save(), report)
                scheduler.record(if (saved) report else
                    "Nicht gespeichert: Ein neuerer Lernstand oder Speicherfehler liegt vor.", saved)
            } catch (error: Exception) {
                if (!cancelled.get()) scheduler.record(
                    "Übung fehlgeschlagen: " + error.javaClass.simpleName, false
                )
            } finally {
                if (!cancelled.get()) jobFinished(params, false)
            }
        }, "device-ai-neural-practice").apply { isDaemon = true; start() }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        cancelled.set(true)
        worker?.interrupt()
        return false
    }
}
