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
import at.heinz.deviceai.chief.ChiefBotPrivateRecordKind
import at.heinz.deviceai.chief.ChiefBotTaskRequest
import at.heinz.deviceai.chief.ChiefBotTaskMode
import at.heinz.deviceai.chief.ChiefEngineeringPracticeCurriculum
import at.heinz.deviceai.chief.ChiefBotTeam
import at.heinz.deviceai.chief.ChiefFollowupDecisionCode
import at.heinz.deviceai.chief.ChiefFollowupReview
import at.heinz.deviceai.chief.ChiefTeamStatus
import at.heinz.deviceai.chief.ChiefTeamTask
import at.heinz.deviceai.cognitive.AgentFollowupEnvelope
import at.heinz.deviceai.learning.SharedOwnerKnowledgeGrant
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
    private val onAssign: ((ChiefBotTaskRequest) -> Boolean)? = null,
    private val team: ChiefBotTeam? = null,
    private val onTeamSubmit: ((String, String, Long, SharedOwnerKnowledgeGrant?, ChiefFollowupReview?) -> Boolean)? = null,
    private val onTeamStop: ((String) -> Unit)? = null
) {
    private var dialog: AlertDialog? = null
    private var taskDialog: ContinuumBotTaskDialog? = null
    private var revision = 0L
    private val storagePending = AtomicBoolean()
    private val statusViews = mutableListOf<Pair<ChiefBotProfile, TextView>>()
    private var pendingRosterSearch: (() -> Unit)? = null
    private var teamRefresh: (() -> Unit)? = null
    private var abandonDraft: (() -> Unit)? = null
    private val ui = ContinuumBotDialogStyle(activity)

    fun show() {
        if (!activity.isFinishing && !activity.isDestroyed) showPage(null)
    }

    fun discardTeamDraft() {
        if (abandonDraft != null) close()
    }

    /** Invalidates presentation only. Accepted storage writes and independent coding jobs continue. */
    fun close() {
        revision++
        releaseDraft()
        pendingRosterSearch = null
        teamRefresh = null
        taskDialog?.close()
        taskDialog = null
        dialog?.dismiss()
        dialog = null
        statusViews.clear()
    }

    /** Reuses the Activity's existing state/tick callbacks; no per-bot timers or render loops. */
    fun refreshRuntimeStatus() {
        taskDialog?.refreshRuntimeStatus()
        teamRefresh?.invoke()
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

    private fun releaseDraft() {
        val cleanup = abandonDraft
        abandonDraft = null
        cleanup?.invoke()
    }

    private fun open(title: String, content: LinearLayout): Long {
        releaseDraft()
        taskDialog?.close()
        taskDialog = null
        pendingRosterSearch = null
        teamRefresh = null
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
                        releaseDraft()
                        revision++
                        dialog = null
                        pendingRosterSearch = null
                        teamRefresh = null
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
        val token = open("Mein KI-Team", body)
        load(token, body, {
            store.seedDefaults(20)
            team?.initialize()
            findPage(afterId, initialQuery)
        }) { page ->
            body.removeAllViews()
            body.addView(ui.label("DEIN LOKALES TEAM", 12f, JarvisDesign.Colors.cyan))
            body.addView(label("Wähle die passende Persönlichkeit.", 20f))
            body.addView(ui.label("Direkt sprechen, einen Auftrag vorbereiten oder eigene Arbeitsnotizen pflegen.",
                14f, JarvisDesign.Colors.textSecondary))
            team?.let { current ->
                val selection = current.snapshot()
                body.addView(label("Chefbot: " + (page.items.firstOrNull { it.agentId == selection.chiefId }?.displayName
                    ?: selection.chiefId ?: "noch nicht bestimmt")))
                body.addView(action("Teamaufträge und Ergebnisse") { if (live(token)) showTeam() })
                body.addView(ui.label("Profile sind keine Kompetenzbelege. Zwei freigegebene Spezialisten arbeiten " +
                    "nacheinander mit einem lokalen Modell.", 13f, JarvisDesign.Colors.textSecondary))
            }
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
        if (term.isEmpty()) return store.page(afterId, 20)
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
        team?.let { currentTeam ->
            val selection = currentTeam.snapshot()
            addView(ui.label(when (profile.agentId) {
                selection.chiefId -> "CHEFBOT · keine zusätzlichen Rechte"
                selection.specialistIds.getOrNull(0) -> "TEAM · Analyse"
                selection.specialistIds.getOrNull(1) -> "TEAM · Gegenprüfung"
                else -> "Nicht für den Teamauftrag ausgewählt"
            }, 12f, ui.accent(profile)))
        }
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
            Triple(profile, store.workingContext(profile.agentId, profile.agentId).orEmpty(), team?.skillIndex().orEmpty())
        }) { (profile, notes, skills) ->
            body.removeAllViews()
            body.addView(ui.identity(profile))
            val persona = ChiefBotPersonas.describe(profile)
            body.addView(label(persona.tagline))
            body.addView(ui.label("ARBEITSWEISE", 12f, ui.accent(profile)))
            body.addView(label(persona.approach))
            if (at.heinz.deviceai.chief.ChiefBotPractice.supports(profile.agentId)) {
                body.addView(label("Fachübungen: Öffne das Gespräch mit ${profile.displayName} und schreibe " +
                    "Training 1 bis Training 6. Die Antwort wird bewertet; eine Korrektur hilft bei der nächsten Übung. " +
                    "Die letzten beiden Aufgaben prüfen die Anwendung auf andere Fälle."))
            }
            body.addView(statusLabel(profile, "REGISTRIERT · Kein aktueller Auftrag\n" +
                "Geprüfte Fähigkeiten: noch keine\nLetztes bestätigtes Arbeitsergebnis: noch keines"))
            onSelect?.let { select -> body.addView(action("Mit ${profile.displayName} sprechen") {
                if (live(token)) { close(); select(profile) }
            }) }
            onAssign?.let { body.addView(action("Auftrag geben") { if (live(token)) showTask(profile) }) }
            onAssign?.let { assign ->
                body.addView(action("Kotlin-Reparaturübung vorbereiten") {
                    if (live(token) && assign(ChiefBotTaskRequest(profile.agentId, profile.profileVersion,
                            ChiefEngineeringPracticeCurriculum.KOTLIN_REPAIR_GOAL, ChiefBotTaskMode.CODING_DRAFT))) close()
                })
                body.addView(ui.label("Öffnet den bestehenden CCC1-Entwurf: Quell-SHA, Dateien, feste Tests " +
                    "und Freigabe müssen ergänzt werden. Keine Ausführung oder Gewichtsänderung.",
                    13f, JarvisDesign.Colors.textSecondary))
            }
            team?.let { currentTeam ->
                val selection = currentTeam.snapshot()
                body.addView(action("Als Chefbot bestimmen") {
                    if (live(token)) load(token, body, {
                        currentTeam.configure(profile.agentId, selection.specialistIds.filterNot { it == profile.agentId },
                            selection.revision)
                    }) { showProfile(profile.agentId) }
                })
                if (selection.chiefId != null && selection.chiefId != profile.agentId) {
                    val included = profile.agentId in selection.specialistIds
                    body.addView(action(if (included) "Aus Team entfernen" else "Für Textprüfung ins Team aufnehmen") {
                        if (live(token)) load(token, body, {
                            val next = if (included) selection.specialistIds - profile.agentId
                                else selection.specialistIds + profile.agentId
                            currentTeam.configure(requireNotNull(selection.chiefId), next, selection.revision)
                        }) { showProfile(profile.agentId) }
                    }.apply { isEnabled = included || selection.specialistIds.size < 2 })
                }
                body.addView(label("Fachgebiet: ${profile.role}\n" +
                    "Modellwissen: vom geladenen Modell abhängig, nicht geprüft.\n" +
                    "Team-Werkzeuge: keine (nur übergebener Text).\n" +
                    "Coding: vorhandener CCC1-Entwurf; Compiler/Arbeitsbereich separat prüfen.\n" +
                    "Unabhängig bestätigte Coding-Kompetenz: noch kein angebundener Beleg."))
                body.addView(label("Fachanleitungen (bestehende Skill-Bibliothek): " +
                    if (skills.isEmpty()) "noch keine gespeichert." else skills.joinToString("\n", prefix = "\n") {
                        "${it.name} · ${it.status} · ${it.whenToUse}"
                    }))
                body.addView(ui.label("Nur eine passende, freigegebene Anleitung wird bei Bedarf geladen. " +
                    "Eine Anleitung ist keine Werkzeugfreigabe und kein Kompetenznachweis dieses Bots.",
                    13f, JarvisDesign.Colors.textSecondary))
                body.addView(action("Aufgaben und Ergebnisbelege ansehen") { if (live(token)) showTeam(profile.agentId) })
            }
            body.addView(label("Arbeitsplatz: ${profile.workspaceId}\n" +
                "Speicherort: private App-Daten auf diesem Gerät\n" +
                "Ausführung: noch keine isolierte Umgebung verbunden.\n" +
                "Arbeitsnotizen sind getrennt gespeichert; dies ist keine Betriebssystem-Isolation."))
            profile.contextObjectId?.let { hash ->
                body.addView(label("Gespeicherte Arbeitsnotiz · Objekt $hash"))
            }
            body.addView(action("Private Aufzeichnungen suchen") {
                if (live(token)) showPrivateRecords(profile.agentId)
            })
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

    private fun showPrivateRecords(agentId: String, query: String = "") {
        val body = column()
        body.addView(label("Private Aufzeichnungen werden geladen …"))
        val token = open("Bot · Private Aufzeichnungen", body)
        load(token, body, { store.privateRecords(agentId, agentId, query) }) { snapshot ->
            body.removeAllViews()
            body.addView(ui.identity(snapshot.profile))
            body.addView(label("Aktuelle Arbeitsnotiz und gespeicherter Gesprächsstand dieses Bots. " +
                "Die Inhalte sind unbestätigt und werden nicht an andere Bots weitergegeben."))
            body.addView(label("Das Datum bezeichnet die erste Speicherung des gesamten Inhaltsstands, " +
                "nicht einzelner Aussagen. Gelöschte und ersetzte Stände werden nicht durchsucht."))
            val search = edit(query, 160).apply {
                hint = "Text in privaten Aufzeichnungen suchen"
                contentDescription = "Private Aufzeichnungen durchsuchen"
            }
            body.addView(search)
            body.addView(action("Suchen") {
                if (live(token)) showPrivateRecords(agentId, search.text.toString())
            })
            if (query.isNotEmpty()) body.addView(action("Alle aktuellen Aufzeichnungen") {
                if (live(token)) showPrivateRecords(agentId)
            })
            if (snapshot.records.isEmpty()) body.addView(label(if (query.isBlank())
                "Keine aktuellen privaten Aufzeichnungen gespeichert."
            else "Keine aktuelle private Aufzeichnung enthält diesen Suchtext."))
            val date = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss z", java.util.Locale.getDefault())
            snapshot.records.forEach { record ->
                body.addView(ui.label(when (record.kind) {
                    ChiefBotPrivateRecordKind.WORKING_NOTES -> "ARBEITSNOTIZ"
                    ChiefBotPrivateRecordKind.CONVERSATION_SNAPSHOT -> "GESPRÄCHSSTAND"
                }, 12f, ui.accent(snapshot.profile)))
                body.addView(label("Erste Speicherung dieses Gesamtstands: " + date.format(java.util.Date(record.createdAtMillis))))
                body.addView(label(record.text))
            }
            body.addView(action("Zurück zum Arbeitsbereich") {
                if (live(token)) showProfile(agentId)
            })
        }
    }

    private fun edit(value: String, limit: Int, multiline: Boolean = false) = ui.editor(multiline).apply {
        setText(value)
        filters = arrayOf(InputFilter.LengthFilter(limit))
    }

    private fun showTeam(agentId: String? = null) {
        val currentTeam = team ?: return
        val body = column()
        val token = open("Mein KI-Team · Aufträge", body)
        var rendered: Any? = null
        fun render() {
            if (!live(token)) return
            val state = currentTeam.snapshot()
            val key = state to currentTeam.storageError
            if (rendered == key) return
            rendered = key
            body.removeAllViews()
            body.addView(label("Chefbot: ${state.chiefId ?: "nicht gewählt"}", 20f))
            body.addView(label("Analyse: ${state.specialistIds.getOrNull(0) ?: "nicht gewählt"}\n" +
                "Gegenprüfung: ${state.specialistIds.getOrNull(1) ?: "nicht gewählt"}"))
            currentTeam.storageError?.let { body.addView(ui.label(
                "Ergebnis konnte nicht sicher gespeichert werden: $it. Nicht erneut automatisch ausführen.",
                14f, JarvisDesign.Colors.warn)) }
            body.addView(label("Fester Ablauf: Analyse → kritische Gegenprüfung. Zwei Modellaufrufe, " +
                "höchstens zwei Minuten, keine Werkzeuge. Modelltexte bleiben ungeprüft."))
            body.addView(action("Chefbot einen Textprüfauftrag geben") { if (live(token)) showTeamDraft() }.apply {
                isEnabled = state.chiefId != null && state.specialistIds.size == 2 && state.tasks.none { it.active }
            })
            val rows = state.tasks.asReversed().filter {
                agentId == null || agentId in listOf(it.chiefId, it.analystId, it.reviewerId)
            }
            if (rows.isEmpty()) body.addView(label("Noch keine Teamaufträge gespeichert."))
            rows.forEach { task ->
                val card = column().apply { background = ui.surface() }
                card.addView(label(task.request, 17f))
                card.addView(label("${task.id}\n${teamStatus(task)}\n${task.diagnostic}"))
                task.responses.forEach { response ->
                    val proposed = AgentFollowupEnvelope.parse(response.text)
                    card.addView(ui.label("${response.agentId} · ${response.stage}", 14f, JarvisDesign.Colors.cyan))
                    card.addView(label(proposed?.answer ?: response.text))
                    card.addView(ui.label("Ausführung aufgezeichnet · Inhalt NICHT unabhängig geprüft\n" +
                        "Modell SHA-256: ${task.modelSha256}\n" +
                        "Eingabe SHA-256: ${response.promptSha256}\n" +
                        "Ausgabe SHA-256: ${response.outputSha256}\nDauer: ${response.elapsedMillis} ms",
                        12f, JarvisDesign.Colors.textSecondary))
                    if (proposed != null) {
                        card.addView(label("Folgevorschlag · noch nicht beauftragt\n${proposed.proposal.goal}\n" +
                            "Begründung des Workers: ${proposed.proposal.reason}"))
                        val feedback = label("")
                        card.addView(feedback)
                        card.addView(action("Folgevorschlag prüfen") {
                            if (live(token)) load(token, body, { currentTeam.prepareFollowup(task.id, response.stage) }) { decision ->
                                val review = decision.review
                                if (review != null && currentTeam.isFollowupContextCurrent(review))
                                    showTeamDraft(review.proposal.goal, review)
                                else {
                                    review?.revoke()
                                    feedback.text = followupDecisionText(decision.code)
                                }
                            }
                        }.apply { isEnabled = task.status == ChiefTeamStatus.RESPONSES_RECORDED })
                    }
                }
                listOf(task.analystId, task.reviewerId).forEach { id ->
                    card.addView(action("Bot $id öffnen") { if (live(token)) showProfile(id) })
                }
                if (task.active) card.addView(action("Auftrag stoppen") {
                    if (live(token)) onTeamStop?.invoke(task.id)
                }) else card.addView(action("Als neuen Entwurf prüfen (keine automatische Wiederholung)") {
                    if (live(token)) showTeamDraft(task.request)
                })
                body.addView(card)
            }
            if (state.tasks.isNotEmpty()) body.addView(action("Abgeschlossene Textprüfungen löschen") {
                if (live(token)) AlertDialog.Builder(activity)
                    .setTitle("Teamverlauf löschen?")
                    .setMessage("Alle abgeschlossenen Textprüfungen und Antworten werden gelöscht. " +
                        "Offene Entwürfe müssen neu freigegeben werden. Private Bot-Gespräche, Profile, " +
                        "Arbeitsnotizen und CCC1-Belege bleiben erhalten.")
                    .setNegativeButton("Behalten", null)
                    .setPositiveButton("Löschen") { _, _ ->
                        if (live(token)) load(token, body, { currentTeam.clearHistory(state.revision) }) { showTeam(agentId) }
                    }.show()
            }.apply { isEnabled = state.tasks.none { it.active } && !currentTeam.active() })
            body.addView(action("Zur Teamübersicht") { if (live(token)) show() })
        }
        teamRefresh = ::render
        render()
    }

    private fun followupDecisionText(code: ChiefFollowupDecisionCode): String = when (code) {
        ChiefFollowupDecisionCode.UNSUPPORTED_CAPABILITY -> "Nicht angenommen: Dafür ist kein freigegebener Textprüfablauf angeschlossen."
        ChiefFollowupDecisionCode.TOOLS_NOT_AUTHORIZED -> "Nicht angenommen: Der Vorschlag verlangt Werkzeuge außerhalb dieses Textauftrags."
        ChiefFollowupDecisionCode.MODEL_UNAVAILABLE -> "Nicht angenommen: Das lokale Modell ist nicht bereit."
        ChiefFollowupDecisionCode.MODEL_BUDGET_UNAVAILABLE -> "Nicht angenommen: Das Profilbudget erlaubt keinen weiteren Modellaufruf."
        ChiefFollowupDecisionCode.DUPLICATE_GOAL, ChiefFollowupDecisionCode.ALREADY_SUBMITTED ->
            "Nicht erneut beauftragt: Dieser Vorschlag oder dieses Ziel ist bereits im Teamverlauf."
        ChiefFollowupDecisionCode.TEAM_BUSY -> "Nicht angenommen: Ein Teamauftrag läuft noch."
        ChiefFollowupDecisionCode.HISTORY_FULL -> "Nicht angenommen: Der Teamverlauf ist voll."
        ChiefFollowupDecisionCode.SOURCE_NOT_COMPLETE -> "Nicht angenommen: Analyse und Gegenprüfung sind noch nicht vollständig gespeichert."
        ChiefFollowupDecisionCode.SOURCE_CHANGED, ChiefFollowupDecisionCode.NO_PROPOSAL,
        ChiefFollowupDecisionCode.OWNER_APPROVAL_REQUIRED -> "Vorschlag oder Sitzung geändert. Bitte erneut prüfen."
    }

    private fun showTeamDraft(initial: String = "", followup: ChiefFollowupReview? = null) {
        val currentTeam = team ?: return
        val selection = currentTeam.snapshot()
        val requestId = followup?.requestId ?: ("team-" + UUID.randomUUID())
        val body = column()
        val token = open("Chefbot · Textprüfung freigeben", body)
        body.addView(label("Dein Text geht ausschließlich an die zwei ausgewählten Spezialisten. " +
            "Private Bot-Gespräche und Arbeitsnotizen werden nicht geteilt. " +
            "Dies prüft Text; es kompiliert keinen Code und baut keine APK."))
        followup?.let { body.addView(label("Geprüfter Folgevorschlag: neuer Auftrag, zwei neue Modellaufrufe, " +
            "höchstens zwei Minuten innerhalb der Profilbudgets, keine Werkzeuge. " +
            "Bisherige Freigaben und gespeicherte Angaben werden nicht übernommen. " +
            "Analyse: ${selection.specialistIds.getOrNull(0)} · Gegenprüfung: ${selection.specialistIds.getOrNull(1)}. " +
            "Das gebundene Ziel ist nicht bearbeitbar; für Änderungen einen normalen neuen Entwurf öffnen.")) }
        val text = ui.editor(multiline = true).apply {
            filters = arrayOf(InputFilter.LengthFilter(1_800))
            setText(initial); hint = "Auftrag und zu prüfenden Text eingeben (max. 1800 Zeichen)"
            contentDescription = "Teamauftrag"; tag = "bot-team-request"
            if (followup != null) { keyListener = null; isFocusable = false; isCursorVisible = false }
        }
        body.addView(text)
        var shared: SharedOwnerKnowledgeGrant? = null
        var pendingFollowup = followup
        var sharingRevision = 0L
        var selectionDialog: AlertDialog? = null
        val sharedSummary = label("Keine gespeicherten Angaben für diesen Auftrag freigegeben.")
        abandonDraft = {
            sharingRevision++
            shared?.revoke()
            shared = null
            pendingFollowup?.revoke()
            pendingFollowup = null
            selectionDialog?.dismiss()
            selectionDialog = null
        }
        if (currentTeam.supportsSharedKnowledge()) {
            body.addView(sharedSummary)
            body.addView(action("Bestätigte Angaben auswählen") {
                if (!live(token)) return@action
                val request = text.text.toString()
                if (request.isBlank()) {
                    sharedSummary.text = "Zuerst den konkreten Auftrag eingeben."
                    return@action
                }
                val selectedRevision = ++sharingRevision
                shared?.revoke()
                shared = null
                sharedSummary.text = "Bestätigte Angaben werden geprüft …"
                load(token, body, { currentTeam.reviewSharedKnowledge(requestId, request, selection.revision) }) { review ->
                    if (sharingRevision != selectedRevision) return@load
                    if (!review.scope.matchesRequest(requestId, text.text.toString())) {
                        sharedSummary.text = "Der Auftrag wurde geändert. Angaben bitte neu auswählen."
                        return@load
                    }
                    if (review.facts.isEmpty()) {
                        sharedSummary.text = "Keine aktiven bestätigten Angaben vorhanden."
                        return@load
                    }
                    val selected = BooleanArray(review.facts.size)
                    val recipients = review.scope.recipients.joinToString(", ") { "${it.displayName} (${it.agentId})" }
                    selectionDialog = AlertDialog.Builder(activity)
                        .setCustomTitle(label("Angaben für $recipients\nNur für diesen Auftrag; höchstens fünf kurze Angaben. " +
                            "Kein Zugriff auf private Botnotizen oder Gespräche.").apply {
                            setPadding(dp(16), dp(12), dp(16), dp(8))
                        })
                        .setMultiChoiceItems(review.facts.map { it.text }.toTypedArray(), selected) { _, which, checked ->
                            selected[which] = checked
                        }
                        .setNegativeButton("Nicht teilen", null)
                        .setPositiveButton("Für diesen Auftrag freigeben") { _, _ ->
                            if (!live(token) || sharingRevision != selectedRevision) return@setPositiveButton
                            val ids = review.facts.filterIndexed { index, _ -> selected[index] }.map { it.id }.toSet()
                            if (ids.isEmpty()) {
                                sharedSummary.text = "Keine gespeicherten Angaben freigegeben."
                                return@setPositiveButton
                            }
                            load(token, body, { runCatching { review.approve(ids, ownerApproved = true) } }) { result ->
                                result.fold({ grant ->
                                    if (sharingRevision != selectedRevision) {
                                        grant.revoke()
                                        return@fold
                                    }
                                    if (!grant.scope.matchesRequest(requestId, text.text.toString())) {
                                        grant.revoke()
                                        sharedSummary.text = "Der Auftrag wurde geändert. Angaben bitte neu auswählen."
                                    } else {
                                        shared = grant
                                        sharedSummary.text = "Für $recipients freigegeben, nur für diesen Auftrag:\n" +
                                            grant.facts.joinToString("\n") { it.text } +
                                            "\nPassende Angaben dürfen als Kontext abgerufen werden. " +
                                            "Auftrag stoppen oder die betroffene Erinnerung widerrufen beendet diese Freigabe."
                                    }
                                }, { error ->
                                    if (sharingRevision != selectedRevision) return@fold
                                    sharedSummary.text = if (error.message == "BOT_SHARED_CONTEXT_BUDGET")
                                        "Bitte höchstens fünf kürzere Angaben wählen; der Kontext wurde nicht gekürzt oder freigegeben."
                                    else "Angaben geändert, widerrufen oder nicht sicher lesbar. Bitte neu auswählen."
                                })
                            }
                        }.show()
                }
            })
            body.addView(action("Angabenauswahl verwerfen") {
                if (live(token)) {
                    sharingRevision++
                    shared?.revoke()
                    shared = null
                    sharedSummary.text = "Keine gespeicherten Angaben freigegeben."
                }
            })
            text.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    sharingRevision++
                    if (shared != null) {
                        shared?.revoke()
                        shared = null
                        sharedSummary.text = "Der Auftrag wurde geändert. Angaben bitte neu auswählen."
                    }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        val consent = android.widget.CheckBox(activity).apply {
            this.text = "Diese Textprüfung mit zwei Spezialisten freigeben"
            setTextColor(JarvisDesign.Colors.text); minHeight = dp(48); tag = "bot-team-consent"
        }
        body.addView(consent)
        val feedback = label("")
        val submit = action("An Chefbot übergeben") {}
        submit.setOnClickListener {
            if (!live(token)) return@setOnClickListener
            if (!consent.isChecked || text.text.isBlank() || text.text.length > 1_800) {
                feedback.text = "Bitte einen Text mit 1–1800 Zeichen eingeben und die Freigabe bestätigen."
            } else if (followup != null && (!currentTeam.isFollowupContextCurrent(followup) ||
                    text.text.toString() != followup.proposal.goal)) {
                feedback.text = "Der Folgevorschlag ist nicht mehr gültig. Bitte erneut prüfen."
            } else {
                submit.isEnabled = false
                if (onTeamSubmit?.invoke(requestId, text.text.toString(), selection.revision, shared, pendingFollowup) == true) {
                    shared = null
                    pendingFollowup = null
                    showTeam()
                }
                else { submit.isEnabled = true; feedback.text = "Nicht angenommen. Ein anderer Modelllauf ist noch belegt." }
            }
        }
        body.addView(feedback); body.addView(submit)
        body.addView(action("Zurück zu den Aufträgen") { if (live(token)) showTeam() })
    }

    private fun teamStatus(task: ChiefTeamTask): String = when (task.status) {
        ChiefTeamStatus.ACCEPTED -> "Angenommen"
        ChiefTeamStatus.ANALYZING -> "Analyse beauftragt"
        ChiefTeamStatus.REVIEWING -> "Gegenprüfung beauftragt"
        ChiefTeamStatus.RESPONSES_RECORDED -> "Beide Antworten gespeichert · Inhalt ungeprüft"
        ChiefTeamStatus.BLOCKED -> "Blockiert · Voraussetzung fehlt"
        ChiefTeamStatus.FAILED -> "Fehlgeschlagen"
        ChiefTeamStatus.CANCELLED -> "Abgebrochen · keine automatische Wiederholung"
        ChiefTeamStatus.INTERRUPTED -> "Unterbrochen · vor neuem Auftrag prüfen"
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
