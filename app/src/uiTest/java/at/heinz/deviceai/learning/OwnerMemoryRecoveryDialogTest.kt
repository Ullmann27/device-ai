package at.heinz.deviceai.learning

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.TextView
import at.heinz.deviceai.R
import at.heinz.deviceai.ui.OwnerMemoryRecoveryDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OwnerMemoryRecoveryDialogTest {
    private class QueuedExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        val pending: Int get() = tasks.size
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runNext() {
            check(tasks.isNotEmpty())
            tasks.removeFirst().run()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private class Fixture(
        val activity: Activity,
        val core: ContinuumLearningCore,
        val marker: File,
        val executor: QueuedExecutor
    ) {
        var handle: AutoCloseable? = null
        fun open(): AlertDialog {
            handle = OwnerMemoryRecoveryDialog.show(activity, executor, core)
            assertEquals("Loading is scheduled on the supplied executor", 1, executor.pending)
            executor.runNext()
            return current()
        }
    }

    private fun fixture(block: (Fixture) -> Unit) {
        val directory = Files.createTempDirectory("owner-dialog-").toFile()
        val original = ContinuumLearningCore(directory)
        original.confirmOwnerFact("Ich wohne in Steyr.")
        original.confirmOwnerFact("Ich trinke gerne Tee.")
        original.confirmOwnerFact("Mein Hund heißt Bruno.")
        val primary = File(directory, "learning-core-v2/experiences-v2.bin")
        primary.writeBytes(primary.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        val core = ContinuumLearningCore.withAutomaticRecovery(directory)
        assertTrue(core.activeOwnerFacts().isEmpty())
        assertEquals(2, core.recoveredOwnerFactsForReview().facts.size)
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        val activity = controller.setup().get()
        val f = Fixture(activity, core,
            File(directory, "learning-core-v2/${OwnerMemoryRecovery.MARKER_NAME}"), QueuedExecutor())
        try {
            block(f)
        } finally {
            OwnerMemoryRecovery.beforeWriteForTest = null
            f.handle?.close()
            shadowOf(Looper.getMainLooper()).idle()
            controller.pause().stop().destroy()
            directory.deleteRecursively()
        }
    }

    @Test fun reviewShowsTheExactUnselectedFactsAndCancelNeverChangesConsent() = fixture { f ->
        val before = f.marker.readBytes()
        val dialog = f.open()
        val list = list(dialog.window!!.decorView)!!
        assertEquals(0, list.checkedItemCount)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        val displayed = list.adapter.getItem(0) as RecoveredOwnerFact
        val row = list.adapter.getView(0, null, list) as CheckedTextView
        assertEquals(displayed.content, row.text.toString())
        assertNull(row.ellipsize)
        choose(list, 0)
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(0, f.executor.pending)
        assertArrayEquals(before, f.marker.readBytes())
        assertTrue(f.core.activeOwnerFacts().isEmpty())
    }

    @Test fun onlyThePositiveButtonSchedulesTheExactSelectedConfirmation() = fixture { f ->
        val before = f.marker.readBytes()
        val dialog = f.open()
        val list = list(dialog.window!!.decorView)!!
        val displayed = list.adapter.getItem(0) as RecoveredOwnerFact
        choose(list, 0)
        assertEquals(0, f.executor.pending)
        assertArrayEquals(before, f.marker.readBytes())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(1, f.executor.pending)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled)
        assertTrue(f.core.activeOwnerFacts().isEmpty())
        f.executor.runNext()
        assertEquals(listOf(displayed.id), f.core.activeOwnerFacts().map { it.id })
        assertEquals(1, f.core.recoveredOwnerFactsForReview().facts.size)
        assertTrue(text(current().window!!.decorView).contains("Eine ausgewählte Angabe ist jetzt wieder aktiv."))
    }

    @Test fun aChangedReviewCannotActivateItsOldSelectionOrDisplaySuccess() = fixture { f ->
        val dialog = f.open()
        val list = list(dialog.window!!.decorView)!!
        val displayed = list.adapter.getItem(0) as RecoveredOwnerFact
        choose(list, 0)
        f.core.confirmOwnerFact("Meine Lieblingsfarbe ist Blau.")
        val before = f.marker.readBytes()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        f.executor.runNext()
        assertFalse(f.core.activeOwnerFacts().any { it.id == displayed.id })
        assertArrayEquals(before, f.marker.readBytes())
        val message = text(current().window!!.decorView)
        assertTrue(message.contains("nicht zuverlässig bestätigt"))
        assertFalse(message.contains("ist jetzt wieder aktiv"))
    }

    @Test fun failedDurableConfirmationNeverDisplaysSuccess() = fixture { f ->
        val dialog = f.open()
        choose(list(dialog.window!!.decorView)!!, 0)
        val before = f.marker.readBytes()
        OwnerMemoryRecovery.beforeWriteForTest = {
            if (it.name == OwnerMemoryRecovery.MARKER_NAME) throw IOException("synthetic dialog write failure")
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        f.executor.runNext()
        assertArrayEquals(before, f.marker.readBytes())
        assertTrue(f.core.activeOwnerFacts().isEmpty())
        val message = text(current().window!!.decorView)
        assertTrue(message.contains("nicht zuverlässig bestätigt"))
        assertFalse(message.contains("ist jetzt wieder aktiv"))
    }

    @Test fun closingDuringLoadSuppressesLateDialogsAndCreatesNoConsent() = fixture { f ->
        val before = f.marker.readBytes()
        f.handle = OwnerMemoryRecoveryDialog.show(f.activity, f.executor, f.core)
        val loading = current()
        f.handle!!.close()
        f.executor.runNext()
        assertSame(loading, current())
        assertFalse(loading.isShowing)
        assertArrayEquals(before, f.marker.readBytes())
        assertTrue(f.core.activeOwnerFacts().isEmpty())
    }

    @Test fun finishingActivityDoesNotReceiveLateReviewResults() = fixture { f ->
        val before = f.marker.readBytes()
        f.handle = OwnerMemoryRecoveryDialog.show(f.activity, f.executor, f.core)
        val loading = current()
        f.activity.finish()
        f.executor.runNext()
        assertSame(loading, current())
        assertFalse(loading.isShowing)
        assertArrayEquals(before, f.marker.readBytes())
    }

    companion object {
        private fun current(): AlertDialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
        private fun choose(list: ListView, index: Int) {
            list.performItemClick(list.adapter.getView(index, null, list), index, index.toLong())
            assertTrue(list.isItemChecked(index))
        }
        private fun list(view: View): ListView? {
            if (view is ListView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) {
                list(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        private fun text(view: View): String = when (view) {
            is TextView -> view.text.toString()
            is ViewGroup -> (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) }
            else -> ""
        }
    }
}
