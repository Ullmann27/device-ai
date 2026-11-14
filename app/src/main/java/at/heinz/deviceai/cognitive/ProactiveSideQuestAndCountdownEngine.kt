package at.heinz.deviceai.cognitive

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded mission timer plus provenance-gated post-mission initiative.
 *
 * This class never reads calendars, memory stores or tools by itself. Callers must provide
 * already-authorized [MemoryRecord] values. It cannot mutate memory or execute an action.
 */
class ProactiveSideQuestAndCountdownEngine(
    private val zoneId: ZoneId = ZoneId.of("Europe/Vienna"),
    private val ownerAddress: String = "Heinz"
) {
    data class MissionTimerSnapshot(
        val turnId: String,
        val elapsedMillis: Long,
        val remainingBudgetMillis: Long,
        val formattedSecondsGerman: String,
        val finishedUnderBudget: Boolean
    )

    data class ProactiveSideQuest(
        val memoryId: String,
        val title: String,
        val spokenInitiativeLine: String,
        val suggestedActionLabel: String
    )

    private val missionStartNanos = ConcurrentHashMap<String, Long>()
    private val missionBudgetMs = ConcurrentHashMap<String, Long>()
    private val announcedMemoryIds = ConcurrentHashMap<String, Long>()

    @Volatile
    private var lastProactiveTriggerAtMs: Long = -1L

    fun startMissionCountdown(
        turnId: String,
        targetBudgetMillis: Long = DEFAULT_BUDGET_MS,
        nowNanos: Long = System.nanoTime()
    ) {
        require(turnId.isNotBlank() && turnId.length <= 192)
        require(nowNanos >= 0L)
        if (missionStartNanos.size >= MAX_ACTIVE_MISSIONS && !missionStartNanos.containsKey(turnId)) {
            val oldest = missionStartNanos.minByOrNull { it.value }?.key
            if (oldest != null) {
                missionStartNanos.remove(oldest)
                missionBudgetMs.remove(oldest)
            }
        }
        missionStartNanos[turnId] = nowNanos
        missionBudgetMs[turnId] = targetBudgetMillis.coerceIn(MIN_BUDGET_MS, MAX_BUDGET_MS)
    }

    fun sampleMissionTimer(
        turnId: String,
        nowNanos: Long = System.nanoTime()
    ): MissionTimerSnapshot? {
        val start = missionStartNanos[turnId] ?: return null
        val budget = missionBudgetMs[turnId] ?: DEFAULT_BUDGET_MS
        val elapsedMs = ((nowNanos - start).coerceAtLeast(0L) / 1_000_000L)
        val remainingMs = (budget - elapsedMs).coerceAtLeast(0L)
        return MissionTimerSnapshot(
            turnId = turnId,
            elapsedMillis = elapsedMs,
            remainingBudgetMillis = remainingMs,
            formattedSecondsGerman = String.format(Locale.GERMANY, "%.1f", elapsedMs / 1000.0),
            finishedUnderBudget = elapsedMs <= budget
        )
    }

    fun stopMissionCountdown(
        turnId: String,
        nowNanos: Long = System.nanoTime()
    ): MissionTimerSnapshot? {
        val snapshot = sampleMissionTimer(turnId, nowNanos)
        missionStartNanos.remove(turnId)
        missionBudgetMs.remove(turnId)
        return snapshot
    }

    /**
     * Returns one timely initiative at most. Only already-authorized records supplied by the caller
     * are considered. Relative "today/tonight" memories must themselves be from the current local day,
     * so an old "heute Abend" sentence cannot re-fire days later.
     */
    @Synchronized
    fun findTimelySideQuestOrNull(
        records: Collection<MemoryRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): ProactiveSideQuest? {
        require(nowMillis >= 0L)
        if (lastProactiveTriggerAtMs >= 0L &&
            nowMillis - lastProactiveTriggerAtMs < MIN_COOLDOWN_MS
        ) return null

        pruneAnnouncementHistory(nowMillis)

        val hits = HybridMemoryRetriever.retrieve(
            query = EVENT_QUERY,
            records = records,
            limit = 6,
            nowMillis = nowMillis,
            allowedPrivacy = ALLOWED_PRIVACY
        )

        val candidate = hits.firstOrNull { scored ->
            val memory = scored.memory
            val trusted = memory.provenance == ProvenanceType.USER_PROVIDED ||
                (memory.validationState == ValidationState.VERIFIED &&
                    memory.validationEvidenceReferences.isNotEmpty())
            val usableState = memory.validationState != ValidationState.CONTRADICTED &&
                memory.validationState != ValidationState.STALE
            val notRecentlyAnnounced =
                (announcedMemoryIds[memory.id] ?: Long.MIN_VALUE) < nowMillis - DAY_MS
            trusted &&
                usableState &&
                notRecentlyAnnounced &&
                scored.score.total >= MIN_RETRIEVAL_SCORE &&
                isExplicitlyToday(memory, nowMillis)
        }?.memory ?: return null

        lastProactiveTriggerAtMs = nowMillis
        announcedMemoryIds[candidate.id] = nowMillis

        val snippet = candidate.content
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_SNIPPET_CHARS)

        return ProactiveSideQuest(
            memoryId = candidate.id,
            title = "Kalender- & Gedächtnis-Impuls",
            spokenInitiativeLine =
                "Ach $ownerAddress, ich habe in deinen bestätigten Notizen gesehen: $snippet. Soll ich dafür etwas vorbereiten?",
            suggestedActionLabel = "Vorbereitung starten"
        )
    }

    private fun isExplicitlyToday(memory: MemoryRecord, nowMillis: Long): Boolean {
        val content = memory.content
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val recordDay = Instant.ofEpochMilli(memory.updatedAt).atZone(zoneId).toLocalDate()
        val today = now.toLocalDate()

        if (RELATIVE_TODAY.containsMatchIn(content)) {
            return recordDay == today && EVENT_KEYWORDS.containsMatchIn(content)
        }

        val explicitDate = NUMERIC_DATE.findAll(content).any { match ->
            val next = content.getOrNull(match.range.last + 1)
            if (next != null && (next.isLetterOrDigit() || next in "/-" ||
                    next == '.' && content.getOrNull(match.range.last + 2)?.isDigit() == true)) return@any false
            val yearText = match.groupValues[3]
            val timeTail = if (yearText.length in 1..2 && yearText.toInt() in 0..23)
                TIME_TAIL.matchAt(content, match.range.last + 1) else null
            val isTime = timeTail != null && (timeTail.groupValues[1].isEmpty() ||
                timeTail.groupValues[1].toInt() in 0..59)
            if (yearText.isNotEmpty() && !isTime && yearText.length != 4) return@any false
            val year = if (isTime || yearText.isEmpty()) today.year else yearText.toInt()
            runCatching {
                LocalDate.of(year, match.groupValues[2].toInt(), match.groupValues[1].toInt())
            }.getOrNull() == today
        }
        return explicitDate && EVENT_KEYWORDS.containsMatchIn(content)
    }

    private fun pruneAnnouncementHistory(nowMillis: Long) {
        if (announcedMemoryIds.size <= MAX_ANNOUNCEMENT_HISTORY) return
        val cutoff = nowMillis - ANNOUNCEMENT_RETENTION_MS
        announcedMemoryIds.entries.removeIf { it.value < cutoff }
        if (announcedMemoryIds.size <= MAX_ANNOUNCEMENT_HISTORY) return
        announcedMemoryIds.entries
            .sortedBy { it.value }
            .take(announcedMemoryIds.size - MAX_ANNOUNCEMENT_HISTORY)
            .forEach { announcedMemoryIds.remove(it.key, it.value) }
    }

    companion object {
        const val MIN_COOLDOWN_MS = 30L * 60L * 1000L
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val DEFAULT_BUDGET_MS = 50_000L
        private const val MIN_BUDGET_MS = 1_000L
        private const val MAX_BUDGET_MS = 300_000L
        private const val MAX_ACTIVE_MISSIONS = 64
        private const val MAX_ANNOUNCEMENT_HISTORY = 256
        private const val ANNOUNCEMENT_RETENTION_MS = 7L * DAY_MS
        private const val MAX_SNIPPET_CHARS = 140
        private const val MIN_RETRIEVAL_SCORE = 0.25

        private val ALLOWED_PRIVACY = setOf(
            PrivacyClass.PUBLIC,
            PrivacyClass.PRIVATE,
            PrivacyClass.DEVICE_LOCAL_ONLY
        )

        private const val EVENT_QUERY =
            "heute abend heute termin abendessen geburtstag treffen abholen reservierung geschenk"

        private val RELATIVE_TODAY =
            Regex("""(?iu)\b(?:heute(?:\s+abend)?|today|tonight)\b""")

        private val NUMERIC_DATE =
            Regex("""(?<![\p{L}\p{N}./-])([0-9]{1,2})\.([0-9]{1,2})\.(?:[ \t]*([0-9]+))?""")

        private val TIME_TAIL =
            Regex("""(?iu)(?::([0-9]{2})(?:[ \t]+Uhr)?|[ \t]+Uhr)(?![\p{L}\p{N}:])""")

        private val EVENT_KEYWORDS =
            Regex("""(?iu)\b(?:abendessen|geburtstag|termin|treffen|abholen|reservierung|geschenk|arzt|schule|feier|essen)\b""")
    }
}
