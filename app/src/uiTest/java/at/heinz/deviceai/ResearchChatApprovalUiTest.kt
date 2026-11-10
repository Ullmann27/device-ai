package at.heinz.deviceai

import android.app.AlertDialog
import android.Manifest
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.EditText
import at.heinz.deviceai.cognitive.ApprovedWebResearchSession
import at.heinz.deviceai.cognitive.ApprovedWebToolGate
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.InternetConsentPolicy
import at.heinz.deviceai.cognitive.LocalChatTranscriptStore
import at.heinz.deviceai.cognitive.ResearchMode
import at.heinz.deviceai.cognitive.WebToolRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSpeechRecognizer
import org.robolectric.shadows.ShadowPowerManager
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Actual dialogs/send; no network execution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class ResearchChatApprovalUiTest {
    @Implements(PowerManager::class)
    class ConsentPowerManager : ShadowPowerManager() {
        @Implementation(minSdk = 29)
        protected fun addThermalStatusListener(executor: Executor,
            listener: PowerManager.OnThermalStatusChangedListener) {
            super.addThermalStatusListener(listener)
        }
    }

    private class Fixture(val activity: AgentActivity, val pauseAndResume: () -> Unit) {
        var now = 0L
        val consent = InternetConsentPolicy(InternetAccessMode.ASK) { now }
        val gate = ApprovedWebToolGate(consent)
        val completed = mutableListOf<ApprovedWebResearchSession?>()
        var invalidated = 0
        lateinit var whilePaused: (() -> Unit) -> Unit
    }

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)

    private fun set(target: Any, name: String, value: Any?) = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(target, value)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun withActivity(check: (Fixture) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit()
            .putString("mode", "ASK").commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", false).putBoolean("native-head-3d", false).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            val fixture = Fixture(controller.get()) { controller.pause().resume() }
            fixture.whilePaused = { action ->
                controller.pause()
                try { action() } finally { controller.resume() }
            }
            set(fixture.activity, "internetConsent", fixture.consent)
            set(fixture.activity, "webToolGate", fixture.gate)
            idle()
            check(fixture)
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            controller.pause().stop().destroy()
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.register(null)
        }
    }

    private fun ask(f: Fixture, query: String, inChat: Boolean = true): AlertDialog {
        val completed: (ApprovedWebResearchSession?) -> Unit = { f.completed += it }
        val invalidated: () -> Unit = { f.invalidated++ }
        AgentActivity::class.java.declaredMethods.single {
            it.name == "confirmWebRequest" && it.parameterCount == 4
        }.apply { isAccessible = true }.invoke(f.activity, WebToolRequest.Search(query),
            ResearchMode.QUICK, invalidated, completed)
        idle()
        return requireNotNull(ShadowAlertDialog.getLatestAlertDialog()).also {
            assertEquals("Im Chat antworten", it.getButton(AlertDialog.BUTTON_NEUTRAL).text.toString())
            if (inChat) {
                it.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
                idle()
            }
        }
    }

    private fun reply(f: Fixture, text: String = "Ja, mach.") {
        (field(f.activity, "input") as EditText).setText(text)
        assertEquals("Recherchefreigabe senden", (field(f.activity, "sendButton") as Button).contentDescription)
        assertTrue((field(f.activity, "sendButton") as Button).performClick())
        idle()
    }

    private fun startRecognizer(f: Fixture, handsFree: Boolean = true): SpeechRecognizer {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        AgentActivity::class.java.getDeclaredMethod(
            if (handsFree) "onChatMicrophoneTap" else "onChatMicrophoneLongPress")
            .apply { isAccessible = true }.invoke(f.activity)
        idle()
        val recognizer = requireNotNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        assertEquals(true, field(f.activity, "voiceListening"))
        return recognizer
    }

    private fun deliverResult(recognizer: SpeechRecognizer, text: String = "Ja, mach.") {
        shadowOf(recognizer).triggerOnResults(Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
        })
        idle()
    }

    private fun recognize(f: Fixture, beforeResult: () -> Unit = {}) {
        val recognizer = startRecognizer(f)
        beforeResult()
        deliverResult(recognizer)
    }

    @Test @Config(sdk = [35], shadows = [ConsentPowerManager::class])
    fun recognizerResultCannotApproveAReplacementTicket() {
        withActivity { f ->
            ask(f, "Unveränderter Auftrag")
            recognize(f)
            assertEquals(WebToolRequest.Search("Unveränderter Auftrag"), f.completed.single()!!.call)
        }
        withActivity { f ->
            ask(f, "Alter Auftrag")
            recognize(f) { ask(f, "Neuer Auftrag") }
            assertTrue("Old recognition must not approve replacement", f.completed.isEmpty())
            assertTrue((field(f.activity, "input") as EditText).text.isBlank())
            recognize(f)
            assertEquals(WebToolRequest.Search("Neuer Auftrag"), f.completed.single()!!.call)
        }
    }

    @Test @Config(sdk = [35], shadows = [ConsentPowerManager::class])
    fun staleActivitySpeechCallbackPreservesNewRecognition() = withActivity { f ->
        ask(f, "Alter Auftrag")
        startRecognizer(f)
        val speech = requireNotNull(field(f.activity, "onDeviceSpeech"))
        val session = requireNotNull(field(speech, "session"))
        val active = requireNotNull(field(session, "active"))
        @Suppress("UNCHECKED_CAST")
        val delayedCallback = field(active, "onText") as (String) -> Unit
        ask(f, "Neuer Auftrag")
        val currentRecognizer = startRecognizer(f)
        delayedCallback("Ja, mach.")
        idle()
        assertTrue(f.completed.isEmpty())
        assertEquals(true, field(f.activity, "voiceListening"))
        deliverResult(currentRecognizer)
        assertEquals(WebToolRequest.Search("Neuer Auftrag"), f.completed.single()!!.call)
    }

    @Test @Config(sdk = [35], shadows = [ConsentPowerManager::class])
    fun resolvedTicketDoesNotDiscardCurrentDictation() {
        for (accepted in listOf(true, false)) withActivity { f ->
            ask(f, "Erledigter Diktatauftrag")
            val recognizer = startRecognizer(f, handsFree = false)
            reply(f, if (accepted) "Ja, mach." else "Nein.")
            assertNull(field(f.activity, "pendingResearchChatApproval"))
            val text = "Notiz zum nächsten Termin"
            deliverResult(recognizer, text)
            assertEquals(text, (field(f.activity, "input") as EditText).text.toString())
            assertEquals(1, f.completed.size)
            assertEquals(accepted, f.completed.single() != null)
        }
    }

    @Test @Config(sdk = [35], shadows = [ConsentPowerManager::class])
    fun resolvedTicketDoesNotEndCurrentHandsFreeSpeech() {
        for (accepted in listOf(true, false)) withActivity { f ->
            ask(f, "Erledigter Gesprächsauftrag")
            val recognizer = startRecognizer(f)
            reply(f, if (accepted) "Ja, mach." else "Nein.")
            assertNull(field(f.activity, "pendingResearchChatApproval"))
            val text = "Was hast du dadurch verstanden?"
            deliverResult(recognizer, text)
            assertTrue(transcript(f).any { it.user && it.text == text })
            val conversation = field(f.activity, "continuousConversation") as
                at.heinz.deviceai.cognitive.ContinuousConversationMode
            assertTrue("Resolved consent must not stop the current conversation", conversation.active)
            assertEquals(1, f.completed.size)
            assertEquals(accepted, f.completed.single() != null)
        }
    }

    @Test @Config(sdk = [35], shadows = [ConsentPowerManager::class])
    fun speechStartedWithoutATicketCannotApproveANewTicket() = withActivity { f ->
        val recognizer = startRecognizer(f)
        ask(f, "Späterer Auftrag")
        deliverResult(recognizer)
        assertTrue(f.completed.isEmpty())
        assertNotNull(field(f.activity, "pendingResearchChatApproval"))
        recognize(f)
        assertEquals(WebToolRequest.Search("Späterer Auftrag"), f.completed.single()!!.call)
    }

    private fun transcript(f: Fixture): List<LocalChatTranscriptStore.Entry> {
        val id = field(f.activity, "sessionId") as String
        return AgentActivity.chatTranscriptIo.submit(java.util.concurrent.Callable {
            LocalChatTranscriptStore(java.io.File(f.activity.filesDir, "chat-history")).load(id)
        }).get(2, TimeUnit.SECONDS)
    }

    private fun assertResearchTranscriptOrder(f: Fixture, request: String, submit: () -> Unit) {
        (field(f.activity, "localAgentExecutor") as ExecutorService).shutdown()
        val before = transcript(f).size
        submit()
        idle()
        val dialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        idle()
        val waiting = transcript(f).drop(before)
        assertEquals("Original request precedes the question", request, waiting.first().text)
        assertTrue(waiting.first().user)
        assertTrue(waiting.any { !it.user && it.text.startsWith("Soll ich diese Recherche") })
        reply(f)
        val entries = transcript(f).drop(before)
        assertEquals(1, entries.count { it.user && it.text == request })
        val prompt = entries.indexOfFirst { !it.user && it.text.startsWith("Soll ich diese Recherche") }
        val approval = entries.indexOfFirst { it.user && it.text == "Ja, mach." }
        assertTrue("Question precedes approval", prompt in 1 until approval)
        assertTrue("No research execution", (field(f.activity, "localAgentExecutor") as ExecutorService).isShutdown)
    }

    @Test fun topicDialogRecordsRequestBeforeConsentAndNeverDuplicatesIt() = withActivity { f ->
        val query = "Photosynthese und Blattfarben"
        assertResearchTranscriptOrder(f, query) {
            AgentActivity::class.java.getDeclaredMethod("confirmInternetResearch", String::class.java,
                ResearchMode::class.java, String::class.java).apply { isAccessible = true }
                .invoke(f.activity, query, ResearchMode.QUICK, null)
        }
    }

    @Test fun chatWebRequestRecordsRequestBeforeConsentAndNeverDuplicatesIt() = withActivity { f ->
        val request = "https://example.org/synthetic-order"
        assertResearchTranscriptOrder(f, request) {
            (field(f.activity, "input") as EditText).setText(request)
            AgentActivity::class.java.getDeclaredMethod("sendCurrentInput", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(f.activity, false)
        }
    }

    private fun askTopic(f: Fixture, query: String): AlertDialog {
        AgentActivity::class.java.getDeclaredMethod("confirmInternetResearch", String::class.java,
            ResearchMode::class.java, String::class.java).apply { isAccessible = true }
            .invoke(f.activity, query, ResearchMode.QUICK, null)
        idle()
        return requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
    }

    private fun noExecutionNotices(f: Fixture) = transcript(f).filter {
        !it.user && it.text.startsWith("Recherche nicht ausgeführt:")
    }

    @Test fun declineCancelAndExpiredConsentExplainOnceWithoutReplacingTheDraft() {
        for (ending in listOf("decline", "cancel", "expired")) withActivity { f ->
            val before = noExecutionNotices(f).size
            val dialog = askTopic(f, "Synthetische Recherche $ending")
            val draft = "Neuer noch ungesendeter Entwurf"
            (field(f.activity, "input") as EditText).setText(draft)
            when (ending) {
                "decline" -> dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                "cancel" -> dialog.cancel()
                else -> {
                    f.now = 60_001
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                }
            }
            idle()
            assertEquals(before + 1, noExecutionNotices(f).size)
            assertEquals(draft, (field(f.activity, "input") as EditText).text.toString())
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            idle()
            assertEquals("One outcome per ticket", before + 1, noExecutionNotices(f).size)
            assertNull(field(f.activity, "activeWebResearchSession"))
        }
    }

    @Test fun replacedConsentCannotExplainOrCancelTheSuccessorRequest() = withActivity { f ->
        val before = noExecutionNotices(f).size
        val old = askTopic(f, "Alter nicht ausgeführter Auftrag")
        val current = askTopic(f, "Neuer aktueller Auftrag")
        val binding = field(f.activity, "pendingResearchChatApproval")
        old.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idle()
        assertEquals(before, noExecutionNotices(f).size)
        assertSame(binding, field(f.activity, "pendingResearchChatApproval"))
        assertTrue(current.isShowing)
        current.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idle()
        assertEquals(before + 1, noExecutionNotices(f).size)
    }

    @Test fun oldConsentCannotWriteAnOutcomeIntoAnotherSession() = withActivity { f ->
        val old = askTopic(f, "Auftrag aus der alten Sitzung")
        set(f.activity, "sessionId", java.util.UUID.randomUUID().toString())
        val before = transcript(f)
        val draft = "Entwurf in der neuen Sitzung"
        (field(f.activity, "input") as EditText).setText(draft)
        old.cancel()
        idle()
        assertEquals(before, transcript(f))
        assertEquals(draft, (field(f.activity, "input") as EditText).text.toString())
        val current = askTopic(f, "Aktuelle neue Recherche")
        current.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idle()
        assertEquals(1, noExecutionNotices(f).size)
    }

    @Test fun pausedCurrentConsentKeepsItsOutcomeForResume() = withActivity { f ->
        val before = noExecutionNotices(f).size
        val dialog = askTopic(f, "Recherche vor dem Hintergrundwechsel")
        val draft = "Ungesendeter Entwurf bleibt erhalten"
        (field(f.activity, "input") as EditText).setText(draft)
        f.whilePaused {
            dialog.cancel()
            idle()
            assertEquals(before + 1, noExecutionNotices(f).size)
            assertNull(field(f.activity, "activeWebResearchSession"))
            assertFalse(field(f.activity, "voiceSpeaking") as Boolean)
        }
        idle()
        assertEquals(before + 1, noExecutionNotices(f).size)
        assertEquals(draft, (field(f.activity, "input") as EditText).text.toString())
    }

    private fun assertAcceptedResearchBlocked(reason: String) {
        for (fromChat in listOf(false, true)) withActivity { f ->
            val before = noExecutionNotices(f).size
            val dialog = if (fromChat) {
                (field(f.activity, "input") as EditText).setText("https://example.org/synthetic-blocked")
                AgentActivity::class.java.getDeclaredMethod("sendCurrentInput", Boolean::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(f.activity, false)
                idle()
                requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
            } else askTopic(f, "Erlaubte Recherche ohne Start")
            if (reason == "busy") set(f.activity, "busy", true)
            else (field(f.activity, "localAgentExecutor") as ExecutorService).shutdown()
            val draft = "Neuer ungesendeter Entwurf"
            (field(f.activity, "input") as EditText).setText(draft)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            val notices = noExecutionNotices(f).drop(before)
            assertEquals(1, notices.size)
            assertTrue(notices.single().text.contains(
                if (reason == "busy") "anderer Auftrag" else "Verarbeitung"))
            assertNull(field(f.activity, "activeWebResearchSession"))
            assertNull(field(f.activity, "pendingResearchChatApproval"))
            assertEquals(draft, (field(f.activity, "input") as EditText).text.toString())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertEquals("One outcome after repeated acceptance", before + 1, noExecutionNotices(f).size)
        }
    }

    @Test fun acceptedResearchWhileBusyExplainsWhyItDidNotStart() = assertAcceptedResearchBlocked("busy")

    @Test fun acceptedResearchAfterExecutorShutdownExplainsWhyItDidNotStart() =
        assertAcceptedResearchBlocked("executor")

    @Test fun exactReplyApprovesOnlyTheDisplayedTicketWhileTheModelWaits() = withActivity { f ->
        val dialog = ask(f, "Photosynthese")
        set(f.activity, "busy", true)
        set(f.activity, "activeLocalRequest", true)
        reply(f)
        assertEquals(1, f.completed.size)
        val session = requireNotNull(f.completed.single())
        assertEquals(WebToolRequest.Search("Photosynthese"), session.call)
        assertThrows(IllegalStateException::class.java) {
            session.execute(WebToolRequest.Search("Andere Frage")) { fail("Wrong request") }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertEquals(1, f.completed.size)
        assertEquals(0, f.invalidated)
    }

    @Test fun declineCompletesWithoutAGrant() = withActivity { f ->
        ask(f, "Photosynthese")
        reply(f, "Nein.")
        assertEquals(listOf<ApprovedWebResearchSession?>(null), f.completed)
    }

    @Test fun recognizedReplyDismissesItsOriginalModal() = withActivity { f ->
        val dialog = ask(f, "Photosynthese", inChat = false)
        assertTrue(dialog.isShowing)
        (field(f.activity, "input") as EditText).setText("Ja, mach.")
        AgentActivity::class.java.getDeclaredMethod("sendCurrentInput", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(f.activity, false)
        idle()
        assertFalse(dialog.isShowing)
        assertNotNull(f.completed.single())
    }

    @Test fun pauseThenResumeCannotReviveThePendingTicket() = withActivity { f ->
        ask(f, "Photosynthese")
        f.pauseAndResume()
        idle()
        reply(f)
        assertTrue(f.completed.isEmpty())
        assertEquals(1, f.invalidated)
    }

    @Test fun expiredChangedOrRevokedBindingCannotGrant() {
        val invalidate: List<(Fixture) -> Unit> = listOf(
            { it.now = 60_001 },
            { set(it.activity, "sessionId", "different-session") },
            { set(it.activity, "foregroundLearningActive", false) },
            { it.gate.cancel() },
            { it.consent.setMode(InternetAccessMode.OFF); it.consent.setMode(InternetAccessMode.ASK) }
        )
        for (revoke in invalidate) withActivity { f ->
            ask(f, "Photosynthese")
            revoke(f)
            reply(f)
            assertTrue(f.completed.all { it == null })
            assertTrue(f.completed.size + f.invalidated == 1)
        }
    }

    @Test fun replacedDialogCannotApproveTheCurrentChatRequest() = withActivity { f ->
        val old = ask(f, "Alter Auftrag")
        ask(f, "Neuer Auftrag")
        old.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertTrue(f.completed.isEmpty())
        reply(f)
        assertEquals(WebToolRequest.Search("Neuer Auftrag"), f.completed.single()!!.call)
    }

    @Test fun ambiguousOrUnrelatedReplyInvalidatesTheChatBinding() = withActivity { f ->
        val dialog = ask(f, "Photosynthese")
        (field(f.activity, "input") as EditText).setText("Ja, mach den anderen.")
        val consume = AgentActivity::class.java.getDeclaredMethod("consumePendingResearchChatReply")
            .apply { isAccessible = true }
        assertEquals(false, consume.invoke(f.activity))
        assertEquals(1, f.invalidated)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertTrue(f.completed.isEmpty())
        (field(f.activity, "input") as EditText).setText("Ja, mach.")
        assertEquals(false, consume.invoke(f.activity))
    }

    @Test fun otherToolConfirmationDisarmsTheResearchChatReply() {
        for (destructive in listOf(false, true)) withActivity { f ->
            val researchDialog = ask(f, "Photosynthese")
            val proposal = if (destructive) ToolProposal("delete_file",
                mapOf("path" to "/storage/emulated/0/Download/synthetic-consent-test.txt"))
            else ToolProposal("open_app", mapOf("packageOrName" to "com.example.synthetic"))
            val decisions = mutableListOf<Boolean>()
            val recordOnly: (Boolean) -> Unit = { decisions += it }
            AgentActivity::class.java.declaredMethods.single {
                it.name == "showStructuredToolConfirmation" && it.parameterCount == 3
            }.apply { isAccessible = true }.invoke(f.activity, proposal, destructive, recordOnly)
            idle()
            val otherDialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
            assertNotSame(researchDialog, otherDialog)
            (field(f.activity, "input") as EditText).setText("Ja, mach.")
            val consume = AgentActivity::class.java.getDeclaredMethod("consumePendingResearchChatReply")
                .apply { isAccessible = true }
            assertEquals(false, consume.invoke(f.activity))
            researchDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertEquals(1, f.invalidated)
            assertTrue(f.completed.isEmpty())
            assertTrue(decisions.isEmpty())
            assertTrue(otherDialog.isShowing)
        }
    }
}
