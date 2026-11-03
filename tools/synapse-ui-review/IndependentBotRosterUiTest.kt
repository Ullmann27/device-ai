package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
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
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference

/** Independent tests of actual widgets and real persisted registry. No fake store or model. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class IndependentBotRosterUiTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var dir: File
    private lateinit var store: ChiefBotProfileStore
    private lateinit var io: QueuedIo
    private lateinit var roster: ContinuumBotRosterDialog

    private class QueuedIo : Executor {
        val pending = ConcurrentLinkedQueue<Runnable>()
        var submissions = 0
        override fun execute(command: Runnable) { submissions++; pending.add(command) }
        fun one() {
            val task = requireNotNull(pending.poll())
            val failure = AtomicReference<Throwable?>()
            val worker = Thread {
                try {
                    check(Looper.myLooper() != Looper.getMainLooper())
                    task.run()
                } catch (e: Throwable) { failure.set(e) }
            }
            worker.start(); worker.join(10_000)
            check(!worker.isAlive) { "Background I/O did not finish" }
            failure.get()?.let { throw AssertionError("Background task crashed", it) }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        fun drain() { var count = 0; while (pending.isNotEmpty()) { check(++count <= 30); one() } }
    }

    @Before fun setup() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
        dir = Files.createTempDirectory("independent-bot-roster-").toFile()
        store = ChiefBotProfileStore(dir)
        io = QueuedIo()
        roster = ContinuumBotRosterDialog(activity, store, io)
    }
    @After fun cleanup() {
        ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
        if (!activity.isDestroyed) controller.pause().stop().destroy()
        dir.deleteRecursively()
    }
    private fun current(): AlertDialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
    private fun views(root: View = current().window!!.decorView): List<View> = buildList {
        add(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) addAll(views(root.getChildAt(i)))
    }
    private fun text(): String = views().filterIsInstance<TextView>().joinToString("\n") { it.text.toString() }
    private fun buttons(value: String) = views().filterIsInstance<Button>().filter { it.text.toString() == value }
    private fun click(value: String) { val button = buttons(value).single(); assertTrue(button.isEnabled); button.performClick(); Shadows.shadowOf(Looper.getMainLooper()).idle() }
    private fun open() { roster.show(); io.drain() }
    private fun firstProfile() { buttons("Profil und Arbeitsbereich öffnen").first().performClick(); io.drain() }
    private fun allProfiles() = ChiefBotProfileStore(dir).page(limit = 100).items
    private fun editor() = views().filterIsInstance<EditText>().single()
    private fun profileNamesOnPage() = allProfiles().filter { p -> views().filterIsInstance<TextView>().any { it.text.toString() == p.displayName + " · " + p.role } }.map { it.agentId }

    @Test fun actualTwentyProfilesPageExactlyOnceAndRegisteredMeansNoRunningModel() {
        open()
        assertEquals(20, allProfiles().size)
        assertEquals(setOf("Atlas", "Mira", "Echo", "Mnemos", "Prisma", "Nova", "Sigma", "Kora", "Forge", "Lint", "Argus", "Pulse", "Link", "Chronos", "Vox", "Iris", "Orbit", "Archiv", "Scout", "Helix"), allProfiles().map { it.displayName }.toSet())
        val seen = mutableListOf<String>()
        repeat(3) { page ->
            val names = profileNamesOnPage()
            assertEquals(if (page < 2) 8 else 4, names.size)
            seen += names
            assertTrue(text().contains("REGISTRIERT"))
            assertFalse(text().contains("MODELL AKTIV"))
            if (page < 2) { click("Weitere Bots"); io.drain() }
        }
        assertEquals(20, seen.size); assertEquals(20, seen.toSet().size)
        assertTrue(buttons("Weitere Bots").isEmpty())
        assertTrue(allProfiles().all { it.verifiedCapabilities.isEmpty() && it.permissionScope.isEmpty() && it.currentTaskId == null })
    }

    @Test fun repeatedOpeningHonorsOneInFlightStorageBudget() {
        repeat(16) { roster.show() }
        assertEquals("Repeated openings must not queue unbounded registry work", 1, io.pending.size)
    }

    @Test fun storageAndSeedingWaitForBackgroundExecutorWhileMainLooperRemainsResponsive() {
        roster.show()
        assertEquals(1, io.pending.size)
        assertFalse(File(dir, "bot-profiles-v1.bin").exists())
        var responsive = false
        android.os.Handler(Looper.getMainLooper()).post { responsive = true }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(responsive)
        assertTrue(text().contains("werden geladen"))
        io.drain()
        assertTrue(File(dir, "bot-profiles-v1.bin").exists())
        assertEquals(20, allProfiles().size)
    }

    @Test fun twentyFirstProfileIsCreatedThroughUiAndSurvivesStoreAndDialogRestart() {
        open(); click("Bot hinzufügen")
        val fields = views().filterIsInstance<EditText>()
        assertEquals(3, fields.size)
        fields[0].setText("Neue Expertin"); fields[1].setText("Zusätzliche Forschung"); fields[2].setText("Prüft Quellen und nennt Unsicherheit")
        click("Profil anlegen"); io.drain()
        val created = allProfiles().single { it.displayName == "Neue Expertin" }
        assertEquals(21, allProfiles().size)
        assertTrue(text().contains(created.workspaceId))
        current().dismiss(); Shadows.shadowOf(Looper.getMainLooper()).idle()
        roster = ContinuumBotRosterDialog(activity, ChiefBotProfileStore(dir), io)
        open()
        val seen = mutableSetOf<String>()
        while (true) {
            seen += profileNamesOnPage()
            if (buttons("Weitere Bots").isEmpty()) break
            click("Weitere Bots"); io.drain()
        }
        assertEquals(21, seen.size); assertTrue(created.agentId in seen)
    }

    @Test fun renameAndNotesStayOnStableIdentityAcrossRestartWithoutGrantingRights() {
        open()
        val selected = allProfiles().sortedBy { it.agentId }.first()
        val other = allProfiles().first { it.agentId != selected.agentId }
        firstProfile()
        editor().setText("Private Arbeitsnotiz: überprüfte Frage bleibt offen.")
        click("Arbeitsnotiz speichern"); io.drain()
        click("Namen ändern"); editor().setText("Neuer sichtbarer Name")
        click("Name speichern"); io.drain()
        val fresh = ChiefBotProfileStore(dir)
        val renamed = requireNotNull(fresh.get(selected.agentId))
        assertEquals("Neuer sichtbarer Name", renamed.displayName)
        assertEquals(selected.memoryNamespace, renamed.memoryNamespace)
        assertEquals(selected.workspaceId, renamed.workspaceId)
        assertEquals(selected.permissionScope, renamed.permissionScope)
        assertEquals("Private Arbeitsnotiz: überprüfte Frage bleibt offen.", fresh.workingContext(selected.agentId, selected.agentId))
        assertNull(fresh.workingContext(other.agentId, other.agentId))
        current().dismiss(); Shadows.shadowOf(Looper.getMainLooper()).idle()
        roster = ContinuumBotRosterDialog(activity, fresh, io)
        open(); firstProfile()
        assertEquals("Private Arbeitsnotiz: überprüfte Frage bleibt offen.", editor().text.toString())
        assertTrue(text().contains("Neuer sichtbarer Name"))
    }

    @Test fun dismissedLoadCannotReviveWindow() {
        roster.show(); val original = current()
        original.dismiss(); Shadows.shadowOf(Looper.getMainLooper()).idle()
        io.drain()
        assertFalse(original.isShowing)
        assertSame(original, current())
    }

    @Test fun destroyedActivityCannotApplyLateLoad() {
        roster.show(); val original = current()
        controller.pause().stop().destroy()
        io.drain()
        assertTrue(activity.isDestroyed)
        assertSame(original, current())
        assertTrue(text().contains("werden geladen"))
        assertFalse(text().contains("Geprüfte Fähigkeiten"))
    }

    @Test fun olderLoadCannotReplaceNewRosterAndCallbacksStayBounded() {
        roster.show(); val first = current()
        roster.show(); val second = current()
        assertNotSame(first, second)
        assertEquals(2, io.pending.size)
        io.one()
        assertSame(second, current())
        assertTrue(text().contains("werden geladen"))
        io.one()
        assertSame(second, current())
        assertEquals(8, buttons("Profil und Arbeitsbereich öffnen").size)
        assertEquals(2, io.submissions)
    }

    @Test fun lateSaveWritesOnlySelectedIdentityAndDoesNotReopenItsProfile() {
        open(); val selected = allProfiles().sortedBy { it.agentId }.first(); firstProfile()
        editor().setText("Nur für die ausgewählte stabile ID")
        click("Arbeitsnotiz speichern")
        assertFalse(buttons("Arbeitsnotiz speichern").single().isEnabled)
        roster.show(); val newer = current()
        io.one()
        assertSame(newer, current()); assertTrue(text().contains("werden geladen"))
        io.drain()
        assertSame(newer, current())
        assertEquals("Nur für die ausgewählte stabile ID", store.workingContext(selected.agentId, selected.agentId))
        assertEquals(1, allProfiles().count { it.contextObjectId != null })
    }

    @Test fun corruptedRegistryShowsFailureAndDoesNotSilentlyReseed() {
        open(); current().dismiss(); Shadows.shadowOf(Looper.getMainLooper()).idle()
        val file = File(dir, "bot-profiles-v1.bin")
        val corrupt = byteArrayOf(0, 1, 2, 3, 4, 5)
        file.writeBytes(corrupt)
        open()
        assertTrue(text().contains("nicht sicher gelesen oder gespeichert"))
        assertFalse(text().contains("REGISTRIERT"))
        assertArrayEquals(corrupt, file.readBytes())
        assertTrue(buttons("Profil und Arbeitsbereich öffnen").isEmpty())
    }

    @Test fun staleProfileSaveShowsFailureWithoutOverwritingNewerName() {
        open(); val selected = allProfiles().sortedBy { it.agentId }.first(); firstProfile()
        click("Namen ändern"); editor().setText("Veraltete Änderung")
        store.rename(selected.agentId, "Neuere bestätigte Änderung", selected.profileVersion)
        click("Name speichern"); io.drain()
        assertTrue(text().contains("nicht sicher gelesen oder gespeichert"))
        assertEquals("Neuere bestätigte Änderung", store.get(selected.agentId)?.displayName)
    }

    @Test fun rejectedIoQueueShowsFailureInsteadOfFakeProfiles() {
        val rejecting = Executor { throw RejectedExecutionException("independent shutdown") }
        ContinuumBotRosterDialog(activity, store, rejecting).show()
        assertTrue(text().contains("Sitzung wird beendet"))
        assertTrue(buttons("Profil und Arbeitsbereich öffnen").isEmpty())
        assertFalse(File(dir, "bot-profiles-v1.bin").exists())
    }

    @Test fun narrowLargeFontHasScrollableReachableControlsAndLabelledWorkingEditor() {
        val configuration = Configuration(activity.resources.configuration).apply { fontScale = 2f }
        activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
        open(); firstProfile()
        val dialog = current()
        dialog.window!!.setLayout(320, 640)
        val decor = dialog.window!!.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 320, 640)
        val scroll = views().filterIsInstance<ScrollView>().first { candidate -> views(candidate).filterIsInstance<Button>().any { it.text.toString() == "Arbeitsnotiz speichern" } }
        assertTrue(scroll.getChildAt(0).measuredHeight > scroll.measuredHeight)
        for (button in views(scroll).filterIsInstance<Button>()) {
            assertTrue("${button.text} touch height ${button.measuredHeight}", button.measuredHeight >= 48)
            assertTrue(button.measuredWidth <= 320)
            assertTrue(button.text.isNotBlank() || !button.contentDescription.isNullOrBlank())
        }
        scroll.fullScroll(View.FOCUS_DOWN)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(scroll.scrollY > 0)
        val input = editor()
        val associatedLabel = views().filterIsInstance<TextView>().any { input.id != View.NO_ID && it.labelFor == input.id }
        assertTrue("Working note editor needs accessible name or labelFor association", !input.contentDescription.isNullOrBlank() || !input.hint.isNullOrBlank() || associatedLabel)
    }
}
