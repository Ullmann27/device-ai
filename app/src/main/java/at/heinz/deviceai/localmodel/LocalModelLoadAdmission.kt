package at.heinz.deviceai.localmodel

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import at.heinz.deviceai.cognitive.ConstrainedResourceFabric

/** Measured Android telemetry at the moment a native model load is requested. */
data class LocalModelResourceSnapshot(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    /** ActivityManager.MemoryInfo.lowMemory: Android itself reports memory pressure. */
    val systemLowMemory: Boolean,
    /** PowerManager.currentThermalStatus (0..6); null below API 29. */
    val thermalStatus: Int?
) {
    init {
        require(totalRamBytes > 0 && availableRamBytes >= 0)
        require(thermalStatus == null || thermalStatus in 0..6)
    }
}

/**
 * Admission in front of the only native model-load path. It reuses the
 * measured ConstrainedResourceFabric rules instead of inventing a second guard:
 *  - thermal >= SEVERE (3) refuses,
 *  - Android's own lowMemory signal refuses,
 *  - weights that cannot stay resident in 3/4 of physical RAM refuse.
 * LiteRT-LM memory-maps weights, so the file size is NOT compared with the
 * momentary availMem (that would wrongly lock the owner's working Gemma out).
 * Missing telemetry fails closed. A refusal never calls runtime.load.
 */
object LocalModelLoadAdmission {
    const val REFUSED_THERMAL = "LOCAL_MODEL_LOAD_REFUSED_THERMAL"
    const val REFUSED_MEMORY = "LOCAL_MODEL_LOAD_REFUSED_MEMORY"
    const val NO_MEASUREMENT = "LOCAL_MODEL_LOAD_NO_MEASUREMENT"

    /** Null when admitted, otherwise one of the refusal codes above. */
    fun refusal(model: ImportedLocalModel, snapshot: LocalModelResourceSnapshot?): String? {
        snapshot ?: return NO_MEASUREMENT
        if (snapshot.thermalStatus != null && ConstrainedResourceFabric.plan(
                ConstrainedResourceFabric.Snapshot(snapshot.availableRamBytes, 0, 0, snapshot.thermalStatus)
            ) == ConstrainedResourceFabric.Plan.REFUSE_THERMAL) return REFUSED_THERMAL
        if (snapshot.systemLowMemory) return REFUSED_MEMORY
        val residency = ConstrainedResourceFabric.plan(
            ConstrainedResourceFabric.Snapshot(snapshot.totalRamBytes, model.byteCount, 0, snapshot.thermalStatus ?: 0)
        )
        if (residency == ConstrainedResourceFabric.Plan.REFUSE_MEMORY) return REFUSED_MEMORY
        return null
    }

    fun isRefusal(problem: String?): Boolean = problem == REFUSED_THERMAL || problem == REFUSED_MEMORY || problem == NO_MEASUREMENT

    fun ownerMessage(problem: String?): String = when (problem) {
        REFUSED_THERMAL -> "Modell nicht geladen: Das Gerät ist zu heiß. Bitte abkühlen lassen und erneut laden."
        REFUSED_MEMORY -> "Modell nicht geladen: Zu wenig Arbeitsspeicher. Bitte andere Apps schließen und erneut laden."
        NO_MEASUREMENT -> "Modell nicht geladen: Speicher-/Temperaturmessung nicht verfügbar."
        else -> "Modell nicht geladen."
    }

    /**
     * Calls [onAdmitted] and then runtime.load only when admitted; otherwise
     * reports the refusal through [onRefused] without touching the runtime.
     */
    fun loadIfAdmitted(
        runtime: LocalModelRuntime,
        model: ImportedLocalModel,
        snapshot: LocalModelResourceSnapshot?,
        onRefused: (Result<ImportedLocalModel>) -> Unit,
        onAdmitted: () -> Unit = {},
        callback: (Result<ImportedLocalModel>) -> Unit = {}
    ) {
        val refusal = refusal(model, snapshot)
        if (refusal != null) {
            onRefused(Result.failure(IllegalStateException(refusal)))
            return
        }
        onAdmitted()
        runtime.load(model.id, model.version, callback)
    }
}

/** Reads real Android telemetry; null when ActivityManager is unavailable. */
fun Context.localModelResourceSnapshot(): LocalModelResourceSnapshot? = runCatching {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
    val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus
    } else null
    LocalModelResourceSnapshot(info.totalMem, info.availMem, info.lowMemory, thermal)
}.getOrNull()
