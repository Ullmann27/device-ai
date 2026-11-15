package at.heinz.deviceai.localmodel

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.io.File

/**
 * One model catalog and one LiteRT-LM runtime per process. An activity that is
 * recreated (rotation, folding) reuses the loaded engine instead of loading a
 * second copy of a model of several hundred megabytes or leaking the first one
 * together with its worker thread. The activity unloads the model only when it
 * is really finishing.
 */
object LocalModelHost {
    const val PAUSED_MEMORY = "LOCAL_MODEL_PAUSED_MEMORY"
    const val MEMORY_NO_MEASUREMENT = "LOCAL_MODEL_MEMORY_NO_MEASUREMENT"

    class Host(val catalog: LocalModelCatalog, val runtime: LocalModelRuntime) {
        // Activity executors may be interrupted on rotation/folding; a multi-GB
        // import belongs to the same process lifetime as this catalog/runtime.
        val imports = LocalModelImportCoordinator(catalog)
    }

    @Volatile private var host: Host? = null

    fun get(context: Context): Host = host ?: synchronized(this) {
        host ?: context.applicationContext.let { app ->
            val catalog = LocalModelCatalog(File(app.filesDir, "local-models"), NativeLiteRtModelInspector())
            val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val runtime = LiteRtLmLocalModelRuntime(catalog, File(app.cacheDir, "litertlm"),
                cpuThreadCount = { inferenceThreads(app, power) },
                contextCapacitySelector = { modelLimit ->
                    LocalModelContextWindow.choose(modelLimit, app.localModelResourceSnapshot(),
                        runCatching { power?.isPowerSaveMode }.getOrNull())
                })
            Host(catalog, thermallyGuarded(app, runtime, power))
        }.also { host = it }
    }

    /**
     * #569: every caller (chat, research, bots, planning) shares this one runtime, so the thermal guard sits here.
     * The listener lives as long as the process-wide runtime; it only reads Android's thermal level.
     */
    private fun thermallyGuarded(app: Context, native: LocalModelRuntime, power: PowerManager?): LocalModelRuntime {
        val status: () -> Int? = { thermalStatus(power) }
        val memoryGuarded = object : LocalModelRuntime by native {
            private fun start(callback: LocalInferenceCallback, action: (LocalInferenceCallback) -> Unit) {
                val refusal = generationMemoryRefusal(app)
                if (refusal != null) callback.onEvent(LocalInferenceEvent.Failed(refusal))
                else action(callback)
            }
            override fun stream(prompt: String, callback: LocalInferenceCallback) =
                start(callback) { native.stream(prompt, it) }
            override fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) =
                start(callback) { native.streamTextOnly(prompt, it) }
            override fun streamThinking(prompt: String, callback: LocalInferenceCallback) =
                start(callback) { native.streamThinking(prompt, it) }
            override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
                start(callback) { native.respondToTools(responses, it) }
        }
        val runtime = LocalModelThermalGuard.wrap(memoryGuarded, status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching {
            power?.addThermalStatusListener(app.mainExecutor) { level -> LocalModelThermalGuard.onThermalStatus(runtime, level) }
        }
        return runtime
    }

    private fun generationMemoryRefusal(app: Context): String? {
        val lowMemory: Boolean? = runCatching {
            val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return@runCatching null
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo).lowMemory
        }.getOrNull()
        return when (lowMemory) {
            false -> null
            true -> PAUSED_MEMORY
            null -> MEMORY_NO_MEASUREMENT
        }
    }

    private fun thermalStatus(power: PowerManager?): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching { power?.currentThermalStatus }.getOrNull() else null

    fun coolingMode(context: Context): LocalModelComputeBudget.CoolingMode {
        val saved = context.applicationContext
            .getSharedPreferences(LocalModelComputeBudget.PREFERENCES, Context.MODE_PRIVATE)
            .getString(LocalModelComputeBudget.KEY_COOLING_MODE, null)
        return if (saved == null)
            LocalModelComputeBudget.defaultMode(Build.MANUFACTURER, Build.MODEL)
        else LocalModelComputeBudget.parseMode(saved)
    }

    /** Owner preference; takes effect at the next model load (engine thread count is fixed per load). */
    fun setCoolingMode(context: Context, mode: LocalModelComputeBudget.CoolingMode) {
        context.applicationContext.getSharedPreferences(LocalModelComputeBudget.PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(LocalModelComputeBudget.KEY_COOLING_MODE, mode.name).apply()
    }

    /** Thread budget for the LiteRT-LM CPU backend, decided from the core count, thermal level and cooling mode. */
    fun inferenceThreads(context: Context, power: PowerManager?): Int = LocalModelComputeBudget.inferenceThreads(
        Runtime.getRuntime().availableProcessors(), thermalStatus(power), coolingMode(context))
}
