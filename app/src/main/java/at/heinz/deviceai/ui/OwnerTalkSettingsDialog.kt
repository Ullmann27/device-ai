package at.heinz.deviceai.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import at.heinz.deviceai.OwnerTalk
import at.heinz.deviceai.OwnerTalkStore
import at.heinz.deviceai.cognitive.OwnerConversationTone

/**
 * „Gesprächston“ in the settings hub: Persönlich · Sachlich, the greeting when the owner gets in touch, how he is
 * addressed and whether his name may be handed to the cloud brain. Every change is the owner's own tap here (or his
 * spoken sentence, see [OwnerTalk.handle]); the texts say exactly what each switch does.
 */
internal object OwnerTalkSettingsDialog {
    const val TAG_TONE = "owner-talk-tone:"
    const val TAG_GREETING = "owner-talk-greeting"
    const val TAG_NAME = "owner-talk-name"
    const val TAG_NAME_CLOUD = "owner-talk-name-cloud"
    const val TAG_STATE = "owner-talk-state"

    const val INTRO = "Persönlich: locker, per Du, wie unter alten Freunden – ehrlich bleibt es trotzdem, und Freigaben und " +
        "Fehler bleiben eindeutig. Sachlich: die nüchterne Art wie bisher, ohne Begrüßung. Die Einstellung gilt für das " +
        "Modell auf dem Gerät und für das Cloud-Gehirn."
    const val NAME_CLOUD_QUESTION = "Dein Name steht dann in den Arbeitsanweisungen, die bei jeder Anfrage an das Cloud-Gehirn " +
        "(Anthropic) gesendet werden, damit es dich damit anspricht. Ohne diese Freigabe bleibt dein Name auf dem Gerät; " +
        "die Begrüßung der App nennt ihn trotzdem. Du kannst die Freigabe hier jederzeit zurücknehmen."

    fun toneLine(store: OwnerTalkStore): String = "Gesprächston: " + store.tone.label
    fun greetingLine(store: OwnerTalkStore): String = "Begrüßung, wenn du dich nach einer Pause meldest: " +
        when {
            store.tone != OwnerConversationTone.PERSONAL -> "aus (im Gesprächston „Sachlich“ gibt es keine)"
            store.returnGreeting -> "EIN"
            else -> "AUS"
        }
    fun nameCloudLine(store: OwnerTalkStore): String = "Name an das Cloud-Gehirn: " +
        if (store.nameToCloud) "FREIGEGEBEN" else "nicht freigegeben – der Name bleibt auf dem Gerät"

    fun show(activity: Activity, store: OwnerTalkStore, talk: OwnerTalk): AlertDialog {
        val cockpit = ContinuumBentoCockpit
        val box = cockpit.panel(activity, "CONTINUUM · GESPRÄCH", "Gesprächston", INTRO)
        box.tag = "continuum-owner-talk"
        lateinit var dialog: AlertDialog
        box.addView(cockpit.action(activity, "ZURÜCK ZUR APP") { dialog.dismiss() })
        val rows = cockpit.column(activity)
        box.addView(rows)
        fun render() {
            rows.removeAllViews()
            rows.addView(cockpit.section(activity, toneLine(store)).apply { tag = TAG_STATE })
            OwnerConversationTone.entries.forEach { tone ->
                rows.addView(cockpit.action(activity, (if (store.tone == tone) "✓ " else "") + tone.label.uppercase(),
                    primary = store.tone == tone) { store.tone = tone; render() }.apply { tag = TAG_TONE + tone.name })
            }
            rows.addView(cockpit.section(activity, greetingLine(store)))
            if (store.tone == OwnerConversationTone.PERSONAL) rows.addView(cockpit.action(activity,
                if (store.returnGreeting) "BEGRÜSSUNG AUSSCHALTEN" else "BEGRÜSSUNG EINSCHALTEN") {
                store.returnGreeting = !store.returnGreeting
                render()
            }.apply { tag = TAG_GREETING })
            rows.addView(cockpit.section(activity, "Anrede: " + (talk.name() ?: "ohne Namen") + " · " + talk.nameSource()))
            rows.addView(cockpit.action(activity, "NAMEN EINTRAGEN ODER ÄNDERN") {
                val field = EditText(activity).apply {
                    setSingleLine(true)
                    hint = "So soll ich dich nennen"
                    setText(store.name?.name.orEmpty())
                    tag = TAG_NAME
                }
                AlertDialog.Builder(activity).setTitle("Wie soll ich dich nennen?").setView(field)
                    .setNegativeButton("Abbrechen", null)
                    .setNeutralButton("Namen löschen") { _, _ -> talk.forgetName(); render() }
                    .setPositiveButton("Speichern") { _, _ ->
                        if (!talk.typeName(field.text.toString())) AlertDialog.Builder(activity)
                            .setMessage("Das habe ich nicht als Namen erkannt; es wurde nichts geändert.")
                            .setPositiveButton("OK", null).show()
                        render()
                    }.show()
            })
            rows.addView(cockpit.section(activity, nameCloudLine(store)))
            rows.addView(cockpit.action(activity, if (store.nameToCloud) "FREIGABE ZURÜCKNEHMEN · NAME BLEIBT AUF DEM GERÄT"
                else "NAMEN FÜR DAS CLOUD-GEHIRN FREIGEBEN", critical = store.nameToCloud) {
                if (store.nameToCloud) { store.nameToCloud = false; render() }
                else AlertDialog.Builder(activity).setTitle("Namen an das Cloud-Gehirn geben?")
                    .setMessage(NAME_CLOUD_QUESTION)
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Ja, freigeben") { _, _ -> store.nameToCloud = true; render() }.show()
            }.apply { tag = TAG_NAME_CLOUD })
        }
        render()
        dialog = cockpit.show(activity, box)
        return dialog
    }
}
