package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.widget.ScrollView
import android.widget.TextView

/** Keeps the connection attempt and its result visible instead of dismissing a menu. */
class ShizukuConnectionDialog(
    private val activity: Activity,
    private val bridge: ShizukuBridge
) {
    private var dialog: AlertDialog? = null
    private var statusView: TextView? = null
    private var lastStatus = "Shizuku-Verbindung wird geprüft …"

    fun updateStatus(message: String) {
        lastStatus = message
        statusView?.text = description(message)
    }

    fun show(): AlertDialog {
        dialog?.takeIf { it.isShowing }?.let { return it }
        val padding = (20 * activity.resources.displayMetrics.density).toInt()
        val status = TextView(activity).apply {
            text = description(lastStatus)
            textSize = 16f
            setPadding(padding, padding, padding, padding)
            setTextIsSelectable(true)
        }
        statusView = status
        val shown = AlertDialog.Builder(activity)
            .setTitle("Shizuku · Gerätezugriff")
            .setView(ScrollView(activity).apply { addView(status) })
            .setPositiveButton("VERBINDEN", null)
            .setNeutralButton("SHIZUKU ÖFFNEN", null)
            .setNegativeButton("Schließen", null)
            .create()
        dialog = shown
        shown.setOnDismissListener {
            if (dialog === shown) { dialog = null; statusView = null }
        }
        shown.show()
        // Android's Builder listeners dismiss by default. Connection and manager
        // actions deliberately keep this status screen available on return.
        shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { bridge.requestAccess() }
        shown.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val intent = activity.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent == null) {
                updateStatus("Shizuku ist nicht installiert oder die App ist nicht verfügbar. Bitte Shizuku installieren und dort den Dienst starten.")
            } else {
                runCatching { activity.startActivity(intent) }.onFailure {
                    updateStatus("Shizuku konnte nicht geöffnet werden: ${it.message ?: it.javaClass.simpleName}")
                }
            }
        }
        bridge.refresh()
        return shown
    }

    fun dismiss() { dialog?.dismiss() }

    private fun description(message: String): String = message + "\n\n" +
        "1. Shizuku öffnen und den Dienst dort starten, zum Beispiel über Androids WLAN-Debugging.\n\n" +
        "2. Hier VERBINDEN wählen und die Shizuku-Berechtigung erlauben.\n\n" +
        "Erst eine antwortende Shell-Verbindung meldet BEREIT. Eine WLAN-Verbindung allein startet Shizuku nicht."
}
