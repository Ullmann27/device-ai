package at.heinz.deviceai.codingagents

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import at.heinz.deviceai.ContinuumBotDialogStyle
import at.heinz.deviceai.cockpit.CockpitAnimationTriggers
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.ui.JarvisDesign

/**
 * The coding agents on the owner's screen: the thin Android side of [CodingAgentController]. It shows the agents'
 * messages in the conversation, their steps in the work window and the dialog „Coding-Agenten (Claude, Cloud)“.
 * Everything the Activity has to provide arrives as a function; no logic of the agents lives in the Activity.
 */
class CodingAgentScreen(
    private val activity: Activity,
    private val triggers: CockpitAnimationTriggers,
    private val say: (String) -> Unit,
    private val log: (String) -> Unit,
    private val idle: () -> Boolean,
    private val ownerTurn: (String) -> Unit,
    private val assistantName: () -> String?,
    private val controllerOf: () -> CodingAgentController = { AndroidCodingAgents.controller(activity) }
) : CodingAgentHost {
    private val main = Handler(Looper.getMainLooper())
    private val ui by lazy { ContinuumBotDialogStyle(activity) }
    private var dialog: AlertDialog? = null
    private var head: LinearLayout? = null
    private var form: LinearLayout? = null
    private var list: LinearLayout? = null
    private var notice: String? = null
    val controller: CodingAgentController get() = controllerOf()

    private fun alive() = !activity.isFinishing && !activity.isDestroyed
    private fun timeline(runId: String) = "coding-agent-$runId"

    fun attach() = controller.attach(this)
    fun detach() { controller.detach(this); close() }

    /** One owner turn; true when the coding agents took it (then it is shown as the owner's turn). */
    fun take(spoken: String): Boolean {
        if (!controller.take(spoken, assistantName())) return false
        ownerTurn(spoken)
        return true
    }

    fun controls(spoken: String): Boolean = controller.controls(spoken, assistantName())

    override fun post(action: () -> Unit) { main.post { if (alive()) action() } }

    private val waiting = ArrayDeque<String>()
    private var deliveryScheduled = false

    /** A message of the agents never lands in the middle of an answer that is being written: it waits for the lane. */
    override fun say(text: String) {
        waiting.addLast(text)
        if (!deliveryScheduled) deliver(0)
    }

    private fun deliver(waits: Int) {
        deliveryScheduled = false
        if (!alive() || waiting.isEmpty()) return
        if (!idle.invoke() && waits < MAX_WAITS) {
            deliveryScheduled = true
            main.postDelayed({ deliver(waits + 1) }, WAIT_MILLIS)
            return
        }
        while (waiting.isNotEmpty()) say.invoke(waiting.removeFirst())
    }

    override fun activity(line: String) = log(line)
    override fun runStarted(runId: String, title: String) = triggers.commandAcknowledged(timeline(runId), title)
    override fun step(runId: String, key: String, label: String, state: StepState) = triggers.workStep(timeline(runId), key, label, state)
    override fun runEnded(runId: String, delivered: Boolean, verified: Boolean) = triggers.turnEnded(timeline(runId), delivered, verified)
    override fun idle(): Boolean = idle.invoke()

    /** A run moved on: only the status and the run list are redrawn, so nothing the owner is typing is lost. */
    override fun changed() {
        if (dialog?.isShowing != true) return
        renderHead()
        renderRuns()
    }

    override fun openPanel() {
        if (!alive()) return
        if (dialog?.isShowing == true) return render()
        val content = ui.column()
        head = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }.also(content::addView)
        form = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }.also(content::addView)
        list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }.also(content::addView)
        dialog = AlertDialog.Builder(activity)
            .setCustomTitle(ui.label(CodingAgentTexts.TITLE, 21f).apply { setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(8)) })
            .setView(ScrollView(activity).apply { addView(content) })
            .setNegativeButton("Schließen", null).create().also { created ->
                created.setOnDismissListener { if (dialog === created) { dialog = null; head = null; form = null; list = null; notice = null } }
                created.show()
                created.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(JarvisDesign.Colors.cyan)
                ui.fitWindow(created)
            }
        render()
    }

    fun close() { dialog?.dismiss(); dialog = null; head = null; form = null; list = null }

    private fun render() { renderHead(); renderForm(); renderRuns() }

    /** The answer to something the owner just did in the dialog; the form is redrawn with what is stored now. */
    private fun tell(text: String?) { notice = text; render() }
    private fun small(text: String) = ui.label(text, 13f, JarvisDesign.Colors.textSecondary)
    private fun heading(text: String) = ui.label(text, 12f, JarvisDesign.Colors.cyan).apply { setPadding(0, ui.dp(16), 0, ui.dp(2)) }
    private fun field(hint: String, tag: String, secret: Boolean = false, multiline: Boolean = false): EditText = ui.editor(multiline).apply {
        this.hint = hint
        this.tag = tag
        contentDescription = hint
        // A token is never shown again and never offered to the keyboard's learning or to autofill.
        if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        if (secret) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    }
    private fun button(text: String, tag: String, primary: Boolean = false, click: () -> Unit) =
        ui.action(text, primary, click).apply { this.tag = tag }

    private fun confirmNotice() {
        AlertDialog.Builder(activity).setTitle(CodingAgentTexts.CONSENT_TITLE)
            .setMessage(CodingAgentTexts.CONSENT)
            .setPositiveButton(CodingAgentTexts.CONSENT_CONFIRM) { _, _ -> controller.giveConsent(); tell("Zustimmung gespeichert.") }
            .setNegativeButton(CodingAgentTexts.CONSENT_DECLINE, null).show()
    }

    private fun renderHead() {
        val content = head ?: return
        val agents = controller
        content.removeAllViews()
        content.addView(ui.label(CodingAgentTexts.INTRO))
        notice?.let { content.addView(ui.label(it, 15f, JarvisDesign.Colors.warn).apply { tag = "coding-agent-notice" }) }
        content.addView(small(agents.statusText()).apply { tag = "coding-agent-status" })
        agents.pendingConfirmation()?.let { question ->
            content.addView(heading("BESTÄTIGUNG"))
            content.addView(ui.label(question).apply { tag = "coding-agent-confirmation" })
            content.addView(button("Ja, starten", "coding-agent-yes", primary = true) { agents.confirm(); tell(null) })
            content.addView(button("Nicht starten", "coding-agent-no") { agents.decline(); tell(null) })
        }
    }

    private fun renderForm() {
        val content = form ?: return
        val agents = controller
        val settings = agents.settings
        // What the owner has typed as an order survives a redraw; a typed token never does.
        val draft = content.findViewWithTag<EditText>("coding-agent-task")?.text?.toString().orEmpty()
        content.removeAllViews()

        content.addView(heading("1 · ZUSTIMMUNG"))
        if (settings.consentGiven) content.addView(button("Zustimmung widerrufen", "coding-agent-revoke") {
            agents.revokeConsent(); tell("Zustimmung widerrufen. Es startet kein Coding-Agent mehr.")
        }) else content.addView(button("Hinweis lesen und zustimmen", "coding-agent-consent", primary = true) { confirmNotice() })

        content.addView(heading("2 · GITHUB-TOKEN"))
        val token = field("github_pat_… einfügen", "coding-agent-token", secret = true)
        content.addView(token)
        content.addView(button("Token speichern", "coding-agent-token-save") {
            val text = agents.saveToken(token.text.toString())
            token.text.clear()
            tell(text)
        })
        content.addView(button("So erzeugst du das Token", "coding-agent-token-help") {
            AlertDialog.Builder(activity).setTitle(CodingAgentTexts.TOKEN_TITLE).setMessage(CodingAgentTexts.TOKEN_HELP)
                .setPositiveButton("Verstanden", null).show()
        })
        if (agents.tokenPresent() || agents.resources.credentialId != null)
            content.addView(button("Token entfernen (Gerät und Tresor bei Anthropic)", "coding-agent-token-remove") {
                agents.removeToken { text -> tell(text + " " + CodingAgentTexts.TOKEN_REVOKE_HINT) }
            })

        content.addView(heading("3 · FREIGEGEBENE REPOSITORIES"))
        settings.repositories.forEach { repository ->
            content.addView(button("$repository · entfernen", "coding-agent-repo-remove:$repository") {
                agents.removeRepository(repository); tell("$repository ist nicht mehr freigegeben.")
            })
        }
        val repository = field("Besitzer/Name, zum Beispiel Ullmann27/device-ai", "coding-agent-repo")
        content.addView(repository)
        content.addView(button("Repository freigeben", "coding-agent-repo-add") {
            tell(agents.addRepository(repository.text.toString()) ?: "Freigegeben. Der Standardzweig wird beim Auftrag von GitHub gelesen.")
        })

        content.addView(heading("4 · GRENZEN"))
        fun limit(hint: String, tag: String, current: String, apply: (String) -> String?) {
            val input = field(hint, tag).apply { setText(current) }
            content.addView(small(hint))
            content.addView(input)
            content.addView(button("Übernehmen", "$tag-set") { tell(apply(input.text.toString()) ?: "Gespeichert.") })
        }
        limit("Budget je Lauf in Dollar (harte Obergrenze bei Anthropic)", "coding-agent-budget",
            CodingAgentMoney.usd(settings.budgetCents).removeSuffix(" $"), agents::setBudget)
        limit("Höchstes Budget, das ein Lauf haben darf", "coding-agent-budget-max",
            CodingAgentMoney.usd(settings.maxBudgetCents).removeSuffix(" $"), agents::setMaxBudget)
        limit("Längste Laufzeit in Minuten", "coding-agent-minutes", settings.maxRunMinutes.toString(), agents::setMaxRunMinutes)
        limit("Modell (Kennung bei Anthropic)", "coding-agent-model", settings.model, agents::setModel)

        content.addView(heading("5 · BEI ANTHROPIC"))
        content.addView(button("Bei Anthropic einrichten (kostet keine Modell-Token)", "coding-agent-provision") {
            tell("Einrichtung läuft …")
            agents.provision { text -> tell(text) }
        })

        content.addView(heading("AUFTRAG"))
        content.addView(small(CodingAgentTexts.SECURITY_NOTE))
        val task = field("Was soll der Agent tun?", "coding-agent-task", multiline = true).apply { setText(draft) }
        content.addView(task)
        fun order(kind: CodingAgentTaskKind, target: String) {
            agents.order(kind, CodingAgentRepoChoice.Named(target), task.text.toString())
            tell(null)
        }
        if (settings.repositories.isEmpty()) content.addView(small("Gib zuerst ein Repository frei."))
        settings.repositories.forEach { target ->
            content.addView(button("Coding-Auftrag für $target prüfen", "coding-agent-order:$target", primary = true) {
                order(CodingAgentTaskKind.CODE, target)
            })
            content.addView(button("Sicherheitsprüfung für $target prüfen", "coding-agent-security:$target") {
                order(CodingAgentTaskKind.SECURITY, target)
            })
        }
    }

    private fun renderRuns() {
        val content = list ?: return
        val agents = controller
        content.removeAllViews()
        content.addView(heading("LÄUFE"))
        val runs = agents.runs()
        if (runs.isEmpty()) content.addView(small("Noch kein Lauf."))
        runs.forEach { run ->
            content.addView(ui.label(CodingAgentReport.listLine(run)).apply { tag = "coding-agent-run:${run.id}" })
            // A running agent shows its last real steps; a finished one its last.
            run.progress.takeLast(if (run.active) LIVE_LINES else 1).forEach { content.addView(small(it)) }
            run.approvals.forEach { approval ->
                content.addView(ui.label("Freigabe nötig: ${approval.summary}", 15f, JarvisDesign.Colors.warn))
                content.addView(button("Erlauben", "coding-agent-allow:${approval.eventId}", primary = true) { agents.decide(run.id, approval.eventId, true) })
                content.addView(button("Ablehnen", "coding-agent-deny:${approval.eventId}") { agents.decide(run.id, approval.eventId, false) })
            }
            if (run.active) content.addView(button("Stopp", "coding-agent-stop:${run.id}", primary = true) { agents.stop(run.id) })
            content.addView(button("Bericht anzeigen", "coding-agent-report:${run.id}") {
                AlertDialog.Builder(activity).setTitle(run.repository).setMessage(agents.reportText(run.id).orEmpty())
                    .setPositiveButton("Schließen", null).show()
            })
            if (run.sessionId != null && !run.historyDeleted) {
                content.addView(button("Aktualisieren", "coding-agent-refresh:${run.id}") { agents.refresh(run.id) })
                if (!run.active) content.addView(button("Verlauf bei Anthropic löschen", "coding-agent-delete:${run.id}") {
                    AlertDialog.Builder(activity).setTitle("Verlauf löschen?")
                        .setMessage("Die Sitzung mit Auftrag, Ereignisverlauf und Sandbox wird bei Anthropic endgültig gelöscht. " +
                            "Zweig und Pull Request auf GitHub bleiben bestehen.")
                        .setPositiveButton("Löschen") { _, _ -> agents.deleteHistory(run.id) { text -> tell(text) } }
                        .setNegativeButton("Abbrechen", null).show()
                })
            } else if (!run.active) content.addView(button("Aus der Liste entfernen", "coding-agent-remove:${run.id}") { agents.removeRun(run.id) })
        }
    }

    private companion object {
        const val WAIT_MILLIS = 1_500L
        /** After ten minutes a message is shown even though the lane is still busy. */
        const val MAX_WAITS = 400
        const val LIVE_LINES = 5
    }
}
