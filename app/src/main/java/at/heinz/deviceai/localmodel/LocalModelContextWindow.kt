package at.heinz.deviceai.localmodel

/** Optional larger on-device context only when model metadata and real Android health agree.
 * The 4K baseline remains unchanged on low-RAM, warm, low-memory, power-save or unknown states.
 * This selects a native context *capacity*, not a claim about answer quality or a free token budget.
 */
internal object LocalModelContextWindow {
    private const val GIB = 1_073_741_824L
    private const val MIN_TOTAL_RAM = 10L * GIB
    private const val MID_AVAILABLE_RAM = 4L * GIB
    private const val HIGH_AVAILABLE_RAM = 6L * GIB
    private const val MID_CONTEXT = 6_144
    private const val HIGH_CONTEXT = 8_192

    fun choose(modelLimit: Int, snapshot: LocalModelResourceSnapshot?, powerSave: Boolean?): Int {
        val baseline = LocalNativePromptBudget.contextCapacity(modelLimit)
        if (modelLimit <= 0 || snapshot == null || powerSave != false ||
            snapshot.systemLowMemory || snapshot.thermalStatus == null ||
            snapshot.thermalStatus >= 2 || snapshot.totalRamBytes < MIN_TOTAL_RAM) return baseline

        val preferred = when {
            snapshot.availableRamBytes >= HIGH_AVAILABLE_RAM -> HIGH_CONTEXT
            snapshot.availableRamBytes >= MID_AVAILABLE_RAM -> MID_CONTEXT
            else -> return baseline
        }
        // Never exceed the model's advertised context or the tested upper limit.
        return minOf(modelLimit, preferred).coerceAtLeast(baseline)
    }
}
