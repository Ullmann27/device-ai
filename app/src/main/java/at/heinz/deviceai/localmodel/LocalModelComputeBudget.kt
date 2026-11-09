package at.heinz.deviceai.localmodel

/**
 * Owner finding (Fold7, Code50 0.3.49): the device heats up within one or two Gemma answers and Android then
 * reports SEVERE, which makes [LocalModelThermalGuard] stop the generation. The main heat source is the LiteRT-LM
 * CPU backend, which by default spins up one worker per core (8 on the Fold7's Snapdragon 8 Elite), including the
 * efficiency cores that add little decode speed but a lot of heat. This budget picks the number of inference
 * threads at LOAD time from real signals only: the core count, Android's thermal status level (0..6, never a
 * temperature in °C — Android does not expose the owner's "35 °C" to apps) and the owner's cooling preference.
 *
 * Pure Kotlin; no Android dependency. Changing the thread count requires a model reload (engine config).
 */
object LocalModelComputeBudget {
    /** BALANCED: about half the cores. COOL: a quarter, at most two threads; slower but much cooler. */
    enum class CoolingMode { BALANCED, COOL }

    const val PREFERENCES = "continuum.compute"
    const val KEY_COOLING_MODE = "cooling_mode"
    /** PowerManager.THERMAL_STATUS_MODERATE. */
    const val MODERATE = 2

    /** Fold7 defaults to two threads; explicit user preference always overrides it. */
    fun defaultMode(manufacturer: String?, model: String?): CoolingMode =
        if (manufacturer?.trim().equals("samsung", ignoreCase = true) &&
            model?.trim()?.startsWith("SM-F966", ignoreCase = true) == true) CoolingMode.COOL
        else CoolingMode.BALANCED

    fun inferenceThreads(availableProcessors: Int, thermalStatus: Int?, mode: CoolingMode): Int {
        val cores = availableProcessors.coerceAtLeast(1)
        val base = when (mode) {
            CoolingMode.BALANCED -> (cores / 2).coerceIn(1, 4)
            CoolingMode.COOL -> (cores / 4).coerceIn(1, 2)
        }
        // Already warm at load time: do not add fuel; the guard would stop the first long answer anyway.
        val warm = (thermalStatus ?: 0) >= MODERATE
        return if (warm) base.coerceAtMost(2) else base
    }

    fun parseMode(raw: String?): CoolingMode =
        CoolingMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CoolingMode.BALANCED

    fun describe(threads: Int, cores: Int, mode: CoolingMode): String =
        "Lokale Inferenz: $threads von $cores CPU-Kernen (${if (mode == CoolingMode.COOL) "Kühlmodus" else "ausgewogen"})."
}
