package at.heinz.deviceai.notifications

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import at.heinz.deviceai.ui.ContinuumBentoCockpit
import java.util.Locale

/**
 * „Eingehende Nachrichten melden“ in the settings: the owner's switch (off by default), his app list and his options.
 * Switching on shows in plain words what Continuum then sees and needs an explicit yes; switching off takes effect
 * at once and forgets what was held. Nothing here asks Android for a permission.
 */
internal object IncomingMessageSettingsDialog {
    const val TAG_STATUS = "incoming-status"
    const val TAG_TOGGLE = "incoming-toggle"
    const val TAG_SERVICE = "incoming-service"
    const val TAG_APPS = "incoming-apps"
    const val TAG_OPTION = "incoming-option:"
    const val TAG_ALWAYS = "incoming-always"
    const val TAG_NEVER = "incoming-never"
    const val TAG_CLEAR = "incoming-clear"

    fun statusLine(settings: IncomingMessageSettings): String = "Eingehende Nachrichten melden: " +
        if (settings.enabled) "EIN" else "AUS – Continuum sieht keine Benachrichtigung"

    /** Installed apps with a launcher entry, as package and label; the owner picks from these. */
    fun launchableApps(activity: Activity): List<Pair<String, String>> = runCatching {
        val packages = activity.packageManager
        packages.getInstalledApplications(0)
            .filter { it.packageName != activity.packageName && packages.getLaunchIntentForPackage(it.packageName) != null }
            .map { it.packageName to it.loadLabel(packages).toString().take(40) }
    }.getOrDefault(emptyList())

    fun serviceLine(accessibilityOn: Boolean, listening: Boolean, listener: Boolean): String = when {
        listener -> "Signal: Benachrichtigungsdienst dieser Installation ist verbunden."
        !accessibilityOn -> "Signal: Die Continuum-Bedienungshilfe ist AUS – ohne sie kommt kein Signal an. " +
            "Schalte sie in den Android-Einstellungen ein."
        listening -> "Signal: Continuum-Bedienungshilfe ist ein und empfängt Benachrichtigungs-Ereignisse."
        else -> "Signal: Continuum-Bedienungshilfe ist ein; Benachrichtigungs-Ereignisse sind noch nicht angefordert."
    }

    /**
     * [installedApps]: package and label of the apps the owner can choose from. [accessibilityOn]: the owner's
     * accessibility service is enabled. [changed] runs after every stored change.
     */
    fun show(activity: Activity, accessibilityOn: () -> Boolean,
             installedApps: () -> List<Pair<String, String>> = { launchableApps(activity) }, changed: () -> Unit = {}): AlertDialog {
        val cockpit = ContinuumBentoCockpit
        val box = cockpit.panel(activity, "CONTINUUM · NACHRICHTEN", "Eingehende Nachrichten melden",
            "Standardmäßig aus. Eingeschaltet fragt Continuum bei einer neuen wichtigen Nachricht, ob es sie vorlesen soll – " +
                "Werbung wird nicht gemeldet. Nichts verlässt das Gerät.")
        box.tag = "continuum-incoming-messages"
        lateinit var dialog: AlertDialog
        box.addView(cockpit.action(activity, "ZURÜCK ZUR APP") { dialog.dismiss() })
        val rows = cockpit.column(activity)
        box.addView(rows)
        lateinit var render: () -> Unit
        // The full app list is read only when the owner asks for it (it costs one system call per app).
        var loaded: List<Pair<String, String>>? = null
        fun available(): List<Pair<String, String>> = loaded ?: installedApps().also { loaded = it }
        fun label(app: String): String = loaded?.firstOrNull { it.first == app }?.second
            ?: IncomingMessages.label(activity, app).ifEmpty { app }
        fun store(change: (IncomingMessageSettings) -> IncomingMessageSettings) {
            IncomingMessagePreferences.update(activity, change)
            IncomingMessages.settingsChanged(activity)
            changed()
            render()
        }
        fun option(key: String, label: String, on: Boolean, change: (IncomingMessageSettings) -> IncomingMessageSettings) =
            rows.addView(cockpit.action(activity, "$label · " + if (on) "an" else "aus") { store(change) }.apply { tag = TAG_OPTION + key })
        fun editList(title: String, hint: String, current: List<String>, save: (IncomingMessageSettings, List<String>) -> IncomingMessageSettings) {
            val field = EditText(activity).apply {
                setText(current.joinToString("\n")); this.hint = hint; minLines = 3; tag = "incoming-list-field"
            }
            AlertDialog.Builder(activity).setTitle(title).setMessage("Ein Eintrag je Zeile: Name des Absenders, der Gruppe oder ein Wort aus dem Betreff.")
                .setView(field).setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern") { _, _ ->
                    val entries = field.text.toString().split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                    store { save(it, entries) }
                }.show()
        }
        render = {
            rows.removeAllViews()
            val settings = IncomingMessagePreferences.load(activity)
            rows.addView(cockpit.section(activity, statusLine(settings)).apply { tag = TAG_STATUS })
            rows.addView(cockpit.action(activity, if (settings.enabled) "AUSSCHALTEN" else "EINSCHALTEN",
                primary = !settings.enabled, critical = settings.enabled) {
                if (settings.enabled) store { it.copy(enabled = false) }
                else AlertDialog.Builder(activity).setTitle("Eingehende Nachrichten melden?")
                    .setMessage(IncomingMessagePreferences.EXPLANATION)
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Ja, einschalten") { _, _ ->
                        val installed = available().map { it.first }.toSet()
                        store { current ->
                            // First time: the three suggested apps that are installed; afterwards the owner's own list.
                            current.copy(enabled = true, apps = current.apps.ifEmpty {
                                IncomingMessagePreferences.SUGGESTED_APPS.filter { it in installed }
                            })
                        }
                    }.show()
            }.apply { tag = TAG_TOGGLE })
            rows.addView(cockpit.section(activity, serviceLine(accessibilityOn(), AccessibilityNotificationSource.listening(),
                ContinuumNotificationBus.listenerConnected)).apply { tag = TAG_SERVICE })
            if (!accessibilityOn() && !ContinuumNotificationBus.listenerConnected) rows.addView(cockpit.action(activity,
                "BEDIENUNGSHILFE IN ANDROID ÖFFNEN") {
                runCatching { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.onFailure {
                    Toast.makeText(activity, "Die Android-Einstellung konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show()
                }
            })
            val chosen = settings.apps.map(::label)
            rows.addView(cockpit.action(activity, "APPS WÄHLEN\n" + if (chosen.isEmpty()) "noch keine App gewählt" else chosen.joinToString(", ")) {
                val apps = available().sortedWith(compareBy({ it.first !in IncomingMessagePreferences.SUGGESTED_APPS },
                    { it.second.lowercase(Locale.GERMAN) }))
                if (apps.isEmpty()) {
                    Toast.makeText(activity, "Die App-Liste konnte nicht gelesen werden.", Toast.LENGTH_LONG).show()
                    return@action
                }
                val checked = BooleanArray(apps.size) { apps[it].first in settings.apps }
                AlertDialog.Builder(activity).setTitle("Apps, deren Nachrichten gemeldet werden")
                    .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, index, on -> checked[index] = on }
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Übernehmen") { _, _ ->
                        store { it.copy(apps = apps.filterIndexed { index, _ -> checked[index] }.map { app -> app.first }) }
                    }.show()
            }.apply { tag = TAG_APPS })
            option("groups", "GRUPPEN-CHATS MELDEN", settings.groups) { it.copy(groups = !it.groups) }
            option("silent", "STUMME BENACHRICHTIGUNGEN MELDEN", settings.silentNotifications) {
                it.copy(silentNotifications = !it.silentNotifications)
            }
            option("unclear", "UNKLARE NACHRICHTEN KURZ MELDEN", settings.unclear == UnclearHandling.BRIEF) {
                it.copy(unclear = if (it.unclear == UnclearHandling.BRIEF) UnclearHandling.SILENT else UnclearHandling.BRIEF)
            }
            option("quiet", "RUHEZEIT 22 BIS 7 UHR", settings.quietHours) { it.copy(quietHours = !it.quietHours) }
            option("card", "HINWEIS-KARTE, WENN DIE APP NICHT OFFEN IST", settings.backgroundCard) {
                it.copy(backgroundCard = !it.backgroundCard)
            }
            option("voice", "KURZE ANSAGE, WENN DIE APP NICHT OFFEN IST", settings.backgroundVoice) {
                it.copy(backgroundVoice = !it.backgroundVoice)
            }
            rows.addView(cockpit.action(activity, "IMMER MELDEN\n" + settings.always.ifEmpty { listOf("keine Einträge") }.joinToString(", ")) {
                editList("Immer melden", "z. B. Bezirksgericht\nOrdination Dr. Steiner", settings.always) { current, entries ->
                    current.copy(always = entries)
                }
            }.apply { tag = TAG_ALWAYS })
            rows.addView(cockpit.action(activity, "NIE MELDEN\n" + settings.never.ifEmpty { listOf("keine Einträge") }.joinToString(", ")) {
                editList("Nie melden", "z. B. Newsletter\nLinkedIn", settings.never) { current, entries ->
                    current.copy(never = entries)
                }
            }.apply { tag = TAG_NEVER })
            if (settings.backgroundCard && !IncomingMessages.notificationsAllowed()) rows.addView(cockpit.section(activity,
                "Android-Benachrichtigungen für Continuum sind aus: Die Hinweis-Karte kann nicht erscheinen."))
            rows.addView(cockpit.action(activity, "OFFENE NACHRICHTEN LÖSCHEN") {
                IncomingMessages.clear()
                Toast.makeText(activity, "Offene Nachrichten gelöscht.", Toast.LENGTH_SHORT).show()
            }.apply { tag = TAG_CLEAR })
        }
        IncomingMessages.start(activity)
        render()
        dialog = cockpit.show(activity, box)
        return dialog
    }
}
