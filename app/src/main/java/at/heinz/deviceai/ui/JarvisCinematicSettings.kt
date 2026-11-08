package at.heinz.deviceai.ui

import android.content.Context
import android.content.SharedPreferences

/** Reversible presentation-only switch; never changes avatar, speech or task state. */
object JarvisCinematicSettings {
    const val KEY = "cinematic-work-windows"
    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = preferences(context).getBoolean(KEY, true)
}
