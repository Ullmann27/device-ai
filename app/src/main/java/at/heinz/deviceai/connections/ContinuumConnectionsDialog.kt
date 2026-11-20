package at.heinz.deviceai.connections

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import at.heinz.deviceai.gmail.GmailCapability
import at.heinz.deviceai.gmail.GmailConnection
import at.heinz.deviceai.gmail.GmailText
import at.heinz.deviceai.ui.ContinuumBentoCockpit
import java.text.DateFormat
import java.util.Date

/** What the „Verbindungen“ pages may do – each an existing path of the Activity. */
internal interface ContinuumConnectionsActions {
    fun rows(): List<ContinuumConnection>
    /** The row's one action for [kind]. */
    fun perform(kind: ContinuumConnectionKind, action: ContinuumConnectionAction)
    fun gmail(): GmailConnection
    /** Google's consent for exactly [capability]; [done] with the sentence to show. */
    fun connectGmail(capability: GmailCapability, done: (String) -> Unit)
    fun disconnectGmail(done: (String) -> Unit)
    /** false while another job occupies the single work lane: the Gmail buttons then say so instead of starting. */
    fun idle(): Boolean
}

/**
 * Settings → „Verbindungen“: every connection with its real state, account, purpose, last use, last error and the one
 * button that applies; and the page „Google Gmail“ with „Konto verbinden“, the separate rights, the cloud switch and
 * „Trennen“. Every row is built from the checks at the moment the page opens or after an action.
 */
internal object ContinuumConnectionsDialog {
    const val TAG = "continuum-connections"
    const val TAG_ROW = "connection-row:"
    const val TAG_ACTION = "connection-action:"
    const val TAG_GMAIL = "continuum-gmail"
    const val TAG_ARGOS_ATLAS = "connection-argos-atlas"
    private const val ARGOS_ATLAS_URL = "https://www.argosatlas.com/"

    fun show(activity: Activity, actions: ContinuumConnectionsActions): AlertDialog {
        val cockpit = ContinuumBentoCockpit
        val box = cockpit.panel(activity, "CONTINUUM · VERBINDUNGEN", "Verbindungen",
            "Was Continuum nutzen darf, mit welchem Konto und wie es gerade steht. Jede Taste öffnet die Stelle, an der du es änderst.")
        box.tag = TAG
        lateinit var dialog: AlertDialog
        box.addView(cockpit.action(activity, "ZURÜCK") { dialog.dismiss() })
        val rows = cockpit.column(activity)
        box.addView(rows)
        fun render() {
            rows.removeAllViews()
            actions.rows().forEach { row ->
                rows.addView(cockpit.section(activity, row.kind.title + " · " + row.state.label).apply { tag = TAG_ROW + row.kind.name })
                rows.addView(label(activity, lines(row), 13f))
                row.action?.let { action ->
                    rows.addView(cockpit.action(activity, action.label, primary = row.kind == ContinuumConnectionKind.GMAIL) {
                        if (action == ContinuumConnectionAction.OPEN_GMAIL) showGmail(activity, actions) { render() }
                        else { dialog.dismiss(); actions.perform(row.kind, action) }
                    }.apply { tag = TAG_ACTION + row.kind.name })
                }
            }
            rows.addView(cockpit.section(activity, "ARGOS ATLAS · EXTERN"))
            rows.addView(label(activity,
                "Weltkarte mit öffentlichen Kameras, Flugzeugen und Schiffen. " +
                    "Noch keine direkte Datenverbindung: API/MCP ist beim Anbieter angekündigt, " +
                    "aber hier nicht eingerichtet. Anmeldung nur auf der ARGOS-Website.", 13f))
            rows.addView(cockpit.action(activity, "ARGOS ATLAS IM BROWSER ÖFFNEN") {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ARGOS_ATLAS_URL)).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                    activity.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    AlertDialog.Builder(activity)
                        .setTitle("Kein Browser verfügbar")
                        .setMessage("Öffne " + ARGOS_ATLAS_URL + " in einem Browser.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }.apply { tag = TAG_ARGOS_ATLAS })
            rows.addView(cockpit.section(activity, ContinuumConnectionRegistry.DRIVE_NOTE))
        }
        render()
        dialog = cockpit.show(activity, box)
        return dialog
    }

    fun lines(row: ContinuumConnection): String = buildList {
        row.account?.let { add("Konto: $it") }
        add("Wofür: " + row.kind.usedFor)
        add(row.detail)
        add("Zuletzt verwendet: " + (row.lastUseMillis?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "noch nie"))
        row.lastError?.let { add("Letzter Fehler: $it") }
    }.joinToString("\n")

    /** The Gmail page. [changed] re-renders the overview after a change. */
    fun showGmail(activity: Activity, actions: ContinuumConnectionsActions, changed: () -> Unit = {}): AlertDialog {
        val cockpit = ContinuumBentoCockpit
        val box = cockpit.panel(activity, "CONTINUUM · VERBINDUNGEN", "Google Gmail",
            "Du meldest dich auf Googles eigener Seite an – Continuum fragt nie nach deinem Passwort. Jedes Recht gibst du einzeln frei; " +
                "Zugangsschlüssel bleiben nur im Arbeitsspeicher. Funktioniert nur in der original signierten App.")
        box.tag = TAG_GMAIL
        lateinit var dialog: AlertDialog
        box.addView(cockpit.action(activity, "ZURÜCK") { dialog.dismiss() })
        val body = cockpit.column(activity)
        box.addView(body)
        val status = label(activity, "", 13f)
        lateinit var render: () -> Unit
        fun run(start: ((String) -> Unit) -> Unit) {
            if (!actions.idle()) { status.text = "Gerade läuft ein anderer Auftrag. Versuch es gleich noch einmal."; return }
            status.text = "Google öffnet seine Freigabeseite …"
            start { line -> status.text = line; render(); changed() }
        }
        render = {
            body.removeAllViews()
            val gmail = actions.gmail()
            val row = actions.rows().firstOrNull { it.kind == ContinuumConnectionKind.GMAIL }
            body.addView(cockpit.section(activity, "Status: " + (row?.state?.label ?: "unbekannt")))
            body.addView(label(activity, buildList {
                add("Konto: " + (gmail.account ?: if (gmail.connected) "Adresse noch nicht abgerufen" else "keines verbunden"))
                GmailCapability.entries.forEach { add(it.label + ": " + (if (gmail.grant.granted(it)) "freigegeben" else "nicht freigegeben") + " – " + it.right) }
                add(GmailText.CLOUD_SWITCH + ": " + if (gmail.cloudContent) "an" else "aus")
                row?.lastError?.let { add("Letzter Fehler: $it") }
            }.joinToString("\n"), 13f))
            body.addView(status)
            val buttons = ArrayList<android.view.View>()
            buttons += cockpit.action(activity, if (gmail.connected) "Erneut verbinden" else "Konto verbinden", primary = !gmail.connected) {
                run { done -> actions.connectGmail(GmailCapability.READ, done) }
            }.apply { tag = "gmail-connect" }
            if (gmail.connected) {
                if (!gmail.grant.granted(GmailCapability.DRAFTS)) buttons += cockpit.action(activity, "Entwürfe erlauben") {
                    run { done -> actions.connectGmail(GmailCapability.DRAFTS, done) }
                }.apply { tag = "gmail-drafts" }
                if (!gmail.grant.granted(GmailCapability.SEND)) buttons += cockpit.action(activity, "Senden erlauben") {
                    AlertDialog.Builder(activity).setTitle("Senden erlauben").setMessage(
                        "Continuum sendet trotzdem nie von selbst: Vor jeder Mail zeigt es dir Empfänger, Betreff und den ganzen Text und " +
                            "sendet erst nach deinem „Ja, senden“. " + GmailText.COMPOSE_NOTE)
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Weiter zu Google") { _, _ -> run { done -> actions.connectGmail(GmailCapability.SEND, done) } }
                        .show()
                }.apply { tag = "gmail-send" }
                buttons += cockpit.action(activity, GmailText.CLOUD_SWITCH + " · " + if (gmail.cloudContent) "an" else "aus") {
                    if (gmail.cloudContent) { gmail.cloudContent = false; render(); changed() }
                    else AlertDialog.Builder(activity).setTitle(GmailText.CLOUD_SWITCH).setMessage(GmailText.CLOUD_SWITCH_DETAIL +
                        "\n\nEinschalten heißt: Wenn Claude als Gehirn gewählt ist und du eine Zusammenfassung verlangst, geht der Text der " +
                        "betroffenen Mails an Anthropic. Ausschalten kannst du es hier jederzeit.")
                        .setNegativeButton("Aus lassen", null)
                        .setPositiveButton("Einschalten") { _, _ -> gmail.cloudContent = true; render(); changed() }
                        .show()
                }.apply { tag = "gmail-cloud" }
                buttons += cockpit.action(activity, "Trennen", critical = true) {
                    AlertDialog.Builder(activity).setTitle("Gmail trennen")
                        .setMessage("Continuum widerruft bei Google alle Rechte und löscht Konto und Freigaben aus der App.")
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Trennen") { _, _ -> run { done -> actions.disconnectGmail(done) } }
                        .show()
                }.apply { tag = "gmail-disconnect" }
            }
            cockpit.grid(activity, body, buttons, 2)
            body.addView(label(activity, "Widerrufen kannst du den Zugriff auch selbst unter myaccount.google.com/permissions. " +
                GmailText.SETUP_HINT, 12f))
        }
        render()
        dialog = cockpit.show(activity, box)
        return dialog
    }

    private fun label(activity: Activity, value: String, size: Float): android.widget.TextView = android.widget.TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(android.graphics.Color.rgb(214, 236, 246))
        setLineSpacing(0f, 1.15f)
        setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
    }
}
