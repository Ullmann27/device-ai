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
import at.heinz.deviceai.chief.ChiefBotProfileStore
import at.heinz.deviceai.chief.ChiefBotTeam
import at.heinz.deviceai.chief.ChiefTeamModel
import at.heinz.deviceai.chief.ChiefTeamStatus
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.learning.ContinuumLearningCore
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

/** Real widgets and worker path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumBotSharedKnowledgeUiTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var root: File
    private lateinit var core: ContinuumLearningCore
    private lateinit var store: ChiefBotProfileStore
    private lateinit var team: ChiefBotTeam
    private lateinit var roster: ContinuumBotRosterDialog
    private lateinit var io: QueuedIo
    private lateinit var draft: AlertDialog
    private val prompts = CopyOnWriteArrayList<String>()
    private val selected = "Ich wohne in Steyr."
    private val other = "Mein Hund heißt Balu."
    private val request = "Prüfe meine bestätigte Wohnangabe in Steyr; ändere keine Dateien."

    private class QueuedIo : Executor {
        val pending = ConcurrentLinkedQueue<Runnable>()
        override fun execute(command: Runnable) { pending.add(command) }
        fun drain() {
            var count = 0
            while (pending.isNotEmpty()) {
                check(++count <= 20)
                val action = pending.remove()
                val failure = AtomicReference<Throwable?>()
                val worker = Thread {
                    try { check(Looper.myLooper() != Looper.getMainLooper()); action.run() }
                    catch (error: Throwable) { failure.set(error) }
                }
                worker.start(); worker.join(10_000)
                check(!worker.isAlive)
                failure.get()?.let { throw AssertionError("Shared-context background failure", it) }
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }

    @Before fun setup() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
        root = Files.createTempDirectory("bot-shared-ui-").toFile()
        io = QueuedIo()
        core = ContinuumLearningCore(File(root, "owner"))
        core.confirmOwnerFact(selected)
        core.confirmOwnerFact(other)
        core.confirmOwnerFact("Vielleicht ziehe ich nach Barcelona.")
        core.confirmOwnerFact("Ich arbeite in Wien.")
        core.revokeOwnerFact("Ich arbeite in Wien.")
        store = ChiefBotProfileStore(File(root, "bots"))
        store.seedDefaults()
        val privateProfile = store.get("bot-kora")!!
        store.saveWorkingContext(privateProfile.agentId, privateProfile.agentId,
            "PRIVATE_NOTE_NOT_IN_PICKER", privateProfile.profileVersion)
        team = ChiefBotTeam(store, object : ChiefTeamModel {
            override fun readySha256() = "a".repeat(64)
            override fun generate(prompt: String, cancellation: DeliberationCancellation, deadlineMillis: Long): String {
                cancellation.throwIfCancelled()
                prompts += prompt
                return "Kontrollierte Textantwort, kein unabhängiger Inhaltsbeleg."
            }
        }, sharedKnowledge = core)
        team.initialize()
        team.configure("bot-atlas", listOf("bot-kora", "bot-lint"), 0)
        roster = ContinuumBotRosterDialog(activity, store, io, team = team,
            onTeamSubmit = { id, text, revision, shared, followup ->
                io.execute { team.submit(id, text, revision, true, shared = shared, followup = followup) }
                true
            })
        roster.show(); io.drain()
        click(latest(), "Teamaufträge und Ergebnisse")
        click(latest(), "Chefbot einen Textprüfauftrag geben")
        draft = latest()
        editor().setText(request)
    }

    @After fun cleanup() {
        roster.close()
        team.close()
        if (!activity.isDestroyed) controller.pause().stop().destroy()
        root.deleteRecursively()
    }

    private fun latest(): AlertDialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun all(dialog: AlertDialog) = views(dialog.window!!.decorView)
    private fun text(dialog: AlertDialog) = all(dialog).filterIsInstance<TextView>().joinToString("\n") { it.text.toString() }
    private fun button(dialog: AlertDialog, label: String) = all(dialog).filterIsInstance<Button>()
        .single { it.text.toString() == label }
    private fun click(dialog: AlertDialog, label: String) {
        assertTrue(button(dialog, label).performClick())
        shadowOf(Looper.getMainLooper()).idle()
        io.drain()
    }
    private fun editor() = all(draft).filterIsInstance<EditText>().single()
    private fun picker(): AlertDialog {
        click(draft, "Bestätigte Angaben auswählen")
        return latest().also { assertNotSame(draft, it); assertTrue(it.isShowing) }
    }
    private fun select(picker: AlertDialog, value: String) {
        val list = picker.listView
        val index = (0 until list.adapter.count).single { list.adapter.getItem(it).toString() == value }
        assertTrue(list.performItemClick(list.adapter.getView(index, null, list), index, list.adapter.getItemId(index)))
        assertTrue(list.isItemChecked(index))
    }
    private fun approve(picker: AlertDialog, drain: Boolean = true) {
        assertTrue(picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick())
        shadowOf(Looper.getMainLooper()).idle()
        if (drain) io.drain()
    }
    private fun submit() {
        all(draft).filterIsInstance<CheckBox>().single().isChecked = true
        click(draft, "An Chefbot übergeben")
        assertTrue(team.awaitIdle(10_000))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ChiefTeamStatus.RESPONSES_RECORDED, store.teamState().tasks.single().status)
        assertEquals(2, prompts.size)
    }
    private fun assertNoSharedFacts() {
        prompts.forEach {
            assertFalse(JSONObject(it.substringAfter("\nTASK_DATA_JSON:\n")).has("ownerApprovedSharedFacts"))
            assertFalse(it.contains(selected))
            assertFalse(it.contains(other))
        }
    }

    @Test fun explicitFactSelectionAndSeparateTaskConsentReachActualTeamPrompts() {
        val picker = picker()
        assertEquals(2, picker.listView.adapter.count)
        (0 until picker.listView.adapter.count).forEach { assertFalse(picker.listView.isItemChecked(it)) }
        assertTrue(text(picker).contains("bot-kora"))
        assertTrue(text(picker).contains("bot-lint"))
        assertFalse(text(picker).contains("PRIVATE_NOTE_NOT_IN_PICKER"))
        assertFalse(text(picker).contains("Barcelona"))
        assertFalse(text(picker).contains("Ich arbeite in Wien."))
        select(picker, selected)
        approve(picker)
        assertTrue(text(draft), text(draft).contains(selected))
        click(draft, "An Chefbot übergeben")
        assertTrue(store.teamState().tasks.isEmpty())
        assertTrue(prompts.isEmpty())
        submit()
        prompts.forEach {
            val facts = JSONObject(it.substringAfter("\nTASK_DATA_JSON:\n")).getJSONArray("ownerApprovedSharedFacts")
            assertEquals(1, facts.length())
            assertEquals(selected, facts.getJSONObject(0).getString("text"))
            assertFalse(it.contains(other))
            assertFalse(it.contains("PRIVATE_NOTE_NOT_IN_PICKER"))
        }
    }

    @Test fun cancellingFactPickerKeepsTaskApprovalFreeOfStoredFacts() {
        val picker = picker()
        select(picker, selected)
        assertTrue(picker.getButton(AlertDialog.BUTTON_NEGATIVE).performClick())
        shadowOf(Looper.getMainLooper()).idle()
        submit()
        assertNoSharedFacts()
    }

    @Test fun editingAndRestoringTaskInvalidatesEarlierFactSelection() {
        val picker = picker()
        select(picker, selected); approve(picker)
        editor().setText("Geänderter Auftrag")
        editor().setText(request)
        assertTrue(text(draft), text(draft).contains("neu auswählen"))
        submit()
        assertNoSharedFacts()
    }

    @Test fun discardDuringQueuedApprovalCannotRestoreWithdrawnSharing() {
        val picker = picker()
        select(picker, selected); approve(picker, drain = false)
        assertEquals(1, io.pending.size)
        assertTrue(button(draft, "Angabenauswahl verwerfen").performClick())
        io.drain()
        assertTrue(text(draft), text(draft).contains("Keine gespeicherten Angaben freigegeben."))
        submit()
        assertNoSharedFacts()
    }

    @Test fun changedAndRestoredTaskDuringQueuedApprovalStillRequiresNewSelection() {
        val picker = picker()
        select(picker, selected); approve(picker, drain = false)
        assertEquals(1, io.pending.size)
        editor().setText("Zwischenzeitlich geändert")
        editor().setText(request)
        io.drain()
        submit()
        assertNoSharedFacts()
    }

    @Test fun closedDraftCannotStartOrRedisplayAfterQueuedApproval() {
        val picker = picker()
        select(picker, selected); approve(picker, drain = false)
        assertEquals(1, io.pending.size)
        roster.close()
        io.drain()
        assertFalse(draft.isShowing)
        assertFalse(picker.isShowing)
        assertTrue(store.teamState().tasks.isEmpty())
        assertTrue(prompts.isEmpty())
    }

    @Test fun sourceRevokedBeforeApprovalIsRejectedWithoutImplicitSharing() {
        val picker = picker()
        select(picker, selected)
        core.revokeOwnerFact(selected)
        approve(picker)
        assertTrue(text(draft), text(draft).contains("widerrufen oder nicht sicher lesbar"))
        submit()
        assertNoSharedFacts()
    }
}
