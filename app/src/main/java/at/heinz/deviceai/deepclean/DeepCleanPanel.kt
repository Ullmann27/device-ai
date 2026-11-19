package at.heinz.deviceai.deepclean

import android.app.Activity
import android.app.AlertDialog
import android.util.TypedValue
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import at.heinz.deviceai.ui.ContinuumBentoCockpit

/**
 * „Speicher & Bereinigung“ – the native screen of the storage lane. Every button starts the same deterministic order
 * a sentence would start ([DeepCleanDirector.start]); the result appears in the chat and here. The selection shows
 * path, size, origin and recoverability of every offered file; nothing is preselected.
 */
internal object DeepCleanPanel {
    const val TAG = "continuum-deepclean"

    private fun line(activity: Activity, text: String, size: Float = 14f): TextView = TextView(activity).apply {
        this.text = text
        setTextColor(0xFFF1F5FF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setPadding(8, 6, 8, 6)
    }

    fun show(activity: Activity, director: DeepCleanDirector, host: DeepCleanHost): AlertDialog {
        val cockpit = ContinuumBentoCockpit
        val box = cockpit.panel(activity, "CONTINUUM · GERÄT", "Speicher & Bereinigung",
            "Prüfen löscht nichts. „Sicher bereinigen“ entfernt nur Continuums eigene, eindeutig entbehrliche Zwischendateien. " +
                "Alles andere nur, wenn du es hier auswählst – und Android fragt dann selbst noch einmal.")
        box.tag = TAG
        lateinit var dialog: AlertDialog
        box.addView(cockpit.action(activity, "ZURÜCK ZUR APP") { dialog.dismiss() })

        fun run(label: String, mode: DeepCleanMode) {
            if (director.busy || !host.idle()) { Toast.makeText(activity, "Gerade läuft noch ein Auftrag.", Toast.LENGTH_SHORT).show(); return }
            dialog.dismiss()
            director.start(label, DeepCleanCommand(mode))
        }

        val access = host.access()
        box.addView(cockpit.section(activity, "Zugriff"))
        box.addView(line(activity, when (access.shared) {
            DeepCleanAccess.Shared.GRANTED -> "Gemeinsamer Speicher: freigegeben („Zugriff auf alle Dateien“)."
            DeepCleanAccess.Shared.MISSING -> "Gemeinsamer Speicher: nicht freigegeben – geprüft werden nur Continuums eigene Dateien."
            DeepCleanAccess.Shared.UNSUPPORTED -> "Gemeinsamer Speicher: auf diesem Android nicht lesbar."
        }))
        box.addView(line(activity, "Speicher anderer Apps: nur in den Android-Einstellungen sichtbar (keine Berechtigung dafür).", 12f))
        host.volume()?.let { box.addView(line(activity, "Frei: ${DeepCleanText.bytes(it.freeBytes)} von ${DeepCleanText.bytes(it.totalBytes)} (Android-Messung, jetzt)")) }

        box.addView(cockpit.section(activity, "Aufträge"))
        val actions = listOf(
            cockpit.action(activity, "SPEICHER PRÜFEN\nnur ansehen, nichts löschen", primary = true) { run("Speicher prüfen", DeepCleanMode.ANALYSE) },
            cockpit.action(activity, "SICHER BEREINIGEN\nnur Continuums eigene Zwischendateien") { run("Sicher bereinigen", DeepCleanMode.SAFE_CLEAN) },
            cockpit.action(activity, "TIEFENBEREINIGUNG\nsicher bereinigen + Auswahl großer Dateien und Duplikate") {
                run("Tiefenbereinigung", DeepCleanMode.DEEP_CLEAN) },
            cockpit.action(activity, "GERÄT OPTIMIEREN\nTemperatur und Last von Continuum") { run("Gerät optimieren", DeepCleanMode.OPTIMISE) },
            cockpit.action(activity, "WIEDERHERSTELLEN\naus dem Android-Papierkorb") { run("Wiederherstellen", DeepCleanMode.RESTORE) },
            cockpit.action(activity, "WIE VIEL WURDE FREI?\nletztes gemessenes Ergebnis") { run("Wie viel Platz wurde frei?", DeepCleanMode.REPORT) })
        cockpit.grid(activity, box, actions, 2)
        val settings = ArrayList<android.view.View>()
        if (access.shared == DeepCleanAccess.Shared.MISSING) settings += cockpit.action(activity, "ZUGRIFF AUF ALLE DATEIEN\nin Android einschalten") {
            if (!host.openFileAccessSettings()) Toast.makeText(activity, "Die Android-Einstellung ließ sich nicht öffnen.", Toast.LENGTH_LONG).show()
        }
        settings += cockpit.action(activity, "ANDROID-SPEICHERVERWALTUNG\nApps und Caches in den Einstellungen") {
            if (!host.openStorageSettings()) Toast.makeText(activity, "Android bietet diese Seite hier nicht an.", Toast.LENGTH_LONG).show()
        }
        cockpit.grid(activity, box, settings, 2)

        director.lastSummary?.let { summary ->
            box.addView(cockpit.section(activity, "Letztes Ergebnis"))
            box.addView(line(activity, summary, 13f))
        }

        val offered = director.offered()
        if (offered.isNotEmpty()) {
            box.addView(cockpit.section(activity, if (director.offeredReadOnly()) "Gefunden (nur zum Ansehen)" else "Zur Auswahl – nichts ist vorausgewählt"))
            val checks = offered.mapIndexed { index, item ->
                CheckBox(activity).apply {
                    text = DeepCleanText.line(index + 1, item, director.finding(item))
                    setTextColor(0xFFF1F5FF.toInt())
                    isEnabled = !director.offeredReadOnly()
                    tag = "deepclean-item:" + item.id
                }.also { box.addView(it) }
            }
            if (!director.offeredReadOnly()) box.addView(cockpit.action(activity, "AUSGEWÄHLTE IN DEN PAPIERKORB", critical = true) {
                val chosen = offered.filterIndexed { index, _ -> checks[index].isChecked }
                if (chosen.isEmpty()) { Toast.makeText(activity, "Du hast nichts ausgewählt.", Toast.LENGTH_SHORT).show(); return@action }
                director.choose("Auswahl in Speicher & Bereinigung", chosen)
                AlertDialog.Builder(activity).setTitle("In den Papierkorb verschieben?")
                    .setMessage("${DeepCleanText.files(chosen.size)}, zusammen ${DeepCleanText.bytes(chosen.sumOf { it.sizeBytes })}. " +
                        "Android fragt danach selbst noch einmal. Wiederherstellen geht, bis Android den Papierkorb leert.")
                    .setNegativeButton("Nein") { _, _ -> }
                    .setPositiveButton("Ja, verschieben") { _, _ -> dialog.dismiss(); director.confirmChoice("Ja, verschieben") }
                    .show()
            })
        }

        val log = director.log()
        box.addView(cockpit.section(activity, "Protokoll (was wirklich geschah)"))
        if (log.isEmpty()) box.addView(line(activity, "Noch nichts entfernt.", 12f))
        else log.forEach { box.addView(line(activity, it, 12f)) }
        box.addView(LinearLayout(activity))
        dialog = cockpit.show(activity, box)
        return dialog
    }
}
