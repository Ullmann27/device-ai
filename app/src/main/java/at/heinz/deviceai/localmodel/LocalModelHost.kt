package at.heinz.deviceai.localmodel

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
    class Host(val catalog: LocalModelCatalog, val runtime: LocalModelRuntime) {
        // Activity executors may be interrupted on rotation/folding; a multi-GB
        // import belongs to the same process lifetime as this catalog/runtime.
        val imports = LocalModelImportCoordinator(catalog)
    }

    @Volatile private var host: Host? = null

    fun get(context: Context): Host = host ?: synchronized(this) {
        host ?: context.applicationContext.let { app ->
            val catalog = LocalModelCatalog(File(app.filesDir, "local-models"), NativeLiteRtModelInspector())
            Host(catalog, thermallyGuarded(app, LiteRtLmLocalModelRuntime(catalog, File(app.cacheDir, "litertlm"))))
        }.also { host = it }
    }

    /**
     * #569: every caller (chat, research, bots, planning) shares this one runtime, so the thermal guard sits here.
     * The listener lives as long as the process-wide runtime; it only reads Android's thermal level.
     */
    private fun thermallyGuarded(app: Context, native: LocalModelRuntime): LocalModelRuntime {
        val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val status: () -> Int? = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power?.currentThermalStatus else null }
        val runtime = LocalModelThermalGuard.wrap(native, status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching {
            power?.addThermalStatusListener(app.mainExecutor) { level -> LocalModelThermalGuard.onThermalStatus(runtime, level) }
        }
        return runtime
    }
}
