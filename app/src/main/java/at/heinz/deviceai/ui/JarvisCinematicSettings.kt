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
    enum class Experiment(val label: String) {
        EXP_01_Z_GLASS("3D-Glasschichten"),
        EXP_02_RIM("Dauerhafter Hologrammrahmen"),
        EXP_03_DEPTH_GLASS("Geschützte Tiefenflächen"),
        EXP_04_FOLDER_FLIGHT("Fensterflug aus Kacheln"),
        EXP_05_WINGS("Räumliche Cockpit-Flügel"),
        EXP_06_FACE_LIGHT("Fensterlicht auf Avatar"),
        EXP_07_GAZE("Augen-Hals-Kopplung"),
        EXP_08_PRE_SPEECH("Vorbereitende Lippenbewegung"),
        EXP_09_FOCUS_BLINK("Blickwechsel-Lidschlag"),
        EXP_10_SENTENCE("Natürliche Satzgesten"),
        EXP_11_ORB_LINK("Kugel-Fenster-Energiebrücke"),
        EXP_12_RECEIPTS("Bestätigte Arbeitsschritt-Impulse"),
        EXP_13_LUXURY("Edles Rauchglas und gerichtete Farben"),
        EXP_14_GLASS_SHADER("Hardware-Glaskanten"),
        EXP_15_FRAME_BUDGET("Thermisch adaptive Animation")
    }

    fun experimentEnabled(context: Context, experiment: Experiment): Boolean =
        enabled(context) && depthExperimentEnabled(context) &&
            preferences(context).getBoolean("exp52." + experiment.name, true)

    fun setExperiment(context: Context, experiment: Experiment, enabled: Boolean) {
        preferences(context).edit().putBoolean("exp52." + experiment.name, enabled).apply()
    }

}
