package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import at.heinz.deviceai.cognitive.CockpitWorkJournal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

// Controlled picker; real journal writes.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumDraftSaveBindingUiTest {
    @Before fun pauseVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun read(activity: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun currentEpisode(activity: AgentActivity, id: String?) {
        AgentActivity::class.java.getDeclaredField("workEpisodeId").apply { isAccessible = true }.set(activity, id)
    }

    private fun withActivity(block: (AgentActivity, () -> Unit) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            block(controller.get()) { controller.pause().resume() }
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.close() }
        }
    }

    private fun draft(journal: CockpitWorkJournal, name: String): CockpitWorkJournal.Episode {
        val episode = journal.begin(CockpitWorkJournal.Kind.DOCUMENT, "Entwurf $name",
            CockpitWorkJournal.State.DRAFTED, "$name.pdf")
        return requireNotNull(journal.update(episode.id) { it.copy(draft = "Unverändert $name") })
    }

    private fun beginSave(activity: AgentActivity, episode: String?, text: String): Intent {
        currentEpisode(activity, episode)
        AgentActivity::class.java.getDeclaredMethod("saveDraft", String::class.java, String::class.java)
            .apply { isAccessible = true }.invoke(activity, text, "A.pdf")
        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull(started)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.intent.action)
        assertEquals("text/markdown", started.intent.type)
        return started.intent
    }

    private fun finishSave(activity: AgentActivity, picker: Intent, target: Uri): ByteArrayOutputStream {
        val output = ByteArrayOutputStream()
        shadowOf(activity.contentResolver).registerOutputStream(target, output)
        shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, Intent().setData(target))
        return output
    }

    private fun drain(activity: AgentActivity) {
        val lane = read(activity, "documentIoExecutor") as ExecutorService
        lane.submit {}.get(10, TimeUnit.SECONDS)
        drainMain()
        lane.submit {}.get(10, TimeUnit.SECONDS)
        drainMain()
    }

    private fun drainMain() {
        val reached = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { reached.countDown() }
        repeat(64) {
            if (reached.count == 0L) return
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
        }
        assertEquals("UI callback barrier reached", 0L, reached.count)
    }

    private fun assertSaved(journal: CockpitWorkJournal, first: CockpitWorkJournal.Episode,
                            second: CockpitWorkJournal.Episode, text: String, target: Uri) {
        val actual = journal.all().associateBy { it.id }
        assertEquals(CockpitWorkJournal.State.SAVED, actual[first.id]?.state)
        assertEquals(text, actual[first.id]?.draft)
        assertEquals(target.toString(), actual[first.id]?.draftUri)
        assertEquals(second, actual[second.id])
    }

    @Test fun pickerResultKeepsTheEpisodeSelectedBeforeThePicker() = withActivity { activity, pauseAndResume ->
        val journal = CockpitWorkJournal(activity.filesDir)
        val first = draft(journal, "A")
        val second = draft(journal, "B")
        val text = "Bearbeiteter Testentwurf A"
        val picker = beginSave(activity, first.id, text)
        pauseAndResume()
        currentEpisode(activity, second.id)
        val target = Uri.parse("content://test.documents/original-draft.md")
        val output = finishSave(activity, picker, target)
        drain(activity)
        assertEquals(text, output.toString(Charsets.UTF_8.name()))
        assertSaved(journal, first, second, text, target)
        assertNull(read(activity, "pendingDraftSave"))
    }

    @Test fun aDelayedWriteCannotMarkANewerEpisodeSaved() = withActivity { activity, _ ->
        val journal = CockpitWorkJournal(activity.filesDir)
        val first = draft(journal, "A")
        val second = draft(journal, "B")
        val text = "Verzögert geschriebener Testentwurf A"
        val picker = beginSave(activity, first.id, text)
        val lane = read(activity, "documentIoExecutor") as ExecutorService
        val gate = CountDownLatch(1)
        lane.execute { gate.await(10, TimeUnit.SECONDS) }
        val target = Uri.parse("content://test.documents/delayed-draft.md")
        val output: ByteArrayOutputStream
        try {
            output = finishSave(activity, picker, target)
            currentEpisode(activity, second.id)
        } finally { gate.countDown() }
        drain(activity)
        assertEquals(text, output.toString(Charsets.UTF_8.name()))
        assertSaved(journal, first, second, text, target)
    }

    @Test fun anUnboundDraftCannotAdoptALaterEpisode() = withActivity { activity, _ ->
        val journal = CockpitWorkJournal(activity.filesDir)
        val later = draft(journal, "B")
        val text = "Testentwurf ohne Journaleintrag"
        val picker = beginSave(activity, null, text)
        currentEpisode(activity, later.id)
        val target = Uri.parse("content://test.documents/unbound-draft.md")
        val output = finishSave(activity, picker, target)
        drain(activity)
        assertEquals(text, output.toString(Charsets.UTF_8.name()))
        assertEquals(later, journal.all().single { it.id == later.id })
    }

    @Test fun cancellingThePickerPreservesBothJournalEntries() = withActivity { activity, _ ->
        val journal = CockpitWorkJournal(activity.filesDir)
        val first = draft(journal, "A")
        val second = draft(journal, "B")
        val picker = beginSave(activity, first.id, "Nicht speichern")
        currentEpisode(activity, second.id)
        shadowOf(activity).receiveResult(picker, Activity.RESULT_CANCELED, null)
        drain(activity)
        val actual = journal.all().associateBy { it.id }
        assertEquals(first, actual[first.id])
        assertEquals(second, actual[second.id])
        assertNull(read(activity, "pendingDraftSave"))
    }
}
