package at.heinz.deviceai.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.widget.SeekBar
import android.widget.Switch
import org.json.JSONObject

/** Additional light, independent of avatar. */
object Command03AppearanceSettings {
    private const val DEFAULT_LIGHT = 70
    private fun prefs(context: Context) = context.getSharedPreferences("continuum-command03", Context.MODE_PRIVATE)

    fun snapshot(context: Context): JSONObject = JSONObject()
        .put("neon", prefs(context).getInt("neon_light", DEFAULT_LIGHT).coerceIn(0, 100))
        .put("lightMotion", prefs(context).getBoolean("neon_motion", true))

    /** Owner-selected quality profile of the HTML stage; absent or unknown values mean automatic. */
    fun performanceProfile(context: Context): Command03StageSignals.Profile =
        Command03StageSignals.Profile.of(prefs(context).getString("performance_profile", null))

    fun show(activity: Activity, onChanged: () -> Unit): AlertDialog {
        val cockpit = ContinuumBentoCockpit
        val box = cockpit.panel(activity, "CONTINUUM · DARSTELLUNG", "Hologramm & Neonlicht",
            "Cyan und Violett beleuchten Glaskanten und Projektion. Änderungen siehst du direkt nach dem Schließen.")
        box.tag = "command03-appearance-settings"
        val settings = snapshot(activity)
        val value = cockpit.section(activity, "Neonlicht · ${settings.getInt("neon")}%")
        box.addView(value)
        val slider = SeekBar(activity).apply {
            tag = "command03-neon-intensity"
            contentDescription = "Neonlichtstärke"
            max = 100
            progress = settings.getInt("neon")
            minimumHeight = (48 * resources.displayMetrics.density).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "Neonlicht · $progress%"
                    if (fromUser) {
                        prefs(activity).edit().putInt("neon_light", progress).apply()
                        onChanged()
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        box.addView(slider)
        val motion = Switch(activity).apply {
            tag = "command03-neon-motion"
            text = "Sanfte Lichtbewegung"
            setTextColor(Color.rgb(231, 249, 255))
            minimumHeight = (56 * resources.displayMetrics.density).toInt()
            isChecked = settings.getBoolean("lightMotion")
            setOnCheckedChangeListener { _, checked ->
                prefs(activity).edit().putBoolean("neon_motion", checked).apply()
                onChanged()
            }
        }
        box.addView(motion)
        val profileLabel = cockpit.section(activity, "")
        fun showProfile() {
            profileLabel.text = "Leistungsprofil · " + performanceProfile(activity).label
        }
        showProfile()
        box.addView(profileLabel)
        box.addView(cockpit.action(activity, "LEISTUNGSPROFIL WECHSELN") {
            val all = Command03StageSignals.Profile.entries
            val next = all[(all.indexOf(performanceProfile(activity)) + 1) % all.size]
            prefs(activity).edit().putString("performance_profile", next.name).apply()
            showProfile()
            onChanged()
        }.apply { tag = "command03-performance-profile" })
        box.addView(cockpit.action(activity, "STANDARD WIEDERHERSTELLEN") {
            prefs(activity).edit().putInt("neon_light", DEFAULT_LIGHT).putBoolean("neon_motion", true)
                .remove("performance_profile").apply()
            slider.progress = DEFAULT_LIGHT
            motion.isChecked = true
            showProfile()
            onChanged()
        })
        lateinit var dialog: AlertDialog
        box.addView(cockpit.action(activity, "FERTIG", primary = true) { dialog.dismiss() })
        dialog = cockpit.show(activity, box)
        return dialog
    }
}
