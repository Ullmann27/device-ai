package at.heinz.deviceai.localmodel

import android.content.Context
import android.content.SharedPreferences

/**
 * Android storage for the owner's accelerator switch and its crash-loop markers (see [LocalModelAcceleration]).
 * One controller per process, shared by the runtime and the model centre.
 */
object LocalModelAccelerationHost {
    @Volatile private var controller: LocalModelAccelerationController? = null

    fun controller(context: Context): LocalModelAccelerationController = controller ?: synchronized(this) {
        controller ?: LocalModelAccelerationController(store(context.applicationContext.getSharedPreferences(
            LocalModelAcceleration.PREFERENCES, Context.MODE_PRIVATE))).also { controller = it }
    }

    internal fun store(preferences: SharedPreferences): LocalModelAccelerationStore = PreferenceStore(preferences)

    private class PreferenceStore(private val preferences: SharedPreferences) : LocalModelAccelerationStore {
        override var requested: LocalModelBackend
            get() = LocalModelAcceleration.parse(preferences.getString(LocalModelAcceleration.KEY_REQUESTED, null))
            set(value) = durable { putString(LocalModelAcceleration.KEY_REQUESTED, value.name) }

        override var gpuLoadPending: Boolean
            get() = preferences.getBoolean(LocalModelAcceleration.KEY_GPU_LOAD_PENDING, false)
            set(value) = durable { putBoolean(LocalModelAcceleration.KEY_GPU_LOAD_PENDING, value) }

        override var gpuGenerationPending: Boolean
            get() = preferences.getBoolean(LocalModelAcceleration.KEY_GPU_GENERATION_PENDING, false)
            set(value) = durable { putBoolean(LocalModelAcceleration.KEY_GPU_GENERATION_PENDING, value) }

        override var gpuProvenModel: String?
            get() = preferences.getString(LocalModelAcceleration.KEY_GPU_PROVEN_MODEL, null)
            set(value) = durable { putString(LocalModelAcceleration.KEY_GPU_PROVEN_MODEL, value) }

        override var notice: LocalModelAcceleration.Notice?
            get() = LocalModelAcceleration.parseNotice(preferences.getString(LocalModelAcceleration.KEY_NOTICE, null))
            set(value) = durable { putString(LocalModelAcceleration.KEY_NOTICE, value?.name) }

        // commit(), not apply(): a marker must be on disk before the native call that may end the process.
        private fun durable(edit: SharedPreferences.Editor.() -> Unit) {
            check(preferences.edit().apply(edit).commit()) { "LOCAL_MODEL_ACCELERATOR_STATE_NOT_STORED" }
        }
    }
}
