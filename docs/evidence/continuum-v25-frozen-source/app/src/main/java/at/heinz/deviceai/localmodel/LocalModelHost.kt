package at.heinz.deviceai.localmodel

import android.content.Context
import java.io.File

/**
 * One model catalog and one LiteRT-LM runtime per process. An activity that is
 * recreated (rotation, folding) reuses the loaded engine instead of loading a
 * second copy of a model of several hundred megabytes or leaking the first one
 * together with its worker thread. The activity unloads the model only when it
 * is really finishing.
 */
object LocalModelHost {
    class Host(val catalog: LocalModelCatalog, val runtime: LiteRtLmLocalModelRuntime)

    @Volatile private var host: Host? = null

    fun get(context: Context): Host = host ?: synchronized(this) {
        host ?: context.applicationContext.let { app ->
            val catalog = LocalModelCatalog(File(app.filesDir, "local-models"), NativeLiteRtModelInspector())
            Host(catalog, LiteRtLmLocalModelRuntime(catalog, File(app.cacheDir, "litertlm")))
        }.also { host = it }
    }
}
