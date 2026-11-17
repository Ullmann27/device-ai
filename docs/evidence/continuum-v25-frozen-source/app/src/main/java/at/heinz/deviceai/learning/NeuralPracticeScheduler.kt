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
import at.heinz.deviceai.BootstrapMemoryStore
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicitly opted-in learning conductor for the EXISTING local 273-weight classifier.
 *
 * - While UI is visible: short opportunities without charging (activity checks battery/power saver).
 * - Background: short opt-in cycles without charging from 20% battery, not a permanent process.
 * - When three cycles do not improve generalization, stop weight updates until new APPROVED lessons.
 * - All prior weights are retained unless frozen holdouts and user-approved training improve.
 * No network, always-listening microphone, device tool call or unreviewed self-labeling.
 */
class NeuralPracticeScheduler(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val jobs get() = app.getSystemService(JobScheduler::class.java)

    val enabled: Boolean get() = prefs.getBoolean("enabled", false)
    val lastStatus: String get() = prefs.getString("status", "Noch kein Lernzyklus.")
        ?: "Noch kein Lernzyklus."
    val attempts: Int get() = prefs.getInt("attempts", 0)
    val improvements: Int get() = prefs.getInt("improvements", 0)
    val waitingForExperience: Boolean get() =
        enabled && prefs.getInt("stagnant", 0) >= 3

    fun enable(): Boolean {
        val lab = runCatching {
            BootstrapMemoryStore(app).neuralSnapshot()?.let(NeuralDialogue::restore)
        }.getOrNull()
        if (lab == null || lab.lessonCount() == 0) return false
        if (!schedule()) return false
        val success = prefs.edit()
            .putBoolean("enabled", true)
            .putInt("stagnant", 0)
            .putString("fingerprint", lab.lessonFingerprint())
            .putString("status", "Lernbereit: kurze Übungszyklen auch ohne Ladekabel ab 20 % Akku.")
            .commit()
        if (!success) jobs.cancel(JOB_ID)
        return success
    }

    fun restoreIfEnabled() {
        if (!enabled) return
        val existing = jobs.getPendingJob(JOB_ID)
        // Do not replace an identical running job. Migrate legacy charging-only jobs.
        if (existing == null || existing.isRequireCharging ||
            existing.isRequireBatteryNotLow || !existing.isPersisted ||
            existing.intervalMillis != PERIOD_MS ||
            existing.networkType != JobInfo.NETWORK_TYPE_NONE ||
            existing.service.className != NeuralPracticeJobService::class.java.name) {
            schedule()
        }
    }

    fun recordBatteryDeferral() {
        prefs.edit().putString("status",
            "Akkustand unter 20 % oder nicht messbar: nächster Übungszyklus abwarten.").apply()
    }

    fun disable() {
        prefs.edit().putBoolean("enabled", false)
            .putString("status", "Automatisches Üben ausgeschaltet. Gewichte bleiben erhalten.").commit()
        jobs.cancel(JOB_ID)
    }

    /**
     * One bounded cycle, also usable from the visible app's background executor.
     * Caller must not invoke on main thread. Serialized across JobService and foreground.
     */
    fun practiceOnce(cancelled: () -> Boolean = { false },
        progress: (NeuralLearningProgress) -> Unit = {}
    ): String {
        if (!enabled || cancelled()) return "Lernzyklus nicht aktiv."
        if (!RUNNING.compareAndSet(false, true)) return "Bereits ein Lernzyklus aktiv."
        try {
            val memory = BootstrapMemoryStore(app)
            val original = memory.neuralSnapshot()
                ?: return record("Kein trainierter neuronaler Lernstand vorhanden.", false, "")
            val model = NeuralDialogue.restore(original)
            if (model.lessonCount() == 0)
                return record("Keine bestätigten Lernbeispiele vorhanden.", false, model.lessonFingerprint())
            val fingerprint = model.lessonFingerprint()
            if (!canPractice(fingerprint)) {
                val waiting = "Lernbereit: Ich warte auf ein neues bestätigtes Sprachbeispiel. " +
                    "Die bisherigen Wiederholungen haben keinen weiteren Fortschritt gebracht."
                prefs.edit().putString("status", waiting).apply()
                return waiting
            }
            val result = model.practice(steps = 200, cancelled = cancelled, onProgress = progress)
            if (cancelled() || !enabled) return "Übungsdurchgang abgebrochen; nichts gespeichert."
            if (!result.accepted)
                return record("200er-Zyklus verworfen: " + result.reason +
                    "; neuer Wortschatz " + result.unfamiliarAfter.correct + "/12.", false, fingerprint)
            val report = "Lernzyklus: " + result.completedSteps + " Updates; Verlust " +
                "%.5f → %.5f".format(Locale.ROOT, result.trainingLossBefore,result.trainingLossAfter) +
                "; Härtetest " + result.unfamiliarBefore.correct + "/12 → " +
                result.unfamiliarAfter.correct + "/12."
            val saved = !cancelled() && enabled &&
                memory.compareAndSaveNeuralSnapshot(original,model.save(),report)
            return record(if(saved) report else
                "Neue Gewichte nicht übernommen: Speicherstand wurde verändert oder Speichern fehlgeschlagen.",
                saved, fingerprint)
        } catch (error: Exception) {
            if (cancelled()) return "Übungsdurchgang abgebrochen."
            return record("Lernzyklus fehlgeschlagen: " + error.javaClass.simpleName,false,"")
        } finally { RUNNING.set(false) }
    }

    private fun canPractice(fingerprint: String): Boolean {
        if (prefs.getString("fingerprint", "") != fingerprint) {
            // New approved examples resume the already consented, periodic learning cycle.
            prefs.edit().putInt("stagnant", 0).putString("fingerprint", fingerprint).commit()
        }
        return prefs.getInt("stagnant", 0) < 3
    }

    private fun record(text: String, improved: Boolean, fingerprint: String): String {
        val old = prefs.getInt("stagnant", 0)
        val misses = if (improved) 0 else (old + 1).coerceAtMost(3)
        val message = if (misses >= 3)
            text + " Nächste Gewichtsübungen erst nach neuen bestätigten Beispielen."
        else text
        prefs.edit()
            .putInt("stagnant", misses)
            .putString("fingerprint", fingerprint)
            .putInt("attempts", (attempts + 1).coerceAtMost(1000000))
            .putInt("improvements", (improvements + if(improved) 1 else 0).coerceAtMost(1000000))
            .putString("status", message.take(500))
            .putLong("last_run", System.currentTimeMillis())
            .commit()
        return message
    }

    private fun schedule(): Boolean {
        val info = JobInfo.Builder(JOB_ID, ComponentName(app, NeuralPracticeJobService::class.java))
            .setRequiresCharging(false)
            .setRequiresBatteryNotLow(false)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .setPeriodic(PERIOD_MS)
            .setPersisted(true).build()
        return jobs.schedule(info) == JobScheduler.RESULT_SUCCESS
    }

    companion object {
        private val RUNNING = AtomicBoolean(false)
        private const val PREFS = "device_ai_neural_practice_v1"
        const val JOB_ID = 620132
        const val PERIOD_MS = 15 * 60 * 1000L
    }
}

/** One short scheduled learning opportunity, limited by Android's background policy. */
class NeuralPracticeJobService : JobService() {
    private val cancelled = AtomicBoolean(false)
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val scheduler = NeuralPracticeScheduler(this)
        if (!scheduler.enabled) return false
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1) ?: -1
        if (level < 0 || scale <= 0 ||
            level.toLong()*100L/scale < ContinuumOfflineLearningBudget.MIN_BATTERY_PERCENT) {
            scheduler.recordBatteryDeferral()
            return false
        }
        cancelled.set(false)
        worker = Thread({
            try { scheduler.practiceOnce(cancelled::get) }
            finally { if (!cancelled.get()) jobFinished(params, false) }
        }, "device-ai-neural-practice").apply { isDaemon = true; start() }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        cancelled.set(true)
        worker?.interrupt()
        return false
    }
}
