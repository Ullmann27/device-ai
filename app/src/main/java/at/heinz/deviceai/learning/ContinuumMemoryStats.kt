package at.heinz.deviceai.learning

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Read-only memory figures for the cockpit (Jarvis Kern). Every value is computed from the real LEX2/LEX3 ledger;
 * an empty or absent ledger yields 0 / null, never a default or invented number.
 * - totalEntries: all ledger records (any type/status)
 * - newToday: records created since local midnight in [Clock.getZone] (default Europe/Vienna)
 * - lastActivity: newest updatedAt, or null when empty
 * - confirmed / unconfirmed / hypothetical / revoked: owner statements (USER_FACT) by status;
 *   hypothetical = held with modality HYPOTHETICAL, unconfirmed = held with any other non-confirmable modality.
 */
data class ContinuumMemoryStats(
    val totalEntries:Int,
    val newToday:Int,
    val lastActivity:Instant?,
    val confirmed:Int,
    val unconfirmed:Int,
    val hypothetical:Int,
    val revoked:Int
)

/** Opens the ledger read-only (no write path); [appDirectory] is the same base directory as [ContinuumLearningCore]. */
class ContinuumMemoryStatsReader(appDirectory:File, private val clock:Clock = Clock.system(ZoneId.of("Europe/Vienna"))) {
    private val store=FileLearningExperienceStore(File(appDirectory,"learning-core-v2"))

    fun read():ContinuumMemoryStats {
        val items=store.all()
        val midnight=LocalDate.now(clock).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val owner=items.filter { it.type==LearningExperienceType.USER_FACT && it.trust==LearningTrustClass.OWNER_CONFIRMED }
        val held=owner.filter { it.status==LearningStatus.OBSERVED }
        val hypo=held.count { it.annotation==OwnerStatementModality.HELD_PREFIX+StatementModality.HYPOTHETICAL.name }
        return ContinuumMemoryStats(
            totalEntries=items.size,
            newToday=items.count { it.createdAt>=midnight },
            lastActivity=items.maxOfOrNull { it.updatedAt }?.let(Instant::ofEpochMilli),
            confirmed=owner.count { it.status==LearningStatus.CONFIRMED },
            unconfirmed=held.size-hypo,
            hypothetical=hypo,
            revoked=owner.count { it.status==LearningStatus.REVOKED }
        )
    }
}
