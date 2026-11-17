package at.heinz.deviceai.learning

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Explicitly enabled research-only dialogue practice inside the existing daily offline job.
 * Synthetic dialogue data never become owner memories and CRE1 stays shadow-only.
 */
object ContinuumAutoDialoguePolicy {
    private const val PREFS = "continuum-auto-dialogue-v1"
    private const val ENABLED = "owner_enabled"
    const val MIN_REMAINING_MILLIS = 3_500L

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, enabled).commit()

    fun shouldStart(enabled: Boolean, remainingMillis: Long, attemptedStages: Int): Boolean =
        enabled && remainingMillis >= MIN_REMAINING_MILLIS &&
            attemptedStages in 0 until ContinuumAutoDialogueCurriculum.MAX_STAGES

    fun lastResult(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("last_result", "Noch kein autonomer Dialogversuch.").orEmpty()

    fun record(context: Context, result: String): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("last_result", result.take(180))
            .putLong("last_run", System.currentTimeMillis()).commit()
}

/**
 * Exactly one synthetic dialogue stage per invocation; the stage is reserved durably
 * BEFORE training to prevent an interrupted Android process from replaying its gradient
 * writes. An interrupted attempt is counted as attempted, never as learned.
 */
class ContinuumAutoDialogueLab(private val filesDir: File) {
    private val stageFile = File(filesDir, "learning-core-v2/auto-dialogue-v1-next.txt")

    data class Round(
        val stage: Int,
        val accepted: Boolean,
        val steps: Int,
        val stoppedEarly: Boolean,
        val trainBefore: Double,
        val trainAfter: Double,
        val auditBefore: Double,
        val auditAfter: Double,
        val modelHash: String?
    ) {
        fun summary(): String =
            "Dialogstufe " + stage + "/" + ContinuumAutoDialogueCurriculum.MAX_STAGES +
                ": " + (if (stoppedEarly) "UNTERBROCHEN" else if (accepted) "AKZEPTIERT" else "VERWORFEN") +
                " · Schritte " + steps + " · CRE1 nur Forschung"
    }

    fun attemptedStages(): Int = synchronized(LOCK) { readStage() }

    fun next(cancelled: () -> Boolean = { false }): Round? = synchronized(LOCK) {
        if (cancelled()) return@synchronized null
        val index = readStage()
        if (index >= ContinuumAutoDialogueCurriculum.MAX_STAGES) return@synchronized null
        val batch = ContinuumAutoDialogueCurriculum.batch(index)
        if (cancelled()) return@synchronized null
        val core = ContinuumLearningCore(filesDir)
        val swbHash = core.modelHash()
        // Durable reservation: after a crash, move to a new distinct group of cases.
        reserveStage(index + 1)
        val report = core.trainExperimentalCre1(
            batch.training, batch.audit, steps = 32, cancelled = cancelled
        )
        check(core.modelHash() == swbHash) {
            "Synthetic dialogue research changed SWB1 unexpectedly"
        }
        Round(index + 1, report.accepted, report.steps, report.stoppedEarly,
            report.trainingBeforeLoss, report.trainingAfterLoss,
            report.beforeLoss, report.afterLoss, report.modelSha256)
    }

    private fun readStage(): Int {
        require(!Files.isSymbolicLink(stageFile.toPath())) { "Stage symlink forbidden" }
        // exists() follows links: a dangling link must not reset an already consumed curriculum.
        if (!stageFile.exists()) return 0
        require(stageFile.isFile && stageFile.length() in 1..3) { "Invalid stage file" }
        return stageFile.readText(Charsets.UTF_8).trim().toIntOrNull()?.also {
            require(it in 0..ContinuumAutoDialogueCurriculum.MAX_STAGES)
        } ?: error("Invalid synthetic dialogue stage. No reset allowed.")
    }

    private fun reserveStage(next: Int) {
        require(next in 1..ContinuumAutoDialogueCurriculum.MAX_STAGES)
        val dir = stageFile.parentFile ?: error("No stage directory")
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create stage directory" }
        val tmp = File.createTempFile("auto-dialogue-", ".tmp", dir)
        try {
            FileOutputStream(tmp).use {
                it.write(next.toString().toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            Files.move(tmp.toPath(), stageFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            tmp.delete()
        }
    }

    companion object { private val LOCK = Any() }
}
