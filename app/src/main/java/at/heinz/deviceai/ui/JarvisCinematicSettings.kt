package at.heinz.deviceai.ui

import android.content.Context
import android.content.SharedPreferences

/** Reversible presentation-only switch; never changes avatar, speech or task state. */
object JarvisCinematicSettings {
    const val KEY = "cinematic-work-windows"
    const val DEPTH_EXPERIMENT_KEY = "exp-52-cine-depth"
    fun depthExperimentEnabled(context: Context): Boolean =
        preferences(context).getBoolean(DEPTH_EXPERIMENT_KEY, false)
    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = preferences(context).getBoolean(KEY, true)
}
