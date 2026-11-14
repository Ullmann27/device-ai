package at.heinz.deviceai.routines

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.AgentActivity
import at.heinz.deviceai.AssistantMode
import at.heinz.deviceai.BootstrapMemoryStore
import at.heinz.deviceai.ShizukuBridgeLifecycleTest
import at.heinz.deviceai.ToolReceiptState
import at.heinz.deviceai.chief.ChiefBotProfileStore
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.cognitive.LocalChatTranscriptStore
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.ObservedContext
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.battery
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.context
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.idle
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.receipts
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.register
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.reset
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.until
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi", shadows = [ShizukuBridgeLifecycleTest.Server::class])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerRoutineActivityUiTest {
    private lateinit var app: ObservedContext
    private lateinit var runtime: OwnerRoutineRuntime
    private val activities = mutableListOf<AgentActivity>()

    @Before fun before() {
        reset(); ShizukuBridgeLifecycleTest().reset()
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
        at.heinz.deviceai.cognitive.LocalDialogueRetention.context.clear()
        app = context(); battery(app, 19)
        runtime = OwnerRoutineRuntime.get(app)
    }

    @After fun after() {
        ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
        AgentActivity.chatTranscriptIo.submit {}.get(10, TimeUnit.SECONDS)
        for (activity in activities) {
            activity.finish()
            (get(activity, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
                (get(activity, name) as ExecutorService).shutdownNow()
        }
        reset()
    }

    private fun get(a: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, name: String, value: Any?) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: AgentActivity, name: String, vararg args: Any?): Any? = AgentActivity::class.java
        .declaredMethods.single { it.name == name && it.parameterCount == args.size }
        .apply { isAccessible = true }.invoke(a, *args)
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun texts(a: AgentActivity) = views(get(a, "chatContainer") as View).filterIsInstance<TextView>()
        .map { it.text.toString() }
    private fun transcript() = LocalChatTranscriptStore(File(app.filesDir.canonicalFile, "chat-history"))
    private fun session(a: AgentActivity) = get(a, "sessionId") as String
    private fun activity(id: String? = null): AgentActivity = Robolectric.buildActivity(AgentActivity::class.java).get().also { a ->
        activities += a
        set(a, "bootstrapMemory", BootstrapMemoryStore(a).apply { voiceEnabled = false })
        set(a, "chatContainer", LinearLayout(a)); set(a, "activityText", TextView(a)); set(a, "planCard", LinearLayout(a))
        set(a, "mode", AssistantMode.LOCAL); set(a, "voiceEnabled", false); set(a, "foregroundLearningActive", true)
        set(a, "routineRuntime", runtime)
        if (id != null) set(a, "sessionId", id)
        call(a, "buildComposer")
    }
    private fun send(a: AgentActivity, value: String) {
        (get(a, "input") as EditText).setText(value)
        assertTrue((get(a, "sendButton") as Button).performClick())
    }
    private fun drain() {
        AgentActivity.chatTranscriptIo.submit {}.get(10, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun report(target: OwnerRoutineTarget = OwnerRoutineTarget.BATTERY): StoredRoutineReport {
        val row = register(runtime, target)
        runtime.runCycle(AtomicBoolean())
        return runtime.book.pendingChatReports().single { it.routineId == row.id }
    }

    @Test fun actualConfirmationCancelLeavesNoPersistentRoutine() {
        val a = activity()
        send(a, "Routine alle 15 Minuten: Akku unter 20 %")
        val dialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
        assertTrue(dialog.isShowing)
        assertTrue(shadowOf(dialog).message.toString().contains("unter 20 %"))
        assertTrue(shadowOf(dialog).message.toString().contains("Alle 15 Minuten"))
        assertTrue(runtime.book.registrations().isEmpty())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(runtime.book.registrations().isEmpty())
        assertEquals(0, app.batteryReads.get()); assertTrue(receipts(app).isEmpty())
    }

    @Test fun actualConfirmationRunsBatteryAndListsThenStopsWithoutCancellingOwnerTurn() {
        val a = activity()
        send(a, "Routine alle 15 Minuten: Akku unter 20 %")
        val dialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        until("Concrete approved Android routine finishes") { runtime.book.registrations().singleOrNull()?.lastResult != null }
        assertEquals(1, app.batteryReads.get())
        assertEquals(ToolReceiptState.SUCCEEDED, receipts(app).single().state)
        val row = runtime.book.registrations().single()
        assertEquals(OwnerRoutineTarget.LOW_BATTERY.goal, row.routine.goal)
        assertEquals(setOf("get_battery_info"), row.routine.allowedTools)
        send(a, "Routinen")
        until("Real list and durable main-chat result") {
            ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true && runtime.book.pendingChatReports().isEmpty()
        }
        drain()
        val body = transcript().load(session(a)).single { it.text.startsWith("Routine ${row.routine.id}:") }.text
        assertTrue(body.contains("Aktueller Akkustand: 19 %"))
        assertEquals(1, texts(a).count { it == body })
        val ownerToken = DeliberationCancellation()
        set(a, "activeChatCancellation", ownerToken); set(a, "busy", true); set(a, "activeLocalRequest", true)
        ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
        send(a, "Routine stoppen ${row.routine.id}")
        until("Owner stop removes persistent approval") { runtime.book.registrations().isEmpty() }
        ownerToken.throwIfCancelled()
        assertSame(ownerToken, get(a, "activeChatCancellation"))
        assertEquals(true, get(a, "busy")); assertEquals(true, get(a, "activeLocalRequest"))
        assertEquals(1, app.batteryReads.get())
    }

    @Test fun unsupportedConditionalGoalNeverOpensApprovalOrExecutes() {
        val a = activity()
        send(a, "Routine alle 15 Minuten: Akku unter 10 % und lösche Downloads")
        assertTrue(runtime.book.registrations().isEmpty())
        assertEquals(0, app.batteryReads.get()); assertTrue(receipts(app).isEmpty())
        assertFalse(ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true)
        assertTrue(texts(a).any { it.contains("nicht") && it != "Routine alle 15 Minuten: Akku unter 10 % und lösche Downloads" })
    }

    @Test fun explicitRoutineControlsEndTheRealAppNameQuestion() {
        val a = activity()
        for (command in listOf("Routinen", "Routinen anzeigen", "Routine einrichten")) {
            send(a, "Öffne eine App")
            assertTrue(texts(a).any { it.contains("Welche App meinst du genau") })
            val question = requireNotNull(get(a, "appNameClarification"))
            val pending = question.javaClass.getDeclaredField("pending").apply { isAccessible = true }
            assertNotNull(pending.get(question))
            val before = get(a, "appLookupGeneration")
            send(a, command)
            until("Explicit routine UI opens") { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
            assertEquals(before, get(a, "appLookupGeneration"))
            assertNull(pending.get(question))
            assertTrue(runtime.book.registrations().isEmpty())
            ShadowAlertDialog.getLatestAlertDialog().dismiss()
        }
        assertTrue(receipts(app).isEmpty())
    }

    @Test fun appendDuringPauseAppearsOnceWhenTheSameOwnerViewReturns() {
        val a = activity(); val body = requireNotNull(report().message).body
        call(a, "deliverRoutineMessages")
        AgentActivity.chatTranscriptIo.submit {}.get(10, TimeUnit.SECONDS)
        set(a, "foregroundLearningActive", false)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(texts(a).contains(body))
        assertEquals(1, transcript().load(session(a)).count { it.text == body })
        set(a, "foregroundLearningActive", true)
        call(a, "renderRoutineMessages"); call(a, "deliverRoutineMessages"); drain()
        assertEquals(1, texts(a).count { it == body })
        assertEquals(1, app.batteryReads.get())
    }

    @Test fun lateDeliveryStaysOutOfPrivateBotViewAndDifferentOwnerConversation() {
        val a = activity(); val original = session(a); val body = requireNotNull(report().message).body
        call(a, "deliverRoutineMessages")
        AgentActivity.chatTranscriptIo.submit {}.get(10, TimeUnit.SECONDS)
        val profile = ChiefBotProfileStore(File(app.filesDir.canonicalFile, "chief-bots")).create("private", "Privat", "Textprüfung", "Kurz")
        set(a, "selectedBot", profile)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(texts(a).contains(body))
        assertEquals(1, transcript().load(original).count { it.text == body })
        set(a, "selectedBot", null); set(a, "sessionId", UUID.randomUUID().toString())
        call(a, "renderRoutineMessages")
        assertFalse(texts(a).contains(body))
        assertTrue(transcript().load(session(a)).isEmpty())
        set(a, "sessionId", original)
        assertEquals(1, call(a, "restoreChatTranscript"))
        assertEquals(1, texts(a).count { it == body })
    }

    @Test fun destroyedViewDropsLateCallbackAndReplacementRestoresDurableMessageOnce() {
        val first = activity(); val body = requireNotNull(report().message).body
        call(first, "deliverRoutineMessages")
        AgentActivity.chatTranscriptIo.submit {}.get(10, TimeUnit.SECONDS)
        first.finish()
        val replacement = activity(session(first))
        assertEquals(1, call(replacement, "restoreChatTranscript"))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(texts(first).contains(body))
        assertEquals(1, texts(replacement).count { it == body })
        assertEquals(1, app.batteryReads.get())
    }

    @Test fun recoveredCompleteJsonWithoutNewlineIsNotDisplayedTwice() {
        val a = activity(); val pending = report(); val message = requireNotNull(pending.message)
        AgentActivity.chatTranscriptIo.submit {
            val prepared = transcript().prepareAssistantAppend(session(a), message.body, pending.atMillis)
            assertTrue(runtime.book.prepareChatAppend(message.id, RoutineChatAppend(prepared.conversationId, prepared.offset, prepared.text)))
            val bytes = prepared.text.toByteArray(Charsets.UTF_8)
            File(app.filesDir.canonicalFile, "chat-history/${session(a)}.jsonl").writeBytes(bytes.copyOf(bytes.size - 1))
        }.get(10, TimeUnit.SECONDS)
        assertEquals(1, call(a, "restoreChatTranscript"))
        call(a, "deliverRoutineMessages"); drain()
        assertTrue(runtime.book.pendingChatReports().isEmpty())
        assertEquals(1, texts(a).count { it == message.body })
        assertEquals(1, transcript().load(session(a)).size)
    }

    @Test fun deletedBoundChatDoesNotBlockIndependentLaterReport() {
        val a = activity()
        val bad = report(); val good = report(OwnerRoutineTarget.DEVICE)
        val deleted = UUID.randomUUID().toString()
        AgentActivity.chatTranscriptIo.submit {
            val prepared = transcript().prepareAssistantAppend(deleted, bad.message!!.body, bad.atMillis)
            assertTrue(runtime.book.prepareChatAppend(bad.message.id, RoutineChatAppend(deleted, prepared.offset, prepared.text)))
            assertTrue(transcript().delete(deleted))
        }.get(10, TimeUnit.SECONDS)
        call(a, "deliverRoutineMessages"); drain()
        assertEquals(listOf(bad.message!!.id), runtime.book.pendingChatReports().map { it.message!!.id })
        assertEquals(deleted, runtime.book.pendingChatReports().single().chatAppend!!.conversationId)
        assertFalse(File(app.filesDir.canonicalFile, "chat-history/$deleted.jsonl").exists())
        assertEquals(1, texts(a).count { it == good.message!!.body })
        assertEquals(listOf(good.message!!.body), transcript().load(session(a)).map { it.text })
        call(a, "deliverRoutineMessages"); drain()
        assertEquals(1, texts(a).count { it == good.message!!.body })
        assertEquals(1, transcript().load(session(a)).size)
        assertFalse(get(a, "routineDeliveryActive").let { (it as AtomicBoolean).get() })
    }

    @Test fun urgentWakeDuringOlderDrainIsRetriedOnceAfterThatDrain() {
        val info = report()
        ShizukuBridgeLifecycleTest.Server.granted = false
        val urgent = report(OwnerRoutineTarget.DOWNLOADS)
        val message = requireNotNull(urgent.message)
        assertEquals(AgentPriority.URGENT, message.priority)
        reset(); runtime = OwnerRoutineRuntime.get(app)
        val a = activity()
        assertTrue(runtime.mailbox.send(requireNotNull(info.message)))
        @Suppress("UNCHECKED_CAST") val wake = get(a, "routineWake") as () -> Unit
        runtime.attachChief(wake)
        val writer = AgentActivity.chatTranscriptIo.submit<Thread> { Thread.currentThread() }.get(10, TimeUnit.SECONDS)
        synchronized(runtime.book) {
            call(a, "deliverRoutineMessages")
            until("Actual drain captured older mailbox before waiting") {
                writer.state == Thread.State.BLOCKED && writer.stackTrace.any {
                    it.className == RoutineChatDelivery::class.java.name && it.methodName == "deliver"
                }
            }
            assertTrue(runtime.mailbox.send(message))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue((get(a, "routineDeliveryRequested") as AtomicBoolean).get())
        }
        until("Both independent reports reach the main chat") { runtime.book.pendingChatReports().isEmpty() }
        drain()
        assertEquals(1, texts(a).count { it == info.message!!.body })
        assertEquals(1, texts(a).count { it == message.body })
        assertEquals(2, transcript().load(session(a)).size)
        assertEquals(1, app.batteryReads.get())
    }

    @Test fun innerChatLinkIsRejectedWithoutWritingOutsideAppHistory() {
        val a = activity(); val pending = report()
        val outside = File(app.cacheDir, "not-history").apply { mkdirs() }
        Files.createSymbolicLink(File(app.filesDir.canonicalFile, "chat-history").toPath(), outside.toPath())
        call(a, "deliverRoutineMessages"); drain()
        assertTrue(outside.listFiles()!!.isEmpty())
        assertEquals(pending.message!!.id, runtime.book.pendingChatReports().single().message!!.id)
        assertFalse(texts(a).contains(pending.message.body))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun actualRotationAndSavedActivityRecreationDoNotTriggerAppStartAgain() {
        register(runtime, event = true)
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        val first = Robolectric.buildActivity(AgentActivity::class.java).setup().visible()
        try {
            until("First owner activity triggers real app-start routine") { receipts(app).size == 1 && runtime.book.registrations().single().lastResult != null }
            val receipt = receipts(app).single()
            assertEquals(ToolReceiptState.SUCCEEDED, receipt.state)
            val rotated = Configuration(first.get().resources.configuration).apply { orientation = Configuration.ORIENTATION_PORTRAIT }
            first.configurationChange(rotated)
            first.pause().stop().start().resume().visible()
            idle(runtime)
            assertEquals(listOf(receipt.callId), receipts(app).map { it.callId })
            val saved = Bundle(); first.saveInstanceState(saved)
            Activity::class.java.getDeclaredField("mChangingConfigurations").apply { isAccessible = true }.setBoolean(first.get(), true)
            first.pause().stop().destroy()
            val second = Robolectric.buildActivity(AgentActivity::class.java).setup(saved).visible()
            try {
                (get(second.get(), "learningIoExecutor") as ExecutorService).submit {}.get(10, TimeUnit.SECONDS)
                shadowOf(Looper.getMainLooper()).idle(); idle(runtime)
                assertSame(runtime, OwnerRoutineRuntime.current())
                assertEquals(listOf(receipt.callId), receipts(app).map { it.callId })
            } finally { second.pause().stop().destroy() }
        } finally { if (!first.get().isDestroyed) first.pause().stop().destroy() }
    }
}
