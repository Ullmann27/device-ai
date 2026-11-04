package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import at.heinz.deviceai.chief.ChiefBotProfile
import at.heinz.deviceai.chief.ChiefBotPersonas
import at.heinz.deviceai.chief.ChiefBotProfilePage
import at.heinz.deviceai.chief.ChiefBotProfileStore
import at.heinz.deviceai.chief.ChiefBotTaskRequest
import at.heinz.deviceai.ui.JarvisDesign
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
    private val onContextChanging: ((String) -> Unit)? = null,
    private val onAssign: ((ChiefBotTaskRequest) -> Boolean)? = null
) {
    private var dialog: AlertDialog? = null
    private var taskDialog: ContinuumBotTaskDialog? = null
    private var revision = 0L
    private val storagePending = AtomicBoolean()
    private val statusViews = mutableListOf<Pair<ChiefBotProfile, TextView>>()
    private var pendingRosterSearch: (() -> Unit)? = null
    private val ui = ContinuumBotDialogStyle(activity)

    fun show() {
        if (!activity.isFinishing && !activity.isDestroyed) showPage(null)
    }

    /** Invalidates presentation only. Accepted storage writes and independent coding jobs continue. */
    fun close() {
        revision++
        pendingRosterSearch = null
        taskDialog?.close()
        taskDialog = null
        dialog?.dismiss()
        dialog = null
        statusViews.clear()
    }

    /** Reuses the Activity's existing state/tick callbacks; no per-bot timers or render loops. */
    fun refreshRuntimeStatus() {
        taskDialog?.refreshRuntimeStatus()
        val status = runtimeStatus ?: return
        if (activity.isFinishing || activity.isDestroyed || dialog?.isShowing != true) return
        statusViews.forEach { (profile, view) -> view.text = status(profile) }
    }

    private fun statusLabel(profile: ChiefBotProfile, fallback: String): TextView =
        ui.label(runtimeStatus?.invoke(profile) ?: fallback, 13f, JarvisDesign.Colors.textSecondary)
            .also { statusViews += profile to it }

    private fun column() = ui.column()

    private fun label(text: String, size: Float = 15f) = ui.label(text, size)

    private fun action(text: String, click: () -> Unit) = ui.action(text, click = click)

    private fun open(title: String, content: LinearLayout): Long {
        taskDialog?.close()
        taskDialog = null
        pendingRosterSearch = null
        dialog?.dismiss()
        statusViews.clear()
        val token = ++revision
        dialog = AlertDialog.Builder(activity).setCustomTitle(label(title, 21f).apply {
            setPadding(dp(16), dp(16), dp(16), dp(8))
        })
            .setView(ScrollView(activity).apply { addView(content) })
            .setNegativeButton("Schließen", null).create().also { current ->
                current.setOnDismissListener {
                    if (dialog === current) {
                        revision++
                        dialog = null
                        pendingRosterSearch = null
                        statusViews.clear()
                    }
                }
                current.show()
                current.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(JarvisDesign.Colors.cyan)
                ui.fitWindow(current)
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
                    try {
                        result.fold(done) {
                            body.removeAllViews()
                            val code = ChiefBotProfileStore.diagnosticCode(it)
                            body.addView(label("Der Bot-Bestand konnte nicht sicher gelesen oder gespeichert werden. " +
                                "Bestehende Daten werden nicht automatisch ersetzt."))
                            body.addView(label("Speicherdiagnose: $code").apply {
                                contentDescription = "Bot-Speicherdiagnose: $code"
                            })
                            body.addView(label(diagnosticAdvice(code)))
                            body.addView(action("Übersicht erneut öffnen") { show() })
                        }
                    } finally {
                        if (live(token) && !storagePending.get()) {
                            val next = pendingRosterSearch
                            pendingRosterSearch = null
                            next?.invoke()
                        }
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

    private fun diagnosticAdvice(code: String): String = when (code) {
        "BOT_REGISTRY_UNSUPPORTED_SCHEMA" -> "Dieser Bestand benötigt eine kompatible App-Version. Bitte keine App-Daten löschen."
        "BOT_REGISTRY_CORRUPT", "BOT_REGISTRY_CHECKSUM_MISMATCH", "BOT_REGISTRY_CAPACITY_OR_CORRUPTION",
        "BOT_DUPLICATE_ID", "BOT_REGISTRY_TRAILING_DATA" ->
            "Der gespeicherte Bestand muss geprüft werden. Er wird weder neu angelegt noch zurückgesetzt."
        "BOT_STORE_SYMLINK_REJECTED", "BOT_STORE_OUTSIDE_TRUSTED_ROOT", "BOT_STORE_NOT_FILE", "BOT_STORE_NOT_DIRECTORY" ->
            "Der private Speicherpfad wurde aus Sicherheitsgründen abgelehnt. Bitte diese Diagnose zur Prüfung weitergeben."
        "BOT_REGISTRY_WRITE_FAILED", "BOT_STORE_ACCESS_DENIED", "BOT_STORE_IO_ERROR" ->
            "Der Speicherzugriff wurde nicht bestätigt. Bitte verfügbaren Gerätespeicher prüfen und erneut öffnen."
        "BOT_PROFILE_VERSION_CONFLICT" -> "Das Profil wurde inzwischen geändert. Bitte die Übersicht erneut öffnen."
        else -> "Bitte diese Diagnose zur Prüfung weitergeben und die Übersicht erneut öffnen."
    }

    private fun showPage(afterId: String?, initialQuery: String = "") {
        val body = column()
        body.addView(label("Bot-Profile werden geladen …"))
        val token = open("Team · Bots", body)
        load(token, body, {
            store.seedDefaults(20)
            findPage(afterId, initialQuery)
        }) { page ->
            body.removeAllViews()
            body.addView(ui.label("DEIN LOKALES TEAM", 12f, JarvisDesign.Colors.cyan))
            body.addView(label("Wähle die passende Persönlichkeit.", 20f))
            body.addView(ui.label("Direkt sprechen, einen Auftrag vorbereiten oder eigene Arbeitsnotizen pflegen.",
                14f, JarvisDesign.Colors.textSecondary))
            val search = ui.editor().apply {
                hint = "Name, Rolle oder Arbeitsstil suchen"
                contentDescription = "Bots nach Name, Rolle oder Arbeitsstil suchen"
                setText(initialQuery)
                tag = "bot-roster-search"
            }
            body.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(12) })
            val results = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            body.addView(results)
            var searchRevision = 0L
            fun render(result: ChiefBotProfilePage, query: String, cursor: String?, sequence: Long) {
                if (!live(token) || sequence != searchRevision) return
                results.removeAllViews()
                statusViews.clear()
                val current = { live(token) && sequence == searchRevision }
                val resultText = if (query.isBlank()) "${result.items.size} Profile auf dieser Seite"
                    else "${result.items.size} Treffer auf dieser Seite"
                results.addView(ui.label(resultText, 12f, JarvisDesign.Colors.textSecondary))
                val columns = if (activity.resources.configuration.screenWidthDp >= 680 &&
                    activity.resources.configuration.fontScale <= 1.3f) 2 else 1
                result.items.chunked(columns).forEach { profiles ->
                    val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    profiles.forEachIndexed { index, profile ->
                        row.addView(profileCard(profile, current), LinearLayout.LayoutParams(0, -2, 1f).apply {
                            if (index > 0) leftMargin = dp(10)
                            bottomMargin = dp(12)
                        })
                    }
                    if (columns == 2 && profiles.size == 1) row.addView(View(activity), LinearLayout.LayoutParams(0, 0, 1f))
                    results.addView(row)
                }
                if (result.items.isEmpty()) results.addView(label("Kein Bot passt zu dieser Suche. Versuche einen Namen, eine Rolle oder einen anderen Arbeitsstil."))
                result.nextAfterId?.let { next -> results.addView(action("Weitere Bots") {
                    if (current()) showPage(next, query)
                }) }
                if (cursor != null) results.addView(action("Zur ersten Seite") { if (current()) showPage(null, query) })
                results.addView(action("Bot hinzufügen") { if (current()) createProfile() })
            }
            render(page, initialQuery, afterId, searchRevision)
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun afterTextChanged(s: Editable?) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!live(token)) return
                    val query = s?.toString().orEmpty()
                    val sequence = ++searchRevision
                    results.removeAllViews()
                    statusViews.clear()
                    results.addView(label("Bots werden gesucht …"))
                    val searchTask: () -> Unit = {
                        if (live(token) && sequence == searchRevision)
                            load(token, results, { findPage(null, query) }) { render(it, query, null, sequence) }
                    }
                    // At most one latest read is deferred. Writes and tasks are never queued here.
                    if (storagePending.get()) pendingRosterSearch = searchTask else searchTask()
                }
            })
        }
    }

    private fun findPage(afterId: String?, query: String): ChiefBotProfilePage {
        val term = query.trim()
        if (term.isEmpty()) return store.page(afterId, 8)
        val matches = ArrayList<ChiefBotProfile>(9)
        var cursor = afterId
        do {
            val page = store.page(cursor, 100)
            for (profile in page.items) {
                if (listOf(profile.displayName, profile.role, profile.communicationStyle).any { it.contains(term, ignoreCase = true) }) {
                    matches += profile
                    if (matches.size == 9) return ChiefBotProfilePage(matches.take(8), matches[7].agentId)
                }
            }
            cursor = page.nextAfterId
        } while (cursor != null)
        return ChiefBotProfilePage(matches, null)
    }

    private fun profileCard(profile: ChiefBotProfile, current: () -> Boolean) = column().apply {
        background = ui.surface(ui.accent(profile))
        val persona = ChiefBotPersonas.describe(profile)
        addView(ui.identity(profile))
        addView(label(persona.tagline))
        addView(ui.label("ARBEITSWEISE", 11f, ui.accent(profile)))
        addView(ui.label(persona.approach, 14f, JarvisDesign.Colors.textSecondary))
        addView(statusLabel(profile, "REGISTRIERT · Kein laufender Auftrag\nGeprüfte Fähigkeiten: noch keine"))
        onSelect?.let { select -> addView(ui.action("Mit ${profile.displayName} sprechen", primary = true) {
            if (current()) { close(); select(profile) }
        }) }
        onAssign?.let { addView(action("Auftrag geben") { if (current()) showTask(profile) }) }
        addView(action("Profil und Arbeitsbereich öffnen") { if (current()) showProfile(profile.agentId) })
    }

    private fun showTask(profile: ChiefBotProfile) {
        val assign = onAssign ?: return
        close()
        taskDialog = ContinuumBotTaskDialog(activity, profile, assign, runtimeStatus).also { it.show() }
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
            body.addView(ui.identity(profile))
            val persona = ChiefBotPersonas.describe(profile)
            body.addView(label(persona.tagline))
            body.addView(ui.label("ARBEITSWEISE", 12f, ui.accent(profile)))
            body.addView(label(persona.approach))
            body.addView(statusLabel(profile, "REGISTRIERT · Kein aktueller Auftrag\n" +
                "Geprüfte Fähigkeiten: noch keine\nLetztes bestätigtes Arbeitsergebnis: noch keines"))
            onSelect?.let { select -> body.addView(action("Mit ${profile.displayName} sprechen") {
                if (live(token)) { close(); select(profile) }
            }) }
            onAssign?.let { body.addView(action("Auftrag geben") { if (live(token)) showTask(profile) }) }
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

    private fun edit(value: String, limit: Int, multiline: Boolean = false) = ui.editor(multiline).apply {
        setText(value)
        filters = arrayOf(InputFilter.LengthFilter(limit))
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
            if (!live(token)) return@setOnClickListener
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

    private fun dp(value: Int) = ui.dp(value)
}
