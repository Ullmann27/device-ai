package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import at.heinz.deviceai.chief.ChiefBotProfile
import at.heinz.deviceai.chief.ChiefBotProfileStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/** Real persisted profiles and actual dialog callbacks; no model/activity capability claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class IndependentBotSelectionUiTest {
    private lateinit var lifecycle: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var root: File
    private lateinit var store: ChiefBotProfileStore
    private lateinit var roster: ContinuumBotRosterDialog
    private val pending = ConcurrentLinkedQueue<Runnable>()
    private val selected = mutableListOf<ChiefBotProfile>()
    private val statusIds = mutableListOf<String>()
    private val invalidations = mutableListOf<Pair<String, Int>>()
    private val io = Executor { pending.add(it) }

    @Before fun setup() {
        lifecycle = Robolectric.buildActivity(Activity::class.java).setup()
        activity = lifecycle.get()
        root = Files.createTempDirectory("independent-bot-selection-").toFile()
        store = ChiefBotProfileStore(root)
        roster = ContinuumBotRosterDialog(activity, store, io,
            onSelect = { selected += it },
            runtimeStatus = { profile -> statusIds += profile.agentId; "REGISTRIERT · Keine Inferenz gestartet" },
            onContextChanging = { id -> invalidations += id to pending.size })
    }
    @After fun cleanup() {
        roster.close()
        if (!activity.isDestroyed) lifecycle.pause().stop().destroy()
        root.deleteRecursively()
    }
    private fun main() { Shadows.shadowOf(Looper.getMainLooper()).idle() }
    private fun drain() {
        var count = 0
        while (pending.isNotEmpty()) {
            check(++count <= 30)
            val task = requireNotNull(pending.poll())
            val failure = AtomicReference<Throwable?>()
            val worker = Thread { try { task.run() } catch (e: Throwable) { failure.set(e) } }
            worker.start(); worker.join(10_000)
            check(!worker.isAlive)
            failure.get()?.let { throw AssertionError("Storage callback failed", it) }
            main()
        }
    }
    private fun current(): AlertDialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
    private fun views(view: View = current().window!!.decorView): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(views(view.getChildAt(i)))
    }
    private fun button(text: String) = views().filterIsInstance<Button>().single { it.text.toString() == text }
    private fun speechButton() = views().filterIsInstance<Button>().first { it.text.startsWith("Mit ") && it.text.endsWith(" sprechen") }
    private fun open() { roster.show(); drain() }

    @Test fun customRenamedIdentityComesFromStoreAndOnlyExplicitClickSelects() {
        val created = store.create("aaa-independent", "Ursprünglicher Name", "Eigene Prüfung", "Nennt Unsicherheit")
        val renamed = store.rename(created.agentId, "Eigener Profilname", created.profileVersion)
        open()
        assertEquals(21, ChiefBotProfileStore(root).page(limit = 100).items.size)
        assertTrue(selected.isEmpty())
        assertEquals(8, statusIds.size)
        assertTrue(created.agentId in statusIds)
        button("Mit Eigener Profilname sprechen").performClick(); main()
        val observed = selected.single()
        assertEquals(renamed.agentId, observed.agentId)
        assertEquals(renamed.displayName, observed.displayName)
        assertEquals(created.memoryNamespace, observed.memoryNamespace)
        assertEquals(created.workspaceId, observed.workspaceId)
        assertEquals(created.permissionScope, observed.permissionScope)
        assertFalse(current().isShowing)
    }

    @Test fun repeatedClickOnSameSelectionIsConsumedExactlyOnce() {
        open(); val action = speechButton()
        action.performClick(); action.performClick(); main()
        assertEquals("A closed selection cannot admit another callback", 1, selected.size)
    }

    @Test fun oldSelectionCannotCloseOrSelectFromNewWindow() {
        open(); val stale = speechButton()
        open(); val replacement = current()
        stale.performClick(); main()
        assertTrue("Old window must not issue selection", selected.isEmpty())
        assertSame(replacement, current())
        assertTrue("Old selection must not close the new window", replacement.isShowing)
    }

    @Test fun selectionDeliveredAfterDismissIsIgnored() {
        open(); val stale = speechButton()
        current().dismiss(); main()
        stale.performClick(); main()
        assertTrue(selected.isEmpty())
    }

    @Test fun selectionDeliveredAfterActivityDestroyIsIgnored() {
        open(); val stale = speechButton()
        lifecycle.pause().stop().destroy()
        stale.performClick(); main()
        assertTrue(selected.isEmpty())
    }

    @Test fun detailSelectionUsesSavedRenameOnSameStableIdentity() {
        open()
        views().filterIsInstance<Button>().first { it.text == "Profil und Arbeitsbereich öffnen" }.performClick(); drain()
        val id = store.page(limit = 100).items.first().agentId
        button("Namen ändern").performClick(); main()
        views().filterIsInstance<EditText>().single().setText("Detailname bleibt editierbar")
        button("Name speichern").performClick(); drain()
        button("Mit Detailname bleibt editierbar sprechen").performClick(); main()
        assertEquals(id, selected.single().agentId)
        assertEquals("Detailname bleibt editierbar", selected.single().displayName)
        assertEquals(store.get(id)?.profileVersion, selected.single().profileVersion)
    }

    @Test fun contextInvalidationHappensBeforeSelectedIdentityWriteIsQueuedOrCompleted() {
        open()
        views().filterIsInstance<Button>().first { it.text == "Profil und Arbeitsbereich öffnen" }.performClick(); drain()
        val id = store.page(limit = 100).items.first().agentId
        views().filterIsInstance<EditText>().single().setText("Neue private Arbeitsnotiz")
        button("Arbeitsnotiz speichern").performClick(); main()
        assertEquals(listOf(id to 0), invalidations)
        assertEquals(1, pending.size)
        assertNull(store.workingContext(id, id))
        drain()
        assertEquals("Neue private Arbeitsnotiz", store.workingContext(id, id))
        assertEquals(1, invalidations.size)
        assertTrue(selected.isEmpty())
    }

    @Test fun staleContextEditCannotInvalidateOrWriteAfterAnotherWindowOpens() {
        open()
        views().filterIsInstance<Button>().first { it.text == "Profil und Arbeitsbereich öffnen" }.performClick(); drain()
        val id = store.page(limit = 100).items.first().agentId
        views().filterIsInstance<EditText>().single().setText("Veraltete Eingabe")
        val staleSave = button("Arbeitsnotiz speichern")
        open(); val replacement = current()
        staleSave.performClick(); main()
        assertTrue(invalidations.isEmpty())
        assertTrue(pending.isEmpty())
        assertNull(store.workingContext(id, id))
        assertSame(replacement, current())
        assertTrue(replacement.isShowing)
    }
}
