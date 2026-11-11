package at.heinz.deviceai.research.voice

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executor

/**
 * Isolated research UI over the existing personal-voice staging contract.
 * No Activity registration, microphone, network, model update or owner-fact write.
 * The caller owns [io] and calls [dismiss] when its Activity scope ends.
 */
class AndroidPersonalVoiceReviewDialog(
    private val activity: Activity,
    private val store: PersonalVoiceLearningStore,
    private val io: Executor
) {
    private val main = Handler(Looper.getMainLooper())
    private var visible: AlertDialog? = null
    private var generation = 0L

    private data class ListedExample(val id: String, val label: String)
    private data class Overview(val consent: VoiceLearningConsent, val records: List<ListedExample>)
    private data class ReviewSnapshot(val example: PersonalVoiceExample, val revision: Long)

    /** Explicit entry point; opening this screen does not enable collection. */
    fun show() {
        requireMainThread()
        val body = column()
        body.addView(text("Bestätigte Textbeispiele bleiben lokal geschützt. Hier werden keine Modellgewichte automatisch geändert."))
        val consentBox = CheckBox(activity).apply {
            text = "Korrigierte Texte lokal zum Lernen vormerken"
            isEnabled = false
        }
        body.addView(consentBox)
        body.addView(text("Audio: Keine Aufnahme implementiert. Es werden hier weder Tonaufnahmen gespeichert noch Daten an eine Cloud gesendet."))
        val status = text("Gespeicherte Beispiele werden geladen …")
        body.addView(status)
        var records = emptyList<ListedExample>()
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Persönliches Sprachlernen · Forschung")
            .setView(scroll(body))
            .setPositiveButton("Beispiele prüfen", null)
            .setNegativeButton("Schließen", null)
            .create()
        display(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { showList(records) }
        background(dialog, {
            val chosen = store.consent()
            val entries = store.ids().mapIndexed { index, id ->
                val e = store.inspect(id)
                ListedExample(id, "${index + 1}. ${preview(e.intendedInstruction)}")
            }
            Overview(chosen, entries)
        }) { result ->
            result.fold(onSuccess = { overview ->
                records = overview.records
                consentBox.isChecked = overview.consent.textTraining
                consentBox.isEnabled = true
                status.text = "${records.size} gespeicherte Beispiele. Prüfen und Löschen ist auch bei ausgeschaltetem Lernen möglich."
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = records.isNotEmpty()
                consentBox.setOnCheckedChangeListener { _, wanted ->
                    consentBox.isEnabled = false
                    background(dialog, {
                        val current = store.consent()
                        store.setConsent(current.copy(textTraining = wanted))
                        store.consent()
                    }) { changed ->
                        changed.fold(onSuccess = { state ->
                            // Assigning the same requested value does not retrigger this listener.
                            if (consentBox.isChecked != state.textTraining) {
                                // Concurrent changes are shown by a fresh screen, never hidden by a new grant.
                                show()
                            } else {
                                consentBox.isEnabled = true
                                status.text = if (state.textTraining)
                                    "Textlernen erlaubt. Jedes Beispiel braucht weiterhin deine eigene Bestätigung."
                                else "Textlernen ausgeschaltet. Vorhandene Beispiele kannst du prüfen oder löschen."
                            }
                        }, onFailure = {
                            notice("Die Einstellung konnte nicht gespeichert werden.")
                            show()
                        })
                    }
                }
            }, onFailure = {
                status.text = "Der geschützte Lernbereich konnte nicht gelesen werden. Es wurde nichts geändert."
            })
        }
    }

    /** Review one newly proposed example; the caller must not store it beforehand. */
    fun reviewCandidate(candidate: PersonalVoiceExample) {
        requireMainThread()
        loadReview(null, candidate)
    }

    fun dismiss() {
        requireMainThread()
        generation++
        visible?.setOnDismissListener(null)
        visible?.dismiss()
        visible = null
    }

    private fun showList(records: List<ListedExample>) {
        if (records.isEmpty()) return show()
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Gespeicherte Beispiele")
            .setItems(records.map { it.label }.toTypedArray()) { _, which -> loadReview(records[which].id, null) }
            .setNegativeButton("Zurück") { _, _ -> show() }
            .create()
        display(dialog)
    }

    private fun loadReview(id: String?, proposed: PersonalVoiceExample?) {
        val loading = AlertDialog.Builder(activity)
            .setTitle("Beispiel prüfen")
            .setMessage("Geschütztes Beispiel wird geladen …")
            .setNegativeButton("Abbrechen", null)
            .create()
        display(loading)
        background(loading, {
            if (id != null) {
                val reviewed = store.inspectForReview(id)
                ReviewSnapshot(reviewed.example, reviewed.reviewRevision)
            } else {
                ReviewSnapshot(requireNotNull(proposed), store.reviewRevision())
            }
        }) { result ->
            result.fold(onSuccess = { showReview(id, it) }, onFailure = {
                notice("Das Beispiel oder die Freigabe hat sich geändert. Bitte neu öffnen.")
                show()
            })
        }
    }

    private fun showReview(existingId: String?, snapshot: ReviewSnapshot) {
        val original = snapshot.example
        val body = column()
        body.addView(text("Erkannter Wortlaut · unverändert"))
        body.addView(text(original.recognizedTranscript, selectable = true))
        body.addView(text("Gesprächskontext · unverändert"))
        body.addView(text(if (original.ownerContext.isEmpty()) "Kein früherer Kontext vorhanden."
            else original.ownerContext.joinToString("\n\n"), selectable = true))
        body.addView(text("Herkunft: ${original.sourceReference}", selectable = true))
        if (original.previousTaskReferences.isNotEmpty())
            body.addView(text("Frühere Aufträge: ${original.previousTaskReferences.joinToString(", ")}", selectable = true))

        val useWording = CheckBox(activity).apply {
            text = "Einen korrigierten Wortlaut verwenden"
            isChecked = original.correctedTranscript != null
        }
        body.addView(useWording)
        val wording = editor(body, "Korrigierter Wortlaut", original.correctedTranscript.orEmpty())
        wording.isEnabled = useWording.isChecked
        val wordingConfirmed = CheckBox(activity).apply {
            text = "Diesen korrigierten Wortlaut habe ich geprüft."
            isEnabled = useWording.isChecked
        }
        body.addView(wordingConfirmed)
        val intention = editor(body, "Das war tatsächlich gemeint", original.intendedInstruction)
        val outcome = editor(body, "Gewünschtes Ergebnis", original.desiredOutcome)
        val forbidden = editor(body, "Was ausdrücklich nicht passieren darf · ein Punkt pro Zeile",
            original.forbiddenInterpretations.joinToString("\n"))
        val meaningConfirmed = CheckBox(activity).apply {
            text = "Anweisung, Ergebnis und Einschränkungen sind richtig."
        }
        val ownerOnly = CheckBox(activity).apply {
            text = "Diese Angaben stammen nur von mir."
        }
        val sensitive = CheckBox(activity).apply {
            text = "Ich habe auch den Kontext und sensible Inhalte geprüft und gebe genau dieses Beispiel zum lokalen Lernen frei."
        }
        body.addView(meaningConfirmed)
        body.addView(ownerOnly)
        body.addView(sensitive)
        val status = text("Noch nicht bestätigt. Eine Speicherung erfolgt erst nach deiner Prüfung.")
        body.addView(status)
        val builder = AlertDialog.Builder(activity)
            .setTitle("Sprachbeispiel prüfen")
            .setView(scroll(body))
            .setPositiveButton(if (existingId == null) "Bestätigen und vormerken" else "Änderung bestätigen", null)
            .setNegativeButton("Abbrechen", null)
        if (existingId != null) builder.setNeutralButton("Löschen", null)
        val dialog = builder.create()
        display(dialog)
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val delete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        val controls = listOf<View>(useWording, wording, wordingConfirmed, intention, outcome, forbidden,
            meaningConfirmed, ownerOnly, sensitive)

        fun fieldsValid(): Boolean = intention.text.isNotBlank() && outcome.text.isNotBlank() &&
            listOf(intention, outcome, forbidden).all { it.text.length <= 8192 } &&
            (!useWording.isChecked || wording.text.isNotBlank() && wording.text.length <= 8192) &&
            forbidden.text.lines().count { it.isNotBlank() } <= 16
        fun updateSave() {
            save.isEnabled = fieldsValid() && meaningConfirmed.isChecked && ownerOnly.isChecked && sensitive.isChecked &&
                (!useWording.isChecked || wordingConfirmed.isChecked)
            if (!fieldsValid()) status.text = "Bitte prüfe leere oder zu lange Felder. Einschränkungen: höchstens 16 Zeilen."
            else status.text = "Jede Änderung braucht eine neue Bestätigung. Rohtext und Kontext bleiben erhalten."
        }
        fun resetMeaning() {
            meaningConfirmed.isChecked = false
            sensitive.isChecked = false
            updateSave()
        }
        listOf(intention, outcome, forbidden).forEach { field -> field.addTextChangedListener(watcher { resetMeaning() }) }
        wording.addTextChangedListener(watcher {
            wordingConfirmed.isChecked = false
            resetMeaning()
        })
        useWording.setOnCheckedChangeListener { _, checked ->
            wording.isEnabled = checked
            wordingConfirmed.isEnabled = checked
            wordingConfirmed.isChecked = false
            resetMeaning()
        }
        listOf(wordingConfirmed, meaningConfirmed, ownerOnly, sensitive).forEach { box ->
            box.setOnCheckedChangeListener { _, _ -> updateSave() }
        }
        updateSave()

        save.setOnClickListener {
            if (!save.isEnabled) return@setOnClickListener
            // The immutable proposal is captured on the UI thread after explicit checks.
            val revised = PersonalVoiceExample(original.sessionId, original.recognizedTranscript,
                if (useWording.isChecked) wording.text.toString() else null, useWording.isChecked && wordingConfirmed.isChecked,
                intention.text.toString(), meaningConfirmed.isChecked, original.ownerContext, original.previousTaskReferences,
                outcome.text.toString(), forbidden.text.lines().map(String::trim).filter(String::isNotEmpty),
                original.sourceReference, ownerOnly.isChecked, sensitive.isChecked, sensitive.isChecked)
            controls.forEach { it.isEnabled = false }
            save.isEnabled = false; cancel.isEnabled = false; delete?.isEnabled = false
            dialog.setCancelable(false)
            status.text = "Das geprüfte Beispiel wird lokal gespeichert …"
            background(dialog, {
                val approval = store.approveForTraining(revised, ownerConfirmed = true,
                    expectedReviewRevision = snapshot.revision)
                if (existingId == null) store.stage(revised, approval) else store.replace(existingId, revised, approval)
            }) { result ->
                dialog.setCancelable(true)
                cancel.isEnabled = true
                result.fold(onSuccess = { saved ->
                    when (saved) {
                        VoiceStageResult.STORED, VoiceStageResult.DUPLICATE -> {
                            notice("Das geprüfte Beispiel ist lokal vorgemerkt.")
                            show()
                        }
                        VoiceStageResult.CONSENT_REQUIRED -> {
                            status.text = "Textlernen ist ausgeschaltet. Bitte schließe diese Ansicht und prüfe die Einstellung."
                        }
                        VoiceStageResult.REVIEW_REQUIRED -> {
                            status.text = "Die Freigabe hat sich geändert. Bitte schließe das Beispiel und prüfe es neu."
                        }
                        VoiceStageResult.CAPACITY_REACHED -> {
                            status.text = "Der Lernbereich ist voll. Bitte prüfe und lösche zuerst ein gespeichertes Beispiel."
                        }
                        VoiceStageResult.INVALID -> {
                            status.text = "Dieses Beispiel ist nicht gültig. Bitte schließe es und prüfe die Felder neu."
                        }
                    }
                }, onFailure = {
                    status.text = "Beispiel oder Freigabe hat sich geändert oder konnte nicht gespeichert werden. Bitte neu öffnen und prüfen."
                })
            }
        }
        if (existingId != null) delete?.setOnClickListener { confirmDelete(existingId) }
    }

    private fun confirmDelete(id: String) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Dieses Lernbeispiel löschen?")
            .setMessage("Dieses geschützte Beispiel wird entfernt. Bereits trainierte Modellgewichte werden dadurch nicht rückgängig gemacht.")
            .setPositiveButton("Löschen", null)
            .setNegativeButton("Abbrechen") { _, _ -> loadReview(id, null) }
            .create()
        display(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            background(dialog, { store.delete(id) }) { result ->
                result.fold(onSuccess = { removed ->
                    notice(if (removed) "Das Lernbeispiel wurde gelöscht." else "Das Beispiel ist bereits entfernt.")
                    show()
                }, onFailure = {
                    notice("Das Lernbeispiel konnte nicht gelöscht werden.")
                    show()
                })
            }
        }
    }

    private fun display(dialog: AlertDialog) {
        requireMainThread()
        generation++
        visible?.setOnDismissListener(null)
        visible?.dismiss()
        visible = dialog
        val shownGeneration = generation
        dialog.setOnDismissListener {
            if (visible === dialog && generation == shownGeneration) {
                generation++
                visible = null
            }
        }
        dialog.show()
    }

    private fun <T> background(dialog: AlertDialog, task: () -> T, completed: (Result<T>) -> Unit) {
        val acceptedGeneration = generation
        val deliver: (Result<T>) -> Unit = { result ->
            main.post {
                if (visible === dialog && generation == acceptedGeneration && dialog.isShowing &&
                    !activity.isFinishing && !activity.isDestroyed) completed(result)
            }
        }
        try { io.execute { deliver(runCatching(task)) } }
        catch (_: RuntimeException) { deliver(Result.failure(IllegalStateException("VOICE_REVIEW_UNAVAILABLE"))) }
    }

    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
    }
    private fun scroll(body: LinearLayout) = ScrollView(activity).apply { addView(body) }
    private fun text(value: String, selectable: Boolean = false) = TextView(activity).apply {
        text = value
        setTextIsSelectable(selectable)
        val margin = (8 * resources.displayMetrics.density).toInt()
        setPadding(0, margin, 0, margin)
    }
    private fun editor(parent: LinearLayout, label: String, value: String): EditText {
        parent.addView(text(label))
        return EditText(activity).apply {
            setText(value)
            setSingleLine(false)
            minLines = 2
            maxLines = 6
            contentDescription = label
            parent.addView(this)
        }
    }
    private fun watcher(changed: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
        override fun afterTextChanged(s: Editable?) {}
    }
    private fun preview(value: String): String = value.replace(Regex("\\s+"), " ").take(90)
    private fun notice(message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    private fun requireMainThread() = check(Looper.myLooper() == Looper.getMainLooper())
}
