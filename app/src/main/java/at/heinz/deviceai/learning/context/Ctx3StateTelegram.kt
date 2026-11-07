package at.heinz.deviceai.learning.context

import at.heinz.deviceai.learning.ClaimRevision
import at.heinz.deviceai.learning.ContinuumTemporalMemory
import at.heinz.deviceai.learning.TemporalResolution
import java.security.MessageDigest

/**
 * Read-only projection of the canonical temporal memory.
 * It never stores facts, changes claims or replaces the production prompt.
 *
 * The selected source ids only choose which subject/predicate keys are relevant.
 * Values are always resolved against the full [ContinuumTemporalMemory], so an
 * older retrieved source cannot resurrect a value that a later correction replaced.
 */
internal object Ctx3StateTelegram {
    data class Result(
        val text: String,
        val citedSourceIds: List<String>,
        val keyCount: Int,
        val conflictCount: Int,
        val unresolvedCount: Int,
        val rawSelectedBytes: Int,
        val telegramBytes: Int
    ) {
        val sha256: String
            get() = if (text.isEmpty()) "" else MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    fun compile(
        memory: ContinuumTemporalMemory,
        selectedSourceIds: Set<String>,
        nowMillis: Long,
        maxKeys: Int = 16
    ): Result {
        require(nowMillis >= 0)
        require(maxKeys in 1..64)
        if (selectedSourceIds.isEmpty()) {
            return Result("", emptyList(), 0, 0, 0, 0, 0)
        }

        val claims = memory.claims()
        val rawSelectedBytes = claims.asSequence()
            .filter { it.sourceId in selectedSourceIds }
            .distinctBy { it.sourceId }
            .sumOf { it.sourceText.toByteArray(Charsets.UTF_8).size }

        val selectedKeys = claims.asSequence()
            .filter { it.sourceId in selectedSourceIds }
            .map { it.subject to it.predicate }
            .distinct()
            .map { key ->
                key to (claims.asSequence()
                    .filter { it.subject == key.first && it.predicate == key.second }
                    .maxOfOrNull { it.assertedAt } ?: Long.MIN_VALUE)
            }
            .sortedByDescending { it.second }
            .take(maxKeys)
            .map { it.first }
            .toList()

        if (selectedKeys.isEmpty()) {
            return Result("", emptyList(), 0, 0, 0, rawSelectedBytes, 0)
        }

        val lines = ArrayList<String>(selectedKeys.size + 1)
        val cited = linkedSetOf<String>()
        var conflicts = 0
        var unresolved = 0
        lines += "[STATE_TELEGRAM_V1@$nowMillis]"

        selectedKeys.forEach { (subject, predicate) ->
            when (val resolved = memory.valueAt(subject, predicate, nowMillis, nowMillis)) {
                is TemporalResolution.Found -> {
                    resolved.values.forEach { value ->
                        cited += value.sourceIds
                        val correction = claims.asSequence()
                            .filter {
                                it.confirmed &&
                                    it.subject == subject &&
                                    it.predicate == predicate &&
                                    it.revision == ClaimRevision.CORRECTION &&
                                    it.assertedAt <= nowMillis &&
                                    (it.retractedAt == null || it.retractedAt > nowMillis) &&
                                    it.value?.equals(value.value, ignoreCase = true) == true &&
                                    !it.negatedValue.isNullOrBlank()
                            }
                            .maxByOrNull { it.assertedAt }
                        lines += buildString {
                            append("S=").append(field(subject))
                            append("|P=").append(field(predicate))
                            append("|V=").append(field(value.value))
                            append("|FROM=").append(value.validFromMillis ?: "-")
                            append("|TO=").append(value.validToMillis ?: "-")
                            correction?.negatedValue?.let { append("|NOT=").append(field(it)) }
                            append("|SRC=").append(value.sourceIds.distinct().sorted().joinToString(",") { field(it) })
                        }
                    }
                }
                is TemporalResolution.Conflict -> {
                    conflicts++
                    cited += resolved.sourceIds
                    lines += buildString {
                        append("S=").append(field(subject))
                        append("|P=").append(field(predicate))
                        append("|CONFLICT=").append(resolved.values.distinct().sorted().joinToString(",") { field(it) })
                        append("|SRC=").append(resolved.sourceIds.distinct().sorted().joinToString(",") { field(it) })
                    }
                }
                is TemporalResolution.UnconfirmedOnly -> {
                    unresolved++
                    lines += "S=" + field(subject) + "|P=" + field(predicate) + "|UNCONFIRMED=1"
                }
                TemporalResolution.NoEvidence -> {
                    unresolved++
                    lines += "S=" + field(subject) + "|P=" + field(predicate) + "|NO_EVIDENCE=1"
                }
            }
        }

        val text = lines.joinToString("\n")
        return Result(
            text = text,
            citedSourceIds = cited.toList().sorted(),
            keyCount = selectedKeys.size,
            conflictCount = conflicts,
            unresolvedCount = unresolved,
            rawSelectedBytes = rawSelectedBytes,
            telegramBytes = text.toByteArray(Charsets.UTF_8).size
        )
    }

    private fun field(value: String): String = buildString(value.length) {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '|' -> append("\\|")
                '=' -> append("\\=")
                '\n', '\r', '\t' -> append(' ')
                else -> if (!c.isISOControl()) append(c)
            }
        }
    }.trim().take(240)
}
