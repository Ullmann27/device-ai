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
    enum class Experiment(val label: String, val wired: Boolean = false) {
        EXP_01_Z_GLASS("3D-Glasschichten", wired = true),
        EXP_02_RIM("Dauerhafter Hologrammrahmen", wired = true),
        EXP_03_DEPTH_GLASS("Geschützte Tiefenflächen"),
        EXP_04_FOLDER_FLIGHT("Fensterflug aus Kacheln", wired = true),
        EXP_05_WINGS("Räumliche Cockpit-Flügel", wired = true),
        EXP_06_FACE_LIGHT("Fensterlicht auf Avatar", wired = true),
        EXP_07_GAZE("Augen-Hals-Kopplung"),
        EXP_08_PRE_SPEECH("Vorbereitende Lippenbewegung", wired = true),
        EXP_09_FOCUS_BLINK("Blickwechsel-Lidschlag", wired = true),
        EXP_10_SENTENCE("Natürliche Satzgesten"),
        EXP_11_ORB_LINK("Kugel-Fenster-Energiebrücke"),
        EXP_12_RECEIPTS("Bestätigte Arbeitsschritt-Impulse", wired = true),
        EXP_13_LUXURY("Edles Rauchglas und gerichtete Farben", wired = true),
        EXP_14_GLASS_SHADER("Hardware-Glaskanten", wired = true),
        EXP_15_FRAME_BUDGET("Thermisch adaptive Animation")
    }

    fun experimentEnabled(context: Context, experiment: Experiment): Boolean =
        experiment.wired && enabled(context) && depthExperimentEnabled(context) &&
            preferences(context).getBoolean("exp52." + experiment.name, true)

    fun setExperiment(context: Context, experiment: Experiment, enabled: Boolean) {
        if (experiment.wired)
            preferences(context).edit().putBoolean("exp52." + experiment.name, enabled).apply()
    }

}
