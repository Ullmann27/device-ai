package at.heinz.deviceai.localmodel

import android.content.Context
import org.json.JSONObject
import java.time.YearMonth
import java.util.Locale

/** Token counts of one API attempt as the response's `usage` reports them. */
data class ClaudeTokenUsage(
    /** Uncached input tokens (`input_tokens` is the uncached remainder only). */
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long = 0L,
    val cacheWriteTokens: Long = 0L
) {
    init { require(inputTokens >= 0 && outputTokens >= 0 && cacheReadTokens >= 0 && cacheWriteTokens >= 0) }
    val promptTokens: Long get() = inputTokens + cacheReadTokens + cacheWriteTokens
}

/**
 * The one place for prices. USD per million tokens, kept as nano-USD per token so every sum stays an exact Long.
 * Stand 03.11.2026, kann sich ändern: the figures are the published list prices of that day and only feed the
 * owner's local estimate; the binding amount is always the one on the Anthropic account.
 */
object ClaudePricing {
    const val AS_OF = "Stand 03.11.2026, kann sich ändern"

    data class Rate(val inputNanoUsd: Long, val outputNanoUsd: Long, val cacheReadNanoUsd: Long,
                    val cacheWriteNanoUsd: Long)

    /** Claude Haiku 5.5 switches to its second rate card when the prompt is longer than this. */
    private const val HAIKU_LONG_PROMPT_TOKENS = 100_000L

    private fun usd(input: Double, output: Double, cacheRead: Double, cacheWrite5m: Double) = Rate(
        Math.round(input * 1_000), Math.round(output * 1_000),
        Math.round(cacheRead * 1_000), Math.round(cacheWrite5m * 1_000))

    private val RATES: Map<String, Rate> = mapOf(
        // The four models the owner can select.
        "claude-opus-5-5" to usd(4.0, 20.0, 0.20, 5.0),
        "claude-sonnet-5-5" to usd(2.0, 10.0, 0.10, 2.50),
        "claude-haiku-5-5" to usd(0.10, 0.50, 0.01, 0.125),
        "claude-fable-5-2" to usd(8.0, 40.0, 0.20, 10.0),
        // Models that can serve a request after a server-side refusal fallback.
        "claude-opus-5" to usd(5.0, 25.0, 0.50, 6.25),
        "claude-opus-4-8" to usd(5.0, 25.0, 0.50, 6.25),
        "claude-sonnet-5" to usd(2.0, 10.0, 0.20, 2.50)
    )
    private val HAIKU_LONG_PROMPT = usd(0.50, 2.50, 0.05, 0.625)

    fun knows(modelId: String): Boolean = modelId in RATES

    /** [requestedModelId] prices a served model this table does not know (the estimate then says so). */
    fun rate(modelId: String, requestedModelId: String, promptTokens: Long): Rate {
        val id = if (modelId in RATES) modelId else requestedModelId
        if (id == "claude-haiku-5-5" && promptTokens > HAIKU_LONG_PROMPT_TOKENS) return HAIKU_LONG_PROMPT
        return RATES[id] ?: RATES.getValue(ClaudeModel.DEFAULT.id)
    }

    fun costNanoUsd(modelId: String, requestedModelId: String, usage: ClaudeTokenUsage): Long {
        val rate = rate(modelId, requestedModelId, usage.promptTokens)
        return usage.inputTokens * rate.inputNanoUsd + usage.outputTokens * rate.outputNanoUsd +
            usage.cacheReadTokens * rate.cacheReadNanoUsd + usage.cacheWriteTokens * rate.cacheWriteNanoUsd
    }

    /** German table for the settings screen. */
    fun describe(): String = ClaudeModel.entries.joinToString("\n") { model ->
        val rate = RATES.getValue(model.id)
        "${model.label}: ${usdPerMillion(rate.inputNanoUsd)} / ${usdPerMillion(rate.outputNanoUsd)} USD " +
            "je Million Token (Eingabe / Ausgabe)"
    } + "\n" + AS_OF + "."

    private fun usdPerMillion(nanoUsdPerToken: Long): String =
        String.format(Locale.GERMANY, "%.2f", nanoUsdPerToken / 1_000.0)
}

/** Where the monthly counter is kept. */
interface ClaudeLedgerStorage {
    fun read(): String?
    fun write(value: String): Boolean
}

class InMemoryClaudeLedgerStorage : ClaudeLedgerStorage {
    @Volatile private var value: String? = null
    override fun read(): String? = value
    override fun write(value: String): Boolean { this.value = value; return true }
}

class PreferencesClaudeLedgerStorage(context: Context) : ClaudeLedgerStorage {
    private val preferences = context.applicationContext.getSharedPreferences("continuum.claude.usage", Context.MODE_PRIVATE)
    override fun read(): String? = preferences.getString("ledger", null)
    override fun write(value: String): Boolean = preferences.edit().putString("ledger", value).commit()
}

/**
 * Local monthly counter: tokens in and out per model plus an estimated cost from [ClaudePricing]. It counts what
 * this app sent with the owner's key on this device; it is not the account's invoice and knows nothing about other
 * devices or programs that use the same key. The owner's monthly limit blocks further requests once the estimate
 * reaches it. A request that was cut off (STOP, lost connection) has no final usage; its part is estimated
 * conservatively and counted as such.
 */
class ClaudeUsageLedger(
    private val storage: ClaudeLedgerStorage,
    private val currentMonth: () -> String = { YearMonth.now().toString() }
) {
    data class ModelTotals(val requests: Long = 0, val inputTokens: Long = 0, val outputTokens: Long = 0,
                           val cacheReadTokens: Long = 0, val cacheWriteTokens: Long = 0, val costNanoUsd: Long = 0)

    data class Snapshot(val month: String, val models: Map<String, ModelTotals>, val estimatedRequests: Long,
                        val unpricedRequests: Long) {
        val costNanoUsd: Long get() = models.values.sumOf { it.costNanoUsd }
        val requests: Long get() = models.values.sumOf { it.requests }
        /** Whole cents, rounded up: the limit must never be undercut by rounding. */
        val costCents: Long get() = (costNanoUsd + NANO_USD_PER_CENT - 1) / NANO_USD_PER_CENT
    }

    @Synchronized fun snapshot(): Snapshot {
        val month = currentMonth()
        val stored = runCatching { storage.read()?.let(::JSONObject) }.getOrNull()
        // A new month starts at zero; the old month is not carried over.
        if (stored == null || stored.optString("month") != month) return Snapshot(month, emptyMap(), 0, 0)
        val models = stored.optJSONObject("models")
        val totals = buildMap {
            models?.keys()?.forEach { id ->
                val entry = models.optJSONObject(id) ?: return@forEach
                put(id, ModelTotals(entry.optLong("requests"), entry.optLong("in"), entry.optLong("out"),
                    entry.optLong("cacheRead"), entry.optLong("cacheWrite"), entry.optLong("nanoUsd")))
            }
        }
        return Snapshot(month, totals, stored.optLong("estimated"), stored.optLong("unpriced"))
    }

    /** True when the local estimate has reached the owner's monthly limit. */
    fun limitReached(limitCents: Int): Boolean = snapshot().costCents >= limitCents

    /**
     * Adds one API attempt. [servedModelId] is the model that answered (after a refusal fallback it differs from
     * [requestedModelId]). [estimated] marks a cut-off request without a final usage report.
     */
    @Synchronized fun record(servedModelId: String, requestedModelId: String, usage: ClaudeTokenUsage,
                             estimated: Boolean = false, countsAsRequest: Boolean = true): Boolean {
        val before = snapshot()
        val previous = before.models[servedModelId] ?: ModelTotals()
        val updated = previous.copy(
            requests = previous.requests + if (countsAsRequest) 1 else 0,
            inputTokens = previous.inputTokens + usage.inputTokens,
            outputTokens = previous.outputTokens + usage.outputTokens,
            cacheReadTokens = previous.cacheReadTokens + usage.cacheReadTokens,
            cacheWriteTokens = previous.cacheWriteTokens + usage.cacheWriteTokens,
            costNanoUsd = previous.costNanoUsd + ClaudePricing.costNanoUsd(servedModelId, requestedModelId, usage))
        val models = JSONObject()
        (before.models + (servedModelId to updated)).toSortedMap().forEach { (id, totals) ->
            models.put(id, JSONObject().put("requests", totals.requests).put("in", totals.inputTokens)
                .put("out", totals.outputTokens).put("cacheRead", totals.cacheReadTokens)
                .put("cacheWrite", totals.cacheWriteTokens).put("nanoUsd", totals.costNanoUsd))
        }
        return storage.write(JSONObject().put("month", before.month).put("models", models)
            .put("estimated", before.estimatedRequests + if (estimated) 1 else 0)
            .put("unpriced", before.unpricedRequests + if (ClaudePricing.knows(servedModelId)) 0 else 1)
            .toString())
    }

    /** German summary for the settings screen. */
    fun describe(limitCents: Int): String {
        val now = snapshot()
        val lines = mutableListOf("Monat ${now.month}: geschätzt ${usd(now.costNanoUsd)} USD von " +
            "${usd(limitCents * NANO_USD_PER_CENT)} USD Monatslimit · ${now.requests} Anfragen")
        now.models.toSortedMap().forEach { (id, totals) ->
            lines += "$id: Eingabe ${totals.inputTokens + totals.cacheReadTokens + totals.cacheWriteTokens} Token " +
                "(davon ${totals.cacheReadTokens} aus dem Cache) · Ausgabe ${totals.outputTokens} Token · " +
                "${usd(totals.costNanoUsd)} USD"
        }
        if (now.requests == 0L) lines += "Noch keine Anfrage in diesem Monat."
        if (now.estimatedRequests > 0) lines += "${now.estimatedRequests} abgebrochene Anfragen ohne Endabrechnung " +
            "sind vorsichtig geschätzt."
        if (now.unpricedRequests > 0) lines += "${now.unpricedRequests} Anfragen beantwortete ein Modell ohne " +
            "Eintrag in der Preistabelle; sie sind zum Preis des gewählten Modells gerechnet."
        lines += "Schätzung dieser App auf diesem Gerät, keine Rechnung. Verbindlich ist dein Anthropic-Konto. " +
            ClaudePricing.AS_OF + "."
        return lines.joinToString("\n")
    }

    companion object {
        const val NANO_USD_PER_CENT = 10_000_000L
        fun usd(nanoUsd: Long): String = String.format(Locale.GERMANY, "%.2f", nanoUsd / 1_000_000_000.0)
    }
}
