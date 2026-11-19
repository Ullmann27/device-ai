package at.heinz.deviceai.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import at.heinz.deviceai.localmodel.BrainKind
import at.heinz.deviceai.localmodel.ClaudeApiKey
import at.heinz.deviceai.localmodel.ClaudeBrain
import at.heinz.deviceai.localmodel.ClaudeBrainFailure
import at.heinz.deviceai.localmodel.ClaudeBrainSettings
import at.heinz.deviceai.localmodel.ClaudeConsentNotice
import at.heinz.deviceai.localmodel.ClaudeEffort
import at.heinz.deviceai.localmodel.ClaudeModel
import at.heinz.deviceai.localmodel.ClaudeModelRuntime
import at.heinz.deviceai.localmodel.ClaudePricing
import at.heinz.deviceai.localmodel.ClaudeServerToolTexts

/** Texts of the brain settings; pure, so they are checked without a device. */
object ClaudeBrainPresentation {
    const val LOCAL_LABEL = "Lokal (Gemma, auf dem Gerät)"
    const val CLAUDE_LABEL = "Claude (Cloud, eigener API-Schlüssel)"
    /** Status badge while Claude is the brain; the local badge keeps its existing texts. */
    const val BADGE = "● " + ClaudeBrain.MARKER_CLOUD
    /** "Modus" in the dashboards. */
    const val DASHBOARD_MODE = ClaudeBrain.MARKER_CLOUD

    fun status(settings: ClaudeBrainSettings, keyPresent: Boolean): String = buildString {
        append("Aktives Gehirn: ")
        append(if (settings.claudeSelected) "${ClaudeBrain.MARKER_CLOUD} · ${settings.model.id}"
            else "${ClaudeBrain.MARKER_LOCAL} · das Modell auf dem Gerät antwortet, nichts geht an Claude")
        append("\nAPI-Schlüssel: ").append(ClaudeApiKey.status(keyPresent))
        append("\nZustimmung zur Datenübertragung: ").append(if (settings.consentGiven) "erteilt" else "nicht erteilt")
        append("\nGerätelokale Erinnerungen mitsenden: ").append(if (settings.shareLocalMemory) "ja" else "nein")
        append("\nClaude-Modell: ").append(settings.model.label)
        append("\nDenktiefe: ").append(effortLabel(settings.effortOverride))
        if (settings.claudeSelected && !keyPresent)
            append("\n\nClaude ist gewählt, kann ohne Schlüssel aber nicht antworten. Ich schalte nicht von selbst um.")
    }

    fun effortLabel(effort: ClaudeEffort?): String =
        if (effort == null) "automatisch (Gespräch ${ClaudeEffort.DIALOGUE.label}, Arbeit ${ClaudeEffort.WORK.label})"
        else effort.label

    fun probe(result: ClaudeModelRuntime.Probe): String = when (result) {
        is ClaudeModelRuntime.Probe.Reachable -> "Verbindung in Ordnung: ${result.servedModel} hat geantwortet " +
            "(${result.promptTokens} Token Eingabe, ${result.outputTokens} Token Ausgabe)."
        is ClaudeModelRuntime.Probe.Failed -> when (result.problem) {
            ClaudeModelRuntime.BUSY -> "Es läuft gerade eine andere Anfrage. Bitte danach noch einmal testen."
            ClaudeModelRuntime.CANCELLED -> "Der Verbindungstest wurde abgebrochen."
            else -> "Verbindungstest fehlgeschlagen: " + ClaudeBrainFailure.ownerMessage(result.problem)
        }
    }

    /** Whole US dollars from the owner's input, or null when it is no usable amount. */
    fun limitCents(input: String): Int? = input.trim().toIntOrNull()?.let { it * 100 }?.takeIf {
        it in ClaudeBrainSettings.MIN_MONTHLY_LIMIT_CENTS..ClaudeBrainSettings.MAX_MONTHLY_LIMIT_CENTS
    }
}

/**
 * "Gehirn": local model or Claude, the Claude model, the depth of thinking, the owner's API key, the consent and
 * the monthly counter. It changes settings through [ClaudeBrain] only; a request leaves the device from here
 * solely when the owner taps "Verbindung testen".
 */
object ClaudeBrainDialog {
    interface Host {
        /** A model turn is running; the brain is not switched under it. */
        fun busy(): Boolean
        /** Selection or settings changed: refresh the markers; a switch to LOCAL may reload the local model. */
        fun brainChanged(previous: BrainKind)
        /** One line for the activity log; never a secret. */
        fun log(line: String)
        /** The owner's internet mode, for the status of the cloud web search; null when unknown. */
        fun internetMode(): at.heinz.deviceai.cognitive.InternetAccessMode? = null
    }

    fun show(activity: Activity, brain: ClaudeBrain, host: Host): AlertDialog {
        val cockpit = ContinuumBentoCockpit
        val box = cockpit.panel(activity, "CONTINUUM · GEHIRN", "Gehirn",
            "Wer antwortet: das Modell auf deinem Gerät oder Claude in der Cloud. „Lokal“ ist der Standard; " +
                "Claude braucht deinen eigenen API-Schlüssel und deine ausdrückliche Zustimmung.")
        box.tag = "claude-brain-dialog"
        lateinit var dialog: AlertDialog
        val status = note(activity, "claude-brain-status")
        val usage = note(activity, "claude-brain-usage")
        val cloudTools = note(activity, "claude-cloud-tools-status")
        val message = note(activity, "claude-brain-message").apply { visibility = View.GONE }
        val buttons = mutableMapOf<String, TextView>()

        fun say(text: String) {
            message.text = text
            message.visibility = View.VISIBLE
        }

        fun render() {
            val settings = brain.settings
            status.text = ClaudeBrainPresentation.status(settings, brain.hasKey())
            usage.text = brain.describeUsage() + "\n\n" + ClaudePricing.describe()
            fun mark(tag: String, label: String, selected: Boolean) {
                buttons[tag]?.apply {
                    text = (if (selected) "✓ " else "") + label
                    contentDescription = label + if (selected) ", ausgewählt" else ""
                    isSelected = selected
                }
            }
            mark("claude-brain-local", ClaudeBrainPresentation.LOCAL_LABEL, !settings.claudeSelected)
            mark("claude-brain-claude", ClaudeBrainPresentation.CLAUDE_LABEL, settings.claudeSelected)
            ClaudeModel.entries.forEach { mark("claude-model:" + it.id, it.label + "\n" + it.id, settings.model == it) }
            mark("claude-effort:auto", "Automatisch\nGespräch niedrig, Arbeit mittel", settings.effortOverride == null)
            ClaudeEffort.entries.forEach {
                mark("claude-effort:" + it.name, it.label.replaceFirstChar(Char::uppercase), settings.effortOverride == it)
            }
            mark("claude-share-memory", "Gerätelokale Erinnerungen mitsenden: " +
                (if (settings.shareLocalMemory) "EIN" else "AUS"), settings.shareLocalMemory)
            mark("claude-cloud-web", ClaudeServerToolTexts.switchLabel(ClaudeServerToolTexts.WEB_LABEL,
                settings.cloudWebSearch), settings.cloudWebSearch)
            mark("claude-cloud-code", ClaudeServerToolTexts.switchLabel(ClaudeServerToolTexts.CODE_LABEL,
                settings.cloudCodeExecution), settings.cloudCodeExecution)
            cloudTools.text = ClaudeServerToolTexts.status(settings, host.internetMode(), brain.hasKey()) + "\n\n" +
                brain.ledger.describeServerTools()
        }

        /**
         * One of the two cloud-tool switches. Off is immediate; on shows first what leaves the device and that it
         * costs extra API credit, and switches only when the owner confirms.
         */
        fun toggleCloudTool(label: String, explanation: String, on: Boolean, set: (Boolean) -> Boolean) {
            fun done(now: Boolean) {
                host.log("GEHIRN · $label: " + if (now) "EIN" else "AUS")
                render()
            }
            if (on) {
                if (set(false)) done(false)
                return
            }
            if (!brain.settings.consentGiven) return say("Das geht erst nach der Zustimmung zur Datenübertragung.")
            AlertDialog.Builder(activity).setTitle("$label einschalten?")
                .setMessage(explanation)
                .setNegativeButton("AUS LASSEN", null)
                .setPositiveButton("EINSCHALTEN") { _, _ ->
                    if (set(true)) done(true) else say("Die Einstellung konnte nicht gespeichert werden.")
                }.show()
        }

        fun changed(previous: BrainKind, line: String) {
            host.log(line)
            host.brainChanged(previous)
            render()
        }

        fun select(brainKind: BrainKind) {
            val previous = brain.settings.brain
            if (previous == brainKind) return render()
            if (host.busy()) return say("Ein Auftrag läuft noch. Beende ihn zuerst mit STOP, dann schalte um.")
            if (brainKind == BrainKind.LOCAL) {
                if (brain.selectBrain(BrainKind.LOCAL)) {
                    say("Lokal ist wieder das Gehirn. Ab jetzt geht keine Nachricht mehr an Claude.")
                    changed(previous, "GEHIRN · " + ClaudeBrain.MARKER_LOCAL)
                }
                return
            }
            if (!brain.hasKey()) {
                say("Speichere zuerst deinen eigenen Anthropic-API-Schlüssel. Ohne Schlüssel bleibt „Lokal“ aktiv.")
                return keyDialog(activity, brain, ::say) { render() }
            }
            val activate = {
                if (brain.selectBrain(BrainKind.CLAUDE)) {
                    say("Claude ist jetzt das Gehirn. Die Statuszeile zeigt „${ClaudeBrain.MARKER_CLOUD}“.")
                    changed(previous, "GEHIRN · " + ClaudeBrain.MARKER_CLOUD + " · " + brain.settings.model.id)
                } else say("Claude konnte nicht aktiviert werden; „Lokal“ bleibt aktiv.")
            }
            if (brain.settings.consentGiven) activate()
            else consentDialog(activity, brain, firstActivation = true, onChanged = { render() }, onConfirmed = activate)
        }

        box.addView(status, wide(activity, 10))
        box.addView(message, wide(activity, 8))

        box.addView(cockpit.section(activity, "GEHIRN WÄHLEN"), wide(activity, 10))
        cockpit.grid(activity, box, listOf(
            button(activity, buttons, "claude-brain-local") { select(BrainKind.LOCAL) },
            button(activity, buttons, "claude-brain-claude") { select(BrainKind.CLAUDE) }), 2)

        box.addView(cockpit.section(activity, "CLAUDE-MODELL"), wide(activity, 10))
        cockpit.grid(activity, box, ClaudeModel.entries.map { model ->
            button(activity, buttons, "claude-model:" + model.id) {
                if (host.busy()) say("Ein Auftrag läuft noch. Beende ihn zuerst mit STOP.")
                else if (brain.setModel(model)) changed(brain.settings.brain, "GEHIRN · Claude-Modell " + model.id)
            }
        }, 2)

        box.addView(cockpit.section(activity, "DENKTIEFE"), wide(activity, 10))
        cockpit.grid(activity, box, listOf(button(activity, buttons, "claude-effort:auto") {
            if (brain.setEffortOverride(null)) render()
        }) + ClaudeEffort.entries.map { effort ->
            button(activity, buttons, "claude-effort:" + effort.name) { if (brain.setEffortOverride(effort)) render() }
        }, 4)

        box.addView(cockpit.section(activity, "EIGENER API-SCHLÜSSEL"), wide(activity, 10))
        cockpit.grid(activity, box, listOf(
            cockpit.action(activity, "SCHLÜSSEL EINGEBEN", primary = true) {
                keyDialog(activity, brain, ::say) { render() }
            }.apply { tag = "claude-key-enter" },
            cockpit.action(activity, "VERBINDUNG TESTEN\neine kleine echte Anfrage") {
                if (!brain.hasKey()) say("Es ist kein Schlüssel gespeichert; es wurde nichts gesendet.")
                else {
                    say("Verbindung wird getestet …")
                    brain.runtime.probe { result ->
                        activity.runOnUiThread {
                            if (!activity.isFinishing) {
                                say(ClaudeBrainPresentation.probe(result))
                                render()
                            }
                        }
                    }
                }
            }.apply { tag = "claude-key-test" },
            cockpit.action(activity, "SCHLÜSSEL LÖSCHEN", critical = true) {
                AlertDialog.Builder(activity).setTitle("API-Schlüssel löschen?")
                    .setMessage("Der gespeicherte Schlüssel wird von diesem Gerät entfernt. In der Anthropic-Konsole " +
                        "bleibt er gültig, bis du ihn dort widerrufst.")
                    .setNegativeButton("ABBRECHEN", null)
                    .setPositiveButton("LÖSCHEN") { _, _ ->
                        say(if (brain.deleteKey()) "Der Schlüssel wurde von diesem Gerät gelöscht."
                            else "Der Schlüssel konnte nicht vollständig gelöscht werden.")
                        host.log("GEHIRN · API-Schlüssel gelöscht")
                        render()
                        if (brain.claudeSelected) AlertDialog.Builder(activity)
                            .setTitle("Auf Lokal zurückschalten?")
                            .setMessage("Claude bleibt als Gehirn gewählt, kann ohne Schlüssel aber nicht antworten. " +
                                "Ich schalte nicht von selbst um.")
                            .setNegativeButton("SO LASSEN", null)
                            .setPositiveButton("AUF LOKAL UMSCHALTEN") { _, _ -> select(BrainKind.LOCAL) }
                            .show()
                    }.show()
            }.apply { tag = "claude-key-delete" }), 3)

        box.addView(cockpit.section(activity, "DATENSCHUTZ"), wide(activity, 10))
        cockpit.grid(activity, box, listOf(
            cockpit.action(activity, "ZUSTIMMUNG ANSEHEN ODER WIDERRUFEN") {
                val before = brain.settings.brain
                consentDialog(activity, brain, firstActivation = false, onChanged = {
                    changed(before, "GEHIRN · Zustimmung widerrufen · " + ClaudeBrain.MARKER_LOCAL)
                }, onConfirmed = { render() })
            }.apply { tag = "claude-consent" },
            button(activity, buttons, "claude-share-memory") {
                val settings = brain.settings
                if (!settings.consentGiven) say("Das geht erst nach der Zustimmung zur Datenübertragung.")
                else if (brain.setShareLocalMemory(!settings.shareLocalMemory)) {
                    host.log("GEHIRN · gerätelokale Erinnerungen mitsenden: " +
                        if (brain.settings.shareLocalMemory) "EIN" else "AUS")
                    render()
                }
            }), 2)

        box.addView(cockpit.section(activity, "CLOUD-WERKZEUGE · KOSTEN EXTRA"), wide(activity, 10))
        cockpit.grid(activity, box, listOf(
            button(activity, buttons, "claude-cloud-web") {
                toggleCloudTool(ClaudeServerToolTexts.WEB_LABEL, ClaudeServerToolTexts.WEB_EXPLANATION,
                    brain.settings.cloudWebSearch, brain::setCloudWebSearch)
            },
            button(activity, buttons, "claude-cloud-code") {
                toggleCloudTool(ClaudeServerToolTexts.CODE_LABEL, ClaudeServerToolTexts.CODE_EXPLANATION,
                    brain.settings.cloudCodeExecution, brain::setCloudCodeExecution)
            }), 2)
        box.addView(cloudTools, wide(activity, 6))

        box.addView(cockpit.section(activity, "KOSTEN · SCHÄTZUNG DIESER APP"), wide(activity, 10))
        box.addView(usage, wide(activity, 6))
        box.addView(cockpit.action(activity, "MONATSLIMIT ÄNDERN") {
            val field = EditText(activity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = "Ganze US-Dollar, 1 bis 1000"
                setText((brain.settings.monthlyLimitCents / 100).toString())
                tag = "claude-limit-input"
            }
            AlertDialog.Builder(activity).setTitle("Monatslimit für Claude")
                .setMessage("Erreicht die Schätzung dieser App das Limit, sende ich in diesem Monat nichts mehr an Claude.")
                .setView(field)
                .setNegativeButton("ABBRECHEN", null)
                .setPositiveButton("SPEICHERN") { _, _ ->
                    val cents = ClaudeBrainPresentation.limitCents(field.text.toString())
                    if (cents == null || !brain.setMonthlyLimitCents(cents))
                        say("Bitte einen ganzen Betrag von 1 bis 1000 US-Dollar eingeben.")
                    render()
                }.show()
        }.apply { tag = "claude-limit" }, wide(activity, 6))

        box.addView(cockpit.action(activity, "SCHLIESSEN") { dialog.dismiss() }, wide(activity, 12))
        render()
        dialog = cockpit.show(activity, box)
        return dialog
    }

    /**
     * The data-transfer notice. [firstActivation]: confirm or decline. Otherwise the same text with the option to
     * revoke. Nothing is stored unless the owner presses the confirming button.
     */
    fun consentDialog(activity: Activity, brain: ClaudeBrain, firstActivation: Boolean,
                      onChanged: () -> Unit, onConfirmed: () -> Unit): AlertDialog {
        val share = CheckBox(activity).apply {
            text = ClaudeConsentNotice.SHARE_MEMORY_LABEL
            isChecked = brain.settings.shareLocalMemory
            tag = "claude-consent-share-memory"
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (18 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(TextView(activity).apply {
                text = ClaudeConsentNotice.TEXT
                textSize = 15f
                tag = "claude-consent-text"
                setTextIsSelectable(true)
            })
            addView(share)
        }
        val builder = AlertDialog.Builder(activity).setTitle(ClaudeConsentNotice.TITLE)
            .setView(ScrollView(activity).apply { addView(body) })
        if (firstActivation || !brain.settings.consentGiven) {
            builder.setNegativeButton(ClaudeConsentNotice.DECLINE, null)
            builder.setPositiveButton(ClaudeConsentNotice.CONFIRM) { _, _ ->
                if (brain.confirmConsent(share.isChecked)) onConfirmed()
            }
        } else {
            builder.setNegativeButton("SCHLIESSEN", null)
            builder.setNeutralButton("ZUSTIMMUNG WIDERRUFEN") { _, _ -> if (brain.revokeConsent()) onChanged() }
        }
        return builder.show()
    }

    /** Masked input; pasting is allowed. The window is secure, so no screenshot or recents preview shows the key. */
    private fun keyDialog(activity: Activity, brain: ClaudeBrain, say: (String) -> Unit, done: () -> Unit) {
        val field = EditText(activity).apply {
            hint = if (brain.hasKey()) "Schlüssel gespeichert · neuer Schlüssel ersetzt ihn" else "sk-ant-…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            tag = "claude-key-input"
        }
        val dialog = AlertDialog.Builder(activity).setTitle("Anthropic-API-Schlüssel")
            .setMessage("Erzeuge den Schlüssel in der Anthropic-Konsole (console.anthropic.com) und füge ihn hier ein. " +
                "Er wird mit einem nicht exportierbaren Schlüssel des Android-Keystore verschlüsselt nur auf diesem " +
                "Gerät gespeichert, nie angezeigt und ausschließlich als Zugangsdaten an api.anthropic.com gesendet. " +
                "Beim Speichern wird nichts gesendet.")
            .setView(field)
            .setNegativeButton("ABBRECHEN") { _, _ -> field.text.clear() }
            .setPositiveButton("SPEICHERN") { _, _ ->
                val plausible = ClaudeApiKey.plausible(field.text.toString().trim())
                val stored = brain.saveKey(field.text.toString())
                field.text.clear()
                say(when {
                    stored -> "Schlüssel verschlüsselt gespeichert. Es wurde nichts gesendet; mit „Verbindung testen“ " +
                        "prüfst du ihn."
                    !plausible -> "Das ist kein Anthropic-API-Schlüssel (er beginnt mit „sk-ant-“). " +
                        "Es wurde nichts gespeichert."
                    else -> "Der Schlüssel konnte auf diesem Gerät nicht verschlüsselt gespeichert werden " +
                        "(Android-Keystore). Es wurde nichts gespeichert."
                })
                done()
            }.create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    private fun note(activity: Activity, tag: String): TextView = TextView(activity).apply {
        this.tag = tag
        setTextColor(Color.rgb(231, 240, 255))
        textSize = 13.5f
        background = ContinuumControls.surface(activity, 12f)
        val pad = (12 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        setTextIsSelectable(true)
    }

    private fun button(activity: Activity, buttons: MutableMap<String, TextView>, tag: String,
                       run: () -> Unit): TextView =
        ContinuumBentoCockpit.action(activity, tag, run = run).also {
            it.tag = tag
            buttons[tag] = it
        }

    private fun wide(activity: Activity, topDp: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = (topDp * activity.resources.displayMetrics.density).toInt()
    }
}
