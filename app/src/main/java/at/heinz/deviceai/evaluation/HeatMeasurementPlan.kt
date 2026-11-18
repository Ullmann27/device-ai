package at.heinz.deviceai.evaluation

import org.json.JSONObject
import java.util.Locale

/**
 * Owner-started „Wärme- und Zeitmessung“: what is measured, in which order, and how it is started by text.
 *
 * Nothing in this file touches Android. The fixed owner orders come from the versioned asset
 * `continuum-heat-v1/prompts.json` (source: docs/evaluation/continuum-heat-v1). Every configuration differs from
 * the baseline A in exactly ONE factor; a configuration that cannot run on the device is recorded as NOT_RUN with
 * its reason and is never replaced by another one. No temperature, frame time or duration is assumed anywhere:
 * the only numbers here are waiting limits and the order of the runs.
 */
object HeatMeasurement {
    const val SCHEMA = "continuum-heat-measurement-v1"
    const val PROMPT_ASSET = "continuum-heat-v1/prompts.json"
    const val NOT_RUN = "NOT_RUN"
    const val NOT_AVAILABLE = "NOT_AVAILABLE"

    /** Stated in every report: the probe does not use the microphone. */
    const val INPUT_KIND = "TEXT_AT_SPEECH_HANDOVER"
    const val INPUT_NOTE = "Eingabe: identischer Text an der Sprachübergabe, kein Mikrofon"

    const val FACTOR_STAGE_OFF = "STAGE_OFF"
    const val FACTOR_STAGE_FULL_EFFECTS = "STAGE_FULL_EFFECTS"
    const val FACTOR_DIALOGUE_REUSE = "DIALOGUE_REUSE"
    const val FACTOR_MODEL_E2B = "MODEL_E2B"
    const val FACTOR_BACKEND_GPU = "BACKEND_GPU"

    /**
     * A: the defaults of this app version. B–F change one thing each. C sends the stage the same flag a cloud
     * brain sends, so it moves fully while the device computes – the stage motion of Code67, at the refresh rate
     * this version asks for (Code67 additionally kept 120 Hz in the profile „Volle Qualität“).
     */
    val CONFIGURATIONS: List<HeatConfiguration> = listOf(
        HeatConfiguration("A", "Basis: Bühne ruht beim Rechnen, Gemma 4 E4B, Gespräch nicht behalten", null,
            HeatStageMode.RESTING, dialogueReuse = false, model = HeatModelChoice.BASELINE, backend = HeatBackendChoice.BASELINE),
        HeatConfiguration("B", "Bühne aus (Seite unsichtbar)", FACTOR_STAGE_OFF,
            HeatStageMode.OFF, dialogueReuse = false, model = HeatModelChoice.BASELINE, backend = HeatBackendChoice.BASELINE),
        HeatConfiguration("C", "Bühne mit voller Bewegung beim Rechnen (wie Code67)", FACTOR_STAGE_FULL_EFFECTS,
            HeatStageMode.FULL_EFFECTS, dialogueReuse = false, model = HeatModelChoice.BASELINE, backend = HeatBackendChoice.BASELINE),
        HeatConfiguration("D", "Gespräch im Modell behalten", FACTOR_DIALOGUE_REUSE,
            HeatStageMode.RESTING, dialogueReuse = true, model = HeatModelChoice.BASELINE, backend = HeatBackendChoice.BASELINE),
        HeatConfiguration("E", "Gemma 4 E2B statt E4B", FACTOR_MODEL_E2B,
            HeatStageMode.RESTING, dialogueReuse = false, model = HeatModelChoice.GEMMA_E2B, backend = HeatBackendChoice.BASELINE),
        HeatConfiguration("F", "GPU-Beschleuniger statt CPU", FACTOR_BACKEND_GPU,
            HeatStageMode.RESTING, dialogueReuse = false, model = HeatModelChoice.BASELINE, backend = HeatBackendChoice.GPU)
    )

    val BASELINE: HeatConfiguration get() = CONFIGURATIONS.first()

    fun plan(size: HeatSize, prompts: HeatPromptSet): HeatPlan = HeatPlan(size, prompts, CONFIGURATIONS,
        if (size == HeatSize.KURZ) HeatLimits(cooldownMaxMs = 180_000L) else HeatLimits(cooldownMaxMs = 300_000L))
}

enum class HeatSize(val label: String, val repetitions: Int) { KURZ("kurz", 1), VOLL("voll", 2) }

/** RESTING is the unchanged behaviour of this app version (the stage rests while the on-device model computes). */
enum class HeatStageMode { RESTING, OFF, FULL_EFFECTS }
enum class HeatModelChoice { BASELINE, GEMMA_E2B }
enum class HeatBackendChoice { BASELINE, GPU }

data class HeatConfiguration(
    val id: String,
    val label: String,
    /** Null for the baseline; otherwise the one factor that differs from it. */
    val factor: String?,
    val stage: HeatStageMode,
    val dialogueReuse: Boolean,
    val model: HeatModelChoice,
    val backend: HeatBackendChoice
) {
    /** Number of settings in which this configuration differs from [other]. */
    fun differences(other: HeatConfiguration): Int = listOf(stage != other.stage, dialogueReuse != other.dialogueReuse,
        model != other.model, backend != other.backend).count { it }

    /** Loading another model file or engine: its runs stay together so that the model is swapped once. */
    val swapsModel: Boolean get() = model != HeatModelChoice.BASELINE || backend != HeatBackendChoice.BASELINE

    fun settingsJson(): JSONObject = JSONObject().put("stage", stage.name).put("dialogueReuse", dialogueReuse)
        .put("model", model.name).put("backend", backend.name)
}

/** One fixed owner order: a short dialogue in a new chat, first question and follow-up. */
data class HeatPrompt(val id: String, val label: String, val turns: List<String>) {
    init {
        require(id.matches(Regex("[a-z0-9-]{1,32}")) && label.isNotBlank())
        require(turns.size in 1..MAX_TURNS && turns.all { it.isNotBlank() && it.length <= MAX_TURN_CHARACTERS })
    }

    companion object {
        const val MAX_TURNS = 4
        const val MAX_TURN_CHARACTERS = 400
    }
}

data class HeatPromptSet(val id: String, val version: Int, val prompts: List<HeatPrompt>) {
    init { require(id.isNotBlank() && version > 0 && prompts.isNotEmpty() && prompts.map { it.id }.distinct().size == prompts.size) }

    companion object {
        fun parse(text: String): HeatPromptSet {
            val json = JSONObject(text)
            val list = json.getJSONArray("prompts")
            return HeatPromptSet(json.getString("id"), json.getInt("version"), (0 until list.length()).map { index ->
                val prompt = list.getJSONObject(index)
                val turns = prompt.getJSONArray("turns")
                HeatPrompt(prompt.getString("id"), prompt.getString("label"), (0 until turns.length()).map(turns::getString))
            })
        }
    }
}

/**
 * Waiting limits of the protocol. They are limits and cadences of the measurement itself, not expectations about
 * the device: how long a cool-down may take at most, when a turn counts as timed out, how often sensors are read.
 */
data class HeatLimits(
    val turnTimeoutMs: Long = 240_000L,
    /** Even a device that reports the start status at once rests this long before a run. */
    val cooldownMinMs: Long = 20_000L,
    val cooldownMaxMs: Long = 180_000L,
    /** Allowed distance of the battery temperature from the first start temperature, in °C. */
    val batteryBandC: Double = 1.0,
    /** The configuration is applied this long before the order is handed over. */
    val settleMs: Long = 3_000L,
    val sampleIntervalMs: Long = 2_000L,
    val pollIntervalMs: Long = 250L,
    /** A started spoken answer is awaited at most this long before the next cool-down begins. */
    val speechWaitMaxMs: Long = 120_000L,
    val freshChatWaitMs: Long = 15_000L
) {
    init {
        require(turnTimeoutMs > 0 && cooldownMinMs >= 0 && cooldownMaxMs >= cooldownMinMs && batteryBandC > 0)
        require(settleMs >= 0 && sampleIntervalMs > 0 && pollIntervalMs in 1..sampleIntervalMs)
        require(speechWaitMaxMs >= 0 && freshChatWaitMs > 0)
    }

    fun toJson(): JSONObject = JSONObject().put("turnTimeoutMs", turnTimeoutMs).put("cooldownMinMs", cooldownMinMs)
        .put("cooldownMaxMs", cooldownMaxMs).put("batteryBandC", batteryBandC).put("settleMs", settleMs)
        .put("sampleIntervalMs", sampleIntervalMs).put("speechWaitMaxMs", speechWaitMaxMs)
}

data class HeatPlannedRun(val configuration: HeatConfiguration, val prompt: HeatPrompt, val repetition: Int)

class HeatPlan(
    val size: HeatSize,
    val promptSet: HeatPromptSet,
    val configurations: List<HeatConfiguration>,
    val limits: HeatLimits
) {
    init {
        val baseline = configurations.first()
        require(baseline.factor == null) { "The first configuration is the baseline." }
        require(configurations.drop(1).all { it.factor != null && it.differences(baseline) == 1 }) {
            "Every configuration differs from the baseline in exactly one factor."
        }
        require(configurations.map { it.id }.distinct().size == configurations.size)
    }

    /** „kurz“: the first order once per configuration. „voll“: every order, repeated. */
    val prompts: List<HeatPrompt> = if (size == HeatSize.KURZ) promptSet.prompts.take(1) else promptSet.prompts

    /**
     * Order of the runs. Configurations that keep the loaded model alternate order by order and repetition by
     * repetition (A B C D, A B C D, …), so that a slow drift of the device does not fall on one configuration.
     * A configuration that needs another model or engine runs as one block afterwards: one swap in, one back.
     */
    val runs: List<HeatPlannedRun> = run {
        val (blocks, rounds) = configurations.partition { it.swapsModel }
        val repetitions = 1..size.repetitions
        repetitions.flatMap { repetition -> prompts.flatMap { prompt -> rounds.map { HeatPlannedRun(it, prompt, repetition) } } } +
            blocks.flatMap { block -> repetitions.flatMap { repetition -> prompts.map { HeatPlannedRun(block, it, repetition) } } }
    }

    val turnCount: Int get() = runs.sumOf { it.prompt.turns.size }

    /** What the waiting limits allow at most, without model swaps; a bound, not an expectation. */
    val upperBoundMs: Long get() = runs.sumOf { run ->
        limits.cooldownMaxMs + limits.settleMs + limits.freshChatWaitMs +
            run.prompt.turns.size * (limits.turnTimeoutMs + limits.speechWaitMaxMs)
    }

    /** The waiting the protocol always does, whatever the device measures. */
    val fixedWaitMs: Long get() = runs.size * (limits.cooldownMinMs + limits.settleMs)

    /**
     * Expected duration from answer times this device really produced ([recentAnswerMs], e.g. of this session);
     * null when there are none – then only [fixedWaitMs] and [upperBoundMs] can be stated.
     */
    fun expectedMs(recentAnswerMs: List<Long>): Long? {
        val median = HeatStatistics.median(recentAnswerMs.filter { it > 0 }.map(Long::toDouble)) ?: return null
        return fixedWaitMs + Math.round(median * turnCount)
    }

    /** The owner sees this before he confirms the start. */
    fun announcement(recentAnswerMs: List<Long>): String = buildString {
        val known = recentAnswerMs.count { it > 0 }
        append("Wärme- und Zeitmessung (").append(size.label).append("): ").append(configurations.size)
            .append(" Konfigurationen, ").append(runs.size).append(" Läufe, bis zu ").append(turnCount)
            .append(" Modellantworten. Was auf diesem Gerät nicht laufen kann, wird als NOT_RUN eingetragen.\n")
        val expected = expectedMs(recentAnswerMs)
        if (expected != null) append("Erwartete Dauer: etwa ").append(minutes(expected)).append(" Minuten (gerechnet mit dem Median deiner letzten ")
            .append(known).append(if (known == 1) " Antwortzeit" else " Antwortzeiten").append(" in dieser Sitzung, ohne Abkühlzeiten über ")
            .append(limits.cooldownMinMs / 1_000).append(" s je Lauf und ohne Modellwechsel).\n")
        else append("Erwartete Dauer: unbekannt – in dieser Sitzung liegt noch keine gemessene Antwortzeit vor. " +
            "Allein die festen Wartezeiten dauern ").append(minutes(fixedWaitMs)).append(" Minuten.\n")
        append("Obergrenze durch die Zeitlimits: ").append(minutes(upperBoundMs)).append(" Minuten.\n")
        append("Das Gerät bitte liegen lassen, nicht laden, nicht auf- oder zuklappen. STOPP beendet die Messung " +
            "jederzeit; bis dahin gemessene Läufe bleiben im Bericht.")
    }

    private fun minutes(ms: Long): Long = Math.max(1L, Math.round(ms / 60_000.0))
}

sealed interface HeatCommand {
    /** [size] null: the owner did not say which; he is asked. */
    data class Start(val size: HeatSize?) : HeatCommand
    data object Stop : HeatCommand
    /** Save what an interrupted measurement (app closed or ended by Android) had measured so far. */
    data object SavePartial : HeatCommand
}

/**
 * „Continuum Wärmemessung“, „Continuum Wärmemessung kurz“, „Wärmemessung stoppen“, „Continuum Wärmemessung
 * Teilbericht“ – the whole sentence or nothing.
 */
object HeatMeasurementCommand {
    private val start = Regex("(?:continuum )?(?:starte )?(?:die )?w(?:ä|ae)rme(?: und zeit)? ?messung(?: starten)?" +
        "(?: (kurz|voll|vollständig|vollstaendig|komplett|lang))?")
    private val stop = Regex("(?:continuum )?w(?:ä|ae)rme(?: und zeit)? ?messung (?:stoppen|stopp|stop|abbrechen|beenden)")
    private val partial = Regex("(?:continuum )?w(?:ä|ae)rme(?: und zeit)? ?messung teilbericht(?: sichern| speichern)?")
    private val plainStop = setOf("stopp", "stop", "abbrechen", "messung stoppen", "messung abbrechen")

    fun parse(text: String): HeatCommand? {
        val spoken = normalize(text)
        if (stop.matches(spoken)) return HeatCommand.Stop
        if (partial.matches(spoken)) return HeatCommand.SavePartial
        val match = start.matchEntire(spoken) ?: return null
        return HeatCommand.Start(when (match.groupValues[1]) {
            "" -> null
            "kurz" -> HeatSize.KURZ
            else -> HeatSize.VOLL
        })
    }

    /** Only while a measurement is running: a bare „Stopp“ ends it. */
    fun stopsRunningMeasurement(text: String): Boolean =
        normalize(text).let { it in plainStop || stop.matches(it) }

    private fun normalize(text: String): String = text.lowercase(Locale.GERMAN)
        .replace(Regex("[\\-–—]"), " ").replace(Regex("[.!?,;:„“\"']"), " ").replace(Regex("\\s+"), " ").trim()
}
