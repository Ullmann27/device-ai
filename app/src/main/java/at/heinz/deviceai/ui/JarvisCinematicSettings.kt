package at.heinz.deviceai.ui

import android.content.Context
import android.content.SharedPreferences

/** Cinema 52 release presentation: active by default; Android thermal/accessibility safety remains authoritative. */
object JarvisCinematicSettings {
    const val KEY = "cinematic-work-windows"
    const val DEPTH_EXPERIMENT_KEY = "exp-52-cine-depth"
    private const val CINEMA52_MIGRATED_KEY = "cinema52-effects-enabled-on-upgrade-v1"
    fun depthExperimentEnabled(context: Context): Boolean =
        preferences(context).getBoolean(DEPTH_EXPERIMENT_KEY, true)
    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = preferences(context).getBoolean(KEY, true)

    /**
     * One-time Code52 migration: old persisted OFF switches must not silently downgrade
     * an in-place upgrade to an almost empty/legacy-looking screen. All 15 wired Cinema
     * features become active on first Code52 launch, including for existing installations.
     * A later explicit per-effect owner choice is not rewritten on each launch.
     * GPU/CPU protection and Android reduced-motion remain handled by JarvisRenderQuality.
     */
    @Synchronized fun activateCinema52ByDefault(context: Context) {
        val prefs = preferences(context)
        if (prefs.getBoolean(CINEMA52_MIGRATED_KEY, false)) return
        val editor = prefs.edit().putBoolean(KEY, true).putBoolean(DEPTH_EXPERIMENT_KEY, true)
        Experiment.entries.filter { it.wired }.forEach { experiment ->
            editor.putBoolean("exp52." + experiment.name, true)
        }
        editor.putBoolean(CINEMA52_MIGRATED_KEY, true).apply()
    }

    enum class Experiment(val label: String, val wired: Boolean = false) {
        EXP_01_Z_GLASS("3D-Glasschichten", wired = true),
        EXP_02_RIM("Dauerhafter Hologrammrahmen", wired = true),
        EXP_03_DEPTH_GLASS("Geschützte Tiefenflächen", wired = true),
        EXP_04_FOLDER_FLIGHT("Fensterflug aus Kacheln", wired = true),
        EXP_05_WINGS("Räumliche Cockpit-Flügel", wired = true),
        EXP_06_FACE_LIGHT("Fensterlicht und Lesefaden am Avatar", wired = true),
        EXP_07_GAZE("Augen-Hals-Kopplung", wired = true),
        EXP_08_PRE_SPEECH("Vorbereitende Lippenbewegung", wired = true),
        EXP_09_FOCUS_BLINK("Blickwechsel-Lidschlag", wired = true),
        EXP_10_SENTENCE("Natürliche Satzgesten", wired = true),
        EXP_11_ORB_LINK("Kugel-Fenster-Energiebrücke", wired = true),
        EXP_12_RECEIPTS("Bestätigte Arbeitsschritt-Impulse", wired = true),
        EXP_13_LUXURY("Edles Rauchglas und gerichtete Farben", wired = true),
        EXP_14_GLASS_SHADER("Hardware-Glaskanten", wired = true),
        EXP_15_FRAME_BUDGET("Thermisch adaptive Animation", wired = true)
    }

    fun cinemaOptIn(context: Context): Boolean =
        enabled(context) && depthExperimentEnabled(context)

    fun experimentEnabled(context: Context, experiment: Experiment): Boolean =
        experiment.wired && enabled(context) && depthExperimentEnabled(context) &&
            preferences(context).getBoolean("exp52." + experiment.name, true)

    fun setExperiment(context: Context, experiment: Experiment, enabled: Boolean) {
        if (experiment.wired)
            preferences(context).edit().putBoolean("exp52." + experiment.name, enabled).apply()
    }

}
