package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.text.InputType
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

/**
 * EXP-00/08: owner's preview-only Android workstation launcher.
 * Only fixed local Termux build commands are sent; raw chat never becomes a shell command.
 */
class ContinuumMobileBuilderActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var typedRunButton: Button
    private var selectedProject = ContinuumOwnedProject.NOTES
    private val preferences by lazy {
        getSharedPreferences(ContinuumMobileBuildResultReceiver.PREFERENCES, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun px(dp: Int) = (dp * density + 0.5f).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(24), px(20), px(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        fun paragraph(message: String, bold: Boolean = false): TextView = TextView(this).apply {
            text = message
            textSize = if (bold) 22f else 16f
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, px(10), 0, px(10))
            column.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val backToAgent = Button(this).apply {
            text = "← Zurück zu Device AI"
            isAllCaps = false
            contentDescription = "Builder schließen und zur vorherigen Device-AI-Oberfläche zurückkehren"
            setOnClickListener { finish() }
        }
        column.addView(backToAgent, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, px(48)))
        column.addView(Button(this).apply {
            text = "M5 · Gespräch zu Projektplan (Forschung)"
            isAllCaps = false
            setOnClickListener { startActivity(Intent(this@ContinuumMobileBuilderActivity, ContinuumM5LabActivity::class.java)) }
        })
        paragraph("Continuum · Android Builder", true)
        paragraph("Entwicklung direkt auf diesem Smartphone. Termux und ein geprüfter Device-AI-Checkout unter ~/device-ai sind erforderlich.")
        status = paragraph("Noch kein mobiler Geräte-Build bestätigt.")
        status.setTextIsSelectable(true)
        paragraph("AUTONOMER CODING-AGENT · TYPISIERTER AUFTRAG",true)
        val incoming = ContinuumTypedCodingBridge.parseAuthorization(
            intent.getStringExtra(ContinuumTypedCodingBridge.INTENT_JSON),
            intent.getStringExtra(ContinuumTypedCodingBridge.INTENT_SHA))
        if(incoming != null) {
            paragraph("Auftrag: ${incoming.task.goal.take(150)}\n" +
                "Commit ${incoming.task.sourceCommit.take(12)} · SHA ${incoming.taskSha256.take(16)}…\n" +
                "Vorbereitung startet kein KI-Modell und nimmt keinen Patch an.")
            column.addView(Button(this).apply {
                text = "Bestätigten Auftrag in CCC1 vorbereiten"
                isAllCaps = false
                setOnClickListener {
                    val review = ContinuumTypedCodingBridge.preparationReview(incoming)
                    if (review == null) {
                        status.text = "Auftragsbindung ungültig. Keine CCC1-Vorbereitung."
                        return@setOnClickListener
                    }
                    val reviewText = TextView(this@ContinuumMobileBuilderActivity).apply {
                        text = review
                        textSize = 16f
                        setTextIsSelectable(true)
                        setPadding(px(20), px(8), px(20), px(16))
                    }
                    val reviewScroll = ScrollView(this@ContinuumMobileBuilderActivity).apply {
                        addView(reviewText)
                    }
                    AlertDialog.Builder(this@ContinuumMobileBuilderActivity)
                        .setTitle("Vollständigen Coding-Auftrag prüfen")
                        .setView(reviewScroll)
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Vorbereiten") { _, _ ->
                            if (ContinuumTypedCodingBridge.preparationReview(incoming) != review) {
                                status.text = "Auftragsdaten seit der Prüfung verändert. Nichts vorbereitet."
                            } else {
                                dispatchTypedPrepare(incoming)
                            }
                        }
                        .show()
                }
            },LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,px(54)))
        } else if(intent.hasExtra(ContinuumTypedCodingBridge.INTENT_JSON)) {
            paragraph("Übergebener Coding-Auftrag ist ungültig. Nichts vorbereitet.")
        }
        typedRunButton = Button(this).apply {
            text = "Vorbereiteten Coding-Auftrag ausführen"
            isAllCaps = false
            isEnabled = false
            setOnClickListener {
                val id = preferences.getString(ContinuumMobileBuildResultReceiver.TYPED_PREPARED_ID,null)
                val hash = preferences.getString(ContinuumMobileBuildResultReceiver.TYPED_PREPARED_SHA,null)
                val commit = preferences.getString(ContinuumMobileBuildResultReceiver.TYPED_PREPARED_COMMIT,null)
                if(id == null || hash == null || commit == null || preferences.contains(ContinuumMobileBuildResultReceiver.JOB_ID)){
                    status.text="Kein bestätigter Coding-Auftrag bereit. Nichts gestartet."
                } else AlertDialog.Builder(this@ContinuumMobileBuilderActivity)
                    .setTitle("Lokales Coding-Modell jetzt ausführen?")
                    .setMessage("Nur der zuvor gebundene Auftrag ${id.take(40)} / ${hash.take(16)}… wird gestartet. " +
                        "CCC1, Testketten, Reparatur und unabhängige Prüfung bleiben unverändert. " +
                        "Dies kann längere Zeit dauern; kein Patch wird übernommen, keine APK installiert.")
                    .setNegativeButton("Nicht starten",null)
                    .setPositiveButton("Jetzt ausführen") { _, _ -> dispatchTypedRun(id,hash) }
                    .show()
            }
        }
        column.addView(typedRunButton,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,px(56)))
        selectedProject = ContinuumOwnedProject.ofId(preferences.getString("selected_m4_project_v1", null))
            ?: ContinuumOwnedProject.NOTES
        // The M5 handoff is a one-shot selection: applied on the first creation only and stored like a manual choice.
        // Re-applying the still attached Intent extra after fold/rotation would revert the owner's later explicit switch.
        if (savedInstanceState == null) ContinuumOwnedProject.ofId(intent.getStringExtra("m5_project"))?.let {
            if (intent.getStringExtra("m5_plan_sha") == it.planSha256) {
                selectedProject = it
                preferences.edit().putString("selected_m4_project_v1", it.id).commit()
            }
        }
        val projectCatalogReady = runCatching {
            assets.open("continuum-owned-project-plans-v1.json").bufferedReader().use {
                ContinuumOwnedProjectPlan.readCatalog(it.readText())
            }
            true
        }.getOrDefault(false)
        for (operation in ContinuumMobileBuildPlan.Operation.entries) {
            if (operation == ContinuumMobileBuildPlan.Operation.PROJECT_CREATE) {
                paragraph("M4 · EIGENE ANDROID-APP", true)
                paragraph("Wähle eine getrennte Notiz- oder Zähler-App mit zwei Ansichten und privater lokaler Speicherung. " +
                    "Nur die beiden geprüften Projektpläne unter ~/continuum-projects werden ausgeführt. " +
                    "Bestehende Projekte, Continuum-Daten und installierte Apps werden nicht überschrieben.")
                column.addView(Button(this).apply {
                    text = "Projekt: ${selectedProject.title} (antippen zum Wechseln)"
                    isAllCaps = false
                    isEnabled = projectCatalogReady
                    setOnClickListener {
                        selectedProject = ContinuumOwnedProject.entries[
                            (selectedProject.ordinal + 1) % ContinuumOwnedProject.entries.size]
                        preferences.edit().putString("selected_m4_project_v1", selectedProject.id).commit()
                        text = "Projekt: ${selectedProject.title} (antippen zum Wechseln)"
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56)))
                if (!projectCatalogReady) paragraph("Projektkatalog ungültig. M4 bleibt bis zu einer geprüften App-Aktualisierung gesperrt.")
            }
            val button = Button(this).apply {
                text = when (operation) {
                    ContinuumMobileBuildPlan.Operation.PROJECT_CREATE -> "M4 · Gewähltes Projekt erzeugen"
                    ContinuumMobileBuildPlan.Operation.PROJECT_BUILD -> "M4 · Gewähltes Projekt testen und bauen"
                    else -> operation.title
                }
                isAllCaps = false
                isEnabled = !operation.name.startsWith("PROJECT_") || projectCatalogReady
                setOnClickListener {
                    val project = selectedProject
                    val confirmation = when (operation) {
                        ContinuumMobileBuildPlan.Operation.BUILD ->
                            "Lokalen Build starten?" to
                                "Termux führt Tests, Lint und einen APK-Build auf diesem Telefon aus. Das kann bis zu 35 Minuten dauern und Akku/Speicher beanspruchen. Keine Installation erfolgt."
                        ContinuumMobileBuildPlan.Operation.PROJECT_CREATE ->
                            "Projekt ${project.title} erzeugen?" to
                                "Termux legt den geprüften Plan unter ~/continuum-projects/${project.id} an. " +
                                "Keine Änderungen an Device AI, keine Installation. Ein bereits vorhandenes Projekt wird nicht überschrieben."
                        ContinuumMobileBuildPlan.Operation.PROJECT_BUILD ->
                            "Projekt ${project.title} testen und bauen?" to
                                "Termux führt im getrennten Projekt Kotlin-Tests, Android-Lint und den Offline-APK-Build aus. " +
                                "Signatur, Paketname und Quellintegrität werden geprüft. Kein automatisches Installieren."
                        else -> null
                    }
                    if (confirmation != null) {
                        AlertDialog.Builder(this@ContinuumMobileBuilderActivity)
                            .setTitle(confirmation.first)
                            .setMessage(confirmation.second)
                            .setNegativeButton("Abbrechen", null)
                            .setPositiveButton("Bestätigen") { _, _ -> dispatch(operation, project) }
                            .show()
                    } else dispatch(operation, project)
                }
            }
            column.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(56)))
        }
        paragraph("EIGENER CODING-AGENT · LOKALER AUFTRAG", true)
        paragraph("Wähle ein vorhandenes, freigegebenes JavaScript-Modul im Continuum-Checkout. " +
            "Der Auftrag überprüft ein exportiertes Verhalten mit deinen Testfällen. " +
            "Ein Patch wird ausschließlich zur Kontrolle erzeugt, niemals automatisch übernommen.")
        fun codingInput(description: String): EditText = EditText(this).apply {
            hint = description
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            textSize = 15f
            column.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(52)))
        }
        val moduleInput = codingInput("Modul: .github/continuum/<name>.mjs")
        val exportInput = codingInput("Exportierter Funktionsname")
        val casesInput = codingInput("""JSON-Testfälle: [{"args":[5],"expected":15}]""")
        val codingButton = Button(this).apply {
            text = "Coding-Auftrag prüfen und starten"
            isAllCaps = false
            setOnClickListener {
                val order = ContinuumMobileCodingJob.newOrder(
                    moduleInput.text.toString().trim(),
                    exportInput.text.toString().trim(),
                    casesInput.text.toString().trim()
                )
                val problems = ContinuumMobileCodingJob.problems(order)
                if (problems.isNotEmpty()) {
                    status.text = problems.joinToString("\n")
                    return@setOnClickListener
                }
                AlertDialog.Builder(this@ContinuumMobileBuilderActivity)
                    .setTitle("Eigenen Coding-Test auf dem Handy ausführen?")
                    .setMessage("Termux prüft nur das angegebene Modul und die Testfälle. " +
                        "Der lokale Coding-Worker kann bis zu 20 Minuten rechnen. " +
                        "Er verändert weder den Hauptbranch noch installierte Apps oder Gewichte. " +
                        "Auch ein erfolgreicher Patch bleibt ausschließlich ein Prüfvorschlag.")
                    .setNegativeButton("Nicht starten", null)
                    .setPositiveButton("Auftrag starten") { _, _ -> dispatchCoding(order) }
                    .show()
            }
        }
        column.addView(codingButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, px(58)))
        paragraph("Coding-Läufe lassen sich derzeit nur über den bestehenden Termux-CLI-Abbruch " +
            "gezielt stoppen. Bei einem offenen Auftrag startet die App keinen zweiten Auftrag.")
        paragraph("CCC1 · REPARATURAUFTRAG", true)
        paragraph("CCC1 führt eine feste Testkette in einem isolierten Arbeitsstand aus. Findet sie einen echten Fehler, " +
            "sucht die bestehende Reparatur-Pipeline einen Patch; ein zweiter, unabhängiger Lauf und der CCC1-Verifier " +
            "prüfen ihn. Der Patch wird nur zur Prüfung angeboten – nie übernommen, gemergt oder installiert.")
        var chain = ContinuumCcc1Bridge.Chain.NODE
        val chainButton = Button(this).apply {
            text = "Testkette: ${chain.cli} (antippen zum Wechseln)"
            isAllCaps = false
            setOnClickListener {
                chain = ContinuumCcc1Bridge.Chain.entries[(chain.ordinal + 1) % ContinuumCcc1Bridge.Chain.entries.size]
                text = "Testkette: ${chain.cli} (antippen zum Wechseln)"
            }
        }
        column.addView(chainButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, px(52)))
        for (operation in ContinuumCcc1Bridge.Operation.entries) {
            val button = Button(this).apply {
                text = operation.title
                isAllCaps = false
                setOnClickListener {
                    val requestedChain = chain
                    val requestedTask = preferences.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null)
                    val question = when (operation) {
                        ContinuumCcc1Bridge.Operation.SUBMIT_REPAIR -> "Reparaturauftrag für die Testkette ${requestedChain.cli} anlegen?" to
                            "Termux prüft zuerst Branch, sauberen Arbeitsstand und Zustandsordner. Der Auftrag wird nur angelegt, noch nicht ausgeführt."
                        ContinuumCcc1Bridge.Operation.RUN_ONCE -> "Nächsten CCC1-Schritt ausführen?" to
                            "Termux führt den nächsten offenen CCC1-Auftrag aus: Testkette, Reparaturkandidaten und unabhängige Prüfung. " +
                            "Das kann bis zu 30 Minuten dauern. Hauptbranch, installierte Apps und Gewichte bleiben unverändert; " +
                            "ein Patch bleibt ein Prüfvorschlag."
                        ContinuumCcc1Bridge.Operation.ABORT -> "CCC1-Auftrag abbrechen?" to
                            "Auftrag ${requestedTask ?: "nicht vorhanden"} wird beendet und ein laufender Job gestoppt. Ein Abbruch zählt nie als Erfolg."
                        else -> null
                    }
                    if (question == null) dispatchCcc1(operation, requestedChain)
                    else AlertDialog.Builder(this@ContinuumMobileBuilderActivity)
                        .setTitle(question.first)
                        .setMessage(question.second)
                        .setNegativeButton("Nicht starten", null)
                        .setPositiveButton("Bestätigen") { _, _ ->
                            if (operation == ContinuumCcc1Bridge.Operation.ABORT &&
                                preferences.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null) != requestedTask) {
                                status.text = "Der offene CCC1-Auftrag hat sich geändert. Bitte den Abbruch neu prüfen."
                                return@setPositiveButton
                            }
                            dispatchCcc1(operation, requestedChain)
                        }
                        .show()
                }
            }
            column.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(52)))
        }
        val refreshButton = Button(this).apply {
            text = "Ergebnis in der App aktualisieren"
            isAllCaps = false
            setOnClickListener { refreshReceipt() }
        }
        column.addView(refreshButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, px(56)))
        paragraph("Voraussetzungen: Termux mit Node.js 20+, Git, JDK 17, Gradle 8.11.1, ARM64-aapt2, SDK 36 und apksigner. In ~/.termux/termux.properties: allow-external-apps=true.")
        paragraph("Freigabe: 'Run commands in Termux'. Es werden nur fest freigegebene Operationen gestartet; Ergebnis und Exitcode kommen über einen einmaligen Android-Rückkanal zurück.")
        val scroll = ScrollView(this)
        scroll.addView(column)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshReceipt()
    }

    private fun refreshReceipt() {
        val last = preferences.getString(ContinuumMobileBuildResultReceiver.LAST_MESSAGE, null)
        val sha = preferences.getString(ContinuumMobileBuildResultReceiver.LAST_SHA, null)
        val pending = preferences.getString(ContinuumMobileBuildResultReceiver.JOB_ID, null)
        val pendingOperation = preferences.getString(ContinuumMobileBuildResultReceiver.JOB_OPERATION, null)
        val started = preferences.getLong(ContinuumMobileBuildResultReceiver.JOB_START, 0L)
        val ccc1Task = preferences.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null)
        val abortPending = preferences.getString(ContinuumMobileBuildResultReceiver.ABORT_JOB_ID, null) != null
        val abortMessage = preferences.getString(ContinuumMobileBuildResultReceiver.ABORT_MESSAGE, null)
        val recovery = if (preferences.contains(ContinuumMobileBuildResultReceiver.STATUS_JOB_ID))
            "M4-Belegabfrage wartet auf Rückmeldung."
        else preferences.getString(ContinuumMobileBuildResultReceiver.STATUS_MESSAGE, null)
        val ccc1 = listOfNotNull(
            ccc1Task?.let { "Offener CCC1-Auftrag: $it" },
            if (abortPending) "Ein CCC1-Abbruch ist unterwegs; kein Ergebnis bestätigt." else abortMessage
        ).joinToString("\n").let { if (it.isEmpty()) "" else "\n$it" }
        typedRunButton.isEnabled = pending == null &&
            preferences.getString(ContinuumMobileBuildResultReceiver.TYPED_PREPARED_ID,null)!=null &&
            preferences.getString(ContinuumMobileBuildResultReceiver.TYPED_PREPARED_SHA,null)!=null &&
            preferences.getString(ContinuumMobileBuildResultReceiver.TYPED_PREPARED_COMMIT,null)?.matches(Regex("[a-f0-9]{40}"))==true
        status.text = when {
            pending != null ->
                "Ein Termux-Auftrag läuft oder wartet auf seine Rückmeldung. Gestartet: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(started))}. Kein Erfolg bestätigt." +
                    (if (pendingOperation == "CODE_TEST") "\nCoding-Auftrags-ID: $pending (für gezielten Termux-Abbruch)." else "")
            last != null -> last + if (sha != null) "\nSHA-256: $sha" else ""
            else -> "Noch kein Rückbericht vom mobilen Builder. Kein Geräte-Build bestätigt."
        } + ccc1 + (recovery?.let { "\n$it" } ?: "")
    }

    private fun dispatch(operation: ContinuumMobileBuildPlan.Operation, project: ContinuumOwnedProject) {
        val requestId = UUID.randomUUID().toString()
        val plan = ContinuumMobileBuildPlan.command(operation, project)
        dispatchApprovedCommand(operation.name, requestId, plan,
            slot = if (operation == ContinuumMobileBuildPlan.Operation.PROJECT_STATUS) ResultSlot.PROJECT_STATUS else ResultSlot.MAIN,
            project = if (operation.name.startsWith("PROJECT_")) project else null)
    }

    private fun dispatchCoding(order: ContinuumMobileCodingJob.Order) {
        val problems = ContinuumMobileCodingJob.problems(order)
        if (problems.isNotEmpty()) {
            status.text = problems.joinToString("\n")
            return
        }
        dispatchApprovedCommand("CODE_TEST", order.jobId, ContinuumMobileCodingJob.command(order))
    }

    private fun dispatchTypedPrepare(authorization:ContinuumCodingTask.Authorization) {
        val plan = runCatching { ContinuumTypedCodingBridge.commandPrepare(authorization) }.getOrNull()
        if(plan == null) {
            status.text="Ungültiger gebundener Coding-Auftrag. Keine Ausführung."
            return
        }
        dispatchApprovedCommand(ContinuumTypedCodingBridge.Operation.PREPARE.callback,
            UUID.randomUUID().toString(),plan,
            typedId=authorization.task.id,typedHash=authorization.taskSha256,
            typedCommit=authorization.task.sourceCommit)
    }

    private fun dispatchTypedRun(id:String,hash:String) {
        val commit=preferences.getString(ContinuumMobileBuildResultReceiver.TYPED_PREPARED_COMMIT,null)
        if(preferences.getString(ContinuumMobileBuildResultReceiver.TYPED_PREPARED_ID,null)!=id ||
           preferences.getString(ContinuumMobileBuildResultReceiver.TYPED_PREPARED_SHA,null)!=hash ||
           commit==null || !commit.matches(Regex("[a-f0-9]{40}"))) {
            status.text="Vorbereiteter Coding-Auftrag wurde geändert. Nichts ausgeführt."
            return
        }
        val plan=runCatching { ContinuumTypedCodingBridge.commandRun(id,hash) }.getOrNull()
        if(plan == null) {
            status.text="Ungültige Coding-Freigabe. Nichts ausgeführt."
            return
        }
        dispatchApprovedCommand(ContinuumTypedCodingBridge.Operation.RUN.callback,
            UUID.randomUUID().toString(),plan,typedId=id,typedHash=hash,typedCommit=commit)
    }

    /** CCC1 step: fixed operation, chain from the selector, this request id as idempotent order id. */
    private fun dispatchCcc1(operation: ContinuumCcc1Bridge.Operation, chain: ContinuumCcc1Bridge.Chain) {
        val requestId = UUID.randomUUID().toString()
        val task = preferences.getString(ContinuumMobileBuildResultReceiver.CCC1_TASK, null)
        if (operation == ContinuumCcc1Bridge.Operation.ABORT && task == null) {
            status.text = "Kein offener CCC1-Auftrag bekannt. Zuerst den CCC1-Status abrufen."
            return
        }
        val plan = try {
            ContinuumCcc1Bridge.command(operation, chain, orderId = requestId, taskId = task)
        } catch (_: IllegalArgumentException) {
            status.text = "Ungültiger CCC1-Auftrag; nichts gestartet."
            return
        }
        val slot = if (operation == ContinuumCcc1Bridge.Operation.ABORT) ResultSlot.CCC1_ABORT else ResultSlot.MAIN
        dispatchApprovedCommand(operation.jobOperation, requestId, plan, slot,
            abortTask = if (operation == ContinuumCcc1Bridge.Operation.ABORT) task else null)
    }

    /**
     * MAIN serializes writing/build jobs. Abort and read-only M4 recovery each have
     * one separate slot; neither replaces a pending build's callback identity.
     */
    private enum class ResultSlot(val idKey: String, val operationKey: String?, val startKey: String, val projectKey: String? = null) {
        MAIN(ContinuumMobileBuildResultReceiver.JOB_ID, ContinuumMobileBuildResultReceiver.JOB_OPERATION, ContinuumMobileBuildResultReceiver.JOB_START, ContinuumMobileBuildResultReceiver.JOB_PROJECT),
        CCC1_ABORT(ContinuumMobileBuildResultReceiver.ABORT_JOB_ID, null, ContinuumMobileBuildResultReceiver.ABORT_JOB_START),
        PROJECT_STATUS(ContinuumMobileBuildResultReceiver.STATUS_JOB_ID, null, ContinuumMobileBuildResultReceiver.STATUS_JOB_START, ContinuumMobileBuildResultReceiver.STATUS_PROJECT)
    }

    private fun dispatchApprovedCommand(
        operationName: String,
        requestId: String,
        plan: ContinuumMobileBuildPlan.Command,
        slot: ResultSlot = ResultSlot.MAIN,
        project: ContinuumOwnedProject? = null,
        abortTask: String? = null,
        typedId: String? = null,
        typedHash: String? = null,
        typedCommit: String? = null
    ) {
        // A confirmation belongs to its live Activity, never to an instance replaced by recreation.
        if (isFinishing || isDestroyed) return
        if (checkSelfPermission(ContinuumMobileBuildPlan.TERMUX_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Termux-Berechtigung zunächst erlauben; dann den Auftrag erneut bestätigen."
            requestPermissions(arrayOf(ContinuumMobileBuildPlan.TERMUX_PERMISSION), 410)
            return
        }
        try {
            packageManager.getPackageInfo(ContinuumMobileBuildPlan.TERMUX_PACKAGE, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            status.text = "Termux ist nicht installiert oder nicht sichtbar. Kein lokaler Worker verfügbar."
            return
        }
        val pendingId = preferences.getString(slot.idKey, null)
        if (pendingId != null) {
            val started = preferences.getLong(slot.startKey, 0)
            if (started > 0 && System.currentTimeMillis() - started > 60 * 60 * 1000L) {
                AlertDialog.Builder(this)
                    .setTitle("Vorherigen Auftrag ohne Rückmeldung abschließen?")
                    .setMessage("Der frühere Vorgang hat seit über einer Stunde nicht geantwortet. Prüfe zuerst Termux: Dort könnte ein Prozess noch laufen. Ein neuer Auftrag kann Ressourcen beanspruchen.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Als ungeklärt markieren") { _, _ ->
                        if (isFinishing || isDestroyed) return@setPositiveButton
                        // The callback may have arrived and a different foreground instance started a new job.
                        if (preferences.getString(slot.idKey, null) != pendingId) {
                            refreshReceipt()
                            return@setPositiveButton
                        }
                        val edit = preferences.edit().remove(slot.idKey).remove(slot.startKey)
                        if (slot == ResultSlot.CCC1_ABORT) edit.remove(ContinuumMobileBuildResultReceiver.ABORT_TASK)
                        slot.operationKey?.let { edit.remove(it) }
                        slot.projectKey?.let { edit.remove(it) }
                        edit.putString(ContinuumMobileBuildResultReceiver.LAST_MESSAGE,
                                "Voriger Termux-Auftrag hat keinen Abschlussnachweis geliefert.")
                            .commit()
                        refreshReceipt()
                    }.show()
            } else {
                status.text = "Zuerst die bestehende Termux-Ausführung abwarten. Es wird kein zweiter paralleler Auftrag gestartet."
            }
            return
        }
        val receiverIntent = Intent(this, ContinuumMobileBuildResultReceiver::class.java).apply {
            // Keep request identity in the already populated Intent data. A
            // mutable Termux fill-in may carry the result, not replace our
            // original operation and request identity.
            data = android.net.Uri.parse("continuum://termux-callback/${operationName}/$requestId")
            putExtra(ContinuumMobileBuildResultReceiver.JOB_ID, requestId)
            putExtra(ContinuumMobileBuildResultReceiver.JOB_OPERATION, operationName)
        }
        // Termux must fill the "result" Bundle; hence an explicit, mutable ONE_SHOT
        // PendingIntent to a non-exported receiver, not a mutable implicit intent.
        val callback = PendingIntent.getBroadcast(
            this,
            java.security.SecureRandom().nextInt(Int.MAX_VALUE),
            receiverIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
        )
        val intent = Intent().apply {
            setClassName(ContinuumMobileBuildPlan.TERMUX_PACKAGE, ContinuumMobileBuildPlan.TERMUX_SERVICE)
            action = ContinuumMobileBuildPlan.TERMUX_ACTION
            putExtra("com.termux.RUN_COMMAND_PATH", plan.executable)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", plan.args.toTypedArray())
            putExtra("com.termux.RUN_COMMAND_WORKDIR", plan.workDir)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra(ContinuumMobileBuildResultReceiver.COMMAND_PENDING_INTENT, callback)
        }
        val pendingEdit = preferences.edit()
            .putString(slot.idKey, requestId)
            .putLong(slot.startKey, System.currentTimeMillis())
        if(ContinuumTypedCodingBridge.Operation.ofCallback(operationName)!=null) {
            if(typedId==null||typedHash==null||typedCommit==null||
               !typedCommit.matches(Regex("[a-f0-9]{40}"))) {
                callback.cancel()
                status.text="Coding-Auftragsbindung fehlt. Nichts gestartet."
                return
            }
            pendingEdit.putString(ContinuumMobileBuildResultReceiver.TYPED_EXPECTED_ID,typedId)
                .putString(ContinuumMobileBuildResultReceiver.TYPED_EXPECTED_SHA,typedHash)
                .putString(ContinuumMobileBuildResultReceiver.TYPED_EXPECTED_COMMIT,typedCommit)
            if(operationName==ContinuumTypedCodingBridge.Operation.PREPARE.callback)
                pendingEdit.remove(ContinuumMobileBuildResultReceiver.TYPED_PREPARED_ID)
                    .remove(ContinuumMobileBuildResultReceiver.TYPED_PREPARED_SHA)
                    .remove(ContinuumMobileBuildResultReceiver.TYPED_PREPARED_COMMIT)
        }
        slot.operationKey?.let { pendingEdit.putString(it, operationName) }
        slot.projectKey?.let {
            if (project != null) pendingEdit.putString(it, project.id) else pendingEdit.remove(it)
        }
        if (slot == ResultSlot.PROJECT_STATUS) pendingEdit.remove(ContinuumMobileBuildResultReceiver.STATUS_MESSAGE)
        if (slot == ResultSlot.CCC1_ABORT) pendingEdit.remove(ContinuumMobileBuildResultReceiver.ABORT_MESSAGE)
            .putString(ContinuumMobileBuildResultReceiver.ABORT_TASK, abortTask)
        if (!pendingEdit.commit()) {
            callback.cancel()
            status.text = "Der lokale Auftragsstatus konnte nicht gesichert werden. Nichts gestartet."
            return
        }
        try {
            if (startService(intent) == null) {
                callback.cancel()
                cancelUnstarted(requestId, "Termux hat die Auftragsauslösung nicht angenommen.", slot)
            } else {
                status.text = "Auftrag an Termux übermittelt. Der tatsächliche Exitcode wird abgewartet; kein Build-Erfolg bestätigt."
            }
        } catch (_: SecurityException) {
            callback.cancel()
            cancelUnstarted(requestId, "Termux-Zugriff verweigert. RUN_COMMAND und allow-external-apps prüfen.", slot)
        } catch (_: IllegalStateException) {
            callback.cancel()
            cancelUnstarted(requestId, "Android hat den Termux-Dienststart verhindert.", slot)
        } catch (e: RuntimeException) {
            callback.cancel()
            cancelUnstarted(requestId, "Termux-Start gescheitert: ${e.javaClass.simpleName}.", slot)
        }
    }

    private fun cancelUnstarted(id: String, message: String, slot: ResultSlot = ResultSlot.MAIN) {
        if (id == preferences.getString(slot.idKey, null)) {
            val edit = preferences.edit().remove(slot.idKey).remove(slot.startKey)
            if (slot == ResultSlot.CCC1_ABORT) edit.remove(ContinuumMobileBuildResultReceiver.ABORT_TASK)
            if (ContinuumTypedCodingBridge.Operation.ofCallback(preferences.getString(ContinuumMobileBuildResultReceiver.JOB_OPERATION,null) ?: "")!=null) {
                edit.remove(ContinuumMobileBuildResultReceiver.TYPED_EXPECTED_ID)
                    .remove(ContinuumMobileBuildResultReceiver.TYPED_EXPECTED_SHA)
                    .remove(ContinuumMobileBuildResultReceiver.TYPED_EXPECTED_COMMIT)
            }
            slot.operationKey?.let { edit.remove(it) }
            slot.projectKey?.let { edit.remove(it) }
            edit.putString(ContinuumMobileBuildResultReceiver.LAST_MESSAGE, message).commit()
        }
        refreshReceipt()
    }
}
