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
                onTeamSubmit = { id, request, revision ->
                    io.execute { team.submit(id, request, revision, true) }
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
}
