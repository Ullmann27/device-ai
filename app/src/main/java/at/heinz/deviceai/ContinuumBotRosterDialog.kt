package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import at.heinz.deviceai.chief.ChiefBotProfile
import at.heinz.deviceai.chief.ChiefBotProfileStore
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Owner-facing catalogue. Profiles never grant tools, start models or create an OS sandbox.
 * At most one storage operation uses the Activity's existing I/O executor. No avatar/render loop,
 * microphone listener or second conversation runtime is created for catalogue cards. */
class ContinuumBotRosterDialog(
    private val activity: Activity,
    private val store: ChiefBotProfileStore,
    private val io: Executor,
    private val onSelect: ((ChiefBotProfile) -> Unit)? = null,
    private val runtimeStatus: ((ChiefBotProfile) -> String)? = null,
    private val onContextChanging: ((String) -> Unit)? = null
) {
    private var dialog: AlertDialog? = null
    private var revision = 0L
    private val storagePending = AtomicBoolean()
    private val statusViews = mutableListOf<Pair<ChiefBotProfile, TextView>>()
    private val cyan = Color.rgb(79, 224, 255)
    private val violet = Color.rgb(159, 120, 255)
    private val surfaceColor = Color.rgb(5, 13, 29)

    fun show() {
        if (!activity.isFinishing && !activity.isDestroyed) showPage(null)
    }

    /** Invalidates presentation only. Accepted storage writes and independent coding jobs continue. */
    fun close() {
        revision++
        dialog?.dismiss()
        dialog = null
        statusViews.clear()
    }

    /** Reuses the Activity's existing state/tick callbacks; no per-bot timers or render loops. */
    fun refreshRuntimeStatus() {
        val status = runtimeStatus ?: return
        if (activity.isFinishing || activity.isDestroyed || dialog?.isShowing != true) return
        statusViews.forEach { (profile, view) -> view.text = status(profile) }
    }

    private fun statusLabel(profile: ChiefBotProfile, fallback: String): TextView =
        label(runtimeStatus?.invoke(profile) ?: fallback).also { statusViews += profile to it }

    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(16))
        setBackgroundColor(surfaceColor)
    }

    private fun label(text: String, size: Float = 15f) = TextView(activity).apply {
        this.text = text
        textSize = size
        setTextColor(Color.rgb(220, 234, 246))
        setPadding(0, dp(6), 0, dp(6))
        setTextIsSelectable(true)
    }

    private fun action(text: String, click: () -> Unit) = Button(activity).apply {
        this.text = text
        isAllCaps = false
        minHeight = dp(48)
        setTextColor(cyan)
        setOnClickListener { click() }
    }

    private fun open(title: String, content: LinearLayout): Long {
        dialog?.dismiss()
        statusViews.clear()
        val token = ++revision
        dialog = AlertDialog.Builder(activity).setTitle(title)
            .setView(ScrollView(activity).apply { addView(content) })
            .setNegativeButton("Schließen", null).create().also { current ->
                current.setOnDismissListener { if (dialog === current) revision++ }
                current.show()
                current.window?.setBackgroundDrawableResource(android.R.color.transparent)
                current.window?.setBackgroundDrawable(GradientDrawable().apply {
                    setColor(surfaceColor); cornerRadius = dp(18).toFloat(); setStroke(dp(1), violet)
                })
            }
        return token
    }

    private fun live(token: Long) = !activity.isFinishing && !activity.isDestroyed &&
        token == revision && dialog?.isShowing == true

    private fun <T> load(token: Long, body: LinearLayout, task: () -> T, done: (T) -> Unit) {
        if (!storagePending.compareAndSet(false, true)) {
            if (live(token)) {
                body.removeAllViews()
                body.addView(label("Ein Speicherzugriff läuft noch. Diese neue Anfrage wurde nicht eingereiht."))
                body.addView(action("Übersicht erneut öffnen") { show() })
            }
            return
        }
        try {
            io.execute {
                val result = try { runCatching(task) } finally { storagePending.set(false) }
                activity.runOnUiThread {
                    if (!live(token)) return@runOnUiThread
                    result.fold(done) {
                        body.removeAllViews()
                        body.addView(label("Der Bot-Bestand konnte nicht sicher gelesen oder gespeichert werden. " +
                            "Bitte im Entwicklerbereich prüfen. Bestehende Daten werden nicht automatisch ersetzt."))
                        body.addView(action("Übersicht erneut öffnen") { show() })
                    }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            storagePending.set(false)
            if (live(token)) {
                body.removeAllViews()
                body.addView(label("Die Sitzung wird beendet. Bitte die Bot-Übersicht erneut öffnen."))
            }
        }
    }

    private fun showPage(afterId: String?) {
        val body = column()
        body.addView(label("Bot-Profile werden geladen …"))
        val token = open("Team · Bots", body)
        load(token, body, {
            store.seedDefaults(20)
            store.page(afterId, 8)
        }) { page ->
            body.removeAllViews()
            body.addView(label("Dauerhafte Rollen und Arbeitsnotizen. Ein Profil startet keine Berechnung."))
            page.items.forEach { profile ->
                val card = column().apply {
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(10, 24, 45)); cornerRadius = dp(12).toFloat()
                        setStroke(dp(1), violet)
                    }
                }
                card.addView(label(profile.displayName + " · " + profile.role, 18f))
                card.addView(label(profile.communicationStyle))
                card.addView(statusLabel(profile,
                    "REGISTRIERT · Kein laufender Auftrag\nGeprüfte Fähigkeiten: noch keine"))
                onSelect?.let { select -> card.addView(action("Mit ${profile.displayName} sprechen") {
                    if (live(token)) { close(); select(profile) }
                }) }
                card.addView(action("Profil und Arbeitsbereich öffnen") { showProfile(profile.agentId) })
                body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            }
            page.nextAfterId?.let { next -> body.addView(action("Weitere Bots") { showPage(next) }) }
            if (afterId != null) body.addView(action("Zur ersten Seite") { show() })
            body.addView(action("Bot hinzufügen") { createProfile() })
        }
    }

    private fun showProfile(agentId: String) {
        val body = column()
        body.addView(label("Arbeitsbereich wird geladen …"))
        val token = open("Bot · Arbeitsbereich", body)
        load(token, body, {
            val profile = requireNotNull(store.get(agentId))
            profile to store.workingContext(profile.agentId, profile.agentId).orEmpty()
        }) { (profile, notes) ->
            body.removeAllViews()
            body.addView(label(profile.displayName + " · " + profile.role, 21f))
            body.addView(label(profile.communicationStyle))
            body.addView(statusLabel(profile, "REGISTRIERT · Kein aktueller Auftrag\n" +
                "Geprüfte Fähigkeiten: noch keine\nLetztes bestätigtes Arbeitsergebnis: noch keines"))
            onSelect?.let { select -> body.addView(action("Mit ${profile.displayName} sprechen") {
                if (live(token)) { close(); select(profile) }
            }) }
            body.addView(label("Arbeitsplatz: ${profile.workspaceId}\n" +
                "Speicherort: private App-Daten auf diesem Gerät\n" +
                "Ausführung: noch keine isolierte Umgebung verbunden.\n" +
                "Arbeitsnotizen sind getrennt gespeichert; dies ist keine Betriebssystem-Isolation."))
            profile.contextObjectId?.let { hash ->
                body.addView(label("Gespeicherte Arbeitsnotiz · Objekt $hash"))
            }
            body.addView(label("Arbeitsnotiz für ${profile.displayName}"))
            val editor = edit(notes, 4_000, multiline = true)
            editor.contentDescription = "Arbeitsnotiz für ${profile.displayName}"
            body.addView(editor)
            val save = action("Arbeitsnotiz speichern") {}
            save.setOnClickListener {
                if (!live(token)) return@setOnClickListener
                save.isEnabled = false
                onContextChanging?.invoke(profile.agentId)
                val text = editor.text.toString()
                load(token, body, {
                    // Principal is selected by this trusted owner UI, never parsed from model text.
                    store.saveWorkingContext(profile.agentId, profile.agentId, text, profile.profileVersion)
                }) { showProfile(it.agentId) }
            }
            body.addView(save)
            body.addView(action("Namen ändern") { editProfile(profile) })
            body.addView(action("Rolle und Arbeitsstil bearbeiten") { editDescription(profile) })
            body.addView(action("Zur Teamübersicht") { show() })
        }
    }

    private fun edit(value: String, limit: Int, multiline: Boolean = false) = EditText(activity).apply {
        setText(value); setTextColor(Color.WHITE); setHintTextColor(cyan)
        filters = arrayOf(InputFilter.LengthFilter(limit))
        setSingleLine(!multiline)
        if (multiline) { minLines = 3; maxLines = 9 }
    }

    private fun editProfile(profile: ChiefBotProfile) {
        val body = column()
        body.addView(label("Name ändern. Identität, Arbeitsnotizen und Rechte bleiben erhalten."))
        val name = edit(profile.displayName, 80)
        name.contentDescription = "Bot-Name"
        body.addView(name)
        val token = open("${profile.displayName} · Name", body)
        val save = action("Name speichern") {}
        save.setOnClickListener {
            if (!live(token)) return@setOnClickListener
            if (name.text.isBlank()) { name.error = "Bitte einen Namen eingeben"; return@setOnClickListener }
            save.isEnabled = false
            onContextChanging?.invoke(profile.agentId)
            val value = name.text.toString()
            load(token, body, { store.rename(profile.agentId, value, profile.profileVersion) }) {
                showProfile(it.agentId)
            }
        }
        body.addView(save)
    }

    private fun createProfile() {
        val body = column()
        val name = edit("", 80).apply { hint = "Name"; contentDescription = "Neuer Bot-Name" }
        val role = edit("", 240).apply { hint = "Fachgebiet"; contentDescription = "Fachgebiet" }
        val style = edit("", 600, true).apply { hint = "Arbeitsstil"; contentDescription = "Arbeitsstil" }
        body.addView(label("Ein neues Rollenprofil erhält eigene Arbeitsnotizen. Fähigkeiten und Werkzeuge werden dadurch nicht freigegeben."))
        body.addView(name); body.addView(role); body.addView(style)
        val token = open("Bot hinzufügen", body)
        val save = action("Profil anlegen") {}
        save.setOnClickListener {
            if (listOf(name, role, style).any { it.text.isBlank() }) {
                name.error = "Bitte Name, Fachgebiet und Arbeitsstil ausfüllen"; return@setOnClickListener
            }
            save.isEnabled = false
            val id = "bot-" + UUID.randomUUID().toString()
            val values = listOf(name, role, style).map { it.text.toString() }
            load(token, body, { store.create(id, values[0], values[1], values[2]) }) { showProfile(it.agentId) }
        }
        body.addView(save)
    }

    private fun editDescription(profile: ChiefBotProfile) {
        val body = column()
        body.addView(label("Rolle und Arbeitsstil sind Vorgaben. Sie bestätigen keine Fähigkeiten und ändern keine Rechte."))
        val role = edit(profile.role, 240).apply { contentDescription = "Fachgebiet" }
        val style = edit(profile.communicationStyle, 600, true).apply { contentDescription = "Arbeitsstil" }
        body.addView(role); body.addView(style)
        val token = open("${profile.displayName} · Rollenprofil", body)
        val save = action("Rollenprofil speichern") {}
        save.setOnClickListener {
            if (!live(token)) return@setOnClickListener
            if (role.text.isBlank() || style.text.isBlank()) {
                role.error = "Bitte Fachgebiet und Arbeitsstil ausfüllen"; return@setOnClickListener
            }
            save.isEnabled = false
            onContextChanging?.invoke(profile.agentId)
            val roleText = role.text.toString()
            val styleText = style.text.toString()
            load(token, body, {
                store.updateDescription(profile.agentId, roleText, styleText, profile.profileVersion)
            }) { showProfile(it.agentId) }
        }
        body.addView(save)
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
