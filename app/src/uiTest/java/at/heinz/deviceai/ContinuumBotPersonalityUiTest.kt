package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import at.heinz.deviceai.chief.ChiefBotPersonas
import at.heinz.deviceai.chief.ChiefBotProfileStore
import at.heinz.deviceai.chief.ChiefBotTaskMode
import at.heinz.deviceai.chief.ChiefBotTaskRequest
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/** Exercises real persisted profiles and Android widgets; never opens a model or tool runtime. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumBotPersonalityUiTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var directory: File
    private lateinit var store: ChiefBotProfileStore
    private lateinit var io: QueuedIo
    private var roster: ContinuumBotRosterDialog? = null
    private var composer: ContinuumBotTaskDialog? = null

    private class QueuedIo : Executor {
        private val pending = ConcurrentLinkedQueue<Runnable>()
        override fun execute(command: Runnable) { pending.add(command) }
        fun drain() {
            var count = 0
            while (pending.isNotEmpty()) {
                check(++count <= 30)
                val task = pending.remove()
                val failure = AtomicReference<Throwable?>()
                val worker = Thread {
                    try { task.run() } catch (error: Throwable) { failure.set(error) }
                }
                worker.start()
                worker.join(10_000)
                check(!worker.isAlive)
                failure.get()?.let { throw AssertionError("Roster I/O failed", it) }
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }

    @Before fun setup() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
        directory = Files.createTempDirectory("bot-personality-ui-").toFile()
        store = ChiefBotProfileStore(File(directory, "profiles"))
        store.seedDefaults(20)
        io = QueuedIo()
    }

    @After fun cleanup() {
        composer?.close()
        roster?.close()
        if (!activity.isDestroyed) controller.pause().stop().destroy()
        directory.deleteRecursively()
    }

    private fun current(): AlertDialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
    private fun views(root: View = current().window!!.decorView): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun labels() = views().filterIsInstance<TextView>().map { it.text.toString() }
    private fun field(): EditText = views().filterIsInstance<EditText>().single { it.tag == "bot-task-text" }
    private fun submit(): Button = views().filterIsInstance<Button>().single { it.tag == "bot-task-submit" }
    private fun choose(mode: ChiefBotTaskMode) {
        views().filterIsInstance<RadioButton>().single { it.tag == "bot-task-mode-${mode.name}" }.performClick()
    }
    private fun openComposer(assign: (ChiefBotTaskRequest) -> Boolean) {
        roster = ContinuumBotRosterDialog(activity, store, io, onSelect = {},
            runtimeStatus = { "Lokales Modell nicht geladen · Kein Auftrag aktiv" }, onAssign = assign).also { it.show() }
        io.drain()
        views().filterIsInstance<Button>().first { it.text.toString() == "Auftrag geben" }.performClick()
    }

    @Test fun searchFindsOwnerEditedProfileBeyondFirstPageByNameRoleAndStyle() {
        val last = store.page(limit = 100).items.last()
        val renamed = store.rename(last.agentId, "Quasar Studio", last.profileVersion)
        store.updateDescription(last.agentId, "Mosaikplanung", "ruhig und einzigartig", renamed.profileVersion)
        roster = ContinuumBotRosterDialog(activity, store, io, onSelect = {}).also { it.show() }
        io.drain()
        val search = views().filterIsInstance<EditText>()
            .singleOrNull { it.contentDescription == "Bots nach Name, Rolle oder Arbeitsstil suchen" }
        assertNotNull("The roster needs an accessible search across stored pages", search)
        for (query in listOf("QUASAR", "mosaik", "einzigartig")) {
            search!!.setText(query)
            io.drain()
            assertTrue(labels().toString(), labels().contains("Quasar Studio"))
            assertEquals(1, views().filterIsInstance<Button>()
                .count { it.text.toString() == "Profil und Arbeitsbereich öffnen" })
        }
    }

    @Test fun rosterDisplaysDistinctWorkingMethodAndRealRuntimeState() {
        val first = store.page(limit = 100).items.first()
        roster = ContinuumBotRosterDialog(activity, store, io, onSelect = {},
            runtimeStatus = { "MODELL NICHT BEREIT · ${it.agentId}" }).also { it.show() }
        io.drain()
        assertTrue("Each bot's working method must be visible", labels().contains(ChiefBotPersonas.describe(first).approach))
        assertTrue(labels().contains("MODELL NICHT BEREIT · ${first.agentId}"))
        assertFalse(labels().any { it.contains("Einsatzbereit", ignoreCase = true) })
    }

    @Test fun rapidSearchRendersOnlyTheLatestQuery() {
        roster = ContinuumBotRosterDialog(activity, store, io).also { it.show() }
        io.drain()
        val search = views().filterIsInstance<EditText>().single()
        search.setText("atlas")
        search.setText("mira")
        search.setText("nova")
        io.drain()
        assertTrue(labels().contains("Nova"))
        assertFalse(labels().contains("Atlas"))
        assertFalse(labels().contains("Mira"))
        assertEquals(1, views().filterIsInstance<Button>().count { it.text.toString() == "Profil und Arbeitsbereich öffnen" })
    }

    @Test fun exampleOnlyFillsEditableDraftAndFinalExplicitHandoffIsOneShot() {
        val assignments = mutableListOf<ChiefBotTaskRequest>()
        openComposer { assignments += it; true }
        val first = store.page(limit = 100).items.first()
        assertTrue(assignments.isEmpty())
        assertTrue(views().filterIsInstance<RadioButton>().none { it.isChecked })
        views().filterIsInstance<Button>().single { it.tag == "bot-task-example-0" }.performClick()
        assertEquals(ChiefBotPersonas.describe(first).starterRequests.first(), field().text.toString())
        assertTrue(assignments.isEmpty())
        assertFalse(submit().isEnabled)
        val fullRequest = "Plane meine Schritte.\nÖffne keine App und verwende kein Internet."
        field().setText(fullRequest)
        choose(ChiefBotTaskMode.PRIVATE_REPLY)
        val finalButton = submit()
        assertTrue(finalButton.isEnabled)
        finalButton.performClick()
        finalButton.performClick()
        assertEquals(listOf(ChiefBotTaskRequest(first.agentId, first.profileVersion, fullRequest,
            ChiefBotTaskMode.PRIVATE_REPLY)), assignments)
        assertFalse(current().isShowing)
    }

    @Test fun rejectedHandoffPreservesCompleteDraftAndAllowsExplicitRetry() {
        val assignments = mutableListOf<ChiefBotTaskRequest>()
        openComposer { assignments += it; false }
        val fullRequest = "  Prüfe diesen Status.\nNichts löschen; offline bleiben.  "
        field().setText(fullRequest)
        choose(ChiefBotTaskMode.LOCAL_TOOLS)
        submit().performClick()
        assertTrue(current().isShowing)
        assertEquals(fullRequest, field().text.toString())
        assertEquals(fullRequest, assignments.single().text)
        assertTrue(labels().any { it.contains("vollständiger Auftrag bleibt") })
        submit().performClick()
        assertEquals(2, assignments.size)
        assertEquals(assignments.first(), assignments.last())
    }

    @Test fun cancelledOrDestroyedComposerControlsCannotSubmitLater() {
        var calls = 0
        openComposer { calls++; true }
        field().setText("Plane einen nächsten Schritt.")
        choose(ChiefBotTaskMode.PRIVATE_REPLY)
        val cancelledButton = submit()
        roster!!.close()
        cancelledButton.performClick()
        assertEquals(0, calls)
        openComposer { calls++; true }
        field().setText("Plane einen nächsten Schritt.")
        choose(ChiefBotTaskMode.PRIVATE_REPLY)
        val destroyedButton = submit()
        controller.pause().stop().destroy()
        destroyedButton.performClick()
        assertEquals(0, calls)
    }

    @Test fun replacingComposerMakesOldControlsInert() {
        var calls = 0
        val profile = store.page(limit = 100).items.first()
        composer = ContinuumBotTaskDialog(activity, profile, { calls++; true }).also { it.show() }
        field().setText("Ein alter Auftrag.")
        choose(ChiefBotTaskMode.PRIVATE_REPLY)
        val old = submit()
        val oldCancel = views().filterIsInstance<Button>().single { it.text.toString() == "Abbrechen" }
        composer!!.show()
        field().setText("Dieser neue Entwurf muss erhalten bleiben.")
        old.performClick()
        oldCancel.performClick()
        assertEquals(0, calls)
        assertTrue(current().isShowing)
        assertEquals("Dieser neue Entwurf muss erhalten bleiben.", field().text.toString())
    }

    @Test fun throwingHandoffKeepsDraftButNeverOffersAnUnconfirmedDuplicate() {
        val accepted = mutableListOf<ChiefBotTaskRequest>()
        openComposer { accepted += it; throw IllegalStateException("Failure after custody transfer") }
        val draft = "Nur diese Dateien prüfen. Nichts verändern."
        field().setText(draft)
        choose(ChiefBotTaskMode.LOCAL_TOOLS)
        submit().performClick()
        assertEquals(draft, accepted.single().text)
        assertEquals(draft, field().text.toString())
        assertTrue(current().isShowing)
        assertFalse(submit().isEnabled)
        assertTrue(labels().any { it.contains("Annahme des Auftrags konnte nicht bestätigt") })
        field().append(" Offline bleiben.")
        choose(ChiefBotTaskMode.PRIVATE_REPLY)
        assertFalse(submit().isEnabled)
        submit().performClick()
        assertEquals(1, accepted.size)
    }

    @Test fun overLimitUnicodeAndCodingTextStayWholeWithoutDispatch() {
        var calls = 0
        openComposer { calls++; true }
        val unicode = "😀".repeat(1_001) + " Nichts löschen."
        field().setText(unicode)
        choose(ChiefBotTaskMode.PRIVATE_REPLY)
        assertEquals(unicode, field().text.toString())
        assertFalse(submit().isEnabled)
        submit().performClick()
        assertEquals(0, calls)
        val coding = "x".repeat(601)
        field().setText(coding)
        choose(ChiefBotTaskMode.CODING_DRAFT)
        assertEquals(coding, field().text.toString())
        assertFalse(submit().isEnabled)
        submit().performClick()
        assertEquals(0, calls)
        field().setText("x".repeat(600))
        assertTrue(submit().isEnabled)
        submit().performClick()
        assertEquals(1, calls)
    }

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderActualPhoneRosterAndComposer() = renderWidgets("phone-412x915", 1f)

    @Test @Config(qualifiers = "w840dp-h649dp-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderActualFoldRosterAndComposer() = renderWidgets("fold-840x649", 1f)

    @Test @Config(qualifiers = "w320dp-h720dp-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderActualNarrowWidgetsWithLargeType() = renderWidgets("narrow-320x720-font200", 2f)

    @Suppress("DEPRECATION")
    private fun renderWidgets(name: String, fontScale: Float) {
        val configuration = android.content.res.Configuration(activity.resources.configuration).apply { this.fontScale = fontScale }
        activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
        roster = ContinuumBotRosterDialog(activity, store, io, onSelect = {},
            runtimeStatus = { "Lokales Modell nicht geladen · Kein Auftrag aktiv" }, onAssign = { false }).also { it.show() }
        io.drain()
        capture("$name-roster")
        views().filterIsInstance<Button>().first { it.text.toString() == "Auftrag geben" }.performClick()
        field().setText("Ordne meine nächsten Schritte. Bleibe offline und ändere keine Dateien.")
        choose(ChiefBotTaskMode.PRIVATE_REPLY)
        capture("$name-composer")
        val finalButton = submit()
        assertTrue("The final handoff remains reachable at font scale $fontScale", finalButton.isShown)
        assertTrue(finalButton.height >= (48 * activity.resources.displayMetrics.density).toInt())
        val decor = current().window!!.decorView
        val buttonPosition = IntArray(2)
        finalButton.getLocationInWindow(buttonPosition)
        assertTrue("Final handoff fits inside the window", buttonPosition[1] + finalButton.height <= decor.height)
        val scroller = generateSequence(field().parent) { it.parent }
            .filterIsInstance<ScrollView>().first()
        scroller.isSmoothScrollingEnabled = false
        scroller.fullScroll(View.FOCUS_DOWN)
        capture("$name-composer-details")
        if (fontScale == 2f) {
            // Real widget layout at a keyboard-sized reduced window; this is not a physical IME claim.
            current().window!!.setLayout(current().window!!.attributes.width,
                (360 * activity.resources.displayMetrics.density).toInt())
            capture("$name-composer-height360")
            assertTrue("Reduced-height dialog keeps usable editing/scrolling room", scroller.height >= 96)
            finalButton.getLocationInWindow(buttonPosition)
            assertTrue("Final handoff remains in the reduced window", buttonPosition[1] + finalButton.height <= current().window!!.decorView.height)
        }
    }

    private fun capture(name: String) {
        val window = current().window!!
        val decor = window.decorView
        val width = window.attributes.width
        val height = window.attributes.height
        assertTrue(width > 0 && height > 0)
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, width, height)
        // Capture the selected state, not the first frame of Android's radio/ripple animation.
        views(decor).forEach { it.jumpDrawablesToCurrentState() }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            decor.draw(Canvas(bitmap))
            System.getProperty("continuum.ui.evidenceDir")?.let { directory ->
                val target = File(directory).apply { mkdirs() }
                File(target, "bots-$name.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            views().filterIsInstance<Button>().filter { it.visibility == View.VISIBLE }.forEach {
                assertTrue("Button has a usable measured width: ${it.text}", it.measuredWidth > 0)
                assertTrue("Button wraps rather than truncating: ${it.text}", it.layout?.let { layout ->
                    (0 until layout.lineCount).none { line -> layout.getEllipsisCount(line) > 0 }
                } ?: false)
            }
        } finally { bitmap.recycle() }
    }
}
