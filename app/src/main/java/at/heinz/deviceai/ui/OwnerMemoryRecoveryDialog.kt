package at.heinz.deviceai.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import at.heinz.deviceai.learning.ContinuumLearningCore
import at.heinz.deviceai.learning.OwnerRecoveryDialogDecision
import at.heinz.deviceai.learning.OwnerRecoveryReview
import at.heinz.deviceai.learning.RecoveredOwnerFact
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit owner review for the existing memory store. The caller supplies its existing IO executor.
 * Close the returned handle when its Activity is destroyed. Closing suppresses late UI callbacks;
 * it cannot undo a durable write already authorized by the positive button. No model tool calls this.
 */
object OwnerMemoryRecoveryDialog {
    fun show(
        activity: Activity,
        executor: Executor,
        core: ContinuumLearningCore
    ): AutoCloseable {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Open the owner review on the UI thread" }
        return Session(activity, executor, core).also { it.start() }
    }

    private class Session(
        activity: Activity,
        private val executor: Executor,
        private val core: ContinuumLearningCore
    ) : AutoCloseable {
        private val owner = WeakReference(activity)
        private val ui = Handler(Looper.getMainLooper())
        private val closed = AtomicBoolean(false)
        private var dialog: AlertDialog? = null
        @Volatile private var decision: OwnerRecoveryDialogDecision? = null

        fun start() {
            val activity = liveActivity() ?: return
            replaceDialog(AlertDialog.Builder(activity)
                .setTitle("Wiederhergestellte Angaben prüfen")
                .setMessage("Die Angaben werden aus deinem lokalen Gedächtnis geladen …")
                .setNegativeButton("Abbrechen") { _, _ -> close() }
                .create())
            execute({ core.recoveredOwnerFactsForReview() }) { current, result ->
                result.fold(
                    onSuccess = { showReview(current, it) },
                    onFailure = { showMessage(current, "Gedächtnisprüfung nicht verfügbar",
                        "Die wiederhergestellten Angaben konnten nicht sicher geladen werden. " +
                            "Bitte versuche es später erneut.") }
                )
            }
        }

        private fun showReview(activity: Activity, review: OwnerRecoveryReview) {
            if (review.facts.isEmpty()) {
                showMessage(activity, "Keine offenen Bestätigungen",
                    "Es liegen keine wiederhergestellten eigenen Angaben zur erneuten Bestätigung vor.")
                return
            }
            val choice = OwnerRecoveryDialogDecision(review)
            decision = choice
            val padding = (20 * activity.resources.displayMetrics.density).toInt()
            val explanation = TextView(activity).apply {
                text = "Diese wiederhergestellten Angaben sind noch inaktiv. " +
                    "Lies sie und wähle nur aus, was für dich weiterhin zutrifft. " +
                    "Erst „Auswahl bestätigen“ aktiviert genau diese Auswahl. " +
                    "Abbrechen lässt die Angaben unbestätigt."
                textSize = 16f
                setPadding(padding, padding / 2, padding, padding / 2)
            }
            val list = ListView(activity).apply {
                choiceMode = ListView.CHOICE_MODE_MULTIPLE
                adapter = object : ArrayAdapter<RecoveredOwnerFact>(activity,
                    android.R.layout.simple_list_item_multiple_choice, review.facts) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                        (super.getView(position, convertView, parent) as CheckedTextView).apply {
                            val fact = getItem(position)!!
                            text = fact.content
                            setSingleLine(false)
                            maxLines = Int.MAX_VALUE
                            ellipsize = null
                            contentDescription = "Wiederhergestellte Angabe ${position + 1}: ${fact.content}"
                            setPadding(padding, padding / 2, padding, padding / 2)
                        }
                }
            }
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(explanation)
                addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    (activity.resources.displayMetrics.heightPixels / 2).coerceAtLeast(padding * 4)))
            }
            val shown = AlertDialog.Builder(activity)
                .setTitle("Wiederhergestellte Angaben prüfen")
                .setView(body)
                .setPositiveButton("Auswahl bestätigen", null)
                .setNegativeButton("Abbrechen") { _, _ -> close() }
                .create()
            replaceDialog(shown)
            val confirm = shown.getButton(AlertDialog.BUTTON_POSITIVE)
            confirm.isEnabled = false
            confirm.filterTouchesWhenObscured = true
            list.filterTouchesWhenObscured = true
            list.setOnItemClickListener { _, _, position, _ ->
                if (choice.select(review.facts[position].id, list.isItemChecked(position))) {
                    confirm.isEnabled = choice.selectedCount > 0
                    confirm.text = if (choice.selectedCount == 0) "Auswahl bestätigen"
                        else "${choice.selectedCount} bestätigen"
                }
            }
            confirm.setOnClickListener {
                if (closed.get()) return@setOnClickListener
                val proof = runCatching { choice.takeConfirmation() }.getOrElse {
                    showConfirmationFailure(activity)
                    return@setOnClickListener
                } ?: return@setOnClickListener
                confirm.isEnabled = false
                shown.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                shown.setCancelable(false)
                shown.setCanceledOnTouchOutside(false)
                list.isEnabled = false
                explanation.text = "Deine ausdrückliche Bestätigung wird lokal gespeichert …"
                execute({
                    core.reconfirmRecoveredOwnerFacts(review, proof).also {
                        check(it == proof.confirmedFactHashes.keys) { "Confirmation result did not match selection" }
                    }
                }) { current, result ->
                    result.fold(
                        onSuccess = { confirmed -> showMessage(current, "Auswahl bestätigt",
                            if (confirmed.size == 1) "Eine ausgewählte Angabe ist jetzt wieder aktiv."
                            else "${confirmed.size} ausgewählte Angaben sind jetzt wieder aktiv.") },
                        onFailure = { showConfirmationFailure(current) }
                    )
                }
            }
        }

        private fun showConfirmationFailure(activity: Activity) = showMessage(activity,
            "Bestätigung nicht abgeschlossen",
            "Diese Auswahl konnte nicht zuverlässig bestätigt werden. " +
                "Öffne die Prüfung erneut, um den aktuellen Stand zu sehen und die Angaben nochmals zu prüfen.")

        private fun showMessage(activity: Activity, title: String, message: String) {
            replaceDialog(AlertDialog.Builder(activity).setTitle(title).setMessage(message)
                .setPositiveButton("Schließen") { _, _ -> close() }.create())
        }

        private fun replaceDialog(next: AlertDialog) {
            dialog?.apply { setOnDismissListener(null); dismiss() }
            dialog = next
            next.setOnDismissListener { if (dialog === next) close() }
            next.show()
        }

        private fun liveActivity(): Activity? {
            val activity = owner.get()
            if (closed.get() || activity == null || activity.isFinishing || activity.isDestroyed) {
                close()
                return null
            }
            return activity
        }

        private fun <T> execute(work: () -> T, complete: (Activity, Result<T>) -> Unit) {
            try {
                executor.execute task@{
                    if (closed.get()) return@task
                    val result = runCatching(work)
                    ui.post { liveActivity()?.let { complete(it, result) } }
                }
            } catch (failure: RejectedExecutionException) {
                ui.post { liveActivity()?.let { complete(it, Result.failure(failure)) } }
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            decision?.cancel()
            owner.clear()
            val dismiss = Runnable {
                dialog?.apply { setOnDismissListener(null); dismiss() }
                dialog = null
            }
            if (Looper.myLooper() == Looper.getMainLooper()) dismiss.run() else ui.post(dismiss)
        }
    }
}
