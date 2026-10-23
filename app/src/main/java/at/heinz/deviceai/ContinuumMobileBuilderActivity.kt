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
        paragraph("Continuum · Android Builder", true)
        paragraph("Entwicklung direkt auf diesem Smartphone. Termux und ein geprüfter Device-AI-Checkout unter ~/device-ai sind erforderlich.")
        status = paragraph("Noch kein mobiler Geräte-Build bestätigt.")
        status.setTextIsSelectable(true)
        for (operation in ContinuumMobileBuildPlan.Operation.entries) {
            val button = Button(this).apply {
                text = operation.title
                isAllCaps = false
                setOnClickListener {
                    if (operation == ContinuumMobileBuildPlan.Operation.BUILD) {
                        AlertDialog.Builder(this@ContinuumMobileBuilderActivity)
                            .setTitle("Lokalen Build starten?")
                            .setMessage("Termux führt Tests, Lint und einen APK-Build auf diesem Telefon aus. Das kann bis zu 35 Minuten dauern und Akku/Speicher beanspruchen. Keine Installation erfolgt.")
                            .setNegativeButton("Abbrechen", null)
                            .setPositiveButton("Bauen") { _, _ -> dispatch(operation) }
                            .show()
                    } else dispatch(operation)
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
                    val question = when (operation) {
                        ContinuumCcc1Bridge.Operation.SUBMIT_REPAIR -> "Reparaturauftrag für die Testkette ${chain.cli} anlegen?" to
                            "Termux prüft zuerst Branch, sauberen Arbeitsstand und Zustandsordner. Der Auftrag wird nur angelegt, noch nicht ausgeführt."
                        ContinuumCcc1Bridge.Operation.RUN_ONCE -> "Nächsten CCC1-Schritt ausführen?" to
                            "Termux führt den nächsten offenen CCC1-Auftrag aus: Testkette, Reparaturkandidaten und unabhängige Prüfung. " +
                            "Das kann bis zu 30 Minuten dauern. Hauptbranch, installierte Apps und Gewichte bleiben unverändert; " +
                            "ein Patch bleibt ein Prüfvorschlag."
                        ContinuumCcc1Bridge.Operation.ABORT -> "CCC1-Auftrag abbrechen?" to
                            "Der zuletzt bekannte offene Auftrag wird beendet und ein laufender Job gestoppt. Ein Abbruch zählt nie als Erfolg."
                        else -> null
                    }
                    if (question == null) dispatchCcc1(operation, chain)
                    else AlertDialog.Builder(this@ContinuumMobileBuilderActivity)
                        .setTitle(question.first)
                        .setMessage(question.second)
                        .setNegativeButton("Nicht starten", null)
                        .setPositiveButton("Bestätigen") { _, _ -> dispatchCcc1(operation, chain) }
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
        val ccc1 = listOfNotNull(
            ccc1Task?.let { "Offener CCC1-Auftrag: $it" },
            if (abortPending) "Ein CCC1-Abbruch ist unterwegs; kein Ergebnis bestätigt." else abortMessage
        ).joinToString("\n").let { if (it.isEmpty()) "" else "\n$it" }
        status.text = when {
            pending != null ->
                "Ein Termux-Auftrag läuft oder wartet auf seine Rückmeldung. Gestartet: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(started))}. Kein Erfolg bestätigt." +
                    (if (pendingOperation == "CODE_TEST") "\nCoding-Auftrags-ID: $pending (für gezielten Termux-Abbruch)." else "")
            last != null -> last + if (sha != null) "\nSHA-256: $sha" else ""
            else -> "Noch kein Rückbericht vom mobilen Builder. Kein Geräte-Build bestätigt."
        } + ccc1
    }

    private fun dispatch(operation: ContinuumMobileBuildPlan.Operation) {
        val requestId = UUID.randomUUID().toString()
        val plan = ContinuumMobileBuildPlan.command(operation)
        dispatchApprovedCommand(operation.name, requestId, plan)
    }

    private fun dispatchCoding(order: ContinuumMobileCodingJob.Order) {
        val problems = ContinuumMobileCodingJob.problems(order)
        if (problems.isNotEmpty()) {
            status.text = problems.joinToString("\n")
            return
        }
        dispatchApprovedCommand("CODE_TEST", order.jobId, ContinuumMobileCodingJob.command(order))
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
        dispatchApprovedCommand(operation.jobOperation, requestId, plan, slot)
    }

    /**
     * One-shot result slots in the preferences. Every job uses MAIN (one outstanding job); a CCC1 abort has its own
     * slot so it can reach a running CCC1 step without dropping that step's result.
     */
    private enum class ResultSlot(val idKey: String, val operationKey: String?, val startKey: String) {
        MAIN(ContinuumMobileBuildResultReceiver.JOB_ID, ContinuumMobileBuildResultReceiver.JOB_OPERATION, ContinuumMobileBuildResultReceiver.JOB_START),
        CCC1_ABORT(ContinuumMobileBuildResultReceiver.ABORT_JOB_ID, null, ContinuumMobileBuildResultReceiver.ABORT_JOB_START)
    }

    private fun dispatchApprovedCommand(
        operationName: String,
        requestId: String,
        plan: ContinuumMobileBuildPlan.Command,
        slot: ResultSlot = ResultSlot.MAIN
    ) {
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
                        val edit = preferences.edit().remove(slot.idKey).remove(slot.startKey)
                        slot.operationKey?.let { edit.remove(it) }
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
        slot.operationKey?.let { pendingEdit.putString(it, operationName) }
        if (slot == ResultSlot.CCC1_ABORT) pendingEdit.remove(ContinuumMobileBuildResultReceiver.ABORT_MESSAGE)
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
            slot.operationKey?.let { edit.remove(it) }
            edit.putString(ContinuumMobileBuildResultReceiver.LAST_MESSAGE, message).commit()
        }
        refreshReceipt()
    }
}
