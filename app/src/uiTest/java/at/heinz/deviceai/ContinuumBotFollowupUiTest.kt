package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.chief.*
import at.heinz.deviceai.cognitive.AgentFollowupEnvelope
import at.heinz.deviceai.cognitive.DeliberationCancellation
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/** Real dialogs, workers, persistent results. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumBotFollowupUiTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var root: File
    private lateinit var store: ChiefBotProfileStore
    private lateinit var team: ChiefBotTeam
    private lateinit var roster: ContinuumBotRosterDialog
    private lateinit var io: QueuedIo
    private val prompts = CopyOnWriteArrayList<String>()
    private val submissions = CopyOnWriteArrayList<Result<ChiefTeamTask>>()
    private val session = AtomicReference("session-a")
    private var proposalTools: List<String> = emptyList()
    private val nextGoal = "Prüfe als neuen Auftrag: Jede Raute ist ein Quadrat."

    private class QueuedIo : Executor {
        val pending = ConcurrentLinkedQueue<Runnable>()
        override fun execute(command: Runnable) { pending += command }
        fun drain() {
            var count = 0
            while (pending.isNotEmpty()) {
                check(++count <= 20)
                val action = pending.remove()
                val error = AtomicReference<Throwable?>()
                val thread = Thread { try { action.run() } catch (failure: Throwable) { error.set(failure) } }
                thread.start(); thread.join(10_000)
                check(!thread.isAlive)
                error.get()?.let { throw AssertionError("Followup background failure", it) }
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }

    @Before fun setup() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
        root = Files.createTempDirectory("followup-ui-").toFile()
        store = ChiefBotProfileStore(File(root, "bots")); io = QueuedIo()
        team = ChiefBotTeam(store, object : ChiefTeamModel {
            override fun readySha256() = "a".repeat(64)
            override fun generate(prompt: String, cancellation: DeliberationCancellation, deadlineMillis: Long): String {
                cancellation.throwIfCancelled(); prompts += prompt
                return if (prompts.size == 2) JSONObject().put("schema", AgentFollowupEnvelope.SCHEMA)
                    .put("answer", "Ungeprüfte Gegenprüfung des ersten Satzes.")
                    .put("next", JSONObject().put("goal", nextGoal).put("capability", "text.analyze")
                        .put("tools", JSONArray(proposalTools)).put("reason", "Zweiter Satz noch offen.")).toString()
                else "Kontrollierte Textantwort ${prompts.size}."
            }
        }, changed = { activity.runOnUiThread { if (::roster.isInitialized) roster.refreshRuntimeStatus() } },
            ownerSession = session::get)
        team.initialize(); team.configure("bot-atlas", listOf("bot-kora", "bot-lint"), 0)
        roster = ContinuumBotRosterDialog(activity, store, io, team = team,
            onTeamSubmit = { id, text, revision, shared, followup ->
                val admission = team.admissionToken()
                io.execute { submissions += runCatching {
                    team.submit(id, text, revision, true, admission, shared, followup)
                } }
                true
            })
        roster.show(); io.drain()
        click("Teamaufträge und Ergebnisse")
    }

    @After fun cleanup() {
        roster.close(); team.close()
        controller.pause().stop().destroy(); root.deleteRecursively()
    }

    private fun latest() = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun views(dialog: AlertDialog = latest()) = views(dialog.window!!.decorView)
    private fun button(label: String, dialog: AlertDialog = latest()) = views(dialog).filterIsInstance<Button>()
        .single { it.text.toString() == label }
    private fun click(label: String, drain: Boolean = true) {
        assertTrue(button(label).performClick())
        shadowOf(Looper.getMainLooper()).idle()
        if (drain) io.drain()
    }
    private fun text() = views().filterIsInstance<TextView>().joinToString("\n") { it.text.toString() }
    private fun consent() = views().filterIsInstance<CheckBox>().single()
    private fun source() {
        click("Chefbot einen Textprüfauftrag geben")
        views().filterIsInstance<EditText>().single().setText("Prüfe: Jedes Quadrat ist eine Raute.")
        consent().isChecked = true
        click("An Chefbot übergeben")
        assertTrue(team.awaitIdle(10_000))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ChiefTeamStatus.RESPONSES_RECORDED, store.teamState().tasks.single().status)
        assertEquals(2, prompts.size)
    }

    @Test fun actualResultCardOpensBoundNewDraftAndNeedsFreshUncheckedConsent() {
        source()
        assertTrue(text().contains("Folgevorschlag · noch nicht beauftragt"))
        click("Folgevorschlag prüfen")
        assertEquals(nextGoal, views().filterIsInstance<EditText>().single().text.toString())
        assertFalse(views().filterIsInstance<EditText>().single().isFocusable)
        assertFalse(consent().isChecked)
        assertTrue(text().contains("zwei neue Modellaufrufe"))
        assertTrue(text().contains("nicht übernommen"))
        click("An Chefbot übergeben")
        assertEquals(1, submissions.size); assertEquals(2, prompts.size)
        consent().isChecked = true
        click("An Chefbot übergeben")
        assertTrue(team.awaitIdle(10_000)); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(submissions.all { it.isSuccess })
        assertEquals(4, prompts.size)
        assertEquals(2, store.teamState().tasks.size)
        assertEquals(nextGoal, store.teamState().tasks.last().request)
    }

    @Test fun unsupportedToolProposalShowsChiefRejectionWithoutNewDraftOrWork() {
        proposalTools = listOf("delete_file")
        source(); click("Folgevorschlag prüfen")
        assertTrue(text(), text().contains("Werkzeuge außerhalb"))
        assertTrue(views().filterIsInstance<CheckBox>().isEmpty())
        assertEquals(1, submissions.size); assertEquals(2, prompts.size)
    }

    @Test fun ownerSessionChangeInvalidatesVisibleApprovalBeforeCallback() {
        source(); click("Folgevorschlag prüfen")
        consent().isChecked = true
        session.set("session-b")
        click("An Chefbot übergeben")
        assertTrue(text(), text().contains("nicht mehr gültig"))
        assertEquals(1, submissions.size); assertEquals(2, prompts.size)
    }

    @Test fun stopBetweenAcceptedUiAndQueuedSubmitStartsNoNewModel() {
        source(); click("Folgevorschlag prüfen")
        consent().isChecked = true
        click("An Chefbot übergeben", drain = false)
        assertEquals(1, io.pending.size)
        team.invalidate()
        io.drain()
        assertTrue(submissions.last().isFailure)
        assertEquals(1, store.teamState().tasks.size); assertEquals(2, prompts.size)
    }

    @Test fun closingDuringChiefReviewCannotReopenOrExecuteTheOldDraft() {
        source(); click("Folgevorschlag prüfen", drain = false)
        assertEquals(1, io.pending.size)
        val previous = latest()
        roster.close(); io.drain()
        assertFalse(previous.isShowing)
        assertEquals(1, submissions.size); assertEquals(2, prompts.size)
    }

    @Test fun stopDiscardMakesRetainedApprovalButtonInert() {
        source(); click("Folgevorschlag prüfen")
        val draft = latest()
        consent().isChecked = true
        val oldButton = button("An Chefbot übergeben")
        roster.discardTeamDraft()
        oldButton.performClick(); io.drain()
        assertFalse(draft.isShowing)
        assertEquals(1, submissions.size); assertEquals(2, prompts.size)
    }
}
