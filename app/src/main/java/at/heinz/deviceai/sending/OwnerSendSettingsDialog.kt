package at.heinz.deviceai.sending

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.ui.ContinuumBentoCockpit

/**
 * „Selbst senden“ in the settings hub: the one place where the owner grants and revokes the standing permission per
 * channel. Default off. Switching on shows what Continuum will then do without asking again and needs an explicit
 * confirmation; switching off takes effect at once. This file is the only production code that creates an
 * [OwnerSendOwnerTap] – a grant can therefore only change by the owner's own tap here.
 */
internal object OwnerSendSettingsDialog {
    const val TAG_STATUS = "owner-send-status:"
    const val TAG_TOGGLE = "owner-send-toggle:"
    const val TAG_STATE = "owner-send-state"
    const val TAG_DIAGNOSIS = "owner-send-diagnosis"

    fun statusLine(grants: OwnerSendGrants, channel: SendChannel): String = channel.settingLabel + ": " +
        (grants.grantedOn(channel)?.let { "EIN (seit $it)" } ?: "AUS – nur Entwürfe")

    fun show(activity: Activity, grants: OwnerSendGrants, accessibilityOn: () -> Boolean, contactsReadable: () -> Boolean,
             startDiagnosis: () -> Unit, lastDiagnosis: () -> String?): AlertDialog {
        val cockpit = ContinuumBentoCockpit
        val box = cockpit.panel(activity, "CONTINUUM · SELBST SENDEN", "Selbst senden",
            "Standardmäßig aus: Ohne Freigabe bereitet Continuum nur Entwürfe vor und du sendest selbst. " +
                "Mit Freigabe sendet Continuum auf deinen ausdrücklichen Auftrag ohne weitere Rückfrage.")
        box.tag = "continuum-owner-send"
        lateinit var dialog: AlertDialog
        box.addView(cockpit.action(activity, "ZURÜCK ZUR APP") { dialog.dismiss() })
        val rows = cockpit.column(activity)
        box.addView(rows)
        fun render() {
            rows.removeAllViews()
            SendChannel.entries.forEach { channel ->
                val on = grants.enabled(channel)
                rows.addView(cockpit.section(activity, statusLine(grants, channel)).apply { tag = TAG_STATUS + channel.name })
                rows.addView(cockpit.action(activity,
                    if (on) "FREIGABE WIDERRUFEN · ${channel.label}" else "EINSCHALTEN · ${channel.label}",
                    primary = !on, critical = on) {
                    if (on) {
                        grants.change(channel, false, OwnerSendOwnerTap())
                        render()
                    } else AlertDialog.Builder(activity).setTitle(channel.settingLabel + "?")
                        .setMessage(OwnerSendGrants.grantDialogText(channel))
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Ja, selbst senden") { _, _ ->
                            grants.change(channel, true, OwnerSendOwnerTap())
                            render()
                        }.show()
                }.apply { tag = TAG_TOGGLE + channel.name })
            }
            rows.addView(cockpit.section(activity,
                "Heute selbst gesendet: ${grants.sentToday()} von ${OwnerSendGrants.DAILY_CAP} · " +
                    "Bedienungshilfe: ${if (accessibilityOn()) "ein" else "AUS – in den Android-Einstellungen einschalten"} · " +
                    "Kontakte: ${if (contactsReadable()) "lesbar" else "noch nicht freigegeben (Android fragt beim ersten Auftrag)"}")
                .apply { tag = TAG_STATE })
            rows.addView(cockpit.action(activity, "SENDE-DIAGNOSE STARTEN\nKennungen der Bedienelemente aufzeichnen, kein Text") {
                dialog.dismiss()
                startDiagnosis()
            }.apply { tag = TAG_DIAGNOSIS })
            lastDiagnosis()?.let { report ->
                rows.addView(cockpit.action(activity, "LETZTE SENDE-DIAGNOSE ANZEIGEN") { showDiagnosis(activity, report) })
            }
        }
        render()
        dialog = cockpit.show(activity, box)
        return dialog
    }

    /** The diagnosis stays on the device; the owner may copy it to pass it on himself. */
    fun showDiagnosis(activity: Activity, report: String): AlertDialog {
        val text = TextView(activity).apply {
            this.text = report
            setTextIsSelectable(true)
            textSize = 11f
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val scroll = android.widget.ScrollView(activity).apply {
            addView(text, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        return AlertDialog.Builder(activity).setTitle("Sende-Diagnose (bleibt auf dem Gerät)").setView(scroll)
            .setNegativeButton("Schließen", null)
            .setPositiveButton("Kopieren") { _, _ ->
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                    ?.setPrimaryClip(ClipData.newPlainText("Continuum Sende-Diagnose", report))
            }.show()
    }
}
