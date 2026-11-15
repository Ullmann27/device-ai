package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.chief.ChiefBotProfileStore
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
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/** Real roster, background executor and temporary filesystem; no model/runtime is created. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumBotRosterStorageUiTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var parent: File
    private lateinit var io: QueuedIo
    private var roster: ContinuumBotRosterDialog? = null

    private class QueuedIo : Executor {
        val pending = ConcurrentLinkedQueue<Runnable>()
        override fun execute(command: Runnable) { pending.add(command) }
        fun drain() {
            var count = 0
            while (pending.isNotEmpty()) {
                check(++count <= 20)
                val task = pending.remove()
                val failure = AtomicReference<Throwable?>()
                val worker = Thread {
                    try { check(Looper.myLooper() != Looper.getMainLooper()); task.run() }
                    catch (error: Throwable) { failure.set(error) }
                }
                worker.start(); worker.join(10_000)
                check(!worker.isAlive)
                failure.get()?.let { throw AssertionError("Roster background task failed", it) }
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }

    @Before fun setup() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
        parent = Files.createTempDirectory("bot-roster-ui-").toFile()
        io = QueuedIo()
    }
    @After fun cleanup() {
        roster?.close()
        if (!activity.isDestroyed) controller.pause().stop().destroy()
        parent.deleteRecursively()
    }
    private fun current(): AlertDialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
    private fun views(root: View = current().window!!.decorView): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun text() = views().filterIsInstance<TextView>().joinToString("\n") { it.text.toString() }
    private fun buttons(label: String) = views().filterIsInstance<Button>().filter { it.text.toString() == label }
    private fun click(label: String) { assertTrue(buttons(label).single().performClick()); io.drain() }
    private fun open(store: ChiefBotProfileStore) {
        roster = ContinuumBotRosterDialog(activity, store, io).also { it.show() }
        io.drain()
    }
    private fun assertDiagnostic(code: String) {
        assertTrue(text(), text().contains("Speicherdiagnose: $code"))
        assertTrue(views().any { it.contentDescription == "Bot-Speicherdiagnose: $code" })
        assertFalse(text().contains(parent.absolutePath))
        assertTrue(buttons("Profil und Arbeitsbereich öffnen").isEmpty())
    }

    @Test fun aliasedSystemParentShowsTwentyBotsAndEditsKeepStableIdentityAfterReopen() {
        val managed = File(parent, "managed").apply { mkdir() }
        val files = File(managed, "app/files").apply { mkdirs() }
        val alias = File(parent, "system-alias")
        Files.createSymbolicLink(alias.toPath(), managed.toPath())
        val aliasedFiles = File(alias, "app/files")
        val store = ChiefBotProfileStore(File(aliasedFiles, "chief-bots"), trustedParentDirectory = aliasedFiles)
        open(store)
        assertEquals(20, buttons("Profil und Arbeitsbereich öffnen").size)
        assertTrue(text().contains("REGISTRIERT"))
        val original = store.page().items.first()
        buttons("Profil und Arbeitsbereich öffnen").first().performClick(); io.drain()
        click("Namen ändern")
        views().filterIsInstance<EditText>().single().setText("Dauerhafter Besitzername")
        click("Name speichern")
        val reopened = ChiefBotProfileStore(File(files, "chief-bots"), trustedParentDirectory = files)
        val renamed = reopened.get(original.agentId)!!
        assertEquals("Dauerhafter Besitzername", renamed.displayName)
        assertEquals(original.memoryNamespace, renamed.memoryNamespace)
        assertEquals(original.workspaceId, renamed.workspaceId)
        assertTrue(renamed.permissionScope.isEmpty())
        click("Zur Teamübersicht")
        assertEquals(20, buttons("Profil und Arbeitsbereich öffnen").size)
        assertTrue(buttons("Weitere Bots").isEmpty())
        assertEquals(20, reopened.page().items.size)
    }

    @Test fun chiefSelectionAndBlockedTaskSurviveReturnToTeamView() {
        val store = ChiefBotProfileStore(File(parent, "team"))
        val model = object : at.heinz.deviceai.chief.ChiefTeamModel {
            override fun readySha256(): String? = null
            override fun generate(prompt: String, cancellation: at.heinz.deviceai.cognitive.DeliberationCancellation,
                                  deadlineMillis: Long): String = error("No model in this widget test")
        }
        val team = at.heinz.deviceai.chief.ChiefBotTeam(store, model,
            changed = { activity.runOnUiThread { roster?.refreshRuntimeStatus() } })
        try {
            team.initialize()
            team.configure("bot-atlas", listOf("bot-kora", "bot-lint"), 0)
            roster = ContinuumBotRosterDialog(activity, store, io, team = team,
                onTeamSubmit = { id, request, revision, shared, followup ->
                    io.execute { team.submit(id, request, revision, true, shared = shared, followup = followup) }
                    true
                }).also { it.show() }
            io.drain()
            val chosen = store.page(limit = 20).items.first()
            buttons("Profil und Arbeitsbereich öffnen").first().performClick(); io.drain()
            click("Als Chefbot bestimmen")
            assertEquals(chosen.agentId, store.teamState().chiefId)
            click("Zur Teamübersicht")
            click("Teamaufträge und Ergebnisse")
            click("Chefbot einen Textprüfauftrag geben")
            views().filterIsInstance<EditText>().single().setText("Prüfe nur diesen Text.")
            views().filterIsInstance<android.widget.CheckBox>().single().isChecked = true
            click("An Chefbot übergeben")
            assertTrue(text(), text().contains("MODEL_UNAVAILABLE"))
            assertTrue(text(), text().contains("Blockiert"))
            assertEquals(1, store.teamState().tasks.size)
            click("Zur Teamübersicht")
            click("Teamaufträge und Ergebnisse")
            assertTrue(text().contains("Prüfe nur diesen Text."))
            assertEquals(chosen.agentId, store.teamState().chiefId)
        } finally { team.close() }
    }

    @Test fun corruptRegistryShowsAccessibleFixedDiagnosticAndPreservesOwnerBytes() {
        val dir = File(parent, "bots").apply { mkdir() }
        val bytes = byteArrayOf(0, 1)
        val registry = File(dir, "bot-profiles-v1.bin").apply { writeBytes(bytes) }
        open(ChiefBotProfileStore(dir))
        assertDiagnostic("BOT_REGISTRY_CORRUPT")
        assertArrayEquals(bytes, registry.readBytes())
        click("Übersicht erneut öffnen")
        assertDiagnostic("BOT_REGISTRY_CORRUPT")
        assertArrayEquals(bytes, registry.readBytes())
    }

    @Test fun unsupportedSchemaShowsCompatibleVersionAdviceWithoutReseeding() {
        val dir = File(parent, "bots").apply { mkdir() }
        val bytes = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use {
            it.writeUTF("CONTINUUM_BOT_PROFILES"); it.writeInt(999)
        } }.toByteArray()
        val registry = File(dir, "bot-profiles-v1.bin").apply { writeBytes(bytes) }
        open(ChiefBotProfileStore(dir))
        assertDiagnostic("BOT_REGISTRY_UNSUPPORTED_SCHEMA")
        assertTrue(text().contains("kompatible App-Version"))
        assertArrayEquals(bytes, registry.readBytes())
    }

    @Test fun realReadonlyDirectoryFailureDoesNotShowSeededProfilesOrReplaceRegistry() {
        val dir = File(parent, "bots")
        val store = ChiefBotProfileStore(dir)
        store.seedDefaults(3)
        val registry = File(dir, "bot-profiles-v1.bin")
        val bytes = registry.readBytes()
        val mode = Files.getPosixFilePermissions(dir.toPath())
        try {
            Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("r-x------"))
            open(store)
            assertDiagnostic("BOT_STORE_ACCESS_DENIED")
            assertArrayEquals(bytes, registry.readBytes())
            assertEquals(3, store.page().items.size)
        } finally { Files.setPosixFilePermissions(dir.toPath(), mode) }
    }

    @Test fun unsafeBotRootLinkShowsSafetyDiagnosticWithoutOutsideWrite() {
        val outside = File(parent, "outside").apply { mkdir() }
        val files = File(parent, "files").apply { mkdir() }
        val dir = File(files, "bots")
        Files.createSymbolicLink(dir.toPath(), outside.toPath())
        open(ChiefBotProfileStore(dir, trustedParentDirectory = files))
        assertDiagnostic("BOT_STORE_SYMLINK_REJECTED")
        assertTrue(outside.listFiles()!!.isEmpty())
    }

    @Test fun closedRosterDoesNotReviveAfterBackgroundPersistenceCompletes() {
        val dir = File(parent, "bots")
        roster = ContinuumBotRosterDialog(activity, ChiefBotProfileStore(dir), io).also { it.show() }
        val original = current()
        assertEquals(1, io.pending.size)
        roster!!.close()
        io.drain()
        assertFalse(original.isShowing)
        assertSame(original, current())
        assertEquals(20, ChiefBotProfileStore(dir).page().items.size)
    }

    @Test fun privateSearchShowsActualDatesAndOnlyTheSelectedBotsCurrentRecords() {
        val root = File(parent, "private-records")
        val store = ChiefBotProfileStore(root)
        val profiles = store.seedDefaults(20)
        val selected = profiles.sortedBy { it.agentId }.first()
        val other = profiles.first { it.agentId != selected.agentId }
        store.saveWorkingContext(selected.agentId, selected.agentId, "EIGENE_PRIVATE_NOTIZ", selected.profileVersion)
        store.saveWorkingContext(other.agentId, other.agentId, "ANDERE_PRIVATE_NOTIZ", other.profileVersion)
        open(store)
        buttons("Profil und Arbeitsbereich öffnen").first().performClick(); io.drain()
        click("Private Aufzeichnungen suchen")
        assertTrue(text(), text().contains("EIGENE_PRIVATE_NOTIZ"))
        assertFalse(text(), text().contains("ANDERE_PRIVATE_NOTIZ"))
        val record = store.privateRecords(selected.agentId, selected.agentId).records.single()
        val date = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss z", java.util.Locale.getDefault())
        assertTrue(text(), text().contains(date.format(java.util.Date(record.createdAtMillis))))
        assertTrue(text(), text().contains("nicht einzelner Aussagen"))
        views().filterIsInstance<EditText>().single().setText("andere_private")
        click("Suchen")
        assertTrue(text(), text().contains("Keine aktuelle private Aufzeichnung enthält diesen Suchtext."))
        assertFalse(text().contains("EIGENE_PRIVATE_NOTIZ"))
        click("Alle aktuellen Aufzeichnungen")
        assertTrue(text().contains("EIGENE_PRIVATE_NOTIZ"))
        assertFalse(text().contains("ANDERE_PRIVATE_NOTIZ"))
    }

    @Test fun clearingNoteThroughExistingEditorRemovesItFromReopenedPrivateSearch() {
        val root = File(parent, "private-clear")
        val store = ChiefBotProfileStore(root)
        val selected = store.seedDefaults(20).sortedBy { it.agentId }.first()
        val noted = store.saveWorkingContext(selected.agentId, selected.agentId, "ENTFERNTE_PRIVATE_NOTIZ", selected.profileVersion)
        open(store)
        buttons("Profil und Arbeitsbereich öffnen").first().performClick(); io.drain()
        click("Private Aufzeichnungen suchen")
        assertTrue(text().contains("ENTFERNTE_PRIVATE_NOTIZ"))
        click("Zurück zum Arbeitsbereich")
        views().filterIsInstance<EditText>().single().setText("")
        click("Arbeitsnotiz speichern")
        roster!!.close()
        open(ChiefBotProfileStore(root))
        buttons("Profil und Arbeitsbereich öffnen").first().performClick(); io.drain()
        click("Private Aufzeichnungen suchen")
        assertTrue(text(), text().contains("Keine aktuellen privaten Aufzeichnungen gespeichert."))
        assertFalse(text().contains("ENTFERNTE_PRIVATE_NOTIZ"))
        assertTrue(File(root, "working-context/${selected.memoryNamespace}/${noted.contextObjectId}.obj").isFile)
    }

    @Test fun closedPrivateSearchDoesNotPresentItsQueuedStorageResult() {
        val store = ChiefBotProfileStore(File(parent, "private-closed"))
        val selected = store.seedDefaults(20).sortedBy { it.agentId }.first()
        store.saveWorkingContext(selected.agentId, selected.agentId, "PRIVATE_AFTER_CLOSE", selected.profileVersion)
        open(store)
        buttons("Profil und Arbeitsbereich öffnen").first().performClick(); io.drain()
        assertTrue(buttons("Private Aufzeichnungen suchen").single().performClick())
        val old = current()
        assertEquals(1, io.pending.size)
        roster!!.close()
        io.drain()
        assertFalse(old.isShowing)
        assertSame(old, current())
        assertFalse(views(old.window!!.decorView).filterIsInstance<TextView>().any {
            it.text.toString().contains("PRIVATE_AFTER_CLOSE")
        })
    }
}
