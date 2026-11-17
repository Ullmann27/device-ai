package at.heinz.deviceai.cockpit

/**
 * In-memory presentation gate. Only verified adapter events should enter.
 * This does NOT authorize or execute tools.
 */
internal class VerifiedHologramWorkGate(private val limit: Int = 4) {
    init { require(limit in 1..8) }

    enum class Kind { MAIL, MESSAGING, CALENDAR, WEB, DEVICE_CHECK, CODE }
    enum class Phase { STARTED, DATA_READY, COMPLETED, FAILED }

    data class Snapshot(
        val workId: String,
        val kind: Kind,
        val phase: Phase,
        val startEvidenceId: String,
        val lastEvidenceId: String,
        val updatedAtMillis: Long
    )

    sealed interface Event {
        val workId: String
        val atMillis: Long

        data class Started(
            override val workId: String,
            override val atMillis: Long,
            val kind: Kind,
            val toolStartEvidenceId: String
        ) : Event

        data class DataReady(
            override val workId: String,
            override val atMillis: Long,
            val dataEvidenceId: String
        ) : Event

        data class Completed(
            override val workId: String,
            override val atMillis: Long,
            val providerReceiptId: String
        ) : Event

        data class Failed(
            override val workId: String,
            override val atMillis: Long,
            val failureReference: String
        ) : Event

        data class Closed(
            override val workId: String,
            override val atMillis: Long
        ) : Event
    }

    private val items = LinkedHashMap<String, Snapshot>()
    private val retired = LinkedHashSet<String>()
    private val retiredLimit = 32

    @Synchronized fun accept(event: Event): Boolean {
        if (event.workId.isBlank() || event.workId.length > 96 ||
            event.atMillis < 0L || event.workId in retired) return false
        val prior = items[event.workId]
        if (prior != null && event.atMillis < prior.updatedAtMillis) return false
        return when (event) {
            is Event.Started -> {
                if (prior != null || items.size >= limit ||
                    event.toolStartEvidenceId.isBlank()) false
                else {
                    items[event.workId] = Snapshot(
                        event.workId, event.kind, Phase.STARTED,
                        event.toolStartEvidenceId, event.toolStartEvidenceId, event.atMillis
                    )
                    true
                }
            }
            is Event.DataReady -> {
                if (prior == null || prior.phase !in setOf(Phase.STARTED, Phase.DATA_READY) ||
                    event.dataEvidenceId.isBlank()) false
                else {
                    items[event.workId] = prior.copy(
                        phase = Phase.DATA_READY,
                        lastEvidenceId = event.dataEvidenceId,
                        updatedAtMillis = event.atMillis
                    )
                    true
                }
            }
            is Event.Completed -> {
                if (prior == null || prior.phase !in setOf(Phase.STARTED, Phase.DATA_READY) ||
                    event.providerReceiptId.isBlank()) false
                else {
                    items[event.workId] = prior.copy(
                        phase = Phase.COMPLETED,
                        lastEvidenceId = event.providerReceiptId,
                        updatedAtMillis = event.atMillis
                    )
                    true
                }
            }
            is Event.Failed -> {
                if (prior == null || prior.phase in setOf(Phase.COMPLETED, Phase.FAILED) ||
                    event.failureReference.isBlank()) false
                else {
                    items[event.workId] = prior.copy(
                        phase = Phase.FAILED,
                        lastEvidenceId = event.failureReference,
                        updatedAtMillis = event.atMillis
                    )
                    true
                }
            }
            is Event.Closed -> {
                if (prior == null) false
                else {
                    items.remove(event.workId)
                    retired.add(event.workId)
                    while (retired.size > retiredLimit) retired.remove(retired.first())
                    true
                }
            }
        }
    }

    @Synchronized fun snapshot(): List<Snapshot> = items.values.toList()
}
