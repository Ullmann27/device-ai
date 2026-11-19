package at.heinz.deviceai.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout

/** Navigation only; existing controls apply. */
object ContinuumSettingsHub {
    enum class Entry(val group: String, val label: String, val detail: String) {
        HOLOGRAM("Darstellung & Stimme", "Hologramm & Neonlicht", "Lichtstärke und sanfte Lichtbewegung"),
        VOICE("Darstellung & Stimme", "Stimme auswählen", "Maya / ElevenLabs oder Android offline"),
        VOICE_SETUP("Darstellung & Stimme", "Sprache, Ton & Gespräch", "Mikrofon, Hörtest und Startbegleitung"),
        TONE("Darstellung & Stimme", "Gesprächston", "Persönlich oder sachlich, Anrede und Begrüßung"),
        FACE("Darstellung & Stimme", "Kamera & Gesicht", "Blick, lokale Wiedererkennung und Freigaben"),
        MODEL("KI & Gedächtnis", "Lokales KI-Modell", "Modell auswählen, laden und verwalten"),
        BRAIN("KI & Gedächtnis", "Gehirn: Lokal oder Claude", "Modell auf dem Gerät oder Claude in der Cloud"),
        MEMORY("KI & Gedächtnis", "Gedächtnis & Fakten", "Erinnerungen ansehen, berichtigen und löschen"),
        LEARNING("KI & Gedächtnis", "Lernen & Training", "Lernzentrum und vorhandene Trainingsoptionen"),
        PERSONAL("KI & Gedächtnis", "Personalisierung", "Persönliche Fakten und Lernzugänge"),
        CONNECTIONS("Internet & Verbindungen", "Verbindungen", "Google Gmail, Kalender, Kontakte, Dateien, WhatsApp, Internet, Benachrichtigungen"),
        INTERNET("Internet & Verbindungen", "Internetfreigabe", "Offline, nachfragen oder freigegeben"),
        RESEARCH("Internet & Verbindungen", "Recherche & Datenschutz", "Recherchemodus und bestehende Freigaben"),
        CLOUD("Internet & Verbindungen", "Cloud-Backend", "Server und Verbindungsoptionen"),
        OPENROUTER("Internet & Verbindungen", "OpenRouter API-Schlüssel", "Gespeicherten Zugang verwalten"),
        SYSTEM("Gerät & Kontrolle", "System & Berechtigungen", "Gerätestatus, Dienste und weitere Zugänge"),
        AUTONOMY("Gerät & Kontrolle", "Autonomie & Not-Stopp", "Berechtigte Aktionen und Autonomiestufe"),
        SELF_SEND("Gerät & Kontrolle", "Selbst senden", "WhatsApp und Gmail: Freigabe, Widerruf, Sende-Diagnose"),
        INCOMING_MESSAGES("Gerät & Kontrolle", "Eingehende Nachrichten melden",
            "WhatsApp und Gmail: bei neuer Nachricht fragen, vorlesen, antworten – ohne Werbung"),
        STORAGE("Gerät & Kontrolle", "Speicher & Bereinigung", "Speicher prüfen, sicher bereinigen, Auswahl, Protokoll, Wiederherstellen"),
        SHIZUKU("Gerät & Kontrolle", "Shizuku-Verbindung", "Vorhandenen Gerätezugriff verbinden"),
        DIAGNOSTICS("Gerät & Kontrolle", "Diagnose & Protokolle", "Status und Fehlerberichte ansehen"),
        UPDATES("Gerät & Kontrolle", "App-Updates", "Verfügbare Continuum-Versionen prüfen"),
        CINEMA("Weitere Darstellung", "Cinema-Effekte", "Effekte der klassischen Ansicht einstellen"),
        AVATAR("Weitere Darstellung", "Klassischer Avatar", "Kugel oder Kopf der klassischen Ansicht"),
        CLASSIC("Weitere Darstellung", "Klassische Ansicht öffnen", "Zur bisherigen Oberfläche wechseln"),
        WORK_AREAS("Weitere Bereiche", "Alle Arbeitsbereiche", "Weitere vorhandene Funktionen öffnen")
    }

    fun show(activity: Activity, onSelect: (Entry) -> Unit): AlertDialog {
        val cockpit = ContinuumBentoCockpit
        val box = cockpit.panel(activity, "CONTINUUM · STEUERUNG", "Alle Einstellungen",
            "Wähle einen Bereich oder suche nach einer Einstellung.")
        box.tag = "continuum-settings-hub"
        lateinit var dialog: AlertDialog
        box.addView(cockpit.action(activity, "ZURÜCK ZUR APP") { dialog.dismiss() })
        val search = EditText(activity).apply {
            tag = "continuum-settings-search"
            hint = "Einstellungen suchen …"
            contentDescription = "Einstellungen suchen"
            setTextColor(Color.rgb(231, 249, 255))
            setHintTextColor(Color.rgb(158, 190, 210))
            setSingleLine(true)
            minimumHeight = (48 * resources.displayMetrics.density).toInt()
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        box.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val results = cockpit.column(activity).apply { tag = "continuum-settings-results" }
        box.addView(results)
        fun render(query: String) {
            results.removeAllViews()
            val words = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            val entries = Entry.entries.filter { entry ->
                val text = entry.group + " " + entry.label + " " + entry.detail
                words.all { text.contains(it, ignoreCase = true) }
            }
            if (entries.isEmpty()) {
                results.addView(cockpit.section(activity, "Keine passende Einstellung. Suche kürzer."))
            }
            entries.groupBy(Entry::group).forEach { (group, items) ->
                results.addView(cockpit.section(activity, group))
                val buttons = items.map { entry ->
                    cockpit.action(activity, entry.label + "\n" + entry.detail,
                        primary = entry == Entry.HOLOGRAM) {
                        dialog.dismiss()
                        onSelect(entry)
                    }.apply { tag = "continuum-setting:" + entry.name }
                }
                cockpit.grid(activity, results, buttons, 2)
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        render("")
        box.isFocusableInTouchMode = true
        box.requestFocus()
        dialog = cockpit.show(activity, box)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        return dialog
    }
}
